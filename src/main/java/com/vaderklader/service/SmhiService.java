package com.vaderklader.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaderklader.model.WeatherData;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class SmhiService {

    private static final String OPEN_METEO_URL =
        "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s" +
        "&current=temperature_2m,apparent_temperature,wind_speed_10m,relative_humidity_2m,precipitation,weather_code" +
        "&hourly=weather_code&forecast_days=1&timezone=auto";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WeatherData getWeather(double lat, double lon) {
        String url = String.format(OPEN_METEO_URL,
            String.valueOf(lat).replace(",", "."),
            String.valueOf(lon).replace(",", "."));

        try {
            String json = restTemplate.getForObject(url, String.class);
            return parseResponse(json);
        } catch (Exception e) {
            throw new RuntimeException("Kunde inte hämta väderdata: " + e.getMessage());
        }
    }

    private WeatherData parseResponse(String json) throws Exception {
        JsonNode root    = objectMapper.readTree(json);
        JsonNode current = root.get("current");

        double temperature   = current.get("temperature_2m").asDouble();
        double feelsLike     = current.get("apparent_temperature").asDouble();
        double windSpeedKmh  = current.get("wind_speed_10m").asDouble();
        double humidity      = current.get("relative_humidity_2m").asDouble();
        double precipitation = current.get("precipitation").asDouble();
        int weatherCode      = current.get("weather_code").asInt();

        double windSpeedMs = windSpeedKmh / 3.6;
        int precipCategory = weatherCodeToPrecipCategory(weatherCode);
        String forecastWarning = detectForecastWarning(root);

        return new WeatherData(temperature, feelsLike, windSpeedMs, humidity, precipitation, precipCategory, forecastWarning);
    }

    private String detectForecastWarning(JsonNode root) {
        try {
            String currentTime = root.get("current").get("time").asText();
            JsonNode times = root.get("hourly").get("time");
            JsonNode codes = root.get("hourly").get("weather_code");

            String currentHour = currentTime.substring(0, 13); // "2024-01-15T14"
            int currentIndex = -1;
            for (int i = 0; i < times.size(); i++) {
                if (times.get(i).asText().startsWith(currentHour)) {
                    currentIndex = i;
                    break;
                }
            }
            if (currentIndex < 0) return null;

            for (int h = 1; h <= 4; h++) {
                int idx = currentIndex + h;
                if (idx >= codes.size()) break;
                int code = codes.get(idx).asInt();
                if (code >= 51) {
                    String timeStr = times.get(idx).asText();
                    int forecastHour = Integer.parseInt(timeStr.substring(11, 13));
                    boolean snow = (code >= 71 && code <= 77) || code == 85 || code == 86;
                    String type = snow ? "Snöfall" : "Regn";
                    return String.format("%s väntas om ca %d timm%s (ca kl %02d:00)",
                        type, h, h == 1 ? "e" : "ar", forecastHour);
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private int weatherCodeToPrecipCategory(int code) {
        if (code == 0 || code <= 3)  return 0; // Klart/molnigt
        if (code == 71 || code == 73 || code == 75 || code == 77 || code == 85 || code == 86) return 1; // Snö
        if (code == 51 || code == 53 || code == 55) return 4; // Duggregn
        if (code == 56 || code == 57) return 6; // Underkylt duggregn
        if (code == 66 || code == 67) return 5; // Underkylt regn
        if (code >= 61) return 3; // Regn / åska
        return 0;
    }
}
