# Väder & Kläder

AI-genererade klädförslag baserat på väder och färdmedel. Inbyggd i WordPress via shortcode-plugin med Java-backend.

## Flöde

GPS → välj färdmedel (Buss / Tåg / Cykel / Bil / Gång) → väderdata hämtas → AI-klädförslag visas

## UI
Widgeten har en animerad rubrikrad med rullande väderikoner (☀️ 🌤️ 🌧️ ❄️ ⛅) mot mörk blå bakgrund — mobilanpassad med media query under 420px. Startknappen har pulserande glow, shimmer-effekt och float-animation. Väder- och klädförslagsrutorna har mörkt färgtema med Groq-badge.

## Funktioner

- **Upplevd temperatur** — Open-Meteo `apparent_temperature` visas i väderkortet och skickas till AI:n för mer relevanta klädråd
- **Prognos** — timprognos för de nästa 6 timmarna. Om regn, snö, åska eller hagel väntas visas en gul varning i väderkortet och AI:n nämner det med ungefärlig tid. Åska och hagel ger specifika råd (söka skydd, cyklister bör stanna)
- **IP-begränsning** — max 10 förfrågningar per timme och IP-adress (sliding window, 429 vid överskridning)
- **Caching** — identisk väder + färdmedel-kombination cachas i 30 minuter, sparar tokens och ger snabbare svar
- **Groq-kvalitet** — system-prompt sätter persona, `temperature: 0.4` för konsistenta svar, `max_tokens: 200` håller svaren koncisa
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
- **Årstidskontext** — aktuell månad skickas till AI:n för säsongsan­passade klädråd

## Stack

| Del | Teknik |
|-----|--------|
| Frontend | WordPress (PHP), JavaScript |
| Backend | Java 21, Spring Boot, Docker — hostad på [Render](https://render.com) |
| Väder | [Open-Meteo](https://open-meteo.com/) — gratis |
| AI | [Groq](https://console.groq.com/) Llama 3.3 70B — gratis |

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
