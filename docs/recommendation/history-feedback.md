# Meal History und Recommendation Feedback

## Zweck und klare Grenzen

R2 speichert lokale Signale, verändert aber weder R0-Scores noch deren Sortierung. Drei
fachlich verschiedene Aussagen bleiben getrennt:

- `MealPlanEntry`: Ein Gericht ist geplant.
- `MealHistoryEntry`: Das Gericht wurde ausdrücklich als tatsächlich gekocht oder gegessen
  bestätigt.
- `RecommendationInteraction`: Der Nutzer hat auf ein konkretes angezeigtes
  Recommendation-Ergebnis reagiert.

Eine Planung wird nie automatisch zu History. `SHOWN` oder `SELECTED` bedeutet ebenfalls
nicht automatisch, dass ein Gericht gekocht wurde. Nicht-Klicken wird nicht als negatives
Signal interpretiert.

## Meal History

Jeder immutable `MealHistoryEntry` besitzt eine eigene UUID, die lokale Recipe-UUID,
`occurredAt`, die bestätigte positive Portionszahl, `source` und `createdAt`. Die Portionen
sind ein Snapshot und ändern sich nicht mit dem späteren Recipe-Standard. `occurredAt` ist
der ausdrücklich bestätigte Zeitpunkt des Kochens beziehungsweise Essens; `createdAt` ist
der technische Erfassungszeitpunkt.

Die Quellen sind bewusst auf drei tatsächlich unterscheidbare Fälle begrenzt:

- `MANUAL`: unabhängig von Planung oder Recommendation erfasst,
- `MEAL_PLAN`: aus einem konkreten Planungseintrag bestätigt,
- `RECOMMENDATION`: nach einer Empfehlung ausdrücklich als gekocht bestätigt.

Nur `MEAL_PLAN` besitzt eine `sourceMealPlanEntryId`. Diese ID ist in SQLite eindeutig:
Wiederholtes Bestätigen desselben Planungseintrags erzeugt kein zweites History-Ereignis.
Mehrere manuelle Kochvorgänge desselben Recipes bleiben dagegen erlaubt.

`MealHistoryRepository` bietet die für R3 benötigten kleinen Abfragen: neueste N Ereignisse,
Historie und letztes Ereignis eines Recipes, Existenz sowie Häufigkeit in einem halboffenen
Zeitraum. Die Sortierung verwendet Zeitstempel und UUID als stabilen Fallback.

## Snapshot- und Löschsemantik

R2 speichert bewusst keinen vollständigen Recipe-Snapshot. Neben der lokalen Recipe-UUID
wird nur der zum Ereignis gehörende Recipe-Name als kleiner Anzeige-Snapshot gehalten. Die
Tabelle besitzt absichtlich keinen Foreign Key auf `recipes`. Dadurch bleibt History nach
Recipe-Löschung verständlich und wird weder blockiert noch mitgelöscht. Catalog-, Community-
oder Name-Identitäten ersetzen die lokale Recipe-UUID nicht.

History-Kernfelder werden nicht aktualisiert. Ein fehlerhaftes Ereignis kann ausdrücklich
gelöscht und neu erfasst werden. Diese Korrektur verändert weder Meal Plan noch Inventory.

## Recipe Feedback

`RecipeFeedback` ist kein Ereignisstrom, sondern höchstens ein aktueller Zustand pro lokaler
Recipe-UUID. Das Modell unterstützt zwei getrennte, optionale Signale:

- binäre Präferenz `LIKE` oder `DISLIKE`,
- Bewertung von 1 bis 5.

Mindestens ein Signal muss gesetzt sein. Um widersprüchliche Zustände auszuschließen, kann
`LIKE` nur mit 4 oder 5 und `DISLIKE` nur mit 1 oder 2 kombiniert werden. Eine neutrale 3 ist
als reine Bewertung ohne binäre Präferenz möglich. Kein gespeicherter Zustand bedeutet
neutral; ein zusätzliches `NEUTRAL`-Enum ist deshalb unnötig. Aktualisierungen bewahren die
stabile Feedback-UUID. Leeren beider Signale entfernt den Zustand.

