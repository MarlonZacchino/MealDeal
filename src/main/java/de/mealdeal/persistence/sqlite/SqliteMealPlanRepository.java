package de.mealdeal.persistence.sqlite;

import de.mealdeal.domain.MealPlanEntry;
import de.mealdeal.domain.MealRole;
import de.mealdeal.domain.Recipe;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.persistence.repository.MealPlanRepository;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** SQLite/JDBC implementation of {@link MealPlanRepository}. */
public final class SqliteMealPlanRepository implements MealPlanRepository {

    private static final String SELECT_COLUMNS =
            "SELECT id, planned_date, recipe_id, serving_count, meal_role, position "
                    + "FROM meal_plan_entries";
    private static final String SAVE_SQL = """
            INSERT INTO meal_plan_entries
                (id, planned_date, recipe_id, serving_count, meal_role, position)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                planned_date = excluded.planned_date,
                recipe_id = excluded.recipe_id,
                serving_count = excluded.serving_count,
                meal_role = excluded.meal_role,
                position = excluded.position
            """;

    private final SqliteDatabase database;
    private final SqliteRecipeRepository recipeRepository;

    public SqliteMealPlanRepository(SqliteDatabase database) {
        this.database = Objects.requireNonNull(database, "Database must not be null.");
        this.recipeRepository = new SqliteRecipeRepository(database);
    }

    @Override
    public void save(MealPlanEntry entry) {
        Objects.requireNonNull(entry, "Meal plan entry must not be null.");
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(SAVE_SQL)) {
            save(entry, statement);
        } catch (SQLException exception) {
            throw new PersistenceException(
                    "Could not save meal plan entry. Its recipe must already exist.", exception);
        }
    }

    @Override
    public void applyChanges(List<MealPlanEntry> entriesToSave, List<UUID> entryIdsToDelete) {
        Objects.requireNonNull(entriesToSave, "Entries to save must not be null.");
        Objects.requireNonNull(entryIdsToDelete, "Entry IDs to delete must not be null.");
        entriesToSave.forEach(entry -> Objects.requireNonNull(
                entry, "Meal plan entry to save must not be null."));
        entryIdsToDelete.forEach(id -> Objects.requireNonNull(
                id, "Meal plan entry ID to delete must not be null."));

        try (var connection = database.openConnection();
             var deleteStatement = connection.prepareStatement(
                     "DELETE FROM meal_plan_entries WHERE id = ?");
             var saveStatement = connection.prepareStatement(SAVE_SQL)) {
            connection.setAutoCommit(false);
            try {
                for (UUID entryId : entryIdsToDelete) {
                    deleteStatement.setString(1, entryId.toString());
                    deleteStatement.executeUpdate();
                }
                // Releasing only the changed IDs prevents a temporary unique-index
                // collision when two persisted SIDE positions are swapped. The same
                // transaction immediately recreates them with their stable UUIDs.
                for (MealPlanEntry entry : entriesToSave) {
                    deleteStatement.setString(1, entry.getId().toString());
                    deleteStatement.executeUpdate();
                }
                for (MealPlanEntry entry : entriesToSave) {
                    save(entry, saveStatement);
                }
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw new PersistenceException(
                        "Could not save weekly meal plan changes. No changes were persisted.",
                        exception);
            }
        } catch (SQLException exception) {
            throw new PersistenceException(
                    "Could not save weekly meal plan changes. No changes were persisted.",
                    exception);
        }
    }

    @Override
    public Optional<MealPlanEntry> findById(UUID id) {
        Objects.requireNonNull(id, "Meal plan entry ID must not be null.");
        return findOne(SELECT_COLUMNS + " WHERE id = ?", id.toString());
    }

    @Override
    public Optional<MealPlanEntry> findByDate(LocalDate date) {
        Objects.requireNonNull(date, "Meal plan date must not be null.");
        return findOne(SELECT_COLUMNS + " WHERE planned_date = ? AND meal_role = 'MAIN'",
                date.toString());
    }

    @Override
    public List<MealPlanEntry> findBetween(LocalDate startInclusive, LocalDate endInclusive) {
        Objects.requireNonNull(startInclusive, "Start date must not be null.");
        Objects.requireNonNull(endInclusive, "End date must not be null.");
        if (endInclusive.isBefore(startInclusive)) {
            throw new IllegalArgumentException("End date must not be before start date.");
        }

        String sql = SELECT_COLUMNS
                + " WHERE planned_date BETWEEN ? AND ?"
                + " ORDER BY planned_date,"
                + " CASE meal_role WHEN 'MAIN' THEN 0 ELSE 1 END, position, id";
        List<EntryRow> rows = new ArrayList<>();
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, startInclusive.toString());
            statement.setString(2, endInclusive.toString());
            try (var resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(readRow(resultSet));
                }
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load meal plan date range.", exception);
        }
        return rows.stream().map(this::toDomainEntry).toList();
    }

    @Override
    public boolean deleteById(UUID id) {
        Objects.requireNonNull(id, "Meal plan entry ID must not be null.");
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(
                     "DELETE FROM meal_plan_entries WHERE id = ?")) {
            statement.setString(1, id.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new PersistenceException("Could not delete meal plan entry.", exception);
        }
    }

    @Override
    public int deleteBefore(LocalDate cutoffExclusive) {
        Objects.requireNonNull(cutoffExclusive, "Cleanup cutoff date must not be null.");
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(
                     "DELETE FROM meal_plan_entries WHERE planned_date < ?")) {
            statement.setString(1, cutoffExclusive.toString());
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new PersistenceException("Could not clean up meal plan entries.", exception);
        }
    }

    private Optional<MealPlanEntry> findOne(String sql, String parameter) {
        EntryRow row;
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                row = readRow(resultSet);
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load meal plan entry.", exception);
        }
        return Optional.of(toDomainEntry(row));
    }

    private static void save(MealPlanEntry entry, java.sql.PreparedStatement statement)
            throws SQLException {
        statement.setString(1, entry.getId().toString());
        statement.setString(2, entry.getDate().toString());
        statement.setString(3, entry.getRecipe().getId().toString());
        statement.setInt(4, entry.getServingCount());
        statement.setString(5, entry.getMealRole().name());
        statement.setInt(6, entry.getPosition());
        statement.executeUpdate();
    }

    private static void rollback(java.sql.Connection connection, SQLException exception) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            exception.addSuppressed(rollbackException);
        }
    }

    private MealPlanEntry toDomainEntry(EntryRow row) {
        Recipe recipe = recipeRepository.findById(row.recipeId())
                .orElseThrow(() -> new PersistenceException(
                        "Meal plan entry references an unknown recipe."));
        return new MealPlanEntry(row.id(), row.date(), recipe, row.servingCount(),
                row.mealRole(), row.position());
    }

    private static EntryRow readRow(java.sql.ResultSet resultSet) throws SQLException {
        return new EntryRow(
                UUID.fromString(resultSet.getString("id")),
                LocalDate.parse(resultSet.getString("planned_date")),
                UUID.fromString(resultSet.getString("recipe_id")),
                resultSet.getInt("serving_count"),
                MealRole.valueOf(resultSet.getString("meal_role")),
                resultSet.getInt("position"));
    }

    private record EntryRow(UUID id, LocalDate date, UUID recipeId, int servingCount,
                            MealRole mealRole, int position) {
    }
}
