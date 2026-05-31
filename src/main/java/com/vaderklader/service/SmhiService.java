package com.vaderklader.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaderklader.model.WeatherData;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class SmhiService {

    private static final String OPEN_METEO_URL =
        "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s&current=temperature_2m,wind_speed_10m,relative_humidity_2m,precipitation,weather_code";

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
        JsonNode current = objectMapper.readTree(json).get("current");

        double temperature  = current.get("temperature_2m").asDouble();
        double windSpeedKmh = current.get("wind_speed_10m").asDouble();
        double humidity     = current.get("relative_humidity_2m").asDouble();
        double precipitation = current.get("precipitation").asDouble();
        int weatherCode     = current.get("weather_code").asInt();

        double windSpeedMs = windSpeedKmh / 3.6;
        int precipCategory = weatherCodeToPrecipCategory(weatherCode);

        return new WeatherData(temperature, windSpeedMs, humidity, precipitation, precipCategory);
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