Feedback gehört zum aktuellen Recipe und wird daher bei dessen Löschung per Cascade
entfernt. Die historische Tatsache einer Mahlzeit bleibt davon unabhängig erhalten.

V1 legt explizites Feedback damit bewusst auf die langfristige Recipe-Ebene. Ein
`MealHistoryEntry` beschreibt zwar den konkreten Kochvorgang, trägt aber noch keine zweite
ereignisspezifische Bewertung. Dafür existiert derzeit keine eindeutige UX; ein weiteres
Feedback-Modell ohne Erfassungsweg würde nur zwei ähnlich wirkende Signale erzeugen. Falls
später „dieser konkrete Kochvorgang war gut/schlecht“ erhoben wird, benötigt dieses Signal
eine eigene, ausdrücklich vom Recipe-Feedback getrennte Semantik.

## Recommendation Interactions

`RecommendationInteraction` ist ein immutable lokales Ereignis mit eigener UUID,
Recipe-UUID, Aktion, Zeitpunkt und einer leichten `recommendationSessionId`. Eine Session
gruppiert Ergebnisse derselben Berechnung ausschließlich über ihre UUID; R2 benötigt dafür
keine zusätzliche Tabelle.

V1 speichert nur sicher beobachtbare Aktionen:

- `SHOWN`,
- `SELECTED`,
- `DISMISSED`.

`SKIPPED` fehlt bewusst, weil bloße Nicht-Interaktion keine eindeutige Aussage ist. Jedes
Ereignis hält den angezeigten positiven Rang und den exakten R0-Score in `0..1` als Snapshot.
Die vollständige Explainability-Struktur wird nicht dupliziert. Interaktionen besitzen
keinen Recipe-Foreign-Key und bleiben als Evaluationsereignisse auch nach Recipe-Löschung
erhalten.

## Abgrenzung zum Consumption Ledger

`InventoryConsumption` beantwortet ausschließlich, ob und wie ein früher Planungseintrag
bereits gegen den Bestand verarbeitet wurde. `MealHistoryEntry` beantwortet, was als
tatsächlich gekocht bestätigt wurde. `MealHistoryService.confirmCooked()` schreibt nur
History und ruft weder `InventoryConsumptionService` noch Inventory-Repositories auf.
History-Löschung macht deshalb keinen Bestandsabzug rückgängig und löst keinen erneuten
Abzug aus. Eine gekoppelte Bestandskorrektur ist eine spätere, eigene Produktentscheidung.

## Local-first, Privacy und späterer Scope

Alle Daten bleiben in der lokalen SQLite-Datenbank. R2 führt keine Telemetrie, Accounts,
Cloud-Übertragung oder personenbezogenen Metadaten ein. Eine spätere Cloud-Version muss
Meal History als potenziell haushaltsbezogen und persönliche Präferenz als potenziell
nutzerspezifisch behandeln; mangels heutiger User-/Household-Identität werden diese IDs in
R2 nicht vorweggenommen.

R3 nutzt diese Query-APIs nun über `RecommendationPersonalizationService`. Drei Batch-
Abfragen laden für alle aktuellen Candidates jeweils letztes Meal, aktuellen Feedback-
Zustand und Interaktionen. Der Service schreibt dabei nichts. `PersonalizedRecommendationService`
übergibt den erzeugten Snapshot an den bestehenden R0-Scorer.

Meal History liefert ausschließlich die 14-Tage-Freshness; gekocht bedeutet nicht LIKE.
`SELECTED` liefert ausschließlich schwache implizite Preference; ausgewählt bedeutet nicht
gekocht. Explizites Feedback überschreibt den Interaction-Fallback. Recipe-Löschung bleibt
unproblematisch, weil nur aktuell geladene Candidate-UUIDs einen Snapshot und Score erhalten.
