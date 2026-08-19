package net.jojoaddison.service;

import java.util.Optional;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.repository.ProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Service Implementation for managing {@link net.jojoaddison.domain.Profile}.
 */
@Service
public class ProfileService {

    private final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final ProfileRepository profileRepository;

    public ProfileService(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    /**
     * Save a profile.
     *
     * @param profile the entity to save.
     * @return the persisted entity.
     */
    public Profile save(Profile profile) {
        log.debug("Request to save Profile : {}", profile);
        return profileRepository.save(profile);
    }

    /**
     * Update a profile.
     *
     * @param profile the entity to save.
     * @return the persisted entity.
     */
    public Profile update(Profile profile) {
        log.debug("Request to update Profile : {}", profile);
        return profileRepository.save(profile);
    }

    /**
     * Partially update a profile.
     *
     * @param profile the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<Profile> partialUpdate(Profile profile) {
        log.debug("Request to partially update Profile : {}", profile);

        return profileRepository
            .findById(profile.getId())
            .map(existingProfile -> {
                if (profile.getPatientId() != null) {
                    existingProfile.setPatientId(profile.getPatientId());
                }
                if (profile.getFirstName() != null) {
                    existingProfile.setFirstName(profile.getFirstName());
                }
                if (profile.getMiddleNames() != null) {
                    existingProfile.setMiddleNames(profile.getMiddleNames());
                }
                if (profile.getLastName() != null) {
                    existingProfile.setLastName(profile.getLastName());
                }
                if (profile.getMembership() != null) {
                    existingProfile.setMembership(profile.getMembership());
                }
                if (profile.getBirthDate() != null) {
                    existingProfile.setBirthDate(profile.getBirthDate());
                }
                if (profile.getSex() != null) {
                    existingProfile.setSex(profile.getSex());
                }
                if (profile.getBloodGroup() != null) {
                    existingProfile.setBloodGroup(profile.getBloodGroup());
                }
                if (profile.getMobilePhone() != null) {
                    existingProfile.setMobilePhone(profile.getMobilePhone());
                }
                if (profile.getPhoneNumber() != null) {
                    existingProfile.setPhoneNumber(profile.getPhoneNumber());
                }
                if (profile.getEmail() != null) {
                    existingProfile.setEmail(profile.getEmail());
                }
                if (profile.getCardType() != null) {
                    existingProfile.setCardType(profile.getCardType());
                }
                if (profile.getCardNumber() != null) {
                    existingProfile.setCardNumber(profile.getCardNumber());
                }
                if (profile.getContacts() != null) {
                    existingProfile.setContacts(profile.getContacts());
                }
                if (profile.getAddress() != null) {
                    existingProfile.setAddress(profile.getAddress());
                }
                if (profile.getTeam() != null) {
                    existingProfile.setTeam(profile.getTeam());
                }
                if (profile.getImageUrl() != null) {
                    existingProfile.setImageUrl(profile.getImageUrl());
                }
                if (profile.getAbout() != null) {
                    existingProfile.setAbout(profile.getAbout());
                }
                if (profile.getSocialHandle() != null) {
                    existingProfile.setSocialHandle(profile.getSocialHandle());
                }
                if (profile.getCareAngelName() != null) {
                    existingProfile.setCareAngelName(profile.getCareAngelName());
                }
                if (profile.getCareAngelPhone() != null) {
                    existingProfile.setCareAngelPhone(profile.getCareAngelPhone());
                }
                if (profile.getOnboardingStatus() != null) {
                    existingProfile.setOnboardingStatus(profile.getOnboardingStatus());
                }
                if (profile.getOnboardingStep() != null) {
                    existingProfile.setOnboardingStep(profile.getOnboardingStep());
                }
                if (profile.getOnboardingCompletedAt() != null) {
                    existingProfile.setOnboardingCompletedAt(profile.getOnboardingCompletedAt());
                }

                // careAngelEmail and careAngelLogin are deliberately NOT merged here, and their absence is the
                // point rather than an oversight. They are a display cache of the active CareDelegation, maintained
                // by CareDelegationService as delegations change. Letting a generic PATCH write them would let the
                // profile screen name an angel who has no delegation, or fail to name one who has — a caller cannot
                // grant themselves access this way, because PatientScope reads the delegation and never this cache,
                // but "the record says my angel is X" and "X may act for me" would stop being the same statement.

                return existingProfile;
            })
            .map(profileRepository::save);
    }

    /**
     * Get all the profiles.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    public Page<Profile> findAll(Pageable pageable) {
        log.debug("Request to get all Profiles");
        return profileRepository.findAll(pageable);
    }

    /**
     * Get one profile by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    public Optional<Profile> findOne(String id) {
        log.debug("Request to get Profile : {}", id);
        return profileRepository.findById(id);
    }

    /**
     * Delete the profile by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        log.debug("Request to delete Profile : {}", id);
        profileRepository.deleteById(id);
    }
}
