# MealDeal – R5.2 Mode Review

Stand: 9. September 2026. Auf dem vorhandenen uncommitteten R5-/R5.1-Stand umgesetzt.

## 1. Executive Summary

„Gericht finden“ bietet zwei getrennt sichtbare Modi mit erhaltenem Seitenzustand.
Die Empfehlungseingabe ist kompakter; die gezielte Suche ist direkt erreichbar.
Automatisierte Validierung und Package sind erfolgreich. Visuelle Abnahme steht aus.

## 2. Alte UI-Struktur

Die Empfehlungseingabe blieb über einer aufklappbaren „Filter & Suchoptionen“-Suche
sichtbar. Nur Empfehlungsergebnisse wurden ausgeblendet. Dadurch konkurrierten
Eingaben und Geschmacksauswahlen auf derselben Seite.

## 3. Neue Mode-Struktur

Ein gemeinsamer Seitentitel und die bestehende Route `SEARCH` bleiben erhalten.
Zwei ToggleButtons in einer ToggleGroup wählen „Für mich empfehlen“ oder „Gezielt
suchen“. FXML-Includes behalten beide Controller und ihre vollständigen Inhaltsbereiche.
Der inaktive Bereich ist sowohl unsichtbar als auch unmanaged.

## 4. Aktiver Default Mode

Eine frisch geöffnete Seite startet in „Für mich empfehlen“. Der aktive Schalter
ist ausgewählt, fett und unterstrichen und besitzt einen entsprechenden AccessibleText.
Die Gruppe stellt bei Abwahl des aktiven Schalters dessen Auswahl wieder her.

## 5. Recommendation State Lifecycle

Personen, Zeit, Taste-Auswahl, Session-UUID, Ergebnisreihenfolge und sessionlokale
Dismissals bleiben beim Wechsel in derselben Controller-/View-Instanz erhalten.
Nur „Vorschläge anzeigen“ startet eine neue Runde mit neuer UUID. Die bestehende
Deduplication von SHOWN, SELECTED, DISMISSED und Kochbestätigung bleibt erhalten.

## 6. Search State Lifecycle

Zutaten, Geschmäcker, Suchmodus, Filter und Ergebnisse bleiben unabhängig erhalten.
„Suche starten“ verwendet unverändert die bestehende Fachlogik; Reset betrifft die
Suchauswahl, nicht die Recommendation. Hauptnavigation beginnt später eine frische Seite.

## 7. Mode Switch Verhalten

Ein Wechsel ändert ausschließlich aktiven Modus, Sichtbarkeit, Layoutteilnahme und
Scrollposition. Er löst keine Suche, Recommendation, neue Session oder Events aus.
Die Auswahländerung der ToggleGroup steuert den Inhalt auch bei Tastaturbedienung.

## 8. Detail Return Verhalten

Der bestehende Navigator bewahrt dieselbe gemeinsame Seite samt Controller auf.
Rückkehr aus einem Recommendation- oder Suchergebnis erhält damit den tatsächlichen
Modus und dessen Zustand. Der bestehende Feedback-Refresh aktualisiert explizites
Feedback ohne Neuberechnung der Reihenfolge. Bibliotheksrückkehr bleibt unverändert.
Gespeicherte Recipe-Edits und Löschen laden wie in R5.1 den Ursprung frisch; Abbrechen
bewahrt ihn. Es gibt keinen zusätzlichen globalen View-Cache.

## 9. Scroll Verhalten

Ein tatsächlicher Moduswechsel setzt die gemeinsame ScrollPane auf ihren Minimalwert.
Erneute Auswahl des aktiven Modus und Detail-Rückkehr setzen sie nicht ausdrücklich
zurück. Kein separater Scroll-Cache pro Modus. Das Verhalten im gerenderten Layout
einschließlich Fokusübernahme bleibt Teil der manuellen Abnahme.

## 10. Taste-Duplikation beseitigt

