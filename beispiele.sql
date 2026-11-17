-- ============================================================================
--  OIDA-SQL  -  Beispiele zum Abschauen
-- ============================================================================
--  Jeder Befehl hört mit OIDA auf, danach kommt ein Strichpunkt.
--  Diese Datei wird beim Testen wirklich gegen MySQL ausgeführt
--  (test/BeispieleTest.java), die Befehle stimmen also.

-- Aufräumen, falls von vorher noch was da ist
GUSCH DI AUSICHT WENNS DES GIBT brave OIDA;
GUSCH DI TABÖN WENNS DES GIBT schuela OIDA;
GUSCH DI TABÖN WENNS DES GIBT klassn OIDA;

-- Tabellen anlegen
ERSTÖN TABÖN WENNS NO NED GIBT klassn (
    id   ZOI ZÖHT SÖWA MIT HAUPTSCHLISSL,
    name KETTN(20) MUASS SEI EINMOLIG SENF 'zum Beispiel 4AHIT'
) MOTA=InnoDB OIDA;

ERSTÖN TABÖN schuela (
    id         ZOI OHNE MINUS ZÖHT SÖWA MIT HAUPTSCHLISSL,
    name       KETTN(50) MUASS SEI,
    geburtstog TOG,
    schnitt    KOMMAZOI(3,2) NORMALERWEIS NUI,
    brav       JONA STANDARD WOAHR,
    gschicht   GSCHICHTL,
    klasse_id  ZOI,
    FREMDSCHLISSL (klasse_id) ZAGT AUF klassn(id) AUF SCHLEICH DI DOMINO
) OIDA;

-- Daten einehaun
EINEHAUN IN klassn (name) WERTE ('4AHIT'), ('4BHIT') OIDA;

EINEHAUN IN schuela (name, geburtstog, schnitt, klasse_id) WERTE
    ('Franz', '2008-03-14', 1.80, 1),
    ('Sepp',  '2008-11-02', 3.25, 1),
    ('Resi',  '2009-01-20', 1.20, 2),
    ('Vroni', '2008-07-07', NIX,  2) OIDA;

-- Doppelt? Wurscht.
EINEHAUN WURSCHT IN klassn (name) WERTE ('4AHIT') OIDA;

-- Abfragen
GIB MA * VON schuela OIDA;

ZAG MA name, schnitt VON schuela
WO schnitt IS NED NUI UND schnitt DAZWISCHN 1 UND 2
DA REIH NOCH schnitt OWE HÖCHSTENS 3 OIDA;

GIB MA EINZIGOATIG klasse_id VON schuela SORTIERN NOCH klasse_id AUFFE GRENZ 5 OBSTOND 0 OIDA;

GIB MA k.name OIS klasse, WIAVÜ(*) OIS leit, PI MOI DAUMEN(IM SCHNITT(s.schnitt), 1) HAASST notn
VON schuela s ZAMQUETSCHN klassn k AUF s.klasse_id = k.id
NIMM ZOM NOCH k.name DER HOT WIAVÜ(*) > 1 OIDA;

GIB MA s.name, k.name VON schuela s LINKS AUSSN VERBANDLN klassn k AUF s.klasse_id = k.id OIDA;

GIB MA name,
    KOMMT DRAUF AU
        FOIS schnitt < 2 DAUN 'Streber'
        FOIS schnitt IS NIX DAUN 'ka Ahnung'
        SUNST 'passt eh'
    PASST SCHO OIS urteil
VON schuela OIDA;

GIB MA SCHREI(name), FLÜSTER(name), LÄNGN(name), STICKL(name, 1, 2), ZAMPICKN(name, ' is do')
VON schuela WO name SO WIA 'F%' ODA name DRINNA ('Resi', 'Sepp') OIDA;

GIB MA ERSTBESTE(schnitt, 5), FOISNIX(schnitt, 5), WENN(brav, 'brav', 'schlimm'), JETZAD(), HEIT() VON schuela OIDA;

GIB MA KLANSTA(schnitt), GRÖSSTA(schnitt), ZAMZÖHN(schnitt), GRUPPN_ZAMPICKN(name) VON schuela OIDA;

GIB MA name VON schuela WO GIBTS (GIB MA 1 VON klassn WO klassn.id = schuela.klasse_id) OIDA;

GIB MA name VON schuela ZAMMGLEGT OLLE GIB MA name VON klassn OIDA;

MIT guade OIS (GIB MA name, schnitt VON schuela WO schnitt < 2)
GIB MA * VON guade OIDA;

-- Ändern
AKTUALISIERN schuela SETZN brav = SCHMARRN WO name = 'Sepp' OIDA;
AUSBESSERN schuela SETZN brav = STIMMT WO name = 'Sepp' OIDA;

ÄNDER TABÖN schuela DAZUA SPOITN spitznom KETTN(30) HINTA name OIDA;
ÄNDER TABÖN schuela UMBAUN SPOITN spitznom KETTN(40) OIDA;
ÄNDER TABÖN schuela ZAMHAUN SPOITN spitznom OIDA;
ERSTÖN VERZEICHNIS schuela_name AUF schuela (name) OIDA;

-- Transaktion: probieren und doch wieder zurück
GEMMA OIDA;
SCHLEICH DI VON schuela WO name = 'Vroni' OIDA;
DES WOA NIX OIDA;

GEMMA OIDA;
LÖSCHN VON schuela WO name = 'Vroni' OIDA;
DES BLEIBT SO OIDA;

-- Anschauen
ERSTÖN AUSICHT brave OIS GIB MA name VON schuela WO brav = STIMMT OIDA;
HERZAGN DE TISCHE OIDA;
HERZAGN DE SPOITN VON schuela OIDA;
HERZAGN ERSTÖN TABÖN schuela OIDA;
WOS IS schuela OIDA;
ERKLÄR MA GIB MA * VON schuela WO id = 1 OIDA;

-- Und wieder weg damit
AUSRAMA TABÖN schuela OIDA;
GUSCH DI AUSICHT brave OIDA;
GUSCH DI TABÖN schuela OIDA;
ZAMHAUN TABÖN klassn OIDA;
