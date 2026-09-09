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
| 28 | heute gekocht vs. gestern gekocht vs. nie gekocht | heute gekocht ist ausgeschlossen; gestern bleibt Candidate und liegt bei gleichem Basis-Fit hinter nie gekocht | Ausschluss `RECIPE_COOKED_TODAY`, sonst `RECENTLY_COOKED` bzw. `RECIPE_NEVER_COOKED` |
| 29 | gleiches Basis-Fit, explizites LIKE vs. neutral | liked Recipe liegt vorn; kein doppelter Rating-Bonus | `RECIPE_EXPLICITLY_LIKED` |
| 30 | Candidate- oder Inventory-Reihenfolge vertauscht | identische Eligibility, Scores, Optionen und Reihenfolge | identische Reason Codes |
| 31 | starkes Basis-Fit neutral vs. schwaches Fit mit LIKE | Pantry- und Basisqualität bleibt ausschlaggebend | passende Pantry- und Preference-Gründe |
| 32 | nur SHOWN Events | Score und Ranking bleiben gegenüber keiner Interaction gleich | kein Interaction-Grund |
| 33 | viele SELECTED plus explizites DISLIKE | explizites Feedback bestimmt Preference | Dislike-Grund, kein Selected-Grund |
| 34 | viele DISMISSED plus explizites LIKE | explizites Feedback bestimmt Preference | Like-Grund, kein Dismissed-Grund |

Die Tabelle ist der vollständige Golden-Acceptance-Katalog. Automatisierte Tests sichern die
kritischen ausführbaren Regeln gruppiert ab, aber nicht jede Tabellenzeile besitzt zwingend
eine gleichnamige Eins-zu-eins-Testmethode. Besonders abgedeckt sind Shared Budget,
Alternativen mit Teildeckung und drei Optionen, Eligibility-Kombinationen, Household-Grenzen,
Sekundenpräzision, Tie-Breaks, Eingabereihenfolge sowie die folgenden Invarianten. R3 macht
die früher zurückgestellten Szenarien 28 und 29 mit festen History-/Feedback-Snapshots
ausführbar.

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

JUnit-Beispieldaten übernehmen diese Rolle ohne zusätzliche Property-Test-Dependency.
R3-Tests ergänzen feste Clock-Szenarien für 0, 1, 7, 14 und mehr als 14 Tage,
monotone Freshness, Feedback-Mapping, begrenzte Interaction-Aggregation und deterministische
personalisierte Rangfolgen. Eine dedizierte Generatorbibliothek wird erst eingeführt, wenn
sie gegenüber den deterministischen Invarianten einen belegbaren Mehrwert liefert.

## R4-Hypothesen aus R3

- **H1:** Ein lineares 14-Tage-Fenster ist für Recipe-level Recency sinnvoll.
- **H2:** Explizites Recipe Feedback soll implizite Interaktionen vollständig überstimmen.
- **H3:** `SHOWN` ist neutral und darf keine selbstverstärkende Rangschleife erzeugen.
- **H4:** `0,5 * net / (evidence + 2)` ist als Interaction-Fallback ausreichend schwach und
  beschränkt.
- **H5:** Gesamtfrequenz wird nicht separat bestraft; ein Lieblingsgericht bleibt möglich.
- **H6:** Recency desselben Recipes reicht als erste V1-Variety-Näherung.

Diese Annahmen sind experimentell. R4 bewertet sie gegen Golden Scenarios und freiwillige
lokale Nutzung, ohne die unten definierten Produktschwellen rückwirkend abzusenken.

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

## R4 Offline Evaluation

### Eingefrorene R3-Baseline

R4 bewertet unverändert das Profil `V1`. Der interne Score liegt in `0..1`, ist aber weder
Prozentwert noch kalibrierte Wahrscheinlichkeit. Fehlende Signale werden ausgelassen; die
Gewichte der vorhandenen Signale werden auf ihren aktiven Nenner normiert.

