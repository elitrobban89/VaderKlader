package com.vaderklader.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reserv när Open-Meteo inte svarar: MET Norways Locationforecast (samma data som yr.no).
 *
 * Open-Meteos gratisnivå har en dygnskvot per IP, och Renders gratistjänster delar
 * utgående IP med andra kunder — kvoten kan alltså vara slut utan att vi gjort många
 * anrop ("Daily API request limit exceeded"). MET har ingen dygnskvot och ingen nyckel,
 * bara krav på en User-Agent som identifierar appen.
 *
 * Svaret görs om till Open-Meteos format, så att OpenMeteoService.parseResponse och dess
 * prov gäller oförändrade för båda källorna.
 *
 * @author Robert Andersson Kopler
 */
@Service
public class MetNorwayService {

    private static final String MET_URL =
        "https://api.met.no/weatherapi/locationforecast/2.0/complete?lat=%s&lon=%s";

    // MET nekar anrop utan en User-Agent som pekar ut appen (403)
    private static final String USER_AGENT = "VaderKlader/1.0 (+https://elitrobban.se)";

    private static final DateTimeFormatter LOCAL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final int HOURS = 12; // som forecast_hours=12 i Open-Meteo-anropet
    private static final int DAYS = 7;

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MetNorwayService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** Hämtar prognosen från MET och returnerar den som Open-Meteo-JSON. */
    public String fetchAsOpenMeteoJson(double lat, double lon) {
        String url = String.format(MET_URL, String.valueOf(lat), String.valueOf(lon));
        String json = restClient.get().uri(url)
            .header("User-Agent", USER_AGENT)
            .retrieve().body(String.class);
        return toOpenMeteoJson(json, lat, lon, Instant.now());
    }

