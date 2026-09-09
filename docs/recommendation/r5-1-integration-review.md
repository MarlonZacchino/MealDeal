# MealDeal – R5.1 Integration Review

Historischer R5.1-Stand: Die damalige Filter-Klappe wurde durch die in
[R5.2](r5-2-mode-review.md) dokumentierten Modi ersetzt. Dessen Prüfliste ist aktuell.

Stand: 8. September 2026. Umsetzung auf dem vorhandenen uncommitteten R5-/Astra-Stand.
Die freigegebene Konsolidierung und die drei Integrationsfixes sind implementiert.

## 1. Executive Summary

**R5 READY FOR MANUAL FINAL REVIEW.** Detailkontext, Rückkehr zur vorhandenen Runde und
Verbrauchsabgleich vor dem Inventory-Snapshot sind verbunden. „Gericht finden“ bietet
Empfehlungen als Hauptweg und die bestehende gezielte Suche als aufklappbaren Nebenweg.
Explizites Feedback steht außerdem im bestehenden Detailview aus jeder Herkunft bereit.

Final: **73/73 gezielte Tests**, **749/749 im vollständigen Java-25-Lauf**, Maven Package
erfolgreich. Keine GUI-Automation, kein Commit, kein Push. R0–R4-Fachlogik und Expected Values
wurden nicht verändert.

## 2. Detail Context Fix

Ursache: Die Karte übergab nur das Recipe; das Detail initialisierte dessen Standardportionen
und Standardoptionen. Die Pantry-Bewertung konnte dadurch eine andere Auswahl betreffen.

Lösung: `RecipeDetailContext.recommended(session, recipeId)` trägt Recipe, angefragte
Portionen und die `suggestedIngredientOptions`-Map in `ViewNavigator` und den bestehenden
`RecipeDetailController`. `RecipeDetailIngredientModel` initialisiert seine temporäre
Auswahl daraus; `RecipeScaler` liefert weiterhin sämtliche Mengen. Der Spinner erhält
dieselbe Portionszahl. Die Map wird kopiert und gegen die Gruppen/Optionen geprüft.

Tests prüfen den vollständigen Übergabeschritt des tatsächlichen RecommendationControllers
und separat die Mengenanzeige: Standard 2 Personen/Kalb, Vorschlag 4 Personen/Hähnchen,
Detail 4 Personen/Hähnchen/600 g. Das persistierte Recipe behält seine Standardoption und
Standardportionen. Keine Recipe-Mutation, MealPlan-Speicherung oder neue Datenbankspalte.

## 3. Recommendation Return-State Fix

Ursache: Jede Navigation lud neu; „Zurück“ führte unabhängig von der Herkunft zur Bibliothek.

Lösung: `ViewNavigator` hält genau eine Ursprungsansicht mit Controller für die offene
Detail-/Edit-Kette. Zurück stellt dieselbe Parent-/Controller-Instanz wieder her und markiert
deren Navigationspunkt. Bibliothek, gemeinsame Suchseite und Wochenplan sind abgedeckt.
Bei klassischer Suche bleibt insbesondere der aufgeklappte Suchbereich erhalten.

Damit bleiben Session-Objekt/UUID, Personenfeld, Zeittext, Taste-Auswahl, Ranking,
sessionlokale Dismissals, Feedback und Cooked-Zustand erhalten. Auch die ScrollPane wird
wiederverwendet; die genaue sichtbare Scrollposition und Fokusfolge sind manuell zu prüfen.
Der Rückweg führt keine neue Recommendation-Berechnung aus. Nur explizites Feedback wird
aus der Persistenz aktualisiert; die vorhandene SHOWN-Deduplizierung gilt weiter.

Lebensdauer:

- Detail öffnen/Zurück und Edit-Abbrechen erhalten den Ursprung.
- Bewusste Hauptnavigation gibt den Rückkehrzustand frei. Späteres „Gericht finden“ lädt
  eine neue Seite ohne wiederbelebte Session.
