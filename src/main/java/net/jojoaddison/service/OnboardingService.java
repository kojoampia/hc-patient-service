package net.jojoaddison.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import net.jojoaddison.domain.enumeration.IdentificationType;
import net.jojoaddison.domain.enumeration.MedicationStatus;
import net.jojoaddison.domain.enumeration.OnboardingStatus;
import net.jojoaddison.domain.enumeration.StatSource;
import net.jojoaddison.repository.AddressRepository;
import net.jojoaddison.repository.AllergyRepository;
import net.jojoaddison.repository.ConditionRepository;
import net.jojoaddison.repository.MedicationRepository;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.repository.StatRepository;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.dto.OnboardingAddressDTO;
import net.jojoaddison.service.dto.OnboardingBaselineDTO;
import net.jojoaddison.service.dto.OnboardingCareAngelDTO;
import net.jojoaddison.service.dto.OnboardingCurrentStateDTO;
import net.jojoaddison.service.dto.OnboardingIdentificationDTO;
import net.jojoaddison.service.dto.OnboardingIdentityDTO;
import net.jojoaddison.service.dto.OnboardingStatusDTO;
import net.jojoaddison.service.event.PatientEventPublisher;
import net.jojoaddison.service.event.PatientEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
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
    private final PatientEventPublisher events;
    private final GatewayAccountClient gatewayAccountClient;

    public OnboardingService(
        ProfileRepository profileRepository,
        AddressRepository addressRepository,
        StatRepository statRepository,
        ConditionRepository conditionRepository,
        AllergyRepository allergyRepository,
        MedicationRepository medicationRepository,
        CareDelegationService careDelegationService,
        PatientEventPublisher events,
        GatewayAccountClient gatewayAccountClient
    ) {
        this.profileRepository = profileRepository;
        this.addressRepository = addressRepository;
        this.statRepository = statRepository;
        this.conditionRepository = conditionRepository;
        this.allergyRepository = allergyRepository;
        this.medicationRepository = medicationRepository;
        this.careDelegationService = careDelegationService;
        this.events = events;
        this.gatewayAccountClient = gatewayAccountClient;
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
        saved.setAccountId(resolveAccountId());
        saved = saveUnlinkingIfTheAccountWasTakenMeanwhile(saved);
        if (address != null) {
            address.setPatientId(saved.getPatientId());
            addressRepository.save(address);
        }

        // The only place the email -> patientId mapping is published. A consumer that has been watching this person
        // since registration learns here which patient they became.
        events.publish(
            PatientEventType.ONBOARDING_STARTED,
            saved.getEmail(),
            null,
            saved.getPatientId(),
            Map.of("startedAt", Instant.now().toString())
        );
        publishStep(saved, STEP_IDENTITY, "identity");
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
        Profile advanced = advance(profile, STEP_CARE_ANGEL);
        publishStep(advanced, STEP_CARE_ANGEL, "careAngel");
        return advanced;
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
        Profile advanced = advance(profile, STEP_BASELINE);
        publishStep(advanced, STEP_BASELINE, "baseline");
        return advanced;
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
        Profile advanced = advance(profile, STEP_CURRENT_STATE);
        publishStep(advanced, STEP_CURRENT_STATE, "currentState");
        return advanced;
    }

    /** Step 5 — identification. Required, and with no "none" accepted. */
    public Profile identification(Profile profile, OnboardingIdentificationDTO identification) {
        requireText(identification == null ? null : identification.cardType(), "ID type");
        requireText(identification.cardNumber(), "ID number");

        // Canonicalised, not validated. IdentificationType.canonicalise never rejects: the web form is still a
        // free-text input, so a service that refused unrecognised values would answer 400 to every patient
        // finishing onboarding the moment it deployed ahead of the clients — the same cross-repo ordering failure
        // the Stat pagination work already cost this subsystem. Tightening to strict rejection is safe only once
        // both clients ship a constrained control.
        profile.setCardType(IdentificationType.canonicalise(identification.cardType()));
        profile.setCardNumber(identification.cardNumber());
        Profile advanced = advance(profile, STEP_IDENTIFICATION);
        publishStep(advanced, STEP_IDENTIFICATION, "identification");
        return advanced;
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
        Profile done = profileRepository.save(profile);
        events.publish(
            PatientEventType.ONBOARDING_COMPLETED,
            done.getEmail(),
            null,
            done.getPatientId(),
            Map.of("completedAt", done.getOnboardingCompletedAt().toString())
        );
        return done;
    }

    /** The profile this email owns, for the steps that require one to exist already. */
    public Optional<Profile> profileFor(String email) {
        return profileRepository.findOneByEmailIgnoreCase(email);
    }

    // --- internals ------------------------------------------------------------------------------------------------

    /**
     * The gateway account the caller signed in with, for {@link Profile#getAccountId()}.
     *
     * <h2>Resolved from the gateway, not from the token, and that is a sequencing decision</h2>
     *
     * <p>Backlog item 44. There are two ways for a {@code User.id} to reach this service: ask the gateway, or have
     * the gateway put it in the token. <strong>The second is better and is a change in a different repository</strong>
     * — hc-professional already does it, minting a {@code uid} claim — so it is filed rather than assumed here. This
     * relays the caller's own token to {@code GET /api/account}, which takes no subject and can therefore name nobody
     * but them.</p>
     *
     * <h2>What happens when the gateway is unreachable</h2>
     *
     * <p><strong>Onboarding succeeds and the profile is written with no account id.</strong> Nothing in this service
     * authorises on the field, so an unlinked profile behaves in every way the profiles written before the field
     * existed do; what is lost is that hc-admin cannot yet name that patient, which is precisely the state the whole
     * estate is in today. Refusing instead would make the patient's one path into their own record depend on a
     * sibling service being up, to populate a field nothing on that path reads.</p>
     *
     * <p>It is not left for somebody to notice: change unit {@code 004} runs on every application start and links
     * exactly the profiles that have no account id, so the repair is the next restart rather than an intervention.</p>
     *
     * @return the account id, or null — never a throw.
     */
    private String resolveAccountId() {
        String accountId = SecurityUtils.getCurrentRequestJwt().flatMap(gatewayAccountClient::accountIdOfCaller).orElse(null);
        if (accountId == null) {
            // No address and no id in the line: this is the one method here that runs while a patient is waiting, and
            // a correlation key in a log is the breach hc-admin's item 43 forbids outright.
            log.warn("Onboarding could not resolve a gateway account id; change unit 004 will link this profile on the next start");
            return null;
        }
        if (profileRepository.findOneByAccountId(accountId).isPresent()) {
            // Two profiles are never given one account id — it is what hc-admin will name a person by. Reaching here
            // means an account already has a profile under a different address, which start() cannot have refused.
            log.warn("A profile is already linked to this gateway account; leaving the new one unlinked for review");
            return null;
        }
        return accountId;
    }

    /**
     * Saves the new profile, giving up the account link rather than the onboarding if the link has been taken.
     *
     * <p>{@link #resolveAccountId()} asks whether the account is already claimed and then this writes, and there is no
     * transaction around the pair — Mongo runs standalone. The gap is real and it is narrow: the backfill runs as an
     * {@code ApplicationRunner}, so change unit {@code 004} can be linking this very account while a patient is
     * finishing step 1. {@code ProfileAccountIdUniqueIndex} turns that into a refused write instead of two profiles
     * claiming one account, and this is the half that decides what the patient sees when it happens.</p>
     *
     * <p><strong>They see success.</strong> The profile is written unlinked, exactly as it is when the gateway cannot
     * be reached at all, and change unit {@code 004} links it on the next start. Letting the exception out would fail
     * the one request a new patient has to make, at the last of three writes, over a field nothing on that path
     * reads — and it would fail it with a database error, which is neither actionable nor true of anything they did.</p>
     *
     * <p>A {@code DuplicateKeyException} on a profile that carries no account id is <em>not</em> this race — the
     * partial index only covers documents that have the field — so it is left to propagate rather than swallowed
     * under a message about an account link it has nothing to do with.</p>
     */
    private Profile saveUnlinkingIfTheAccountWasTakenMeanwhile(Profile profile) {
        String requested = profile.getAccountId();
        try {
            return profileRepository.save(profile);
        } catch (DuplicateKeyException conflict) {
            if (requested == null) {
                throw conflict;
            }
            log.warn(
                "This gateway account was linked to another profile between the check and the write; the new profile " +
                "is saved unlinked and change unit 004 will reconsider it on the next start"
            );
            profile.setAccountId(null);
            return profileRepository.save(profile);
        }
    }

    /**
     * Announces a completed step.
     *
     * <p>Carries the step and how much was answered, and nothing that was answered — see
     * {@link PatientEventPublisher} for why that line is drawn absolutely rather than case by case.</p>
     */
    private void publishStep(Profile profile, int step, String stepName) {
        events.publish(
            PatientEventType.ONBOARDING_STEP_COMPLETED,
            profile.getEmail(),
            null,
            profile.getPatientId(),
            Map.of("step", step, "stepName", stepName, "completedAt", Instant.now().toString())
        );
    }

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
