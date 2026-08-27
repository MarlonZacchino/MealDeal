# MealDeal

MealDeal ist eine lokale Desktop-Anwendung für den privaten Gebrauch. Sie soll künftig Gerichte, Rezepte, Wochenpläne und Einkaufslisten verwalten.

## Status

Das Projekt befindet sich in einer frühen Entwicklungsphase. Technisches Fundament, Domain-Modell, SQLite-Persistenz, Portions- und Einheitenberechnungen, Recipe-Suche, Wochenplanung und fachliche Einkaufslistenberechnung sind eingerichtet. Das JavaFX-Anwendungsgerüst bietet bereits eine feste Seitenleiste und strukturelle Ansichten für Start, Gerichte, Suche, Wochenplan und Einkauf. Die fachlichen Funktionen dieser Ansichten werden in späteren Phasen angebunden.

## Technologie-Stack

- Java 25 LTS
- JavaFX 25
- Maven
- SQLite mit direktem JDBC-Zugriff
- Repository Pattern für die spätere Persistenz
- JUnit 5
- Windows als primäre Zielplattform

## Voraussetzungen unter Windows

- JDK 25; `JAVA_HOME` verweist auf diese Installation
- Apache Maven; `mvn` ist über `PATH` erreichbar

Die Installation lässt sich in PowerShell prüfen:

```powershell
java -version
mvn -version
```

Beide Ausgaben müssen Java 25 anzeigen.

## Bauen und testen

Im Projektverzeichnis:

```powershell
mvn clean test
```

## Anwendung starten

```powershell
mvn javafx:run
```

Der Start öffnet das Hauptfenster „MealDeal“. Über die linke Seitenleiste lassen sich alle derzeit angelegten Bereiche im selben Fenster aufrufen.

## Architekturprinzipien

Das Projekt trennt vier Verantwortungsbereiche: Domänenmodell, Services, Persistenz und JavaFX-Oberfläche. Geschäftslogik bleibt unabhängig von JavaFX und wird automatisiert testbar entwickelt. SQL-Zugriffe werden später ausschließlich in der Persistenzschicht über JDBC und das Repository Pattern umgesetzt.

Weitere Grundlagen stehen in [docs/architecture.md](docs/architecture.md). Dauerhafte Entwicklungsregeln sind in [AGENTS.md](AGENTS.md) festgehalten.
