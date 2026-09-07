# Recommendation Evaluation Contract

R0 definiert vorab, wie die Baseline und spätere Erweiterungen bewertet werden. Die
Produktschwellen sind Hypothesen, keine nachträglich anzupassenden Erfolgsbehauptungen.

## Offline-Metriken

- **Acceptance@1:** Anteil der Testsituationen, in denen Rang 1 als akzeptabel markiert ist.
- **Acceptance@3:** Anteil mit mindestens einem akzeptablen Recipe in den ersten drei Rängen.
- **Candidate Coverage:** Anteil fachlich geeigneter Test-Recipes, die Eligibility korrekt in
  die Candidate-Menge übernimmt. Hard Exclusions werden getrennt als Safety Recall geprüft.
- **Ranking Stability:** Anteil unveränderter Rangbeziehungen bei semantisch irrelevanten
  Änderungen wie Eingabereihenfolge; zusätzlich deterministischer Wiederholungstest.
- **Explainability Coverage:** Anteil der Recommendations und Exclusions mit mindestens einem
  korrekten maschinenlesbaren Grund; Ziel für auswertbare Ergebnisse ist 100 Prozent.

Ein Golden Scenario ist bestanden, wenn Eligibility, relevante relative Reihenfolge und
erwartete Reason Codes stimmen. Exakte Dezimalwerte werden nur an echten Grenzwerten fixiert.

## Golden Scenarios

| # | Input/Konflikt | Eligibility und erwartete Rangrelation | Erwartete Gründe |
|---:|---|---|---|
| 1 | zwei Groups benötigen je 100 g X, Bestand 200 g | eligible und vollständig; gemeinsames Budget reicht | `PANTRY_FULL_COVERAGE` |
| 2 | zwei Groups benötigen je 100 g X, Bestand 100 g | eligible, Coverage 0,5 und eine Group unterdeckt | `MISSING_ONE_INGREDIENT_GROUP` |
| 3 | gleiche Gesamtzahl Groups, aber größerer Anteil unterdeckt | eligible; kleinerer Missing-Anteil liegt bei sonst gleichen Signalen vorn | passender Missing-Grund |
| 4 | flexible Group X/Y konkurriert mit fixer X-Group, X und Y vorhanden | eligible; Alternative löst Konflikt und beide Groups sind gedeckt | `ALTERNATIVE_AVAILABLE`, `ALTERNATIVE_IMPROVES_COVERAGE` |
| 5 | zwei Groups konkurrieren um dieselbe knappe Alternative | eligible; Bestand wird höchstens einmal verwendet | Missing- und Pantry-Grund der gemeinsamen Zuordnung |
| 6 | drei oder mehr Alternativen, mehrere davon vollständig vorhanden | eligible; Gruppe zählt einmal, Standard gewinnt fachlichen Tie | `PANTRY_FULL_COVERAGE`, kein doppelter Bonus |
| 7 | starke positive Taste-Affinität | eligible; vor sonst gleichem negativem Match | `TASTE_STRONG_MATCH` |
| 8 | negative Taste-Affinität ohne Hard Constraint | eligible; Ranking-Malus, kein Ausschluss | `TASTE_MISMATCH` |
| 9 | keine passenden Taste-Präferenzdaten | eligible; Taste-Signal nicht verfügbar | kein erfundener Taste-Grund |
| 10 | Gesamtzeit innerhalb Nutzerlimit | eligible; vor sonst gleichem Recipe über Limit | `TIME_WITHIN_LIMIT` |
| 11 | Gesamtzeit über Nutzerlimit | eligible; proportionaler Time Fit | `TIME_OVER_LIMIT` |
| 12 | Nutzerlimit vorhanden, Recipe-Zeit fehlt | eligible; kein geratener Wert | `TIME_UNKNOWN` |
| 13 | Household mit übereinstimmend positiven Präferenzen | eligible; hoher Hybridwert | `HOUSEHOLD_STRONG_MATCH` |
| 14 | Household mit normalen unterschiedlichen Präferenzen (`-0,5`, `+1,0`) | eligible; Household-Aggregat `0,3625` | kein Household-Reason; Signalwert bleibt für den Score verfügbar |
| 15 | ein Mitglied lehnt stark ab | eligible, aber Gesamtscore höchstens `0,39` | `HOUSEHOLD_CONFLICT`, `HOUSEHOLD_MEMBER_DISLIKES` |
| 16 | stark ablehnendes plus viele positive Mitglieder | eligible; Cap bleibt unverändert wirksam, kein positiver Household-Grund | `HOUSEHOLD_CONFLICT`, `HOUSEHOLD_MEMBER_DISLIKES` |
| 17 | Recipe-UUID hart ausgeschlossen | ineligible; kein Score | `RECIPE_HARD_EXCLUDED` |
| 18 | alle Optionen einer Gruppe als Ingredient ausgeschlossen | ineligible; kein Score | `INGREDIENT_GROUP_HARD_EXCLUDED` |
| 19 | ausgeschlossener Standard, sichere Alternative vorhanden | eligible; nur sichere Option vorgeschlagen | `HARD_EXCLUDED_ALTERNATIVE_IGNORED` |
| 20 | gewünschter `DishType` passt nicht | ineligible; kein Score | `DISH_TYPE_MISMATCH` |
| 21 | leeres Inventory | eligible; Coverage `0`, deterministische Ties | `PANTRY_LOW_COVERAGE` |
| 22 | höhere gewünschte Portionszahl | eligible; Bedarf wird vorher skaliert | Pantry-/Missing-Gründe nach skalierter Menge |
| 23 | Bestand `1 kg`, Bedarf `1000 g` | eligible; vollständig gedeckt | `PANTRY_FULL_COVERAGE` |
| 24 | inkompatible Unit desselben Ingredients | eligible; Bestand zählt nicht | Missing-/Low-Coverage-Grund |
| 25 | identische Scores und fehlende Gruppen | eligible; kürzere bekannte Zeit vor längerer | Time-/Pantry-Gründe unverändert |
| 26 | vollständiger fachlicher Tie | eligible; Name, dann UUID entscheidet stabil | gleiche fachliche Gründe |
| 27 | Recipe ohne optionale Zeit/Nutrition/Steps | eligible, sofern Gruppen/Taste valide | keine Gründe für fehlende optionale Daten |
| 28 | kürzlich geplant/gegessen | R0 unverändert; erst R2 nach belastbarer History | später `RECENTLY_COOKED` |
| 29 | wiederholte ähnliche Gerichte | R0 unverändert; erst R2 nach Variety-Definition | später `VARIETY_BONUS` |
| 30 | Candidate- oder Inventory-Reihenfolge vertauscht | identische Eligibility, Scores, Optionen und Reihenfolge | identische Reason Codes |

