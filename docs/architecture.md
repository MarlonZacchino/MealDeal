# Architekturgrundlagen

MealDeal trennt die Verantwortlichkeiten in vier überschaubare Bereiche. Die Schichten werden erst dann mit Klassen ergänzt, wenn eine spätere Phase sie fachlich benötigt.

## Domain

Enthält die fachlichen Datenobjekte. Die Domain hat weder JavaFX-, JDBC- noch SQLite-Abhängigkeiten.

Das derzeit implementierte Modell ist:

```text
Recipe
 ├── DishType (MAIN, SIDE or DESSERT)
 ├── RecipeIngredientGroup *
 │    └── RecipeIngredientOption *
 │         └── Ingredient
 ├── RecipeStep *
 ├── Taste *
 └── NutritionInfo (optional, per serving)
```

`Ingredient` und `Taste` besitzen eine persistenzunabhängige UUID als stabile technische Identität. Ein Recipe besitzt außerdem genau einen `DishType`: `MAIN`, `SIDE` oder `DESSERT`. Seine geordnete Liste von `RecipeIngredientGroup`s beschreibt Zutatenbedarfe; jede Gruppe hat eine stabile UUID, mindestens eine geordnete `RecipeIngredientOption` und genau eine ihrer Optionen als Standard. Optionen besitzen ebenfalls stabile UUIDs sowie ihre eigene positive `BigDecimal`-Menge, Unit und Position. `Recipe.getIngredients()` bildet für noch nicht migrierte Anwendungsfälle ausschließlich die Standardoption jeder Gruppe als alte `RecipeIngredient`-Ansicht ab; eine zweite Zutatenliste wird nicht gespeichert. Rezeptschritte sind optional und werden, wenn vorhanden, anhand ihrer eindeutigen Position sortiert. Rezeptmengen werden mit `BigDecimal` gespeichert. Vorbereitungs-, Koch-, Back- und Ruhezeit sind optionale positive Minutenwerte. Die Gesamtzeit wird zentral als Summe aller gesetzten Einzelzeiten abgeleitet; ist keine gesetzt, bleibt auch die Gesamtzeit leer. Optionales `NutritionInfo` bündelt Kalorien sowie Protein, Kohlenhydrate und Fett pro Portion; es wird nicht aus Zutaten berechnet und nicht mit Personenanzahlen skaliert. Einheiten weisen ihre Dimension aus, damit spätere Umrechnung nur zwischen kompatiblen Einheiten erfolgt.

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

Schema-Versionen werden beim Öffnen über `PRAGMA user_version` schrittweise migriert. Version 2 ergänzt `meal_plan_entries`, Version 3 ergänzt die zwei nullable Zeitspalten `preparation_time_minutes` und `cooking_time_minutes` in `recipes`, Version 4 ergänzt dort die vier nullable Nährwertspalten pro Portion. Version 5 ergänzt `recipes.dish_type` und ersetzt den früheren einzelnen Meal-Plan-Eintrag pro Datum durch Haupt- und Beilageneinträge. Version 6 ergänzt die nullable Spalte `baking_time_minutes`; eine Gesamtzeitspalte existiert bewusst nicht. Version 7 ersetzt die flache Tabelle `recipe_ingredients` vollständig durch `recipe_ingredient_groups` und `recipe_ingredient_options`. Version 8 ergänzt die optionale Zuordnung konkreter Alternativzutaten pro Planungseintrag, Version 9 Ingredient-Kategorien und Version 10 das lokale Inventar. Version 11 erweitert die Recipe- und MealPlan-CHECK-Constraints um `DESSERT` und ergänzt einen eigenen eindeutigen Positionsindex für Nachtische pro Datum. Version 12 ergänzt das unveränderliche Verbrauchsledger aus `inventory_consumptions` und `inventory_consumption_items`. Version 13 ergänzt die nullable positive `resting_time_minutes`-Spalte; eine Gesamtzeit wird weiterhin nicht gespeichert. Vorhandene Daten bleiben erhalten; bestehende Recipes und Planungseinträge aus älteren Schemas migrieren jeweils zu `MAIN`. UUIDs werden als Text, `BigDecimal`-Mengen und Nährwert-Grammwerte verlustfrei als Dezimaltext und Units über ihre Enum-Namen gespeichert. Jede neue Verbindung aktiviert SQLite-Foreign-Keys ausdrücklich.

