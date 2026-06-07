package com.vaderklader.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaderklader.model.WeatherData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ClaudeService {

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final long CACHE_TTL_MS = 30 * 60 * 1000L;
    private static final int MAX_CACHE_SIZE = 500;

    @Value("${groq.api.key}")
    private String apiKey;

    private record CacheEntry(String suggestion, long timestamp) {}

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private volatile long quotaExceededUntil = 0;

    public ClaudeService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(factory);
    }

    public String getOutfitSuggestion(WeatherData weather, String transport) {
        String cacheKey = buildCacheKey(weather, transport);
        CacheEntry entry = cache.get(cacheKey);
        if (entry != null && System.currentTimeMillis() - entry.timestamp() < CACHE_TTL_MS) {
            return entry.suggestion();
        }

        String prompt = buildPrompt(weather, transport);

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", "llama-3.3-70b-versatile");
            body.put("temperature", 0.4);
            body.put("max_tokens", 200);
            ArrayNode messages = body.putArray("messages");
            ObjectNode system = messages.addObject();
            system.put("role", "system");
            system.put("content", "Du är en praktisk klädrådgivare i Sverige. Svara alltid på svenska i 2-3 meningar. Var konkret och specifik om plagg och accessoarer.");
            ObjectNode message = messages.addObject();
            message.put("role", "user");
            message.put("content", prompt);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(GROQ_URL, request, String.class);

            JsonNode responseJson = objectMapper.readTree(response.getBody());
            String suggestion = responseJson.get("choices").get(0).get("message").get("content").asText();
            evictIfNeeded();
            cache.put(cacheKey, new CacheEntry(suggestion, System.currentTimeMillis()));
            return suggestion;

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                String body = e.getResponseBodyAsString();
                quotaExceededUntil = System.currentTimeMillis() + parseRetryMs(body);
                throw new RuntimeException("Dagsgränsen för AI-anrop är nådd. Försök igen om " + parseRetryTime(body) + ".");
            }
            throw new RuntimeException("Kunde inte hämta klädförslag: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Kunde inte hämta klädförslag: " + e.getMessage());
        }
    }

    private void evictIfNeeded() {
        if (cache.size() < MAX_CACHE_SIZE) return;
        long cutoff = cache.values().stream()
                .mapToLong(CacheEntry::timestamp)
                .sorted()
                .skip(cache.size() / 2)
                .findFirst()
                .orElse(0L);
        cache.values().removeIf(e -> e.timestamp() < cutoff);
    }

    private String buildCacheKey(WeatherData weather, String transport) {
        return Math.round(weather.getTemperature()) + "|" +
               Math.round(weather.getFeelsLike()) + "|" +
               Math.round(weather.getWindSpeed()) + "|" +
               weather.getPrecipitationDescription() + "|" +
               transport.toLowerCase();
    }

    public boolean isQuotaExceeded() {
        return System.currentTimeMillis() < quotaExceededUntil;
    }

    public String getQuotaResetInfo() {
        long ms = quotaExceededUntil - System.currentTimeMillis();
        if (ms <= 0) return null;
        int minutes = (int) Math.ceil(ms / 60_000.0);
        return minutes <= 1 ? "1 minut" : minutes + " minuter";
    }

    private long parseRetryMs(String body) {
        try {
            Matcher m = Pattern.compile("try again in ([\\d]+m[\\d.]+s|[\\d.]+s)").matcher(body);
            if (!m.find()) return 60_000L;
            String t = m.group(1);
            Matcher minMatcher = Pattern.compile("(\\d+)m").matcher(t);
            Matcher secMatcher = Pattern.compile("([\\d.]+)s").matcher(t);
            int minutes = minMatcher.find() ? Integer.parseInt(minMatcher.group(1)) : 0;
            double seconds = secMatcher.find() ? Double.parseDouble(secMatcher.group(1)) : 0;
            return (long) ((minutes * 60 + seconds) * 1000);
        } catch (Exception e) {
            return 60_000L;
        }
    }

    private String parseRetryTime(String body) {
        long ms = parseRetryMs(body);
        int total = (int) Math.ceil(ms / 60_000.0);
        return total <= 1 ? "1 minut" : total + " minuter";
    }

    private String buildPrompt(WeatherData weather, String transport) {
        String transportContext = switch (transport.toLowerCase()) {
            case "cykel" -> "Cykel: rörelsefrihet, vindskydd, svettreglering, reflexer vid dålig sikt.";
            case "buss"  -> "Buss: väntan utomhus vid hållplats, varmt inomhus — lagerklädsel.";
            case "tåg"   -> "Tåg: gång till/från station, varmt inne — lagerklädsel.";
            case "bil"   -> "Bil: kort promenad i väder, varmt i bilen — lättavtagbart ytterplagg.";
            case "gång"  -> "Gång: hela vägen utomhus, rörelsefrihet, bekväma skor.";
            default      -> "";
        };

        String forecastSection = weather.getForecastWarning() != null
            ? "\nPrognos: " + weather.getForecastWarning() +
              "\nNämn detta EXPLICIT. Vid åska/hagel: sök skydd. Avsluta med: \"OBS: [nederbörd] väntas [tid] — ta med ...\""
            : "";

        String[] months = {"januari","februari","mars","april","maj","juni",
                           "juli","augusti","september","oktober","november","december"};
        String month = months[LocalDate.now().getMonthValue() - 1];

        return String.format("Månad: %s\nVäderdata: %s%s\nFärdmedel: %s — %s",
            month, weather.toPromptDescription(), forecastSection, transport, transportContext);
    }
}
