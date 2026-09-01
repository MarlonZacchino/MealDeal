# Architekturgrundlagen

MealDeal trennt die Verantwortlichkeiten in vier überschaubare Bereiche. Die Schichten werden erst dann mit Klassen ergänzt, wenn eine spätere Phase sie fachlich benötigt.

## Domain

Enthält die fachlichen Datenobjekte. Die Domain hat weder JavaFX-, JDBC- noch SQLite-Abhängigkeiten.

Das derzeit implementierte Modell ist:

```text
Recipe
 ├── DishType (MAIN or SIDE)
 ├── RecipeIngredientGroup *
 │    └── RecipeIngredientOption *
 │         └── Ingredient
 ├── RecipeStep *
 ├── Taste *
 └── NutritionInfo (optional, per serving)
```

`Ingredient` und `Taste` besitzen eine persistenzunabhängige UUID als stabile technische Identität. Ein Recipe besitzt außerdem genau einen `DishType`: `MAIN` oder `SIDE`. Seine geordnete Liste von `RecipeIngredientGroup`s beschreibt Zutatenbedarfe; jede Gruppe hat eine stabile UUID, mindestens eine geordnete `RecipeIngredientOption` und genau eine ihrer Optionen als Standard. Optionen besitzen ebenfalls stabile UUIDs sowie ihre eigene positive `BigDecimal`-Menge, Unit und Position. `Recipe.getIngredients()` bildet für noch nicht migrierte Anwendungsfälle ausschließlich die Standardoption jeder Gruppe als alte `RecipeIngredient`-Ansicht ab; eine zweite Zutatenliste wird nicht gespeichert. Rezeptschritte sind optional und werden, wenn vorhanden, anhand ihrer eindeutigen Position sortiert. Rezeptmengen werden mit `BigDecimal` gespeichert. Vorbereitungs- und Garzeit sind optionale positive Minutenwerte. Die Gesamtzeit wird zentral daraus abgeleitet: bei zwei Werten als Summe, bei einem Wert als dieser Wert und andernfalls gar nicht. Optionales `NutritionInfo` bündelt Kalorien sowie Protein, Kohlenhydrate und Fett pro Portion; es wird nicht aus Zutaten berechnet und nicht mit Personenanzahlen skaliert. Einheiten weisen ihre Dimension aus, damit spätere Umrechnung nur zwischen kompatiblen Einheiten erfolgt.

## Service

Enthält Geschäftslogik unabhängig von Darstellung und Persistenz. `RecipeScaler` erzeugt für eine gewünschte positive Personenanzahl neue `RecipeIngredient`-Objekte, ohne das gespeicherte Recipe zu verändern. Die Berechnung verwendet `BigDecimal` mit `MathContext.DECIMAL128`.

```text
Recipe
  ↓
RecipeScaler
  ↓
skalierte RecipeIngredient-Daten
```

`Quantity` bildet Menge und Unit als eigenständigen unveränderlichen Wert ab. Addition behält deterministisch die Einheit des ersten Operanden. `UnitConverter` unterstützt ausschließlich identische Units sowie `GRAM` ↔ `KILOGRAM` und `MILLILITER` ↔ `LITER`. Eine gemeinsame `UnitDimension` allein erlaubt keine Konvertierung; insbesondere bleiben Küchenmaße untereinander inkompatibel. `SLICE`, `CLOVE` und `SPRIG` teilen als Zähleinheiten die Dimension `COUNT` mit `PIECE`, sind aber jeweils nur zu sich selbst konvertierbar und werden in Einkaufslisten getrennt aggregiert.

`RecipeSearchService` bewertet eine übergebene Recipe-Sammlung anhand der UUID-Identitäten ausgewählter Ingredients oder Tastes. Er lädt selbst keine Daten und besitzt keine Repository-Abhängigkeit.

```text
Recipe collection
      ↓
RecipeSearchService
      ↓
IngredientSearchResult / TasteSearchResult
```

