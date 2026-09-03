package de.mealdeal.service;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.IngredientCategories;
import de.mealdeal.domain.IngredientCategory;
import de.mealdeal.persistence.repository.IngredientCategoryRepository;
import de.mealdeal.persistence.repository.IngredientRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IngredientManagementServiceTest {

    @Test
    void createsTrimmedCentralIngredientWithStableIdentityAndSelectedCategory() {
        MemoryIngredientRepository ingredients = new MemoryIngredientRepository();
        IngredientManagementService service = service(ingredients);

        Ingredient created = service.create(
                "  Pfirsich  ", IngredientCategories.FRUIT.getId());

        assertNotNull(created.getId());
        assertEquals("Pfirsich", created.getName());
        assertEquals(IngredientCategories.FRUIT, created.getCategory());
        assertEquals(created, ingredients.findById(created.getId()).orElseThrow());
    }

    @Test
    void createRejectsBlankNameMissingCategoryAndCaseInsensitiveDuplicate() {
        MemoryIngredientRepository ingredients = new MemoryIngredientRepository();
        ingredients.save(new Ingredient("Pfirsich", IngredientCategories.FRUIT));
        IngredientManagementService service = service(ingredients);

        assertThrows(IllegalArgumentException.class,
                () -> service.create("  ", IngredientCategories.FRUIT.getId()));
        assertThrows(IllegalArgumentException.class,
                () -> service.create("Apfel", null));
        assertThrows(IllegalArgumentException.class,
                () -> service.create("pFiRsIcH", IngredientCategories.FRUIT.getId()));
        assertEquals(1, ingredients.findAll().size());
    }

    @Test
    void renamesAndRecategorizesIngredientWithoutChangingItsIdentity() {
        MemoryIngredientRepository ingredients = new MemoryIngredientRepository();
        Ingredient original = new Ingredient("Tomate", IngredientCategories.VEGETABLES);
        ingredients.save(original);
        IngredientManagementService service = service(ingredients);

        Ingredient updated = service.update(original.getId(), "  Cherrytomate  ",
                IngredientCategories.FRUIT.getId());

        assertEquals(original.getId(), updated.getId());
        assertEquals("Cherrytomate", updated.getName());
        assertEquals(IngredientCategories.FRUIT, updated.getCategory());
        assertEquals(updated.getId(), ingredients.findById(original.getId()).orElseThrow().getId());
    }

    @Test
    void rejectsBlankAndCaseInsensitiveDuplicateNames() {
        MemoryIngredientRepository ingredients = new MemoryIngredientRepository();
        Ingredient tomato = new Ingredient("Tomate", IngredientCategories.VEGETABLES);
        Ingredient potato = new Ingredient("Kartoffel", IngredientCategories.VEGETABLES);
        ingredients.save(tomato);
        ingredients.save(potato);
        IngredientManagementService service = service(ingredients);

        assertThrows(IllegalArgumentException.class,
                () -> service.update(tomato.getId(), "  ", IngredientCategories.FRUIT.getId()));
        assertThrows(IllegalArgumentException.class,
                () -> service.update(tomato.getId(), "kArToFfEl",
                        IngredientCategories.FRUIT.getId()));
    }

    private static IngredientManagementService service(MemoryIngredientRepository ingredients) {
        IngredientCategoryRepository categories = new IngredientCategoryRepository() {
            @Override public void save(IngredientCategory category) { throw new UnsupportedOperationException(); }
            @Override public Optional<IngredientCategory> findById(UUID id) {
                return IngredientCategories.all().stream().filter(c -> c.getId().equals(id)).findFirst();
            }
            @Override public List<IngredientCategory> findAll() { return IngredientCategories.all(); }
            @Override public void replaceAll(List<IngredientCategory> values) { throw new UnsupportedOperationException(); }
            @Override public int countIngredients(UUID categoryId) { return 0; }
            @Override public void deleteAndReassign(UUID categoryId, UUID fallbackCategoryId,
                                                    List<IngredientCategory> values) {
                throw new UnsupportedOperationException();
            }
        };
        return new IngredientManagementService(ingredients,
                new IngredientCategoryService(categories));
    }

    private static final class MemoryIngredientRepository implements IngredientRepository {
        private final List<Ingredient> values = new ArrayList<>();
        @Override public void save(Ingredient ingredient) {
            values.removeIf(existing -> existing.getId().equals(ingredient.getId()));
            values.add(ingredient);
        }
        @Override public Optional<Ingredient> findById(UUID id) {
            return values.stream().filter(value -> value.getId().equals(id)).findFirst();
        }
        @Override public List<Ingredient> findAll() { return List.copyOf(values); }
        @Override public boolean deleteById(UUID id) { return false; }
    }
}
