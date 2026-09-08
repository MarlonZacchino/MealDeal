package de.mealdeal.service.recommendation;

import de.mealdeal.domain.Recipe;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Application facade that enriches a context once and delegates all ranking to R0. */
public final class PersonalizedRecommendationService {

    private final RecommendationPersonalizationService personalizationService;
    private final RecipeRecommendationService recommendationService;

    public PersonalizedRecommendationService(
            RecommendationPersonalizationService personalizationService,
            RecipeRecommendationService recommendationService) {
        this.personalizationService = Objects.requireNonNull(
                personalizationService, "Personalization service must not be null.");
        this.recommendationService = Objects.requireNonNull(
                recommendationService, "Recommendation service must not be null.");
    }

    /** Ranks loaded candidates without persisting SHOWN or any other interaction. */
    public RecommendationOutcome recommend(
            Collection<Recipe> candidates, RecommendationContext baseContext) {
        Objects.requireNonNull(candidates, "Recommendation candidates must not be null.");
        List<Recipe> snapshot = List.copyOf(candidates);
        RecommendationContext enriched = personalizationService.enrich(snapshot, baseContext);
        return recommendationService.recommend(snapshot, enriched);
    }
}