- Ein ausdrücklicher Vorschlagsstart erzeugt eine neue UUID und neue Dismiss-Menge.
- Gespeicherte Recipe-Änderung oder Löschung invalidiert die alte Ergebnisansicht;
  Zurück lädt die Herkunftsroute frisch. Ohne Herkunft ist die Bibliothek das Rückfallziel.
- Scheitert das Laden einer anderen Hauptseite, bleibt der bisherige Rückweg verfügbar.

Neun Navigationstests prüfen diese Übergänge ohne JavaFX-Toolkit oder Stage. Ergänzende
Workflowtests belegen stabiles Ranking, Feedback-Refresh, Dismiss-/Cooked-Erhalt und
SHOWN-Deduplizierung. Die Formularerhaltung beruht auf der geprüften Identität der
Ursprungsansicht; ein manueller Klick-Roundtrip wird dadurch nicht behauptet.

## 4. Inventory Day-Change Fix

Ursache: Recommendation las Inventory direkt und konnte den sonst durch Start/Inventar/
Einkauf ausgelösten Abgleich nach einem Datumswechsel umgehen.

`RecommendationWorkflowService` erhält jetzt verpflichtend den vorhandenen
`InventoryConsumptionService`. Nach Request-Validierung ruft `start()` dessen
`consumePastEntries()` auf, bevor es Inventory lädt. Der Scorer und der Controller enthalten
keine zweite Verbrauchslogik. Das vorhandene atomare Consumption Ledger bleibt unverändert.

SQLite-Regression mit verstellbarer Clock: Tag X, Vorrat 150 g, heutiger Plan benötigt
100 g; noch kein Verbrauch. Clock auf X+1, direkt neuer Recommendation-Start: Vorrat 50 g,
Pantry Coverage 0,5 und genau ein Ledger-Eintrag mit dem neuen Zeitpunkt. Zweiter Start:
weiter 50 g und derselbe einzelne Ledger-Eintrag. Kein anderer Screen wurde aufgerufen.
Dabei entsteht keine Meal History.

## 5. Dismiss-Semantik

Der bestätigte Vertrag bleibt erhalten: kein Session-Leak und kein dauerhafter UI-Ausschluss.
„Gerade nicht“ ersetzt „Für diesmal ausblenden“. Der Hinweis unter „Deine Vorschläge“ erklärt
die Ausblendung in dieser Runde und die leichte historische Wirkung auf spätere Vorschläge.
„Gefällt mir nicht“ bezeichnet weiterhin ausdrücklich gespeichertes DISLIKE-Feedback.

Ein zulässiges Recipe muss nach einem neuen Start nicht wieder unter den ersten fünf liegen.
Die vorhandenen Tests trennen Dismiss-Menge, historisches Präferenzsignal und Top-5-Grenze.
DISMISSED bleibt unabhängig von DISLIKE und COOKED.

## 6. Navigation Merge

Vorher: eigene Hauptnavigation „Empfehlungen“ neben der klassischen Seite „Gericht finden“.
Jetzt: `SEARCH` öffnet `find-meal-view.fxml`; Empfehlung ist primär, die klassische Suche
liegt zunächst geschlossen unter „Filter & Suchoptionen“. Auch die Startseitenaktion führt
dorthin, mit entsprechend aktualisierter Beschreibung.

`RECOMMENDATIONS`, `recommendationsButton` und `showRecommendations` sind entfernt.
`recommendation-view.fxml` und `search-view.fxml` bleiben als tatsächlich verwendete
FXML-Includes mit ihren jeweiligen Controllern erhalten. Es gibt keinen ungenutzten
separaten Navigationsweg. `FindMealController` komponiert lediglich beide Teile.

## 7. Recommendation/Search State Separation

Der aufgeklappte Suchbereich bestimmt, welcher Ergebnisbereich sichtbar ist. Aufklappen
versteckt die Empfehlungsergebnisse, Zuklappen stellt sie wieder dar. Ein validierter
Vorschlagsstart schließt die Suche vor der Berechnung, damit auch ein Berechnungsfehler
sichtbar wird. Versteckte Ergebnisse werden nicht gelöscht.

