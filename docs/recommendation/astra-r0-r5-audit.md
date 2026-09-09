# MealDeal – Astra R0–R5 Technical, UX/UI und Design Audit

> Historischer Audit vor R5.1. Die später freigegebenen Integrationsfixes und der aktuelle
> Abnahmestand sind in [R5.1 Integration Review](r5-1-integration-review.md) dokumentiert.
> Die ursprünglichen Findings und das damalige Urteil bleiben hier nachvollziehbar erhalten.

Stand: 8. September 2026. Statischer Repository-Audit mit automatisierten Java-25-Tests.
Keine GUI-Automation, kein manueller App-Start, keine Screenshots, keine Untersuchung der
privaten produktiven SQLite-Datenbank. Aussagen zum gerenderten Erscheinungsbild sind daher
Risiken oder aus CSS berechnete Farbverhältnisse, keine visuelle Abnahme.

## A. Executive Summary

**R5 NOT READY FOR COMMIT.** Die offenen HIGH-Findings F01–F03 unterbrechen den
pantrybewussten Entscheidungsweg. Tests und Package sind grün; die technischen Gates allein
belegen noch keine vollständige Produktintegration.

- **R0–R4 Core:** Die geprüften Implementierungen tragen die dokumentierte Baseline weitgehend.
  Keine Änderung an Gewichten, Scoring, Recency, Feedback-Mapping oder Household-Regeln.
  Die R4-Ergebnisse sind synthetische Vertragsprüfungen, keine bewiesene Nutzerakzeptanz.
- **R5:** Sinnvolle JavaFX-unabhängige Workflow-Grenze; getrennte History-, Feedback- und
  Interaction-Schreibwege. Die Detailübergabe verliert jedoch Empfehlungskontext, und die
  Navigation verliert die laufende Runde. Ein Datumswechsel kann veralteten Vorrat liefern.
- **Dismiss:** Kein bestätigter Session-Leak. Neue Tests zeigen einen reproduzierbaren
  Top-5-Effekt: ein historisches Dismissal kann einen weiterhin zulässigen Kandidaten unter
  Rang fünf verschieben. Die konkrete manuelle Beobachtung ist ohne deren Daten nicht
  abschließend erklärt, aber dieser Effekt erklärt sie unter genau benannten Bedingungen.
- **UX:** Recommendation ist momentan ein Zusatzpunkt; die Startseite führt weiter in die
  klassische Suche. Gemeinsame Aufgabe „Gericht finden“ mit Empfehlungen als Hauptweg empfohlen.
- **UI/Design:** Bordeaux, gemeinsame Container und CSS-Module sind erkennbar konsistent.
  Relevante Dark-Mode-Kontrastprobleme, überladene Ergebniskarten und Lücken beim Tastaturfokus.
  Kleine R5-Anzeigefehler wurden korrigiert; keine Neugestaltung vorgenommen.
- **Architektur:** Pure Scorer und Repository Pattern funktionieren. Tatsächliches N+1 im
  vorhandenen Recipe-Laden, synchrone Arbeit auf dem FX-Thread und unbeschränkte Historienlese-
  mengen bleiben Skalierungsrisiken. Kein Grund für neue Frameworks.

Git-Ausgangslage: `main`, HEAD = `main` = lokal gespeichertes `origin/main` =
`6155988b6f564a2fc5b4e4221dbf32cf89bcc688`. Remote-Tracking-Stand ohne Fetch geprüft;
keine Aussage über zwischenzeitliche Änderungen am Server. Vollständiger getrackter Diff
gegen `main` und die zusätzlich ungetrackten R5-Quell-, Ressourcen-, Dokumentations- und
Testdateien gelesen. Alle vorhandenen Änderungen erhalten. Kein Commit, Push, Reset, Stash,
Checkout, Clean oder Revert.

### Evidenzregister

Die Methodenangaben identifizieren den jeweiligen Datenfluss; Zeilennummern beziehen sich
auf den geprüften Stand nach den kleinen Fixes.

| Kürzel | Konkrete Quelle |
|---|---|
| E01 | [Workflow: start, select, dismiss, recordCooked](<C:/Users/Marlon Zacchino/Programmieren/MealDeal/src/main/java/de/mealdeal/service/recommendation/RecommendationWorkflowService.java>) |
| E02 | [Session: sichtbare Ergebnisse, Dismiss-Set, Snapshots](<C:/Users/Marlon Zacchino/Programmieren/MealDeal/src/main/java/de/mealdeal/service/recommendation/RecommendationSession.java>) |
| E03 | [RecommendationController: Start und Rendering](<C:/Users/Marlon Zacchino/Programmieren/MealDeal/src/main/java/de/mealdeal/ui/controller/RecommendationController.java>) |
| E04 | [Karten: Reasons, Feedback, Aktionen](<C:/Users/Marlon Zacchino/Programmieren/MealDeal/src/main/java/de/mealdeal/ui/controller/RecommendationResultCardFactory.java>) |
| E05 | [ViewNavigator: navigateToRecipeDetail, loadView](<C:/Users/Marlon Zacchino/Programmieren/MealDeal/src/main/java/de/mealdeal/ui/navigation/ViewNavigator.java>) |
| E06 | [RecipeDetailController: showRecipe, backToRecipes, configureServingSelection](<C:/Users/Marlon Zacchino/Programmieren/MealDeal/src/main/java/de/mealdeal/ui/controller/RecipeDetailController.java>) |
| E07 | [Recipe-Laden: findAll, loadRecipe, loadIngredientGroups](<C:/Users/Marlon Zacchino/Programmieren/MealDeal/src/main/java/de/mealdeal/persistence/sqlite/SqliteRecipeRepository.java>) |
| E08 | [R3 Batch-Context](<C:/Users/Marlon Zacchino/Programmieren/MealDeal/src/main/java/de/mealdeal/service/recommendation/RecommendationPersonalizationService.java>) |
| E09 | [Pure Scorer](<C:/Users/Marlon Zacchino/Programmieren/MealDeal/src/main/java/de/mealdeal/service/recommendation/RecipeRecommendationService.java>) |
| E10 | [R3 Policy](<C:/Users/Marlon Zacchino/Programmieren/MealDeal/src/main/java/de/mealdeal/service/recommendation/RecommendationPersonalizationPolicy.java>) |
| E11 | [Theme Tokens](<C:/Users/Marlon Zacchino/Programmieren/MealDeal/src/main/resources/de/mealdeal/ui/styles/tokens.css>) und [R5 Styles](<C:/Users/Marlon Zacchino/Programmieren/MealDeal/src/main/resources/de/mealdeal/ui/styles/search.css>) |
| E12 | [Hauptnavigation](<C:/Users/Marlon Zacchino/Programmieren/MealDeal/src/main/resources/de/mealdeal/ui/main-view.fxml>) und [Startseite](<C:/Users/Marlon Zacchino/Programmieren/MealDeal/src/main/resources/de/mealdeal/ui/home-view.fxml>) |
| E13 | [Search-Layout](<C:/Users/Marlon Zacchino/Programmieren/MealDeal/src/main/resources/de/mealdeal/ui/search-view.fxml>) |
| E14 | [Lokale Katalogauflösung](<C:/Users/Marlon Zacchino/Programmieren/MealDeal/src/main/java/de/mealdeal/catalog/LocalCatalogResolver.java>) |
| E15 | [Wochenplan und lokale Drafts](<C:/Users/Marlon Zacchino/Programmieren/MealDeal/src/main/java/de/mealdeal/ui/controller/WeekPlanController.java>) |
| E16 | [Zusammensetzung und Startabgleich](<C:/Users/Marlon Zacchino/Programmieren/MealDeal/src/main/java/de/mealdeal/ui/ApplicationContext.java>) |

## B. R0–R5 Technical Audit

### R0

