package net.jojoaddison.domain;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public class DutyRosterTestSamples {

    public static DutyRoster getDutyRosterSample1() {
        return new DutyRoster()
            .id("id1")
            .name("name1")
            .description("description1")
            .location("location1")
            .subscribedProfessionalIds(Set.of("professional1"))
            .createdDate(LocalDate.of(2026, 1, 1))
            .modifiedDate(LocalDate.of(2026, 1, 1))
            .createdBy("createdBy1")
            .modifiedBy("modifiedBy1");
    }

    public static DutyRoster getDutyRosterSample2() {
        return new DutyRoster()
            .id("id2")
            .name("name2")
            .description("description2")
            .location("location2")
            .subscribedProfessionalIds(Set.of("professional2"))
            .createdDate(LocalDate.of(2026, 1, 2))
            .modifiedDate(LocalDate.of(2026, 1, 2))
            .createdBy("createdBy2")
            .modifiedBy("modifiedBy2");
    }

    public static DutyRoster getDutyRosterRandomSampleGenerator() {
        return new DutyRoster()
            .id(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString())
            .location(UUID.randomUUID().toString())
            .subscribedProfessionalIds(Set.of(UUID.randomUUID().toString()))
            .createdBy(UUID.randomUUID().toString())
            .modifiedBy(UUID.randomUUID().toString());
    }
}
