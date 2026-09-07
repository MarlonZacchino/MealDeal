# Recommendation Signals V1

Alle Werte sind deterministisch und auf `0..1` normalisiert. „Nicht verfügbar“ bedeutet,
dass das Signal bei dieser Anfrage nicht in die gewichtete Summe eingeht.

## pantryCoverage

- Bedeutung: durchschnittlicher mengenbezogener Inventory-Deckungsgrad der benötigten Gruppen.
- Input: skaliertes Recipe, Inventory-Snapshot, zulässige Optionen.
- Berechnung je Option: `min(1, kompatibler Bestand / skalierter Bedarf)`.
- Berechnung je Gruppe: Maximum ihrer zulässigen Optionen; eine Gruppe zählt genau einmal.
- Gesamtsignal: arithmetisches Mittel der Gruppenabdeckungen.
- Units: nur Regeln aus `UnitConverter`; inkompatible Units tragen null bei.
- Alternativen: bei Gleichstand Standardoption, danach Position und UUID. Mehrere verfügbare
  Alternativen erzeugen keine Mehrfachbelohnung.
- Leeres Inventory: `0`. Mengenüberdeckung: auf `1` begrenzt.
- Richtung: höher ist besser.
- Reasons: `PANTRY_FULL_COVERAGE`, `PANTRY_MOSTLY_COVERED` ab `0,75`, sonst
  `PANTRY_LOW_COVERAGE`.

## missingIngredientPenalty

- Bedeutung: Anteil der IngredientGroups, deren beste zulässige Option nicht vollständig
  mengenmäßig gedeckt ist.
- Input: dieselben Gruppenabdeckungen wie Pantry Coverage.
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
- Input: optionales Nutzerlimit und `Recipe.getTotalTime()`.
- Innerhalb des Limits: `1`.
- Über dem Limit: `limit / totalTime`.
- Kein Limit: nicht verfügbar.
- Limit vorhanden, Recipe-Zeit fehlt: nicht verfügbar plus `TIME_UNKNOWN`.
- Richtung: höher ist besser.
- Reasons: `TIME_WITHIN_LIMIT`, `TIME_OVER_LIMIT`, `TIME_UNKNOWN`.
- Die aktuelle Domain trennt aktive und passive Zeit nicht sicher. V1 verwendet deshalb
  Vorbereitung + Kochen + Backen + Ruhezeit als konservative Gesamtdauer.

## ingredientAlternativeFit

- Bedeutung: tatsächliche Verbesserung der Pantry-Abdeckung durch eine zulässige
  Nicht-Standardoption.
- Input: Gruppen mit mindestens einer zulässigen Alternative.
- Je relevante Gruppe: `max(0, beste Abdeckung - Abdeckung der Standardoption)`.
- Gesamtsignal: Mittel dieser Verbesserungen.
- Keine zulässige Alternative: nicht verfügbar.
- Mehrere Alternativen derselben Gruppe: höchstens die beste Verbesserung zählt einmal.
- Richtung: höher ist besser.
- Reason: `ALTERNATIVE_AVAILABLE`, wenn eine bessere Nicht-Standardoption vorgeschlagen wird.
- Ausgeschlossene, aber sicher ersetzbare Optionen erzeugen zusätzlich
  `HARD_EXCLUDED_ALTERNATIVE_IGNORED`.

## recentMealPenalty

- Bedeutung: späterer Malus für ein kürzlich tatsächlich gekochtes Recipe.
- Geplante V1-Range: `0` nicht kürzlich gekocht bis `1` unmittelbar wiederholt.
- R0-Status: nicht verfügbar und nicht berechnet.
- Grund: MealPlan-Daten belegen Planung, nicht Akzeptanz oder tatsächliches Kochen; das
  Verbrauchsledger besitzt keinen Recipe-Verweis.
- Abgrenzung zu Variety: Recency betrachtet ausschließlich dasselbe Recipe und zeitlichen
  Abstand, nicht Ähnlichkeit oder Verteilung anderer Gerichte.
- Vorgesehener Reason: `RECENTLY_COOKED`.

## varietyScore

- Bedeutung: spätere Vielfalt gegenüber einer Folge tatsächlich gekochter Gerichte.
- Geplante V1-Range: `0` sehr repetitiv bis `1` hohe relevante Vielfalt.
- R0-Status: nicht verfügbar und nicht berechnet.
- Abgrenzung zu Recency: Variety betrachtet Muster über unterschiedliche Recipes, DishTypes,
  Tastes oder zentrale Ingredients; es bestraft nicht nochmals nur denselben Recipe-Abstand.
- Erst R2 darf festlegen, welche Dimensionen fachlich zählen und welche Historie belastbar ist.
- Vorgesehener Reason: `VARIETY_BONUS`.

## householdPreference

- Bedeutung: faire gemeinsame Präferenz zulässiger Recipes.
- Input: explizite Taste-Affinitäten relevanter Haushaltsmitglieder.
- Mitgliedswert: Mittel seiner explizit bewerteten Recipe-Tastes, normalisiert auf `0..1`.
- Aggregation: `0,70 * Minimum + 0,30 * Average`.
- Keine passenden Mitgliedsdaten: nicht verfügbar.
- Richtung: höher ist besser.
- Reasons: `HOUSEHOLD_STRONG_MATCH`; bei einer einzelnen starken Ablehnung
  `HOUSEHOLD_MEMBER_DISLIKES` plus globaler Score-Cap.
- Hard Constraints aller Mitglieder laufen vorher in Eligibility und können nie durch dieses
  Signal aufgehoben werden.

## Eligibility Reason Codes

- `RECIPE_HARD_EXCLUDED`: Recipe-UUID ist hart ausgeschlossen.
- `INGREDIENT_GROUP_HARD_EXCLUDED`: keine zulässige Option einer benötigten Gruppe.
- `DISH_TYPE_MISMATCH`: angefragter und tatsächlicher `DishType` unterscheiden sich.
- `MISSING_REQUIRED_INGREDIENT_STRUCTURE`: keine IngredientGroup für Pantry-Bewertung.

Reason Codes enthalten keine UI-Texte. Eine spätere Oberfläche lokalisiert sie und zeigt
positive, negative und Ausschlussgründe getrennt an.
