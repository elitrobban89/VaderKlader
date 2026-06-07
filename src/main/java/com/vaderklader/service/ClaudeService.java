package com.vaderklader.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaderklader.model.WeatherData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ClaudeService {

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final long CACHE_TTL_MS = 30 * 60 * 1000L;

    @Value("${groq.api.key}")
    private String apiKey;

    private record CacheEntry(String suggestion, long timestamp) {}

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

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
            cache.put(cacheKey, new CacheEntry(suggestion, System.currentTimeMillis()));
            return suggestion;

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 429) {
                throw new RuntimeException("Dagsgränsen för AI-anrop är nådd. Försök igen om " + parseRetryTime(e.getResponseBodyAsString()) + ".");
            }
            throw new RuntimeException("Kunde inte hämta klädförslag: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException("Kunde inte hämta klädförslag: " + e.getMessage());
        }
    }

    private String buildCacheKey(WeatherData weather, String transport) {
        return Math.round(weather.getTemperature()) + "|" +
               Math.round(weather.getFeelsLike()) + "|" +
               Math.round(weather.getWindSpeed()) + "|" +
               weather.getPrecipitationDescription() + "|" +
               transport.toLowerCase();
    }

    private String parseRetryTime(String body) {
        try {
            Matcher m = Pattern.compile("try again in ([\\d]+m[\\d.]+s|[\\d.]+s)").matcher(body);
            if (!m.find()) return "en stund";
            String t = m.group(1);
            Matcher minMatcher = Pattern.compile("(\\d+)m").matcher(t);
            Matcher secMatcher = Pattern.compile("([\\d.]+)s").matcher(t);
            int minutes = minMatcher.find() ? Integer.parseInt(minMatcher.group(1)) : 0;
            double seconds = secMatcher.find() ? Double.parseDouble(secMatcher.group(1)) : 0;
            int total = (int) Math.ceil(minutes + seconds / 60.0);
            return total <= 1 ? "1 minut" : total + " minuter";
        } catch (Exception e) {
            return "en stund";
        }
    }

    private String buildPrompt(WeatherData weather, String transport) {
        String transportContext = switch (transport.toLowerCase()) {
            case "cykel" -> """
                Användaren cyklar till sitt mål. Tänk på: rörelsefrihet, vindskydd, \
                svettreglering och synlighet (reflexer/ljusa kläder). Undvik alltför löst sittande plagg.""";
            case "buss" -> """
                Användaren åker buss. Tänk på: väntan utomhus vid busshållplatsen (vind, kyla, regn) \
                och att det kan vara varmt inomhus på bussen — lagerklädsel är bra.""";
            case "tåg" -> """
                Användaren åker tåg. Tänk på: gång till/från station, väntan på perrongen \
                och att det är varmt inne på tåget — lagerklädsel är bra.""";
            case "bil" -> """
                Användaren kör bil. Tänk på: kort promenad till/från bilen i väder, \
                och att det är varmt i bilen — ett ytterplagg som lätt kan tas av är bra.""";
            case "gång" -> """
                Användaren går till sitt mål. Tänk på: hela vägen är utomhus, \
                rörelsefrihet är viktig och temperaturen upplevs annorlunda i rörelse. \
                Bekväma skor är ett plus att nämna.""";
            default -> "Användaren reser på ett okänt sätt.";
        };

        String forecastSection = weather.getForecastWarning() != null
            ? "\nKommande väder (prognos): " + weather.getForecastWarning() +
              "\nVIKTIGT: Nämn prognosen EXPLICIT i ditt svar med exakt tid. Rekommendera konkret utifrån situationen, t.ex.:" +
              "\n- Paraply om användaren är ute kortare stunder" +
              "\n- Regnkappa/regnställ om användaren rör sig mycket utomhus (cykel, gång)" +
              "\n- Vattentäta skor eller gummistövlar om kraftigt regn väntas" +
              "\nAvsluta alltid med en mening som börjar med: \"OBS: [typ av nederbörd] väntas [tid] — ta med ...\""
            : "";

        return String.format("""
            Du är en klädrådgivare i Sverige. Baserat på nedanstående väderförhållanden och färdmedel, \
            ge ett konkret och praktiskt klädförslag på svenska i 2-3 meningar. \
            Var specifik om plaggen (t.ex. "tunn t-shirt", "lätt fleecejacka", "regnkappa", "vinterjacka"). \
            Inkludera också tips om accessoarer om det är relevant (mössa, handskar, paraply, solglasögon).

            Väderdata just nu:
            %s
            %s

            Färdmedel: %s
            %s

            Ge endast klädförslaget, inga inledande fraser som "Baserat på väderdata...".
            """, weather.toPromptDescription(), forecastSection, transport, transportContext);
    }
}
