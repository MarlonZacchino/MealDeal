package de.mealdeal.ui.form;

import java.time.Duration;
import java.util.Objects;

/** UI input units for converting positive whole values to canonical recipe durations. */
public enum RecipeTimeUnit {
    SECONDS("Sekunden", 1),
    MINUTES("Minuten", 60),
    HOURS("Stunden", 3600);

    private final String displayName;
    private final long secondsPerUnit;

    RecipeTimeUnit(String displayName, long secondsPerUnit) {
        this.displayName = displayName;
        this.secondsPerUnit = secondsPerUnit;
    }

    /** Converts a positive whole input value without losing precision. */
    public Duration toDuration(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("Recipe time must be greater than zero.");
        }
        return Duration.ofSeconds(Math.multiplyExact(value, secondsPerUnit));
    }

    /** Selects the largest unit that represents the duration exactly. */
    public static EditValue forEditing(Duration duration) {
        Objects.requireNonNull(duration, "Duration must not be null.");
        long seconds = duration.getSeconds();
        if (duration.isZero() || duration.isNegative() || duration.getNano() != 0) {
            throw new IllegalArgumentException("Duration must contain positive whole seconds.");
        }
        if (seconds % HOURS.secondsPerUnit == 0) {
            return new EditValue(Math.toIntExact(seconds / HOURS.secondsPerUnit), HOURS);
        }
        if (seconds % MINUTES.secondsPerUnit == 0) {
            return new EditValue(Math.toIntExact(seconds / MINUTES.secondsPerUnit), MINUTES);
        }
        return new EditValue(Math.toIntExact(seconds), SECONDS);
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Exact value and matching unit used to prefill one edit control. */
    public record EditValue(int value, RecipeTimeUnit unit) {
    }
}