**Bestätigt:** Eligibility läuft vor Scoring; Recipe-Ausschlüsse, DishType und die Vereinigung
aller Household-Ingredient-Ausschlüsse werden berücksichtigt. Sichere Alternativen bleiben
zulässig. Pantry arbeitet mit lokalen UUIDs und kanonischen kompatiblen Units. Vorrat wird je
Rezept als gemeinsames Budget verrechnet, nicht mehrfach pro Gruppe verwendet. Portionierung
erfolgt vorher. RecipeScaler bewahrt exakt terminierende Ergebnisse und verwendet DECIMAL128
nur als Fallback bei nicht terminierender Division.

Die Optionssuche maximiert `0,35 * Coverage + 0,15 * Completeness`; sie ist kein separater
zweiter Recommendation-Score. Kleine Bedarfe zuerst sind für eine feste Zuordnung fachlich
begründet. Die optimistische Schranke verwendet Restgruppen gegen vollen Vorrat; stabile
Group-UUIDs und Standard/Position/Option-UUID sichern Ties. Die I2-/Monotonie-Aussage muss auf
den **kombinierten Pantry-Nutzen** bezogen werden; einzelne Teilmetriken sind nicht zwingend
jeweils monoton. Kein zusätzlicher Alternative-Bonus.

Im V1-Profil summieren sich alle Rohgewichte auf 1,00. Der aktive Nenner kann kleiner sein;
insbesondere bleibt RECENT_MEAL_PENALTY ungesetzt. Aggregation mit nichtnegativen Gewichten
und validierten Signalen bleibt in 0..1, ohne dass ein nachträglicher Clamp nötig wäre.
Die allgemeine Profil-API erlaubt andere Gewichtssummen und normalisiert diese korrekt.
Household = 0,70 Minimum + 0,30 Mittel; starke Ablehnung wird vor Mittelung einzelner
Affinitäten erkannt und begrenzt den Gesamtscore auf 0,39. Keine gleichzeitige positive
Household-Strong-Match-Reason. Taste-/Household-Strong-Match werden nicht aus Recipe-Likes
erfunden. Ranking verwendet Score, Missing Count, Coverage, Zeit, Namen und UUID deterministisch.

**Probleme/Risiken:** Exponentielle Worst-Case-Optionssuche bleibt dokumentiert und kann im
synchronen R5-Aufruf die Bedienung blockieren (F08). DECIMAL128 ist definierte endliche
Rechenpräzision, kein Beweis exakter rationaler Ordnung beliebig naher Grenzfälle. Oracle-
Tests sind nützlich, aber kein vollständiger mathematischer Beweis. Kein belegter R0-Fehler,
der eine Algorithmusänderung rechtfertigt.

### R1

**Bestätigt:** Eigene immutable Catalog-Typen; 78 Ingredient- und zehn Taste-Einträge laut
Katalog und Integritätstests. Sprachunabhängige IDs ersetzen keine lokalen UUIDs. V15 ergänzt
nur nullable Textlinks ohne Backfill oder Catalog-FK. Umbenennen/Umkategorisieren erhält den
Catalog-Link im IngredientManagementService. Matching folgt ID → exakter Name → Alias →
normalisierter Name → normalisierter Alias. NFKC, Locale.ROOT, Unicode-Whitespace und deutsche
Umschrift sind zentral; keine Teilstring-, Plural- oder Fuzzy-Automatik. Mehrdeutige Treffer
derselben Stufe bleiben mehrdeutig; keine Mutation durch Resolver.

**Problem:** Namen-Fallback kann eine lokale Entity mit einem **anderen bereits gesetzten
Catalog-Link** als wiederverwendbar liefern (F15). Beispiel: lokaler Name „Tomate“, aber
Catalog-Link `ingredient.milk`; Auflösung von `ingredient.tomato` findet keinen ID-Treffer
und akzeptiert anschließend den Namen. Das verändert heute keine Daten, ist aber eine
gefährliche Vorbedingung für spätere Imports. Linkkonflikt-Policy vor Import-Einsatz entscheiden.

**Risiken:** Deutscher V1-Wortschatz ist keine mehrsprachige Normalisierungsstrategie.
UUID-/Catalog-Trennung ist plattformfähig; spätere Übersetzungen müssen Mehrdeutigkeiten
erneut prüfen. Keine Notwendigkeit für neue Persistenz oder automatische Verknüpfung in R5.

### R2

**Bestätigt:** History und Interaction sind immutable UUID-Events; Feedback ist ein
immutable Objekt als ersetzbarer aktueller Zustand mit stabiler Feedback-UUID. SQL erzwingt
ein Feedback pro Recipe und gültige binäre/Rating-Kombinationen. Rating 3 ohne binäre Präferenz
ist zulässig. Leeren entfernt Feedback. Recipe-Löschung entfernt Feedback per Cascade;
History mit Namenssnapshot und Interactions überleben. Bestehende MealPlan-FKs können die
Recipe-Löschung unabhängig davon blockieren.

MEAL_PLAN-History besitzt eine eindeutige Source-ID; `ON CONFLICT ... DO NOTHING` und erneutes
Lesen machen die Bestätigung idempotent. Mehrere manuelle oder Recommendation-Mahlzeiten sind
fachlich erlaubt. Der R5-Doppelklickschutz gilt nur je Session und Recipe. Score wird als
Dezimaltext, Zeit mit neun Nachkommastellen in UTC gespeichert; übliche Kalenderdaten sind
damit lexikographisch zeitlich sortierbar. Query-Indizes passen zu Recipe-, Session- und
Zeitraumsuchen. Fehlende Recipe-FKs an historischen Events sind bewusst, keine verwaisten
versehentlichen Beziehungen. History-Service ruft weder Inventory noch Consumption Ledger auf.

**Risiken:** Recommendation-Events haben keine persistente Unique-Regel für
Session/Recipe/Action; R5 dedupliziert im lebenden Sessionobjekt. Kein Crash-/Mehrprozess-
Idempotenzversprechen. Die UI bietet für irrtümliche Kochbestätigung noch keine Korrektur
(F12). Die History-/Feedback-/Interaction-Aktionen sind bewusst getrennte Transaktionen;
eine atomare Gesamttransaktion für „Auswahl + Like + Kochen“ wäre fachlich falsch.

### R3

**Bestätigt:** E08 lädt bei nichtleerer Kandidatenliste genau drei Repository-Batches und
liest genau einen Clock-Zeitpunkt. Bei leerer Liste sind es null Abfragen. Snapshots sind
immutable; Scorer kennt keine Repositories. Nie gekocht/14+ Tage → Freshness 1; jetzt oder
Zukunft → 0; dazwischen linear. Maßgeblich ist letztes occurredAt, nicht createdAt oder
Planung. Nur VARIETY_SCORE wird gesetzt; keine doppelte Recency. Recipe Preference hat
Rohgewicht 0,05. Ratings und LIKE/DISLIKE werden korrekt gemappt, Rating gewinnt innerhalb
expliziten Feedbacks. Explicit überschreibt Implicit vollständig. Formel
`0,5 * (selected - dismissed) / (selected + dismissed + 2)` ist schwach und beschränkt.
SHOWN bleibt neutral; SELECTED erzeugt kein COOKED, COOKED kein LIKE. R0-Contexts ohne
Personalisierung behalten ihre aktive Signalmenge.

**Probleme/Risiken:** Drei Batches bedeuten weder drei SQL-Abfragen im ganzen R5-Workflow
noch beschränkte Datenmenge. History-Batch liest alle History-Zeilen der Kandidaten und wählt
in Java die erste; Interaction-Batch lädt auch sämtliche neutralen SHOWN-Events. Keine
gemeinsame Lesetransaktion über drei Repositories; im heutigen synchronen Einzelprozess
vertretbar, bei späterer Parallelisierung erneut prüfen. Siehe F08/F09.

### R4