Zutaten werden nach Trefferzahl bewertet. Taste-Suchen unterstützen `AND`, `OR` und `RANKING`. Ranking-Ergebnisse verwenden gemeinsam `PERFECT` für vollständige Treffer, `GOOD` für mehr als die Hälfte und andernfalls `PARTIAL`. Namen dienen nur der stabilen Ergebnissortierung; Ähnlichkeit, Normalisierung und Synonyme sind nicht Bestandteil der Suche.

## Persistence

Kapselt den direkten JDBC-Zugriff auf SQLite. Repository-Schnittstellen kennen nur Domain- und Standard-Java-Typen; ihre SQLite-Implementierungen bilden Domain-Objekte auf das relationale Schema ab.

```text
Repository Interface
        ↓
SQLite Repository
        ↓
       JDBC
        ↓
      SQLite
```

Schema-Versionen werden beim Öffnen über `PRAGMA user_version` schrittweise migriert. Version 2 ergänzt `meal_plan_entries`, Version 3 ergänzt die zwei nullable Zeitspalten `preparation_time_minutes` und `cooking_time_minutes` in `recipes`, Version 4 ergänzt dort die vier nullable Nährwertspalten pro Portion. Version 5 ergänzt `recipes.dish_type` und ersetzt den früheren einzelnen Meal-Plan-Eintrag pro Datum durch Haupt- und Beilageneinträge. Vorhandene Daten bleiben erhalten; bestehende Recipes und Planungseinträge migrieren jeweils zu `MAIN`. UUIDs werden als Text, `BigDecimal`-Mengen und Nährwert-Grammwerte verlustfrei als Dezimaltext und Units über ihre Enum-Namen gespeichert. Jede neue Verbindung aktiviert SQLite-Foreign-Keys ausdrücklich.

Ingredients und Tastes werden über ihre eigenen Repositories verwaltet und müssen vor einem referenzierenden Recipe existieren. Das vollständige Speichern oder Aktualisieren eines Recipe läuft in einer Transaktion. Dabei werden seine Beziehungs- und Schrittzeilen verständlich und atomar ersetzt; bei Fehlern erfolgt ein Rollback. Ein Recipe ohne Zubereitung erzeugt dabei schlicht keine Zeile in `recipe_steps`; das bestehende Schema benötigt dafür keine Migration.

Beim Erstellen über die UI werden neue Ingredients und Tastes deshalb zuerst über ihre eigenen Repositories gespeichert und erst danach das Recipe. Diese drei Repository-Aufrufe teilen bewusst keine übergreifende Transaktion: Schlägt der abschließende Recipe-Aufruf fehl, bleiben zuvor angelegte zentrale Einträge als weiterhin verwendbare Daten bestehen. Diese für Phase 2.3 gewählte Teilpersistenz vermeidet eine vorgezogene Unit-of-Work- beziehungsweise Connection-Refaktorierung; der Fehler wird in der UI ausdrücklich angezeigt.

Die produktive Windows-Anwendung verwendet `%LOCALAPPDATA%\MealDeal\mealdeal.db` als lokalen, nicht roamingfähigen Datenbankpfad. `ApplicationDataPaths` prüft die Umgebungsvariable und legt das Anwendungsverzeichnis bei Bedarf an. Schlägt diese Startvorbereitung fehl, wird der Fehler ausdrücklich weitergegeben statt unbemerkt auf einen anderen Speicherort auszuweichen.

## Wochenplanung

`MealPlanEntry` verbindet über eine eigene UUID ein `LocalDate` mit einem bereits persistierten Recipe und einer individuellen Personenanzahl. Jeder Eintrag besitzt zusätzlich die Rolle `MAIN` oder `SIDE` sowie eine Position. Die Rolle muss dem `DishType` des Recipe entsprechen. SQLite erzwingt mit einem partiellen Unique-Index höchstens ein `MAIN` pro Datum; `SIDE`-Einträge sind auch ohne Hauptgericht beliebig oft erlaubt und ihre Position ist pro Datum eindeutig. Leere Tage werden nicht persistiert. Ein Recipe mit Planungshistorie ist durch `ON DELETE RESTRICT` vor versehentlichem Löschen geschützt.

```text
Recipe
   ↑
MealPlanEntry
   │
   ▼
MealPlanRepository
   │
   ▼
SQLite
```