    String toOpenMeteoJson(String metJson, double lat, double lon, Instant now) {
        JsonNode series = objectMapper.readTree(metJson).path("properties").path("timeseries");
        if (series.isEmpty()) throw new IllegalStateException("MET-svaret saknar tidsserie");
        ZoneId zone = zoneFor(lat, lon);

        // MET börjar på innevarande hel timme (UTC) — aktuell post = sista som inte ligger i framtiden
        int cur = 0;
        for (int i = 0; i < series.size(); i++) {
            if (Instant.parse(series.get(i).path("time").asString("")).isAfter(now)) break;
            cur = i;
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.set("current", current(series.get(cur).path("data"), now.atZone(zone)));
        root.set("hourly", hourly(series, cur, zone));
        root.set("daily", daily(series, lat, lon, zone));
        return root.toString();
    }

    private ObjectNode current(JsonNode data, ZonedDateTime now) {
        JsonNode d = data.path("instant").path("details");
        double temp = d.path("air_temperature").asDouble(0);
        ObjectNode c = objectMapper.createObjectNode();
        c.put("time", LOCAL_TIME.format(now));
        c.put("temperature_2m", temp);
        c.put("apparent_temperature", d.path("apparent_air_temperature").asDouble(temp));
        c.put("wind_speed_10m", d.path("wind_speed").asDouble(0) * 3.6); // m/s → km/h som Open-Meteo
        c.put("wind_direction_10m", (int) Math.round(d.path("wind_from_direction").asDouble(0)));
        c.put("relative_humidity_2m", d.path("relative_humidity").asDouble(0));
        c.put("precipitation", data.path("next_1_hours").path("details").path("precipitation_amount").asDouble(0));
        c.put("weather_code", wmoCode(symbol(data)));
        c.put("uv_index", d.path("ultraviolet_index_clear_sky").asDouble(0));
        return c;
    }

    private ObjectNode hourly(JsonNode series, int cur, ZoneId zone) {
        ObjectNode h = objectMapper.createObjectNode();
        ArrayNode time = h.putArray("time"), code = h.putArray("weather_code"), temp = h.putArray("temperature_2m"),
                  prob = h.putArray("precipitation_probability"), wind = h.putArray("wind_speed_10m");
        for (int i = cur; i < series.size() && i < cur + HOURS; i++) {
            JsonNode data = series.get(i).path("data");
            JsonNode d = data.path("instant").path("details");
            time.add(localTime(series.get(i), zone));
            code.add(wmoCode(symbol(data)));
            temp.add(d.path("air_temperature").asDouble(0));
            JsonNode p = data.path("next_1_hours").path("details").path("probability_of_precipitation");
            if (p.isNumber()) prob.add((int) Math.round(p.asDouble(0))); else prob.addNull();
            wind.add(d.path("wind_speed").asDouble(0) * 3.6);
        }
        return h;
    }

    /** Dygnsvärden ur tidsserien: max/min över dygnets poster, värsta vädret som dygnets kod. */
    private ObjectNode daily(JsonNode series, double lat, double lon, ZoneId zone) {
        Map<LocalDate, double[]> days = new LinkedHashMap<>(); // {max, min, kod}
        for (JsonNode entry : series) {
            LocalDate date = Instant.parse(entry.path("time").asString("")).atZone(zone).toLocalDate();
            if (!days.containsKey(date) && days.size() == DAYS) break;
            JsonNode data = entry.path("data");
            double t = data.path("instant").path("details").path("air_temperature").asDouble(0);
            JsonNode six = data.path("next_6_hours").path("details");
            double max = six.path("air_temperature_max").asDouble(t);
            double min = six.path("air_temperature_min").asDouble(t);
            int code = wmoCode(symbol(data));
            double[] agg = days.computeIfAbsent(date, k -> new double[] {max, min, code});
            agg[0] = Math.max(agg[0], max);
            agg[1] = Math.min(agg[1], min);
            agg[2] = Math.max(agg[2], code); // WMO-koderna växer ungefär med hur besvärligt vädret är
        }

        ObjectNode d = objectMapper.createObjectNode();
        ArrayNode time = d.putArray("time"), sunrise = d.putArray("sunrise"), sunset = d.putArray("sunset"),
                  max = d.putArray("temperature_2m_max"), min = d.putArray("temperature_2m_min"),
                  code = d.putArray("weather_code");
        for (Map.Entry<LocalDate, double[]> e : days.entrySet()) {
            String[] sun = sunTimes(e.getKey(), lat, lon, zone);
            time.add(e.getKey().toString());
            sunrise.add(sun[0]);
            sunset.add(sun[1]);
            max.add(e.getValue()[0]);
            min.add(e.getValue()[1]);
            code.add((int) e.getValue()[2]);
        }
        return d;
    }

    private static String localTime(JsonNode entry, ZoneId zone) {
        return LOCAL_TIME.format(Instant.parse(entry.path("time").asString("")).atZone(zone));
    }

    /** Närmaste sammanfattning: nästa timme, annars sex eller tolv timmar framåt. */
    private static String symbol(JsonNode data) {
        for (String period : new String[] {"next_1_hours", "next_6_hours", "next_12_hours"}) {
            String s = data.path(period).path("summary").path("symbol_code").asString("");
            if (!s.isEmpty()) return s;
        }
        return "";
    }

    /** MET:s symbolkod (t.ex. "lightrainshowers_day") → närmaste WMO-kod som Open-Meteo använder. */
    static int wmoCode(String symbol) {
        String s = symbol.replaceAll("_(day|night|polartwilight)$", "");
        if (s.contains("thunder")) return 95;
        return switch (s) {
            case "clearsky" -> 0;
            case "fair" -> 1;
            case "partlycloudy" -> 2;
            case "fog" -> 45;
            case "lightrain" -> 61;
            case "rain" -> 63;
            case "heavyrain" -> 65;
            case "lightsleet", "lightsleetshowers" -> 66;
            case "sleet", "heavysleet", "sleetshowers", "heavysleetshowers" -> 67;
            case "lightsnow" -> 71;
            case "snow" -> 73;
            case "heavysnow" -> 75;
            case "lightrainshowers" -> 80;
            case "rainshowers" -> 81;
            case "heavyrainshowers" -> 82;
            case "lightsnowshowers", "snowshowers" -> 85;
            case "heavysnowshowers" -> 86;
            default -> 3; // "cloudy" och okända symboler: mulet, ingen påhittad nederbörd
        };
    }

    /**
     * Open-Meteo räknar fram tidszonen själv (timezone=auto); MET svarar bara i UTC.
     * Appen används nästan bara i Sverige — utanför landet ger longituden en ungefärlig zon.
     */
    static ZoneId zoneFor(double lat, double lon) {
        if (lat >= 55.0 && lat <= 69.1 && lon >= 10.9 && lon <= 24.2) return ZoneId.of("Europe/Stockholm");
        return ZoneOffset.ofHours((int) Math.max(-12, Math.min(14, Math.round(lon / 15.0))));
    }

    /**
     * Soluppgång och solnedgång (soluppgångsekvationen, ~1 min noggrannhet) — MET:s
     * prognos har dem inte, och ett extra anrop för två klockslag är inte värt det.
     * Midnattssol och polarnatt ges som 00:00–23:59 resp. 23:59–00:00, så att
     * mörkerkollen blir rätt dygnet runt.
     */
    static String[] sunTimes(LocalDate date, double lat, double lon, ZoneId zone) {
        double n = date.toEpochDay() - 10957;                     // dygn sedan J2000
        double jStar = n - lon / 360.0;
        double m = Math.toRadians((357.5291 + 0.98560028 * jStar) % 360);
        double c = 1.9148 * Math.sin(m) + 0.02 * Math.sin(2 * m) + 0.0003 * Math.sin(3 * m);
        double lambda = Math.toRadians((Math.toDegrees(m) + c + 180 + 102.9372) % 360);
        double jTransit = 2451545.0 + jStar + 0.0053 * Math.sin(m) - 0.0069 * Math.sin(2 * lambda);
        double sinDecl = Math.sin(lambda) * Math.sin(Math.toRadians(23.4397));
        double cosDecl = Math.cos(Math.asin(sinDecl));
        double phi = Math.toRadians(lat);
        double cosOmega = (Math.sin(Math.toRadians(-0.833)) - Math.sin(phi) * sinDecl) / (Math.cos(phi) * cosDecl);

        String day = date.toString();
        if (cosOmega < -1) return new String[] {day + "T00:00", day + "T23:59"}; // midnattssol
        if (cosOmega > 1)  return new String[] {day + "T23:59", day + "T00:00"}; // polarnatt
        double omega = Math.toDegrees(Math.acos(cosOmega));
        return new String[] {julianToLocal(jTransit - omega / 360.0, zone), julianToLocal(jTransit + omega / 360.0, zone)};
    }

    private static String julianToLocal(double julianDay, ZoneId zone) {
        long epochMillis = Math.round((julianDay - 2440587.5) * 86_400_000L);
        return LOCAL_TIME.format(Instant.ofEpochMilli(epochMillis).atZone(zone));
    }
}
