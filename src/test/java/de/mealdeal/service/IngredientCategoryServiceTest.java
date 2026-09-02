package de.mealdeal.service;

import de.mealdeal.domain.IngredientCategories;
import de.mealdeal.domain.IngredientCategory;
import de.mealdeal.persistence.DuplicateIngredientCategoryException;
import de.mealdeal.persistence.repository.IngredientCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngredientCategoryServiceTest {

    private InMemoryCategoryRepository repository;
    private IngredientCategoryService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryCategoryRepository(List.of(
                IngredientCategories.FRUIT,
                IngredientCategories.VEGETABLES,
                new IngredientCategory(IngredientCategories.OTHER.getId(), "Sonstiges", 2)));
        service = new IngredientCategoryService(repository);
    }

    @Test
    void createsBeforeFallbackAndRejectsBlankOrDuplicateNameIgnoringCase() {
        IngredientCategory created = service.create("  Fermentiertes  ");

        assertEquals("Fermentiertes", created.getName());
        assertEquals(List.of("Obst", "Gemüse", "Fermentiertes", "Sonstiges"), names());
        assertEquals(List.of(0, 1, 2, 3), positions());
        assertThrows(IllegalArgumentException.class, () -> service.create("   "));
        assertThrows(DuplicateIngredientCategoryException.class,
                () -> service.create(" oBsT "));
    }

    @Test
    void renamesWithStableIdentityButProtectsFallbackAndDuplicateNames() {
        UUID fruitId = IngredientCategories.FRUIT.getId();

        service.rename(fruitId, "Früchte");

        IngredientCategory renamed = repository.findById(fruitId).orElseThrow();
        assertEquals(fruitId, renamed.getId());
        assertEquals("Früchte", renamed.getName());
        assertEquals(0, renamed.getPosition());
        assertThrows(DuplicateIngredientCategoryException.class,
                () -> service.rename(fruitId, "gemüse"));
        assertThrows(IllegalArgumentException.class,
                () -> service.rename(IngredientCategories.OTHER.getId(), "Rest"));
    }

    @Test
    void movesOneStepAndKeepsEveryPositionContiguous() {
        assertTrue(service.moveDown(IngredientCategories.FRUIT.getId()));
        assertEquals(List.of("Gemüse", "Obst", "Sonstiges"), names());
        assertEquals(List.of(0, 1, 2), positions());

        assertTrue(service.moveUp(IngredientCategories.FRUIT.getId()));
        assertEquals(List.of("Obst", "Gemüse", "Sonstiges"), names());
        assertFalse(service.moveUp(IngredientCategories.FRUIT.getId()));
    }

    @Test
    void deletesOnlyMutableCategoryUsingFallbackAndNormalizedRemainingOrder() {
        repository.ingredientCounts.put(IngredientCategories.VEGETABLES.getId(), 3);

        assertEquals(3, service.countIngredients(IngredientCategories.VEGETABLES.getId()));
        service.delete(IngredientCategories.VEGETABLES.getId());

        assertEquals(List.of("Obst", "Sonstiges"), names());
        assertEquals(List.of(0, 1), positions());
        assertEquals(IngredientCategories.VEGETABLES.getId(), repository.deletedCategoryId);
        assertEquals(IngredientCategories.OTHER.getId(), repository.fallbackCategoryId);
        assertThrows(IllegalArgumentException.class,
                () -> service.delete(IngredientCategories.OTHER.getId()));
    }

    private List<String> names() {
        return service.loadCategories().stream().map(IngredientCategory::getName).toList();
    }

    private List<Integer> positions() {
        return service.loadCategories().stream().map(IngredientCategory::getPosition).toList();
    }

    private static final class InMemoryCategoryRepository
            implements IngredientCategoryRepository {
        private final List<IngredientCategory> categories;
        private final Map<UUID, Integer> ingredientCounts = new java.util.HashMap<>();
        private UUID deletedCategoryId;
        private UUID fallbackCategoryId;

        private InMemoryCategoryRepository(List<IngredientCategory> categories) {
            this.categories = new ArrayList<>(categories);
        }

        @Override public void save(IngredientCategory category) {
            categories.removeIf(existing -> existing.getId().equals(category.getId()));
            categories.add(category);
        }
        @Override public void replaceAll(List<IngredientCategory> replacement) {
            categories.clear();
            categories.addAll(replacement);
        }
        @Override public Optional<IngredientCategory> findById(UUID id) {
            return categories.stream().filter(category -> category.getId().equals(id)).findFirst();
        }
        @Override public List<IngredientCategory> findAll() { return List.copyOf(categories); }
        @Override public int countIngredients(UUID categoryId) {
            return ingredientCounts.getOrDefault(categoryId, 0);
        }
        @Override public void deleteAndReassign(UUID categoryId, UUID fallbackId,
                                                List<IngredientCategory> remaining) {
            deletedCategoryId = categoryId;
            fallbackCategoryId = fallbackId;
            categories.clear();
            categories.addAll(remaining);
        }
    }
}
