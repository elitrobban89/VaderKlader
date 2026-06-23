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
    private static final String MODEL_PRIMARY = "llama-3.3-70b-versatile";
    private static final String MODEL_FALLBACK = "llama-3.1-8b-instant";
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

        if (isQuotaExceeded()) {
            return buildFallbackSuggestion(weather, transport);
        }

        String prompt = buildPrompt(weather, transport);

        try {
            String suggestion = callGroq(MODEL_PRIMARY, prompt);
            quotaExceededUntil = 0;
            evictIfNeeded();
            cache.put(cacheKey, new CacheEntry(suggestion, System.currentTimeMillis()));
            return suggestion;

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                quotaExceededUntil = System.currentTimeMillis() + parseRetryMs(e.getResponseBodyAsString());
                try {
                    String suggestion = callGroq(MODEL_FALLBACK, prompt);
                    evictIfNeeded();
                    cache.put(cacheKey, new CacheEntry(suggestion, System.currentTimeMillis()));
                    return suggestion;
                } catch (Exception ignored) {}
            }
            String fallback = buildFallbackSuggestion(weather, transport);
            cache.put(cacheKey, new CacheEntry(fallback, System.currentTimeMillis()));
            return fallback;
        } catch (Exception e) {
            return buildFallbackSuggestion(weather, transport);
        }
    }

    private String callGroq(String model, String prompt) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
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
        return responseJson.get("choices").get(0).get("message").get("content").asText();
    }

    private String buildFallbackSuggestion(WeatherData weather, String transport) {
        double t = weather.getFeelsLike();
        String precip = weather.getPrecipitationDescription();
        double wind = weather.getWindSpeed();
        boolean dark = weather.isDark();
        boolean isRain = precip.contains("regn");
        boolean isSnow = precip.contains("snö") || precip.contains("Snö");
        boolean isThunder = precip.contains("ska");

        StringBuilder sb = new StringBuilder();
        if (t < -10) sb.append("Extremt kallt — termounderställ, tjock fleece, varm vinterjacka, mössa och vantar är ett måste.");
        else if (t < 0) sb.append("Kall dag — klä dig i lager med termounderställ och tjock vinterjacka, mössa och vantar.");
        else if (t < 5) sb.append("Kyligt ute — fleece eller tjock tröja under en varm jacka, mössa och handskar rekommenderas.");
        else if (t < 10) sb.append("Svalare väder — ta på dig en mellantjock jacka och tänk på lagerklädsel.");
        else if (t < 15) sb.append("Lite kylig luft — en lättare jacka räcker, men ha ett extra lager redo.");
        else if (t < 20) sb.append("Behagligt väder — en tunn jacka eller hoodie räcker bra.");
        else if (t < 26) sb.append("Varmt och skönt — tunna kläder som t-shirt och lätta byxor fungerar fint.");
        else sb.append("Riktigt varmt ute — kortärmat och shorts passar, drick gärna extra vatten.");

        if (isThunder) sb.append(" Åska väntas — ta med vattentät jacka och undvik att vara ute i onödan.");
        else if (isRain) sb.append(" Det regnar — ta med regnjacka eller paraply.");
        else if (isSnow) sb.append(" Snöfall — vattentäta skor och halkfria sulor är viktigt.");

        if (wind > 10) sb.append(" Kraftig vind — välj ett tätt vindskyddande ytterplagg.");
        else if (wind > 6 && t < 15) sb.append(" Det blåser en del — ett vindskydd gör skillnad.");

        if (dark) sb.append(" Det är mörkt ute — reflexer rekommenderas.");

        if ("cykel".equalsIgnoreCase(transport) && t < 10) sb.append(" Med cykel: handskar och cykelmössa under hjälmen.");
        else if ("cykel".equalsIgnoreCase(transport) && isRain) sb.append(" Med cykel: vattentäta leggings och regnponcho hjälper.");

        return sb.toString();
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
               weather.getWindDirection() + "|" +
               weather.getPrecipitationDescription() + "|" +
               weather.isDark() + "|" +
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
            case "cykel" -> "Cykel: rörelsefrihet, vindskydd, svettreglering.";
            case "buss"  -> "Buss: väntan utomhus vid hållplats, varmt inomhus — lagerklädsel.";
            case "tåg"   -> "Tåg: gång till/från station, varmt inne — lagerklädsel.";
            case "bil"   -> "Bil: kort promenad i väder, varmt i bilen — lättavtagbart ytterplagg.";
            case "gång"  -> "Gång: hela vägen utomhus, rörelsefrihet, bekväma skor.";
            default      -> "";
        };

        String forecastSection = weather.getForecastWarning() != null
            ? "\nKommande väder: " + weather.getForecastWarning() + " — nämn detta naturligt i klädråden (t.ex. rekommendera regnjacka) men skriv INTE en separat OBS-rad."
            : "";

        String[] months = {"januari","februari","mars","april","maj","juni",
                           "juli","augusti","september","oktober","november","december"};
        String month = months[LocalDate.now().getMonthValue() - 1];

        return String.format("Månad: %s\nVäderdata: %s%s\nFärdmedel: %s — %s",
            month, weather.toPromptDescription(), forecastSection, transport, transportContext);
    }
}