In V7 besitzt `recipe_ingredient_groups` eine UUID, den Recipe-Fremdschlüssel, eine pro Recipe eindeutige Position und die UUID seiner verpflichtenden Standardoption. `recipe_ingredient_options` besitzt eine UUID, den Group- und Ingredient-Fremdschlüssel, Dezimalmenge, Unit sowie eine pro Gruppe eindeutige Position. Recipe-, Group- und Ingredient-Beziehungen verwenden Cascades beziehungsweise `ON DELETE RESTRICT` so, dass beim Entfernen eines Recipe seine Gruppen und Optionen verschwinden, zentrale Ingredients aber erhalten bleiben. Ein zusammengesetzter, bis zum Commit verschobener Fremdschlüssel `(group.id, group.default_option_id) -> (option.group_id, option.id)` erzwingt, dass die Standardoption tatsächlich derselben Gruppe angehört. Die Domain erzwingt zusätzlich mindestens eine Option und genau eine vorhandene Standardoption.

Die Migration V6→V7 erzeugt pro alter `recipe_ingredients`-Zeile eine neue stabile Group- und Option-UUID. Sie übernimmt Ingredient-UUID, Menge und Unit unverändert und setzt die einzige Option als Standard. Die Gruppenposition folgt deterministisch derselben bisherigen Ladeordnung nach Ingredient-Name und -UUID. Erst nachdem Gruppen und Optionen innerhalb derselben Transaktion vollständig angelegt wurden, wird die alte Tabelle entfernt; es existiert danach keine zweite persistierte Zutatenquelle.

V8 ergänzt `meal_plan_ingredient_selections` mit dem Primärschlüssel `(meal_plan_entry_id, ingredient_group_id)`. Die gespeicherte Option wird über einen zusammengesetzten Fremdschlüssel an dieselbe IngredientGroup gebunden; beim Löschen eines Planungseintrags verschwinden seine Auswahlen per Cascade. Für bestehende V7-Planungen werden keine Zeilen erzeugt: Eine fehlende Zuordnung bedeutet bewusst, dass die aktuelle Standardoption der Recipe-Gruppe verwendet wird. Beim Aktualisieren eines Recipe bleiben noch gültige Auswahlen anhand ihrer stabilen Group-/Option-UUID erhalten. Entfernte Optionen oder auf eine einzelne Option reduzierte Gruppen verlieren ihre nicht mehr gültige Zuordnung innerhalb derselben Recipe-Transaktion und fallen beim nächsten Laden automatisch auf den aktuellen Standard zurück.

Ingredients und Tastes werden über ihre eigenen Repositories verwaltet und müssen vor einem referenzierenden Recipe existieren. Das vollständige Speichern oder Aktualisieren eines Recipe läuft in einer Transaktion. Dabei werden seine Gruppen, Optionen, übrigen Beziehungs- und Schrittzeilen verständlich und atomar ersetzt; stabile Group- und Option-UUIDs aus dem Domainobjekt werden beim erneuten Einfügen beibehalten. Entfernte Gruppen und Optionen hinterlassen durch die Foreign-Key-Cascades keine verwaisten Datensätze; bei Fehlern erfolgt ein Rollback. Ein Recipe ohne Zubereitung erzeugt dabei schlicht keine Zeile in `recipe_steps`; das bestehende Schema benötigt dafür keine Migration.

`IngredientCategoryService` verwaltet den geordneten, zentralen Kategorienkatalog. Namen werden getrimmt und ohne Beachtung der Groß-/Kleinschreibung eindeutig behandelt; jede Änderung normalisiert die Positionen wieder auf eine lückenlose Reihenfolge. Die stabile UUID von `Sonstiges` kennzeichnet den geschützten Fallback, der weder umbenannt noch gelöscht werden kann. `IngredientCategoryRepository.deleteAndReassign()` verschiebt beim Löschen einer anderen Kategorie alle betroffenen Ingredients nach `Sonstiges`, entfernt anschließend die Kategorie und speichert die neue Reihenfolge in einer einzigen SQLite-Transaktion. Ingredient-, Recipe- und Inventory-Identitäten bleiben dabei unverändert; eine Schemaänderung ist nicht erforderlich.