Empfehlungs-Tastes und Such-Tastes gehören weiterhin verschiedenen Controllern/Modellen.
Ein Kompositionstest mit den produktiv erzeugten Controllern prüft Sichtbarkeit/Managed,
erhaltene Ergebnisobjekte und Session sowie „Herzhaft“ als Empfehlung gegenüber „Süß“ als
Suchfilter. Such-Reset verändert die Empfehlungs-Auswahl nicht. PERFECT/GOOD/PARTIAL,
AND/OR/RANKING, `RecipeSearchService` und `CombinedRecipeSearchService` bleiben unverändert.

## 8. Card-/Action-Hierarchy

„Gericht öffnen“ bleibt die Hauptaktion. Like/Dislike/Clear bilden eine separate, nachgeordnete
Feedbackgruppe. „Gerade nicht“ ist ein Hyperlink. Die Aktionsbereiche umbrechen als FlowPane.

„Als gekocht markieren“ bleibt nachgeordnet auf der Karte. Dort sind angefragte Portionen
und die bestehende Bestätigungssperre pro Session eindeutig. Eine Verlagerung mit eigener
Detail-Kochsemantik oder Undo wäre ein weiterer fachlicher Umfang. Der vollständige R5-Loop
bleibt ausführbar; SELECTED erzeugt weder History noch Bestandsverbrauch.

## 9. Feedback UI

Die kleine gemeinsame `RecipeFeedbackView` rendert Like, Dislike und Clear auf Karten und im
Detail. Die Persistenz bleibt bei `RecipeFeedbackService`. In der produktiven Composition
ist das Detailfeedback aus Bibliothek, Suche, Recommendation und Wochenplan verfügbar.

Aktive binäre Bewertung: Häkchen, zugänglicher Statustext und Randmarkierung. Rating-only-
Feedback bleibt sichtbar und entfernbar; es gibt keine neue Rating-Eingabe. Clear entfernt
den gesamten gespeicherten Zustand. Beim Rückweg wird Feedback aktualisiert, ohne die
Reihenfolge oder die Reason-Snapshots der laufenden Runde neu zu berechnen.

## 10. CSS / Responsive

Die bestehenden Module, Theme-Tokens und Viewport-Schwellen bleiben erhalten:
compact unter 1100, normal ab 1100, wide ab 1440, extra-wide ab 2100 logischen Pixeln.
Die gemeinsame Seite benutzt einen einzigen ScrollPane mit `fitToWidth` und ohne
horizontale Scrollbar sowie den vorhandenen adaptiven `page-container`.

Request-Felder, Geschmacks-Chips und Kartenaktionen umbrechen. Auch Suchaktionen und
AND/OR/RANKING-Karten wurden auf FlowPane umgestellt. Das bestehende Suchauswahl-Raster
bleibt im Compact-Modus einspaltig. Lange Rezeptnamen haben Wrap und Mindestbreite 0.
Die Includes bringen keine zusätzlichen Seitenränder oder ScrollPanes mit.

Gemeinsame Feedbackmarkierung liegt in `components.css`, Hyperlink-Grundstil in
`controls.css`, Empfehlungshinweise weiterhin in `search.css`. Die Feedbacktexte erben
den bereits lesbaren Text-Token der Secondary Buttons auch im Dark Mode. Negative Reasons,
Warnungspriorität, leeres Inventar, getrennte Empty States und Personenvalidierung bleiben
erhalten. Keine neuen doppelten Selektoren, kein `!important`, kein Inline-Stil/`setStyle`.
FXML-/CSS-Parser-, Import-, Modul- und Duplikatprüfungen sind grün. Dies ist eine statische
Layoutprüfung; tatsächliches Rendering, Fokus und alle Fenstergrößen bleiben manuelle Abnahme.

## 11. N+1 Finding

**SHOULD NEXT – bewusst nicht in R5.1 umgebaut.** Der Befund liegt im tatsächlichen Hot Path:
`RecommendationWorkflowService.start` ruft `SqliteRecipeRepository.findAll` auf. Dessen
vollständiges Laden benötigt aktuell 1 ID-Abfrage + 4 Abfragen je Recipe + eine je Gruppe.
`loadRecipe` wird außerdem für Einzelzugriffe wiederverwendet.

