package de.mealdeal.service;

import de.mealdeal.domain.WeekRange;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WeekServiceTest {

    private final WeekService service = new WeekService();

    @Test
    void calculatesNormalWeekFromMiddleDate() {
        assertWeek(LocalDate.of(2026, 8, 27),
                LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 30));
    }

    @Test
    void keepsMondayAsStart() {
        assertWeek(LocalDate.of(2026, 8, 24),
                LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 30));
    }

    @Test
    void mapsSundayToMondayOfSameWeek() {
        assertWeek(LocalDate.of(2026, 8, 30),
                LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 30));
    }

    @Test
    void crossesMonthBoundaryUsingLocalDate() {
        assertWeek(LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4));
    }

    @Test
    void crossesYearBoundaryUsingLocalDate() {
        assertWeek(LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 3));
    }

    @Test
    void handlesLeapYearFebruaryUsingLocalDate() {
        WeekRange range = service.weekContaining(LocalDate.of(2028, 2, 29));

        assertEquals(LocalDate.of(2028, 2, 28), range.getStartDate());
        assertEquals(LocalDate.of(2028, 3, 5), range.getEndDate());
        assertEquals(List.of(
                LocalDate.of(2028, 2, 28), LocalDate.of(2028, 2, 29),
                LocalDate.of(2028, 3, 1), LocalDate.of(2028, 3, 2),
                LocalDate.of(2028, 3, 3), LocalDate.of(2028, 3, 4),
                LocalDate.of(2028, 3, 5)), range.days());
    }

    @Test
    void rejectsNullDateAndNonMondayRangeStart() {
        assertThrows(NullPointerException.class, () -> service.weekContaining(null));
        assertThrows(IllegalArgumentException.class,
                () -> new WeekRange(LocalDate.of(2026, 8, 27)));
    }

    private void assertWeek(LocalDate input, LocalDate expectedStart, LocalDate expectedEnd) {
        WeekRange range = service.weekContaining(input);
        assertEquals(expectedStart, range.getStartDate());
        assertEquals(expectedEnd, range.getEndDate());
        assertEquals(7, range.days().size());
        assertEquals(expectedStart, range.days().getFirst());
        assertEquals(expectedEnd, range.days().getLast());
    }
}