**Bestätigt:** Neu ausgeführt: 14 Golden Scenarios, elf Invarianten und 42 Sensitivity-Fälle.
Golden Records enthalten erwartete Reihenfolge, erforderliche/verbotene Reasons und fachliche
Begründungen. Sie testen reale relative Aussagen statt durchgehend fragile Dezimal-Snapshots.
R0/R3-Tests ergänzen Eligibility, Shared Budget, Präzision, Feedback-Mapping und Batch-Grenzen.
Der Pantry-Oracle enumeriert Optionen und Bestandsallokationen für 18 kleine Probleme; das
ist stärker als bloßes Spiegeln des produktiven Greedy-Schritts.

**Grenzen der Aussagekraft:**

- Mehrere Golden-Fälle sind grob getrennte Extremfälle oder einzelne Kandidaten. „Perfect
  Pantry gegen gar keinen Vorrat“ zeigt keine Qualität enger Alltagsabwägungen.
- R4-Invarianten mit vorbereiteten Signals testen Scorer-Verhalten, nicht den vollständigen
  Persistenzfluss. Der Test „cookedFrequencyCannotBecomePositivePreference“ speichert nicht
  viele Mahlzeiten, sondern übergibt einen vorbereiteten Snapshot.
- Die 25 Wiederholungen wechseln vor allem zwischen zwei Permutationen; das ist keine breite
  Property-Stichprobe. Oracle: drei Gruppen, zwei Optionen, kleine Ganzmengen in Gramm.
- Die pauschale Aussage „identische Personalisierung ändert nie die relative Rangfolge“ ist
  nur bei gleichen aktiven Nennern beziehungsweise weiteren Bedingungen gesichert (F16).
  Gegenbeispiel auf Profilebene: Kandidat A hat Basiszähler 0,300 bei Nenner 0,60, B 0,249
  bei Nenner 0,50. A liegt mit 0,500 vor B mit 0,498. Identische Freshness 1 und neutrale
  Preference 0,5 addieren jeweils Zähler 0,075/Nenner 0,10: A ≈ 0,535714, B = 0,540000.
  Unterschiedlich verfügbare Zeitdaten können solche Nenner erzeugen. Das folgt aus dem
  vereinbarten Modell und ist zunächst eine zu breite Evaluationsbehauptung, kein Anlass
  für eine stille Gewichtsänderung.
- Keine Aussagen über gemessene Acceptance@3, Entscheidungsdauer oder Pantry-Pflege im
  realen Haushalt. Die Pilotziele 70 Prozent/3 Minuten/60 Sekunden sind nicht gemessen.

**Entscheidung:** Keine Parameteränderung. Heterogene Missing-Data-Kontexte und echte lokale
Nutzung sind die nächsten Evaluationsschritte. „14/14“ darf nicht als Produktfreigabe dienen.

### R5

**Bestätigt:** start lädt Inventory/Recipes, setzt optionale Taste-UUIDs auf +1, delegiert
R3/R0 und schneidet erst das fertig sortierte Ergebnis auf fünf zu. Neue UUID und neue Sets
je erfolgreichem Start. SHOWN erst nach Einfügen der Karten, einmal je Recipe/Session;
SELECTED/DISMISSED mit originalem Score-/Rank-Snapshot. Session-Feedback wird nach erfolgreicher
Persistenz aktualisiert; Ranking bleibt eingefroren. Cooked speichert angefragte Portionen,
Recipe-Name, Zeitpunkt und Source RECOMMENDATION; kein Bestandsabzug.

**Probleme:** F01–F14. Besonders kritisch ist die Lücke zwischen Empfehlung und tatsächlichem
Lesen des Rezepts. Die neue Session ist korrekt kurzlebig; die gesamte Entscheidungssituation
lebt dagegen beim Detailöffnen zu kurz. Das ist etwas anderes als ein Dismiss-Leak.

### End-to-End-Datenfluss

| Übergang | Ergebnis des Audits |
|---|---|
| Inventory → Request | UUID-/Mengen-Snapshot korrekt; Tageswechsel-Abgleich fehlt in R5 (F03). |
| Request → Personalisierung | Servings, weiche Zeit und Taste-IDs; keine impliziten Hard Constraints. Drei R3-Batches. |
| Scoring → Ranking | Pure, deterministisch; gemeinsame Pantry-Budgets; unveränderte Baseline. |
| Ranking → Explainability | Codes korrekt abgeleitet; zuvor konnten frühe positive Codes relevante Warnungen verdrängen, jetzt begrenzt korrigiert. |
| UI → SHOWN | Nach Einfügen, nicht nach gemessener Sichtbarkeit im Scroll-Viewport (F10). |
| UI → SELECTED/DISMISSED | Getrennte Events und Session-Deduplizierung; Auswahl wird vor erfolgreicher Detailnavigation geschrieben (F10). |
| UI → Feedback | Persistentes Recipe-Feedback, exklusiv; Clear entfernt Zustand. Laufende Score-Reasons bleiben Snapshot. |
| Detailöffnung | Portionen/Optionen und Herkunft fehlen (F01/F02). |
| UI → COOKED | Nur History, einmal je Session; kein Undo in UI (F12). |
| Nächster Lauf | Neue Session; History/Feedback/Interaktionen erneut gebündelt eingelesen. Historisches Dismissal ist nur Preference. |

## C. Confirmed Bugs

### Gemeldetes Dismiss-Verhalten: Session-Bug nicht bestätigt

`dismissedRecipeIds` ist ein Instanzfeld von RecommendationSession. `start` konstruiert
jedes Mal eine neue Session; der Controller ersetzt `session` nach erfolgreichem Start und
leert vor dem Rendering die alten Karten. Die persistierte Interaction-History wird nicht
als Ausschlussliste verwendet. Weder statischer Zustand noch globaler Dismiss-Cache gefunden.

Reproduzierbar sind zwei verschiedene Fälle:

1. Drei Kandidaten: A wird SHOWN und DISMISSED; in Runde 1 unsichtbar. Runde 2 hat andere
   UUID; A ist wieder sichtbar, sein Score etwas niedriger. Kein DISLIKE, keine History.
2. Sieben ansonsten gleiche Kandidaten: A fällt nach einem DISMISSED hinter sechs neutrale
   Kandidaten. Es bleibt im vollständigen Ranking auf Rang sieben; nur die Top 5 sind sichtbar.

Zusätzlich belegt ein SQLite-Integrationstest mit neuer Database-/Workflow-Instanz, dass
gespeichertes Dismissal kein dauerhaftes UI-Verbergen bewirkt. Ein Fehler beim Speichern des
Dismissals hält die Karte sichtbar; erfolgreicher Retry schreibt genau ein Ereignis.

**Fix:** Keine Session-/Algorithmusänderung. Testlücke geschlossen und Top-5-Semantik erklärt.
Eine Garantie „A muss in jeder nächsten Top 5 erscheinen“ würde dem Ranking-Vertrag widersprechen.

### Weitere bestätigte Anzeigefehler und kleine Fixes

- **F05 Warnung abgeschnitten:** Vier früh angefügte positive Reasons konnten TIME_OVER_LIMIT
  vollständig verdrängen. Jetzt negative Hinweise zuerst, gleiche Texte dedupliziert, maximal
  vier. Regressionstest mit vier positiven Codes vor der Zeitwarnung.
- **F06 Dark Mode:** R5-Positivtext verwendete den dunklen Flächentoken als Text. Jetzt für
  R5-Positivgründe und aktives Feedback vorhandenes `-md-text` im Dark Mode. Übrige betroffene
  Bestandskomponenten bleiben als offenes Finding dokumentiert.
- **F07 Empty State/Zeitsprache:** „Maximale Zeit“ suggerierte harten Filter; „Passe Zeit oder
  Geschmack an“ war beim leeren Resultat sachlich untauglich, weil beide nur ranken. Jetzt
  Zeitwunsch mit Erklärung und getrennte Texte für keine Kandidaten/alles ausgeblendet.
- **F11 Feedback:** Rating-only-Zustand blendete „Feedback entfernen“ aus. Clear hängt jetzt
  an gespeichertem Feedback insgesamt; Rating wird textuell angezeigt. Aktive binäre Meinung
  ist zusätzlich durch Häkchen und Beschriftung erkennbar.
