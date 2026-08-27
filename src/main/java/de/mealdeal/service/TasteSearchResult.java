package de.mealdeal.service;

import de.mealdeal.domain.Recipe;
import de.mealdeal.domain.Taste;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Describes why and how strongly a recipe matched selected tastes. */
public final class TasteSearchResult {

    private final Recipe recipe;
    private final List<Taste> matchedTastes;
    private final List<Taste> missingTastes;
    private final int selectedCount;
    private final BigDecimal matchRatio;
    private final MatchQuality matchQuality;

    TasteSearchResult(Recipe recipe, List<Taste> matchedTastes,
                      List<Taste> missingTastes, BigDecimal matchRatio) {
        this.recipe = Objects.requireNonNull(recipe, "Recipe must not be null.");
        this.matchedTastes = List.copyOf(matchedTastes);
        this.missingTastes = List.copyOf(missingTastes);
        this.selectedCount = this.matchedTastes.size() + this.missingTastes.size();
        this.matchRatio = Objects.requireNonNull(matchRatio, "Match ratio must not be null.");
        this.matchQuality = MatchQuality.fromCounts(getMatchedCount(), selectedCount);
    }

    public Recipe getRecipe() {
        return recipe;
    }

    public int getMatchedCount() {
        return matchedTastes.size();
    }

    public int getSelectedCount() {
        return selectedCount;
    }

    public BigDecimal getMatchRatio() {
        return matchRatio;
    }

    public MatchQuality getMatchQuality() {
        return matchQuality;
    }

    public List<Taste> getMatchedTastes() {
        return matchedTastes;
    }

    public List<Taste> getMissingTastes() {
        return missingTastes;
    }
}
