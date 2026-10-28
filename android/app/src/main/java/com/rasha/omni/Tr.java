package com.rasha.omni;

import java.util.HashMap;
import java.util.Map;

/**
 * Translations for the launcher's own screens: English (the keys), Albanian, Italian and Swiss Standard German (written the Swiss way, without the sharp s).
 * The English text is the key, so a missing translation shows English rather than nothing. Pure and tested: every entry must have all three translations.
 * Written for OMNI by its maintainer's assistant; native speakers are asked to review and correct (see docs/TRANSLATIONS.md).
 */
final class Tr {
    private Tr() { }

    static final String[] CODES = {"en", "sq", "it", "de"};
    static final String[] NAMES = {"English", "Shqip", "Italiano", "Deutsch (CH)"};
    /** Index into CODES. */
    static int lang = 0;

    private static final Map<String, String[]> M = new HashMap<>();

    private static void p(String en, String sq, String it, String de) { M.put(en, new String[]{sq, it, de}); }

    /** The language for a system language tag such as "it", "sq", "de-CH", "gsw": index into CODES, 0 (English) when it is not one we have. */
    static int fromSystem(String tag) {
        if (tag == null) return 0;
        String l = tag.toLowerCase(java.util.Locale.ROOT);
        if (l.startsWith("sq")) return 1;
        if (l.startsWith("it")) return 2;
        if (l.startsWith("de") || l.startsWith("gsw")) return 3;
        return 0;
    }

    static String t(String en) {
        if (lang <= 0 || en == null) return en;
        String[] v = M.get(en);
        return v == null ? en : v[Math.min(lang, 3) - 1];
    }

    /** "3 channels" in the chosen language. */
    static String count(int n, String one, String many) { return n + " " + t(n == 1 ? one : many); }

    static boolean has(String en) { return M.containsKey(en); }

    static java.util.Set<String> keys() { return M.keySet(); }

    static String[] all(String en) { return M.get(en); }