- **F13 Meldungen:** Koch-Erfolg nutzte die rote Fehlerklasse. Erfolg erhält eine neutrale
  vorhandene Tokenfläche; alte Meldungen werden vor Feedback/Dismiss/Cooked-Aktionen entfernt.
  Fehler beim neuen Lauf blenden den alten Inventory-Hinweis aus.
- **F14 Eingabe/Textsicherheit:** Personeneditor wird explizit auf 1..999 Ganzzahl geprüft,
  statt ungültige Werte durch den Spinner-Konverter bis zum generischen Laufzeitfehler zu
  reichen. Fehlende History heißt nun „nicht erfasst“, Teilmengen werden als Mengendeckung
  statt als Anzahl vorhandener Zutaten beschrieben.

## D. UX Findings

Severity bezeichnet die Bedeutung **vor** Korrektur; Status trennt erledigte und offene
Punkte. Aufwand: S = wenige Stunden, M = etwa 1–3 Arbeitstage, L = größere Folgephase,
jeweils grobe Schätzung inklusive sinnvoller Tests. Kein offener BLOCKER im Sinne eines
nachgewiesenen Datenverlusts in R5; die HIGH-Punkte begründen dennoch die negative Empfehlung.

| Finding / Bereich | Severity / Status | Evidence und technische Ursache | User Impact | Empfehlung | Aufwand / Änderungsrisiko |
|---|---|---|---|---|---|
| F01 Detailübergabe | HIGH, offen | E01.select liefert nur Recipe; E05:43–49 übergibt nur dieses Objekt; E06:108–118 und 175ff starten Standardportionen/-optionen. | „Vorrat reicht für 4“ kann beim Öffnen andere Mengen/Zutaten zeigen. Empfehlung ist nicht zuverlässig ausführbar. | Temporären Detailkontext aus Servings und vorgeschlagenen Group/Option-UUIDs übergeben; Recipe nicht umschreiben. | M / mittel: Navigation und temporäre Detailzustände. |
| F02 Rückkehr zur Runde | HIGH, offen | E05:64–80 lädt jedes Mal neue FXML/Controller; E06:122 navigiert fest zu RECIPES. Keine Origin-/Back-State-Übergabe. | Öffnen verliert Liste, Eingaben, Dismiss- und Cooked-Markierungen. Danach kein Feedback/Kochen in derselben Runde möglich. | Rückkehrkontext zur laufenden Runde einschließlich Scrollposition/Eingaben; Lebensdauer explizit festlegen. | M / mittel: Lifecycle und Edit/Delete-Invalidierung. |
| F03 Vorrat nach Datumswechsel | HIGH, offen | E01:96 liest Repository direkt; consumePastEntries existiert nur bei App-Start, Inventory-Refresh und inventarbewusstem Einkauf. | Über Nacht offen gebliebene App empfiehlt auf noch nicht abgezogenem Planverbrauch; Öffnen von Inventar ändert danach die Grundlage. | Bestehenden idempotenten Tagesabgleich vor dem neuen Pantry-Snapshot einbinden, mit Clock-Integrationstest. | S–M / mittel: expliziter Bestands-Schreibzeitpunkt; keine Kopplung an COOKED. |
| F04 Produktnavigation | MEDIUM, offen | E12 enthält acht Punkte; Start-CTA openSearch führt weiterhin SEARCH zu. | Nutzer muss zwischen zwei fast gleich klingenden Entscheidungswegen wählen; Pantry-first schwer auffindbar. | Option B aus Abschnitt F, inklusive Home-CTA. | M / mittel: Produktentscheidung. |
| F07 Anfrage/Empty State | MEDIUM, behoben | E09 behandelt Zeit/Tastes als weiche Signale, altes E03/FXML empfahl dennoch Filteränderung bei leerem Ergebnis. | Falsche Erwartung an Ausschlüsse und untaugliche Abhilfe. | Implementierte präzisere Texte manuell abnehmen. | S / gering. |
| F10 Event-Semantik im UI | MEDIUM, offen | E03.renderSession fügt alle Karten ein und schreibt synchron SHOWN; Fehler versteckt beim Start die Liste. E03.openRecipe schreibt SELECTED vor Detailnavigation. | SHOWN kann trotz fehlender tatsächlicher Sichtbarkeit entstehen; fehlgeschlagenes Öffnen zählt als Auswahl. | SHOWN als „bereitgestellte Ergebnisliste“ präzise definieren oder Sichtbarkeitsgrenze ändern; SELECTED auf erfolgreiche Navigation abstimmen. | M / mittel: Eventvertrag, kein stiller Analytics-Umbau. |
| F11 Feedbackzustand | MEDIUM, teilweise behoben | E04 verwendete nur getValue statt gesamtem Feedback; binärer Zustand nur per Farbe. | Rating konnte nicht entfernt werden, Zustand schwer erkennbar. | Implementierte Anzeige/Clear prüfen; langfristig gleiche Aktion auf Detailseite. | S / gering. |
| F12 Cooked-Aktion | MEDIUM, offen | E01.recordCooked sofortiger History-Write; Session enthält Rückgabe, UI kennt keine Rücknahme. Neue Runde löscht Doppelklickschutz. | Versehentlicher Klick bleibt als Mahlzeit gespeichert; gleiches Gericht kann in neuer Runde nochmals bestätigt werden. | Kleine Rücknahme über bestehende History-ID; kanonischer Detailflow, kein pauschaler tagesweiter Rezept-Dedup. | M / mittel: Korrektur und Lebensdauer entscheiden. |
| F14 Eingabe/Textsicherheit | MEDIUM, behoben | E03 verwendete den Spinner-Konverter, E04 setzte fehlende History mit „nie gekocht“ gleich und Mengencoverage mit Zutatenanzahl. | Unverständliche Eingabefehler und zu weitgehende Aussagen über tatsächliches Kochen/Vorrat. | Explizite Ganzzahlprüfung, quellenbezogene History- und Mengentexte implementiert. | S / gering. |
| F17 Action Overload | MEDIUM, offen | E04 erzeugt fünf Buttons, mit Clear sechs, pro Karte; fünf Karten möglich. | 25–30 Ergebnisaktionen zusätzlich zum Formular; Öffnen, Ablehnen, Bewerten und Kochen konkurrieren. | Öffnen primär, Dismiss zurückhaltend; Kochen/Feedback langfristig im gemeinsamen Detailflow. | M / mittel, zusammen mit F01/F02. |
| F18 Ungespeicherte Eingaben | MEDIUM, offen, Bestand | E15.openRecipe ohne Draft-Guard; E05 ersetzt View. CreateRecipeController schützt Sidebar-Navigation ebenfalls nicht. | Wochenplan-/Formularentwürfe können beim Seitenwechsel ohne Hinweis verloren gehen. | Kleiner allgemeiner Leave-Guard nur für geänderte Entwürfe, nach Entscheidung zum Navigationsmodell. | M / mittel. |
| F19 Suchsprache/Verwaltung | LOW, offen | E13 zeigt AND/OR/RANKING und PERFECT/GOOD/PARTIAL; Zutaten-FXML „zentrale Zutat“, „Fallback“. | Interne Begriffe statt Alltagsaufgabe. | „Alle“, „Mindestens eine“, „Beste Treffer“; „Sonstiges bleibt erhalten“. | S / gering, keine Suchlogikänderung. |

## E. UI/Design Findings

