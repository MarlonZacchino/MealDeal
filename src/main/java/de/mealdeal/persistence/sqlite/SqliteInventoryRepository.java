package de.mealdeal.persistence.sqlite;

import de.mealdeal.domain.Ingredient;
import de.mealdeal.domain.IngredientCategory;
import de.mealdeal.domain.InventoryItem;
import de.mealdeal.domain.Unit;
import de.mealdeal.persistence.DuplicateInventoryItemException;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.persistence.repository.InventoryRepository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** SQLite/JDBC implementation of the local inventory repository. */
public final class SqliteInventoryRepository implements InventoryRepository {

    private static final String SELECT_COLUMNS = """
            SELECT inventory.id, inventory.quantity, inventory.unit,
                   ingredient.id AS ingredient_id, ingredient.name AS ingredient_name,
                   category.id AS category_id, category.name AS category_name,
                   category.position AS category_position
            FROM inventory_items inventory
            JOIN ingredients ingredient ON ingredient.id = inventory.ingredient_id
            JOIN ingredient_categories category ON category.id = ingredient.category_id
            """;

    private final SqliteDatabase database;

    public SqliteInventoryRepository(SqliteDatabase database) {
        this.database = Objects.requireNonNull(database, "Database must not be null.");
    }

    @Override
    public void save(InventoryItem item) {
        Objects.requireNonNull(item, "Inventory item must not be null.");
        String sql = """
                INSERT INTO inventory_items (id, ingredient_id, quantity, unit)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    ingredient_id = excluded.ingredient_id,
                    quantity = excluded.quantity,
                    unit = excluded.unit
                """;
        try (var connection = database.openConnection()) {
            rejectDuplicate(connection, item);
            try (var statement = connection.prepareStatement(sql)) {
                statement.setString(1, item.getId().toString());
                statement.setString(2, item.getIngredient().getId().toString());
                statement.setString(3, item.getQuantity().toPlainString());
                statement.setString(4, item.getUnit().name());
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new PersistenceException(
                    "Could not save inventory item. Its ingredient must already exist.",
                    exception);
        }
    }

    private static void rejectDuplicate(java.sql.Connection connection, InventoryItem item)
            throws SQLException {
        String sql = """
                SELECT id FROM inventory_items
                WHERE ingredient_id = ? AND unit = ? AND id <> ?
                LIMIT 1
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, item.getIngredient().getId().toString());
            statement.setString(2, item.getUnit().name());
            statement.setString(3, item.getId().toString());
            try (var resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    throw new DuplicateInventoryItemException(
                            "Inventory already contains this ingredient and unit.");
                }
            }
        }
    }

    @Override
    public Optional<InventoryItem> findById(UUID id) {
        Objects.requireNonNull(id, "Inventory item ID must not be null.");
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(SELECT_COLUMNS
                     + " WHERE inventory.id = ?")) {
            statement.setString(1, id.toString());
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(readItem(resultSet)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load inventory item.", exception);
        }
    }

    @Override
    public List<InventoryItem> findAll() {
        String sql = SELECT_COLUMNS
                + " ORDER BY category.position, ingredient.name COLLATE NOCASE, inventory.id";
        List<InventoryItem> items = new ArrayList<>();
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql);
             var resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                items.add(readItem(resultSet));
            }
            return List.copyOf(items);
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load inventory.", exception);
        }
    }

    @Override
    public boolean deleteById(UUID id) {
        Objects.requireNonNull(id, "Inventory item ID must not be null.");
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(
                     "DELETE FROM inventory_items WHERE id = ?")) {
            statement.setString(1, id.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new PersistenceException("Could not delete inventory item.", exception);
        }
    }

    private static InventoryItem readItem(java.sql.ResultSet resultSet) throws SQLException {
        IngredientCategory category = new IngredientCategory(
                UUID.fromString(resultSet.getString("category_id")),
                resultSet.getString("category_name"),
                resultSet.getInt("category_position"));
        Ingredient ingredient = new Ingredient(
                UUID.fromString(resultSet.getString("ingredient_id")),
                resultSet.getString("ingredient_name"), category);
        return new InventoryItem(UUID.fromString(resultSet.getString("id")), ingredient,
                new BigDecimal(resultSet.getString("quantity")),
                Unit.valueOf(resultSet.getString("unit")));
    }
}