Ein Batch-Lader ist technisch möglich, aber kein kleiner Aufruf-Fix: Er müsste Recipe-Zeilen,
geordnete Gruppen/Optionen, Kategorien und Catalog-Links, Steps und Tastes über die gesamte
Ergebnismenge zusammenführen und dieselbe Rekonstruktion einschließlich Zeiten/Nährwerten
absichern. Das wäre ein größerer interner Repository-Umbau mit eigenen Aggregations- und
Abfragezahltests. Dafür sind weder neues Schema noch Framework nötig. Empfohlen ist eine
eigene begrenzte Folgeänderung mit synthetischer Laufzeitmessung und gemeinsamem Batch-Lader.

Die drei R3-Personalisierungsbatches bleiben unberührt und getestet. Zusätzliches R5-Feedback-
Lesen und historienabhängige Arbeit im bestehenden Verbrauchsabgleich werden nicht als
„drei Gesamtqueries“ ausgegeben. Synchrone Verarbeitung bleibt ein Skalierungsrisiko.

## 12. Neue Regressionstests

Gegenüber dem übernommenen Stand mit 731 sind es **18 zusätzliche Testausführungen**:

| Bereich | Zusätzlich | Inhalt |
|---|---:|---|
| ViewNavigatorTest | 9 | Ursprung, Identität, Wiederholung, Lebensdauer, Edit, Löschen, Ladefehler, Fallback |
| RecipeDetailControllerTest | 2 | reale Scorer-Alternative/4 Personen/Menge; validierter unveränderlicher Kontext |
| FindMealControllerTest | 2 | getrennte Zustände/Sichtbarkeit; tatsächliche Controller-Detailübergabe und SELECTED |
| RecommendationWorkflowServiceTest | 1 | Feedback-Refresh bei stabiler Runde einschließlich Dismiss/COOKED/SHOWN |
| RecommendationFeedbackLoopIntegrationTest | 1 | Clock-Wechsel, vorgezogener Abgleich, Ledger-Idempotenz |
| FxmlResourceTest | 3 | Komposition/Route, gemeinsame Styles/keine Inline-Stile, zusätzliche Ressourcenprüfung |

Die vorhandenen R5-Tests bleiben enthalten: neue UUID, SHOWN einmal, SELECTED, DISMISSED,
LIKE/DISLIKE/Clear, Rating-only, COOKED, persistierte Folgewirkung, leeres Inventar,
Parser/Validierung und persistierte Dismiss-/Top-5-Trennung.

## 13. Gezielte Tests: 73/73

`ViewNavigatorTest` 9, `RecipeDetailControllerTest` 8, `FindMealControllerTest` 2,
`RecommendationWorkflowServiceTest` 10, `RecommendationFeedbackLoopIntegrationTest` 3,
`RecommendationControllerTest` 8, `ApplicationContextRecommendationTest` 1 und
`FxmlResourceTest` 32. Failures/Errors/Skipped jeweils 0. Log: `target/r51-targeted.log`.

## 14. R0: 67/67

Teilmenge des finalen Gesamtaufrufs: RecipeRecommendationService 23, Contract 9, GoldenScenario
8, PantrySharedBudget 10, PantryOptimizerOracle 2, ReviewRegression 11 und TieBreak 4.
Keine Gewichte, Scoreformeln, Eligibility oder erwarteten Werte angepasst.

## 15. R1: 25/25

Teilmenge: CatalogMatcher 3, CatalogTextNormalizer 2, LocalCatalogResolver 4,
StandardCatalogIntegrity 3, SqliteCatalogLinkIntegration 2, Ingredient 6 und Taste 5.
Die beiden Domain-Suites enthalten auch ältere allgemeine Verträge. Catalog-Policy,
UUIDs und Link-Persistenz wurden nicht verändert.

## 16. R2: 24/24

Teilmenge: MealHistoryEntry 4, RecipeFeedback 4, RecommendationInteraction 3,
SqliteMealHistoryRepositoryIntegration 7, SqliteRecipeFeedbackRepositoryIntegration 3,
SqliteRecommendationInteractionRepositoryIntegration 2, RecommendationInteractionService 1.
Die neuen Workflow-/SQLite-Looptests prüfen ergänzend die Integration dieser Verträge.

