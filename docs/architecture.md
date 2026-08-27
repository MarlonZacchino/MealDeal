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

Enthält später die Geschäftslogik, beispielsweise Portionsberechnung, Suche, Planung und Einkaufslisten. Services bleiben unabhängig von der Darstellung.

## Persistence

Kapselt später den direkten JDBC-Zugriff auf SQLite. Repository-Implementierungen übernehmen das Speichern und Laden fachlicher Daten.

## UI

Verwendet JavaFX für Darstellung und Benutzereingaben. Views und Controller enthalten keine Geschäftslogik und keine direkten SQL-Zugriffe.
