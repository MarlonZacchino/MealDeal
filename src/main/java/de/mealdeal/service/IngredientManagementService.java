package de.mealdeal.service;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.IngredientCategory;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.persistence.repository.IngredientRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Updates central ingredients while retaining their stable technical identities. */
public final class IngredientManagementService {

    private static final Comparator<Ingredient> ORDER = Comparator
            .comparingInt((Ingredient ingredient) -> ingredient.getCategory().getPosition())
            .thenComparing(Ingredient::getName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Ingredient::getName)
            .thenComparing(Ingredient::getId);

    private final IngredientRepository ingredientRepository;
    private final IngredientCategoryService categoryService;

    public IngredientManagementService(IngredientRepository ingredientRepository,
                                       IngredientCategoryService categoryService) {
        this.ingredientRepository = Objects.requireNonNull(
                ingredientRepository, "Ingredient repository must not be null.");
        this.categoryService = Objects.requireNonNull(
                categoryService, "Ingredient category service must not be null.");
    }

    /** Returns all central ingredients grouped deterministically by category and name. */
    public List<Ingredient> loadIngredients() {
        return ingredientRepository.findAll().stream().sorted(ORDER).toList();
    }

    /** Returns the current category catalog for ingredient editors. */
    public List<IngredientCategory> loadCategories() {
        return categoryService.loadCategories();
    }

    /** Renames and/or recategorizes one ingredient without changing its UUID. */
    public Ingredient update(UUID ingredientId, String name, UUID categoryId) {
        Objects.requireNonNull(ingredientId, "Ingredient ID must not be null.");
        Objects.requireNonNull(categoryId, "Ingredient category ID must not be null.");
        String validatedName = validatedName(name);
        Ingredient current = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new PersistenceException("Die Zutat existiert nicht mehr."));
        rejectDuplicateName(validatedName, ingredientId);
        IngredientCategory category = categoryService.loadCategories().stream()
                .filter(candidate -> candidate.getId().equals(categoryId))
                .findFirst()
                .orElseThrow(() -> new PersistenceException(
                        "Die ausgewählte Kategorie existiert nicht mehr."));
        Ingredient updated = new Ingredient(current.getId(), validatedName, category);
        ingredientRepository.save(updated);
        return updated;
    }

    private void rejectDuplicateName(String name, UUID ignoredId) {
        String normalizedName = normalized(name);
        boolean duplicate = ingredientRepository.findAll().stream()
                .filter(ingredient -> !ingredient.getId().equals(ignoredId))
                .anyMatch(ingredient -> normalized(ingredient.getName()).equals(normalizedName));
        if (duplicate) {
            throw new IllegalArgumentException("Eine Zutat mit diesem Namen existiert bereits.");
        }
    }

    private static String validatedName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Bitte gib einen Zutatennamen ein.");
        }
        return name.strip();
    }

    private static String normalized(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }
}