| Finding | Severity / Status | Evidence / Ursache | User Impact und Empfehlung | Aufwand / Risiko |
|---|---|---|---|---|
| F05 Sichtbare Warnungen | HIGH, behoben | E09 hängt Reasons in fester Reihenfolge an; E04 begrenzte stumpf die ersten vier. | Relevante Gegenargumente nicht sichtbar. Neue begrenzte Darstellung getestet; harte Konflikte künftig explizit priorisieren, falls R5 Household-Eingaben erhält. | S / gering. |
| F06 Dunkler Akzent als Text | HIGH, R5 teilweise behoben | E11 Dark-Akzent #3b111f auf Surface #241e21 bzw. Soft #51263a. Auch recipe-type-badge, detail-step-position und Planlinks verwenden diesen Token. | Nominale sRGB-Kontraste etwa 1,00:1 bzw. 1,31:1. R5-Gründe/Feedback korrigiert; übrige Stellen gezielt auf vorhandene lesbare Texttokens umstellen. | S / gering; keine neue Palette. |
| F13 Erfolg im Fehlerstil | LOW, behoben | E03.showMessage verwendete immer form-message; components.css gibt dieser rote Semantik. | Erfolg sah wie Fehler aus. Eigene Erfolgsklasse aus vorhandenen Tokens. | S / gering. |
| F20 Fokus und Re-Rendering | MEDIUM, offen | controls.css ersetzt Button-Hintergründe ohne eigene Fokusregeln; Fokusfarbe im Dark Mode ebenfalls dunkler Akzent. E03 baut nach jeder Aktion sämtliche Karten neu. | Tastaturnutzer können Fokus verlieren; sichtbarer Fokus nicht statisch gewährleistet. Fokusdarstellung und Wiederherstellung am betroffenen Control definieren. | M / mittel; manuell prüfen. |
| F21 Compact/geringe Höhe | MEDIUM, offen | E13 Aktions-HBox mit drei langen Buttons und weitere HBox für drei Suchmodi; main-view Sidebar ohne ScrollPane; Fenster-Minimum 900×600. | Risiken für abgeschnittene Controls bei geringer logischer Breite/Höhe. FlowPane/vertikale Modi gezielt prüfen; Sidebar bei Höhe scrollbar. | M / gering–mittel. |
| F22 Semantische Labels | MEDIUM, offen | FXML-Labels überwiegend ohne labelFor; Inventory-Menge/Unit nur Prompt. R5-Buttons wiederholen dieselben Namen je Karte. | Zuordnung ohne Sichtkontext schwerer. Labels/accessibleText mit Rezeptbezug ergänzen; keine Tooltip-Flut. | S–M / gering. |
| F23 CSS-Hygiene | LOW, offen | content-card-header-title, meal-plan-day-name mehrfach mit verschiedenen Properties; form-error/selection-chip ohne gefundene Stildefinition. Shadow-RGBA zweimal hart codiert. | Wartungsaufwand und uneinheitliche Fehlermeldung/Chips. Rollen der Hooks prüfen, getrennte Regeln gezielt zusammenfassen; keine Komplettbereinigung. | S / gering. |

R5 nutzt weiterhin Bordeaux und vorhandene Flächen. Für die korrigierten Dark-Mode-Texte
ergibt `-md-text` nominal etwa 13,88:1 auf Surface und 10,55:1 auf Soft. Das sind Berechnungen
aus den deklarierten Vollfarben, keine Screenshotmessungen und keine WCAG-Zertifizierung.

## F. Navigation Assessment

| Aktueller Eintrag | Tatsächliches Nutzerziel | Überschneidung | Empfehlung |
|---|---|---|---|
| Start | Überblick, heute entscheiden, Plan ansehen | Wiederholt Search/Recipes/WeekPlan | Als Übersicht behalten; Hauptaktion in gemeinsame Entscheidungsseite. |
| Gerichte | Rezepte anlegen, bearbeiten, nachschlagen | Detailöffnung aus Suche/Empfehlung | „Rezepte“ als klaren Verwaltungsbegriff erwägen; eigene Bibliothek behalten. |
| Gericht finden | Gezielte Zutaten-/Geschmackssuche | Gleiche Oberaufgabe wie Empfehlungen | Gemeinsamer Nutzerweg mit Recommendation als Standard. |
| Empfehlungen | Offene Frage „Was soll ich kochen?“ | Gericht finden | In dessen Hauptworkflow integrieren. |
| Wochenplan | Mahlzeiten terminieren | Heute-/Wochenübersicht auf Start | Behalten; Details mit Herkunft öffnen. |
| Zutaten | Zutatenstamm und Kategorien pflegen | Inventar fügt Mengen zu diesen Zutaten hinzu | Als „Zutaten verwalten“ nachgeordnet erreichbar; kein eigener Hauptworkflow gleicher Gewichtung nötig. |
| Inventar | Vorhandene Mengen pflegen | Zutatenstamm, Einkauf mit Bestandsabzug | „Vorräte“ empfehlen; primäre Aufgabe für Pantry-first behalten. |
| Einkaufsliste | Fehlende Mengen aus Planung ansehen | Vorräte und Wochenplan | Behalten; Nähe zu Wochenplan statt technischer Reihenfolge. |

**Option A – zwei Einträge behalten.** Vorteil: geringe Änderung, unterschiedliche Contracts
bleiben sichtbar, kein kombinierter Controller. Nachteil: Nutzer muss interne Produktgrenze
verstehen; Home priorisiert aktuell Search, zusätzlicher mobiler Navigationspunkt. Wartbar,
aber schwach für Recommendation-first. Nur als ausdrücklich befristeter Übergang sinnvoll.

**Option B – „Gericht finden“, Empfehlungen primär, Suche aufklappbar sekundär.** Vorteil:
eine erkennbare Aufgabe; kleiner erster Formularumfang; Pantry wird automatisch genutzt.
Auf schmalen Ansichten natürliche vertikale Reihenfolge. Nachteil: präzise Suche wird weniger
auffällig, und identisch benannte Geschmacksfelder dürfen nicht gegenseitig ihren Zustand
verändern. Wartbar mit einer schlanken Seitenkoordination und zwei getrennten Workflows;
kein Kombinieren der Scorer und kein großer Universalcontroller erforderlich.

**Option C – eine Seite mit zwei Modi/Tabs.** Vorteil: klare Trennung innerhalb derselben
Aufgabe, bessere Auffindbarkeit für regelmäßige Suche, gut testbare Moduszustände. Nachteil:
beide Modi wirken gleichrangig; zusätzliche Auswahl vor einer offenen Kochentscheidung;
State-/Scroll-/Reset-Policy weiterhin erforderlich. Mobile Eignung gut mit zwei kurzen Modi,
bei zusätzlichen Tabs schnell schlechter.

**Klare Empfehlung: B.** Überschrift „Gericht finden“, Frage „Was möchtest du kochen?“ bzw.
„Was passt heute?“, Personen und optionaler Zeitwunsch, Geschmack eingeklappt, Hauptaktion
„Vorschläge anzeigen“. Darunter „Gezielt nach Zutaten und Geschmack suchen“ als eigener
aufklappbarer Bereich. Separate Ergebniszustände, keine Vermischung von Ranking und harten
Suchmodi. Home-CTA führt hierhin. Wenn spätere Nutzung häufige Moduswechsel zeigt, C neu
bewerten. Der Umbau bleibt eine vor Umsetzung abzustimmende Produktentscheidung.

## G. Recommendation UX Assessment

- **Inputs:** Zwei Personen als Standard und automatisch geladenes Inventory ermöglichen
  einen kurzen Start. Zeit/Taste bleiben optional und weich. Keine persistierten individuellen
  Haushalte vorhanden; Personenanzahl bezeichnet Portionierung, nicht mehrere Geschmacksprofile.
  R5 erlaubt MAIN, SIDE und DESSERT im selben Ranking. Für „Was koche ich?“ beobachten, ob eine
  Beilage allein irritiert; keine stille MAIN-Einschränkung einführen.
- **Results:** Fünf als V1-Entscheidungsmenge vertretbar, nicht empirisch optimiert. Alle werden
  untereinander gezeigt; nach Dismiss wird nicht automatisch nachgefüllt. Neue Runde berechnet
  neu und kann gleiche Kandidaten enthalten. Leeres Inventory liefert weiterhin Vorschläge.
  Nur Nullmengen zählen derzeit nicht als inventoryEmpty – kleiner zusätzlicher Textgrenzfall.