| Signal | Gewicht | R4-Status |
|---|---:|---|
| Pantry Coverage | 0,35 | unverändert |
| Missing Ingredient Penalty | 0,15 | unverändert, wird als Nutzen `1 - penalty` aggregiert |
| Taste Affinity | 0,15 | unverändert |
| Preparation Time Fit | 0,10 | unverändert |
| Ingredient Alternative Fit | 0,00 | unverändert; nur Explainability |
| Recent Meal Penalty | 0,05 | konfiguriert, aber bewusst nicht gesetzt |
| Variety Score | 0,05 | unverändert; lineare Recipe-Freshness |
| Recipe Preference | 0,05 | unverändert |
| Household Preference | 0,10 | unverändert |

Weitere eingefrorene Verträge:

- qualitative Bänder bei `0,80`, `0,65` und `0,50`,
- Household-Aggregat `0,70 * minimum + 0,30 * average`,
- starke Ablehnung ab normalisierter Affinität `<= 0,20` und Score-Cap `0,39`,
- 14 Tage lineare Recipe-Freshness nach dem neuesten bestätigten `occurredAt`,
- Feedback-Mapping `1..5 -> -1, -0,5, 0, +0,5, +1` sowie LIKE/DISLIKE `+/-0,75`,
- Interaction-Fallback `0,5 * (selected - dismissed) / (selected + dismissed + 2)`,
- explizites Feedback überschreibt implizite Interaktionen vollständig,
- `SHOWN` bleibt neutral,
- unverändertes Tie-Breaking über Missing Groups, Coverage, Zeit, Name und Recipe-UUID.

### Golden-Scenario-Methode und Ergebnis

`RecommendationR4GoldenScenarioTest` bildet 14 zentrale, fachlich benannte
Ranking-Konflikte als testseitige Scenario Records ab. Jedes Scenario enthält ID,
Beschreibung, Kandidaten, vollständigen Context, erwartete Reihenfolge, erforderliche oder
verbotene Reasons und eine fachliche Begründung. Der parametrisierte JUnit-Name und eine
fehlerspezifische Begründung bilden zugleich den kompakten lokalen Scenario Report; eine
Production-Debug-API ist dafür nicht erforderlich.

| Kategorie | Scenario ID | geprüfte Kernaussage |
|---|---|---|
| A Pantry Dominance | `A-PANTRY` | perfekter Pantry Fit schlägt deutlich schlechteren liked Fit |
| B Missing Ingredients | `B-MISSING` | weniger fehlende Groups liegen vorn |
| C Taste Preference | `C-TASTE` | expliziter Taste Match schlägt Mismatch |
| D Time Fit | `D-TIME` | Grenzwert schlägt deutliches Überschreiten |
| E Recency | `E-RECENCY` | nie gekocht schlägt heute gekocht bei gleichem Basisfit |
| F Explicit Feedback | `F-FEEDBACK` | positive, neutrale und negative Werte wirken monoton |
| G Implicit Interactions | `G-INTERACTION` | wiederholte Auswahl/Ablehnung wirkt schwach und symmetrisch |
| H Household Conflict | `H-HOUSEHOLD` | Konflikt-Cap und Least-Misery bleiben wirksam |
| I Strong Match | `I-STRONG` | Personalisierung erfindet keinen Taste-/Household-Strong-Match |
| J Ingredient Alternatives | `J-ALTERNATIVE` | verfügbare Alternative deckt genau eine Group ohne Doppelbonus |
| K Compound Trade-offs | `K-COMPOUND` | starke Basisqualität schlägt maximale Personalisierung |
| L Neutral / Missing Data | `L-NEUTRAL` | Cold Start verzerrt gleiche Kandidaten nicht |
| M Deterministic Ties | `M-TIE` | dokumentierter stabiler Fallback entscheidet |
| N Edge Cases | `N-EDGE` | identische Personalisierung ändert relative Reihenfolge nicht |

Ergebnis: **14/14 Golden Scenarios, 100 Prozent**. Zusätzlich bestehen 11/11 gezielte
R4-Invarianten sowie 42/42 Sensitivitäts-, Grenzwert- und Parameterfälle.

