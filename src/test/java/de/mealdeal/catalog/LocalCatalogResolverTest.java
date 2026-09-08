package de.mealdeal.catalog;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.IngredientCategories;
import de.mealdeal.domain.Taste;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalCatalogResolverTest {

    private final LocalCatalogResolver resolver = new LocalCatalogResolver();

    @Test
    void explicitCatalogLinkWinsAndKeepsLocalIdentityIndependent() {
        CatalogIngredient tomato = StandardIngredientCatalog.findById("ingredient.tomato")
                .orElseThrow();
        Ingredient linked = new Ingredient("Meine Rispentomate", IngredientCategories.FRUIT,
                tomato.catalogId());
        Ingredient sameNameWithoutLink = new Ingredient("Tomate");

        LocalCatalogResolution<CatalogIngredient, Ingredient> result =
                resolver.resolveIngredient(tomato, List.of(sameNameWithoutLink, linked));

        assertEquals(LocalCatalogMatchType.CATALOG_ID, result.matchType());
        assertEquals(linked, result.existingLocalEntity().orElseThrow());
        assertNotEquals(linked.getId(), UUID.nameUUIDFromBytes(
                tomato.catalogId().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void aliasesAndNormalizationCanFindOneSafeLocalEntity() {
        CatalogIngredient tomato = StandardIngredientCatalog.findById("ingredient.tomato")
                .orElseThrow();

        var exactAlias = resolver.resolveIngredient(tomato,
                List.of(new Ingredient("Tomaten")));
        var normalized = resolver.resolveIngredient(tomato,
                List.of(new Ingredient(" TOMATE ")));

        assertEquals(LocalCatalogMatchType.EXACT_ALIAS, exactAlias.matchType());
        assertEquals(LocalCatalogMatchType.NORMALIZED_CANONICAL_NAME, normalized.matchType());
    }

    @Test
    void ambiguousLocalMatchesAreReportedWithoutAutomaticMerge() {
        CatalogTaste sweet = StandardTasteCatalog.findById("taste.sweet").orElseThrow();

        LocalCatalogResolution<CatalogTaste, Taste> result = resolver.resolveTaste(
                sweet, List.of(new Taste("Suess"), new Taste("SUESS")));

        assertEquals(LocalCatalogMatchType.AMBIGUOUS, result.matchType());
        assertEquals(2, result.localCandidates().size());
        assertTrue(result.existingLocalEntity().isEmpty());
    }

    @Test
    void localChangesCannotMutateCatalogData() {
        CatalogIngredient tomato = StandardIngredientCatalog.findById("ingredient.tomato")
                .orElseThrow();
        UUID localId = UUID.randomUUID();
        Ingredient original = new Ingredient(localId, "Tomate", IngredientCategories.VEGETABLES,
                tomato.catalogId());
        Ingredient renamed = new Ingredient(localId, "Eigene Tomate", IngredientCategories.FRUIT,
                original.getCatalogId().orElseThrow());

        assertEquals(original, renamed);
        assertEquals("Eigene Tomate", renamed.getName());
        assertEquals(IngredientCategories.FRUIT, renamed.getCategory());
        assertEquals("Tomate", tomato.displayName());
        assertEquals(CatalogIngredientCategory.VEGETABLES, tomato.category());
    }
}
