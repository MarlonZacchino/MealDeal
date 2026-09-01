package de.mealdeal.persistence.sqlite;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

final class SqliteSchema {

    static final int CURRENT_VERSION = 6;

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

    private static final String VERSION_2_STATEMENT = """
            CREATE TABLE meal_plan_entries (
                id TEXT PRIMARY KEY NOT NULL,
                planned_date TEXT NOT NULL UNIQUE,
                recipe_id TEXT NOT NULL,
                serving_count INTEGER NOT NULL CHECK (serving_count > 0),
                FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE RESTRICT
            )
            """;

    private static final String[] VERSION_3_STATEMENTS = {
        "ALTER TABLE recipes ADD COLUMN preparation_time_minutes INTEGER "
                + "CHECK (preparation_time_minutes > 0)",
        "ALTER TABLE recipes ADD COLUMN cooking_time_minutes INTEGER "
                + "CHECK (cooking_time_minutes > 0)"
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
            version = 1;
        }
        if (version == 1) {
            createVersion2(connection);
            version = 2;
        }
        if (version == 2) {
            createVersion3(connection);
            version = 3;
        }
        if (version == 3) {
            createVersion4(connection);
            version = 4;
        }
        if (version == 4) {
            createVersion5(connection);
            version = 5;
        }
        if (version == 5) {
            createVersion6(connection);
        }
    }

    static int readVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery("PRAGMA user_version")) {
            return resultSet.getInt(1);
        }
    }

    static void createVersion1(Connection connection) throws SQLException {
        for (String sql : VERSION_1_STATEMENTS) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version = 1");
        }
    }

    static void createVersion2(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(VERSION_2_STATEMENT);
            statement.execute("PRAGMA user_version = 2");
        }
    }

    static void createVersion3(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String sql : VERSION_3_STATEMENTS) {
                statement.execute(sql);
            }
            statement.execute("PRAGMA user_version = 3");
        }
    }

    static void createVersion4(Connection connection) throws SQLException {
        String[] statements = {
            "ALTER TABLE recipes ADD COLUMN calories_kcal INTEGER CHECK (calories_kcal >= 0)",
            "ALTER TABLE recipes ADD COLUMN protein_grams TEXT",
            "ALTER TABLE recipes ADD COLUMN carbohydrate_grams TEXT",
            "ALTER TABLE recipes ADD COLUMN fat_grams TEXT"
        };
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
            statement.execute("PRAGMA user_version = 4");
        }
    }

    /**
     * Adds recipe categories and replaces the former one-entry-per-date table.
     *
     * <p>The table rebuild is necessary because SQLite cannot remove the old
     * {@code UNIQUE(planned_date)} constraint in place. Existing entries are
     * retained as main dishes with their original IDs and portions.</p>
     */
    static void createVersion5(Connection connection) throws SQLException {
        String[] statements = {
            "ALTER TABLE recipes ADD COLUMN dish_type TEXT NOT NULL DEFAULT 'MAIN' "
                    + "CHECK (dish_type IN ('MAIN', 'SIDE'))",
            """
            CREATE TABLE meal_plan_entries_v5 (
                id TEXT PRIMARY KEY NOT NULL,
                planned_date TEXT NOT NULL,
                recipe_id TEXT NOT NULL,
                serving_count INTEGER NOT NULL CHECK (serving_count > 0),
                meal_role TEXT NOT NULL CHECK (meal_role IN ('MAIN', 'SIDE')),
                position INTEGER NOT NULL CHECK (position >= 0),
                CHECK (meal_role = 'SIDE' OR position = 0),
                FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE RESTRICT
            )
            """,
            """
            INSERT INTO meal_plan_entries_v5
                (id, planned_date, recipe_id, serving_count, meal_role, position)
            SELECT id, planned_date, recipe_id, serving_count, 'MAIN', 0
            FROM meal_plan_entries
            """,
            "DROP TABLE meal_plan_entries",
            "ALTER TABLE meal_plan_entries_v5 RENAME TO meal_plan_entries",
            """
            CREATE UNIQUE INDEX meal_plan_one_main_per_date
            ON meal_plan_entries (planned_date)
            WHERE meal_role = 'MAIN'
            """,
            """
            CREATE UNIQUE INDEX meal_plan_side_position_per_date
            ON meal_plan_entries (planned_date, position)
            WHERE meal_role = 'SIDE'
            """
        };
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
            statement.execute("PRAGMA user_version = 5");
        }
    }

    /** Adds the nullable, independently entered baking time to recipes. */
    static void createVersion6(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE recipes ADD COLUMN baking_time_minutes INTEGER "
                    + "CHECK (baking_time_minutes > 0)");
            statement.execute("PRAGMA user_version = 6");
        }
    }
}
