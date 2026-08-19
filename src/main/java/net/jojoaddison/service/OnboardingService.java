package net.jojoaddison.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.jojoaddison.domain.Address;
import net.jojoaddison.domain.Allergy;
import net.jojoaddison.domain.Condition;
import net.jojoaddison.domain.Medication;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.Stat;
import net.jojoaddison.domain.enumeration.ActivitySource;
import net.jojoaddison.domain.enumeration.AllergyCategory;
import net.jojoaddison.domain.enumeration.AllergySeverity;
import net.jojoaddison.domain.enumeration.MedicationStatus;
import net.jojoaddison.domain.enumeration.OnboardingStatus;
import net.jojoaddison.domain.enumeration.StatSource;
import net.jojoaddison.repository.AddressRepository;
import net.jojoaddison.repository.AllergyRepository;
import net.jojoaddison.repository.ConditionRepository;
import net.jojoaddison.repository.MedicationRepository;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.repository.StatRepository;
import net.jojoaddison.service.dto.OnboardingAddressDTO;
import net.jojoaddison.service.dto.OnboardingBaselineDTO;
import net.jojoaddison.service.dto.OnboardingCareAngelDTO;
import net.jojoaddison.service.dto.OnboardingCurrentStateDTO;
import net.jojoaddison.service.dto.OnboardingIdentificationDTO;
import net.jojoaddison.service.dto.OnboardingIdentityDTO;
import net.jojoaddison.service.dto.OnboardingStatusDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The onboarding journey, step by step.
 *
 * <h2>Why this exists at all</h2>
 *
 * <p>{@code POST /api/profiles} cannot be called by the person who needs it most. It stamps ownership through
 * {@code PatientScope.requirePatientIdForWrite}, which resolves the caller to a patient by looking up a profile on
 * their token's email — and a newly registered patient has none. They cannot create the record that would grant them
 * the right to create it. {@link #start} is the one path out of that, and it is deliberately narrow: it acts only on
 * the token's email, and refuses outright if a profile for that email already exists.</p>
 *
 * <h2>Steps are separate writes, on purpose</h2>
 *
 * <p>There is no transaction to wrap them in — Mongo runs standalone here, with no replica set — so the journey is
 * built so that it does not need one. Step 1 writes an Address and a Profile; every later step is an ordinary scoped
 * write that the profile from step 1 has already authorised. A failure part-way leaves a patient with a real record
 * and partial clinical data, and the guard returns them to the step they stopped at. That is a correct intermediate
 * state rather than a corrupt one, which is what makes the missing transaction affordable.</p>
 */
@Service
public class OnboardingService {

    /** Step numbers, as the client resumes on and the status reports. */
    public static final int STEP_IDENTITY = 1;
    public static final int STEP_CARE_ANGEL = 2;
    public static final int STEP_BASELINE = 3;
    public static final int STEP_CURRENT_STATE = 4;
    public static final int STEP_IDENTIFICATION = 5;

    private static final String ENTITY_NAME = "onboarding";

    private final Logger log = LoggerFactory.getLogger(OnboardingService.class);

    private final ProfileRepository profileRepository;
    private final AddressRepository addressRepository;
    private final StatRepository statRepository;
    private final ConditionRepository conditionRepository;
    private final AllergyRepository allergyRepository;
    private final MedicationRepository medicationRepository;
    private final CareDelegationService careDelegationService;

    public OnboardingService(
        ProfileRepository profileRepository,
        AddressRepository addressRepository,
        StatRepository statRepository,
        ConditionRepository conditionRepository,
        AllergyRepository allergyRepository,
        MedicationRepository medicationRepository,
        CareDelegationService careDelegationService
    ) {
        this.profileRepository = profileRepository;
        this.addressRepository = addressRepository;
        this.statRepository = statRepository;
        this.conditionRepository = conditionRepository;
        this.allergyRepository = allergyRepository;
        this.medicationRepository = medicationRepository;
        this.careDelegationService = careDelegationService;
    }

    /**
     * Where this email is in the journey.
     *
     * <p>No profile means not started. A profile with a null status means <em>complete</em> — see
     * {@link OnboardingStatus} for why that default runs the way round it does.</p>
     */
    public OnboardingStatusDTO status(String email) {
        return profileRepository
            .findOneByEmailIgnoreCase(email)
            .map(profile ->
                new OnboardingStatusDTO(
                    profile.getOnboardingStatus(),
                    profile.getOnboardingStep(),
                    profile.getId(),
                    profile.getOnboardingStatus() == null || profile.getOnboardingStatus() == OnboardingStatus.COMPLETE
                )
            )
            .orElseGet(() -> new OnboardingStatusDTO(null, 0, null, false));
    }

    /**
     * Step 1 — create the patient's record. The one write that may run before a profile exists.
     *
     * @param email the token's email. Never taken from the payload.
     * @param identity the answers to step 1.
     * @return the new profile.
     */
    public Profile start(String email, OnboardingIdentityDTO identity) {
        log.debug("Request to start onboarding");
        if (profileRepository.findOneByEmailIgnoreCase(email).isPresent()) {
            // What makes this path un-abusable: it succeeds exactly once per account. Without it, the endpoint that
            // needs no existing profile would be an endpoint that can be called forever.
            throw new DomainStateException("This account already has a patient record", ENTITY_NAME, "alreadyonboarded");
        }
        requireText(identity == null ? null : identity.firstName(), "firstName");
        requireText(identity.lastName(), "lastName");

        // Address first. An orphaned Address is inert and gets overwritten on a retry, whereas a Profile holding a
        // reference to a document that was never written is a dangling pointer every read has to defend against.
        Address address = saveAddress(identity.address(), null);

        Profile profile = new Profile()
            .email(email.toLowerCase(Locale.ROOT))
            .firstName(identity.firstName())
            .middleNames(identity.middleNames())
            .lastName(identity.lastName())
            .birthDate(identity.birthDate())
            .sex(identity.sex())
            .mobilePhone(identity.mobilePhone())
            .phoneNumber(identity.phoneNumber())
            .address(address)
            .onboardingStatus(OnboardingStatus.IN_PROGRESS)
            .onboardingStep(STEP_IDENTITY);
        Profile saved = profileRepository.save(profile);

        // patientId is the identifier every other collection is keyed by. Setting it to the profile's own id keeps the
        // `patientId ?? id` fallback that PatientScope and the dashboard both apply from ever having to fire.
        saved.setPatientId(saved.getId());
        saved = profileRepository.save(saved);
        if (address != null) {
            address.setPatientId(saved.getPatientId());
            addressRepository.save(address);
        }
        return saved;
    }

    /**
     * Step 2 — nominate a care angel, and optionally record a standby nominee.
     *
     * <p>The gateway account for the angel is created by the client calling the gateway; this records the delegation
     * and the contact details. The step completes here regardless of whether the angel ever accepts.</p>
     */
    public Profile careAngel(Profile profile, OnboardingCareAngelDTO careAngel) {
        requireText(careAngel == null ? null : careAngel.email(), "care angel email");
        requireText(careAngel.fullName(), "care angel name");

        careDelegationService.nominate(
            profile.getPatientId(),
            profile.getEmail(),
            careAngel.email(),
            careAngel.fullName(),
            careAngel.phone()
        );

        if (careAngel.standby() != null && careAngel.standby().email() != null && !careAngel.standby().email().isBlank()) {
            if (!Boolean.TRUE.equals(careAngel.advanceConsent())) {
                throw new DomainStateException(
                    "A standby nominee cannot be recorded without advance consent",
                    ENTITY_NAME,
                    "consentrequired"
                );
            }
            careDelegationService.recordStandby(
                profile.getPatientId(),
                profile.getEmail(),
                careAngel.standby().email(),
                careAngel.standby().fullName(),
                careAngel.standby().phone(),
                true
            );
        }

        profile.setCareAngelName(careAngel.fullName());
        profile.setCareAngelPhone(careAngel.phone());
        if (careAngel.contacts() != null) {
            profile.setContacts(careAngel.contacts());
        }
        return advance(profile, STEP_CARE_ANGEL);
    }

    /** Step 3 — the baseline readings, one {@code Stat} each. */
    public Profile baseline(Profile profile, OnboardingBaselineDTO baseline) {
        require(baseline == null ? null : baseline.heightCm(), "height");
        require(baseline.weightKg(), "weight");
        require(baseline.systolic(), "systolic blood pressure");
        require(baseline.diastolic(), "diastolic blood pressure");

        List<Stat> readings = new ArrayList<>();
        readings.add(stat(profile, "HEIGHT", "Height", baseline.heightCm(), null, "cm"));
        readings.add(stat(profile, "WEIGHT", "Weight", baseline.weightKg(), null, "kg"));
        readings.add(stat(profile, "BLOOD_PRESSURE", "Blood pressure", baseline.systolic(), baseline.diastolic(), "mmHg"));
        if (baseline.heartRateBpm() != null) {
            readings.add(stat(profile, "HEART_RATE", "Resting heart rate", baseline.heartRateBpm(), null, "bpm"));
        }
        if (baseline.bloodSugarMmolL() != null) {
            readings.add(stat(profile, "BLOOD_SUGAR", "Blood sugar", baseline.bloodSugarMmolL(), null, "mmol/L"));
        }
        statRepository.saveAll(readings);
        return advance(profile, STEP_BASELINE);
    }

    /** Step 4 — conditions, allergies and medications the patient reports. */
    public Profile currentState(Profile profile, OnboardingCurrentStateDTO state) {
        if (state == null) {
            throw new DomainStateException("No answers were supplied", ENTITY_NAME, "empty");
        }
        requireAnswered(state.conditions(), state.noConditions(), "conditions");
        requireAnswered(state.allergies(), state.noAllergies(), "allergies");
        requireAnswered(state.medications(), state.noMedications(), "medications");

        if (state.bloodGroup() != null) {
            profile.setBloodGroup(state.bloodGroup());
        }

        // ActivitySource.PATIENT on everything written here, and notedById / prescribedById left null. A professional
        // id in those fields is what marks a record clinician-attested; nothing on this path is.
        if (state.conditions() != null) {
            conditionRepository.saveAll(
                state
                    .conditions()
                    .stream()
                    .map(entry ->
                        new Condition()
                            .patientId(profile.getPatientId())
                            .name(entry.name())
                            .description(entry.description())
                            .source(ActivitySource.PATIENT)
                            .createdDate(LocalDate.now())
                    )
                    .toList()
            );
        }
        if (state.allergies() != null) {
            allergyRepository.saveAll(
                state
                    .allergies()
                    .stream()
                    .map(entry ->
                        new Allergy()
                            .patientId(profile.getPatientId())
                            .name(entry.name())
                            .category(enumOrNull(AllergyCategory.class, entry.category()))
                            .severity(enumOrNull(AllergySeverity.class, entry.severity()))
                            .reaction(entry.reaction())
                            .notedOn(LocalDate.now())
                            .source(ActivitySource.PATIENT)
                            .createdDate(LocalDate.now())
                    )
                    .toList()
            );
        }
        if (state.medications() != null) {
            medicationRepository.saveAll(
                state
                    .medications()
                    .stream()
                    .map(entry ->
                        new Medication()
                            .patientId(profile.getPatientId())
                            .name(entry.name())
                            .dosage(entry.dosage())
                            .prescription(entry.prescription())
                            .status(Optional.ofNullable(enumOrNull(MedicationStatus.class, entry.status())).orElse(MedicationStatus.ACTIVE))
                            .startedOn(parseDate(entry.startedOn()))
                            .source(ActivitySource.PATIENT)
                            .createdDate(LocalDate.now())
                    )
                    .toList()
            );
        }
        return advance(profile, STEP_CURRENT_STATE);
    }

    /** Step 5 — identification. Required, and with no "none" accepted. */
    public Profile identification(Profile profile, OnboardingIdentificationDTO identification) {
        requireText(identification == null ? null : identification.cardType(), "ID type");
        requireText(identification.cardNumber(), "ID number");

        profile.setCardType(identification.cardType());
        profile.setCardNumber(identification.cardNumber());
        return advance(profile, STEP_IDENTIFICATION);
    }

    /**
     * Finish, once every required step has been answered.
     *
     * <p>Checked against the record rather than the step counter, because the counter only says how far the client
     * got — a patient who reached step 5 and left the identification blank has a step number that says otherwise.</p>
     */
    public Profile complete(Profile profile) {
        if (profile.getOnboardingStep() == null || profile.getOnboardingStep() < STEP_IDENTIFICATION) {
            throw new DomainStateException("Not every step has been answered yet", ENTITY_NAME, "incomplete");
        }
        if (isBlank(profile.getCardType()) || isBlank(profile.getCardNumber())) {
            throw new DomainStateException("Identification is required to finish onboarding", ENTITY_NAME, "identificationrequired");
        }
        profile.setOnboardingStatus(OnboardingStatus.COMPLETE);
        profile.setOnboardingCompletedAt(Instant.now());
        return profileRepository.save(profile);
    }

    /** The profile this email owns, for the steps that require one to exist already. */
    public Optional<Profile> profileFor(String email) {
        return profileRepository.findOneByEmailIgnoreCase(email);
    }

    // --- internals ------------------------------------------------------------------------------------------------

    private Profile advance(Profile profile, int step) {
        // Never backwards: a patient revisiting step 2 has not un-answered steps 3 to 5.
        if (profile.getOnboardingStep() == null || profile.getOnboardingStep() < step) {
            profile.setOnboardingStep(step);
        }
        if (profile.getOnboardingStatus() == null) {
            profile.setOnboardingStatus(OnboardingStatus.IN_PROGRESS);
        }
        return profileRepository.save(profile);
    }

    private Address saveAddress(OnboardingAddressDTO dto, String patientId) {
        if (dto == null) {
            return null;
        }
        return addressRepository.save(
            new Address()
                .patientId(patientId)
                .digitalAddress(dto.digitalAddress())
                .streetAddress(dto.streetAddress())
                .areaCode(dto.areaCode())
                .town(dto.town())
                .city(dto.city())
                .district(dto.district())
                .state(dto.state())
                .region(dto.region())
                .country(dto.country())
                .createdDate(LocalDate.now())
        );
    }

    private Stat stat(Profile profile, String type, String name, Double value, Double secondary, String unit) {
        return new Stat()
            .patientId(profile.getPatientId())
            .type(type)
            .name(name)
            .value(value)
            .secondaryValue(secondary)
            .unit(unit)
            .recordedAt(Instant.now())
            // Not PROFESSIONAL and not DEVICE: the patient typed this in. Nothing here derives a flag from it either,
            // because judging a reading against a reference band is a clinical act.
            .source(StatSource.PATIENT)
            .createdDate(LocalDate.now());
    }

    private void requireAnswered(List<?> entries, Boolean none, String what) {
        boolean hasEntries = entries != null && !entries.isEmpty();
        boolean declaredEmpty = Boolean.TRUE.equals(none);
        if (!hasEntries && !declaredEmpty) {
            // "I have no allergies" and "I have not answered yet" are different clinical statements, and an empty list
            // cannot tell them apart. The client has to say which it means.
            throw new DomainStateException("Answer the " + what + " question, or say there are none", ENTITY_NAME, "unanswered");
        }
        if (hasEntries && declaredEmpty) {
            throw new DomainStateException("The " + what + " answer contradicts itself", ENTITY_NAME, "contradictory");
        }
    }

    private static void requireText(String value, String what) {
        if (isBlank(value)) {
            throw new DomainStateException(capitalise(what) + " is required", ENTITY_NAME, "required");
        }
    }

    private static void require(Double value, String what) {
        if (value == null) {
            throw new DomainStateException(capitalise(what) + " is required", ENTITY_NAME, "required");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String capitalise(String what) {
        return what.substring(0, 1).toUpperCase(Locale.ROOT) + what.substring(1);
    }

    private static <E extends Enum<E>> E enumOrNull(Class<E> type, String name) {
        if (isBlank(name)) {
            return null;
        }
        try {
            return Enum.valueOf(type, name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainStateException("Unrecognised " + type.getSimpleName() + ": " + name, ENTITY_NAME, "badenum");
        }
    }

    private static LocalDate parseDate(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw new DomainStateException("Unrecognised date: " + value, ENTITY_NAME, "baddate");
        }
    }
}