- **Reasons:** Keine rohen Scores oder Prozentversprechen. Vier Hinweise begrenzen Leselast.
  Nach Fix gehen negative Hinweise vor. Fehlende Zutatenmengen und konkrete vorgeschlagene
  Alternativen bleiben aber nicht ausreichend handlungsfähig erklärt (F01). Die Reason-Liste
  bleibt Berechnungssnapshot: nach neuem DISLIKE kann noch ein alter LIKE-Reason stehen.
  Aktuelles Feedback ist separat beschriftet; Snapshot-Reasons sollten künftig sichtbar als
  „bei Berechnung“ erkennbar oder nur der betroffene Meinungstext aktualisiert werden.
- **Actions:** „Gericht öffnen“ gehört an jede Karte. Dismiss als zurückhaltender Textbutton
  sinnvoll; ein reines unbeschriftetes Icon wäre weniger auffindbar. Kein zusätzlicher Button
  allein wegen vorhandener Service-API. Feedback und Kochen langfristig im gemeinsamen Detail.
- **Feedback:** Persistenz über Runden ist gewollt. LIKE/DISLIKE sind exklusiv und löschbar.
  Ein erneutes Drücken derselben Meinung ist derzeit ein erneutes Update, kein Toggle-Clear.
  Expliziter Clear ist verständlich, braucht aber Platz. Rating-only wird jetzt korrekt gezeigt.
- **Dismiss:** Nur aktuelle Runde verbergen; historische schwache Wirkung bleibt. „Ausblenden“
  klar von „Gefällt mir nicht“ unterscheiden. Keine dauerhafte Sperre versprechen.
- **Cooked:** Tatsächliche Kochbestätigung; kein Like, SELECTED oder Verbrauch. Aktuell zu
  leicht vor dem Lesen auslösbar und ohne Undo. Detail ist der bessere kanonische Ort.

Kanonische Recipe-Detailaktionen langfristig: Kochen bestätigen, Feedback verwalten, Einplanen
als nachfolgende Produktentscheidung; Bearbeiten/Löschen als Verwaltungsaktionen sekundär.
Alle Einstiege sollen dieselbe Semantik erhalten. Herkunft steuert ausschließlich Rückweg,
temporäre Portionen/Optionen und ggf. Recommendation-Events, nicht die Bedeutung von LIKE.

## H. Design-System Assessment

| Bereich | Befund und Empfehlung |
|---|---|
| Typography | Segoe UI, 16px Basis, 36px Seitentitel, 23px Kartentitel, 21px Abschnittstitel; wide moderates Wachstum. Hierarchie klar; kein Rewrite. Lange Rezeptnamen manuell prüfen. |
| Spacing | Gemeinsame Flächen 30px Innenabstand, Unterkarten 15/17px; R5 22/26px und 12px zwischen Karten. 6/8/10/12/13/14/16/18/22/24/28px kommen gemischt vor. Keine einheitliche Spacing-Token-Skala, aber keine notwendige neue Dependency. |
| Cards | content-card, sub-card, expandable-card werden wiederverwendet. R5-Buttons treiben Dichte stärker als reine Card-Abstände. Home/Plan 8px Blockabstand bewusst beibehalten, visuell prüfen. |
| Controls | Meist ausreichend gepolsterte Textbuttons und 44px Mindesthöhe für Inputs; keine pauschale feste Maximalhöhe. Badge-/Focus-Kontrast bleibt teils problematisch. |
| CSS Architecture | Ein Entry-Point, neun geordnete Module, responsive zuletzt. Kein setStyle und kein !important gefunden. Feature-Module sinnvoll; R5 in search.css ist bei gemeinsamer Aufgabe vertretbar. Form-Styles in recipes.css und Save-Message in components.css sind bestehende Grenzunschärfen. |
| Override Chains | content-card → recommendation-card → wide content-card verändert Padding mit höherer Spezifität. Wide/extra-wide Klassen gelten gleichzeitig; extra-wide gewinnt durch Reihenfolge. Absicht nachvollziehbar, aber manuell auf unnötig hohe Karten prüfen. |
| Responsive | Logische Scene-Grenzen <1100, ab1440, ab2100; kein DPI-Hack. Search-Grid 1/2 Spalten, Recipe-/Inventory-/Kategorie-Grid sowie Detail reagieren über Controller auf Root-Klassen. R5 FlowPane wrappt Formular und Aktionen, Card-Header bleibt HBox. Search-Aktionsleiste und Formular-Allgemeinbereich wrappen nicht. |
| Widths | Normal 1080/Detail1240, wide1320/1440, extra-wide1640/1760; CSS-Padding 42, compact28, wide46, extra54. R5 bleibt einspaltig, sehr breite Cards können trotz Textbreitenlimit leer wirken. Keine pixelgenaue Behauptung ohne Abnahme. |
| Dialoge/Overlays | Recipe- und Kategorienlöschung besitzen Owner/Bestätigung. Owner allein überträgt keine theme-dark-Rootklasse; eigener Dialog-Theme-Pfad nicht gefunden. WeekPlan-Save liegt separat über Scrollinhalt mit Abstandshalter; niedrige Höhe prüfen. |
| Light/Dark | Tokenarchitektur gut; Akzent dient zugleich Fläche, Text und Fokus, was im Dark Mode kollidiert. Vorhandene Texttokens gezielt nutzen; Header-Bordeaux erhalten. Disabled-Zustände kommen überwiegend aus JavaFX-Skin und brauchen manuelle Prüfung. |

Keine formale Accessibility-Zertifizierung. Die bestehenden FXML/CSS-Tests prüfen Ressourcen,
Parser und Struktur, nicht Sichtbarkeit, Fokuswanderung oder tatsächliches Abschneiden.

## I. Architecture Findings

| Finding | Severity / Evidenz | Wirkung / technische Ursache | Empfehlung | Aufwand / Risiko |
|---|---|---|---|---|
| F08 N+1 und FX-Thread | MEDIUM, E07/E03 | findAll: 1 ID-Abfrage + 4 Abfragen je Recipe + eine je IngredientGroup. Scoring und Eventwrites laufen synchron im FX-Handler. UI-Latenz wächst mit Bibliothek/Alternativen. | Zuerst tatsächliche Laufzeiten mit synthetischen größeren Daten messen; Recipe-Batch-Lader, danach schmale Hintergrundberechnung mit Ergebnis-/Fehlerübergabe. | M / mittel; keine Threads ohne Session-Lifecycle. |
| F09 Batch ≠ beschränkte Arbeit | MEDIUM, E08 + R2 SQLite-Repositories/E01 | Drei R3-Batches, aber ganze History/Interaction-Listen. R5 lädt Feedback der fünf sichtbaren Recipes zusätzlich ein zweites Mal. | SQL nur letzte Mahlzeit und relevante Counts liefern lassen; später Feedback-Snapshot wiederverwenden. Drei-R3-Reads-Vertrag präzisieren. | M / mittel: Repository-API und Tests. |
| F15 Widersprüchlicher Catalog-Link | MEDIUM, E14 | Alle Entities gehen nach fehlendem ID-Match in Namen-Fallback, auch anders verlinkte. | Explizite Konfliktbehandlung festlegen; vor Importverwendung testen. Aktuell kein produktiver Import/Auto-Merge. | S–M / mittel: fachliche Resolver-Policy, deshalb unverändert. |
| F16 R4-Invarianten zu allgemein | MEDIUM, R4InvariantTest/evaluation.md | Gleiches Feedback nur bei gleichen Nennern/Ties getestet; Dokumentation verallgemeinert. | Aussagen einschränken, Gegenbeispiel aus B aufnehmen; heterogene verfügbare Signale testen. Kein Parameterwechsel. | S / gering. |

