package com.vaderklader.service;

import com.vaderklader.model.WeatherData;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tester för reserven MET Norway: att ett riktigt Locationforecast-svar (Stockholm,
 * hämtat 2026-10-05 kl 21 UTC) blir Open-Meteo-JSON som OpenMeteoService.parseResponse
 * läser — samma väg som i drift. Inga HTTP-anrop.
 *
 * @author Robert Andersson Kopler
 */
class MetNorwayServiceTest {

    private final MetNorwayService met = new MetNorwayService();
    private final OpenMeteoService openMeteo = new OpenMeteoService(met);

    // 23:30 svensk tid — mitt i andra posten (22:00 UTC) i fixturen
    private static final Instant NOW = Instant.parse("2026-10-05T21:30:00Z");

    private static String fixture() throws IOException {
        try (InputStream in = MetNorwayServiceTest.class.getResourceAsStream("/met-locationforecast-stockholm.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private WeatherData parsed() throws Exception {
        return openMeteo.parseResponse(met.toOpenMeteoJson(fixture(), 59.3, 18.1, NOW));
    }

    @Test
    void aktuellaVardenKommerUrInnevarandeTimme() throws Exception {
        WeatherData d = parsed();
        assertThat(d.getTemperature()).isEqualTo(16.7);  // posten 21:00 UTC
        assertThat(d.getFeelsLike()).isEqualTo(16.7);    // MET:s apparent_air_temperature
        assertThat(d.getWindSpeed()).isEqualTo(9.1);     // m/s → km/h → tillbaka till m/s
        assertThat(d.getWindDirection()).isEqualTo("Väst"); // 255°
        assertThat(d.isDark()).isTrue();                 // 23:30 i oktober
    }

    @Test
    void timprognosenBorjarTimmenEfterAktuell() throws Exception {
        WeatherData d = parsed();
        assertThat(d.getHourlyForecast()).hasSize(6);
        assertThat(d.getHourlyForecast().get(0).hoursFromNow()).isEqualTo(1);
    }

    @Test
    void dygnsprognosenGerSjuDagarMedSvenskaNamn() throws Exception {
        WeatherData d = parsed();
        assertThat(d.getDailyForecast()).hasSize(7);
        assertThat(d.getDailyForecast().get(0).dayName()).isEqualTo("Idag");
        assertThat(d.getDailyForecast().get(1).dayName()).isEqualTo("Imorgon");
        d.getDailyForecast().forEach(day -> assertThat(day.tempMax()).isGreaterThanOrEqualTo(day.tempMin()));
    }

    @Test
    void soltiderStammerForStockholm() {
        // Facit (timeanddate.com): 21 juni 03:30/22:08, 21 december 08:43/14:48
        String[] sommar = MetNorwayService.sunTimes(LocalDate.of(2026, 6, 21), 59.33, 18.07, ZoneId.of("Europe/Stockholm"));
        assertThat(sommar[0]).isBetween("2026-06-21T03:27", "2026-06-21T03:34");
        assertThat(sommar[1]).isBetween("2026-06-21T22:04", "2026-06-21T22:11");
        String[] vinter = MetNorwayService.sunTimes(LocalDate.of(2026, 12, 21), 59.33, 18.07, ZoneId.of("Europe/Stockholm"));
        assertThat(vinter[0]).isBetween("2026-12-21T08:40", "2026-12-21T08:47");
        assertThat(vinter[1]).isBetween("2026-12-21T14:45", "2026-12-21T14:52");
    }

    @Test
    void midnattssolOchPolarnattGerRattMorker() {
        ZoneId z = ZoneId.of("Europe/Stockholm");
        String[] juni = MetNorwayService.sunTimes(LocalDate.of(2026, 6, 21), 68.35, 18.83, z);     // Abisko
        assertThat(juni).containsExactly("2026-06-21T00:00", "2026-06-21T23:59");
        String[] december = MetNorwayService.sunTimes(LocalDate.of(2026, 12, 21), 68.35, 18.83, z);
        assertThat(december).containsExactly("2026-12-21T23:59", "2026-12-21T00:00");
    }

    @Test
    void symbolkoderMappasTillWmo() {
        assertThat(MetNorwayService.wmoCode("clearsky_day")).isZero();
        assertThat(MetNorwayService.wmoCode("fair_night")).isEqualTo(1);
        assertThat(MetNorwayService.wmoCode("cloudy")).isEqualTo(3);
        assertThat(MetNorwayService.wmoCode("lightrainshowers_polartwilight")).isEqualTo(80);
        assertThat(MetNorwayService.wmoCode("heavysnow")).isEqualTo(75);
        assertThat(MetNorwayService.wmoCode("rainandthunder")).isEqualTo(95);
        assertThat(MetNorwayService.wmoCode("nagot_okant")).isEqualTo(3);
    }

    @Test
    void sverigeFarStockholmstidUtomlandsLongitudzon() {
        assertThat(MetNorwayService.zoneFor(59.3, 18.1)).isEqualTo(ZoneId.of("Europe/Stockholm"));
        assertThat(MetNorwayService.zoneFor(40.7, -74.0).getRules().getOffset(NOW).getTotalSeconds()).isEqualTo(-5 * 3600);
    }
}