## 17. R3: 32/32

Teilmenge: PersonalizedRecommendationService 8, RecommendationPersonalizationPolicy 18,
RecommendationPersonalizationService 6. Die drei gebündelten Personalisierungslesezugriffe
sowie Recency-/Feedback-/Interaktionsmapping bleiben unverändert.

## 18. R4: 67/67

RecommendationR4GoldenScenario 14, RecommendationR4Invariant 11 und
RecommendationR4Sensitivity 42. Synthetische Vertragsprüfungen; keine Behauptung einer
empirischen Nutzerakzeptanz oder universellen Invarianz über nicht geprüfte Bedingungen.

## 19. Full Java 25: 749/749

Finaler vollständiger Lauf: Failures 0, Errors 0, Skipped 0. Einschließlich der übrigen
Domain-, SQLite-, Inventar-, Wochenplan-, Such-, Formular- und UI-Ressourcentests.
R0–R4-Zahlen oben sind ausdrücklich definierte Teilmengen dieses Gesamtaufrufs.
Auswertung der Surefire-XML-Dateien: `target/r51-phase-results.json`.

Runtime: Microsoft OpenJDK 25.0.4.1, Maven 3.9.11, JavaFX 26 laut Projektkonfiguration.
Automatisierte Tests verwenden temporäre Datenbanken. Kein Zugriff auf private produktive
Rezeptdaten, kein App-Start, keine Desktopbedienung oder Screenshot-/Klickautomation.

## 20. Package: SUCCESS

`mvn -B package` lief einschließlich aller 749 Tests erfolgreich. JAR:
`target/mealdeal-0.1.0-SNAPSHOT.jar`; finales Log `target/r51-package.log`.
Die vorhandenen Native-Access-/Deprecation-Hinweise sind keine Testfehler. Ein Installer-
oder manueller Starttest wird nicht behauptet.

## 21. git diff --check: SUCCESS

Getrackter Diff geprüft; keine Whitespace-Fehler. Zusätzliche Prüfung der ungetrackten
Textdateien auf Konfliktmarker und nachgestellte Leerzeichen. Keine Build-Artefakte zur
Versionierung hinzugefügt.

## 22. Git: uncommitted / unpushed

Branch `main`, unverändertes HEAD `6155988b6f564a2fc5b4e4221dbf32cf89bcc688`.
Vorhandene R5-/Astra-Änderungen wurden als Arbeitsgrundlage erhalten und fachlich fortgeführt.
Die freigegebene Entfernung des R5-Navigationspunkts bringt MainController/main-view.fxml
wieder auf den getrackten Navigationsumfang zurück; dies ist kein Reset.
Kein Commit, Push, Checkout, Stash, Clean, Revert oder Fetch. `origin/main` ist nur der
lokal bekannte Remote-Tracking-Stand. Finaler Status enthält ausschließlich ungestagte
Quell-/Ressourcen-/Test-/Dokumentationsänderungen und neue Dateien.

## 23. Bekannte Einschränkungen

Manuelle visuelle Abnahme bleibt offen, insbesondere Scroll-/Fokuswiederkehr, kleine Fenster,
Light/Dark und die im historischen Audit benannten allgemeinen Badge-/Dialogthemen.
Es gibt keinen vollständigen Desktop-End-to-End-Test. Die Prüfbasis besteht aus produktiver
Composition, echter Fach-/SQLite-Integration, Navigation ohne Toolkit und FXML/CSS-Verträgen.

N+1, synchrone Berechnung und große History-Lesemengen bleiben Folgepunkte. Koch-Undo,
History-Ansicht, Rating-Eingabe und Einplanen aus Recommendation sind weiterhin nicht enthalten.
Recipe-Edit/Löschen lädt beim Rückweg bewusst frisch; dadurch bleibt dort keine alte Runde
oder ungespeicherter Such-/Wochenplanansichtszustand erhalten.

## 24. Manuelle Acceptance Checklist A–T

