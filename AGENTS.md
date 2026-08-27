# MealDeal – dauerhafte Projektregeln

## Technologie

- Java 25 LTS
- JavaFX 25
- Maven
- SQLite mit direktem JDBC-Zugriff
- Repository Pattern für Persistenz
- JUnit für automatisierte Tests
- Windows als primäre Zielplattform der Version 1

Ohne vorherige Abstimmung werden keine ORMs, Dependency-Injection-Frameworks oder anderen großen Frameworks eingeführt. Insbesondere werden Hibernate, Spring und Jakarta Persistence/JPA nicht verwendet.

## Architektur

- Geschäftslogik bleibt unabhängig von JavaFX.
- UI, Domänenmodell, Services und Persistenz bleiben klar getrennt.
- JavaFX-Controller enthalten weder direkte SQL-Logik noch Geschäftslogik.
- JavaFX-Views dienen ausschließlich der Darstellung.
- Persistenz erfolgt später über Repository-Schnittstellen und JDBC-basierte Implementierungen.
- Globale Zustände werden nur verwendet, wenn ein zwingender Grund dokumentiert ist.
- Klassen und Methoden bleiben klein und haben klar abgegrenzte Verantwortlichkeiten.
- Unnötige Frameworks, Abstraktionen und Patterns werden vermieden.

## Änderungen und Entscheidungen

Kleine Implementierungsdetails darf Codex selbstständig entscheiden. Wenn mehrere sinnvolle Lösungen existieren und die Entscheidung wesentliche Auswirkungen auf Architektur, Bedienung, Datenmodell, Persistenz, Erweiterbarkeit, Sicherheit oder Tests hat, muss Codex:

1. die Alternativen und ihre Auswirkungen erläutern,
2. die Implementierung an dieser Stelle stoppen und
3. die Entscheidung mit dem Projektverantwortlichen abstimmen.

Bestehende Architekturentscheidungen oder Dateien werden nicht ohne nachvollziehbaren Grund überschrieben.

## Tests

- Fachlogik wird automatisiert getestet.
- Neue fachliche Logik erhält grundsätzlich passende Tests.
- Tests sollen reale Fehler erkennen und nicht nur formale Testabdeckung erzeugen.
- Vor Abschluss einer Phase werden alle vorhandenen Tests ausgeführt.

## Dokumentation und Verständlichkeit

- Code muss für einen Java-Lernenden nachvollziehbar sein.
- Namen sind sprechend, Konstruktionen bewusst einfach und Methoden überschaubar.
- Wichtige öffentliche APIs erhalten JavaDoc.
- Kommentare erklären Entscheidungen und Gründe; sie wiederholen nicht lediglich den Code.

## Git

- Änderungen bleiben logisch zusammengehörig und klein.
- Unabhängige Änderungen werden nicht in einem Commit vermischt.
- Vor Abschluss werden `git diff` und `git status` geprüft.
- Build-Artefakte und IDE-spezifische temporäre Dateien werden nicht versioniert.
