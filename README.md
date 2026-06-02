# Väder & Kläder

AI-genererade klädförslag baserat på väder och färdmedel. Inbyggd i WordPress via shortcode-plugin med Java-backend.

## Flöde

GPS → välj färdmedel (Buss / Tåg / Cykel / Bil / Gång) → väderdata hämtas → AI-klädförslag visas

## Stack

| Del | Teknik |
|-----|--------|
| Frontend | WordPress (PHP), JavaScript |
| Backend | Java 21, Spring Boot, Docker — hostad på [Render](https://render.com) |
| Väder | [Open-Meteo](https://open-meteo.com/) — gratis |
| AI | [Groq](https://console.groq.com/) Llama 3.1 8B — gratis |

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

## Live

[elitrobban.se](https://elitrobban.se)
