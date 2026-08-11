package net.jojoaddison.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import net.jojoaddison.domain.enumeration.ShiftStatus;

public class ShiftTestSamples {

    public static Shift getShiftSample1() {
        return new Shift()
            .id("id1")
            .rosterId("rosterId1")
            .professionalId("professionalId1")
            .startsAt(Instant.parse("2026-01-01T08:00:00Z"))
            .endsAt(Instant.parse("2026-01-01T20:00:00Z"))
            .status(ShiftStatus.ACTIVE)
            .notes("notes1")
            .createdDate(LocalDate.of(2026, 1, 1))
            .modifiedDate(LocalDate.of(2026, 1, 1))
            .createdBy("createdBy1")
            .modifiedBy("modifiedBy1");
    }

    public static Shift getShiftSample2() {
        return new Shift()
            .id("id2")
            .rosterId("rosterId2")
            .professionalId("professionalId2")
            .startsAt(Instant.parse("2026-01-02T08:00:00Z"))
            .endsAt(Instant.parse("2026-01-02T20:00:00Z"))
            .status(ShiftStatus.UPCOMING)
            .notes("notes2")
            .createdDate(LocalDate.of(2026, 1, 2))
            .modifiedDate(LocalDate.of(2026, 1, 2))
            .createdBy("createdBy2")
            .modifiedBy("modifiedBy2");
    }

    public static Shift getShiftRandomSampleGenerator() {
        return new Shift()
            .id(UUID.randomUUID().toString())
            .rosterId(UUID.randomUUID().toString())
            .professionalId(UUID.randomUUID().toString())
            .notes(UUID.randomUUID().toString())
            .createdBy(UUID.randomUUID().toString())
            .modifiedBy(UUID.randomUUID().toString());
    }
}