`IngredientManagementService` kapselt das Umbenennen und Umkategorisieren zentraler Ingredients. Er validiert nichtleere, ohne Beachtung der Groß-/Kleinschreibung eindeutige Namen und speichert ein neues unveränderliches `Ingredient`-Objekt mit derselben UUID. Da Recipes und InventoryItems über diese UUID referenzieren, laden beide Bereiche anschließend automatisch Name und Kategorie in ihrem aktuellen Stand; JavaFX-Controller enthalten dafür keine Persistenz- oder Referenzlogik.

Beim Erstellen über die UI werden neue Ingredients und Tastes deshalb zuerst über ihre eigenen Repositories gespeichert und erst danach das Recipe. Diese drei Repository-Aufrufe teilen bewusst keine übergreifende Transaktion: Schlägt der abschließende Recipe-Aufruf fehl, bleiben zuvor angelegte zentrale Einträge als weiterhin verwendbare Daten bestehen. Diese für Phase 2.3 gewählte Teilpersistenz vermeidet eine vorgezogene Unit-of-Work- beziehungsweise Connection-Refaktorierung; der Fehler wird in der UI ausdrücklich angezeigt.

Die produktive Windows-Anwendung verwendet `%LOCALAPPDATA%\MealDeal\mealdeal.db` als lokalen, nicht roamingfähigen Datenbankpfad. `ApplicationDataPaths` prüft die Umgebungsvariable und legt das Anwendungsverzeichnis bei Bedarf an. Schlägt diese Startvorbereitung fehl, wird der Fehler ausdrücklich weitergegeben statt unbemerkt auf einen anderen Speicherort auszuweichen.

## Wochenplanung

`MealPlanEntry` verbindet über eine eigene UUID ein `LocalDate` mit einem bereits persistierten Recipe und einer individuellen Personenanzahl. Jeder Eintrag besitzt zusätzlich die Rolle `MAIN`, `SIDE` oder `DESSERT`, eine rollenlokale Position und optional eine Zuordnung `IngredientGroup-UUID -> IngredientOption-UUID` für konkret gewählte Alternativen. Nicht explizit ausgewählte Gruppen verwenden ihre aktuelle Standardoption; Single-Option-Gruppen benötigen keine Zuordnung. Die Rolle muss dem `DishType` des Recipe entsprechen. SQLite erzwingt mit einem partiellen Unique-Index höchstens ein `MAIN` pro Datum; `SIDE`- und `DESSERT`-Einträge sind auch ohne Hauptgericht beliebig oft erlaubt und ihre Position ist getrennt nach Rolle pro Datum eindeutig. Leere Tage werden nicht persistiert. Ein Recipe mit Planungshistorie ist durch `ON DELETE RESTRICT` vor versehentlichem Löschen geschützt.

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

`WeeklyMealPlanService` bildet darauf den Anwendungsfall für die aktuelle Woche. Ein injizierbarer `Clock` bestimmt das lokale Heute, `WeekService` liefert die sieben konkreten Datumswerte und `MealPlanRepository.findBetween()` lädt vorhandene Planungen. Für jeden Tag entsteht ein unveränderlicher `MealPlanDay` aus optionalem `MAIN`, geordneten `SIDE`- und `DESSERT`-Einträgen und Heute-Markierung. `WeeklyMealPlanDayDraft` hält die zugehörigen lokalen Änderungen JavaFX-unabhängig: Er bewahrt bestehende UUIDs bei Recipe-, Portions-, Positions- und Alternativänderungen, erzeugt UUIDs nur für neue Einträge und hält SIDE- sowie DESSERT-Positionen jeweils lückenlos. Eine geänderte Alternativauswahl gehört damit zum Dirty State. Die UI übergibt diese Snapshots als `MealPlanDraft`s gesammelt an den Service. Dieser vergleicht sie mit dem persistierten Stand und übergibt ausschließlich neue, geänderte oder entfernte Einträge als Change-Set an das Repository. Die SQLite-Implementierung speichert Entry und Alternativzuordnungen gemeinsam in derselben Transaktion: Bei einem Fehler bleibt die ganze Woche einschließlich der vorherigen Auswahl unverändert. Beim Vertauschen rollenlokaler Positionen gibt sie innerhalb der Transaktion nur die tatsächlich geänderten IDs frei und speichert sie sofort mit denselben UUIDs neu, damit der jeweilige Positionsindex nicht temporär kollidiert. Die individuelle Personenanzahl bleibt Bestandteil jedes `MealPlanEntry`.

## Einkaufslistenberechnung

