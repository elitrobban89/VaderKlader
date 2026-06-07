# Väder & Kläder

AI-genererade klädförslag baserat på väder och färdmedel. Inbyggd i WordPress via shortcode-plugin med Java-backend.

## Flöde

GPS → välj färdmedel (Buss / Tåg / Cykel / Bil / Gång) → väderdata hämtas → AI-klädförslag visas

## UI
Widgeten har en animerad rubrikrad med rullande väderikoner (☀️ 🌤️ 🌧️ ❄️ ⛅) och texten "Väderbaserade Klädförslag" mot mörk blå bakgrund — mobilanpassad med media query under 420px. Startknappen har pulserande glow, shimmer-effekt och float-animation. Mörkt färgtema på väder- och klädförslagsrutorna.

## Prognos
Hämtar även timprognos för de nästa 4 timmarna. Om regn eller snö väntas nämner AI:n det i klädförslaget med ungefärlig tid, t.ex. *"Ta med ett paraply — regn väntas om ca 2 timmar (ca kl 16:00)"*.

Testat mot Mexico City (klart nu, duggregn om 3 timmar) — AI:n svarade korrekt med regnkappa, vattentäta skor och paraply-rekommendation.

Testat mot Stockholm med Llama 3.3 70B — naturlig och korrekt svenska för alla färdmedel:
- **Cykel**: fleecejacka, tights, vindskydd
- **Buss**: lagerklädsel, vattentäta skor vid hållplatsen
- **Gång**: linneblus, shorts, handskar mot vinden
- **Gång med regnprognos** (Mexico City, regn om 3h): *"OBS: Regn väntas kl 15:00 — ta med regnkläder eller ett paraply"* + regnkappa och paraply rekommenderades
- **Bil med regnprognos**: vattenavvisande jacka + paraply för promenaden till/från bilen
- **Tåg med regnprognos**: lagerklädsel för varm vagn + *"ta med paraply om du är ute efter kl 15:00"*

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

## Driftsättning

UptimeRobot pingar backenden var 5:e minut för att förhindra att Render-tjänsten somnar:
```
https://vaderklader-1.onrender.com/api/health
```
(Använd `/api/health` — inte `/api/weather-outfit` — annars förbrukas Groq-tokens i onödan.)

## Live

[elitrobban.se](https://elitrobban.se)
