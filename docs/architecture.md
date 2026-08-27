# Architekturgrundlagen

MealDeal trennt die Verantwortlichkeiten in vier überschaubare Bereiche. Die Schichten werden erst dann mit Klassen ergänzt, wenn eine spätere Phase sie fachlich benötigt.

## Domain

Enthält die fachlichen Datenobjekte. Die Domain hat weder JavaFX-, JDBC- noch SQLite-Abhängigkeiten.

Das derzeit implementierte Modell ist:

```text
Recipe
 ├── RecipeIngredient *
 │    └── Ingredient
 ├── RecipeStep *
 └── Taste *
```

`Ingredient` und `Taste` besitzen eine persistenzunabhängige UUID als stabile technische Identität. Ein Rezept lässt jede dieser Identitäten höchstens einmal zu. Rezeptschritte werden anhand ihrer eindeutigen Position sortiert. Rezeptmengen werden mit `BigDecimal` gespeichert. Einheiten weisen ihre Dimension aus, damit spätere Umrechnung nur zwischen kompatiblen Einheiten erfolgt.

## Service

Enthält Geschäftslogik unabhängig von Darstellung und Persistenz. `RecipeScaler` erzeugt für eine gewünschte positive Personenanzahl neue `RecipeIngredient`-Objekte, ohne das gespeicherte Recipe zu verändern. Die Berechnung verwendet `BigDecimal` mit `MathContext.DECIMAL128`.

```text
Recipe
  ↓
RecipeScaler
  ↓
skalierte RecipeIngredient-Daten
```

`Quantity` bildet Menge und Unit als eigenständigen unveränderlichen Wert ab. Addition behält deterministisch die Einheit des ersten Operanden. `UnitConverter` unterstützt ausschließlich identische Units sowie `GRAM` ↔ `KILOGRAM` und `MILLILITER` ↔ `LITER`. Eine gemeinsame `UnitDimension` allein erlaubt keine Konvertierung; insbesondere bleiben Küchenmaße untereinander inkompatibel.

## Persistence

Kapselt den direkten JDBC-Zugriff auf SQLite. Repository-Schnittstellen kennen nur Domain- und Standard-Java-Typen; ihre SQLite-Implementierungen bilden Domain-Objekte auf das relationale Schema ab.

```text
Repository Interface
        ↓
SQLite Repository
        ↓
       JDBC
        ↓
      SQLite
```

Schema Version 1 wird beim ersten Öffnen erstellt und über `PRAGMA user_version` verfolgt. UUIDs werden als Text, `BigDecimal`-Mengen verlustfrei als Dezimaltext und Units über ihre Enum-Namen gespeichert. Jede neue Verbindung aktiviert SQLite-Foreign-Keys ausdrücklich.

Ingredients und Tastes werden über ihre eigenen Repositories verwaltet und müssen vor einem referenzierenden Recipe existieren. Das vollständige Speichern oder Aktualisieren eines Recipe läuft in einer Transaktion. Dabei werden seine Beziehungs- und Schrittzeilen verständlich und atomar ersetzt; bei Fehlern erfolgt ein Rollback.

## UI

Verwendet JavaFX für Darstellung und Benutzereingaben. Views und Controller enthalten keine Geschäftslogik und keine direkten SQL-Zugriffe.
