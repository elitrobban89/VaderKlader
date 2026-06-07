package com.vaderklader.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaderklader.model.WeatherData;
import com.vaderklader.model.WeatherOutfitResponse;
import com.vaderklader.service.ClaudeService;
import com.vaderklader.service.OpenMeteoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class WeatherOutfitController {

    private final OpenMeteoService openMeteoService;
    private final ClaudeService claudeService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, List<Long>> ipRequestLog = new ConcurrentHashMap<>();
    private static final int MAX_REQUESTS_PER_HOUR = 10;

    public WeatherOutfitController(OpenMeteoService openMeteoService, ClaudeService claudeService) {
        this.openMeteoService = openMeteoService;
        this.claudeService = claudeService;
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        if (claudeService.isQuotaExceeded()) {
            return ResponseEntity.ok(Map.of(
                "status", "ok",
                "groq", "quota_exceeded",
                "retryIn", claudeService.getQuotaResetInfo()
            ));
        }
        return ResponseEntity.ok(Map.of("status", "ok", "groq", "ok"));
    }

    @GetMapping("/weather-outfit")
    public ResponseEntity<?> getWeatherOutfit(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(required = false, defaultValue = "okänt") String transport,
            HttpServletRequest request) {
        String ip = getClientIp(request);
        if (isRateLimited(ip)) {
            return ResponseEntity.status(429).body(Map.of(
                    "error", "För många förfrågningar från din IP. Försök igen om en stund."
            ));
        }
        try {
            WeatherData weather = openMeteoService.getWeather(lat, lon);
            String suggestion = claudeService.getOutfitSuggestion(weather, transport);
            return ResponseEntity.ok(new WeatherOutfitResponse(lat, lon, weather, suggestion));
        } catch (Exception e) {
            ObjectNode err = objectMapper.createObjectNode();
            err.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(err);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isRateLimited(String ip) {
        long now = System.currentTimeMillis();
        long windowStart = now - 3_600_000;
        ipRequestLog.compute(ip, (k, times) -> {
            List<Long> updated = (times == null) ? new ArrayList<>() : times;
            updated.removeIf(t -> t < windowStart);
            updated.add(now);
            return updated;
        });
        return ipRequestLog.get(ip).size() > MAX_REQUESTS_PER_HOUR;
    }
}
