package de.mealdeal.domain;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Objects;
import java.util.stream.LongStream;

/** Immutable Monday-to-Sunday calendar range. */
public final class WeekRange {

    private static final int DAYS_PER_WEEK = 7;

    private final LocalDate startDate;
    private final LocalDate endDate;

    public WeekRange(LocalDate startDate) {
        this.startDate = Objects.requireNonNull(startDate, "Week start date must not be null.");
        if (startDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("Week start date must be a Monday.");
        }
        this.endDate = startDate.plusDays(DAYS_PER_WEEK - 1L);
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    /** Returns all seven dates in chronological order. */
    public List<LocalDate> days() {
        return LongStream.range(0, DAYS_PER_WEEK)
                .mapToObj(startDate::plusDays)
                .toList();
    }
}
