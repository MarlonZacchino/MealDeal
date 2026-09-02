package de.mealdeal.persistence.sqlite;

import de.mealdeal.domain.ConsumptionItem;
import de.mealdeal.domain.InventoryConsumption;
import de.mealdeal.domain.InventoryItem;
import de.mealdeal.domain.Unit;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.persistence.repository.InventoryConsumptionRepository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** SQLite implementation of the atomic inventory-consumption boundary. */
public final class SqliteInventoryConsumptionRepository
        implements InventoryConsumptionRepository {

    private final SqliteDatabase database;

    public SqliteInventoryConsumptionRepository(SqliteDatabase database) {
        this.database = Objects.requireNonNull(database, "Database must not be null.");
    }

    @Override
    public boolean existsByMealPlanEntryId(UUID mealPlanEntryId) {
        Objects.requireNonNull(mealPlanEntryId, "Meal-plan entry ID must not be null.");
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(
                     "SELECT 1 FROM inventory_consumptions WHERE meal_plan_entry_id = ?")) {
            statement.setString(1, mealPlanEntryId.toString());
            try (var resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not inspect consumption history.", exception);
        }
    }

    @Override
    public List<InventoryConsumption> findAll() {
        String sql = """
                SELECT consumption.id, consumption.meal_plan_entry_id,
                       consumption.planned_date, consumption.processed_at,
                       item.position, item.ingredient_id, item.quantity, item.unit
                FROM inventory_consumptions consumption
                LEFT JOIN inventory_consumption_items item
                    ON item.consumption_id = consumption.id
                ORDER BY consumption.planned_date, consumption.id, item.position
                """;
        Map<UUID, ConsumptionBuilder> consumptions = new LinkedHashMap<>();
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql);
             var resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                UUID id = UUID.fromString(resultSet.getString("id"));
                ConsumptionBuilder builder = consumptions.computeIfAbsent(id,
                        ignored -> new ConsumptionBuilder(id,
                                UUID.fromString(resultSetValue(resultSet, "meal_plan_entry_id")),
                                LocalDate.parse(resultSetValue(resultSet, "planned_date")),
                                Instant.parse(resultSetValue(resultSet, "processed_at"))));
                String ingredientId = resultSet.getString("ingredient_id");
                if (ingredientId != null) {
                    builder.items.add(new ConsumptionItem(UUID.fromString(ingredientId),
                            new BigDecimal(resultSet.getString("quantity")),
                            Unit.valueOf(resultSet.getString("unit"))));
                }
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load consumption history.", exception);
        }
        return consumptions.values().stream().map(ConsumptionBuilder::build).toList();
    }

    @Override
    public void saveWithInventoryUpdates(InventoryConsumption consumption,
                                         List<InventoryItem> inventoryUpdates) {
        Objects.requireNonNull(consumption, "Consumption must not be null.");
        Objects.requireNonNull(inventoryUpdates, "Inventory updates must not be null.");
        if (inventoryUpdates.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Inventory updates must not contain null values.");
        }
        try (var connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                insertConsumption(connection, consumption);
                insertItems(connection, consumption);
                updateInventory(connection, inventoryUpdates);
                connection.commit();
            } catch (SQLException exception) {
                rollback(connection, exception);
                throw exception;
            }
        } catch (SQLException exception) {
            throw new PersistenceException(
                    "Could not persist inventory consumption atomically.", exception);
        }
    }

    private static void insertConsumption(Connection connection,
                                          InventoryConsumption consumption) throws SQLException {
        String sql = """
                INSERT INTO inventory_consumptions
                    (id, meal_plan_entry_id, planned_date, processed_at)
                VALUES (?, ?, ?, ?)
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, consumption.getId().toString());
            statement.setString(2, consumption.getMealPlanEntryId().toString());
            statement.setString(3, consumption.getPlannedDate().toString());
            statement.setString(4, consumption.getProcessedAt().toString());
            statement.executeUpdate();
        }
    }

    private static void insertItems(Connection connection,
                                    InventoryConsumption consumption) throws SQLException {
        String sql = """
                INSERT INTO inventory_consumption_items
                    (consumption_id, position, ingredient_id, quantity, unit)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (var statement = connection.prepareStatement(sql)) {
            for (int position = 0; position < consumption.getItems().size(); position++) {
                ConsumptionItem item = consumption.getItems().get(position);
                statement.setString(1, consumption.getId().toString());
                statement.setInt(2, position);
                statement.setString(3, item.ingredientId().toString());
                statement.setString(4, item.quantity().toPlainString());
                statement.setString(5, item.unit().name());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void updateInventory(Connection connection,
                                        List<InventoryItem> updates) throws SQLException {
        try (var statement = connection.prepareStatement(
                "UPDATE inventory_items SET quantity = ?, unit = ? WHERE id = ?")) {
            for (InventoryItem item : updates) {
                statement.setString(1, item.getQuantity().toPlainString());
                statement.setString(2, item.getUnit().name());
                statement.setString(3, item.getId().toString());
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Inventory item disappeared during consumption.");
                }
            }
        }
    }

    private static void rollback(Connection connection, SQLException original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            original.addSuppressed(rollbackException);
        }
    }

    private static String resultSetValue(java.sql.ResultSet resultSet, String column) {
        try {
            return resultSet.getString(column);
        } catch (SQLException exception) {
            throw new PersistenceException("Could not read consumption history.", exception);
        }
    }

    private static final class ConsumptionBuilder {
        private final UUID id;
        private final UUID entryId;
        private final LocalDate plannedDate;
        private final Instant processedAt;
        private final List<ConsumptionItem> items = new ArrayList<>();

        private ConsumptionBuilder(UUID id, UUID entryId, LocalDate plannedDate,
                                   Instant processedAt) {
            this.id = id;
            this.entryId = entryId;
            this.plannedDate = plannedDate;
            this.processedAt = processedAt;
        }

        private InventoryConsumption build() {
            return new InventoryConsumption(id, entryId, plannedDate, processedAt, items);
        }
    }
}