`ShoppingListService` berechnet eine Einkaufsliste deterministisch aus allen aktuellen Planungs- und Recipe-Daten. Sie berücksichtigt für Heute sämtliche `MAIN`-, `SIDE`- und `DESSERT`-Einträge des Tages und für die Woche alle Einträge von Heute bis Sonntag, einschließlich reiner Beilagen- oder Nachtisch-Tage. Für jede IngredientGroup skaliert sie die konkret im `MealPlanEntry` gewählte Option beziehungsweise ohne explizite Auswahl deren aktuelle Standardoption. MealRole und rollenlokale Position beeinflussen die Aggregation nicht; jede Zutatenmenge wird ausschließlich mit der individuellen Portionszahl ihres `MealPlanEntry` skaliert. Die Liste wird nicht persistiert, damit keine doppelte oder nach Recipe-Änderungen veraltete Datenhaltung entsteht.

Optional zieht derselbe Service anschließend den über `InventoryRepository` gelesenen Bestand ab. Die Einkaufsliste öffnet diesen bestehenden Modus standardmäßig als „Mit Inventar“; „Ohne Inventar“ bleibt als unveränderte Bedarfsansicht auswählbar. „Mit Inventar“ vergleicht Ingredients ausschließlich per UUID, addiert mit `UnitConverter` kompatible Bestände und entfernt vollständig gedeckte Positionen. Das Inventar bleibt dabei strikt unverändert. `InventoryService` kapselt Laden, Kategoriegruppierung und manuelle CRUD-Operationen für die Inventaransicht. Ein Ingredient und dieselbe Unit dürfen nur einmal vorkommen; Service und SQLite-Repository weisen Duplikate zurück, während verschiedene Units bewusst getrennte Einträge bleiben. Dafür ist keine weitere Schema-Migration erforderlich.

`InventoryConsumptionService` verarbeitet ausschließlich Planungseinträge mit `date < today`. Er skaliert ihre gespeicherte Portionszahl über den bestehenden `RecipeScaler` und löst damit zugleich die pro Eintrag gewählte Alternativzutat beziehungsweise den Default auf. Der fachlich benötigte Verbrauch wird als unveränderlicher Snapshot aus Ingredient-UUID, Menge und Unit gespeichert. Änderungen an Recipe oder Planung verändern diesen historischen Stand nicht; das Ledger besitzt deshalb bewusst keinen Fremdschlüssel zum löschbaren `MealPlanEntry` oder Ingredient. Ein Unique-Constraint auf dessen ursprünglicher UUID verhindert eine zweite Verarbeitung zusätzlich auf Datenbankebene.

Ledger und veränderte `InventoryItem`-Mengen werden über `InventoryConsumptionRepository.saveWithInventoryUpdates()` in derselben SQLite-Transaktion geschrieben. Nicht ausreichender Bestand wird nur bis null reduziert, während der vollständige Bedarf im Ledger steht und der Eintrag damit endgültig verarbeitet ist. Kompatible Mengen werden ausschließlich über `UnitConverter` verrechnet und deterministisch auf die nach Kategorie, Ingredient-Name und Item-UUID gelesenen Bestände verteilt; jedes Item bleibt in seiner gespeicherten Unit. Der produktive `ApplicationContext` führt den idempotenten Abgleich beim Start aus. Inventaransicht und inventarbewusste Einkaufsliste wiederholen ihn vor ihrem jeweiligen Bestandslesen, damit auch ein Datumswechsel in einer laufenden Anwendung ohne Polling berücksichtigt wird.

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

`MealDealApplication` öffnet genau eine primäre Stage. `MainController` hält die Seitenleiste dauerhaft sichtbar, während `ViewNavigator` nur den Inhaltsbereich austauscht und den aktiven Navigationseintrag markiert. Dadurch entstehen beim Wechsel zwischen Start, Gerichten, Suche, Wochenplan, Einkauf, Zutaten und Inventar keine zusätzlichen Fenster.

Die zentrale Zutaten- und Kategorieverwaltung besitzt mit `IngredientsController` eine eigene Ansicht. Sie verwendet weiterhin ausschließlich `IngredientManagementService` und `IngredientCategoryService`; Umbenennen, Umkategorisieren, Sortieren und das transaktionale Löschen einer Kategorie bleiben damit außerhalb der UI. Zentrale Zutaten erscheinen alphabetisch in zunächst geschlossenen Kategorie-Unterbereichen, wobei ein Kategorienwechsel nach dem Speichern durch das erneute Laden automatisch die sichtbare Gruppe wechselt. `InventoryController` verwaltet dagegen nur noch Bestandsmenge und Unit zentraler Ingredients. Beide Controller erhalten ihre Services weiterhin manuell über den `ApplicationContext`.