`WeekService` überlässt Monats-, Jahres- und Schaltjahresgrenzen vollständig `LocalDate` und liefert einen `WeekRange` von Montag bis Sonntag. `MealPlanCleanupService` verwendet einen injizierbaren `Clock`: Einträge mit `date < today.minusDays(30)` werden explizit gelöscht, während genau 30 Tage alte Einträge erhalten bleiben. Die Phase enthält noch keine Skalierung oder Einkaufslistenberechnung für geplante Rezepte.

`WeeklyMealPlanService` bildet darauf den Anwendungsfall für die aktuelle Woche. Ein injizierbarer `Clock` bestimmt das lokale Heute, `WeekService` liefert die sieben konkreten Datumswerte und `MealPlanRepository.findBetween()` lädt vorhandene Planungen. Für jeden Tag entsteht ein unveränderlicher `MealPlanDay` aus optionalem `MAIN`, geordneten `SIDE`-Einträgen und Heute-Markierung. `WeeklyMealPlanDayDraft` hält die zugehörigen lokalen Änderungen JavaFX-unabhängig: Er bewahrt bestehende UUIDs bei Recipe-, Portions- und Positionsänderungen, erzeugt UUIDs nur für neue Einträge und hält SIDE-Positionen lückenlos. Die UI übergibt diese Snapshots als `MealPlanDraft`s gesammelt an den Service. Dieser vergleicht sie mit dem persistierten Stand und übergibt ausschließlich neue, geänderte oder entfernte Einträge als Change-Set an das Repository. Die SQLite-Implementierung führt dieses Change-Set in einer Transaktion aus: Bei einem Fehler bleibt die ganze Woche unverändert. Beim Vertauschen von SIDE-Positionen gibt sie innerhalb der Transaktion nur die tatsächlich geänderten IDs frei und speichert sie sofort mit denselben UUIDs neu, damit der Positionsindex nicht temporär kollidiert. Die individuelle Personenanzahl bleibt Bestandteil jedes `MealPlanEntry`.

## Einkaufslistenberechnung

`ShoppingListService` berechnet eine Einkaufsliste deterministisch aus allen aktuellen Planungs- und Recipe-Daten. Sie berücksichtigt für Heute sämtliche `MAIN`- und `SIDE`-Einträge des Tages und für die Woche alle Einträge von Heute bis Sonntag, einschließlich reiner Beilagen-Tage. MealRole und SIDE-Position beeinflussen die Aggregation nicht; jede Zutatenmenge wird ausschließlich mit der individuellen Portionszahl ihres `MealPlanEntry` skaliert. Die Liste wird nicht persistiert, damit keine doppelte oder nach Recipe-Änderungen veraltete Datenhaltung entsteht.

```text
MealPlanRepository
        ↓
MealPlanEntry
        ↓
RecipeScaler
        ↓
ShoppingListService
        ↓
ShoppingList
        └── ShoppingListItem
```

Die geplante Personenanzahl fließt ausschließlich über `RecipeScaler` ein. Gleiche Zutaten werden anhand ihrer UUID und ihrer tatsächlichen Unit-Kompatibilitätsgruppe zusammengeführt; Addition verwendet `Quantity.add()` und behält damit die Einheit des ersten Eintrags. Inkompatible Angaben wie Stück und Gramm bleiben getrennte Positionen. Ein injizierbarer `Clock` bestimmt das lokale Heute. Die aktuelle Wochenliste lädt nur den Bereich von heute bis Sonntag und schließt gespeicherte vergangene Tage ausdrücklich aus.

## UI

Verwendet JavaFX für Darstellung und Benutzereingaben. Die deklarativen FXML-Dateien beschreiben die Struktur der Views; eine zentrale CSS-Datei gestaltet das gemeinsame Anwendungsgerüst. Controller behandeln ausschließlich UI-Ereignisse und Navigation. Sie enthalten keine Geschäftslogik und keine direkten SQL-Zugriffe.

```text
FXML-Views
    ↓
JavaFX-Controller
    ↓
ApplicationContext / Services
    ↓
Domain und Repositories
```

