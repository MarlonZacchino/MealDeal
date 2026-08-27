package de.mealdeal.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MatchQualityTest {

    @ParameterizedTest
    @MethodSource("qualities")
    void classifiesDefinedBoundaries(int matched, int selected, MatchQuality expected) {
        assertEquals(expected, MatchQuality.fromCounts(matched, selected));
    }

    static Stream<Arguments> qualities() {
        return Stream.of(
                Arguments.of(1, 1, MatchQuality.PERFECT),
                Arguments.of(2, 2, MatchQuality.PERFECT),
                Arguments.of(3, 3, MatchQuality.PERFECT),
                Arguments.of(2, 3, MatchQuality.GOOD),
                Arguments.of(3, 4, MatchQuality.GOOD),
                Arguments.of(1, 2, MatchQuality.PARTIAL),
                Arguments.of(1, 3, MatchQuality.PARTIAL),
                Arguments.of(2, 4, MatchQuality.PARTIAL)
        );
    }

    @Test
    void rejectsZeroAndImpossibleCounts() {
        assertThrows(IllegalArgumentException.class, () -> MatchQuality.fromCounts(0, 3));
        assertThrows(IllegalArgumentException.class, () -> MatchQuality.fromCounts(2, 1));
        assertThrows(IllegalArgumentException.class, () -> MatchQuality.fromCounts(1, 0));
    }
}
