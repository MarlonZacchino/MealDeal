package de.mealdeal.persistence.sqlite;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

final class SqliteSchema {

    static final int CURRENT_VERSION = 1;

    private static final String[] VERSION_1_STATEMENTS = {
        """
        CREATE TABLE ingredients (
            id TEXT PRIMARY KEY NOT NULL,
            name TEXT NOT NULL CHECK (length(trim(name)) > 0)
        )
        """,
        """
        CREATE TABLE tastes (
            id TEXT PRIMARY KEY NOT NULL,
            name TEXT NOT NULL CHECK (length(trim(name)) > 0)
        )
        """,
        """
        CREATE TABLE recipes (
            id TEXT PRIMARY KEY NOT NULL,
            name TEXT NOT NULL CHECK (length(trim(name)) > 0),
            standard_serving_count INTEGER NOT NULL CHECK (standard_serving_count > 0)
        )
        """,
        """
        CREATE TABLE recipe_ingredients (
            recipe_id TEXT NOT NULL,
            ingredient_id TEXT NOT NULL,
            quantity TEXT NOT NULL CHECK (length(trim(quantity)) > 0),
            unit TEXT NOT NULL,
            PRIMARY KEY (recipe_id, ingredient_id),
            FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE CASCADE,
            FOREIGN KEY (ingredient_id) REFERENCES ingredients(id) ON DELETE RESTRICT
        )
        """,
        """
        CREATE TABLE recipe_steps (
            recipe_id TEXT NOT NULL,
            position INTEGER NOT NULL CHECK (position > 0),
            description TEXT NOT NULL CHECK (length(trim(description)) > 0),
            PRIMARY KEY (recipe_id, position),
            FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE CASCADE
        )
        """,
        """
        CREATE TABLE recipe_tastes (
            recipe_id TEXT NOT NULL,
            taste_id TEXT NOT NULL,
            PRIMARY KEY (recipe_id, taste_id),
            FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE CASCADE,
            FOREIGN KEY (taste_id) REFERENCES tastes(id) ON DELETE RESTRICT
        )
        """
    };

    private SqliteSchema() {
    }

    static void migrate(Connection connection) throws SQLException {
        int version = readVersion(connection);
        if (version > CURRENT_VERSION) {
            throw new SQLException("Database schema version " + version
                    + " is newer than supported version " + CURRENT_VERSION + ".");
        }
        if (version == 0) {
            createVersion1(connection);
        }
    }

    static int readVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery("PRAGMA user_version")) {
            return resultSet.getInt(1);
        }
    }

    private static void createVersion1(Connection connection) throws SQLException {
        for (String sql : VERSION_1_STATEMENTS) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version = 1");
        }
    }
}
