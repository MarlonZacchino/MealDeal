# Standard Taste Catalog V1

## Zweck und Identität

Der Standard Taste Catalog definiert einen kleinen, offline verfügbaren Wortschatz für
spätere Imports und sprachunabhängige Recommendation-Semantik. `CatalogTaste` und das lokale
`Taste` bleiben getrennte Typen: Der Katalog verwendet stabile IDs, lokale Tastes behalten
ihre eigenen UUIDs und editierbaren Namen.

V1 enthält zehn Begriffe:

- `taste.savory` – Herzhaft
- `taste.sweet` – Süß
- `taste.salty` – Salzig
- `taste.sour` – Sauer
- `taste.spicy` – Scharf
- `taste.creamy` – Cremig
- `taste.fruity` – Fruchtig
- `taste.smoky` – Rauchig
- `taste.fresh` – Frisch
- `taste.umami` – Umami

Wenige eindeutige Aliases wie „Pikant“ sind explizit kuratiert. Anzeigenamen
und Aliases sind keine Identität; spätere Lokalisierung ändert die `taste.*`-ID nicht.

## Normalisierung und Matching

Taste-Begriffe verwenden denselben `CatalogTextNormalizer` und dieselbe strikte
`CatalogMatcher`-Priorität wie Ingredients: ID, exakter Anzeigename, exakter Alias,
normalisierter Anzeigename, normalisierter Alias, sonst kein sicherer Match. Damit kann
beispielsweise `Suess` sicher `taste.sweet` auflösen, ohne ähnliche oder nur teilweise
übereinstimmende Begriffe automatisch gleichzusetzen. Mehrdeutigkeit bleibt explizit und
führt nie zu einer automatischen Identitätsentscheidung.

## Lokale Daten und Persistenz

Schema V15 ergänzt den nullable Link `tastes.catalog_id`. Bestehende und neu erstellte
User-defined Tastes bleiben ohne Catalog ID vollständig gültig. Der Link bedeutet nur, dass
ein lokaler Taste semantisch auf einem Standardbegriff basiert; er erlaubt dem Katalog weder
Umbenennung noch Überschreiben lokaler Daten.

R0 verwendet weiterhin lokale Taste-UUIDs. R1 ändert weder Taste-Scoring noch Household-
Aggregation oder Explainability. Ein späterer Import kann den Catalog-Link zur sicheren
Auflösung nutzen; UI, Community und Content Packs bleiben Folgephasen.
