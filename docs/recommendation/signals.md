# Recommendation Signals V1

Alle Werte sind deterministisch und auf `0..1` normalisiert. „Nicht verfügbar“ bedeutet,
dass das Signal bei dieser Anfrage nicht in die gewichtete Summe eingeht.

## pantryCoverage

- Bedeutung: durchschnittlicher mengenbezogener Inventory-Deckungsgrad der benötigten Gruppen.
- Input: skaliertes Recipe, Inventory-Snapshot, zulässige Optionen.
- Berechnung: Eine exakte gemeinsame Zuordnung wählt pro Gruppe eine Option und verteilt den
  Inventory-Snapshot als nicht wiederverwendbares Mengenbudget.
- Zielfunktion: `0,35 * pantryCoverage + 0,15 * (1 - missingGroupShare)`.
- Berechnung je gewählter Option: tatsächlich zugeteilte kompatible Menge geteilt durch den
  skalierten Bedarf, begrenzt auf `1`.
- Gesamtsignal: arithmetisches Mittel dieser gemeinsam erreichbaren Gruppenabdeckungen.
- Units: nur Regeln aus `UnitConverter`; inkompatible Units tragen null bei.
- Alternativen: Die Suche berücksichtigt Ressourcenkonflikte zwischen allen Gruppen. Bei
  fachlichem Gleichstand gelten Standardoption, Position und UUID. Mehrere verfügbare
  Alternativen erzeugen keine Mehrfachbelohnung.
- Leeres Inventory: `0`. Mengenüberdeckung: auf `1` begrenzt.
- Richtung: höher ist besser.
- Reasons: `PANTRY_FULL_COVERAGE`, `PANTRY_MOSTLY_COVERED` ab `0,75`, sonst
  `PANTRY_LOW_COVERAGE`.

## missingIngredientPenalty

- Bedeutung: Anteil der IngredientGroups, deren beste zulässige Option nicht vollständig
  mengenmäßig gedeckt ist.
- Input: dieselbe optimale gemeinsame Zuordnung wie Pantry Coverage.
- Berechnung: `unterdeckte Gruppen / alle Gruppen`.
- Teilbestand: Gruppe gilt als unterdeckt; die Tiefe der Unterdeckung bleibt ausschließlich
  in Pantry Coverage.
- Richtung: höher ist schlechter.
- Reasons: `MISSING_ONE_INGREDIENT_GROUP` oder `MISSING_MULTIPLE_INGREDIENT_GROUPS`.
- Preise, Einkaufswege und reale Einkaufsdauer sind unbekannt und werden nicht simuliert.

## tasteAffinity

- Bedeutung: explizite persönliche Affinität zu den am Recipe vorhandenen Tastes.
- Input: Taste-UUIDs und temporäre Affinitäten `-1..1`.
- Normalisierung: `(affinity + 1) / 2`, danach Mittel der explizit bewerteten Recipe-Tastes.
- Mehrere Tastes: jede explizit bewertete Recipe-Taste zählt einmal.
- Keine passende Präferenz: nicht verfügbar; keine Annahme aus bloßer Taste-Zuordnung.
- Richtung: höher ist besser.
- Reasons: `TASTE_STRONG_MATCH` ab `0,75`, `TASTE_MATCH` ab `0,50`, sonst
  `TASTE_MISMATCH`.
- Allergien und harte Ablehnungen gehören nicht in dieses Signal.

## preparationTimeFit

- Bedeutung: Passung der bekannten Recipe-Gesamtzeit zum verfügbaren Zeitbudget.
- Input: optionales positives Nutzerlimit in ganzen Sekunden und `Recipe.getTotalTime()`.
- Innerhalb des Limits: `1`.
- Über dem Limit: `limit / totalTime`.
- Kein Limit: nicht verfügbar.
- Limit vorhanden, Recipe-Zeit fehlt: nicht verfügbar plus `TIME_UNKNOWN`.
- Richtung: höher ist besser.
- Reasons: `TIME_WITHIN_LIMIT`, `TIME_OVER_LIMIT`, `TIME_UNKNOWN`.
- Die aktuelle Domain trennt aktive und passive Zeit nicht sicher. V1 verwendet deshalb
  Vorbereitung + Kochen + Backen + Ruhezeit als konservative Gesamtdauer.

## ingredientAlternativeFit

- R0-Review-Status: im V1-Profil Gewicht `0` und nicht als Signalwert erzeugt.
- Grund: Eine Alternative wirkt bereits über die gemeinsam erreichbare Pantry Coverage und
  den Missing Group Count. Ein zusätzlicher Bonus wäre Doppelzählung und konnte durch
  Renormalisierung paradoxe Verschlechterungen verursachen.
- `ALTERNATIVE_AVAILABLE`: Die optimale Zuordnung verwendet tatsächlich eine
  Nicht-Standardoption.
- `ALTERNATIVE_IMPROVES_COVERAGE`: Die optimale Zuordnung erzielt mehr Coverage als die
  beste zulässige reine Standardoptionen-Zuordnung.
