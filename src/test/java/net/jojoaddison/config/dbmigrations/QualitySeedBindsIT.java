package net.jojoaddison.config.dbmigrations;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.enumeration.DelegationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

/**
 * That the quality stack's real demo document still binds against this service's domain.
 *
 * <p>It guards a cross-repo contract that has already broken once: making {@code Profile.address} a document meant
 * the seed's free-text address no longer bound, and {@code DevelopmentDataInitializer} answers a failed read by
 * loading <em>nothing at all</em> — so one stale field would have emptied the entire dataset and left the blank
 * dashboard over a full database that seeding exists to prevent. The fixture in {@code src/test/resources} cannot
 * catch that, because it is not the document that ships.</p>
 *
 * <p><strong>This test is a workstation guard, not a CI gate.</strong> It reads {@code ../quality}, which exists in
 * the seven-repo workspace and nowhere else, and skips when it does not — so a skip here means "not checked", never
 * "checked and fine". Read it as a fast local warning that the seed and the domain have drifted apart.</p>
 */
@IntegrationTest
class QualitySeedBindsIT {

    static final String PATH = "../quality/patient-demo-seed.json";

    static boolean seedPresent() {
        return Files.exists(Path.of(PATH));
    }

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @EnabledIf("seedPresent")
    void theQualityDocumentStillBinds() throws java.io.IOException {
        var document = objectMapper.readValue(Files.readString(Path.of(PATH)), DevelopmentDataInitializer.SeedDocument.class);

        assertThat(document.dev).isNotNull();
        assertThat(document.dev.profiles).isNotEmpty();
        assertThat(document.dev.careDelegations).isNotEmpty();
        assertThat(document.dev.careDelegations)
            .anySatisfy(delegation -> assertThat(delegation.getStatus()).isEqualTo(DelegationStatus.ACTIVE));
    }
}
