package com.vaderklader.service;

import com.vaderklader.model.WeatherData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tester för OpenMeteoServices rena logik: parsning av Open-Meteo-svaret,
 * väderkodsmappningar och klädråd per dag. Inga HTTP-anrop — parseResponse
 * matas med ett fixtur-JSON i samma format som API:t returnerar.
 */
class OpenMeteoServiceTest {

    private final OpenMeteoService service = new OpenMeteoService();

    // Realistiskt Open-Meteo-svar: kl 12:00, 18 km/h vind (= 5.0 m/s), regn (61) kl 14:00
    private static final String SAMPLE_JSON = """
        {
          "current": {
            "time": "2026-07-04T12:00",
            "temperature_2m": 21.3,
            "apparent_temperature": 19.8,
            "wind_speed_10m": 18.0,
            "wind_direction_10m": 90,
            "relative_humidity_2m": 65,
            "precipitation": 0.0,
            "weather_code": 1,
            "uv_index": 4.2
          },
          "hourly": {
            "time": ["2026-07-04T10:00","2026-07-04T11:00","2026-07-04T12:00","2026-07-04T13:00","2026-07-04T14:00","2026-07-04T15:00","2026-07-04T16:00","2026-07-04T17:00"],
            "weather_code": [1, 1, 1, 2, 61, 61, 3, 1],
            "temperature_2m": [19.0, 20.1, 21.3, 21.8, 20.5, 19.9, 19.2, 18.7],
            "precipitation_probability": [5, 5, 10, 20, 80, 75, 30, 10],
            "wind_speed_10m": [10.8, 14.4, 18.0, 18.0, 21.6, 18.0, 14.4, 10.8]
          },
          "daily": {
            "time": ["2026-07-04","2026-07-05","2026-07-06"],
            "sunrise": ["2026-07-04T04:30","2026-07-05T04:31","2026-07-06T04:32"],
            "sunset": ["2026-07-04T21:45","2026-07-05T21:44","2026-07-06T21:43"],
            "temperature_2m_max": [22.0, 24.5, 17.0],
            "temperature_2m_min": [14.0, 15.5, 11.0],
            "weather_code": [1, 0, 61]
          }
        }
        """;

    @Test
    void parseResponseLaserGrunddata() throws Exception {
        WeatherData d = service.parseResponse(SAMPLE_JSON);
        assertThat(d.getTemperature()).isEqualTo(21.3);
        assertThat(d.getFeelsLike()).isEqualTo(19.8);
        assertThat(d.getWindSpeed()).isEqualTo(5.0);       // 18 km/h → 5.0 m/s
        assertThat(d.getWindDirection()).isEqualTo("Öst"); // 90°
        assertThat(d.getSunrise()).isEqualTo("04:30");
        assertThat(d.getSunset()).isEqualTo("21:45");
        assertThat(d.isDark()).isFalse();                  // 12:00 mellan soluppgång och solnedgång
    }

    @Test
    void parseResponseVarnarForRegnOmTvaTimmar() throws Exception {
        WeatherData d = service.parseResponse(SAMPLE_JSON);
        assertThat(d.getForecastWarning())
                .contains("Regn")
                .contains("2 timmar")
                .contains("14:00");
    }

    @Test
    void parseResponseByggerTimOchDagsprognos() throws Exception {
        WeatherData d = service.parseResponse(SAMPLE_JSON);
        // Nuvarande timme är index 2 (12:00) av 8 → 5 kommande timmar (13–17)
        assertThat(d.getHourlyForecast()).hasSize(5);
        assertThat(d.getHourlyForecast().get(1).precipitationProbability()).isEqualTo(80); // 14:00
        assertThat(d.getDailyForecast()).hasSize(3);
        assertThat(d.getDailyForecast().get(0).dayName()).isEqualTo("Idag");
        assertThat(d.getDailyForecast().get(1).dayName()).isEqualTo("Imorgon");
    }

    @Test
    void dailyOutfitPerTemperaturOchVader() {
        assertThat(service.dailyOutfit(27, 0)).isEqualTo("T-shirt + shorts");
        assertThat(service.dailyOutfit(21, 0)).isEqualTo("T-shirt");
        assertThat(service.dailyOutfit(12, 0)).isEqualTo("Tröja + jacka");
        assertThat(service.dailyOutfit(-3, 0)).isEqualTo("Vinterjacka + lager");
        assertThat(service.dailyOutfit(21, 61)).isEqualTo("T-shirt + regnjacka"); // regn
        assertThat(service.dailyOutfit(2, 71)).isEqualTo("Vinterjacka + stövlar"); // snö
    }

    @Test
    void vaderstreckFranGrader() {
        assertThat(service.toCardinal(0)).isEqualTo("Norr");
        assertThat(service.toCardinal(90)).isEqualTo("Öst");
        assertThat(service.toCardinal(225)).isEqualTo("Sydväst");
        assertThat(service.toCardinal(359)).isEqualTo("Norr"); // wrap-around
    }

    @Test
    void svenskaVeckodagsnamn() {
        assertThat(service.toDayNameSv("2026-01-05")).isEqualTo("Mån");
        assertThat(service.toDayNameSv("2026-01-10")).isEqualTo("Lör");
        assertThat(service.toDayNameSv("ogiltigt-datum")).isEqualTo("ogiltigt-datum");
    }

    @Test
    void vaderkodTillNederbordskategori() {
        assertThat(service.weatherCodeToPrecipCategory(0)).isZero();    // klart
        assertThat(service.weatherCodeToPrecipCategory(45)).isEqualTo(9);  // dimma
        assertThat(service.weatherCodeToPrecipCategory(61)).isEqualTo(3);  // lätt regn
        assertThat(service.weatherCodeToPrecipCategory(75)).isEqualTo(13); // kraftig snö
        assertThat(service.weatherCodeToPrecipCategory(95)).isEqualTo(7);  // åska
    }

    @Test
    void vaderkodTillIkon() {
        assertThat(service.weatherCodeToIcon(0)).isEqualTo("☀️");
        assertThat(service.weatherCodeToIcon(63)).isEqualTo("🌧️");
        assertThat(service.weatherCodeToIcon(73)).isEqualTo("❄️");
        assertThat(service.weatherCodeToIcon(95)).isEqualTo("⛈️");
    }
}
