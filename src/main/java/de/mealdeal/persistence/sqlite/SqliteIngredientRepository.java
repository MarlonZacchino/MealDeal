package de.mealdeal.persistence.sqlite;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.persistence.repository.IngredientRepository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** SQLite/JDBC implementation of {@link IngredientRepository}. */
public final class SqliteIngredientRepository implements IngredientRepository {

    private final SqliteDatabase database;

    public SqliteIngredientRepository(SqliteDatabase database) {
        this.database = Objects.requireNonNull(database, "Database must not be null.");
    }

    @Override
    public void save(Ingredient ingredient) {
        Objects.requireNonNull(ingredient, "Ingredient must not be null.");
        String sql = """
                INSERT INTO ingredients (id, name) VALUES (?, ?)
                ON CONFLICT(id) DO UPDATE SET name = excluded.name
                """;
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, ingredient.getId().toString());
            statement.setString(2, ingredient.getName());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new PersistenceException("Could not save ingredient.", exception);
        }
    }

    @Override
    public Optional<Ingredient> findById(UUID id) {
        Objects.requireNonNull(id, "Ingredient ID must not be null.");
        String sql = "SELECT id, name FROM ingredients WHERE id = ?";
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Ingredient(
                        UUID.fromString(resultSet.getString("id")), resultSet.getString("name")));
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load ingredient.", exception);
        }
    }

    @Override
    public List<Ingredient> findAll() {
        String sql = "SELECT id, name FROM ingredients ORDER BY name, id";
        List<Ingredient> ingredients = new ArrayList<>();
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql);
             var resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                ingredients.add(new Ingredient(UUID.fromString(resultSet.getString("id")),
                        resultSet.getString("name")));
            }
            return List.copyOf(ingredients);
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load ingredients.", exception);
        }
    }

    @Override
    public boolean deleteById(UUID id) {
        Objects.requireNonNull(id, "Ingredient ID must not be null.");
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement("DELETE FROM ingredients WHERE id = ?")) {
            statement.setString(1, id.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new PersistenceException("Could not delete ingredient.", exception);
        }
    }
}
