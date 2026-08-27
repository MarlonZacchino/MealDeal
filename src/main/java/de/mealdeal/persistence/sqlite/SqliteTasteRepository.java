package de.mealdeal.persistence.sqlite;

import de.mealdeal.domain.Taste;
import de.mealdeal.persistence.PersistenceException;
import de.mealdeal.persistence.repository.TasteRepository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** SQLite/JDBC implementation of {@link TasteRepository}. */
public final class SqliteTasteRepository implements TasteRepository {

    private final SqliteDatabase database;

    public SqliteTasteRepository(SqliteDatabase database) {
        this.database = Objects.requireNonNull(database, "Database must not be null.");
    }

    @Override
    public void save(Taste taste) {
        Objects.requireNonNull(taste, "Taste must not be null.");
        String sql = """
                INSERT INTO tastes (id, name) VALUES (?, ?)
                ON CONFLICT(id) DO UPDATE SET name = excluded.name
                """;
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, taste.getId().toString());
            statement.setString(2, taste.getName());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new PersistenceException("Could not save taste.", exception);
        }
    }

    @Override
    public Optional<Taste> findById(UUID id) {
        Objects.requireNonNull(id, "Taste ID must not be null.");
        String sql = "SELECT id, name FROM tastes WHERE id = ?";
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            try (var resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Taste(UUID.fromString(resultSet.getString("id")),
                        resultSet.getString("name")));
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load taste.", exception);
        }
    }

    @Override
    public List<Taste> findAll() {
        String sql = "SELECT id, name FROM tastes ORDER BY name, id";
        List<Taste> tastes = new ArrayList<>();
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement(sql);
             var resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                tastes.add(new Taste(UUID.fromString(resultSet.getString("id")),
                        resultSet.getString("name")));
            }
            return List.copyOf(tastes);
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load tastes.", exception);
        }
    }

    @Override
    public boolean deleteById(UUID id) {
        Objects.requireNonNull(id, "Taste ID must not be null.");
        try (var connection = database.openConnection();
             var statement = connection.prepareStatement("DELETE FROM tastes WHERE id = ?")) {
            statement.setString(1, id.toString());
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new PersistenceException("Could not delete taste.", exception);
        }
    }
}
