package de.mealdeal.persistence.sqlite;

import de.mealdeal.domain.MealHistoryEntry;
import de.mealdeal.domain.MealHistorySource;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.persistence.repository.MealHistoryRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** SQLite persistence for immutable meal-history events. */
public final class SqliteMealHistoryRepository implements MealHistoryRepository {

    private final SqliteDatabase database;

    public SqliteMealHistoryRepository(SqliteDatabase database) {
        this.database = Objects.requireNonNull(database, "Database must not be null.");
    }

    @Override
    public void save(MealHistoryEntry entry) {
        Objects.requireNonNull(entry, "Meal history entry must not be null.");
        boolean mealPlanSource = entry.getSource() == MealHistorySource.MEAL_PLAN;
        String conflictClause = mealPlanSource
                ? " ON CONFLICT(source_meal_plan_entry_id) DO NOTHING" : "";
        String sql = """
                INSERT INTO meal_history (
                    id, recipe_id, recipe_name, occurred_at, servings, source,
                    source_meal_plan_entry_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """ + conflictClause;
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, entry.getId().toString());
            statement.setString(2, entry.getRecipeId().toString());
            statement.setString(3, entry.getRecipeName());
            statement.setString(4, SqliteInstantCodec.format(entry.getOccurredAt()));
            statement.setInt(5, entry.getServings());
            statement.setString(6, entry.getSource().name());
            if (entry.getSourceMealPlanEntryId().isPresent()) {
                statement.setString(7, entry.getSourceMealPlanEntryId().orElseThrow().toString());
            } else {
                statement.setNull(7, java.sql.Types.VARCHAR);
            }
            statement.setString(8, SqliteInstantCodec.format(entry.getCreatedAt()));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new PersistenceException("Could not save meal history.", exception);
        }
    }

    @Override
    public Optional<MealHistoryEntry> findById(UUID id) {
        return findOne("SELECT * FROM meal_history WHERE id = ?",
                Objects.requireNonNull(id, "Meal history ID must not be null."));
    }

    @Override
    public Optional<MealHistoryEntry> findBySourceMealPlanEntryId(UUID mealPlanEntryId) {
        return findOne("SELECT * FROM meal_history WHERE source_meal_plan_entry_id = ?",
                Objects.requireNonNull(
                        mealPlanEntryId, "Meal-plan entry ID must not be null."));
    }

    @Override
    public List<MealHistoryEntry> findRecent(int limit) {
        requirePositiveLimit(limit);
        String sql = "SELECT * FROM meal_history "
                + "ORDER BY occurred_at DESC, created_at DESC, id LIMIT ?";
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            return readMany(statement.executeQuery());
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load recent meal history.", exception);
        }
    }

    @Override
    public List<MealHistoryEntry> findByRecipeId(UUID recipeId) {
        Objects.requireNonNull(recipeId, "Recipe ID must not be null.");
        String sql = "SELECT * FROM meal_history WHERE recipe_id = ? "
                + "ORDER BY occurred_at DESC, created_at DESC, id";
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, recipeId.toString());
            return readMany(statement.executeQuery());
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load recipe meal history.", exception);
        }
    }

    @Override
    public List<MealHistoryEntry> findBetween(Instant fromInclusive, Instant toExclusive) {
        requirePeriod(fromInclusive, toExclusive);
        String sql = "SELECT * FROM meal_history WHERE occurred_at >= ? AND occurred_at < ? "
                + "ORDER BY occurred_at DESC, created_at DESC, id";
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, SqliteInstantCodec.format(fromInclusive));
            statement.setString(2, SqliteInstantCodec.format(toExclusive));
            return readMany(statement.executeQuery());
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load meal history period.", exception);
        }
    }

    @Override
    public Optional<MealHistoryEntry> findLatestByRecipeId(UUID recipeId) {
        Objects.requireNonNull(recipeId, "Recipe ID must not be null.");
        String sql = "SELECT * FROM meal_history WHERE recipe_id = ? "
                + "ORDER BY occurred_at DESC, created_at DESC, id LIMIT 1";
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, recipeId.toString());
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(read(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load latest recipe history.", exception);
        }
    }

    @Override
    public Map<UUID, MealHistoryEntry> findLatestByRecipeIds(Collection<UUID> recipeIds) {
        List<UUID> ids = checkedIds(recipeIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        String sql = "SELECT * FROM meal_history WHERE recipe_id IN ("
                + placeholders(ids.size())
                + ") ORDER BY recipe_id, occurred_at DESC, created_at DESC, id";
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql)) {
            bindIds(statement, ids);
            try (var resultSet = statement.executeQuery()) {
                Map<UUID, MealHistoryEntry> latest = new LinkedHashMap<>();
                while (resultSet.next()) {
                    MealHistoryEntry entry = read(resultSet);
                    latest.putIfAbsent(entry.getRecipeId(), entry);
                }
                return Collections.unmodifiableMap(latest);
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load latest recipe history batch.", exception);
        }
    }

    @Override
    public long countByRecipeIdBetween(
            UUID recipeId, Instant fromInclusive, Instant toExclusive) {
        Objects.requireNonNull(recipeId, "Recipe ID must not be null.");
        requirePeriod(fromInclusive, toExclusive);
        String sql = "SELECT count(*) FROM meal_history "
                + "WHERE recipe_id = ? AND occurred_at >= ? AND occurred_at < ?";
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, recipeId.toString());
            statement.setString(2, SqliteInstantCodec.format(fromInclusive));
            statement.setString(3, SqliteInstantCodec.format(toExclusive));
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not count recipe meal history.", exception);
        }
    }

    @Override
    public boolean existsByRecipeId(UUID recipeId) {
        Objects.requireNonNull(recipeId, "Recipe ID must not be null.");
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(
                     "SELECT 1 FROM meal_history WHERE recipe_id = ? LIMIT 1")) {
            statement.setString(1, recipeId.toString());
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not inspect meal history.", exception);
        }
    }

    @Override
    public boolean deleteById(UUID id) {
        Objects.requireNonNull(id, "Meal history ID must not be null.");
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(
                     "DELETE FROM meal_history WHERE id = ?")) {
            statement.setString(1, id.toString());
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new PersistenceException("Could not delete meal history.", exception);
        }
    }

    private Optional<MealHistoryEntry> findOne(String sql, UUID parameter) {
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter.toString());
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(read(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load meal history.", exception);
        }
    }

    private static List<MealHistoryEntry> readMany(ResultSet resultSet) throws SQLException {
        try (resultSet) {
            List<MealHistoryEntry> entries = new ArrayList<>();
            while (resultSet.next()) {
                entries.add(read(resultSet));
            }
            return List.copyOf(entries);
        }
    }

    private static MealHistoryEntry read(ResultSet resultSet) throws SQLException {
        String mealPlanEntryId = resultSet.getString("source_meal_plan_entry_id");
        return new MealHistoryEntry(
                UUID.fromString(resultSet.getString("id")),
                UUID.fromString(resultSet.getString("recipe_id")),
                resultSet.getString("recipe_name"),
                SqliteInstantCodec.parse(resultSet.getString("occurred_at")),
                resultSet.getInt("servings"),
                MealHistorySource.valueOf(resultSet.getString("source")),
                mealPlanEntryId == null ? null : UUID.fromString(mealPlanEntryId),
                SqliteInstantCodec.parse(resultSet.getString("created_at")));
    }

    private static void requirePositiveLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("History query limit must be greater than zero.");
        }
    }

    private static void requirePeriod(Instant fromInclusive, Instant toExclusive) {
        Objects.requireNonNull(fromInclusive, "Start timestamp must not be null.");
        Objects.requireNonNull(toExclusive, "End timestamp must not be null.");
        if (!fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException("History period start must be before its end.");
        }
    }

    private static List<UUID> checkedIds(Collection<UUID> recipeIds) {
        Objects.requireNonNull(recipeIds, "Recipe IDs must not be null.");
        if (recipeIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Recipe IDs must not contain null values.");
        }
        return recipeIds.stream().distinct().sorted().toList();
    }

    private static String placeholders(int count) {
        return String.join(", ", Collections.nCopies(count, "?"));
    }

    private static void bindIds(java.sql.PreparedStatement statement, List<UUID> ids)
            throws SQLException {
        for (int index = 0; index < ids.size(); index++) {
            statement.setString(index + 1, ids.get(index).toString());
        }
    }
}
