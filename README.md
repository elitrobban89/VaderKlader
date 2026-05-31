# Väder & Kläder

En webbtjänst som ger AI-genererade klädförslag baserat på ditt nuvarande väder och ditt valda färdmedel. Tjänsten är inbyggd i WordPress via ett shortcode-plugin och drivs av en Java-backend.

## Hur det fungerar

1. Användaren klickar på knappen och godkänner GPS-åtkomst
2. Väljer färdmedel: **Buss**, **Tåg**, **Cykel** eller **Bil**
3. Backen hämtar aktuell väderdata baserat på GPS-koordinaterna
4. AI:n genererar ett klädförslag anpassat till både vädret och färdmedlet
5. Förslaget visas direkt på sidan

## Teknologier

### Frontend
- **WordPress** med PHP-plugin och shortcode `[weather_outfit]`
- **JavaScript** — GPS-hämtning, API-anrop och UI-flöde

### Backend
- **Java 21** + **Spring Boot 3.1.5**
- **Maven** — byggverktyg
- Hostad på **Railway**

### Externa API:er
| API | Användning | Kostnad |
|-----|-----------|---------|
| [Open-Meteo](https://open-meteo.com/) | Väderdata (temperatur, vind, luftfuktighet, nederbörd) | Gratis |
| [Groq API](https://console.groq.com/) | AI-klädförslag via Llama 3.1 8B | Gratis (14 400 anrop/dag) |

## Projektstruktur

```
VaderKlader/
├── src/main/java/com/vaderklader/
│   ├── controller/
│   │   └── WeatherOutfitController.java   # REST-endpoint
│   ├── service/
│   │   ├── SmhiService.java               # Hämtar väderdata
│   │   └── ClaudeService.java             # Anropar Groq AI
│   ├── model/
│   │   ├── WeatherData.java
│   │   └── WeatherOutfitResponse.java
│   └── config/
│       └── CorsConfig.java
├── src/main/resources/
│   └── application.properties
└── wordpress/
    └── weather-outfit-shortcode.php       # WordPress-plugin
```

## API

### GET `/api/weather-outfit`

| Parameter | Typ | Beskrivning |
|-----------|-----|-------------|
| `lat` | double | Latitud (GPS) |
| `lon` | double | Longitud (GPS) |
| `transport` | string | `buss`, `tåg`, `cykel` eller `bil` |

**Exempel:**
```
GET /api/weather-outfit?lat=59.33&lon=18.06&transport=cykel
```

**Svar:**
```json
{
  "temperature": 14.2,
  "windSpeed": 4.1,
  "humidity": 72,
  "precipitationDescription": "Lätt regn",
  "outfitSuggestion": "Ta på dig ett vattentätt ytterskikt..."
}
```

## Installation

### Backend

1. Klona repot
2. Sätt miljövariabeln `GROQ_API_KEY` med din nyckel från [console.groq.com](https://console.groq.com/)
3. Bygg och starta:
```bash
mvn package
java -jar target/vader-klader-1.0-SNAPSHOT.jar
```

### WordPress-plugin

1. Zippa `wordpress/weather-outfit-shortcode.php` i en mapp med samma namn
2. Ladda upp via **Insticksprogram → Lägg till nytt → Ladda upp**
3. Aktivera pluginet
4. Lägg till `[weather_outfit]` på valfri sida

## Live

Tjänsten körs på [elitrobban.se](https://elitrobban.se)
