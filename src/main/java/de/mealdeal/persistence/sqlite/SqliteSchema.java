package de.mealdeal.persistence.sqlite;

import de.mealdeal.domain.IngredientCategories;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

final class SqliteSchema {

    static final int CURRENT_VERSION = 13;

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
            version = 6;
        }
        if (version == 6) {
            createVersion7(connection);
            version = 7;
        }
        if (version == 7) {
            createVersion8(connection);
            version = 8;
        }
        if (version == 8) {
            createVersion9(connection);
            version = 9;
        }
        if (version == 9) {
            createVersion10(connection);
            version = 10;
        }
        if (version == 10) {
            createVersion11(connection);
            version = 11;
        }
        if (version == 11) {
            createVersion12(connection);
            version = 12;
        }
        if (version == 12) {
            createVersion13(connection);
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

    /**
     * Replaces flat recipe ingredients with ordered groups and ordered options.
     *
     * <p>The composite deferred foreign key guarantees that a group's required
     * default option belongs to that same group. Deferral resolves the intentional
     * insert cycle between a group and its options without weakening the committed
     * database state.</p>
     */
    static void createVersion7(Connection connection) throws SQLException {
        String uuidExpression = "lower(hex(randomblob(4))) || '-' || "
                + "lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))), 2) "
                + "|| '-' || substr('89ab', abs(random() % 4) + 1, 1) "
                + "|| substr(lower(hex(randomblob(2))), 2) || '-' || lower(hex(randomblob(6)))";
        String[] statements = {
            """
            CREATE TABLE recipe_ingredient_groups (
                id TEXT PRIMARY KEY NOT NULL,
                recipe_id TEXT NOT NULL,
                position INTEGER NOT NULL CHECK (position >= 0),
                default_option_id TEXT NOT NULL,
                UNIQUE (recipe_id, position),
                FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE CASCADE,
                FOREIGN KEY (id, default_option_id)
                    REFERENCES recipe_ingredient_options(group_id, id)
                    DEFERRABLE INITIALLY DEFERRED
            )
            """,
            """
            CREATE TABLE recipe_ingredient_options (
                id TEXT PRIMARY KEY NOT NULL,
                group_id TEXT NOT NULL,
                ingredient_id TEXT NOT NULL,
                quantity TEXT NOT NULL CHECK (length(trim(quantity)) > 0),
                unit TEXT NOT NULL,
                position INTEGER NOT NULL CHECK (position >= 0),
                UNIQUE (group_id, id),
                UNIQUE (group_id, position),
                FOREIGN KEY (group_id) REFERENCES recipe_ingredient_groups(id)
                    ON DELETE CASCADE,
                FOREIGN KEY (ingredient_id) REFERENCES ingredients(id)
                    ON DELETE RESTRICT
            )
            """,
            """
            CREATE TEMP TABLE recipe_ingredient_migration_v7 (
                group_id TEXT NOT NULL,
                option_id TEXT NOT NULL,
                recipe_id TEXT NOT NULL,
                ingredient_id TEXT NOT NULL,
                quantity TEXT NOT NULL,
                unit TEXT NOT NULL,
                group_position INTEGER NOT NULL
            )
            """,
            "INSERT INTO recipe_ingredient_migration_v7 "
                    + "SELECT " + uuidExpression + ", " + uuidExpression + ", "
                    + "ri.recipe_id, ri.ingredient_id, ri.quantity, ri.unit, "
                    + "row_number() OVER (PARTITION BY ri.recipe_id ORDER BY i.name, i.id) - 1 "
                    + "FROM recipe_ingredients ri "
                    + "JOIN ingredients i ON i.id = ri.ingredient_id",
            """
            INSERT INTO recipe_ingredient_groups (id, recipe_id, position, default_option_id)
            SELECT group_id, recipe_id, group_position, option_id
            FROM recipe_ingredient_migration_v7
            """,
            """
            INSERT INTO recipe_ingredient_options
                (id, group_id, ingredient_id, quantity, unit, position)
            SELECT option_id, group_id, ingredient_id, quantity, unit, 0
            FROM recipe_ingredient_migration_v7
            """,
            "DROP TABLE recipe_ingredients",
            "DROP TABLE recipe_ingredient_migration_v7"
        };
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
            statement.execute("PRAGMA user_version = 7");
        }
    }

    /** Stores optional per-entry choices for multi-option recipe ingredient groups. */
    static void createVersion8(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE meal_plan_ingredient_selections (
                        meal_plan_entry_id TEXT NOT NULL,
                        ingredient_group_id TEXT NOT NULL,
                        ingredient_option_id TEXT NOT NULL,
                        PRIMARY KEY (meal_plan_entry_id, ingredient_group_id),
                        FOREIGN KEY (meal_plan_entry_id) REFERENCES meal_plan_entries(id)
                            ON DELETE CASCADE,
                        FOREIGN KEY (ingredient_group_id, ingredient_option_id)
                            REFERENCES recipe_ingredient_options(group_id, id)
                            DEFERRABLE INITIALLY DEFERRED
                    )
                    """);
            statement.execute("PRAGMA user_version = 8");
        }
    }

    /** Adds the ordered category catalog and assigns old ingredients to Sonstiges. */
    static void createVersion9(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE ingredient_categories (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL UNIQUE CHECK (length(trim(name)) > 0),
                        position INTEGER NOT NULL UNIQUE CHECK (position >= 0)
                    )
                    """);
        }
        try (var insert = connection.prepareStatement(
                "INSERT INTO ingredient_categories (id, name, position) VALUES (?, ?, ?)")) {
            for (var category : IngredientCategories.all()) {
                insert.setString(1, category.getId().toString());
                insert.setString(2, category.getName());
                insert.setInt(3, category.getPosition());
                insert.addBatch();
            }
            insert.executeBatch();
        }

        String[] statements = {
            "CREATE TEMP TABLE ingredients_migration_v9 AS SELECT id, name FROM ingredients",
            """
            CREATE TEMP TABLE recipe_ingredient_options_migration_v9 AS
            SELECT id, group_id, ingredient_id, quantity, unit, position
            FROM recipe_ingredient_options
            """,
            "DELETE FROM recipe_ingredient_options",
            "DROP TABLE ingredients",
            """
            CREATE TABLE ingredients (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL CHECK (length(trim(name)) > 0),
                category_id TEXT NOT NULL,
                FOREIGN KEY (category_id) REFERENCES ingredient_categories(id)
                    ON DELETE RESTRICT
            )
            """,
            "INSERT INTO ingredients (id, name, category_id) SELECT id, name, '"
                    + IngredientCategories.OTHER.getId() + "' FROM ingredients_migration_v9",
            """
            INSERT INTO recipe_ingredient_options
                (id, group_id, ingredient_id, quantity, unit, position)
            SELECT id, group_id, ingredient_id, quantity, unit, position
            FROM recipe_ingredient_options_migration_v9
            """,
            "DROP TABLE recipe_ingredient_options_migration_v9",
            "DROP TABLE ingredients_migration_v9"
        };
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
            statement.execute("PRAGMA user_version = 9");
        }
    }

    /** Adds persistent local inventory without changing existing application data. */
    static void createVersion10(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE inventory_items (
                        id TEXT PRIMARY KEY NOT NULL,
                        ingredient_id TEXT NOT NULL,
                        quantity TEXT NOT NULL CHECK (length(trim(quantity)) > 0),
                        unit TEXT NOT NULL,
                        FOREIGN KEY (ingredient_id) REFERENCES ingredients(id)
                            ON DELETE RESTRICT
                    )
                    """);
            statement.execute("PRAGMA user_version = 10");
        }
    }

    /** Adds DESSERT recipe and meal-plan roles while preserving all existing data. */
    static void createVersion11(Connection connection) throws SQLException {
        String[] statements = {
            "CREATE TEMP TABLE recipes_migration_v11 AS SELECT * FROM recipes",
            """
            CREATE TEMP TABLE groups_migration_v11 AS
            SELECT id, recipe_id, position, default_option_id
            FROM recipe_ingredient_groups
            """,
            """
            CREATE TEMP TABLE options_migration_v11 AS
            SELECT id, group_id, ingredient_id, quantity, unit, position
            FROM recipe_ingredient_options
            """,
            """
            CREATE TEMP TABLE steps_migration_v11 AS
            SELECT recipe_id, position, description FROM recipe_steps
            """,
            """
            CREATE TEMP TABLE tastes_migration_v11 AS
            SELECT recipe_id, taste_id FROM recipe_tastes
            """,
            """
            CREATE TEMP TABLE meal_entries_migration_v11 AS
            SELECT id, planned_date, recipe_id, serving_count, meal_role, position
            FROM meal_plan_entries
            """,
            """
            CREATE TEMP TABLE meal_selections_migration_v11 AS
            SELECT meal_plan_entry_id, ingredient_group_id, ingredient_option_id
            FROM meal_plan_ingredient_selections
            """,
            "DELETE FROM meal_plan_ingredient_selections",
            "DELETE FROM meal_plan_entries",
            "DELETE FROM recipe_ingredient_groups",
            "DELETE FROM recipe_steps",
            "DELETE FROM recipe_tastes",
            "DROP TABLE recipes",
            """
            CREATE TABLE recipes (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL CHECK (length(trim(name)) > 0),
                standard_serving_count INTEGER NOT NULL CHECK (standard_serving_count > 0),
                preparation_time_minutes INTEGER CHECK (preparation_time_minutes > 0),
                cooking_time_minutes INTEGER CHECK (cooking_time_minutes > 0),
                calories_kcal INTEGER CHECK (calories_kcal >= 0),
                protein_grams TEXT,
                carbohydrate_grams TEXT,
                fat_grams TEXT,
                dish_type TEXT NOT NULL DEFAULT 'MAIN'
                    CHECK (dish_type IN ('MAIN', 'SIDE', 'DESSERT')),
                baking_time_minutes INTEGER CHECK (baking_time_minutes > 0)
            )
            """,
            """
            INSERT INTO recipes
                (id, name, standard_serving_count, preparation_time_minutes,
                 cooking_time_minutes, calories_kcal, protein_grams,
                 carbohydrate_grams, fat_grams, dish_type, baking_time_minutes)
            SELECT id, name, standard_serving_count, preparation_time_minutes,
                   cooking_time_minutes, calories_kcal, protein_grams,
                   carbohydrate_grams, fat_grams, dish_type, baking_time_minutes
            FROM recipes_migration_v11
            """,
            """
            INSERT INTO recipe_ingredient_groups (id, recipe_id, position, default_option_id)
            SELECT id, recipe_id, position, default_option_id FROM groups_migration_v11
            """,
            """
            INSERT INTO recipe_ingredient_options
                (id, group_id, ingredient_id, quantity, unit, position)
            SELECT id, group_id, ingredient_id, quantity, unit, position
            FROM options_migration_v11
            """,
            """
            INSERT INTO recipe_steps (recipe_id, position, description)
            SELECT recipe_id, position, description FROM steps_migration_v11
            """,
            """
            INSERT INTO recipe_tastes (recipe_id, taste_id)
            SELECT recipe_id, taste_id FROM tastes_migration_v11
            """,
            "DROP TABLE meal_plan_entries",
            """
            CREATE TABLE meal_plan_entries (
                id TEXT PRIMARY KEY NOT NULL,
                planned_date TEXT NOT NULL,
                recipe_id TEXT NOT NULL,
                serving_count INTEGER NOT NULL CHECK (serving_count > 0),
                meal_role TEXT NOT NULL CHECK (meal_role IN ('MAIN', 'SIDE', 'DESSERT')),
                position INTEGER NOT NULL CHECK (position >= 0),
                CHECK (meal_role != 'MAIN' OR position = 0),
                FOREIGN KEY (recipe_id) REFERENCES recipes(id) ON DELETE RESTRICT
            )
            """,
            """
            INSERT INTO meal_plan_entries
                (id, planned_date, recipe_id, serving_count, meal_role, position)
            SELECT id, planned_date, recipe_id, serving_count, meal_role, position
            FROM meal_entries_migration_v11
            """,
            """
            CREATE UNIQUE INDEX meal_plan_one_main_per_date
            ON meal_plan_entries (planned_date)
            WHERE meal_role = 'MAIN'
            """,
            """
            CREATE UNIQUE INDEX meal_plan_side_position_per_date
            ON meal_plan_entries (planned_date, position)
            WHERE meal_role = 'SIDE'
            """,
            """
            CREATE UNIQUE INDEX meal_plan_dessert_position_per_date
            ON meal_plan_entries (planned_date, position)
            WHERE meal_role = 'DESSERT'
            """,
            """
            INSERT INTO meal_plan_ingredient_selections
                (meal_plan_entry_id, ingredient_group_id, ingredient_option_id)
            SELECT meal_plan_entry_id, ingredient_group_id, ingredient_option_id
            FROM meal_selections_migration_v11
            """,
            "DROP TABLE recipes_migration_v11",
            "DROP TABLE groups_migration_v11",
            "DROP TABLE options_migration_v11",
            "DROP TABLE steps_migration_v11",
            "DROP TABLE tastes_migration_v11",
            "DROP TABLE meal_entries_migration_v11",
            "DROP TABLE meal_selections_migration_v11"
        };
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
            statement.execute("PRAGMA user_version = 11");
        }
    }

    /** Adds immutable consumption history without referencing deletable meal-plan entries. */
    static void createVersion12(Connection connection) throws SQLException {
        String[] statements = {
            """
            CREATE TABLE inventory_consumptions (
                id TEXT PRIMARY KEY NOT NULL,
                meal_plan_entry_id TEXT NOT NULL UNIQUE,
                planned_date TEXT NOT NULL,
                processed_at TEXT NOT NULL
            )
            """,
            """
            CREATE TABLE inventory_consumption_items (
                consumption_id TEXT NOT NULL,
                position INTEGER NOT NULL CHECK (position >= 0),
                ingredient_id TEXT NOT NULL,
                quantity TEXT NOT NULL CHECK (length(trim(quantity)) > 0),
                unit TEXT NOT NULL,
                PRIMARY KEY (consumption_id, position),
                FOREIGN KEY (consumption_id) REFERENCES inventory_consumptions(id)
                    ON DELETE CASCADE
            )
            """,
            """
            CREATE INDEX inventory_consumptions_planned_date
            ON inventory_consumptions (planned_date, id)
            """,
            "PRAGMA user_version = 12"
        };
        try (Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }

    /** Adds optional recipe resting time while keeping total time derived. */
    static void createVersion13(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE recipes ADD COLUMN resting_time_minutes INTEGER "
                    + "CHECK (resting_time_minutes > 0)");
            statement.execute("PRAGMA user_version = 13");
        }
    }
}
