package de.mealdeal.persistence.sqlite;

import de.mealdeal.domain.IngredientCategory;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.persistence.repository.IngredientCategoryRepository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, category.getId().toString());
            statement.setString(2, category.getName());
            statement.setInt(3, category.getPosition());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new PersistenceException("Could not save ingredient category.", exception);
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

    private static IngredientCategory readCategory(java.sql.ResultSet resultSet)
            throws SQLException {
        return new IngredientCategory(UUID.fromString(resultSet.getString("id")),
                resultSet.getString("name"), resultSet.getInt("position"));
    }
}