`MealDealApplication` öffnet genau eine primäre Stage. `MainController` hält die Seitenleiste dauerhaft sichtbar, während `ViewNavigator` nur den Inhaltsbereich austauscht und den aktiven Navigationseintrag markiert. Dadurch entstehen beim Wechsel zwischen Start, Gerichten, Suche, Wochenplan und Einkauf keine zusätzlichen Fenster.

Jede Hauptansicht verwendet einen vertikal scrollbar ausgeführten Inhaltsbereich. Darin zentriert ein `StackPane` einen Seitencontainer, der bei kleineren Fenstern automatisch schrumpft. Das Layout richtet sich ausschließlich nach dem verfügbaren Scene- und Pane-Bereich; Monitorauflösung, DPI-Prozentwerte oder feste Bildschirmkoordinaten fließen nicht in die Positionierung ein. So bleibt der Inhalt beim Maximieren, Verkleinern und Verschieben zwischen Monitoren stabil, ohne auf den primären Monitor zurückzuspringen.

Die Anwendung überlässt die Per-Monitor-DPI-Skalierung vollständig dem JavaFX-Windows-Toolkit. Weder `MealDealApplication` noch Controller oder FXML setzen Node-/Scene-Transforms, `scaleX`/`scaleY`, `renderScale`, `outputScale` oder Listener für Bildschirm- und Fensterkoordinaten. JavaFX 26 verarbeitet unter Windows den nativen DPI-Wechsel beim Verschieben eines Fensters und aktualisiert damit `outputScale` und den standardmäßig daran gekoppelten `renderScale`.

`MainController` kennzeichnet den Root nur anhand der verfügbaren logischen Scene-Breite als normal, breit (ab 1440 px) oder extrabreit (ab 2100 px). Das zentrale Stylesheet erweitert dann die regulären Seitencontainer von 1080 px beziehungsweise die Detailansicht von 1240 px auf bis zu 1320/1440 px oder 1640/1760 px. Gleichzeitig wachsen Seitenleiste, Abstände, Titel und Controls moderat. Unterhalb dieser Breiten bleiben die bisherigen Maße unverändert. Beschreibende Texte behalten eigene Maximalbreiten, damit breite Karten keine unnötig langen Zeilen erzeugen.

Auf der Startseite führt eine einzige zentrale Aktion „Gericht finden“ in die kombinierte Zutaten- und Geschmackssuche. Tages- und Wochenplanung stehen entsprechend ihrer fachlichen Gewichtung als breite Karten direkt untereinander. Diese vertikale Reihenfolge bleibt auch bei schmaleren Fenstern eindeutig und verhindert ein unnötiges horizontales Auseinanderziehen.

`HomeController` lädt die sichtbaren Planungszusammenfassungen ausschließlich über `WeeklyMealPlanService.loadCurrentWeek()`. Für Heute rendert er das von `MealPlanDay` gelieferte optionale Hauptgericht und alle geordneten Beilagen mit ihrer jeweiligen Portionszahl; ein Beilagen-only-Tag ist damit kein leerer Tag. Die kompakte Wochenübersicht verwendet dasselbe Modell für alle sieben Tage. `HomeMealPlanViewModel` bleibt JavaFX-unabhängig und überführt nur dieses bereits fachlich validierte Tagesmodell in Präsentationsdaten; der Controller enthält weder Repository-Zugriffe noch MAIN/SIDE-Fachlogik.

Das gemeinsame Stylesheet definiert die Oberflächenfarben als vererbte UI-Tokens. Die helle Palette verwendet `#6f1d35` als ruhigen Bordeaux-Hauptakzent; der Dark Mode nutzt eine kontraststärkere Ausprägung derselben Farbfamilie. Fehler- und Warnfarben bleiben eigene semantische Tokens. `MainController` schaltet am permanenten Root-Layout ausschließlich die CSS-Klasse `theme-dark`, sodass alle geladenen Views ohne Neustart folgen. `ThemeService` hält diese reine UI-Einstellung unabhängig von Repositories und Fachlogik und speichert sie unter `%LOCALAPPDATA%\MealDeal\theme.properties` neben der SQLite-Datei. Beim nächsten Start wird die Auswahl wiederhergestellt.

