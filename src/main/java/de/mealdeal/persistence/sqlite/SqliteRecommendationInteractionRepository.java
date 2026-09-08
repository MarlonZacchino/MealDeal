package de.mealdeal.persistence.sqlite;

import de.mealdeal.domain.RecommendationAction;
import de.mealdeal.domain.RecommendationInteraction;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.persistence.repository.RecommendationInteractionRepository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** SQLite persistence for immutable recommendation-interaction events. */
public final class SqliteRecommendationInteractionRepository
        implements RecommendationInteractionRepository {

    private final SqliteDatabase database;

    public SqliteRecommendationInteractionRepository(SqliteDatabase database) {
        this.database = Objects.requireNonNull(database, "Database must not be null.");
    }

    @Override
    public void save(RecommendationInteraction interaction) {
        Objects.requireNonNull(interaction, "Recommendation interaction must not be null.");
        String sql = """
                INSERT INTO recommendation_interactions
                    (id, session_id, recipe_id, action, displayed_rank,
                     displayed_score, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, interaction.getId().toString());
            statement.setString(2, interaction.getRecommendationSessionId().toString());
            statement.setString(3, interaction.getRecipeId().toString());
            statement.setString(4, interaction.getAction().name());
            statement.setInt(5, interaction.getDisplayedRank());
            statement.setString(6, interaction.getDisplayedScore().toPlainString());
            statement.setString(7, SqliteInstantCodec.format(interaction.getOccurredAt()));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new PersistenceException("Could not save recommendation interaction.", exception);
        }
    }

    @Override
    public List<RecommendationInteraction> findBySessionId(UUID sessionId) {
        Objects.requireNonNull(sessionId, "Recommendation session ID must not be null.");
        String sql = "SELECT * FROM recommendation_interactions WHERE session_id = ? "
                + "ORDER BY displayed_rank, occurred_at, id";
        return findByUuid(sql, sessionId, "Could not load recommendation session.");
    }

    @Override
    public List<RecommendationInteraction> findByRecipeId(UUID recipeId) {
        Objects.requireNonNull(recipeId, "Recipe ID must not be null.");
        String sql = "SELECT * FROM recommendation_interactions WHERE recipe_id = ? "
                + "ORDER BY occurred_at DESC, id";
        return findByUuid(sql, recipeId, "Could not load recipe interactions.");
    }

    @Override
    public Map<UUID, List<RecommendationInteraction>> findByRecipeIds(
            Collection<UUID> recipeIds) {
        List<UUID> ids = checkedIds(recipeIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        String sql = "SELECT * FROM recommendation_interactions WHERE recipe_id IN ("
                + String.join(", ", Collections.nCopies(ids.size(), "?"))
                + ") ORDER BY recipe_id, occurred_at DESC, id";
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < ids.size(); index++) {
                statement.setString(index + 1, ids.get(index).toString());
            }
            try (var resultSet = statement.executeQuery()) {
                Map<UUID, List<RecommendationInteraction>> mutable = new LinkedHashMap<>();
                while (resultSet.next()) {
                    RecommendationInteraction interaction = read(resultSet);
                    mutable.computeIfAbsent(interaction.getRecipeId(), ignored -> new ArrayList<>())
                            .add(interaction);
                }
                Map<UUID, List<RecommendationInteraction>> immutable = new LinkedHashMap<>();
                mutable.forEach((recipeId, events) ->
                        immutable.put(recipeId, List.copyOf(events)));
                return Collections.unmodifiableMap(immutable);
            }
        } catch (SQLException exception) {
            throw new PersistenceException(
                    "Could not load recommendation interaction batch.", exception);
        }
    }

    @Override
    public List<RecommendationInteraction> findRecent(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Interaction query limit must be greater than zero.");
        }
        String sql = "SELECT * FROM recommendation_interactions "
                + "ORDER BY occurred_at DESC, id LIMIT ?";
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            return readMany(statement.executeQuery());
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load recent interactions.", exception);
        }
    }

    @Override
    public boolean deleteById(UUID id) {
        Objects.requireNonNull(id, "Recommendation interaction ID must not be null.");
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(
                     "DELETE FROM recommendation_interactions WHERE id = ?")) {
            statement.setString(1, id.toString());
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new PersistenceException("Could not delete recommendation interaction.", exception);
        }
    }

    private List<RecommendationInteraction> findByUuid(
            String sql, UUID value, String errorMessage) {
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, value.toString());
            return readMany(statement.executeQuery());
        } catch (SQLException exception) {
            throw new PersistenceException(errorMessage, exception);
        }
    }

    private static List<RecommendationInteraction> readMany(ResultSet resultSet)
            throws SQLException {
        try (resultSet) {
            List<RecommendationInteraction> interactions = new ArrayList<>();
            while (resultSet.next()) {
                interactions.add(read(resultSet));
            }
            return List.copyOf(interactions);
        }
    }

    private static RecommendationInteraction read(ResultSet resultSet) throws SQLException {
        return new RecommendationInteraction(
                UUID.fromString(resultSet.getString("id")),
                UUID.fromString(resultSet.getString("session_id")),
                UUID.fromString(resultSet.getString("recipe_id")),
                RecommendationAction.valueOf(resultSet.getString("action")),
                resultSet.getInt("displayed_rank"),
                new BigDecimal(resultSet.getString("displayed_score")),
                SqliteInstantCodec.parse(resultSet.getString("occurred_at")));
    }

    private static List<UUID> checkedIds(Collection<UUID> recipeIds) {
        Objects.requireNonNull(recipeIds, "Recipe IDs must not be null.");
        if (recipeIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Recipe IDs must not contain null values.");
        }
        return recipeIds.stream().distinct().sorted().toList();
    }
}
