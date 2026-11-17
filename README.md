# OIDA-SQL

MySQL, aber im Dialekt. Und am Schluss sagst **OIDA**.

```sql
ERSTÖN TABÖN leit (id ZOI ZÖHT SÖWA MIT HAUPTSCHLISSL, name KETTN(50) MUASS SEI) OIDA;
EINEHAUN IN leit (name) WERTE ('Franz'), ('Resi') OIDA;
GIB MA WIAVÜ(*) OIS leit VON leit WO name SO WIA 'F%' OIDA;
GUSCH DI TABÖN leit OIDA;
```

Das ist ganz normales MySQL, nur heißen die Wörter anders. Ein kleiner JDBC-Treiber setzt sich vor
den MySQL-Treiber, prüft jeden Befehl, übersetzt ihn und gibt ihn an MySQL weiter. Ein Plugin sorgt
dafür, dass DataGrip mit dem Dialekt zurechtkommt.

## Installieren

Anleitung mit allen Schritten: **https://lelewithheart.github.io/oida-sql/**

Kurz: In DataGrip unter *Settings → Plugins → Zahnrad → Manage Plugin Repositories…* diese Adresse
eintragen und dann im Reiter *Marketplace* das Plugin **OIDA-SQL** installieren:

```
https://lelewithheart.github.io/oida-sql/updatePlugins.xml
```

Das Plugin bringt den Treiber mit und legt ihn nach `Benutzerordner/.oida-sql/oida-sql.jar`.
Updates meldet DataGrip danach von selbst.

- [Wörterbuch (PDF)](https://lelewithheart.github.io/oida-sql/Woerterbuch.pdf)
- [Anleitung (PDF)](https://lelewithheart.github.io/oida-sql/Anleitung.pdf)
- Beispiele zum Abschauen: [`beispiele.sql`](beispiele.sql)

## Was drin ist

| Ordner / Datei | Wofür |
|---|---|
| `woerterbuch.txt` | Links das MySQL-Wort, rechts das Dialekt-Wort. Leer = bleibt englisch. |
| `einstellungen.txt` | Endwort, Fehlermeldungen (mehrere Varianten, zufällig gewählt), ob Englisch noch erlaubt ist. |
| `ausnahmen.txt` | Befehle, die DataGrip selbst schickt und die kein `OIDA` brauchen. |
| `src/at/oida/` | Der Treiber: `Uebersetzer` (OIDA-SQL → MySQL), `Rueckwaerts` (MySQL → OIDA-SQL), `OidaDriver`. |
| `datagrip-plugin/` | Das Plugin: Fehler ausblenden, Wörter einfärben, Befehl unter dem Cursor ausführen, Treiber bereitlegen. |
| `doku/` | Quelle für die beiden PDFs (`erzeugen.py` druckt sie über Edge). |
| `docs/` | Die Webseite samt `updatePlugins.xml`, von der DataGrip die Updates holt. |
| `test/` | Tests für Übersetzer, Treiber und Plugin-Logik. |

## Wörterbuch ändern

1. `woerterbuch.txt` bearbeiten (die Regeln stehen oben in der Datei, als UTF-8 speichern).
2. `bauen.bat` doppelklicken. Das Wörterbuch wird dabei geprüft: Steht ein Dialekt-Wort bei zwei
   MySQL-Wörtern, sagt das Skript die Zeile.
3. Mit `ausprobieren.bat` kannst du Befehle eintippen und sehen, was daraus wird. Ohne Datenbank.

Gebaut wird mit dem Java, das DataGrip mitbringt. Extra installieren muss man nichts.

## Neue Version veröffentlichen

```
veroeffentlichen.bat
```

Das Skript fragt nach der Versionsnummer, baut alles, legt Plugin und PDFs in `docs/`, macht einen
Commit mit Tag, schiebt ihn zu GitHub und legt ein Release an. Ein bis zwei Minuten später zeigt
DataGrip allen das Update an. Die Nummer muss größer sein als die letzte (steht in `version.txt`).

Dafür braucht es `git`, die GitHub-CLI `gh` (angemeldet), DataGrip und für die PDFs Python und Edge.

## Ein MySQL-Skript in den Dialekt umschreiben

```
java -jar oida-sql.jar --umschreiben eingabe.sql ausgabe.sql
```

Jeder umgeschriebene Befehl wird zur Kontrolle wieder zurück übersetzt und mit dem Original verglichen.

## Gut zu wissen

- **Namen, die wie Dialekt-Wörter heißen:** Eine Spalte, die z.B. `von` heißt, in Backticks schreiben
  (`` `von` ``), sonst wird sie mitübersetzt. `tabelle.von` geht ohne.
- **Ausnahmen sind Schlupflöcher:** DataGrip schickt im Hintergrund selbst SQL (Tabellen laden, Zellen
  bearbeiten). Was auf ein Muster in `ausnahmen.txt` passt, geht ohne `OIDA` durch, auch wenn man es
  von Hand tippt.
- **Wenn DataGrip bei einer eigenen Aktion „Heast, wo is des OIDA?" meldet:** Dann fehlt eine Ausnahme.
  In der Datenquelle unter *Advanced* die Eigenschaft `oidaLog` mit einem Dateipfad anlegen, Aktion
  wiederholen, in der Datei nachschauen, welcher Befehl blockiert wurde, und ein Muster ergänzen.
- **Endwort ändern:** Das Plugin sucht fest nach `OIDA`. Wer das Endwort in `einstellungen.txt`
  ändert, muss es auch in `datagrip-plugin/src/at/oida/datagrip/OidaText.java` ändern.

## Tests

`JAVA` ist `java.exe` aus DataGrips `jbr\bin`, vorher `bauen.bat` laufen lassen:

```
JAVA -cp build\classes test\UebersetzerTest.java
JAVA -cp build\plugin test\OidaTextTest.java
JAVA -cp oida-sql-datagrip-plugin.jar test\TreiberTest.java
```

`DatenbankTest`, `BeispieleTest` und `SkriptTest` brauchen eine MySQL-Datenbank und den MySQL-Treiber
auf dem Klassenpfad; die Zugangsdaten kommen aus den Umgebungsvariablen `OIDA_TEST_URL`,
`OIDA_TEST_USER` und `OIDA_TEST_PW`.