Jede Hauptansicht verwendet einen vertikal scrollbar ausgeführten Inhaltsbereich. Darin zentriert ein `StackPane` einen Seitencontainer, der bei kleineren Fenstern automatisch schrumpft. Das Layout richtet sich ausschließlich nach dem verfügbaren Scene- und Pane-Bereich; Monitorauflösung, DPI-Prozentwerte oder feste Bildschirmkoordinaten fließen nicht in die Positionierung ein. So bleibt der Inhalt beim Maximieren, Verkleinern und Verschieben zwischen Monitoren stabil, ohne auf den primären Monitor zurückzuspringen.

Die Anwendung überlässt die Per-Monitor-DPI-Skalierung vollständig dem JavaFX-Windows-Toolkit. Weder `MealDealApplication` noch Controller oder FXML setzen Node-/Scene-Transforms, `scaleX`/`scaleY`, `renderScale`, `outputScale` oder Listener für Bildschirm- und Fensterkoordinaten. JavaFX 26 verarbeitet unter Windows den nativen DPI-Wechsel beim Verschieben eines Fensters und aktualisiert damit `outputScale` und den standardmäßig daran gekoppelten `renderScale`.

`MainController` kennzeichnet den Root nur anhand der verfügbaren logischen Scene-Breite als kompakt (unter 1100 px), normal, breit (ab 1440 px) oder extrabreit (ab 2100 px). Das zentrale Stylesheet reduziert im Kompaktzustand ausschließlich die horizontal platzkritischen Abstände des Wochenplans; seine Tagesköpfe und Eintragszeilen teilen Inhalt und geschützte Aktionen außerdem über flexible beziehungsweise umbrechende Layout-Container auf. Für breite Viewports erweitert das Stylesheet die regulären Seitencontainer von 1080 px beziehungsweise die Detailansicht von 1240 px auf bis zu 1320/1440 px oder 1640/1760 px. Gleichzeitig wachsen Seitenleiste, Abstände, Titel und Controls moderat. Beschreibende Texte behalten eigene Maximalbreiten, damit breite Karten keine unnötig langen Zeilen erzeugen.

Auf der Startseite führt eine einzige zentrale Aktion „Gericht finden“ in die kombinierte Zutaten- und Geschmackssuche. Tages- und Wochenplanung stehen entsprechend ihrer fachlichen Gewichtung als breite Karten direkt untereinander. Diese vertikale Reihenfolge bleibt auch bei schmaleren Fenstern eindeutig und verhindert ein unnötiges horizontales Auseinanderziehen.

`HomeController` lädt die sichtbaren Planungszusammenfassungen ausschließlich über `WeeklyMealPlanService.loadCurrentWeek()`. Für Heute rendert er das von `MealPlanDay` gelieferte optionale Hauptgericht sowie alle geordneten Beilagen und Nachtische mit ihrer jeweiligen Portionszahl in getrennten Bereichen; ein Beilagen- oder Nachtisch-only-Tag ist damit kein leerer Tag. Die kompakte Wochenübersicht verwendet dasselbe Modell für alle sieben Tage. `HomeMealPlanViewModel` bleibt JavaFX-unabhängig und überführt nur dieses bereits fachlich validierte Tagesmodell in Präsentationsdaten; der Controller enthält weder Repository-Zugriffe noch MealRole-Fachlogik.

`styles.css` bleibt der einzige von FXML geladene CSS-Einstiegspunkt. Er bindet über relative Classpath-Imports in fester Reihenfolge `tokens`, `base`, `controls`, `components` sowie die Feature-Module `recipes`, `search`, `planning` und `stock` ein. `responsive.css` wird bewusst zuletzt geladen, damit die vorhandenen Wide- und Extra-Wide-Überschreibungen ihre Kaskadenwirkung behalten. JavaFX-Control- und Skin-Unterknoten liegen gemeinsam in `controls.css`; fachbezogene Regeln bleiben in ihrem jeweiligen Feature-Modul.

