# Architekturgrundlagen

MealDeal trennt die Verantwortlichkeiten in vier überschaubare Bereiche. Die Schichten werden erst dann mit Klassen ergänzt, wenn eine spätere Phase sie fachlich benötigt.

## Domain

Enthält später die fachlichen Datenobjekte. Die Domain hat weder JavaFX- noch SQLite-Abhängigkeiten.

## Service

Enthält später die Geschäftslogik, beispielsweise Portionsberechnung, Suche, Planung und Einkaufslisten. Services bleiben unabhängig von der Darstellung.

## Persistence

Kapselt später den direkten JDBC-Zugriff auf SQLite. Repository-Implementierungen übernehmen das Speichern und Laden fachlicher Daten.

## UI

Verwendet JavaFX für Darstellung und Benutzereingaben. Views und Controller enthalten keine Geschäftslogik und keine direkten SQL-Zugriffe.