Controllerbewertung: RecommendationController ist nach Fix 267 Zeilen und delegiert Fachlogik
sauber; enthält UI-State, Renderkoordination, Inputvalidierung und Fehlerbehandlung. CardFactory
baut Views und übersetzt Codes. SQL liegt nicht im Controller. RecipeDetail hat 421, Ingredients
449, Inventory 378 Zeilen; Größe allein ist kein Refactor-Grund. Vorhandene spezialisierte
Builder für Search/Form/WeekPlan sind sinnvoll und wurden nicht zurückgebaut. Repository-
Aufrufe in älteren CRUD-Controllern sind kein SQL, bleiben aber enger gekoppelt als R5.

State-Lebensdauer: selectedTastes, Zeit, Portionen und Resultate leben im Controller; Dismiss,
Shown, Selected und Cooked-Deduplizierung in der Session. Feedback wird dauerhaft gespeichert
und als Darstellungssnapshot in die Session kopiert. Kein globaler R5-State gefunden.
Bei Fehleingabe bleibt die alte Runde bestehen; bei erfolgreichem Neustart wird sie ersetzt.
Ein bewusst neuer Lauf ist der richtige Ort für neue Scores; reine Feedbackaktionen dürfen
die laufende Liste nicht umsortieren. Künftige Hintergrundarbeit darf diese mutable Session
nicht gleichzeitig von mehreren Threads verändern.

## J. Test Gaps

**Warum konnte die Dismiss-Beobachtung trotz Tests auftreten?** Der alte Test
`shownSelectionAndDismissalAreExplicitAndDeduplicatedWithinSession` erzeugt sieben Kandidaten
und prüft nach der zweiten Runde ausschließlich die kumulierte SHOWN-Zahl 10. Er prüft weder
die Identität des ausgeblendeten Recipes noch volle Kandidatenmenge, Session-Wechsel und
Top-5-Grenze gemeinsam. Zehn SHOWN können zu anderen fünf Rezepten gehören.

Neue Tests trennen diese Fälle und belegen den Vertrag, statt die manuelle Vermutung durch
einen künstlichen Algorithmusfix zu erzwingen:

- `dismissedRecipeReturnsInNewSessionWhenItStillRanksInVisibleResults`.
- `historicalDismissalCanMoveRecipeBelowTopFiveWithoutExcludingIt`.
- `persistedDismissalIsOnlyPreferenceWhenWorkflowAndSessionAreRecreated`.
- `failedDismissalKeepsRecipeVisibleAndRetryRecordsOneEvent`.

Fünf neue Präsentationstests prüfen Personenvalidierung, erhaltene Zeitwarnung trotz langer
Reason-Liste, deduplizierten Household-Text, ehrlichen Missing-History-Text und Rating-only-
Feedback. Die gesamten Tests benötigen keine Desktopbedienung.

Weitere sinnvolle Lücken: Detailübergabe mit Nichtstandardportionen/-alternativen; vollständiger
Roundtrip einschließlich Feedback/Dismiss/Scroll; Datumswechsel vor Recommendation; teilweise
fehlgeschlagener SHOWN-Batch; erfolgreiche Auswahl vs. fehlgeschlagene Detailnavigation;
Koch-Undo und neue Session; unterschiedliche aktive Scoring-Nenner; Catalog-Link-Konflikte.
FXMLResourceTest heißt nicht, dass FXML visuell abgenommen wurde. Der bisherige
RecommendationControllerTest prüfte nur Zeitparser und einzelne Reason-Texte, keinen
Controller-Lifecycle. Ein eigener JavaFX-unabhängiger Rückkehrzustand würde aussagekräftige
Tests ermöglichen, ohne Klickautomation einzuführen.

## K. Implemented Minor Fixes

Nur bestehende ungetrackte R5-Dateien, deren CSS-Modul und R5-Dokumentation erweitert:

1. Neun neue Regressionstests, davon drei zur Session-/Top-5-Trennung und einer zum Writefehler.
2. Priorisierung negativer und Deduplizierung identischer sichtbarer Reason-Texte.
3. Präzisere Mengen-/History-/Dismiss-Sprache und weicher Zeitwunsch.
4. Getrennte Empty-State-Texte für fehlende Kandidaten und vollständig ausgeblendete Runde.
5. Rating-only-Feedback sichtbar/entfernbar; binärer aktiver Zustand auch textlich markiert.
6. Lesbare R5-Texttokens im Dark Mode und neutraler Koch-Erfolgshinweis.
7. Explizite deutsche Personenvalidierung und Entfernen veralteter Aktionsmeldungen.

Keine Gewichte oder Core-Berechnungen geändert. Keine neue Library, kein globales State-System,
keine Schemaänderung. Keine neue Navigation oder Recipe-Detail-Semantik ohne Entscheidung.

## L. Recommended Follow-Up Plan

### MUST BEFORE R5 COMMIT

1. **F01/F02 gemeinsam konkretisieren:** schmaler, temporärer Detail-/Rückkehrkontext.
   Übergeben: Recipe, gewünschte Portionen, vorgeschlagene Optionen und Rückweg zur lebenden
   Runde. Beim Rückweg weder neu scoren noch SHOWN erneut schreiben. Persistiertes Recipe
   bleibt unverändert. Nach Bearbeiten/Löschen explizit invalidieren/aktualisieren.
   Alternative: allgemeiner Navigation-Stack für alle Seiten; breiterer Scope und Risiko.
   Empfehlung: zunächst begrenzter Rückkehrkontext, später allgemeiner Draft-Guard.
2. **F03 beheben:** vorhandenen Tagesabgleich vor neuem Inventory-Snapshot am Application-
   Workflow ausführen. Alternative wäre zentraler Abgleich bei allen Bestandslesern;
   größere Änderung. Empfehlung: schmale explizite Einbindung im Workflow, nicht im Scorer.
3. Verbleibende Dark-Mode-Badges/Details gezielt korrigieren und A–L manuell abnehmen.
4. Neue Roundtrip-/Datumswechseltests, vollständiger Java-25-Lauf, Package, diff --check.

Die Punkte 1/2 berühren Navigation, State- und Persistenz-Lebensdauer. Der Auftrag begrenzt
Phase B auf kleine eindeutige Fixes; AGENTS.md verlangt bei wesentlich verschiedenen
Architektur-/Bedienalternativen Abstimmung. Deshalb hier konkrete Empfehlung statt
eigenmächtiger Ausweitung. Die Entscheidungsvorlage ist vollständig; Audit und erlaubte
kleine Korrekturen sind abgeschlossen.

### SHOULD NEXT

- Option B der gemeinsamen Seite samt Home-CTA und eigenständigen Suchzuständen.
- Cooked-Rücknahme und kanonisches Feedback/Kochen im Detail.
- Tastaturfokus, Label-Zuordnung, Compact-Aktionsleisten, niedrige Sidebar und Dialogthemes.
- R4-Aussagen auf tatsächlich getestete Bedingungen begrenzen.
- N+1-/Historien-Laufzeit messen und gezielt reduzieren; keine pauschale Optimierungswelle.

### LATER

- Freiwillige lokale Pilot-Abnahme der tatsächlichen Entscheidungsgüte; keine automatische
  Telemetrie. 14 Tage/0,05/Formel bis zu belastbarer gegenteiliger Evidenz beibehalten.
- Resolver-Konfliktpolicy vor Catalog-Import; Mehrsprachigkeit bei tatsächlichem Bedarf.
- Begriffe „Rezepte“, „Vorräte“, „Zutaten verwalten“ mit Navigation zusammen vereinheitlichen.
- Einplanen aus Detail und History-Korrekturansicht nur mit geklärtem Produktumfang.

### DO NOT BUILD

Keine ORMs, DI-/State-/Reactive-/CSS-/Navigation-Frameworks, Eventbus, WebView, ML, neue
Scoreparameter, künstliche Dismiss-Ausschlüsse, Analytics oder Telemetrie. Kein kompletter
Design-System-Rewrite, kein globaler View-Cache und keine Session-Persistenz nur zum Fixen
eines lokalen Rückwegs. Kein automatischer Bestandsverbrauch bei Cooked.