Die Tabelle ist der vollständige Golden-Acceptance-Katalog. Automatisierte Tests sichern die
kritischen ausführbaren Regeln gruppiert ab, aber nicht jede Tabellenzeile besitzt zwingend
eine gleichnamige Eins-zu-eins-Testmethode. Besonders abgedeckt sind Shared Budget,
Alternativen mit Teildeckung und drei Optionen, Eligibility-Kombinationen, Household-Grenzen,
Sekundenpräzision, Tie-Breaks, Eingabereihenfolge sowie die folgenden Invarianten. Die
deferred Szenarien 28 und 29 sind bewusst keine Pseudo-Tests mit erfundener Historie.

## Invarianten / property-orientierte Tests

- Mehr kompatibler Inventory-Bestand verschlechtert bei ansonsten identischem Problem den
  optimalen Pantry-V1-Nutzen `0,35 * Coverage + 0,15 * Completeness` nie. Die einzelnen
  Teilmetriken müssen dabei nicht jeweils monoton sein.
- Eine zusätzlich vollständig erfüllte Gruppe erhöht Missing Penalty nie.
- Hard Exclusions können von keinem Score oder Gewicht aufgehoben werden.
- Gleiche Inputs ergeben exakt dieselben Scores, Optionen, Gründe und dieselbe Reihenfolge.
- Reihenfolge von InventoryItems und Candidates ist semantisch irrelevant.
- Mehrere verfügbare Optionen derselben Gruppe erhöhen deren Coverage nie über `1`.
- Derselbe Inventory-Bestand kann innerhalb eines Recipe niemals mehrfach verbraucht werden.
- Branch-and-Bound liefert für kleine erzeugte Probleme dasselbe Optimum wie vollständige
  Enumeration.
- Portionsskalierung erfolgt vor Pantry Coverage.
- Inkompatible Units tragen nicht zur Abdeckung bei.
- Die Duplizierung positiver Household-Mitglieder entfernt einen vorhandenen Strong-Rejection-
  Cap nicht.

JUnit-Beispieldaten übernehmen diese Rolle ohne zusätzliche Property-Test-Dependency. Eine
dedizierte Generatorbibliothek wird erst eingeführt, wenn sie gegenüber den deterministischen
Invarianten einen belegbaren Mehrwert liefert.

## Pilotmetriken und Datenschutz

Später im freiwilligen Pilot werden benötigt:

- Time-to-decision vom sichtbaren Recommendation-Ergebnis bis zur bewussten Annahme,
- angenommene Recommendation beziehungsweise „keine angenommen“,
- Skip Rate,
- Zeitaufwand für normale Pantry-Korrekturen,
- optional eine freiwillige Usefulness-Bewertung ohne harte Zielschwelle.

Es wird keine verdeckte Telemetrie implementiert. Pilotdaten brauchen vorherige ausdrückliche
Zustimmung, Datensparsamkeit, dokumentierten Zweck und eine definierte Löschfrist. R0 schreibt
keine Nutzungsdaten.

## Verbindliche R4-Schwellen

- **Time to decision:** mindestens 70 Prozent innerhalb von 3 Minuten.
- **Acceptance@3:** mindestens 70 Prozent.
- **Pantry maintenance:** Median unter 60 Sekunden.

Die Schwellen dürfen in R4 nicht stillschweigend abgesenkt werden. Verfehlungen führen zu
Analyse und Produktentscheidung, nicht zur rückwirkenden Änderung der Erfolgskriterien.
