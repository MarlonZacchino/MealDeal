package de.mealdeal.persistence.sqlite;

import de.mealdeal.domain.RecipeFeedback;
import de.mealdeal.domain.RecipeFeedbackValue;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.persistence.repository.RecipeFeedbackRepository;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** SQLite current-state persistence for recipe feedback. */
public final class SqliteRecipeFeedbackRepository implements RecipeFeedbackRepository {

    private final SqliteDatabase database;

    public SqliteRecipeFeedbackRepository(SqliteDatabase database) {
        this.database = Objects.requireNonNull(database, "Database must not be null.");
    }

    @Override
    public void save(RecipeFeedback feedback) {
        Objects.requireNonNull(feedback, "Recipe feedback must not be null.");
        String sql = """
                INSERT INTO recipe_feedback
                    (id, recipe_id, preference, rating, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(recipe_id) DO UPDATE SET
                    preference = excluded.preference,
                    rating = excluded.rating,
                    updated_at = excluded.updated_at
                WHERE recipe_feedback.id = excluded.id
                """;
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, feedback.getId().toString());
            statement.setString(2, feedback.getRecipeId().toString());
            if (feedback.getValue().isPresent()) {
                statement.setString(3, feedback.getValue().orElseThrow().name());
            } else {
                statement.setNull(3, java.sql.Types.VARCHAR);
            }
            if (feedback.getRating().isPresent()) {
                statement.setInt(4, feedback.getRating().getAsInt());
            } else {
                statement.setNull(4, java.sql.Types.INTEGER);
            }
            statement.setString(5, SqliteInstantCodec.format(feedback.getUpdatedAt()));
            if (statement.executeUpdate() != 1) {
                throw new PersistenceException(
                        "Recipe feedback identity must remain stable when it is updated.");
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not save recipe feedback.", exception);
        }
    }

    @Override
    public Optional<RecipeFeedback> findByRecipeId(UUID recipeId) {
        Objects.requireNonNull(recipeId, "Recipe ID must not be null.");
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(
                     "SELECT * FROM recipe_feedback WHERE recipe_id = ?")) {
            statement.setString(1, recipeId.toString());
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(read(resultSet));
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load recipe feedback.", exception);
        }
    }

    @Override
    public Map<UUID, RecipeFeedback> findByRecipeIds(Collection<UUID> recipeIds) {
        List<UUID> ids = checkedIds(recipeIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        String sql = "SELECT * FROM recipe_feedback WHERE recipe_id IN ("
                + String.join(", ", Collections.nCopies(ids.size(), "?"))
                + ") ORDER BY recipe_id";
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < ids.size(); index++) {
                statement.setString(index + 1, ids.get(index).toString());
            }
            try (var resultSet = statement.executeQuery()) {
                Map<UUID, RecipeFeedback> feedbackByRecipe = new LinkedHashMap<>();
                while (resultSet.next()) {
                    RecipeFeedback feedback = read(resultSet);
                    feedbackByRecipe.put(feedback.getRecipeId(), feedback);
                }
                return Collections.unmodifiableMap(feedbackByRecipe);
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load recipe feedback batch.", exception);
        }
    }

    @Override
    public boolean deleteByRecipeId(UUID recipeId) {
        Objects.requireNonNull(recipeId, "Recipe ID must not be null.");
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(
                     "DELETE FROM recipe_feedback WHERE recipe_id = ?")) {
            statement.setString(1, recipeId.toString());
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new PersistenceException("Could not delete recipe feedback.", exception);
        }
    }

    private static RecipeFeedback read(java.sql.ResultSet resultSet) throws SQLException {
        String value = resultSet.getString("preference");
        int rating = resultSet.getInt("rating");
        Integer nullableRating = resultSet.wasNull() ? null : rating;
        return new RecipeFeedback(
                UUID.fromString(resultSet.getString("id")),
                UUID.fromString(resultSet.getString("recipe_id")),
                value == null ? null : RecipeFeedbackValue.valueOf(value),
                nullableRating,
                SqliteInstantCodec.parse(resultSet.getString("updated_at")));
    }

    private static List<UUID> checkedIds(Collection<UUID> recipeIds) {
        Objects.requireNonNull(recipeIds, "Recipe IDs must not be null.");
        if (recipeIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Recipe IDs must not contain null values.");
        }
        return recipeIds.stream().distinct().sorted().toList();
    }
}
