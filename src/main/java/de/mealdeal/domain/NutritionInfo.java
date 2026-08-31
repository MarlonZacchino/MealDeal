package de.mealdeal.domain;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.OptionalInt;

/** Optional nutrition values that apply to exactly one recipe serving. */
public final class NutritionInfo {

    private final OptionalInt caloriesKcal;
    private final Optional<BigDecimal> proteinGrams;
    private final Optional<BigDecimal> carbohydrateGrams;
    private final Optional<BigDecimal> fatGrams;

    /**
     * Creates nutrition information whose individual values may be absent.
     *
     * @param caloriesKcal nonnegative calories in kcal, or {@code null}
     * @param proteinGrams nonnegative protein in grams, or {@code null}
     * @param carbohydrateGrams nonnegative carbohydrates in grams, or {@code null}
     * @param fatGrams nonnegative fat in grams, or {@code null}
     */
    public NutritionInfo(Integer caloriesKcal, BigDecimal proteinGrams,
                         BigDecimal carbohydrateGrams, BigDecimal fatGrams) {
        this.caloriesKcal = optionalCalories(caloriesKcal);
        this.proteinGrams = optionalGrams(proteinGrams, "Protein");
        this.carbohydrateGrams = optionalGrams(carbohydrateGrams, "Carbohydrates");
        this.fatGrams = optionalGrams(fatGrams, "Fat");
    }

    public OptionalInt getCaloriesKcal() {
        return caloriesKcal;
    }

    public Optional<BigDecimal> getProteinGrams() {
        return proteinGrams;
    }

    public Optional<BigDecimal> getCarbohydrateGrams() {
        return carbohydrateGrams;
    }

    public Optional<BigDecimal> getFatGrams() {
        return fatGrams;
    }

    /** Returns whether at least one nutrition value is present. */
    public boolean hasAnyValue() {
        return caloriesKcal.isPresent() || proteinGrams.isPresent()
                || carbohydrateGrams.isPresent() || fatGrams.isPresent();
    }

    private static OptionalInt optionalCalories(Integer value) {
        if (value == null) {
            return OptionalInt.empty();
        }
        if (value < 0) {
            throw new IllegalArgumentException("Calories must not be negative.");
        }
        return OptionalInt.of(value);
    }

    private static Optional<BigDecimal> optionalGrams(BigDecimal value, String fieldName) {
        if (value == null) {
            return Optional.empty();
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative.");
        }
        return Optional.of(value);
    }
}