Es ist stets nur die Taste-Oberfläche des aktiven Modus erreichbar. Die getrennten
Auswahlmodelle bleiben bestehen; keine implizite Übernahme zwischen Empfehlung und Suche.
Die Recommendation verwendet weiterhin die vorhandene `TasteSelectionView`.

## 11. Recommendation Input Compacting

Personen, optionale Zeit und ein kompakter optionaler Geschmacksschalter stehen in
einer umbrechenden Zeile. Die Taste-Auswahl öffnet darunter inline; der Schalter zeigt
die Anzahl ausgewählter Geschmäcker. Redundante Überschrift und Verschachtelung entfallen.
„Vorschläge anzeigen“ funktioniert weiterhin ohne Zeit- oder Taste-Wunsch. Der kurze
Hinweis erklärt Inventarbezug und weiche Wünsche einschließlich möglicher längerer Gerichte.

## 12. Search UI Anpassungen

Äußere Filter-Klappe, doppelte Seitenüberschrift und zusätzliche Optionsklappe entfallen.
AND, OR und RANKING stehen kompakt gemeinsam mit einem erklärenden Hinweis vor Start
und Reset. Zutaten-/Geschmacksauswahl und Kategorien bleiben erhalten. Das bestehende
Raster bleibt bei normaler/breiter Ansicht zweispaltig und bei kompakter Ansicht einspaltig.
Suchsemantik und PERFECT-/GOOD-/PARTIAL-Bewertung wurden nicht verändert.

## 13. Card Action Hierarchy

Die R5.1-Hierarchie bleibt nach statischer Prüfung bestehen: „Gericht öffnen“ als
Hauptaktion, „Als gekocht markieren“ nachgeordnet, „Gerade nicht“ als Hyperlink;
Like/Dislike/Clear stehen in einem getrennten Feedback-Bereich. Die Kochaktion bleibt
bei der Session-Karte, weil angefragte Portionen und die Sperre gegen doppelte Bestätigung
dort eindeutig sind. R5.2 führt keinen neuen Detail-Kochworkflow oder Undo ein.

## 14. CSS Änderungen

`styles/search.css` enthält die neuen lokalen Modusschalter-Regeln und reduzierte
Recommendation-Abstände. Bestehende Theme-Tokens, FlowPane-Umbruch und Breakpoints
werden verwendet. Auswahl ist zusätzlich fett/unterstrichen, Fokus gestrichelt markiert.
Keine neue CSS-Datei, Inline-Styles, `!important` oder zusätzlichen Breakpoints.
Nicht mehr verwendete `taste-mode-card`-Regeln wurden entfernt.

## 15. Entfernte tote Composition-Strukturen

Entfernt sind `targetedSearchPane`, die alte Ergebnis-Sichtbarkeitskoordination,
`recommendationStarted` samt Callback, `setResultsActive`, das unnötige
`recommendationResultsSection`-Feld und `searchOptionsButton`/`searchOptionsContent`
samt Toggle-Methode. Alte Composition-Tests wurden auf vollständige Modi angepasst.

## 16. Neue/angepasste UI-Typen

Neu: `FindingMode`, ein kleines UI-Enum. Angepasst: `FindMealController`,
`RecommendationController`, `IngredientSearchController`, die drei zugehörigen
FXML-Dateien, `search.css`, `FindMealControllerTest` und `FxmlResourceTest`.
Architektur- und Feedback-Loop-Dokumentation beschreiben den aktuellen Stand.

## 17. Persistenzänderungen

Keine durch R5.2. Kein Schema, Repository, Scoring, Katalog, History- oder
Feedbackvertrag wurde für die Moduskonsolidierung geändert. Vorhandene R5/R5.1-Arbeit
bleibt Teil des uncommitteten Gesamtstands.

## 18. Gezielte R5.2 Tests

**82/82 erfolgreich**, ohne Fehler oder übersprungene Tests: FindMealController (6),
FXML-Ressourcen (34), Navigation (9), RecommendationController (8), DetailController
(8), Workflow (10), Feedback-Loop-Integration (3), ApplicationContext (1) und
IngredientSearchController (3). Log: `target/r52-targeted.log`.

