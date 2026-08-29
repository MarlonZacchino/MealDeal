package de.mealdeal.ui.search;

import de.mealdeal.domain.Taste;
import de.mealdeal.persistence.repository.RecipeRepository;
import de.mealdeal.persistence.repository.TasteRepository;
import de.mealdeal.service.RecipeSearchService;
import de.mealdeal.service.TasteFilterMode;
import de.mealdeal.service.TasteSearchResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Holds taste-selection state and delegates filtering and ranking to RecipeSearchService. */
public final class TasteSearchModel {

    private static final Comparator<Taste> TASTE_ORDER = Comparator
            .comparing(Taste::getName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Taste::getName)
            .thenComparing(Taste::getId);

    private final TasteRepository tasteRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeSearchService recipeSearchService;
    private final List<Taste> selectedTastes = new ArrayList<>();

    /** Creates the search state with repositories and the existing search service. */
    public TasteSearchModel(TasteRepository tasteRepository,
                            RecipeRepository recipeRepository,
                            RecipeSearchService recipeSearchService) {
        this.tasteRepository = Objects.requireNonNull(
                tasteRepository, "Taste repository must not be null.");
        this.recipeRepository = Objects.requireNonNull(
                recipeRepository, "Recipe repository must not be null.");
        this.recipeSearchService = Objects.requireNonNull(
                recipeSearchService, "Recipe search service must not be null.");
    }

    /** Loads all selectable central tastes in deterministic display order. */
    public List<Taste> loadAvailableTastes() {
        return tasteRepository.findAll().stream().sorted(TASTE_ORDER).toList();
    }

    /** Adds one taste unless its stable identity is already selected. */
    public SelectionResult select(Taste taste) {
        Objects.requireNonNull(taste, "Taste must not be null.");
        if (selectedTastes.contains(taste)) {
            return SelectionResult.ALREADY_SELECTED;
        }
        selectedTastes.add(taste);
        return SelectionResult.ADDED;
    }

    /** Removes one selected taste by its stable identity. */
    public void remove(Taste taste) {
        selectedTastes.remove(Objects.requireNonNull(taste, "Taste must not be null."));
    }

    public List<Taste> getSelectedTastes() {
        return List.copyOf(selectedTastes);
    }

    /** Loads recipes and delegates the selected mode unchanged to RecipeSearchService. */
    public List<TasteSearchResult> search(TasteFilterMode mode) {
        Objects.requireNonNull(mode, "Taste filter mode must not be null.");
        if (selectedTastes.isEmpty()) {
            throw new IllegalArgumentException("Select at least one taste.");
        }
        return recipeSearchService.searchByTastes(
                recipeRepository.findAll(), selectedTastes, mode);
    }

    public enum SelectionResult {
        ADDED,
        ALREADY_SELECTED
    }
}
