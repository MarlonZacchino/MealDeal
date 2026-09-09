# Recommendation UI und lokaler Feedback Loop

## Zweck und sichtbare Trennung

Seit R5.3 besitzt MealDeal zwei eigenständige Nutzerwege:

- **„Was soll ich kochen?“** lässt MealDeal bis zu fünf passende Gerichte aus lokalem
  Inventory, Recipe-Eigenschaften und den vorhandenen Personalisierungssignalen auswählen.
- **„Rezeptsuche“** lässt den Nutzer seine lokale Bibliothek bewusst nach Zutaten und
  Geschmack filtern.

Es gibt keinen gemeinsamen Modus, keinen Mode-Selector und keinen Wrapper zwischen beiden
Seiten. Die klassische Suche behält ihre IngredientGroup-Auswertung, ihre
PERFECT/GOOD/PARTIAL-Stufen und die Taste-Modi AND/OR/RANKING. Recommendation und Suche
besitzen getrennte Controller und getrennten Präsentationszustand.

## Recommendation-Anfrage und Ergebnis

Eine Anfrage enthält eine positive Personenzahl, ein optionales positives Zeitlimit,
optionale lokale Wunschzutaten und optionale lokale Taste-UUIDs. Der optionale Bereich
„Geschmack und Zutaten“ ist anfangs geschlossen; Schließen verwirft seine Auswahl nicht.
Wunschzutaten bedeuten „darauf hätte ich heute Lust“ und sind ausdrücklich kein Inventory.
Sie erhöhen weder Pantry Coverage noch verfügbare Mengen.

Vor jeder Recommendation führt der Workflow den bestehenden
`InventoryConsumptionService.consumePastEntries()` aus und lädt erst danach den
Inventory-Snapshot. Das Consumption Ledger bleibt die einzige Verbrauchsgrenze.
`RecommendationWorkflowService` baut den `RecommendationContext`, delegiert an
`PersonalizedRecommendationService` und übernimmt die ersten fünf bereits sortierten
Ergebnisse. Recipes ohne Wunschzutat bleiben zulässige Kandidaten.

Die Oberfläche zeigt Name, Gerichtstyp, relevante Zeit, Personenbezug und höchstens vier
verständliche Begründungen. Interne Scores und Prozentwerte bleiben unsichtbar, weil der
R0-Score weder Wahrscheinlichkeit noch Qualitätsversprechen ist. Fehlende History wird als
„Bisher nicht als gekocht erfasst“ beschrieben.

## Wunschzutaten als weiches Signal

`DESIRED_INGREDIENT_FIT` ist nur aktiv, wenn mindestens eine Wunschzutat gewählt wurde. Sein
Wert ist der Anteil gewählter lokaler Ingredient-UUIDs, die in mindestens einer Option einer
RecipeIngredientGroup vorkommen. Dadurch zählt auch eine passende Alternative. Ein fehlender
Match ergibt den Signalwert null, aber keinen Ausschluss. Catalog-Normalisierung, Fuzzy
Matching und Änderungen an Recipe-Gruppen finden nicht statt.

Das neue Rohgewicht beträgt `0,05`. Es nutzt dieselbe Active-Weight-Normalisierung wie alle
anderen optionalen Signale. Ohne Wunschzutaten ist das Signal abwesend und Ergebnis sowie
Score bleiben exakt auf dem vorherigen Stand. Die fachliche Herleitung und Re-Evaluation
sind in `scoring-model.md` und `evaluation.md` dokumentiert.

## Session- und Event-Semantik

Jeder ausdrückliche Start über „Vorschläge anzeigen“ erzeugt eine neue lokale Session-UUID.
Erst nachdem Ergebnis-Karten tatsächlich in die View eingefügt wurden, schreibt der Workflow
je sichtbarem Recipe genau ein `SHOWN`-Ereignis. Reines Neurendern erzeugt kein zweites
`SHOWN` derselben Session.

- `SELECTED` entsteht ausschließlich beim bewussten Öffnen einer Recipe-Detailansicht.
- `DISMISSED` entsteht durch den tertiären Button „Gerade nicht“ und entfernt die Karte nur
  aus der aktuellen Session.
- Nicht-Klicken wird nie als negatives Signal interpretiert.
- Ausblenden ist kein `DISLIKE` und verändert kein Recipe Feedback.

Eine neue Session übernimmt keine ausgeblendeten IDs. Historische DISMISSED-Ereignisse
bleiben das vereinbarte schwache R3-Präferenzsignal. Rank und Score werden so gespeichert,
wie sie berechnet wurden; Aktionen lösen keine automatische Neuberechnung aus.

## Detail-Rückweg

