# Väder & Kläder

AI-genererade klädförslag baserat på väder och färdmedel. Inbyggd i WordPress via shortcode-plugin med Java-backend.

## Flöde

GPS → välj färdmedel (Buss / Tåg / Cykel / Bil / Gång) → väderdata hämtas → AI-klädförslag visas

## UI
Widgeten har en animerad rubrikrad med rullande väderikoner (☀️ 🌤️ 🌧️ ❄️ ⛅) mot mörk blå bakgrund — mobilanpassad med media query under 420px. Startknappen har pulserande glow, shimmer-effekt och float-animation. Väder- och klädförslagsrutorna har mörkt färgtema med Groq-badge.

## Funktioner

- **Upplevd temperatur** — Open-Meteo `apparent_temperature` visas i väderkortet och skickas till AI:n för mer relevanta klädråd
- **Prognos** — timprognos för de nästa 6 timmarna. Om regn, snö, åska eller hagel väntas visas en gul varning i väderkortet och AI:n nämner det med ungefärlig tid. Åska och hagel ger specifika råd (söka skydd, cyklister bör stanna)
- **IP-begränsning** — max 20 förfrågningar per timme och IP-adress (sliding window, 429 vid överskridning)
- **Caching** — identisk väder + färdmedel-kombination cachas i 30 minuter, sparar tokens och ger snabbare svar
- **Groq-kvalitet** — system-prompt sätter persona, `temperature: 0.4` för konsistenta svar, `max_tokens: 200` håller svaren koncisa
- **Väder-fallback-cache** — Open-Meteo-data cachas per position (~1 km-grid); vid API-fel (429 eller nätverksfel) returneras senaste cachat värde oavsett ålder — inga tomma sidor vid tillfälliga driftstörningar
- **Regelbaserat + fallback-modell** — om `openai/gpt-oss-120b`-kvoten är slut provas `qwen/qwen3.6-27b` automatiskt; om det också misslyckas genereras ett regelbaserat klädförslag baserat på upplevd temperatur, nederbörd, vindstyrka och mörkertillstånd (8 temperaturintervall, stöd för cykel, regn, snö, åska, reflexer) — alltid ett svar till användaren
- **Felhantering** — Groq 429 ger svensk feltext med retry-tid ("Försök igen om X minuter"), stilad felruta i widgeten
- **Färdmedel** — alla fem alternativ (Buss, Tåg, Cykel, Bil, Gång) har specifik kontext för AI:n
- **UV-index** — visas i väderkortet och skickas till AI:n vid UV ≥ 3, ger råd om solskydd och solhatt
- **Vindriktning** — Open-Meteo `wind_direction_10m` omvandlas till svensk kardinalriktning (Norr, Nordost osv.), visas i väderkortet och används av AI:n
- **Optimerad prompt** — ~80 tokens sparas per anrop genom kortare instruktioner, ger ~25% mer kapacitet per dag
- **Cache-hantering** — max 500 entries, äldsta hälften rensas automatiskt när taket nås
- **Groq-status i health** — `GET /api/health` returnerar `groq: quota_exceeded` och `retryIn` när dagsgränsen är nådd
- **Mörker-detection** — sunrise/sunset från Open-Meteo, AI:n rekommenderar reflexer efter mörkrets inbrott
- **Reset-fix** — prognos-varning och UV-rad döljs korrekt när användaren väljer nytt färdmedel
- **Koordinatvalidering** — ogiltiga lat/lon ger 400 Bad Request med tydligt felmeddelande
- **Timeouts** — 5s connect / 10s read på Open-Meteo och Groq, inga frysta requests
- **Årstidskontext** — aktuell månad skickas till AI:n för säsongsanpassade klädråd
- **Cache-nyckel** — inkluderar vindrikting och mörker, dag/natt och nord/sydvind ger separata svar
- **Quota-reset** — `quotaExceededUntil` nollställs vid lyckat Groq-anrop, health-endpointen stämmer efter omstart
- **Browser-cache (localStorage)** — senaste resultatet visas direkt vid sidladdning inom 30 min, ingen väntan eller GPS-prompt
- **Anrop-räknare** — visar återstående anrop (t.ex. "18/20 anrop kvar"), varning i orange/rött när ≤ 6/3 kvar
- **Rate limit-nedräkning** — vid IP-gränsen visas en live-countdown ("Försök igen om 4 min 32 sek") som automatiskt visar startknappen när det är fritt
- **Stadssökning** — om GPS nekas kan användaren söka på stad (via Nominatim/OpenStreetMap) för att ändå få klädförslag
- **Regnintensietet** — skillnad mellan Lätt regn, Måttligt regn och Kraftigt regn (Open-Meteo koder 61/63/65/80/81/82)
- **Snöintensietet** — Lätt snö vs Kraftig snö (koder 71/73/75)
- **Dimma** — weather code 45/48 ger "Dimma" i väderkortet och AI:n ger råd om synlighet
- **Rate limit-headers** — `X-RateLimit-Remaining` och `X-RateLimit-Limit` i varje svar, exponerade via CORS
- **Retry-After** — 429-svar inkluderar `retryAfterSeconds` och `Retry-After`-header med exakt väntetid
- **WordPress content-filter fix** — JavaScript-blocket ligger i `wp_footer`-action (priority 99) för att undvika att WordPress `the_content`-filter konverterar `&&` till `&#038;&#038;` och bryter koden
- **Refresh-knapp** — uppdaterar väder och klädförslag med ett klick utan att byta färdmedel, rensar localStorage-cachen
- **Feels like-varning** — blå varningsrad visas när upplevd temperatur avviker mer än 4°C från faktisk (t.ex. "Känns 6°C kallare pga vind")
- **Nederbördsmängd** — visar mm bredvid beskrivningen när det regnar/snöar, t.ex. "Lätt regn (0.4 mm)"
- **6-timmarsprognos** — horisontell remsa visar "Om Xh", ikon, temperatur, regnchans (%) och vindstyrka (m/s) för nästa 6 timmar
- **Veckoprognos med klädförslag** — 5-dagarsöversikt med dagsnamn (Idag/Imorgon/Mån/Tis…), väderikon, max- och min-temperatur, samt ett kort regelbaserat klädtips per dag baserat på maxtemperatur och vädertyp (t.ex. "Tröja + jacka", "T-shirt + regnjacka", "Vinterjacka + stövlar") — genereras på backend utan extra AI-anrop
- **Soluppgång/solnedgång** — visas i väderkortet med klockslag från Open-Meteo daily
- **Transport-emojis** — färdmedelsetiketter inkluderar emoji: Buss 🚌, Tåg 🚆, Cykel 🚲, Bil 🚗, Gång 🚶
- **Laddningsspinners** — CSS-spinner visas under GPS-hämtning och klädförslagsgenerering
- **Naturlig prognos i AI-svar** — kommande regn/snö nämns i klädråden utan att duplicera varningsrutan
- **GPS-position sparas** — lat/lon cachas i localStorage i 6 timmar, återbesök inom 6h hoppar direkt till transport-valet utan ny GPS-prompt
- **Dela-knapp** — 🔗-knapp delar aktuellt klädförslag via Web Share API på mobil (systemets inbyggda delningsblad); på desktop kopieras texten till urklipp med bekräftelsefeedback