    static {
        // navigation and Home
        p("Home", "Kryefaqja", "Home", "Start");
        p("Watch", "Shiko", "Guarda", "Schauen");
        p("Apps", "Aplikacionet", "App", "Apps");
        p("TV", "TV", "TV", "TV");
        p("Settings", "Cilësimet", "Impostazioni", "Einstellungen");
        p("Hold OK for options", "Mbaj OK për opsione", "Tieni premuto OK per le opzioni", "OK gedrückt halten für Optionen");
        p("OK to watch   Hold OK for options   Right for inputs", "OK për të parë   Mbaj OK për opsione   Djathtas për hyrjet", "OK per guardare   Tieni OK per le opzioni   Destra per gli ingressi", "OK zum Schauen   OK halten für Optionen   Rechts für Eingänge");
        p("Opened", "Hapur", "Aperto", "Geöffnet");
        p("Not opened yet", "Ende pa u hapur", "Non ancora aperto", "Noch nicht geöffnet");
        p("time", "herë", "volta", "Mal");
        p("times", "herë", "volte", "Mal");
        p("Favorites", "Të preferuarat", "Preferiti", "Favoriten");
        p("Recent", "Së fundmi", "Recenti", "Zuletzt");
        p("Most used", "Më të përdorurat", "Più usati", "Meist genutzt");
        p("Continue watching", "Vazhdo shikimin", "Continua a guardare", "Weiterschauen");
        // Live TV
        p("Live TV", "TV Live", "TV in diretta", "Live-TV");
        p("All", "Të gjitha", "Tutti", "Alle");
        p("Kosovo", "Kosova", "Kosovo", "Kosovo");
        p("Albania", "Shqipëria", "Albania", "Albanien");
        p("Italy", "Italia", "Italia", "Italien");
        p("Switzerland", "Zvicra", "Svizzera", "Schweiz");
        p("Antenna", "Antena", "Antenna", "Antenne");
        p("Other", "Të tjera", "Altri", "Andere");
        p("Inputs", "Hyrjet", "Ingressi", "Eingänge");
        p("Most watched", "Më të parat", "Più visti", "Meist gesehen");
        p("A to Z", "A–Zh", "A–Z", "A–Z");
        p("Sort", "Rendit", "Ordina", "Sortieren");
        p("channel", "kanal", "canale", "Sender");
        p("channels", "kanale", "canali", "Sender");
        p("No channels here.", "Këtu nuk ka kanale.", "Nessun canale qui.", "Hier gibt es keine Sender.");
        p("Scan for antenna channels", "Kërko kanalet e antenës", "Cerca i canali dell'antenna", "Antennensender suchen");
        p("This TV has a tuner but no channels yet. Scanning is done by the TV itself.", "Ky TV ka tuner por ende nuk ka kanale. Kërkimin e bën vetë TV.", "Questa TV ha un sintonizzatore ma ancora nessun canale. La ricerca la fa la TV stessa.", "Dieser Fernseher hat einen Tuner, aber noch keine Sender. Die Suche macht der Fernseher selbst.");
        // settings
        p("Control Center", "Qendra e kontrollit", "Centro di controllo", "Kontrollzentrum");
        p("Status and shortcuts", "Gjendja dhe shkurtoret", "Stato e scorciatoie", "Status und Kurzbefehle");
        p("Welcome & setup", "Mirësevini dhe konfigurimi", "Benvenuto e configurazione", "Willkommen und Einrichtung");
        p("Run the setup again", "Nise konfigurimin përsëri", "Esegui di nuovo la configurazione", "Einrichtung erneut starten");
        p("Remote buttons", "Telekomanda", "Telecomando", "Fernbedienung");
        p("INFO, colour keys, mic", "INFO, butonat me ngjyra, mikrofoni", "INFO, tasti colorati, microfono", "INFO, Farbtasten, Mikrofon");
        p("Server", "Serveri", "Server", "Server");
        p("Home server status", "Gjendja e serverit në shtëpi", "Stato del server di casa", "Status des Heimservers");
        p("Gallery", "Galeria", "Galleria", "Galerie");
        p("Photos, videos, ambient display settings", "Foto, video, cilësimet e ekranit ambient", "Foto, video, impostazioni dello schermo ambient", "Fotos, Videos, Einstellungen der Ambient-Anzeige");
        p("Ambient display", "Ekrani ambient", "Schermo ambient", "Ambient-Anzeige");
        p("Start the photograph and clock now", "Nis fotografinë dhe orën tani", "Avvia foto e orologio ora", "Foto und Uhr jetzt starten");
        p("Updates", "Përditësimet", "Aggiornamenti", "Updates");
        p("Crash report", "Raporti i gabimit", "Rapporto errori", "Absturzbericht");
        p("What OMNI recorded when it last crashed", "Çfarë regjistroi OMNI në ndalesën e fundit", "Cosa ha registrato OMNI all'ultimo arresto", "Was OMNI beim letzten Absturz aufgezeichnet hat");
        p("Network", "Rrjeti", "Rete", "Netzwerk");
        p("Wi-Fi and Ethernet", "Wi-Fi dhe Ethernet", "Wi-Fi ed Ethernet", "WLAN und Ethernet");
        p("Display", "Ekrani", "Schermo", "Bildschirm");
        p("Resolution and picture", "Rezolucioni dhe imazhi", "Risoluzione e immagine", "Auflösung und Bild");
        p("Sound", "Zëri", "Audio", "Ton");
        p("Volume and outputs", "Volumi dhe daljet", "Volume e uscite", "Lautstärke und Ausgänge");
        p("Permissions and defaults", "Lejet dhe parazgjedhjet", "Permessi e predefiniti", "Berechtigungen und Standards");
        p("Storage", "Hapësira", "Spazio", "Speicher");
        p("Space and drives", "Hapësira dhe disqet", "Spazio e unità", "Platz und Laufwerke");
        p("Date & time", "Data dhe ora", "Data e ora", "Datum und Uhrzeit");
        p("Clock and time zone", "Ora dhe zona kohore", "Orologio e fuso orario", "Uhr und Zeitzone");
        p("About", "Rreth", "Informazioni", "Info");
        p("Model and software", "Modeli dhe softueri", "Modello e software", "Modell und Software");
        p("All settings", "Të gjitha cilësimet", "Tutte le impostazioni", "Alle Einstellungen");
        p("The full Android TV settings", "Cilësimet e plota të Android TV", "Le impostazioni complete di Android TV", "Die vollständigen Android-TV-Einstellungen");
        p("Language", "Gjuha", "Lingua", "Sprache");
        p("Quick access", "Qasje e shpejtë", "Accesso rapido", "Schnellzugriff");
        p("Choose what is on your Home row", "Zgjidh çfarë shfaqet në rreshtin tënd Home", "Scegli cosa mostrare nella riga Home", "Wähle, was in deiner Startzeile erscheint");
        p("Simple mode", "Modaliteti i thjeshtë", "Modalità semplice", "Einfacher Modus");
        p("No server, only what you use", "Pa server, vetëm çfarë përdor", "Nessun server, solo ciò che usi", "Kein Server, nur was du brauchst");
        p("Shortcuts into the TV's system settings, and OMNI's own options.", "Shkurtore te cilësimet e sistemit të TV dhe opsionet e OMNI.", "Scorciatoie verso le impostazioni di sistema della TV e le opzioni di OMNI.", "Kurzbefehle zu den Systemeinstellungen des Fernsehers und zu den Optionen von OMNI.");
        // welcome and setup
        p("WELCOME TO OMNI UI", "MIRË SE VINI NË OMNI UI", "BENVENUTO IN OMNI UI", "WILLKOMMEN BEI OMNI UI");
        p("Make OMNI your Home", "Bëje OMNI Home-in tënd", "Rendi OMNI la tua Home", "Mach OMNI zu deinem Start");
        p("Your remote", "Telekomanda jote", "Il tuo telecomando", "Deine Fernbedienung");
        p("Your OMNI server", "Serveri yt OMNI", "Il tuo server OMNI", "Dein OMNI-Server");
        p("Weather", "Moti", "Meteo", "Wetter");
        p("Antenna channels", "Kanalet e antenës", "Canali dell'antenna", "Antennensender");
        p("Permissions", "Lejet", "Permessi", "Berechtigungen");
        p("You're all set", "Gjithçka është gati", "È tutto pronto", "Alles bereit");
        p("Simple or full?", "I thjeshtë apo i plotë?", "Semplice o completa?", "Einfach oder voll?");
        p("Explore your TV to its maximum potential.", "Zbulo gjithë potencialin e TV tënd.", "Scopri tutto il potenziale della tua TV.", "Entdecke das ganze Potenzial deines Fernsehers.");
        p("Let's start", "Le të fillojmë", "Iniziamo", "Los geht's");
        p("Skip setup", "Kapërce konfigurimin", "Salta la configurazione", "Einrichtung überspringen");
        p("Next", "Tjetra", "Avanti", "Weiter");
        p("Skip", "Kapërce", "Salta", "Überspringen");
        p("Back: previous step", "Prapa: hapi i mëparshëm", "Indietro: passo precedente", "Zurück: voriger Schritt");
        p("Choose your language.", "Zgjidh gjuhën tënde.", "Scegli la tua lingua.", "Wähle deine Sprache.");
        p("Simple: no server, just your TV, your channels and your apps. Full: everything, including a home server for films and the Server screen. You can change this later in Settings.", "I thjeshtë: pa server, vetëm TV, kanalet dhe aplikacionet e tua. I plotë: gjithçka, përfshirë serverin në shtëpi për filma dhe ekranin Server. Mund ta ndryshosh më vonë te Cilësimet.", "Semplice: nessun server, solo la TV, i tuoi canali e le tue app. Completa: tutto, compreso un server di casa per i film e la schermata Server. Puoi cambiare più tardi nelle Impostazioni.", "Einfach: kein Server, nur dein Fernseher, deine Sender und deine Apps. Voll: alles, auch ein Heimserver für Filme und der Server-Bildschirm. Du kannst das später in den Einstellungen ändern.");
        p("Simple", "I thjeshtë", "Semplice", "Einfach");
        p("Full", "I plotë", "Completa", "Voll");
        p("With OMNI as your Home, the Home button on your remote always brings you here. You can switch back to the TV's own home screen at any time in Settings.", "Me OMNI si Home, butoni Home i telekomandës të sjell gjithmonë këtu. Mund të kthehesh te ekrani kryesor i TV në çdo kohë te Cilësimet.", "Con OMNI come Home, il tasto Home del telecomando ti porta sempre qui. Puoi tornare alla schermata Home della TV in qualsiasi momento dalle Impostazioni.", "Mit OMNI als Start bringt dich die Home-Taste der Fernbedienung immer hierher. Du kannst jederzeit in den Einstellungen zum Startbildschirm des Fernsehers zurückwechseln.");
        p("OMNI is your Home", "OMNI është Home-i yt", "OMNI è la tua Home", "OMNI ist dein Start");
        p("Another app is Home right now", "Tani një aplikacion tjetër është Home", "Ora un'altra app è la Home", "Im Moment ist eine andere App der Start");
        p("Choose Home", "Zgjidh Home", "Scegli Home", "Start wählen");
        p("Press INFO on your remote. It opens the Control Center from anywhere in OMNI: volume, inputs, server and weather in one place. Menu works too.", "Shtyp INFO në telekomandë. Hap Qendrën e kontrollit kudo në OMNI: volumin, hyrjet, serverin dhe motin në një vend. Edhe Menu funksionon.", "Premi INFO sul telecomando. Apre il Centro di controllo da qualsiasi punto di OMNI: volume, ingressi, server e meteo in un unico posto. Funziona anche Menu.", "Drücke INFO auf der Fernbedienung. Es öffnet das Kontrollzentrum von überall in OMNI: Lautstärke, Eingänge, Server und Wetter an einem Ort. Menü geht auch.");
        p("INFO works", "INFO funksionon", "INFO funziona", "INFO funktioniert");
        p("Waiting for a button…", "Po pres një buton…", "In attesa di un tasto…", "Warte auf eine Taste…");
        p("A computer at home can keep your films and show them here, with posters and Continue watching. No server? Skip this; everything else works without one.", "Një kompjuter në shtëpi mund t'i ruajë filmat e tu dhe t'i shfaqë këtu, me postera dhe «Vazhdo shikimin». Pa server? Kapërce; gjithçka tjetër punon pa të.", "Un computer di casa può conservare i tuoi film e mostrarli qui, con locandine e «Continua a guardare». Nessun server? Salta; tutto il resto funziona senza.", "Ein Computer zu Hause kann deine Filme speichern und hier zeigen, mit Postern und «Weiterschauen». Kein Server? Überspringe das; alles andere funktioniert ohne.");
        p("Not set up", "Nuk është konfiguruar", "Non configurato", "Nicht eingerichtet");
        p("Find my server", "Gjej serverin tim", "Trova il mio server", "Meinen Server finden");
        p("Pick the place for the weather on Home and in the Control Center.", "Zgjidh vendin për motin te Home dhe te Qendra e kontrollit.", "Scegli il luogo per il meteo nella Home e nel Centro di controllo.", "Wähle den Ort für das Wetter auf dem Start und im Kontrollzentrum.");
        p("No place chosen yet", "Ende nuk është zgjedhur vend", "Nessun luogo scelto", "Noch kein Ort gewählt");
        p("Choose place", "Zgjidh vendin", "Scegli il luogo", "Ort wählen");
        p("Change place", "Ndrysho vendin", "Cambia luogo", "Ort ändern");
        p("Your TV has an antenna tuner. OMNI finds the channels the TV has scanned and sorts them by country. If there are none yet, the TV's own scan finds them.", "TV yt ka tuner antene. OMNI gjen kanalet që TV ka kërkuar dhe i rendit sipas vendit. Nëse ende s'ka, i gjen kërkimi i vetë TV.", "La tua TV ha un sintonizzatore d'antenna. OMNI trova i canali che la TV ha cercato e li ordina per paese. Se non ce ne sono ancora, li trova la ricerca della TV.", "Dein Fernseher hat einen Antennen-Tuner. OMNI findet die vom Fernseher gesuchten Sender und sortiert sie nach Land. Gibt es noch keine, findet sie die Suche des Fernsehers.");
        p("Choose what is in your quick access row on Home: apps, or channels and inputs on a remote button. You can change it any time in Settings.", "Zgjidh çfarë ka rreshti i qasjes së shpejtë te Home: aplikacione, ose kanale dhe hyrje në një buton të telekomandës. Mund ta ndryshosh çdo kohë te Cilësimet.", "Scegli cosa c'è nella riga di accesso rapido nella Home: app, oppure canali e ingressi su un tasto del telecomando. Puoi cambiarlo quando vuoi nelle Impostazioni.", "Wähle, was in deiner Schnellzugriffszeile auf dem Start ist: Apps, oder Sender und Eingänge auf einer Fernbedienungstaste. Du kannst das jederzeit in den Einstellungen ändern.");
        p("Choose apps", "Zgjidh aplikacionet", "Scegli le app", "Apps wählen");
        p("Set remote buttons", "Cakto butonat", "Imposta i tasti", "Tasten festlegen");
        p("Listings fill Continue watching from your apps. HDMI lets inputs play live inside OMNI and shows notices over other apps. Mic is for the assistant. Casting lets OMNI notice when a phone casts to this TV. Each is optional.", "Listat mbushin «Vazhdo shikimin» nga aplikacionet e tua. HDMI lejon hyrjet të luajnë live brenda OMNI dhe tregon njoftime mbi aplikacionet e tjera. Mikrofoni është për asistentin. Cast lejon OMNI të vërejë kur një telefon transmeton në këtë TV. Secila është opsionale.", "Gli elenchi riempiono «Continua a guardare» dalle tue app. HDMI permette agli ingressi di andare in diretta dentro OMNI e mostra avvisi sopra le altre app. Il microfono è per l'assistente. Il casting permette a OMNI di accorgersi quando un telefono trasmette a questa TV. Ognuna è facoltativa.", "Listen füllen «Weiterschauen» aus deinen Apps. HDMI lässt Eingänge live in OMNI laufen und zeigt Hinweise über anderen Apps. Das Mikrofon ist für den Assistenten. Casting lässt OMNI merken, wenn ein Telefon auf diesen Fernseher überträgt. Jedes ist freiwillig.");
        p("INFO opens the Control Center.  Hold OK on anything for options.  The mic button talks to the assistant.  Settings has this setup again.", "INFO hap Qendrën e kontrollit.  Mbaj OK mbi çdo gjë për opsione.  Butoni i mikrofonit flet me asistentin.  Te Cilësimet e ke këtë konfigurim përsëri.", "INFO apre il Centro di controllo.  Tieni premuto OK su qualsiasi cosa per le opzioni.  Il tasto del microfono parla con l'assistente.  Nelle Impostazioni trovi di nuovo questa configurazione.", "INFO öffnet das Kontrollzentrum.  OK gedrückt halten für Optionen auf allem.  Die Mikrofontaste spricht mit dem Assistenten.  In den Einstellungen gibt es diese Einrichtung wieder.");
        p("Start using OMNI", "Fillo të përdorësh OMNI", "Inizia a usare OMNI", "OMNI verwenden");
        // quick access and remote buttons
        p("Choose your quick access", "Zgjidh qasjen tënde të shpejtë", "Scegli il tuo accesso rapido", "Wähle deinen Schnellzugriff");
        p("OK adds or removes an app on your Home row.", "OK shton ose heq një aplikacion nga rreshti Home.", "OK aggiunge o toglie un'app dalla riga Home.", "OK fügt eine App zur Startzeile hinzu oder entfernt sie.");
        p("Done", "U krye", "Fatto", "Fertig");
        p("Open an app…", "Hap një aplikacion…", "Apri un'app…", "App öffnen…");
        p("Open an input…", "Hap një hyrje…", "Apri un ingresso…", "Eingang öffnen…");
        p("Watch a channel…", "Shiko një kanal…", "Guarda un canale…", "Sender schauen…");
        p("Antenna channels", "Kanalet e antenës", "Canali dell'antenna", "Antennensender");
        p("Press a button on your remote", "Shtyp një buton në telekomandë", "Premi un tasto del telecomando", "Drücke eine Taste auf der Fernbedienung");
        p("Then choose what it should open.", "Pastaj zgjidh çfarë të hapë.", "Poi scegli cosa deve aprire.", "Dann wähle, was sie öffnen soll.");
        p("More", "Më shumë", "Altro", "Mehr");
        p("input", "hyrje", "ingresso", "Eingang");
        p("inputs", "hyrje", "ingressi", "Eingänge");
        p("Installed", "I instaluar", "Installato", "Installiert");
        p("up to date", "i përditësuar", "aggiornato", "aktuell");
        p("is available", "është i disponueshëm", "è disponibile", "ist verfügbar");
        p("is ready to install", "është gati për instalim", "è pronto da installare", "ist bereit zur Installation");
        p("What each button does while you are in OMNI.", "Çfarë bën çdo buton ndërsa je në OMNI.", "Cosa fa ogni tasto mentre sei in OMNI.", "Was jede Taste tut, solange du in OMNI bist.");
        p("Use the buttons everywhere", "Përdor butonat kudo", "Usa i tasti ovunque", "Tasten überall verwenden");
        p("On", "Aktiv", "Attivo", "Ein");
        p("Off", "Joaktiv", "Disattivo", "Aus");
        p("Add a button", "Shto një buton", "Aggiungi un tasto", "Taste hinzufügen");
        p("Back to the defaults", "Kthe te parazgjedhjet", "Torna alle impostazioni predefinite", "Zurück zu den Standards");
        p("Choose what this button does in OMNI.", "Zgjidh çfarë bën ky buton në OMNI.", "Scegli cosa fa questo tasto in OMNI.", "Wähle, was diese Taste in OMNI tut.");
        p("Nothing (its usual job)", "Asgjë (puna e zakonshme)", "Niente (la sua funzione abituale)", "Nichts (seine übliche Aufgabe)");
        p("Any button except arrows, OK, Back, Home, volume and power", "Çdo buton përveç shigjetave, OK, Prapa, Home, volumit dhe fikjes", "Qualsiasi tasto tranne frecce, OK, Indietro, Home, volume e accensione", "Jede Taste ausser Pfeilen, OK, Zurück, Start, Lautstärke und Ein/Aus");
        p("Open", "Hap", "Apri", "Öffne");
        p("Watch a channel", "Shiko një kanal", "Guarda un canale", "Sender schauen");
        p("Open an input", "Hap një hyrje", "Apri un ingresso", "Eingang öffnen");
        p("Assistant", "Asistenti", "Assistente", "Assistent");
        p("Search", "Kërko", "Cerca", "Suchen");
        p("OMNI Cinema", "OMNI Cinema", "OMNI Cinema", "OMNI Cinema");
        p("Language changed", "Gjuha u ndryshua", "Lingua cambiata", "Sprache geändert");
    }
}
