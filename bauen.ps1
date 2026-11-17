# Baut oida-sql.jar aus dem Quellcode und den drei Textdateien
# (woerterbuch.txt, einstellungen.txt, ausnahmen.txt).
#
#   bauen.ps1                 bauen
#   bauen.ps1 -Ausprobieren   nicht bauen, sondern Befehle eintippen und die Uebersetzung sehen
#
# Diese Datei absichtlich ohne Umlaute (Windows PowerShell liest sie sonst falsch).
param([switch]$Ausprobieren)

$ErrorActionPreference = 'Stop'
$hier = Split-Path -Parent $MyInvocation.MyCommand.Path

function Finde-JavaOrdner {
    $kandidaten = @()
    if ($env:JAVA_HOME) { $kandidaten += (Join-Path $env:JAVA_HOME 'bin') }
    $imPfad = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($imPfad) { $kandidaten += (Split-Path -Parent $imPfad.Source) }
    # Java, das DataGrip (oder eine andere JetBrains-IDE) mitbringt
    $muster = @(
        "$env:LOCALAPPDATA\Programs\DataGrip*\jbr\bin",
        "$env:ProgramFiles\JetBrains\DataGrip*\jbr\bin",
        "$env:LOCALAPPDATA\JetBrains\Toolbox\apps\datagrip\*\*\jbr\bin",
        "$env:LOCALAPPDATA\JetBrains\Toolbox\apps\DataGrip\*\*\jbr\bin",
        "$env:LOCALAPPDATA\Programs\*\jbr\bin",
        "$env:ProgramFiles\JetBrains\*\jbr\bin",
        "$env:USERPROFILE\.jdks\*\bin"
    )
    foreach ($m in $muster) {
        $kandidaten += @(Get-Item $m -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
    }
    foreach ($k in $kandidaten) {
        if ((Test-Path (Join-Path $k 'javac.exe')) -and (Test-Path (Join-Path $k 'java.exe'))) { return $k }
    }
    throw 'Kein Java mit javac gefunden. DataGrip installieren oder ein JDK installieren und JAVA_HOME setzen.'
}

$bin   = Finde-JavaOrdner
$java  = Join-Path $bin 'java.exe'
$javac = Join-Path $bin 'javac.exe'
$jar   = Join-Path $hier 'oida-sql.jar'

if ($Ausprobieren) {
    if (-not (Test-Path $jar)) { throw 'oida-sql.jar gibt es noch nicht. Zuerst bauen.bat ausfuehren.' }
    & $java -cp $jar at.oida.Werkzeug
    exit $LASTEXITCODE
}

Write-Host "Java: $bin"
$klassen = Join-Path $hier 'build\classes'
if (Test-Path (Join-Path $hier 'build')) { Remove-Item -Recurse -Force (Join-Path $hier 'build') }
New-Item -ItemType Directory -Force $klassen | Out-Null

$quellen = @(Get-ChildItem (Join-Path $hier 'src') -Recurse -Filter *.java | ForEach-Object { $_.FullName })
& $javac --release 11 -encoding UTF-8 -d $klassen $quellen
if ($LASTEXITCODE -ne 0) { throw 'Uebersetzen des Quellcodes ist fehlgeschlagen.' }

foreach ($datei in 'woerterbuch.txt', 'einstellungen.txt', 'ausnahmen.txt') {
    Copy-Item (Join-Path $hier $datei) (Join-Path $klassen 'at\oida')
}
$dienste = Join-Path $klassen 'META-INF\services'
New-Item -ItemType Directory -Force $dienste | Out-Null
[System.IO.File]::WriteAllText((Join-Path $dienste 'java.sql.Driver'), "at.oida.OidaDriver`n")

$neu = Join-Path $hier 'build\oida-sql.jar'
& $java (Join-Path $hier 'werkzeug\Packen.java') $klassen $neu
if ($LASTEXITCODE -ne 0) { throw 'Packen der Jar ist fehlgeschlagen.' }

# Woerterbuch pruefen, bevor die alte Jar ersetzt wird
& $java -cp $neu at.oida.Werkzeug --pruefen
if ($LASTEXITCODE -ne 0) { throw 'In den Textdateien ist ein Fehler (siehe oben). Die alte oida-sql.jar bleibt unveraendert.' }

Copy-Item $neu $jar -Force
Write-Host ''
Write-Host "Fertig: $jar"

# DataGrip-Plugin (blendet die Fehler aus, fuehrt den Befehl unter dem Cursor aus).
# Wird gegen die Bibliotheken von DataGrip gebaut, geht also nur mit dem Java aus DataGrip.
$datagripLib = Join-Path (Split-Path -Parent (Split-Path -Parent $bin)) 'lib'
if (Test-Path (Join-Path $datagripLib 'intellij.platform.editor.ui.jar')) {
    $pluginKlassen = Join-Path $hier 'build\plugin'
    New-Item -ItemType Directory -Force $pluginKlassen | Out-Null
    # Uebersetzer und Woerterbuch mit einpacken (fuer das Einfaerben der Dialekt-Woerter)
    Copy-Item -Recurse -Force (Join-Path $klassen 'at') $pluginKlassen
    $pluginQuellen = @(Get-ChildItem (Join-Path $hier 'datagrip-plugin\src') -Recurse -Filter *.java | ForEach-Object { $_.FullName })
    & $javac --release 21 -encoding UTF-8 -cp "$datagripLib\*;$klassen" -d $pluginKlassen $pluginQuellen
    if ($LASTEXITCODE -ne 0) { throw 'Uebersetzen des DataGrip-Plugins ist fehlgeschlagen.' }
    Copy-Item -Recurse -Force (Join-Path $hier 'datagrip-plugin\resources\*') $pluginKlassen
    # Versionsnummer aus version.txt in die plugin.xml schreiben (ohne BOM, sonst liest DataGrip sie nicht)
    $version = (Get-Content (Join-Path $hier 'version.txt') -TotalCount 1).Trim()
    $ohneBom = New-Object System.Text.UTF8Encoding($false)
    $pluginXml = Join-Path $pluginKlassen 'META-INF\plugin.xml'
    [System.IO.File]::WriteAllText($pluginXml, [System.IO.File]::ReadAllText($pluginXml).Replace('@VERSION@', $version), $ohneBom)
    # Den Treiber mit einpacken: das Plugin legt ihn beim Start nach Benutzerordner\.oida-sql\oida-sql.jar
    Copy-Item $jar (Join-Path $pluginKlassen 'at\oida\datagrip\oida-sql.jar') -Force
    $plugin = Join-Path $hier 'oida-sql-datagrip-plugin.jar'
    & $java (Join-Path $hier 'werkzeug\Packen.java') $pluginKlassen $plugin
    if ($LASTEXITCODE -ne 0) { throw 'Packen des DataGrip-Plugins ist fehlgeschlagen.' }
    Write-Host "Fertig: $plugin (Version $version)"
} else {
    Write-Host 'DataGrip-Bibliotheken nicht gefunden: das DataGrip-Plugin wird nicht neu gebaut.'
}
