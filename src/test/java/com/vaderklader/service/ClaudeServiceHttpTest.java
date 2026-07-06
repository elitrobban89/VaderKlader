package com.vaderklader.service;

import com.sun.net.httpserver.HttpServer;
import com.vaderklader.model.WeatherData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP-felvägstester för ClaudeService: 429 med kvotspärr + modellfallback,
 * trasigt JSON och lyckat svar. Tjänsten pekas mot en lokal stubbserver via
 * groq.api.url — inga externa anrop. Ny tjänstinstans per test (kvotspärren
 * och cachen är instanstillstånd).
 */
class ClaudeServiceHttpTest {

    private HttpServer server;
    private ClaudeService service;
    private final AtomicInteger requests = new AtomicInteger();

    // Sätts per test: statuskod och kropp för varje anrop i tur och ordning.
    private volatile int[] statuses = {200};
    private volatile String[] bodies = {"{}"};

    @BeforeEach
    void setUp() throws Exception {
        requests.set(0);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            int i = Math.min(requests.getAndIncrement(), statuses.length - 1);
            byte[] bytes = bodies[i].getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(statuses[i], bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();

        service = new ClaudeService();
        ReflectionTestUtils.setField(service, "apiKey", "test-nyckel");
        ReflectionTestUtils.setField(service, "groqUrl",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private static WeatherData weather() {
        return new WeatherData(15.0, 13.0, 4.0, "Norr", 70, 0.0, 0,
                2.0, false, null, "04:30", "21:45", List.of(), List.of());
    }

    private static String ok(String content) {
        return "{\"choices\":[{\"message\":{\"content\":\"" + content + "\"}}]}";
    }

    @Test
    void lyckatSvarAnvandsOchKvotenNollstalls() {
        statuses = new int[]{200};
        bodies = new String[]{ok("Ta med regnjacka.")};

        assertThat(service.getOutfitSuggestion(weather(), "cykel")).isEqualTo("Ta med regnjacka.");
        assertThat(service.isQuotaExceeded()).isFalse();
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void primar429FallerTillbakaPaAndraModellen() {
        statuses = new int[]{429, 200};
        bodies = new String[]{
                "{\"error\":{\"message\":\"Rate limit reached, try again in 2m0s\"}}",
                ok("Fallbackmodellens forslag.")};

        assertThat(service.getOutfitSuggestion(weather(), "cykel"))
                .isEqualTo("Fallbackmodellens forslag.");
        assertThat(requests.get()).isEqualTo(2);
        // Kvotspärren sattes av primärens 429 — health kan visa retryIn
        assertThat(service.isQuotaExceeded()).isTrue();
        assertThat(service.getQuotaResetInfo()).isNotBlank();
    }

    @Test
    void bada429GerRegelbaseradFallback() {
        statuses = new int[]{429, 429};
        bodies = new String[]{
                "{\"error\":{\"message\":\"try again in 30s\"}}",
                "{\"error\":{\"message\":\"try again in 30s\"}}"};

        String s = service.getOutfitSuggestion(weather(), "cykel");
        assertThat(s).isNotBlank();
        assertThat(service.isQuotaExceeded()).isTrue();
    }

    @Test
    void trasigtJsonGerRegelbaseradFallbackUtanKvotsparr() {
        statuses = new int[]{200};
        bodies = new String[]{"<html>det har ar inte json</html>"};

        String s = service.getOutfitSuggestion(weather(), "gång");
        assertThat(s).isNotBlank();
        assertThat(service.isQuotaExceeded()).isFalse();
    }
}
