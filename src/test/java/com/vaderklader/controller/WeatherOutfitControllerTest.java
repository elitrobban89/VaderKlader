package com.vaderklader.controller;

import com.vaderklader.model.WeatherData;
import com.vaderklader.service.ClaudeService;
import com.vaderklader.service.OpenMeteoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP-lagertester för WeatherOutfitController: koordinatvalidering,
 * rate limit-headers och -spärr, health med kvotstatus samt felformatet
 * vid tjänstefel. Tjänsterna mockas — inga externa anrop.
 */
@WebMvcTest(WeatherOutfitController.class)
class WeatherOutfitControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private OpenMeteoService openMeteoService;

    @MockBean
    private ClaudeService claudeService;

    private static WeatherData weather() {
        return new WeatherData(15.0, 13.0, 4.0, "Norr", 70, 0.0, 0,
                2.0, false, null, "04:30", "21:45", List.of(), List.of());
    }

    @Test
    void ogiltigaKoordinaterGer400() throws Exception {
        mvc.perform(get("/api/weather-outfit").param("lat", "95").param("lon", "18"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.error").value("Ogiltiga koordinater."));
    }

    @Test
    void lyckatSvarInnehallerForslagVaderOchRateLimitHeaders() throws Exception {
        when(openMeteoService.getWeather(anyDouble(), anyDouble())).thenReturn(weather());
        when(claudeService.getOutfitSuggestion(any(), eq("cykel"))).thenReturn("Ta med regnjacka.");

        mvc.perform(get("/api/weather-outfit")
                .header("X-Forwarded-For", "10.1.1.1")
                .param("lat", "59.33").param("lon", "18.06").param("transport", "cykel"))
           .andExpect(status().isOk())
           .andExpect(header().string("X-RateLimit-Limit", "20"))
           .andExpect(header().string("X-RateLimit-Remaining", "19"))
           .andExpect(jsonPath("$.temperature").value(15.0))
           .andExpect(jsonPath("$.windDirection").value("Norr"))
           .andExpect(jsonPath("$.outfitSuggestion").value("Ta med regnjacka."));
    }

    @Test
    void rateLimitGer429MedRetryAfterEfterTjugoAnrop() throws Exception {
        when(openMeteoService.getWeather(anyDouble(), anyDouble())).thenReturn(weather());
        when(claudeService.getOutfitSuggestion(any(), any())).thenReturn("förslag");

        for (int i = 0; i < 20; i++) {
            mvc.perform(get("/api/weather-outfit")
                    .header("X-Forwarded-For", "10.2.2.2")
                    .param("lat", "59.33").param("lon", "18.06"))
               .andExpect(status().isOk());
        }
        mvc.perform(get("/api/weather-outfit")
                .header("X-Forwarded-For", "10.2.2.2")
                .param("lat", "59.33").param("lon", "18.06"))
           .andExpect(status().isTooManyRequests())
           .andExpect(header().exists("Retry-After"))
           .andExpect(jsonPath("$.retryAfterSeconds").isNumber())
           .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void tjanstefelGer500MedFelmeddelande() throws Exception {
        when(openMeteoService.getWeather(anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("Open-Meteo svarar inte"));

        mvc.perform(get("/api/weather-outfit")
                .header("X-Forwarded-For", "10.3.3.3")
                .param("lat", "59.33").param("lon", "18.06"))
           .andExpect(status().isInternalServerError())
           .andExpect(jsonPath("$.error").value("Open-Meteo svarar inte"));
    }

    @Test
    void healthVisarGroqOkNarKvotenInteArNadd() throws Exception {
        when(claudeService.isQuotaExceeded()).thenReturn(false);

        mvc.perform(get("/api/health"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.status").value("ok"))
           .andExpect(jsonPath("$.groq").value("ok"));
    }

    @Test
    void healthVisarKvotstatusNarGroqkvotenArNadd() throws Exception {
        when(claudeService.isQuotaExceeded()).thenReturn(true);
        when(claudeService.getQuotaResetInfo()).thenReturn("om 42 min");

        mvc.perform(get("/api/health"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.groq").value("quota_exceeded"))
           .andExpect(jsonPath("$.retryIn").value("om 42 min"));
    }
}
