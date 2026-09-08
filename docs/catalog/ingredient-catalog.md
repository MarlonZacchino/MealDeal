# Standard Ingredient Catalog V1

## Zweck und Abgrenzung

Der Standard Ingredient Catalog liefert offline verfügbare, sprachunabhängige
Begriffe für Normalisierung, spätere Imports und gemeinsame Recommendation-Semantik.
Er ist Anwendungscode und kein vom Nutzer bearbeitbarer Datenbestand.

Ein `CatalogIngredient` ist deshalb ausdrücklich kein `Ingredient`:

```text
CatalogIngredient ingredient.tomato
              ↓ optionale semantische Referenz
lokales Ingredient (eigene UUID, eigener Name, eigene Kategorie)
```

Lokale UUID, Name und Kategorie bleiben unabhängig. Ein Catalog-Update überschreibt
keine lokalen Daten und ein umbenanntes lokales Ingredient verändert den Katalog nicht.

## Repräsentation und Identität

`StandardIngredientCatalog` enthält V1 als immutable Java-Definition. Diese Form ist ohne
Parser oder neue Dependency deterministisch, direkt testbar und gemeinsam mit der Anwendung
versioniert. V1 enthält 78 häufige Zutaten aus Gemüse, Obst, Fleisch, Fisch und
Meeresfrüchten, Milchprodukten, Eiern, Getreide/Reis/Nudeln, Hülsenfrüchten,
Kräutern/Gewürzen, Backzutaten, Ölen/Essig/Saucen, Nüssen/Samen und Sonstigem.

IDs folgen `ingredient.<stable_key>`, beispielsweise `ingredient.tomato`. Der deutsche
Anzeigename ist nicht Teil der Identität und kann in einer späteren Catalog-Version durch
lokalisierte Anzeigenamen ergänzt werden. Aliases enthalten nur explizit kuratierte,
fachlich eindeutige Varianten. Typische Units verweisen direkt auf die bestehende `Unit`-
Domain; sie sind Hinweise und führen weder neue Units noch neue Umrechnungen ein.

`CatalogIngredientCategory` stellt die stabile Standardkategorie eines Catalog-Eintrags dar.
Sie ist von den in SQLite gespeicherten und durch Nutzer veränderbaren
`IngredientCategory`-Objekten getrennt. Eine spätere Übernahme kann die Standardkategorie
als Vorschlag nutzen, kontrolliert die lokale Kategorie aber nicht.

## Normalisierung und Matching

`CatalogTextNormalizer` führt ausschließlich konservative Gleichheitsnormalisierung aus:

- Unicode NFKC,
- Kleinschreibung mit `Locale.ROOT`,
- Trim und Zusammenfassen von Unicode-Whitespace,
- `ä/ae`, `ö/oe`, `ü/ue` sowie `ß/ss` als gemeinsame Schreibvarianten.

Es gibt kein Stemming, keine automatische Singular-/Pluralregel, keine Teilstringgleichheit
und kein Fuzzy Matching. Daher bleiben beispielsweise Tomate/Tomatenmark,
Milch/Kokosmilch, Butter/Erdnussbutter und Chili/Chilipulver verschieden.

`CatalogMatcher` prüft strikt in dieser Reihenfolge:

1. Catalog ID,
2. exakter Anzeigename,
3. exakter Alias,
4. normalisierter Anzeigename,
5. normalisierter Alias,
6. unbekannt.

Mehrere Treffer derselben Prioritätsstufe werden als `AMBIGUOUS` geliefert und nie zu einer
Identität hochgestuft. `LocalCatalogResolver` verwendet dieselbe konservative Staffelung,
um zu einem ausgewählten Standardbegriff ein vorhandenes lokales Ingredient zu finden.
Mehrere lokale Kandidaten bleiben ebenfalls explizit mehrdeutig; es findet kein Auto-Merge
statt.

## Persistenz und spätere Imports

Schema V15 ergänzt `ingredients.catalog_id` nullable und ohne Foreign Key auf den statischen
Katalog. Bestehende Rows werden nicht automatisch zugeordnet und behalten UUID, Name und
Kategorie unverändert. User-defined Ingredients benötigen keinen Link.

Ein späterer Community- oder Content-Pack-Import kann eine Catalog ID auflösen, über
`LocalCatalogResolver` einen eindeutigen lokalen Kandidaten wiederverwenden oder nach einer
bewussten Produktentscheidung ein neues lokales Ingredient mit eigener UUID anlegen. R1
implementiert weder Imports noch Catalog-Browser oder automatische Synchronisation.