### Sensitivity und Dominanzgrenzen

Die Analyse verändert jeweils genau ein Signal bei konstanten übrigen Inputs. Pantry,
Taste, Time, Variety und Preference sind jeweils monoton: eine Verbesserung erhöht den
Score. Contribution-Tests weisen die gewichteten Zählerbeiträge separat aus und bestätigen,
dass Variety nur einmal gesetzt wird; `RECENT_MEAL_PENALTY` bleibt abwesend.

Wegen der dokumentierten Active-Weight-Normalisierung hängt die sichtbare Score-Differenz
vom verfügbaren Context ab:

| Vergleich | gewichteter Zähler | vollständiger Context (Nenner 0,95) | Pantry + Personalisierung (Nenner 0,60) |
|---|---:|---:|---:|
| Preference `-1 -> +1` | 0,050 | 0,052632 | 0,083333 |
| Preference neutral `0 -> +1` | 0,025 | 0,026316 | 0,041667 |
| heute gekocht -> nie/14 Tage | 0,050 | 0,052632 | 0,083333 |
| neutral + heute -> maximal positiv + frisch | 0,075 | 0,078947 | 0,125000 |

Die Preference von 0,05 dreht damit bewusst nur enge Rankings. Der Golden-Fall mit
perfektem Pantry Fit gegen fehlenden Bestand bleibt selbst bei maximal positiver Preference
und Freshness stabil. Ein LIKE kann dagegen einen vollständigen fachlichen Tie sinnvoll
drehen. Wird der Pantry Fit des bisherigen Siegers deutlich verbessert, dreht das Ranking
nachvollziehbar zurück.

### Recency-Findings

Die Punkte nie gekocht, 0, 1, 3, 7, 10, 13, 14 und 21 Tage wurden exakt geprüft. Die
Freshness ist kontinuierlich und monoton, beträgt bei 7 Tagen `0,5` und ist ab 14 Tagen
auf `1` begrenzt. Die Reason-Grenzen bleiben gröber als der Score: heute, unter 7 Tagen,
mittleres Fenster und ab 14 Tagen. Dadurch behauptet die Explainability keine künstlich
präzise Bedeutung im mittleren Fenster. Es existiert keine doppelte Recency-Bestrafung.

Ohne reale Nutzerdaten lässt sich nicht beweisen, dass 14 statt 7, 21 oder 28 Tage das
produktoptimalste Fenster ist. Innerhalb der fachlichen Golden Suite ist die bestehende
Policy jedoch stabil, verständlich und ausreichend begrenzt. Entscheidung: **14 Tage
beibehalten**.

### Feedback- und Interaction-Findings

Alle sieben expliziten Mappings sind symmetrisch und monoton. Rating 3 bleibt auch bei sehr
vielen SELECTED-Events exakt neutral, weil explizite Evidenz den Fallback ersetzt. Ebenso
gewinnt DISLIKE gegen 100 SELECTED und LIKE gegen 100 DISMISSED. Es entsteht jeweils nur
der explizite Reason.

| SELECTED / DISMISSED | implizite Preference, ungefähr |
|---:|---:|
| 0 / 0 | 0 |
| 1 / 0 | +0,166667 |
| 2 / 0 | +0,250000 |
| 3 / 1 | +0,166667 |
| 5 / 0 | +0,357143 |
| 10 / 0 | +0,416667 |
| 100 / 0 | +0,490196 |
| 5 / 5 oder 10 / 10 | 0 |

Die negativen Fälle sind exakt symmetrisch. Der offene Grenzwert bleibt `+/-0,5`; er wird
bei endlicher Evidenz nie erreicht. Ein einzelnes SELECTED beeinflusst den Score leicht,
erzeugt aber bewusst noch keinen sichtbaren „wiederholt ausgewählt“-Reason. 1.000 reine
SHOWN-Events sind exakt identisch zu keinem Interaction-Datensatz. Entscheidung:
**Interaction-Formel, SHOWN-Neutralität und Explicit-over-Implicit unverändert lassen**.