Das gemeinsame Stylesheet definiert die Oberflächenfarben als vererbte UI-Tokens. Die helle Palette verwendet `#6f1d35` als ruhigen Bordeaux-Hauptakzent; der Dark Mode nutzt eine kontraststärkere Ausprägung derselben Farbfamilie. Fehler- und Warnfarben bleiben eigene semantische Tokens. `MainController` schaltet am permanenten Root-Layout ausschließlich die CSS-Klasse `theme-dark`, sodass alle geladenen Views ohne Neustart folgen. `ThemeService` hält diese reine UI-Einstellung unabhängig von Repositories und Fachlogik und speichert sie unter `%LOCALAPPDATA%\MealDeal\theme.properties` neben der SQLite-Datei. Beim nächsten Start wird die Auswahl wiederhergestellt.

Container verwenden drei gemeinsame, tokenbasierte CSS-Grundtypen: `content-card` für normale Hauptflächen, `expandable-card` für einheitliche Bordeaux-Header samt Inhalt und `sub-card` für kompakte innere Bereiche. Fachspezifische Klassen ergänzen nur noch das jeweilige Layout oder Verhalten. Breite Viewports vergrößern dabei moderat den horizontalen Innenabstand der Hauptkarten, ohne deren vertikale Abstände unnötig aufzublähen.

`ApplicationContext` übernimmt die bewusste manuelle Zusammensetzung der UI. Er erstellt für den konfigurierten Datenbankpfad die SQLite-Implementierungen von `RecipeRepository`, `IngredientRepository`, `TasteRepository` und `MealPlanRepository` auf einer gemeinsamen `SqliteDatabase` und injiziert die benötigten Schnittstellen und Services in die Controller; ein Dependency-Injection-Framework ist dafür nicht erforderlich.

Der `RecipesController` lädt die gespeicherten Rezepte beim Öffnen der Ansicht neu. Er sortiert sie deterministisch nach Name und bei Namensgleichheit nach UUID und erzeugt daraus kompakte, auswählbare UI-Einträge. Jedes zeigt seinen `DishType` als deutsches Label (`Hauptgericht`, `Beilage` oder `Nachtisch`), ohne die Sortierung oder Navigation zu verändern. Empty State und Ladefehler sind eigene sichtbare Zustände.

Ein ausgewähltes Recipe wird als vollständiges Domain-Objekt an die Detailansicht im bestehenden Inhaltsbereich übergeben. Der `RecipeDetailController` erhält den `RecipeScaler` über den `ApplicationContext` und bezieht bei jeder Änderung der Personenanzahl ausschließlich von ihm neue Zutatenmengen. Die Anzeige formatiert `BigDecimal` ohne binäre Fließkommazahlen mit deutschem Dezimaltrennzeichen und verwendet deutsche Küchenbezeichnungen für Units. Sein `DishType` erscheint als deutsches Metadaten-Label. Vorhandene Zeitwerte erscheinen als deutsche Kurzform (`25 Min.`, `1 Std. 20 Min.`); bei vollständig fehlenden Werten erscheint kein Zeitbereich. Ein Nährwertbereich erscheint ebenso nur bei mindestens einem Wert und zeigt die Angaben unverändert pro Portion. Das gespeicherte Recipe bleibt unverändert.

Das Löschen eines Recipe wird in der Detailansicht ausdrücklich bestätigt und anschließend ausschließlich über `RecipeRepository.deleteById()` ausgeführt. Die abhängigen Recipe-Beziehungszeilen werden durch die bestehenden Cascades entfernt, zentrale Ingredients und Tastes jedoch nicht. Verhindert eine vorhandene Wochenplanung das Löschen per `ON DELETE RESTRICT`, übersetzt die SQLite-Implementierung diesen konkreten Constraint in eine `RecipeDeletionRestrictedException`; die UI bleibt in der Detailansicht und erklärt den Konflikt, ohne Planungsdaten automatisch zu verändern.

Die Zutaten-Suche lädt zentrale Ingredients und Recipes über ihre Repository-Schnittstellen. `IngredientSearchModel` hält ausschließlich den JavaFX-unabhängigen Auswahlzustand von einer bis zehn Zutaten. `IngredientSelectionView` stellt den über `IngredientCategoryGrouping` sortierten und gefilterten Kategorienkatalog sowie die ausgewählten Ingredient-Chips dar, besitzt aber weder Repository- noch Search-Service-Abhängigkeiten. Reine Zutatenabfragen und kombinierte Abfragen reichen die ausgewählten Ingredient-Identitäten unverändert an `RecipeSearchService.searchByIngredients()` weiter. Ranking, Trefferzahl, fehlende Zutaten und Qualitätsstufe stammen ausschließlich aus dessen `IngredientSearchResult`; die UI navigiert vom Ergebnis zum vollständigen Recipe-Detail.