## M. Manual UI Acceptance Checklist

Offene Findings sind **zu bestätigende aktuelle Abweichungen**, nicht als bereits korrigierte
Sollzustände markiert. Verwendung einer Testdatenbank mit mindestens einem Rezept, dessen
Standardportionen vom Request abweichen und dessen verfügbare Alternative nicht Standard ist,
sowie sechs weiteren ähnlich passenden Rezepten empfohlen.

| Bereich | Aktion | Erwartetes Verhalten / Abnahmekriterium | Visuell beachten |
|---|---|---|---|
| A Navigation | Start, alle acht Einträge, dann Rezept aus Search/Recommendation/WeekPlan öffnen und Zurück. | Richtige Seiten/aktiver Eintrag. Aktuell führt Zurück zu Gerichte und verliert Ursprung (F02); nach Fix zur ursprünglichen Runde. | Aktive Markierung, gleiche Titel, keine unverständlichen Sprünge. |
| B Recommendation | Ohne optionale Werte starten, dann Personen/Zeit/Geschmack ändern und erneut starten. | Höchstens fünf, Reihenfolge erst beim ausdrücklichen Start neu. Zeit ist weicher Wunsch; Eingaben 0/leer/Text werden verständlich abgewiesen. | Karten getrennt, Metadaten korrekt, keine Scores/Prozentwerte, Warnung auch bei vielen positiven Gründen. |
| B Empty State | Keine Recipes/keine Zutatenstruktur; danach alle Karten einer gültigen Runde ausblenden. | Unterschiedliche, passende Hilfe für beide Fälle. | Keine alte Ergebnisliste oder irreführender Zeitfilter-Tipp. |
| C Search | Zutaten allein, Taste allein, kombiniert; alle drei Modi und Reset. | Bestehende UUID-/Schnittmengenlogik unverändert; Reset entfernt beide Auswahlen. | Geschlossene Bereiche auffindbar, Aktionsleiste und Modusbeschreibungen lesbar. |
| D Recipe Detail | Empfehlung für 4 bei Standard 2 mit Nichtstandardalternative öffnen. | Nach F01-Fix dieselben vier Portionen und empfohlenen Optionen. Aktuell Standards: bekannte Abweichung. | Mengen/Units, Dropdownauswahl, lange Namen, klare Öffnen-/Bearbeiten-Hierarchie. |
| E Feedback | Gefällt mir → neue Runde → Gefällt mir nicht → Clear → neue Runde. | Meinung bleibt über Runden; exklusiv, dann entfernt. Laufendes Ranking bleibt stehen. | Häkchen/gespeicherter Text eindeutig, Clear sichtbar. Alte Berechnungs-Reasons ggf. weiterhin Snapshot. |
| F Dismiss | Bei höchstens fünf Kandidaten A ausblenden, dann neue Runde. Mit sieben Ties wiederholen. | A verschwindet nur aktuell; bei <=5 wieder sichtbar, bei sieben darf es unter Top 5 fallen. Kein DISLIKE. | Richtiger Empty State; keine Zusage dauerhafter Sperre. |
| G Cooked | Ein Rezept einmal bestätigen, erneut klicken versuchen, neue Runde starten. | Button nach Erfolg deaktiviert; eine History je aktueller Runde. Neue Runde zeigt Recency. Kein automatisches Like/Inventory-Write durch diese Aktion. | Erfolg neutral statt rot; Hinweis auf fehlendes Undo als offenes F12 prüfen. |
| G Tageswechsel | App mit gestern fällig werdendem Plan über Mitternacht offen lassen; Recommendation vor Öffnen des Inventars, danach Inventar öffnen und neu empfehlen. | Nach F03-Fix gleiche bereits abgeglichene Vorratsgrundlage; aktuell mögliche Abweichung. | Keine unerklärten Änderungen der Pantry-Reasons. |
| H Compact | Scene logisch unter1100, besonders nahe Fenster-Minimum, alle Seiten; Geschmack/Filter/Formulare aufklappen. | Erreichbare Buttons/Inputs, vertikale Grids wo vorgesehen, kein nötiger verborgener Horizontalscroll. | Search-Buttons, R5-Header, allgemeine Rezeptfelder, Kategorie-/Inventarraster und Save-Overlay. |
| I Wide | Normal → ab1440 → ab2100 → zurück; lange Namen/mehrzeilige Karten. | Breakpoints reversibel, Daten/Session bleiben erhalten; kein Neuscore durch Resize. | Moderate Breiten, kein übermäßiger Leerraum, Cards nicht unnötig hoch. |
| J Light | Alle Seiten, Erfolg/Fehler, aktives Feedback, Menüs/Dropdowns/Bestätigungen. | Gemeinsame Bordeaux-/Fehlersemantik. | Lesbare Sekundärtexte, keine zusammenklebenden Karten. |
| K Dark | Gleiche Strecke, besonders Recipe-Typbadge, Planlinks, Steps, positive Reasons und Feedback. | R5-Gründe/Feedback jetzt hell lesbar; übrige Akzenttexte als F06 prüfen. | Hover, Fokus, disabled Cooked, Dialog-Theme, alle Dropdown-States. |
| L Keyboard/Focus | Nur Tab/Shift+Tab, Enter/Leertaste durch Navigation, Request, Karte, Feedback und Dialoge. | Logische Reihenfolge, erkennbare aktive Bewertung; nach Re-Render sinnvoller Fokus. Aktuell Fokusverlust-Risiko F20. | Fokus sichtbar in beiden Themes; Beschriftungen reichen ohne Farbinterpretation. |

## N. Quality Gates

| Gate | Ergebnis |
|---|---|
| Unveränderte Baseline, Java25 | **722/722**, keine Fehler, keine übersprungenen Tests. |
| Dismiss-/Feedback-Workflow nach neuen Dismiss-Tests | **10/10**, vor den Anzeigefixes erfolgreich. |
| Gezielte R5-/Composition-/FXML/CSS-Suite | **49/49**, inklusive neuer Präsentationstests. |
| Vollständiger Java25-Lauf nach Fixes | **731/731**, Failures 0, Errors 0, Skipped 0; enthält R0–R4 und alle bestehenden Catalog-/SQLite-/Domain-/Service-/UI-Prüfungen. |
| R4 innerhalb des Volltests | **14/14 Golden**, **11/11 Invariants**, **42/42 Sensitivity**. |
| Maven Package | **SUCCESS**, abschließend `-B package` einschließlich erneut **731/731 Tests** nach der letzten Fehlerstil-Korrektur; JAR gebaut. Kein Windows-Installer-/Starttest behauptet. |
| git diff --check | **SUCCESS**; Zeilenendungs-Hinweise sind keine Whitespace-Fehler. |
| Git | `main`, bestehende und neue R5-/Auditänderungen uncommitted; kein Commit/Push, HEAD unverändert. |
| Manuelle UI-Abnahme | **Offen**, Nutzer übernimmt A–L. |

Verwendet: Microsoft JDK **25.0.4.1**, Maven **3.9.11** aus der vorhandenen IntelliJ-
Installation. Shell-Default zeigte zunächst Java8 und kein Maven; explizite installierte
Werkzeuge verwendet. Der Sandbox-Lesefehler auf java.security wurde durch genehmigte
Ausführung außerhalb der Sandbox gelöst. Keine Installation oder globale PATH-Änderung.

Lokale, nicht versionierte Nachweise unter `target`: `astra-baseline-test.log`,
`astra-dismiss-test.log`, `astra-r5-test.log`, `astra-full-test.log`, `astra-package.log`
und Surefire-XML-Reports. Die Tests verwenden temporäre Datenbanken.

**Abschlussempfehlung: R5 NOT READY FOR COMMIT.** Dismiss-Session-Semantik und Core-Regressions-
gates sind bestätigt, aber F01/F02 und F03 müssen vor Freigabe gezielt bearbeitet werden.
Die große Navigationszusammenführung darf als eigene Folgephase verbleiben.
