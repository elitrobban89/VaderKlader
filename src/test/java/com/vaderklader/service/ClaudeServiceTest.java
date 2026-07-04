package com.vaderklader.service;

import com.vaderklader.model.WeatherData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tester för ClaudeServices rena logik: regelbaserad fallback när Groq inte
 * kan nås, cachenyckeln, 429-retry-parsning och promptbygget. Inga HTTP-anrop
 * och ingen Mockito — allt byggs med riktiga WeatherData-objekt.
 */
class ClaudeServiceTest {

    private final ClaudeService service = new ClaudeService();

    private static WeatherData weather(double temp, double feelsLike, double wind,
                                       int precipCategory, boolean dark, String warning) {
        return new WeatherData(temp, feelsLike, wind, "Norr", 70, 0.0, precipCategory,
                2.0, dark, warning, "04:30", "21:45", List.of(), List.of());
    }

    // --- buildFallbackSuggestion ---

    @Test
    void fallbackKallRegnigCykeldagFarAllaVarningar() {
        WeatherData w = weather(4, 2, 11, 3, true, null); // kallt, regn, hård vind, mörkt
        String s = service.buildFallbackSuggestion(w, "cykel");
        assertThat(s)
                .contains("Kyligt")
                .contains("regnjacka")
                .contains("vind")
                .contains("reflexer")
                .contains("cykel");
    }

    @Test
    void fallbackVarmSommardagArKort() {
        WeatherData w = weather(27, 28, 2, 0, false, null);
        String s = service.buildFallbackSuggestion(w, "gång");
        assertThat(s)
                .contains("varmt")
                .doesNotContain("regnjacka")
                .doesNotContain("reflexer");
    }

    @Test
    void fallbackSnoGerHalkvarning() {
        WeatherData w = weather(-2, -5, 3, 1, false, null);
        String s = service.buildFallbackSuggestion(w, "buss");
        assertThat(s).contains("halkfria");
    }

    // --- buildCacheKey ---

    @Test
    void cachenyckelnAvrundarOchNormaliserarTransport() {
        WeatherData w1 = weather(10.2, 8.4, 3.1, 0, false, null);
        WeatherData w2 = weather(10.4, 8.1, 3.3, 0, false, null); // avrundas till samma
        assertThat(service.buildCacheKey(w1, "Cykel")).isEqualTo(service.buildCacheKey(w2, "cykel"));
    }

    @Test
    void olikaVaderGerOlikaCachenycklar() {
        WeatherData warm = weather(20, 20, 3, 0, false, null);
        WeatherData cold = weather(2, -1, 3, 0, false, null);
        assertThat(service.buildCacheKey(warm, "bil")).isNotEqualTo(service.buildCacheKey(cold, "bil"));
    }

    // --- parseRetryMs ---

    @Test
    void retrytidMedMinuterOchSekunderParsas() {
        assertThat(service.parseRetryMs("Rate limit reached, try again in 2m59.56s"))
                .isEqualTo((long) ((2 * 60 + 59.56) * 1000));
    }

    @Test
    void retrytidMedBaraSekunderParsas() {
        assertThat(service.parseRetryMs("try again in 30s")).isEqualTo(30_000L);
    }

    @Test
    void oparsbarKroppGerEnMinutDefault() {
        assertThat(service.parseRetryMs("<html>502</html>")).isEqualTo(60_000L);
    }

    // --- buildPrompt ---

    @Test
    void promptInnehallerVaderTransportOchManad() {
        WeatherData w = weather(15, 13, 4, 0, false, null);
        String p = service.buildPrompt(w, "cykel");
        assertThat(p)
                .contains("Månad:")
                .contains("Temp: 15")      // decimalseparatorn är lokalberoende (15,0 / 15.0)
                .contains("°C")
                .contains("Färdmedel: cykel")
                .contains("vindskydd");
    }

    @Test
    void prognosvarningVavsInIPrompten() {
        WeatherData w = weather(15, 13, 4, 0, false, "Regn väntas om ca 2 timmar (ca kl 14:00)");
        String p = service.buildPrompt(w, "gång");
        assertThat(p).contains("Kommande väder: Regn väntas om ca 2 timmar");
    }

    // --- kvot-status ---

    @Test
    void ingenKvotsparrFranBorjan() {
        assertThat(service.isQuotaExceeded()).isFalse();
        assertThat(service.getQuotaResetInfo()).isNull();
    }
}
