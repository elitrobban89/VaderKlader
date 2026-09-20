package com.vaderklader.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaderklader.model.WeatherData;
import com.vaderklader.model.WeatherOutfitResponse;
import com.vaderklader.service.ClaudeService;
import com.vaderklader.service.OpenMeteoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** @author Robert Andersson Kopler */
@RestController
@RequestMapping("/api")
public class WeatherOutfitController {

    private final OpenMeteoService openMeteoService;
    private final ClaudeService claudeService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, List<Long>> ipRequestLog = new ConcurrentHashMap<>();
    private static final int MAX_REQUESTS_PER_HOUR = 20;

    // Fylls av Maven vid bygget (@project.version@) respektive av Render vid deploy
    // (RENDER_GIT_COMMIT/RENDER_GIT_BRANCH). Lokalt är de två sistnämnda tomma.
    @Value("${app.version:unknown}")
    private String appVersion;

    @Value("${app.commit:}")
    private String appCommit;

    @Value("${app.branch:}")
    private String appBranch;

    private final Instant startedAt = Instant.now();

    public WeatherOutfitController(OpenMeteoService openMeteoService, ClaudeService claudeService) {
        this.openMeteoService = openMeteoService;
        this.claudeService = claudeService;
    }

    /**
     * Vilken kod som faktiskt kör — svarar på "hann deployen ut?" utan Render-dashboarden.
     *
     * <p>Tillkom 2026-09-17. Groq avvecklade qwen3.6-27b, som var den hårdkodade fallbacken
     * här, och när bytet till 3.8 var pushat gick det inte att se utifrån om tjänsten kört
     * igång den nya koden: {@code /api/health} svarar {@code groq: ok}, men det betyder bara
     * att kvoten inte är slut och säger ingenting om vare sig commit eller modellnamn.
     * <b>Därför bär svaret också modellkedjan</b> — då besvarar ETT anrop båda frågorna, och
     * det var precis de två som inte gick att besvara den dagen.
     *
     * <p>{@code uptimeSeconds} avslöjar dessutom spindown: tjänsten ligger på gratisnivån, så
     * en låg siffra betyder att instansen nyss startat om — inte att något är fel.
     */
    @GetMapping("/version")
    public ResponseEntity<?> version() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", appVersion);
        out.put("commit", appCommit.isBlank() ? "unknown"
                : appCommit.substring(0, Math.min(7, appCommit.length())));
        out.put("commitFull", appCommit.isBlank() ? "unknown" : appCommit);
        out.put("branch", appBranch.isBlank() ? "local" : appBranch);
        out.put("models", ClaudeService.models());
        out.put("startedAt", startedAt.toString());
        out.put("uptimeSeconds", Instant.now().getEpochSecond() - startedAt.getEpochSecond());
        return ResponseEntity.ok(out);
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