- Ausgeschlossene, aber sicher ersetzbare Optionen erzeugen zusätzlich
  `HARD_EXCLUDED_ALTERNATIVE_IGNORED`.

## recentMealPenalty

- R3-Status: weiterhin nicht als eigenes Signal gesetzt.
- Grund: R3 V1 definiert Variety ausschließlich als Freshness desselben Recipes. Ein
  zusätzlicher spiegelbildlicher Penalty würde dieselbe History doppelt zählen.
- Das konfigurierte Gewicht bleibt aus Kompatibilitätsgründen unverändert, wird aber aus dem
  aktiven Nenner entfernt, solange kein eigenständiges Signal definiert ist.

## varietyScore

- Bedeutung in R3 V1: zeitliche Freshness genau dieses Recipes.
- Quelle: neuestes bestätigtes `MealHistoryEntry.occurredAt`; Source und `createdAt` ändern
  den Wert nicht. MealPlan und Consumption Ledger sind keine Ersatzquellen.
- Berechnung: `clamp(vergangene Sekunden / 14 Tage, 0, 1)`.
- Nie gekocht und mindestens 14 Tage: `1`; heute beziehungsweise zukünftiger Zeitstempel:
  `0`; dazwischen kontinuierlich monoton.
- Ein am aktuellen lokalen Kalendertag bestätigtes Recipe wird bereits in Eligibility
  ausgeschlossen. `RECIPE_COOKED_TODAY` ist deshalb ein Ausschlussgrund und kein zusätzlicher
  Score-Penalty. Ab dem Folgetag bleibt das Recipe Candidate.
- Reasons für bewertete Candidates: `RECIPE_NEVER_COOKED`, `RECENTLY_COOKED` für weniger als
  sieben Tage und `RECIPE_NOT_COOKED_RECENTLY` ab 14 Tagen. Der mittlere Bereich erzeugt
  keine künstlich präzise Anzeige-Reason.
- Recipe-Ähnlichkeit, Frequency und weitere Variety-Dimensionen bleiben außerhalb V1.

## recipePreference

- Bedeutung: langfristige explizite Recipe-Präferenz oder, nur ohne explizites Feedback,
  schwache implizite Recommendation-Interaktion.
- Explizites Mapping auf `-1..1`: Rating 1 `-1`, 2 `-0,5`, 3 `0`, 4 `+0,5`, 5 `+1`;
  LIKE ohne Rating `+0,75`, DISLIKE ohne Rating `-0,75`.
- Interaction-Fallback: `0,5 * (selected - dismissed) / (selected + dismissed + 2)`.
  `SHOWN` und Nicht-Interaktion sind neutral; es gibt keinen erfundenen SKIPPED-Wert.
- Scorer-Normalisierung: `(effectivePreference + 1) / 2`.
- Explizites Feedback überschreibt den Interaction-Fallback vollständig und wird niemals
  mit ihm addiert.
- Reasons unterscheiden starke/einfache explizite Meinung sowie mindestens zweimalige
  eindeutige SELECTED- oder DISMISSED-Mehrheit. Neutrale und einzelne implizite Evidenz
  erzeugen keinen Preference-Reason.

## householdPreference

- Bedeutung: faire gemeinsame Präferenz zulässiger Recipes.
- Input: explizite Taste-Affinitäten relevanter Haushaltsmitglieder.
- Mitgliedswert: Mittel seiner explizit bewerteten Recipe-Tastes, normalisiert auf `0..1`.
- Aggregation: `0,70 * Minimum + 0,30 * Average`.
- Keine passenden Mitgliedsdaten: nicht verfügbar.
- Richtung: höher ist besser.
- Reasons: `HOUSEHOLD_STRONG_MATCH`; bei einer einzelnen starken Ablehnung ausschließlich
  die Konflikterklärung `HOUSEHOLD_CONFLICT` plus `HOUSEHOLD_MEMBER_DISLIKES` und globaler
  Score-Cap.
- Hard Constraints aller Mitglieder laufen vorher in Eligibility und können nie durch dieses
  Signal aufgehoben werden.

## Eligibility Reason Codes

- `RECIPE_HARD_EXCLUDED`: Recipe-UUID ist hart ausgeschlossen.
- `INGREDIENT_GROUP_HARD_EXCLUDED`: keine zulässige Option einer benötigten Gruppe.
- `DISH_TYPE_MISMATCH`: angefragter und tatsächlicher `DishType` unterscheiden sich.
- `MISSING_REQUIRED_INGREDIENT_STRUCTURE`: keine IngredientGroup für Pantry-Bewertung.

Reason Codes enthalten keine UI-Texte. Eine spätere Oberfläche lokalisiert sie und zeigt
positive, negative und Ausschlussgründe getrennt an.

Score-bezogene Reasons werden nur erzeugt, wenn das betreffende Signal im aktiven Profil ein
positives Gewicht besitzt. Eligibility-Gründe bleiben davon unabhängig. Rein informative
Hinweise wie `TIME_UNKNOWN` und Alternative-Hinweise dürfen trotz Gewicht `0` erklären, welche
Daten fehlen beziehungsweise welche Option gewählt wurde.
