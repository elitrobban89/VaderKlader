package com.vaderklader.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaderklader.model.WeatherData;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class SmhiService {

    private static final String SMHI_URL =
        "https://opendata-download-metfcst.smhi.se/api/category/pmp3g/version/2/geotype/point/lon/%s/lat/%s/data.json";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WeatherData getWeather(double lat, double lon) {
        String url = String.format(SMHI_URL,
            String.format("%.4f", lon).replace(",", "."),
            String.format("%.4f", lat).replace(",", "."));

        try {
            String json = restTemplate.getForObject(url, String.class);
            return parseSmhiResponse(json);
        } catch (Exception e) {
            throw new RuntimeException("Kunde inte hämta väderdata från SMHI: " + e.getMessage());
        }
    }

    private WeatherData parseSmhiResponse(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode parameters = root.get("timeSeries").get(0).get("parameters");

        double temperature = 0, windSpeed = 0, humidity = 0, precipitation = 0;
        int precipCategory = 0;

        for (JsonNode param : parameters) {
            String name = param.get("name").asText();
            double value = param.get("values").get(0).asDouble();
            switch (name) {
                case "t"     -> temperature = value;
                case "ws"    -> windSpeed = value;
                case "r"     -> humidity = value;
                case "pmean" -> precipitation = value;
                case "pcat"  -> precipCategory = (int) value;
            }
        }

        return new WeatherData(temperature, windSpeed, humidity, precipitation, precipCategory);
    }
}
