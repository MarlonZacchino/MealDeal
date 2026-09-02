package de.mealdeal.service;

import de.mealdeal.domain.IngredientCategories;
import de.mealdeal.domain.IngredientCategory;
import de.mealdeal.persistence.DuplicateIngredientCategoryException;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.persistence.repository.IngredientCategoryRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Manages the mutable category catalog and protects the fallback category. */
public final class IngredientCategoryService {

    private static final Comparator<IngredientCategory> ORDER = Comparator
            .comparingInt(IngredientCategory::getPosition)
            .thenComparing(IngredientCategory::getName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(IngredientCategory::getId);

    private final IngredientCategoryRepository repository;

    public IngredientCategoryService(IngredientCategoryRepository repository) {
        this.repository = Objects.requireNonNull(
                repository, "Ingredient category repository must not be null.");
    }

    /** Returns the current persisted catalog in deterministic position order. */
    public List<IngredientCategory> loadCategories() {
        return repository.findAll().stream().sorted(ORDER).toList();
    }

    /** Creates a category immediately before the protected fallback category. */
    public IngredientCategory create(String name) {
        String validatedName = validatedName(name);
        List<IngredientCategory> categories = new ArrayList<>(loadCategories());
        rejectDuplicateName(categories, validatedName, null);
        int insertionIndex = indexOf(categories, IngredientCategories.OTHER.getId());
        if (insertionIndex < 0) {
            throw new PersistenceException("Die Fallback-Kategorie Sonstiges fehlt.");
        }
        IngredientCategory created = new IngredientCategory(validatedName, insertionIndex);
        categories.add(insertionIndex, created);
        repository.replaceAll(normalizePositions(categories));
        return created;
    }

    /** Renames a category while retaining its stable identity and position. */
    public void rename(UUID categoryId, String name) {
        requireMutableCategory(categoryId);
        String validatedName = validatedName(name);
        List<IngredientCategory> categories = new ArrayList<>(loadCategories());
        int index = requiredIndex(categories, categoryId);
        rejectDuplicateName(categories, validatedName, categoryId);
        IngredientCategory current = categories.get(index);
        categories.set(index, new IngredientCategory(
                current.getId(), validatedName, current.getPosition()));
        repository.replaceAll(normalizePositions(categories));
    }

    /** Moves one category by a single position and returns whether the order changed. */
    public boolean moveUp(UUID categoryId) {
        return move(categoryId, -1);
    }

    /** Moves one category by a single position and returns whether the order changed. */
    public boolean moveDown(UUID categoryId) {
        return move(categoryId, 1);
    }

    /** Returns the number of ingredients shown in a deletion confirmation. */
    public int countIngredients(UUID categoryId) {
        return repository.countIngredients(Objects.requireNonNull(
                categoryId, "Ingredient category ID must not be null."));
    }

    /** Deletes a non-fallback category and atomically reassigns its ingredients. */
    public void delete(UUID categoryId) {
        requireMutableCategory(categoryId);
        List<IngredientCategory> categories = new ArrayList<>(loadCategories());
        int index = requiredIndex(categories, categoryId);
        categories.remove(index);
        if (categories.stream().noneMatch(category -> category.getId()
                .equals(IngredientCategories.OTHER.getId()))) {
            throw new PersistenceException("Die Fallback-Kategorie Sonstiges fehlt.");
        }
        repository.deleteAndReassign(categoryId, IngredientCategories.OTHER.getId(),
                normalizePositions(categories));
    }

    /** Returns whether the category is the protected fallback. */
    public boolean isFallback(IngredientCategory category) {
        return Objects.requireNonNull(category, "Ingredient category must not be null.")
                .getId().equals(IngredientCategories.OTHER.getId());
    }

    private boolean move(UUID categoryId, int offset) {
        Objects.requireNonNull(categoryId, "Ingredient category ID must not be null.");
        List<IngredientCategory> categories = new ArrayList<>(loadCategories());
        int index = requiredIndex(categories, categoryId);
        int targetIndex = index + offset;
        if (targetIndex < 0 || targetIndex >= categories.size()) {
            return false;
        }
        java.util.Collections.swap(categories, index, targetIndex);
        repository.replaceAll(normalizePositions(categories));
        return true;
    }

    private static List<IngredientCategory> normalizePositions(
            List<IngredientCategory> categories) {
        return java.util.stream.IntStream.range(0, categories.size())
                .mapToObj(index -> {
                    IngredientCategory category = categories.get(index);
                    return new IngredientCategory(category.getId(), category.getName(), index);
                })
                .toList();
    }

    private static void rejectDuplicateName(List<IngredientCategory> categories, String name,
                                            UUID ignoredId) {
        String normalized = normalized(name);
        boolean duplicate = categories.stream()
                .filter(category -> ignoredId == null || !category.getId().equals(ignoredId))
                .anyMatch(category -> normalized(category.getName()).equals(normalized));
        if (duplicate) {
            throw new DuplicateIngredientCategoryException(
                    "Eine Kategorie mit diesem Namen existiert bereits.");
        }
    }

    private static String validatedName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Bitte gib einen Kategorienamen ein.");
        }
        return name.strip();
    }

    private static String normalized(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }

    private static int indexOf(List<IngredientCategory> categories, UUID id) {
        for (int index = 0; index < categories.size(); index++) {
            if (categories.get(index).getId().equals(id)) {
                return index;
            }
        }
        return -1;
    }

    private static int requiredIndex(List<IngredientCategory> categories, UUID id) {
        Objects.requireNonNull(id, "Ingredient category ID must not be null.");
        int index = indexOf(categories, id);
        if (index < 0) {
            throw new PersistenceException("Die Kategorie existiert nicht mehr.");
        }
        return index;
    }

    private static void requireMutableCategory(UUID categoryId) {
        Objects.requireNonNull(categoryId, "Ingredient category ID must not be null.");
        if (categoryId.equals(IngredientCategories.OTHER.getId())) {
            throw new IllegalArgumentException(
                    "Die Fallback-Kategorie Sonstiges darf nicht geändert werden.");
        }
    }
}
