package de.mealdeal.persistence.sqlite;

import de.mealdeal.persistence.PersistenceException;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Creates short-lived SQLite connections and initializes the configured file.
 * Every connection explicitly enables foreign-key enforcement because SQLite
 * applies that setting per connection.
 */
public final class SqliteDatabase {

    private final String jdbcUrl;

    public SqliteDatabase(Path databasePath) {
        Objects.requireNonNull(databasePath, "Database path must not be null.");
        jdbcUrl = "jdbc:sqlite:" + databasePath.toAbsolutePath();
        initializeSchema();
    }

    public int getSchemaVersion() {
        try (Connection connection = openConnection()) {
            return SqliteSchema.readVersion(connection);
        } catch (SQLException exception) {
            throw new PersistenceException("Could not read database schema version.", exception);
        }
    }

    Connection openConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
        return connection;
    }

    private void initializeSchema() {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                SqliteSchema.migrate(connection);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not initialize SQLite database.", exception);
        }
    }
}
