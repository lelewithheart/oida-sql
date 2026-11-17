# Veroeffentlicht eine neue Version von OIDA-SQL:
#
#   1. schreibt die Versionsnummer nach version.txt
#   2. baut Treiber und Plugin (bauen.ps1) und die PDFs (doku\erzeugen.py)
#   3. legt Plugin, PDFs und updatePlugins.xml in den Ordner docs (das ist die Webseite,
#      von der DataGrip die Updates holt)
#   4. macht einen Git-Commit mit Tag, schiebt alles zu GitHub und legt dort ein Release an
#
#   veroeffentlichen.ps1 -Version 1.3.0
#   veroeffentlichen.ps1 -Version 1.3.0 -Text "Neue Woerter fuer Datumsfunktionen"
#
# Wichtig: Die Version muss groesser sein als die letzte, sonst zeigt DataGrip kein Update an.
# Diese Datei absichtlich ohne Umlaute (Windows PowerShell liest sie sonst falsch).
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [string]$Text = '',
    [string]$CommitZusatz = ''
)

$ErrorActionPreference = 'Stop'
$hier = Split-Path -Parent $MyInvocation.MyCommand.Path
$seite = 'https://lelewithheart.github.io/oida-sql'
$ohneBom = New-Object System.Text.UTF8Encoding($false)

if ($Version -notmatch '^\d+\.\d+\.\d+$') { throw 'Die Version muss so aussehen: 1.3.0' }
Push-Location $hier
try {
    $schon = git tag --list "v$Version"
    if ($schon) { throw "Die Version $Version gibt es schon. Nimm eine hoehere Nummer." }

    # 1. Versionsnummer
    [System.IO.File]::WriteAllText((Join-Path $hier 'version.txt'), "$Version`n", $ohneBom)

    # 2. Bauen
    & (Join-Path $hier 'bauen.ps1')
    $plugin = Join-Path $hier 'oida-sql-datagrip-plugin.jar'
    $treiber = Join-Path $hier 'oida-sql.jar'
    if (-not (Test-Path $plugin)) { throw 'Das Plugin wurde nicht gebaut (DataGrip nicht gefunden?).' }

    $weitergeben = Join-Path $hier 'weitergeben'
    Copy-Item $plugin $weitergeben -Force
    Copy-Item $treiber $weitergeben -Force
    $python = Get-Command python -ErrorAction SilentlyContinue
    if ($python) {
        & $python.Source (Join-Path $hier 'doku\erzeugen.py')
        if ($LASTEXITCODE -ne 0) { throw 'Die PDFs konnten nicht erzeugt werden.' }
    } else {
        Write-Host 'Python nicht gefunden: die PDFs werden nicht neu erzeugt.'
    }

    # 3. Webseite (Ordner docs)
    $docs = Join-Path $hier 'docs'
    Get-ChildItem $docs -Filter 'oida-sql-datagrip-plugin-*.jar' | Remove-Item -Force
    $pluginName = "oida-sql-datagrip-plugin-$Version.jar"
    Copy-Item $plugin (Join-Path $docs $pluginName) -Force
    foreach ($pdf in 'Anleitung.pdf', 'Woerterbuch.pdf') {
        if (Test-Path (Join-Path $weitergeben $pdf)) { Copy-Item (Join-Path $weitergeben $pdf) $docs -Force }
    }
    $liste = @"
<?xml version="1.0" encoding="UTF-8"?>
<!-- Diese Datei wird von veroeffentlichen.ps1 geschrieben. DataGrip liest sie, um Updates zu finden. -->
<plugins>
  <plugin id="at.oida.datagrip" url="$seite/$pluginName" version="$Version">
    <idea-version since-build="251"/>
    <name>OIDA-SQL</name>
    <vendor>OIDA-SQL</vendor>
    <description><![CDATA[MySQL im Dialekt: GIB MA * VON leit OIDA; Bringt den Treiber mit, blendet die Syntaxfehler aus und faerbt die Dialekt-Woerter ein.]]></description>
  </plugin>
</plugins>
"@
    [System.IO.File]::WriteAllText((Join-Path $docs 'updatePlugins.xml'), $liste, $ohneBom)
    [System.IO.File]::WriteAllText((Join-Path $docs 'version.txt'), "$Version`n", $ohneBom)

    # Zip zum Herunterladen (fuer alle, die es lieber von Hand installieren)
    $zip = Join-Path $hier "build\OIDA-SQL-$Version.zip"
    if (Test-Path $zip) { Remove-Item $zip -Force }
    Compress-Archive -Path (Join-Path $weitergeben '*') -DestinationPath $zip

    # 4. Git und GitHub
    git add -A
    if ($LASTEXITCODE -ne 0) { throw 'git add ist fehlgeschlagen.' }
    $nachricht = "Version $Version"
    if ($Text) { $nachricht = "$nachricht`: $Text" }
    if ($CommitZusatz) { git commit -m $nachricht -m $CommitZusatz } else { git commit -m $nachricht }
    if ($LASTEXITCODE -ne 0) { throw 'git commit ist fehlgeschlagen.' }
    git tag "v$Version"
    git push origin HEAD "v$Version"
    if ($LASTEXITCODE -ne 0) { throw 'git push ist fehlgeschlagen.' }

    $notiz = if ($Text) { $Text } else { "Version $Version" }
    gh release create "v$Version" $zip $plugin $treiber --title "OIDA-SQL $Version" --notes "$notiz`n`nIn DataGrip kommt das Update von selbst (Settings, Plugins). Anleitung: $seite"
    if ($LASTEXITCODE -ne 0) { throw 'Das Release auf GitHub konnte nicht angelegt werden.' }

    Write-Host ''
    Write-Host "Fertig: Version $Version ist veroeffentlicht."
    Write-Host "Die Webseite braucht ein bis zwei Minuten, bis sie die neue Version zeigt: $seite"
} finally {
    Pop-Location
}