Alle Punkte durch den Nutzer auszuführen. Für den Alternativentest ein Rezept mit Standard
2 Personen, Standard A und vorrätiger Alternative B verwenden.

- [ ] **A** „Gericht finden“ über Sidebar und Startseite öffnen: ein Hauptweg, Suche geschlossen.
- [ ] **B** Ohne optionale Filter Vorschläge starten: bis zu fünf verständliche Karten;
  auch leeres Inventar und fehlende geeignete Rezepte prüfen.
- [ ] **C** 3 Personen, 30 Minuten und „Herzhaft“ setzen; fünf Ergebnisse verwenden;
  Zeitwarnungen und ungültige/leere Personen- bzw. ungültige Zeitangaben prüfen.
- [ ] **D** „Filter & Suchoptionen“ aufklappen: Empfehlungsergebnisse verschwinden.
- [ ] **E** Zutaten-/Geschmackssuche mit AND/OR/RANKING verwenden, umschalten und Reset prüfen;
  Empfehlungswünsche bleiben unabhängig. Suchdetail öffnen/Zurück erhält Suche und Filter.
- [ ] **F** Empfehlung öffnen; danach zusätzlich Detail aus Bibliothek und Wochenplan testen.
- [ ] **G** Mit 4 Personen gestarteter Vorschlag zeigt im Detail 4 und entsprechend skalierte Mengen.
- [ ] **H** Vorratsbezogen gewählte Alternative B ist im Detail aktiv; Recipe-Standard bleibt A.
- [ ] **I** Zurück erhält dieselbe Runde, Eingaben, Reihenfolge und ausgeblendetes Resultat C;
  Scrollposition prüfen. Bibliothek/Wochenplan kehren zu ihrem eigenen Ursprung zurück.
- [ ] **J** „Gerade nicht“ entfernt nur diese Karte; vollständiges Ausblenden zeigt passenden Leerzustand.
- [ ] **K** Neue Vorschläge starten: kein dauerhafter UI-Ausschluss. Bei mehr als fünf Kandidaten
  kann historische Präferenz die Top 5 ändern. Hauptseite verlassen/neu öffnen startet ohne alte Runde.
- [ ] **L** Like/Dislike/Clear auf Karte und im Detail aus mehreren Herkünften prüfen;
  aktive Markierung, Rating-only-Clear und Aktualisierung nach Zurück kontrollieren.
- [ ] **M** „Als gekocht markieren“ einmal auslösen: Bestätigung und Wiederholungssperre,
  kein Inventarabzug durch diese Aktion. Detailöffnung allein ist kein Kochen.
- [ ] **N** Neue Recommendation starten: aktuelles Kochen fließt in Recency ein;
  vorherige Runde wurde durch Feedback nicht umsortiert.
- [ ] **O** Mit separaten Testdaten nach Datumswechsel direkt Vorschläge starten:
  vergangener Plan einmal abgeglichen; zweiter Start ohne weiteren Abzug.
- [ ] **P** Compact unter 1100 und normale Breite: Inputs, Chips, lange Namen, Reasons und
  Such-/Kartenaktionen bleiben lesbar und ohne horizontales Abschneiden erreichbar.
- [ ] **Q** Wide ab 1440 und extra-wide ab 2100: Container, Suchraster und lange Texte prüfen.
- [ ] **R** Light Mode: Hierarchie, aktive Bewertung, Warnungen und Leerzustände prüfen.
- [ ] **S** Dark Mode: dieselben Zustände, Hyperlink, Badges und Detailfeedback prüfen.
- [ ] **T** Tastatur/Tab/Shift+Tab, Enter/Leertaste, Auf-/Zuklappen und Fokus nach Zurück prüfen;
  zusätzlich Edit-Abbrechen erhält Ursprung, Edit-Speichern/Löschen liefert frische Herkunftsansicht.

## 25. Empfehlung

**R5 READY FOR MANUAL FINAL REVIEW.** Die freigegebenen Integrationsblocker sind behoben und
automatisiert abgesichert. Als nächstes steht die manuelle A–T-Abnahme an. Es wurde kein
Commit oder Push durchgeführt; der historische Audit bleibt als vorheriger Befund erhalten.
