package de.mealdeal.service;

import de.mealdeal.persistence.repository.MealPlanRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

/** Explicitly removes meal-plan history older than thirty calendar days. */
public final class MealPlanCleanupService {

    private static final int HISTORY_DAYS = 30;

    private final MealPlanRepository repository;
    private final Clock clock;

    /** Creates production cleanup using the local system time zone. */
    public MealPlanCleanupService(MealPlanRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    /** Creates deterministic cleanup with an explicit time source. */
    public MealPlanCleanupService(MealPlanRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "Meal plan repository must not be null.");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null.");
    }

    /**
     * Deletes entries strictly older than thirty days and returns their count.
     * An entry exactly thirty days old is deliberately retained.
     */
    public int deleteExpiredEntries() {
        LocalDate cutoffExclusive = LocalDate.now(clock).minusDays(HISTORY_DAYS);
        return repository.deleteBefore(cutoffExclusive);
    }
}