Die Modustests prüfen exklusive Sichtbarkeit/Layoutteilnahme, getrennte Auswahl,
identische Ergebnisobjekte, erhaltene echte Session und Dismissals, unveränderte
Eventliste sowie Scroll-Callbacks. Detail-Rückkehr wird für beide Modi geprüft.
Diese Tests verwenden keine laufende GUI; echte Control-Eingabe und gerenderte
Detail-Rundreise werden nicht als automatisiert visuell geprüft ausgewiesen.

## 19. R5/R5.1 Regression

**39/39 erfolgreich** in den dedizierten bestehenden Suiten Navigation (9),
RecommendationController (8), DetailController (8), Workflow (10), Feedback-Loop (3)
und ApplicationContext (1). Diese sind eine Teilmenge der 82 gezielten Tests.
Die drei bestehenden IngredientSearchController-Tests sind ebenfalls erfolgreich.

## 20. R0

**67/67 erfolgreich**: RecipeRecommendationService, RecommendationContract,
RecommendationGoldenScenario, PantrySharedBudget, PantryOptimizerOracle,
RecommendationReviewRegression und RecommendationTieBreak.

## 21. R1

**25/25 erfolgreich**: CatalogMatcher, CatalogTextNormalizer, LocalCatalogResolver,
StandardCatalogIntegrity, SqliteCatalogLinkIntegration sowie Ingredient und Taste.

## 22. R2

**24/24 erfolgreich**: MealHistoryEntry, RecipeFeedback, RecommendationInteraction,
deren drei SQLite-Repository-Integrationen und RecommendationInteractionService.

## 23. R3

**32/32 erfolgreich**: PersonalizedRecommendationService,
RecommendationPersonalizationPolicy und RecommendationPersonalizationService.

## 24. R4

**67/67 erfolgreich**: RecommendationR4GoldenScenario (14),
RecommendationR4Invariant (11), RecommendationR4Sensitivity (42).
R0–R4-Zahlen wurden aus den Surefire-XMLs des vollständigen Laufs ermittelt;
keine geänderten fachlichen Erwartungswerte.

## 25. Full Java 25

**755/755 erfolgreich**, Failures 0, Errors 0, Skipped 0. Microsoft JDK
25.0.4.1, Maven 3.9.11. Vollständiger Lauf über `mvn -B package` am 9. September 2026.
Log: `target/r52-package.log`. Die oben genannten Teilmengen werden nicht addiert.

## 26. Package

**SUCCESS.** Maven erzeugte `target/mealdeal-0.1.0-SNAPSHOT.jar`.
Build-Artefakte und Testlogs bleiben unter dem ignorierten `target`-Verzeichnis.

## 27. git diff --check

**SUCCESS.** Abschließende Diff-/Statusprüfung erfolgt ohne Staging oder Git-Bereinigung.

## 28. Git

**Uncommitted / unpushed.** Bestehende R5/R5.1-Änderungen wurden weitergeführt.
Kein Commit, Push, Reset, Checkout, Clean, Stash, Revert oder Fetch ausgeführt.

## 29. Bekannte Einschränkungen

Keine GUI-Automation oder visuelle Bedienung der App durchgeführt. FXML-/CSS-Verträge,
Zustandskoordination, Services und Persistenzpfade sind automatisiert geprüft;
Pixel-Layout, Theme-Kontrast, Tastaturfokus und tatsächliches Scrollverhalten benötigen
die folgende manuelle Abnahme. Das bestehende vollständige Recipe-Laden mit N+1 und
synchrone Verarbeitung bleiben außerhalb R5.2; die R3-Personalisierungsbatches bleiben gleich.

## 30. Manuelle Acceptance Checklist

