package com.vaderklader.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaderklader.model.WeatherData;
import com.vaderklader.model.WeatherOutfitResponse;
import com.vaderklader.service.ClaudeService;
import com.vaderklader.service.SmhiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class WeatherOutfitController {

    private final SmhiService smhiService;
    private final ClaudeService claudeService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WeatherOutfitController(SmhiService smhiService, ClaudeService claudeService) {
        this.smhiService = smhiService;
        this.claudeService = claudeService;
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/weather-outfit")
    public ResponseEntity<?> getWeatherOutfit(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(required = false, defaultValue = "okänt") String transport) {
        try {
            WeatherData weather = smhiService.getWeather(lat, lon);
            String suggestion = claudeService.getOutfitSuggestion(weather, transport);
            return ResponseEntity.ok(new WeatherOutfitResponse(lat, lon, weather, suggestion));
        } catch (Exception e) {
            ObjectNode err = objectMapper.createObjectNode();
            err.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(err);
        }
    }
}