Die Geschmackssuche folgt derselben Trennung: `TasteSearchModel` lädt zentrale Tastes und verwaltet nur deren Auswahlzustand; `TasteSelectionView` kapselt ausschließlich Filterung und Darstellung der verfügbaren Optionen und Auswahl-Chips. `AND`, `OR` oder `RANKING` werden unverändert an `RecipeSearchService.searchByTastes()` delegiert. Zutaten- und Geschmacksauswahl bleiben getrennte UI-Zustände, können aber über eine gemeinsame Suchaktion angewendet werden. `IngredientSearchController` koordiniert Referenzdaten, Auswahlmodelle, Suchmodus, gemeinsame Suche, Reset, Meldungen und Navigation. `RecipeSearchResultsView` übernimmt nur die bestehenden Ergebniszustände und Karten aus den gelieferten `CombinedSearchResult`-Daten; Öffnen wird als Callback zurück an den Controller gegeben.

`CombinedRecipeSearchService` führt beide Suchen ohne SQL und ohne neuen gewichteten Score zusammen. Ist nur ein Filter aktiv, gibt er die bestehende Reihenfolge des jeweiligen `RecipeSearchService`-Ergebnisses unverändert weiter. Sind beide aktiv, bildet er die Schnittmenge: Das Zutaten-Ranking bleibt primär, bei gleichen Zutaten-Treffern dient im Modus `RANKING` die bestehende Taste-Trefferzahl als Tie-Breaker; Name und UUID schließen die deterministische Sortierung ab. `AND` und `OR` werden bereits vor der Schnittmenge ausschließlich durch `RecipeSearchService` ausgewertet. Die UI zeigt beide Teilbewertungen getrennt und kann beide Auswahlmodelle gemeinsam zurücksetzen.

`CreateRecipeController` koordiniert die Initialisierung des gemeinsamen Create-/Edit-Formulars, das Laden der Referenzdaten, den Formular-Snapshot, Speichern, Navigation und Meldungen. Die dynamischen JavaFX-Bereiche kapseln kleine, spezialisierte UI-Bausteine: `RecipeIngredientEditor` verwaltet Ingredient-Gruppen und Alternativen, `RecipeTasteEditor` die auswählbaren Geschmacksrichtungen und `RecipeStepEditor` die geordneten Zubereitungszeilen. Diese Bausteine greifen weder auf Repositorys zu noch treffen sie fachliche Speicherentscheidungen; der Controller führt ihre Eingaben im bestehenden JavaFX-unabhängigen `RecipeFormInput` zusammen. Derselbe FXML- und Controller-Stack dient weiterhin dem Erstellen und Bearbeiten: Im Bearbeitungsmodus werden alle vorhandenen Werte einschließlich des `DishType` vorausgefüllt, Abbrechen führt zum unveränderten Detailobjekt zurück und Speichern zeigt das aktualisierte Detailobjekt. Zutaten werden als geordnete Formulargruppen mit jeweils einer oder mehreren Optionen dargestellt. `IngredientGroupFormState` hält ihre stabilen Group-/Option-UUIDs und die genau eine Standardauswahl JavaFX-unabhängig; beim Entfernen des Standards wird deterministisch die erste verbleibende Option gewählt. `RecipeFormService` konstruiert daraus die Domainobjekte, erhält fortbestehende UUIDs beim Editieren und löst jede Option über denselben zentralen Ingredient-Katalog auf. Die Detailansicht zeigt Alternativen geordnet mit „oder“ und dezenter Standardkennzeichnung. `IngredientCategoryGrouping` gruppiert und sortiert den zentralen Katalog als gemeinsame, JavaFX-unabhängige Präsentationslogik für Verwaltung und Suche. Beide Ansichten zeigen zunächst geschlossene `ingredient-category-pane`-Unterbereiche und blenden leere Kategorien aus; ein aktiver Suchtext öffnet die verbleibenden Treffergruppen automatisch, ohne den Auswahlzustand zu verändern. Die Suche bewertet die Gruppen über alle Optionen; Planung und Einkauf lösen dagegen die konkrete Auswahl des jeweiligen `MealPlanEntry` auf und verwenden nur ohne explizite Auswahl die Standardoption.