## Stack

| Del | Teknik |
|-----|--------|
| Frontend | WordPress (PHP), JavaScript |
| Backend | Java 21, Spring Boot, Docker — hostad på [Render](https://render.com) |
| Väder | [Open-Meteo](https://open-meteo.com/) — gratis |
| AI | [Groq](https://console.groq.com/) `openai/gpt-oss-120b` (fallback: `qwen/qwen3.6-27b`) — gratis |

## API

```
GET /api/weather-outfit?lat=59.33&lon=18.06&transport=cykel
```

## Installation

**Backend på Render:**
1. New → Web Service → välj detta repo → Language: Docker
2. Lägg till miljövariabel `GROQ_API_KEY`
3. Deploy

**Lokalt:**
```bash
GROQ_API_KEY=din-nyckel mvn package
java -jar target/vader-klader-1.0-SNAPSHOT.jar
```

**WordPress-plugin:**
1. Zippa `wordpress/weather-outfit-shortcode.php` i en mapp
2. Ladda upp via Insticksprogram → Lägg till nytt
3. Lägg till `[weather_outfit]` på valfri sida

## Footer

Projektkort i WordPress-footern finns i `wordpress/footer-projektkort.html`. Klistra in innehållet i ett Anpassad HTML-block i footern. CSS-fixet i filen tvingar WordPress Gutenberg-flexblocken att staplas vertikalt på mobil via `:has(.pj-wrap)`.

## Driftsättning

UptimeRobot pingar backenden var 5:e minut för att förhindra att Render-tjänsten somnar:
```
https://vaderklader-1.onrender.com/api/health
```
Viktigt: använd `/api/health` — inte `/api/weather-outfit` — annars förbrukas Groq-tokens i onödan (dagsgräns 100 000).

## Live

[elitrobban.se](https://elitrobban.se)