`ApplicationContext` übernimmt die bewusste manuelle Zusammensetzung der UI. Er erstellt für den konfigurierten Datenbankpfad die SQLite-Implementierungen von `RecipeRepository`, `IngredientRepository`, `TasteRepository` und `MealPlanRepository` auf einer gemeinsamen `SqliteDatabase` und injiziert die benötigten Schnittstellen und Services in die Controller; ein Dependency-Injection-Framework ist dafür nicht erforderlich.

Der `RecipesController` lädt die gespeicherten Rezepte beim Öffnen der Ansicht neu. Er sortiert sie deterministisch nach Name und bei Namensgleichheit nach UUID und erzeugt daraus kompakte, auswählbare UI-Einträge. Jedes zeigt seinen `DishType` als deutsches Label (`Hauptgericht` oder `Beilage`), ohne die Sortierung oder Navigation zu verändern. Empty State und Ladefehler sind eigene sichtbare Zustände.

Ein ausgewähltes Recipe wird als vollständiges Domain-Objekt an die Detailansicht im bestehenden Inhaltsbereich übergeben. Der `RecipeDetailController` erhält den `RecipeScaler` über den `ApplicationContext` und bezieht bei jeder Änderung der Personenanzahl ausschließlich von ihm neue Zutatenmengen. Die Anzeige formatiert `BigDecimal` ohne binäre Fließkommazahlen mit deutschem Dezimaltrennzeichen und verwendet deutsche Küchenbezeichnungen für Units. Sein `DishType` erscheint als deutsches Metadaten-Label. Vorhandene Zeitwerte erscheinen als deutsche Kurzform (`25 Min.`, `1 Std. 20 Min.`); bei vollständig fehlenden Werten erscheint kein Zeitbereich. Ein Nährwertbereich erscheint ebenso nur bei mindestens einem Wert und zeigt die Angaben unverändert pro Portion. Das gespeicherte Recipe bleibt unverändert.

Das Löschen eines Recipe wird in der Detailansicht ausdrücklich bestätigt und anschließend ausschließlich über `RecipeRepository.deleteById()` ausgeführt. Die abhängigen Recipe-Beziehungszeilen werden durch die bestehenden Cascades entfernt, zentrale Ingredients und Tastes jedoch nicht. Verhindert eine vorhandene Wochenplanung das Löschen per `ON DELETE RESTRICT`, übersetzt die SQLite-Implementierung diesen konkreten Constraint in eine `RecipeDeletionRestrictedException`; die UI bleibt in der Detailansicht und erklärt den Konflikt, ohne Planungsdaten automatisch zu verändern.

Die Zutaten-Suche lädt zentrale Ingredients und Recipes über ihre Repository-Schnittstellen. `IngredientSearchModel` hält ausschließlich den JavaFX-unabhängigen Auswahlzustand von einer bis zehn Zutaten. Reine Zutatenabfragen und kombinierte Abfragen reichen die ausgewählten Ingredient-Identitäten unverändert an `RecipeSearchService.searchByIngredients()` weiter. Ranking, Trefferzahl, fehlende Zutaten und Qualitätsstufe stammen ausschließlich aus dessen `IngredientSearchResult`; die UI navigiert vom Ergebnis zum vollständigen Recipe-Detail.

Die Geschmackssuche folgt derselben Trennung: `TasteSearchModel` lädt zentrale Tastes und verwaltet nur deren Auswahlzustand. `AND`, `OR` oder `RANKING` werden unverändert an `RecipeSearchService.searchByTastes()` delegiert. Zutaten- und Geschmacksauswahl bleiben getrennte UI-Zustände, können aber über eine gemeinsame Suchaktion angewendet werden; der Ergebnisbereich navigiert weiterhin zur Recipe-Detailansicht.