Neue Formulare wählen standardmäßig `Hauptgericht`; der `RecipeFormService` weist eine fehlende Typauswahl zurück. Er verarbeitet einen JavaFX-unabhängigen `RecipeFormInput`, validiert ihn vollständig, löst bestehende zentrale Daten namensbasiert auf und orchestriert die Repository-Aufrufe. Beim Aktualisieren konstruiert er das neue unveränderliche Recipe mit der bestehenden UUID; das Repository ersetzt dessen gespeicherte Daten atomar. Mengen akzeptieren positive Ganz- und Dezimalzahlen mit Komma oder Punkt als Dezimaltrennzeichen und werden ohne `double` oder `float` direkt als `BigDecimal` verarbeitet. Vorbereitungs-, Koch-, Back- und Ruhezeit akzeptieren nur positive ganze Minutenwerte; die Gesamtzeit wird ausschließlich im Domainmodell als Summe der gesetzten Einzelzeiten abgeleitet. Reine Mengenanzeigen runden zentral mit `HALF_UP` auf höchstens zwei Nachkommastellen und entfernen Nullen am Ende; editierbare Felder behalten den exakten Dezimalwert, damit ein unverändertes Speichern keine Domainwerte rundet. Optionale Nährwerte akzeptieren nichtnegative Werte und gelten unverändert pro Portion. Zubereitungsschritte sind optional; nichtleere Einträge werden aus ihrer sichtbaren, lückenlos nummerierten Reihenfolge erzeugt und können später ergänzt werden. SQL, Portionierung und Suchlogik bleiben außerhalb der Controller.

`WeekPlanController` koordiniert die sieben vom `WeeklyMealPlanService` gelieferten Tage, ihre `WeeklyMealPlanDayDraft`s und den gemeinsamen Save-/Refresh-Ablauf. Der dynamische JavaFX-Aufbau ist in drei kleine UI-Builder getrennt: `WeekPlanDayCardFactory` erzeugt Tageskarte und Header, `MealRoleSectionFactory` die getrennten MAIN-/SIDE-/DESSERT-Bereiche und `MealPlanEntryRowFactory` die kompakten Ansichts- und Bearbeitungszeilen einschließlich der vorhandenen durchsuchbaren Auswahlfelder. Diese Builder besitzen weder Repositories noch eigene Draft-Kopien; alle Änderungen fließen über kleine Callbacks zurück an den Controller und von dort an den bestehenden Draft.

Die Auswahlfelder erhalten weiterhin ausschließlich Recipes ihres passenden `DishType`. Neue Beilagen und Nachtische übernehmen die aktuelle Hauptgericht-Portionszahl oder ohne Hauptgericht den Standardwert; danach bleiben alle Portionszahlen unabhängig. Bei Recipes mit Multi-Option-Gruppen erscheint zusätzlich eine Auswahl der konkreten Zutatenoption; Single-Option-Gruppen erzeugen kein zusätzliches Steuerelement. Pfeile ändern die jeweilige lokale SIDE- oder DESSERT-Reihenfolge ohne Drag & Drop. Der zentrale Button „Änderungen speichern“ übergibt alle Tagesentwürfe gemeinsam an den Service; danach lädt die Ansicht den persistierten Stand neu. Geplante Gerichte öffnen über den bestehenden `ViewNavigator` dieselbe Recipe-Detailansicht wie die Gerichte- und Suchansichten. SQL, Transaktionen und Kalenderberechnung bleiben außerhalb der UI-Builder und des Controllers.

`ShoppingListController` erhält den `ShoppingListService` manuell über den `ApplicationContext`. Seine beiden Umschaltzustände delegieren ausschließlich an `buildForToday()` beziehungsweise `buildForCurrentWeek()` und zeigen das zurückgegebene, bereits aggregierte `ShoppingList`-Ergebnis an. Er speichert weder Einkaufslisten noch Mengen und führt keine eigene Skalierungs-, Datums- oder Aggregationslogik aus. Für die Anzeige verwendet er dieselbe deutsche `BigDecimal`- und Unit-Formatierung wie die Recipe-Detailansicht; leere und fehlerhafte Ladezustände bleiben sichtbar getrennt.