### Semantische und Explainability-Invarianten

- Häufiges Kochen erzeugt keine positive Preference; nur der letzte Kochzeitpunkt beeinflusst
  Recipe-Freshness.
- Viele SELECTED-Events können Preference beeinflussen, ändern aber `NEVER_COOKED` nicht.
- Identische History beziehungsweise identisches Feedback aller Kandidaten ändert ihre
  relative Rangfolge nicht.
- Reasons enthalten keine widersprüchlichen positiven und negativen Preference-Aussagen.
- Neutrales explizites Feedback und neutrale Interactions erzeugen keinen Preference-Reason.
- Household-Konflikt und Score-Cap können durch Preference, Freshness oder Interactions nicht
  umgangen werden.
- 25 Wiederholungen mit vertauschter Candidate- und Inventory-Reihenfolge liefern exakt
  gleiche Scores, Reihenfolge, Optionen und Reasons.
- Die bestehende R3-Batch-Grenze von drei Repository-Abfragen bleibt unverändert.

### Hypothesenentscheidung und Tuning

| Hypothese | Ergebnis | Entscheidung |
|---|---|---|
| H1: lineare 14-Tage-Recency | mechanisch bestätigt, Produktoptimum mangels Felddaten vorläufig | behalten |
| H2: Preference-Gewicht 0,05 | dreht knappe Ties, überstimmt keine deutlichen Basisdefizite | behalten |
| H3: explizit dominiert implizit | auch bei 100 gegenteiligen Events bestätigt | behalten |
| H4: SHOWN neutral | 1.000 SHOWN exakt neutral | behalten |
| H5: Interaction-Formel | symmetrisch, gedämpft, offen auf `+/-0,5` begrenzt | behalten |
| H6: Recipe-level Variety | für V1 konsistent, semantisch ähnliche Recipes bleiben Grenze | behalten für V1 |
| H7: Gesamtsignal-Balance | alle 14 Konfliktkategorien und Guardrails bestanden | behalten |

**Tatsächliche Tuning-Änderungen: keine.** Insbesondere wurden weder Top-Level-Gewichte,
Score Range, Strong-Match-Regeln, Household-Cap, Recency-Fenster, Feedback-Mapping noch
Interaction-Formel verändert.

Verworfene Änderungen:

- Preference auf `0,07` oder `0,10` erhöhen: kein nachgewiesenes Problem; größere Werte
  würden Basisqualität leichter überstimmen und den R0-Top-Level-Vertrag verändern.
- Recency auf 7, 21 oder 28 Tage setzen: ohne reale Nutzung kein belastbarer Vorteil.
- SHOWN als negatives Signal: Nicht-Interaktion ist kein sicherer Dismissal und würde
  Self-Reinforcement erzeugen.
- Frequency-, Cuisine-, Ingredient- oder Taste-Diversität ergänzen: neue Signalsemantik und
  damit außerhalb von R4.
- Interaction-Decay oder probabilistische Modelle: kein belegter V1-Bedarf.

### Bekannte Grenzen und spätere reale Messung

Golden Scenarios sind synthetische, fachlich begründete Hypothesen und keine Ground Truth.
Es fehlen echte Nutzerdaten, Ingredient-/Cuisine-Diversity, Kosten, Ablaufdaten,
Nutrition-Ziele, personenbezogene Household-Präferenzen und zeitlicher Interaction-Decay.
Recipe-level Recency erkennt beispielsweise Spaghetti Bolognese und Lasagne als verschiedene
Mahlzeiten, auch wenn sie subjektiv sehr ähnlich sein können. Diese Grenze wird dokumentiert,
nicht in R4 gelöst.

Für einen späteren freiwilligen, lokalen Pilot sind sinnvoll:

- Recommendation Selection Rate und Top-N Selection Rate,
- Dismissal Rate,
- Selected-to-Cooked Conversion,
- Time-to-Decision,
- erneute manuelle Suche direkt nach einer Recommendation,
- explizites Feedback nach Recommendation.

