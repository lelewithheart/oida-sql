"""Erzeugt Anleitung.pdf und Woerterbuch.pdf im Ordner ../weitergeben.

Die Anleitung ist anleitung.html. Das Wörterbuch wird aus ../woerterbuch.txt gebaut,
steht also immer das drin, was der Treiber wirklich versteht. Gedruckt wird mit Edge.

Aufruf:  python erzeugen.py
"""
import html
import math
import re
import subprocess
import sys
import tempfile
import time
from pathlib import Path

HIER = Path(__file__).resolve().parent
WOERTERBUCH = HIER.parent / "woerterbuch.txt"
ZIEL = HIER.parent / "weitergeben"
EDGE = [
    Path(r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"),
    Path(r"C:\Program Files\Microsoft\Edge\Application\msedge.exe"),
    Path(r"C:\Program Files\Google\Chrome\Application\chrome.exe"),
]

# Platz auf einer A4-Seite in Millimetern (vorsichtig gerechnet)
HOEHE_ERSTE_SEITE = 212
HOEHE_SEITE = 246
ZEILE = 6.0           # eine Tabellenzeile im Wörterbuch
KOPF_GRUPPE = 14.5    # Überschrift einer Gruppe samt Abstand
ZEICHEN_PRO_ZEILE = 27
ZEILE_ABC = 4.45
KOPF_BUCHSTABE = 8.6
BREITE_ABC = 40.0     # Breite einer Spalte im Abc-Teil


def lies_woerterbuch():
    """Liefert [(Gruppenname, [(englisch, [varianten], auch_englisch)])] in der Reihenfolge der Datei."""
    gruppen = []
    for roh in WOERTERBUCH.read_text(encoding="utf-8-sig").splitlines():
        zeile = roh.strip()
        ueberschrift = re.match(r"^#\s*-{3}\s*(.+?)\s*-{3,}\s*$", zeile)
        if ueberschrift:
            name = re.sub(r"\s*\(.*\)\s*$", "", ueberschrift.group(1))
            gruppen.append((name, []))
            continue
        if not zeile or zeile.startswith("#") or "=" not in zeile:
            continue
        englisch, rechts = [t.strip() for t in zeile.split("=", 1)]
        englisch = " ".join(englisch.split())
        varianten = [" ".join(v.split()) for v in rechts.split(",") if v.strip()]
        if not varianten:
            continue
        auch_englisch = any(v.upper() == englisch.upper() for v in varianten)
        varianten = [v for v in varianten if v.upper() != englisch.upper()]
        if not gruppen:
            gruppen.append(("Allgemein", []))
        gruppen[-1][1].append((englisch, varianten, auch_englisch))
    return [(name, eintraege) for name, eintraege in gruppen if eintraege]


def zeilen_hoehe(eintrag):
    englisch, varianten, auch = eintrag
    text = " / ".join(varianten) + ("  auch englisch" if auch and varianten else "")
    return ZEILE * max(1, math.ceil(len(text) / ZEICHEN_PRO_ZEILE))


def gruppe_html(name, eintraege, fortsetzung=False):
    zeilen = []
    for englisch, varianten, auch in eintraege:
        if varianten:
            oida = '<span class="oder">/</span>'.join(html.escape(v) for v in varianten)
            if auch:
                oida += '<span class="auch">auch englisch</span>'
        else:
            oida = '<span class="auch" style="margin-left:0">bleibt gleich</span>'
        zeilen.append(f'<tr><td class="en">{html.escape(englisch)}</td><td class="oida">{oida}</td></tr>')
    titel = html.escape(name) + (" (weiter)" if fortsetzung else "")
    return f'<div class="gruppe"><h3>{titel}</h3><table class="woerter">{"".join(zeilen)}</table></div>'


def verteile(bloecke, hoehe_erste, hoehe, spalten):
    """Verteilt Blöcke (hoehe, html) der Reihe nach auf Seiten mit mehreren Spalten."""
    seiten = [[[] for _ in range(spalten)]]
    spalte = 0
    frei = hoehe_erste
    for block_hoehe, block_html in bloecke:
        if block_hoehe > frei and seiten[-1][spalte]:
            spalte += 1
            if spalte == spalten:
                seiten.append([[] for _ in range(spalten)])
                spalte = 0
            frei = hoehe_erste if len(seiten) == 1 else hoehe
        seiten[-1][spalte].append(block_html)
        frei -= block_hoehe
    return seiten


def sortier_schluessel(wort):
    return wort.upper().translate(str.maketrans({"Ä": "A", "Ö": "O", "Ü": "U", "ß": "SS"}))


def baue_woerterbuch_html(gruppen):
    anzahl = sum(1 for _, e in gruppen for _, varianten, _ in e if varianten)   # nur echte Übersetzungen

    # Teil 1: nach Thema, MySQL -> OIDA
    bloecke = []
    for name, eintraege in gruppen:
        # Sehr lange Gruppen in zwei Blöcke teilen, damit sie in eine Spalte passen
        grenze = 30
        for anfang in range(0, len(eintraege), grenze):
            teil = eintraege[anfang:anfang + grenze]
            hoehe = KOPF_GRUPPE + sum(zeilen_hoehe(e) for e in teil)
            bloecke.append((hoehe, gruppe_html(name, teil, fortsetzung=anfang > 0)))
    thema_seiten = verteile(bloecke, HOEHE_ERSTE_SEITE, HOEHE_SEITE, 2)

    # Teil 2: nach dem Abc, OIDA -> MySQL
    paare = sorted(((v, englisch) for _, eintraege in gruppen for englisch, varianten, _ in eintraege for v in varianten),
                   key=lambda p: sortier_schluessel(p[0]))
    # Kleine Blöcke (ein Buchstabe wird über mehrere verteilt), damit die Spalten gut gefüllt werden.
    abc_bloecke = []
    buchstabe = None
    zeilen = []
    hoehe = 0.0
    mit_kopf = False

    def abschliessen():
        nonlocal zeilen, hoehe, mit_kopf
        if zeilen:
            kopf = f'<div class="anfang">{buchstabe}</div>' if mit_kopf else ''
            abc_bloecke.append((hoehe + (KOPF_BUCHSTABE if mit_kopf else 0),
                                f'<div class="abcblock">{kopf}{"".join(zeilen)}</div>'))
        zeilen, hoehe, mit_kopf = [], 0.0, False

    for oida, englisch in paare:
        erster = sortier_schluessel(oida)[0]
        if erster != buchstabe:
            abschliessen()
            buchstabe = erster
            mit_kopf = True
        elif len(zeilen) >= 4:
            abschliessen()
        zeilen.append(f'<div class="z"><span class="o">{html.escape(oida)}</span><span class="e">{html.escape(englisch)}</span></div>')
        zu_breit = len(oida) * 1.85 + len(englisch) * 1.6 + 6 > BREITE_ABC
        hoehe += ZEILE_ABC * (2 if zu_breit else 1)
    abschliessen()
    abc_seiten = verteile(abc_bloecke, HOEHE_SEITE - 12, HOEHE_SEITE, 4)

    gesamt = len(thema_seiten) + len(abc_seiten)
    teile = ['<!doctype html><html lang="de"><head><meta charset="utf-8"><title>OIDA-SQL – Wörterbuch</title>'
             '<link rel="stylesheet" href="stil.css"><style>'
             '.reihe{display:flex;gap:8mm;align-items:flex-start}.reihe>div{flex:1 1 0;min-width:0}'
             '.reihe.eng{gap:5mm}</style></head><body>']
    nummer = 0
    for i, seite in enumerate(thema_seiten):
        nummer += 1
        teile.append('<div class="seite">')
        if i == 0:
            teile.append('<div class="fahne"></div><div class="kopf"><div><h1><span>OIDA</span>-SQL</h1>'
                         '<div class="unter">Das Wörterbuch. Links MySQL, rechts wie’s bei uns haaßt.</div></div>'
                         f'<div class="stempel">{anzahl} Wörter<br>auf guat Deitsch</div></div>'
                         '<p style="margin-bottom:6mm">Stehen rechts mehrere Varianten, gehen alle. '
                         'Groß- und Kleinschreibung ist egal. Am Ende von jedem Befehl steht <code>OIDA;</code></p>')
        else:
            teile.append('<div class="kopf-klein"><div class="titel"><span>OIDA</span>-SQL</div>'
                         '<div class="rechts">Wörterbuch · MySQL → Dialekt</div></div>')
        teile.append('<div class="reihe">' + "".join(f'<div>{"".join(spalte)}</div>' for spalte in seite) + '</div>')
        teile.append(f'<div class="fuss"><span>OIDA-SQL · Wörterbuch</span><span>Seite {nummer} von {gesamt}</span></div></div>')
    for i, seite in enumerate(abc_seiten):
        nummer += 1
        teile.append('<div class="seite"><div class="kopf-klein"><div class="titel"><span>OIDA</span>-SQL</div>'
                     '<div class="rechts">Wörterbuch · Dialekt → MySQL, nach dem Abc</div></div>')
        if i == 0:
            teile.append('<p style="margin-bottom:4mm">Zum Nachschauen, wenn du ein Dialekt-Wort liest und wissen willst, was es ist.</p>')
        teile.append('<div class="reihe eng">' + "".join(f'<div>{"".join(spalte)}</div>' for spalte in seite) + '</div>')
        teile.append(f'<div class="fuss"><span>OIDA-SQL · Wörterbuch</span><span>Seite {nummer} von {gesamt}</span></div></div>')
    teile.append('</body></html>')
    return "".join(teile), gesamt, anzahl


def drucke(html_datei, pdf_datei):
    edge = next((e for e in EDGE if e.exists()), None)
    if edge is None:
        sys.exit("Weder Edge noch Chrome gefunden.")
    if pdf_datei.exists():
        pdf_datei.unlink()
    with tempfile.TemporaryDirectory(ignore_cleanup_errors=True) as profil:
        subprocess.run([str(edge), "--headless", "--disable-gpu", "--no-pdf-header-footer",
                        f"--user-data-dir={profil}", f"--print-to-pdf={pdf_datei}", html_datei.as_uri()],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=120)
        # Edge kommt manchmal zurück, bevor die Datei fertig geschrieben ist: warten, bis sie da ist
        # und sich ihre Größe nicht mehr ändert.
        letzte = -1
        for _ in range(120):
            groesse = pdf_datei.stat().st_size if pdf_datei.exists() else -1
            if groesse > 0 and groesse == letzte:
                return
            letzte = groesse
            time.sleep(0.5)
    sys.exit(f"Edge hat {pdf_datei.name} nicht geschrieben.")


def main():
    ZIEL.mkdir(exist_ok=True)
    gruppen = lies_woerterbuch()
    seite, seiten, anzahl = baue_woerterbuch_html(gruppen)
    woerterbuch_html = HIER / "woerterbuch.html"
    woerterbuch_html.write_text(seite, encoding="utf-8")
    drucke(HIER / "anleitung.html", ZIEL / "Anleitung.pdf")
    drucke(woerterbuch_html, ZIEL / "Woerterbuch.pdf")
    print(f"Woerterbuch: {anzahl} Woerter in {len(gruppen)} Gruppen, {seiten} Seiten geplant")
    print(f"Fertig: {ZIEL / 'Anleitung.pdf'}")
    print(f"Fertig: {ZIEL / 'Woerterbuch.pdf'}")


if __name__ == "__main__":
    main()
