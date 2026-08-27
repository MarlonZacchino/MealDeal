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

`RecipeSearchService` bewertet eine übergebene Recipe-Sammlung anhand der UUID-Identitäten ausgewählter Ingredients oder Tastes. Er lädt selbst keine Daten und besitzt keine Repository-Abhängigkeit.

```text
Recipe collection
      ↓
RecipeSearchService
      ↓
IngredientSearchResult / TasteSearchResult
```

Zutaten werden nach Trefferzahl bewertet. Taste-Suchen unterstützen `AND`, `OR` und `RANKING`. Ranking-Ergebnisse verwenden gemeinsam `PERFECT` für vollständige Treffer, `GOOD` für mehr als die Hälfte und andernfalls `PARTIAL`. Namen dienen nur der stabilen Ergebnissortierung; Ähnlichkeit, Normalisierung und Synonyme sind nicht Bestandteil der Suche.

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

Schema-Versionen werden beim Öffnen über `PRAGMA user_version` schrittweise migriert. Version 2 ergänzt `meal_plan_entries`; vorhandene Version-1-Daten bleiben erhalten. UUIDs werden als Text, `BigDecimal`-Mengen verlustfrei als Dezimaltext und Units über ihre Enum-Namen gespeichert. Jede neue Verbindung aktiviert SQLite-Foreign-Keys ausdrücklich.

Ingredients und Tastes werden über ihre eigenen Repositories verwaltet und müssen vor einem referenzierenden Recipe existieren. Das vollständige Speichern oder Aktualisieren eines Recipe läuft in einer Transaktion. Dabei werden seine Beziehungs- und Schrittzeilen verständlich und atomar ersetzt; bei Fehlern erfolgt ein Rollback.

## Wochenplanung

`MealPlanEntry` verbindet über eine eigene UUID ein `LocalDate` mit einem bereits persistierten Recipe und einer individuellen Personenanzahl. Version 1 erlaubt per `UNIQUE(planned_date)` nur einen tatsächlichen Eintrag pro Tag; leere Tage werden nicht persistiert. Ein Recipe mit Planungshistorie ist durch `ON DELETE RESTRICT` vor versehentlichem Löschen geschützt.

```text
Recipe
   ↑
MealPlanEntry
   │
   ▼
MealPlanRepository
   │
   ▼
SQLite
```

`WeekService` überlässt Monats-, Jahres- und Schaltjahresgrenzen vollständig `LocalDate` und liefert einen `WeekRange` von Montag bis Sonntag. `MealPlanCleanupService` verwendet einen injizierbaren `Clock`: Einträge mit `date < today.minusDays(30)` werden explizit gelöscht, während genau 30 Tage alte Einträge erhalten bleiben. Die Phase enthält noch keine Skalierung oder Einkaufslistenberechnung für geplante Rezepte.

## Einkaufslistenberechnung

`ShoppingListService` berechnet eine Einkaufsliste deterministisch aus den aktuellen Planungs- und Recipe-Daten. Sie wird nicht persistiert, damit keine doppelte oder nach Recipe-Änderungen veraltete Datenhaltung entsteht.

```text
MealPlanRepository
        ↓
MealPlanEntry
        ↓
RecipeScaler
        ↓
ShoppingListService
        ↓
ShoppingList
        └── ShoppingListItem
```

Die geplante Personenanzahl fließt ausschließlich über `RecipeScaler` ein. Gleiche Zutaten werden anhand ihrer UUID und ihrer tatsächlichen Unit-Kompatibilitätsgruppe zusammengeführt; Addition verwendet `Quantity.add()` und behält damit die Einheit des ersten Eintrags. Inkompatible Angaben wie Stück und Gramm bleiben getrennte Positionen. Ein injizierbarer `Clock` bestimmt das lokale Heute. Die aktuelle Wochenliste lädt nur den Bereich von heute bis Sonntag und schließt gespeicherte vergangene Tage ausdrücklich aus.

## UI

Verwendet JavaFX für Darstellung und Benutzereingaben. Views und Controller enthalten keine Geschäftslogik und keine direkten SQL-Zugriffe.