Diese Metriken sind nur mit vorheriger Einwilligung und klarer Löschfrist sinnvoll. R4
implementiert weder Telemetrie noch Analytics und gibt mangels Ground Truth keine
irreführenden Precision-/Recall-/NDCG-Aussagen als Produktqualität aus.

## R5.3 Re-Evaluation: Wunschzutaten

R5.3 ergänzt genau ein optionales Signal: `DESIRED_INGREDIENT_FIT`. Es misst den Anteil
gewählter lokaler Ingredient-UUIDs, die in mindestens einer Option einer RecipeIngredientGroup
vorkommen. Das Signal ist bei leerer Auswahl abwesend, verändert keine Pantry-Menge und
schließt einen Kandidaten mit null Treffern nicht aus. Bestehende Top-Level-Gewichte,
Strong-Match-Regeln, Household-Cap, Eligibility und Tie-Breaking bleiben unverändert.

### Gewichtsaudit

Vor R5.3 summierten sich die konfigurierten Rohgewichte auf `1,00`; fehlende Signale wurden
bereits aus dem aktiven Nenner entfernt. Damit existiert ein sauberer Extension-Point, ohne
vorhandene Gewichte neu zu verteilen. Verglichen wurden `0,03`, `0,05` und `0,07`.

| Kandidat | maximale Wirkung beim minimalen Pantry-Context | Einordnung |
|---:|---:|---|
| 0,03 | `0,03 / 0,53 = 5,66 %` | sehr zurückhaltend; kann selbst enge Wünsche leicht übersehen |
| 0,05 | `0,05 / 0,55 = 9,09 %` | entspricht Recipe Preference und bleibt Pantry klar untergeordnet |
| 0,07 | `0,07 / 0,57 = 12,28 %` | nähert sich dem Time-Signal und gibt einem optionalen Wunsch relativ viel Kraft |

Im üblichen vollständigen Context mit vorherigem aktivem Nenner `0,95` bewirkt ein voller
gegenüber keinem Match bei `0,05` höchstens fünf Score-Prozentpunkte. Entscheidung:
**Gewicht `0,05`**. Es ist sichtbar genug, um einen fachlichen Tie zu beeinflussen, und
klein genug, dass ein deutlich besserer Pantry-Basisfit ohne Wunschzutat weiterhin gewinnt.

### Ausgeführte Guardrails

- keine Wunschzutaten: Signal abwesend und bisheriges Recommendation-Ergebnis exakt gleich,
- voller, teilweiser und fehlender Match: monotoner Signalwert `1`, Anteil, `0`,
- fehlender Match: Recipe bleibt Candidate,
- passende Alternative: Group erfüllt den Wunsch ohne Änderung der Pantry-Zuordnung,
- Input-Reihenfolge: identische Scores, Reihenfolge und Reasons,
- alle drei Kandidatengewichte: deutlich besserer Pantry-Fit bleibt dominant,
- R4 Golden Scenarios, Strong-Match- und Household-Regeln: unverändert bestanden.

Das Profil trägt wegen der nachweisbaren, bewusst sichtbaren Signalerweiterung die Version
`V1.1`. Es handelt sich nicht um eine Neukalibrierung des Scores; rohe Werte bleiben in der
UI unsichtbar.

### R5.3 Akzeptanzkorrektur: am selben Tag gekocht

Ein bestätigtes `MealHistoryEntry.occurredAt` am aktuellen Datum der injizierten Clock-Zone
schließt das Recipe nun vor dem Ranking aus. Das ist eine Candidate-Regel, kein Signal und
kein neuer Penalty. Die Gewichte, Active-Weight-Normalisierung und lineare 14-Tage-Freshness
bleiben unverändert. Gestern oder früher gekochte Recipes sind weiterhin Kandidaten. Der
angepasste Golden-Fall E prüft deshalb den unveränderten Recency-Vergleich ab dem Folgetag;
die Same-Day-Eligibility besitzt eigene Service- und Workflow-Regressionstests.
