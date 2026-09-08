package de.mealdeal.persistence.sqlite;

import de.mealdeal.domain.RecommendationAction;
import de.mealdeal.domain.RecommendationInteraction;
import de.mealdeal.persistence.repository.RecommendationInteractionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteRecommendationInteractionRepositoryIntegrationTest {

    @TempDir Path temporaryDirectory;

    private RecommendationInteractionRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SqliteRecommendationInteractionRepository(
                new SqliteDatabase(temporaryDirectory.resolve("interactions.db")));
    }

    @Test
    void persistsAllActionsAndGroupsThemByLightweightSession() {
        UUID session = UUID.randomUUID();
        UUID recipeOne = UUID.randomUUID();
        UUID recipeTwo = UUID.randomUUID();
        RecommendationInteraction secondRank = interaction(session, recipeTwo,
                RecommendationAction.SHOWN, 2, "0.612300",
                Instant.parse("2026-09-03T10:00:01Z"));
        RecommendationInteraction firstRank = interaction(session, recipeOne,
                RecommendationAction.SHOWN, 1, "0.812300",
                Instant.parse("2026-09-03T10:00:00Z"));
        RecommendationInteraction selected = interaction(session, recipeOne,
                RecommendationAction.SELECTED, 1, "0.812300",
                Instant.parse("2026-09-03T10:00:02Z"));
        RecommendationInteraction dismissed = interaction(session, recipeTwo,
                RecommendationAction.DISMISSED, 2, "0.612300",
                Instant.parse("2026-09-03T10:00:03Z"));
        repository.save(secondRank);
        repository.save(firstRank);
        repository.save(selected);
        repository.save(dismissed);

        List<RecommendationInteraction> sessionEvents = repository.findBySessionId(session);
        assertEquals(List.of(firstRank.getId(), selected.getId(), secondRank.getId(),
                        dismissed.getId()),
                sessionEvents.stream().map(RecommendationInteraction::getId).toList());
        assertEquals(new BigDecimal("0.812300"), sessionEvents.getFirst().getDisplayedScore());
        assertEquals(List.of(selected.getId(), firstRank.getId()),
                repository.findByRecipeId(recipeOne).stream()
                        .map(RecommendationInteraction::getId).toList());
        var batch = repository.findByRecipeIds(List.of(
                recipeTwo, UUID.randomUUID(), recipeOne));
        assertEquals(List.of(selected.getId(), firstRank.getId()),
                batch.get(recipeOne).stream().map(RecommendationInteraction::getId).toList());
        assertEquals(List.of(dismissed.getId(), secondRank.getId()),
                batch.get(recipeTwo).stream().map(RecommendationInteraction::getId).toList());
        assertEquals(2, batch.size());
        assertEquals(List.of(dismissed.getId(), selected.getId()), repository.findRecent(2)
                .stream().map(RecommendationInteraction::getId).toList());
    }

    @Test
    void immutableInteractionCanBeExplicitlyDeleted() {
        RecommendationInteraction interaction = interaction(UUID.randomUUID(), UUID.randomUUID(),
                RecommendationAction.SHOWN, 1, "1.0",
                Instant.parse("2026-09-03T10:00:00Z"));
        repository.save(interaction);

        assertTrue(repository.deleteById(interaction.getId()));
        assertFalse(repository.deleteById(interaction.getId()));
        assertTrue(repository.findRecent(1).isEmpty());
    }

    private static RecommendationInteraction interaction(
            UUID session, UUID recipe, RecommendationAction action, int rank,
            String score, Instant occurredAt) {
        return new RecommendationInteraction(
                session, recipe, action, rank, new BigDecimal(score), occurredAt);
    }
}
