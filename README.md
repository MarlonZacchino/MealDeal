# MealDeal

MealDeal ist eine lokale Desktop-Anwendung für den privaten Gebrauch. Sie soll künftig Gerichte, Rezepte, Wochenpläne und Einkaufslisten verwalten.

## Status

Das Projekt befindet sich in einer frühen Entwicklungsphase. Technisches Fundament, Domain-Modell, SQLite-Persistenz, Portions- und Einheitenberechnungen, Recipe-Suche, Wochenplanung und fachliche Einkaufslistenberechnung sind eingerichtet. Das JavaFX-Anwendungsgerüst bietet eine feste Seitenleiste und Ansichten für Start, Gerichte, Suche, Wochenplan und Einkauf. Die Ansicht „Gerichte“ lädt bereits gespeicherte Rezepte aus SQLite; Erstellen, Bearbeiten und Löschen folgen in späteren Phasen.

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

Unter Windows speichert MealDeal seine lokale SQLite-Datenbank unter `%LOCALAPPDATA%\MealDeal\mealdeal.db`. Das Anwendungsverzeichnis wird beim Start bei Bedarf angelegt.

## Architekturprinzipien

Das Projekt trennt vier Verantwortungsbereiche: Domänenmodell, Services, Persistenz und JavaFX-Oberfläche. Geschäftslogik bleibt unabhängig von JavaFX und wird automatisiert testbar entwickelt. SQL-Zugriffe erfolgen ausschließlich in der Persistenzschicht über JDBC und das Repository Pattern.

Weitere Grundlagen stehen in [docs/architecture.md](docs/architecture.md). Dauerhafte Entwicklungsregeln sind in [AGENTS.md](AGENTS.md) festgehalten.