`CombinedRecipeSearchService` führt beide Suchen ohne SQL und ohne neuen gewichteten Score zusammen. Ist nur ein Filter aktiv, gibt er die bestehende Reihenfolge des jeweiligen `RecipeSearchService`-Ergebnisses unverändert weiter. Sind beide aktiv, bildet er die Schnittmenge: Das Zutaten-Ranking bleibt primär, bei gleichen Zutaten-Treffern dient im Modus `RANKING` die bestehende Taste-Trefferzahl als Tie-Breaker; Name und UUID schließen die deterministische Sortierung ab. `AND` und `OR` werden bereits vor der Schnittmenge ausschließlich durch `RecipeSearchService` ausgewertet. Die UI zeigt beide Teilbewertungen getrennt und kann beide Auswahlmodelle gemeinsam zurücksetzen.

`CreateRecipeController` hält ausschließlich den dynamischen JavaFX-Formularzustand und die Navigation. Derselbe FXML- und Controller-Stack dient dem Erstellen und Bearbeiten: Im Bearbeitungsmodus werden alle vorhandenen Werte einschließlich des `DishType` vorausgefüllt, Abbrechen führt zum unveränderten Detailobjekt zurück und Speichern zeigt das aktualisierte Detailobjekt. Neue Formulare wählen standardmäßig `Hauptgericht`; der `RecipeFormService` weist eine fehlende Typauswahl zurück. Er verarbeitet einen JavaFX-unabhängigen `RecipeFormInput`, validiert ihn vollständig, löst bestehende zentrale Daten namensbasiert auf und orchestriert die Repository-Aufrufe. Beim Aktualisieren konstruiert er das neue unveränderliche Recipe mit der bestehenden UUID; das Repository ersetzt dessen gespeicherte Daten atomar. Mengen akzeptieren positive Ganz- und Dezimalzahlen mit Komma oder Punkt als Dezimaltrennzeichen und werden ohne `double` oder `float` direkt als `BigDecimal` verarbeitet. Vorbereitungs- und Garzeit akzeptieren nur positive ganze Minutenwerte; die Gesamtzeit wird ausschließlich im Domainmodell daraus abgeleitet. Optionale Nährwerte akzeptieren nichtnegative Werte und gelten unverändert pro Portion. Zubereitungsschritte sind optional; nichtleere Einträge werden aus ihrer sichtbaren, lückenlos nummerierten Reihenfolge erzeugt und können später ergänzt werden. SQL, Portionierung und Suchlogik bleiben außerhalb der Controller.

`WeekPlanController` rendert die sieben vom `WeeklyMealPlanService` gelieferten Tage als responsive Karten mit getrennten Bereichen für ein optionales Hauptgericht und beliebig viele Beilagen. Die Auswahlfelder erhalten ausschließlich Recipes ihres passenden `DishType`. Neue Beilagen übernehmen die aktuelle Hauptgericht-Portionszahl oder ohne Hauptgericht den Standardwert; danach bleiben alle Portionszahlen unabhängig. Pfeile ändern die lokale SIDE-Reihenfolge ohne Drag & Drop. Der Controller formatiert ausschließlich sichtbare deutsche Tages- und Datumsangaben und delegiert alle lokalen Planänderungen an `WeeklyMealPlanDayDraft`. Der zentrale Button „Änderungen speichern“ übergibt alle Tagesentwürfe gemeinsam an den Service; danach lädt die Ansicht den persistierten Stand neu. Geplante Gerichte öffnen über den bestehenden `ViewNavigator` dieselbe Recipe-Detailansicht wie die Gerichte- und Suchansichten. SQL, Transaktionen und Kalenderberechnung bleiben außerhalb des Controllers.

`ShoppingListController` erhält den `ShoppingListService` manuell über den `ApplicationContext`. Seine beiden Umschaltzustände delegieren ausschließlich an `buildForToday()` beziehungsweise `buildForCurrentWeek()` und zeigen das zurückgegebene, bereits aggregierte `ShoppingList`-Ergebnis an. Er speichert weder Einkaufslisten noch Mengen und führt keine eigene Skalierungs-, Datums- oder Aggregationslogik aus. Für die Anzeige verwendet er dieselbe deutsche `BigDecimal`- und Unit-Formatierung wie die Recipe-Detailansicht; leere und fehlerhafte Ladezustände bleiben sichtbar getrennt.
