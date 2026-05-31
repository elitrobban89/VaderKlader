package com.vaderklader.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaderklader.model.WeatherData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class ClaudeService {

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";

    @Value("${groq.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String getOutfitSuggestion(WeatherData weather, String transport) {
        String prompt = buildPrompt(weather, transport);

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", "llama3-8b-8192");
            ArrayNode messages = body.putArray("messages");
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

        } catch (Exception e) {
            throw new RuntimeException("Kunde inte hämta klädförslag: " + e.getMessage());
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
            default -> "Användaren reser på ett okänt sätt.";
        };

        return String.format("""
            Du är en klädrådgivare i Sverige. Baserat på nedanstående väderförhållanden och färdmedel, \
            ge ett konkret och praktiskt klädförslag på svenska i 2-3 meningar. \
            Var specifik om plaggen (t.ex. "tunn t-shirt", "lätt fleecejacka", "regnkappa", "vinterjacka"). \
            Inkludera också tips om accessoarer om det är relevant (mössa, handskar, paraply, solglasögon).

            Väderdata just nu:
            %s

            Färdmedel: %s
            %s

            Ge endast klädförslaget, inga inledande fraser som "Baserat på väderdata...".
            """, weather.toPromptDescription(), transport, transportContext);
    }
}
