package com.vaderklader.controller;

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

    public WeatherOutfitController(SmhiService smhiService, ClaudeService claudeService) {
        this.smhiService = smhiService;
        this.claudeService = claudeService;
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
            return ResponseEntity.internalServerError().body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }
}
