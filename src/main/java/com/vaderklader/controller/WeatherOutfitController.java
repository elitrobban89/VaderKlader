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
    private static final int MAX_REQUESTS_PER_HOUR = 20;

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

        if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ogiltiga koordinater."));
        }

        String ip = getClientIp(request);
        int remaining = addAndGetRemaining(ip);
        if (remaining < 0) {
            long retryAfterSeconds = getRetryAfterSeconds(ip);
            return ResponseEntity.status(429)
                    .header("Retry-After", String.valueOf(retryAfterSeconds))
                    .body(Map.of(
                            "error", "För många förfrågningar från din IP. Försök igen om en stund.",
                            "retryAfterSeconds", retryAfterSeconds
                    ));
        }

        try {
            WeatherData weather = openMeteoService.getWeather(lat, lon);
            String suggestion = claudeService.getOutfitSuggestion(weather, transport);
            return ResponseEntity.ok()
                    .header("X-RateLimit-Remaining", String.valueOf(remaining))
                    .header("X-RateLimit-Limit", String.valueOf(MAX_REQUESTS_PER_HOUR))
                    .body(new WeatherOutfitResponse(lat, lon, weather, suggestion));
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

    private int addAndGetRemaining(String ip) {
        long now = System.currentTimeMillis();
        long windowStart = now - 3_600_000;
        ipRequestLog.compute(ip, (k, times) -> {
            List<Long> updated = (times == null) ? new ArrayList<>() : times;
            updated.removeIf(t -> t < windowStart);
            updated.add(now);
            return updated;
        });
        return MAX_REQUESTS_PER_HOUR - ipRequestLog.get(ip).size();
    }

    private long getRetryAfterSeconds(String ip) {
        List<Long> times = ipRequestLog.get(ip);
        if (times == null || times.isEmpty()) return 60L;
        long retryAfterMs = (times.get(0) + 3_600_000) - System.currentTimeMillis();
        return Math.max(1L, (long) Math.ceil(retryAfterMs / 1000.0));
    }
}
