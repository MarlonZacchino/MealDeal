package de.mealdeal.persistence.sqlite;

import de.mealdeal.domain.IngredientCategory;
import de.mealdeal.persistence.DuplicateIngredientCategoryException;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.persistence.repository.IngredientCategoryRepository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** SQLite/JDBC implementation of the central ingredient category catalog. */
public final class SqliteIngredientCategoryRepository
        implements IngredientCategoryRepository {

    private final SqliteDatabase database;

    public SqliteIngredientCategoryRepository(SqliteDatabase database) {
        this.database = Objects.requireNonNull(database, "Database must not be null.");
    }

    @Override
    public void save(IngredientCategory category) {
        Objects.requireNonNull(category, "Ingredient category must not be null.");
        String sql = """
                INSERT INTO ingredient_categories (id, name, position) VALUES (?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    name = excluded.name,
                    position = excluded.position
                """;
        try (var connection = database.openConnection()) {
            rejectDuplicateName(connection, category);
            try (var statement = connection.prepareStatement(sql)) {
                setCategory(statement, category);
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not save ingredient category.", exception);
        }
    }

    @Override
    public void replaceAll(List<IngredientCategory> categories) {
        List<IngredientCategory> validated = validateCatalog(categories);
        try (var connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requireCompleteCatalog(connection, validated, null);
                movePositionsOutOfTheWay(connection, validated.size());
                upsertAll(connection, validated);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not save ingredient category order.", exception);
        }
    }

    @Override
    public Optional<IngredientCategory> findById(UUID id) {
        Objects.requireNonNull(id, "Ingredient category ID must not be null.");
        String sql = "SELECT id, name, position FROM ingredient_categories WHERE id = ?";
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readCategory(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load ingredient category.", exception);
        }
    }

    @Override
    public List<IngredientCategory> findAll() {
        String sql = "SELECT id, name, position FROM ingredient_categories "
                + "ORDER BY position, name, id";
        List<IngredientCategory> categories = new ArrayList<>();
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql);
             var resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                categories.add(readCategory(resultSet));
            }
            return List.copyOf(categories);
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load ingredient categories.", exception);
        }
    }

    @Override
    public int countIngredients(UUID categoryId) {
        Objects.requireNonNull(categoryId, "Ingredient category ID must not be null.");
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM ingredients WHERE category_id = ?")) {
            statement.setString(1, categoryId.toString());
            try (var resultSet = statement.executeQuery()) {
                return resultSet.getInt(1);
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not count categorized ingredients.", exception);
        }
    }

    @Override
    public void deleteAndReassign(UUID categoryId, UUID fallbackCategoryId,
                                  List<IngredientCategory> remainingCategories) {
        Objects.requireNonNull(categoryId, "Ingredient category ID must not be null.");
        Objects.requireNonNull(fallbackCategoryId, "Fallback category ID must not be null.");
        if (categoryId.equals(fallbackCategoryId)) {
            throw new IllegalArgumentException("Fallback category must not be deleted.");
        }
        List<IngredientCategory> validated = validateCatalog(remainingCategories);
        try (var connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                requireExisting(connection, categoryId);
                requireExisting(connection, fallbackCategoryId);
                requireCompleteCatalog(connection, validated, categoryId);
                reassignIngredients(connection, categoryId, fallbackCategoryId);
                deleteCategory(connection, categoryId);
                movePositionsOutOfTheWay(connection, validated.size());
                upsertAll(connection, validated);
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new PersistenceException(
                    "Could not delete ingredient category atomically.", exception);
        }
    }

    private static List<IngredientCategory> validateCatalog(
            List<IngredientCategory> categories) {
        Objects.requireNonNull(categories, "Ingredient categories must not be null.");
        if (categories.isEmpty() || categories.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "Ingredient category catalog must not be empty or contain null values.");
        }
        Set<UUID> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        Set<Integer> positions = new HashSet<>();
        for (IngredientCategory category : categories) {
            if (!ids.add(category.getId())
                    || !names.add(category.getName().toLowerCase(java.util.Locale.ROOT))
                    || !positions.add(category.getPosition())) {
                throw new DuplicateIngredientCategoryException(
                        "Kategorienamen und Positionen müssen eindeutig sein.");
            }
        }
        return List.copyOf(categories);
    }

    private static void requireCompleteCatalog(java.sql.Connection connection,
                                               List<IngredientCategory> categories,
                                               UUID categoryBeingDeleted) throws SQLException {
        Set<UUID> suppliedIds = categories.stream()
                .map(IngredientCategory::getId).collect(java.util.stream.Collectors.toSet());
        try (var statement = connection.prepareStatement(
                "SELECT id FROM ingredient_categories");
             var resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                UUID existingId = UUID.fromString(resultSet.getString(1));
                if (!existingId.equals(categoryBeingDeleted) && !suppliedIds.contains(existingId)) {
                    throw new IllegalArgumentException(
                            "Complete ingredient category catalog is required.");
                }
            }
        }
    }

    private static void requireExisting(java.sql.Connection connection, UUID id)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM ingredient_categories WHERE id = ?")) {
            statement.setString(1, id.toString());
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new PersistenceException("Ingredient category does not exist.");
                }
            }
        }
    }

    private static void rejectDuplicateName(java.sql.Connection connection,
                                            IngredientCategory category) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT 1 FROM ingredient_categories
                WHERE name = ? COLLATE NOCASE AND id <> ?
                """)) {
            statement.setString(1, category.getName());
            statement.setString(2, category.getId().toString());
            try (var resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    throw new DuplicateIngredientCategoryException(
                            "Eine Kategorie mit diesem Namen existiert bereits.");
                }
            }
        }
    }

    private static void movePositionsOutOfTheWay(java.sql.Connection connection,
                                                 int categoryCount) throws SQLException {
        int maximumPosition;
        try (var statement = connection.prepareStatement(
                "SELECT COALESCE(MAX(position), 0) FROM ingredient_categories");
             var resultSet = statement.executeQuery()) {
            maximumPosition = resultSet.getInt(1);
        }
        try (var statement = connection.prepareStatement(
                "UPDATE ingredient_categories SET position = position + ?")) {
            statement.setInt(1, maximumPosition + categoryCount + 1);
            statement.executeUpdate();
        }
    }

    private static void upsertAll(java.sql.Connection connection,
                                  List<IngredientCategory> categories) throws SQLException {
        String sql = """
                INSERT INTO ingredient_categories (id, name, position) VALUES (?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET name = excluded.name, position = excluded.position
                """;
        try (var statement = connection.prepareStatement(sql)) {
            for (IngredientCategory category : categories) {
                setCategory(statement, category);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void setCategory(java.sql.PreparedStatement statement,
                                    IngredientCategory category) throws SQLException {
        statement.setString(1, category.getId().toString());
        statement.setString(2, category.getName());
        statement.setInt(3, category.getPosition());
    }

    private static void reassignIngredients(java.sql.Connection connection, UUID categoryId,
                                            UUID fallbackCategoryId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "UPDATE ingredients SET category_id = ? WHERE category_id = ?")) {
            statement.setString(1, fallbackCategoryId.toString());
            statement.setString(2, categoryId.toString());
            statement.executeUpdate();
        }
    }

    private static void deleteCategory(java.sql.Connection connection, UUID categoryId)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "DELETE FROM ingredient_categories WHERE id = ?")) {
            statement.setString(1, categoryId.toString());
            if (statement.executeUpdate() != 1) {
                throw new PersistenceException("Ingredient category does not exist.");
            }
        }
    }

    private static void rollback(java.sql.Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            original.addSuppressed(rollbackException);
        }
    }

    private static IngredientCategory readCategory(java.sql.ResultSet resultSet)
            throws SQLException {
        return new IngredientCategory(UUID.fromString(resultSet.getString("id")),
                resultSet.getString("name"), resultSet.getInt("position"));
    }
}
