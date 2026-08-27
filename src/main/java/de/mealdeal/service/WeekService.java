package de.mealdeal.service;

import de.mealdeal.domain.WeekRange;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

/** Calculates the Monday-to-Sunday week containing a calendar date. */
public final class WeekService {

    public WeekRange weekContaining(LocalDate date) {
        Objects.requireNonNull(date, "Date must not be null.");
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new WeekRange(monday);
    }
}