Eine Detailöffnung hält die Recommendation-Seite als dieselbe Parent-/Controller-Instanz.
Session-UUID, Personen, Zeit, Wunschzutaten, Geschmäcker, Ergebnisse, Dismissals und
Cooked-Zustand bleiben erhalten. Der Rückweg startet keine neue Berechnung. Ein
Feedback-Refresh liest nur den aktuellen expliziten Zustand und verwendet dieselbe Session;
die SHOWN-Deduplizierung bleibt wirksam.

`RecipeDetailContext.recommended` übergibt angefragte Portionen und die vom Scorer gewählte
Group-/Option-UUID-Map. Das bestehende Detailmodell initialisiert daraus seine lokale Auswahl
und skaliert mit `RecipeScaler`. Recipe, MealPlan und Datenbankschema bleiben unverändert.
Bibliothek und Rezeptsuche öffnen mit Recipe-Standards und kehren zu ihrem jeweiligen
Ursprung zurück.

Ausdrückliche Hauptnavigation beendet die alte Detail-/Rückkehrkette. Das bestehende
Navigationssystem hält Seitenzustand nur innerhalb dieser Kette; R5.3 führt absichtlich
keinen globalen Recommendation- oder Search-Cache ein. Nach gespeichertem Recipe-Edit oder
Löschen wird die Herkunftsroute frisch geladen.

## Explizites Feedback und Kartenaktionen

„Gefällt mir“ und „Gefällt mir nicht“ delegieren an den bestehenden
`RecipeFeedbackService`. Die gemeinsame `RecipeFeedbackView` zeigt den gespeicherten Zustand
als ausgewählten ToggleButton mit zusätzlichem Häkchen und Accessible Text. Ein erneuter
Klick auf den aktiven Button löscht das Feedback; der separate Clear-Button entfällt für
binäres Feedback. Die laufende Recommendation bleibt stabil, weil das Signal erst beim
nächsten ausdrücklichen Start neu eingelesen wird.

Die Aktionshierarchie einer Ergebnis-Karte lautet:

1. primär: „Gericht öffnen“,
2. sekundär: „Als gekocht markieren“,
3. Feedback-Gruppe: Like/Dislike,
4. tertiär: „Gerade nicht“ als echter Button.

„Als gekocht markieren“ delegiert an `MealHistoryService.recordRecommendation`. Gespeichert
werden lokale Recipe-UUID, Name-Snapshot, angefragte Personenzahl, Zeitpunkt und
`MealHistorySource.RECOMMENDATION`. Die Session verhindert eine doppelte Bestätigung
desselben Recipes. Nach erfolgreichem Speichern entfernt sie die Karte lokal aus der
aktuellen Runde, ohne neu zu rechnen oder die Reihenfolge der verbleibenden Ergebnisse zu
ändern. Dabei entstehen weder SHOWN, SELECTED noch DISMISSED; die Session-UUID bleibt gleich.
Eine neue Runde schließt das Recipe bis zum nächsten lokalen Kalendertag über Meal History
aus. Die Aktion erzeugt kein Feedback und verbraucht kein Inventory.

## Hilfe und Bildstrategie

Die eigene, scrollbare Seite „Hilfe“ erklärt Recommendation, Rezeptsuche, ihren Unterschied,
Feedback, Dismissal, Cooked-Markierung und alle zentralen Anwendungsbereiche. Sie bleibt rein
lokal und enthält derzeit bewusst keine Bild-Platzhalter, weil keine verlässlichen
Screenshots vorliegen. Später sinnvoll sind geprüfte Light-/Dark-Screenshots der beiden
Eingabeseiten, einer Recommendation-Karte, des Detail-Rückwegs und des Wochenplans. Bilder
dürfen erst eingebunden werden, wenn passende Assets vorliegen; so entstehen keine leeren
oder kaputten Bildflächen.

## Fehler, Datenschutz und Grenzen

Alle Repositories bleiben lokal. R5 führt weder Telemetrie noch Netzwerk-, Cloud- oder
Account-Abhängigkeiten ein. Persistenzfehler werden protokolliert und als verständlicher
UI-Zustand angezeigt. Ein leeres Inventory ist kein Fehler.

Bewusste Grenzen bleiben: keine Historienansicht, keine Session-Historie, keine
Recommendation-Planung, keine Rating-Eingabe und keine globale Wiederherstellung eines
Seitenzustands nach Hauptnavigation. N+1 beim vollständigen Recipe-Laden und synchrone
Verarbeitung bleiben Folgepunkte; die drei R3-Personalisierungsbatches sind unverändert.

Der historische R0–R5-Audit steht in `astra-r0-r5-audit.md`. Die Dokumente
`r5-1-integration-review.md` und `r5-2-mode-review.md` beschreiben abgeschlossene
Zwischenstände und sind keine Beschreibung der aktuellen R5.3-Navigation.
