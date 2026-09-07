# Recommendation Contract und Scoring Model

Status: R0-Baseline, Profil `V1`, experimentell. Der interne Score ist nicht kalibriert
und darf deshalb nicht als Prozentwert dargestellt werden.

## Bestehender fachlicher Stand

Direkt nutzbar sind:

- `Recipe` mit stabiler UUID, `DishType`, Standardportionen, Tastes und optionaler Gesamtzeit,
- geordnete `RecipeIngredientGroup`s mit stabilen, mengen- und unitbezogenen Alternativen,
- `InventoryItem`s mit Ingredient-UUID, nichtnegativer Menge und Unit,
- `RecipeScaler` für exakte Portionsskalierung,
- `UnitConverter` für identische Units, Gramm/Kilogramm und Milliliter/Liter,
- `MealPlanEntry`s mit Recipe, Datum und Portionen sowie maximal 30 Tagen Historie.

Nicht vorhanden oder nicht sicher ableitbar sind:

- persistierte Allergien und sonstige harte Personenregeln,
- positive oder negative Taste-Präferenzwerte,
- persistierte Haushalte und deren Mitglieder,
- eine bestätigte Koch-/Akzeptanzhistorie. Vergangene Planungen zeigen nur Planung; das
  Verbrauchsledger speichert keinen Recipe-Verweis. Beides ist kein sicherer Beleg für
  eine gegessene Mahlzeit,
- Ablaufdaten, Preise, Einkaufswege, Budgetziele und Ernährungsziele,
- eine Trennung von aktiver und passiver Recipe-Zeit.

R0 ergänzt dafür keine Persistenz. Noch nicht persistierte Daten werden ausschließlich als
temporärer Request-Contract angenommen oder ausdrücklich zurückgestellt.

## RecommendationContext

`RecommendationContext` enthält genau den Snapshot einer Anfrage:

- `RecommendationRequest`: gewünschte positive Portionszahl, optionales Zeitlimit und
  optionaler `DishType`,
- unveränderlicher Inventory-Snapshot,
- optionale individuelle Taste-Affinitäten im Bereich `-1..1`,
- optionale Haushaltsmitglieder mit eigenen Taste-Affinitäten und Hard Constraints,
- globale Hard Constraints für Recipe- und Ingredient-UUIDs.

Repositories, JavaFX-Zustand und persistierte Profilobjekte gehören nicht in den Context.
Recent Meal History und Variety werden erst ergänzt, wenn R2 eine belastbare Quelle für
„tatsächlich gekocht/akzeptiert“ definiert.

## Candidate Eligibility

Eligibility läuft immer vollständig vor dem Scoring. Ein ausgeschlossener Kandidat erhält
keinen niedrigen Score, sondern erscheint in `RecommendationOutcome.exclusions`.

Ein Recipe wird ausgeschlossen, wenn mindestens eine Regel greift:

1. seine UUID ist global oder für ein relevantes Haushaltsmitglied ausgeschlossen,
2. der angefragte `DishType` stimmt nicht überein,
3. es besitzt keine IngredientGroup und ist damit für diese Baseline strukturell unvollständig,
4. eine IngredientGroup besitzt nach Zusammenführung aller Hard Constraints keine einzige
   zulässige Option.

Bei Alternativen bleibt ein Recipe zulässig, wenn je Gruppe mindestens eine sichere Option
existiert. Ausgeschlossene Optionen werden niemals für Pantry-Score oder Vorschlag verwendet.
Das modelliert bekannte Ingredient-Ausschlüsse, aber keine unbekannte Kreuzkontamination.

## Scoring Profile V1

Alle verfügbaren Signale liegen in `0..1`. Für positive Signale ist höher besser. Penalties
werden bei der Aggregation in `1 - penalty` umgewandelt. Fehlende Signale werden nicht mit
einem erfundenen neutralen Wert belegt; ihre Gewichte werden für diese Empfehlung aus dem
Nenner entfernt.

```text
benefit(s) = value(s)                         für positive Signale
benefit(s) = 1 - value(s)                     für Penalties

score = Summe(weight(s) * benefit(s))
        / Summe(weight(s))                     nur über verfügbare Signale
```

Der Score wird in `0..1` gehalten. Die zentralen Startgewichte sind:

| Signal | Gewicht | Begründung |
|---|---:|---|
| pantryCoverage | 0,35 | stärkstes Einzelsignal des Pantry-first-Fokus |
| missingIngredientPenalty | 0,15 | ergänzt Abdeckung um die Breite des Einkaufsbedarfs |
| tasteAffinity | 0,15 | persönliche Passung, aber nicht Safety |
| preparationTimeFit | 0,10 | konkrete Entscheidungssituation ohne Pantry zu überstimmen |
| ingredientAlternativeFit | 0,05 | kleiner Bonus für eine tatsächlich hilfreichere Alternative |
| recentMealPenalty | 0,05 | für R2 reserviert; in R0 nicht verfügbar |
| varietyScore | 0,05 | für R2 reserviert; in R0 nicht verfügbar |
| householdPreference | 0,10 | normales Haushaltsaggregat; starke Ablehnung greift zusätzlich als Cap |

Pantry Coverage und Missing Penalty tragen zusammen 50 Prozent der Vollprofil-Gewichte.
Die beiden Signale sind bewusst korreliert, aber nicht identisch: Coverage misst die
Mengenabdeckung je Gruppe, Missing Penalty die Zahl nicht vollständig gedeckter Gruppen.
Alle Werte und Gewichte sind Experimente und müssen in R4 gegen die vorab definierten
Metriken geprüft werden.

## Household Fairness

Verglichene Varianten:

| Modell | Vorteil | Nachteil |
|---|---|---|
| Minimum | schützt schwächstes Mitglied maximal | ignoriert alle übrigen Präferenzen |
| Average | einfach und stabil | starke Ablehnung kann überstimmt werden |
| Weighted Average | bildet Prioritäten ab | benötigt fachlich begründete Personengewichte |
| Hybrid Least-Misery + Aggregate | schützt Ablehnung und nutzt gemeinsames Signal | Parameter sind experimentell |

V1 verwendet den Hybrid. Für jedes Mitglied werden explizit vorhandene Affinitäten der
Recipe-Tastes gemittelt und von `-1..1` auf `0..1` normalisiert. Mitglieder ohne passende
Präferenzdaten gehen nicht in das Aggregat ein.

```text
householdPreference = 0,70 * minimum(memberScores)
                    + 0,30 * average(memberScores)
```

Hard Constraints aller Mitglieder wurden bereits vorher geprüft. Liegt eine einzelne
explizite Taste-Affinität bei höchstens `0,20` normalisiert (entspricht `-0,60` roh), gilt
sie als starke Ablehnung. Dann wird der gesamte Recommendation Score auf höchstens `0,39`
begrenzt und `HOUSEHOLD_MEMBER_DISLIKES` ausgegeben. Zusätzliche positive Mitglieder können
diese Schutzwirkung deshalb nicht wegmitteln.

## Qualitative Bänder

Die Grenzen sind zentral im Profil definiert und ausdrücklich experimentell:

| Band | interner Score |
|---|---:|
| `SEHR_PASSEND` | `>= 0,80` |
| `GUT_PASSEND` | `>= 0,65` und `< 0,80` |
| `PASSEND` | `>= 0,50` und `< 0,65` |
| `WENIGER_PASSEND` | `< 0,50` |

Sie sind qualitative Ranghilfen, keine Wahrscheinlichkeiten und keine Prozentversprechen.

## Tie Breaking

Bei identischem Score gilt deterministisch:

1. weniger fehlende IngredientGroups,
2. höhere Pantry Coverage,
3. bekannte kürzere Gesamtzeit; fehlende Zeit zuletzt,
4. Recipe-Name ohne Beachtung der Groß-/Kleinschreibung,
5. exakter Recipe-Name,
6. stabile Recipe-UUID.

Die Reihenfolge der Candidate- oder Inventory-Eingabe verändert das Ergebnis nicht.

## Bekannte Grenzen und Deferred Decisions

- R0 besitzt keine UI, keine Repositories und keine Profilpersistenz.
- Allergie-/Ausschlussdaten werden nur als bereits bekannte Ingredient- oder Recipe-UUIDs
  angenommen. Zutatenherkunft und Kreuzkontamination sind nicht modellierbar.
- Als Zeitfit dient konservativ die abgeleitete Gesamtzeit. Aktiv/passiv kann mit dem
  aktuellen Modell nicht zuverlässig getrennt werden.
- Fehlende Recipe-Zeit wird bei vorhandenem Nutzerlimit erklärt, aber nicht geraten oder
  künstlich bestraft.
- Recent Meal Penalty und Variety bleiben bis R2 nicht verfügbar. Ihre Gewichte sind bereits
  versioniert; die aktive Gewichtssumme wird ohne sie normalisiert.
- Kosten, Expiry, Nutrition Goals, ML und externe AI sind nicht Bestandteil von V1.