- [ ] **A – Öffnen:** „Gericht finden“ öffnen. Nur „Für mich empfehlen“ ist aktiv und sichtbar.
- [ ] **B – Empfehlung:** Direkt ohne optionale Wünsche starten, danach mit 3 Personen,
  30 Minuten und „Herzhaft“. Eingaben, Hinweise und Ergebnisse sind verständlich.
- [ ] **C – Suchmodus:** „Gezielt suchen“ wählen. Empfehlung samt Formular verschwindet vollständig.
- [ ] **D – Suche:** Zutaten/Geschmack auswählen; AND, OR und RANKING prüfen, Suche starten.
- [ ] **E – Zurück zur Empfehlung:** Den Modus wechseln; vorhandene Runde erscheint sofort.
- [ ] **F – Empfehlungszustand:** 3 Personen, 30 Minuten, „Herzhaft“ und Reihenfolge bleiben
  erhalten. Eine zuvor über „Gerade nicht“ entfernte Karte bleibt in dieser Runde ausgeblendet.
- [ ] **G – Erneut Suche:** Wieder „Gezielt suchen“ auswählen, ohne Suche zu starten.
- [ ] **H – Suchzustand:** Auswahl, Filter, Suchmodus und Ergebnisse bleiben erhalten.
  Such-Reset verändert die Recommendation-Auswahl nicht.
- [ ] **I – Empfehlungsdetail:** Empfehlung öffnen; angefragte Portionen und vorgeschlagene
  Zutatenoptionen kontrollieren, bei Bedarf Feedback ändern.
- [ ] **J – Rückkehr:** Zurück zeigt den Empfehlungsmodus mit derselben Runde, Eingaben
  und Dismissals; Feedback ist aktualisiert. Keine automatische neue Runde.
- [ ] **K – Suchdetail:** Suchergebnis öffnen; Recipe-Standards sind der Ausgangspunkt.
- [ ] **L – Rückkehr:** Zurück zeigt Suchmodus, Auswahl und Ergebnisse. Zusätzlich
  Bibliothek → Detail → Zurück auf den korrekten Bibliotheksursprung prüfen.
- [ ] **M – Geschmack:** Pro aktivem Modus nur dessen Taste-Auswahl sichtbar.
  Recommendation-Schalter öffnet inline und zeigt die Anzahl der Auswahl.
- [ ] **N – Kompakt:** Kleines Fenster prüfen: Modusschalter und Eingabezeile brechen
  sinnvoll um, keine abgeschnittenen Aktionen; Suche einspaltig.
- [ ] **O – Breit:** Normales, breites und extrabreites Fenster prüfen: Suchraster
  zweispaltig, lesbare Breiten und Abstände, klare Reihenfolge.
- [ ] **P – Light:** Helles Theme: Text, Auswahl, Fokus, Hinweise und Karten kontrollieren.
- [ ] **Q – Dark:** Dunkles Theme: dieselben Zustände auf Lesbarkeit und Kontrast prüfen.
- [ ] **R – Tastatur:** Tab/Shift+Tab, Leertaste und Gruppennavigation per Pfeiltasten
  prüfen. Modusinhalt folgt der Auswahl; Fokus sichtbar, inaktive Controls unerreichbar.
- [ ] **S – Scroll:** Weiter unten wechseln: neuer Modus beginnt oben. Aktiven Modus
  nochmals wählen und aus Details zurückkehren: kein erzwungener Sprung nach oben.
- [ ] **T – Kartenaktionen:** Öffnen klar primär, Kochen nachgeordnet, Ausblenden als
  Link und Feedback getrennt. Ausblenden erzeugt kein Dislike; Kochen bestätigt einmal
  pro Session und verändert kein Inventar. Neuer Vorschlagsstart erzeugt eine neue Runde.

## 31. Klare Empfehlung

**R5 READY FOR MANUAL FINAL REVIEW.** Die automatisierte Abnahme ist erfolgreich.
Vor einer Commit-Entscheidung steht die manuelle A–T-Abnahme durch den Nutzer aus.
