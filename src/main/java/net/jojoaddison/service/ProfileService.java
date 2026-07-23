package net.jojoaddison.service;

import java.util.Optional;
import java.util.function.Consumer;
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

    private static final Logger LOG = LoggerFactory.getLogger(ProfileService.class);

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
        LOG.debug("Request to save Profile : {}", profile);
        return profileRepository.save(profile);
    }

    /**
     * Update a profile.
     *
     * @param profile the entity to save.
     * @return the persisted entity.
     */
    public Profile update(Profile profile) {
        LOG.debug("Request to update Profile : {}", profile);
        return profileRepository.save(profile);
    }

    /**
     * Partially update a profile.
     *
     * @param profile the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<Profile> partialUpdate(Profile profile) {
        LOG.debug("Request to partially update Profile : {}", profile);

        return profileRepository
            .findById(profile.getId())
            .map(existingProfile -> {
                updateIfPresent(existingProfile::setFirstName, profile.getFirstName());
                updateIfPresent(existingProfile::setMiddleNames, profile.getMiddleNames());
                updateIfPresent(existingProfile::setLastName, profile.getLastName());
                updateIfPresent(existingProfile::setMembership, profile.getMembership());
                updateIfPresent(existingProfile::setBirthDate, profile.getBirthDate());
                updateIfPresent(existingProfile::setSex, profile.getSex());
                updateIfPresent(existingProfile::setMobilePhone, profile.getMobilePhone());
                updateIfPresent(existingProfile::setPhoneNumber, profile.getPhoneNumber());
                updateIfPresent(existingProfile::setEmail, profile.getEmail());
                updateIfPresent(existingProfile::setCardType, profile.getCardType());
                updateIfPresent(existingProfile::setCardNumber, profile.getCardNumber());
                updateIfPresent(existingProfile::setContacts, profile.getContacts());
                updateIfPresent(existingProfile::setAddress, profile.getAddress());
                updateIfPresent(existingProfile::setTeam, profile.getTeam());

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
        LOG.debug("Request to get all Profiles");
        return profileRepository.findAll(pageable);
    }

    /**
     * Get one profile by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    public Optional<Profile> findOne(String id) {
        LOG.debug("Request to get Profile : {}", id);
        return profileRepository.findById(id);
    }

    /**
     * Delete the profile by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        LOG.debug("Request to delete Profile : {}", id);
        profileRepository.deleteById(id);
    }

    private <T> void updateIfPresent(Consumer<T> setter, T value) {
        if (value != null) {
            setter.accept(value);
        }
    }
}
