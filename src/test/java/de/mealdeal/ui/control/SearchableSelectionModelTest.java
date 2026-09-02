package de.mealdeal.ui.control;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SearchableSelectionModelTest {

    private static final List<String> OPTIONS = List.of(
            "Spaghetti", "Omelett mit Gemüse", "Lasagne", "Omelett", "Öl");

    @Test
    void emptyTextReturnsCompleteAlphabeticalList() {
        assertEquals(List.of("Lasagne", "Öl", "Omelett", "Omelett mit Gemüse", "Spaghetti"),
                model().suggestions("  "));
    }

    @Test
    void ranksExactBeforePrefixBeforeContains() {
        SearchableSelectionModel<String> model = new SearchableSelectionModel<>(
                List.of("Omelett mit Gemüse", "Sommer-Omelett", "Omelett"), value -> value);

        assertEquals(List.of("Omelett", "Omelett mit Gemüse", "Sommer-Omelett"),
                model.suggestions("omelett"));
    }

    @Test
    void supportsCaseInsensitiveTrimmedAndGermanInput() {
        assertEquals(List.of("Omelett", "Omelett mit Gemüse"), model().suggestions("  OME  "));
        assertEquals("Öl", model().suggestions("oel").getFirst());
    }

    @Test
    void tolerantSubsequenceKeepsPlausibleMatch() {
        assertEquals(List.of("Omelett", "Omelett mit Gemüse"), model().suggestions("oml"));
    }

    @Test
    void orderingIsDeterministicForSameMatchClass() {
        assertEquals(List.of("Omelett", "Omelett mit Gemüse"), model().suggestions("ome"));
        assertEquals(model().suggestions("ome"), model().suggestions("ome"));
    }

    @Test
    void validSelectionCommitsAndInvalidTextKeepsIt() {
        SearchableSelectionModel<String> model = model();
        model.commit("Omelett");

        assertEquals("Omelett", model.resolveExactOrKeep("ungültig").orElseThrow());
        assertEquals("Lasagne", model.resolveExactOrKeep("lasagne").orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> model.commit("Unbekannt"));
    }

    @Test
    void instancesDoNotShareSelectionOrFilterState() {
        SearchableSelectionModel<String> first = model();
        SearchableSelectionModel<String> second = model();
        first.commit("Omelett");

        assertEquals("Omelett", first.committedValue().orElseThrow());
        assertEquals(List.of(), second.committedValue().stream().toList());
        assertEquals(List.of("Lasagne"), second.suggestions("las"));
        assertEquals(List.of("Omelett", "Omelett mit Gemüse"), first.suggestions("ome"));
    }

    private static SearchableSelectionModel<String> model() {
        return new SearchableSelectionModel<>(OPTIONS, value -> value);
    }
}
