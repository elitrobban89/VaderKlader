package com.vaderklader.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaderklader.model.WeatherData;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

@Service
public class OpenMeteoService {

    private static final String OPEN_METEO_URL =
        "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s" +
        "&current=temperature_2m,apparent_temperature,wind_speed_10m,wind_direction_10m,relative_humidity_2m,precipitation,weather_code,uv_index" +
        "&hourly=weather_code,temperature_2m,precipitation_probability" +
        "&daily=sunrise,sunset,temperature_2m_max,temperature_2m_min,weather_code" +
        "&forecast_days=7&timezone=auto";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenMeteoService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(factory);
    }

    public WeatherData getWeather(double lat, double lon) {
        String url = String.format(OPEN_METEO_URL,
            String.valueOf(lat).replace(",", "."),
            String.valueOf(lon).replace(",", "."));

        try {
            String json = restTemplate.getForObject(url, String.class);
            return parseResponse(json);
        } catch (Exception e) {
            throw new RuntimeException("Kunde inte hämta väderdata: " + e.getMessage());
        }
    }

    private WeatherData parseResponse(String json) throws Exception {
        JsonNode root    = objectMapper.readTree(json);
        JsonNode current = root.get("current");

        double temperature    = current.get("temperature_2m").asDouble();
        double feelsLike      = current.get("apparent_temperature").asDouble();
        double windSpeedKmh   = current.get("wind_speed_10m").asDouble();
        int windDirDeg        = current.get("wind_direction_10m").asInt();
        double humidity       = current.get("relative_humidity_2m").asDouble();
        double precipitation  = current.get("precipitation").asDouble();
        int weatherCode       = current.get("weather_code").asInt();
        double uvIndex        = current.get("uv_index").asDouble();

        double windSpeedMs = Math.round((windSpeedKmh / 3.6) * 10.0) / 10.0;
        String windDirection = toCardinal(windDirDeg);
        int precipCategory = weatherCodeToPrecipCategory(weatherCode);
        String forecastWarning = detectForecastWarning(root);
        boolean isDark = detectIsDark(root);

        String sunriseRaw = root.get("daily").get("sunrise").get(0).asText();
        String sunsetRaw  = root.get("daily").get("sunset").get(0).asText();
        String sunrise = sunriseRaw.length() >= 16 ? sunriseRaw.substring(11, 16) : "";
        String sunset  = sunsetRaw.length()  >= 16 ? sunsetRaw.substring(11, 16)  : "";

        List<WeatherData.HourlyForecast> hourlyForecast = extractHourlyForecast(root);
        List<WeatherData.DailyForecast> dailyForecast = extractDailyForecast(root);

        return new WeatherData(temperature, feelsLike, windSpeedMs, windDirection,
                humidity, precipitation, precipCategory, uvIndex, isDark, forecastWarning,
                sunrise, sunset, hourlyForecast, dailyForecast);
    }

    private List<WeatherData.HourlyForecast> extractHourlyForecast(JsonNode root) {
        List<WeatherData.HourlyForecast> result = new ArrayList<>();
        try {
            String currentTime = root.get("current").get("time").asText();
            String currentHour = currentTime.substring(0, 13);
            JsonNode times  = root.get("hourly").get("time");
            JsonNode codes  = root.get("hourly").get("weather_code");
            JsonNode temps  = root.get("hourly").get("temperature_2m");
            JsonNode probs  = root.get("hourly").get("precipitation_probability");

            int currentIndex = -1;
            for (int i = 0; i < times.size(); i++) {
                if (times.get(i).asText().startsWith(currentHour)) {
                    currentIndex = i;
                    break;
                }
            }
            if (currentIndex < 0) return result;

            for (int h = 1; h <= 6; h++) {
                int idx = currentIndex + h;
                if (idx >= times.size()) break;
                int code  = codes.get(idx).asInt();
                double temp = Math.round(temps.get(idx).asDouble() * 10.0) / 10.0;
                int prob  = probs != null && !probs.get(idx).isNull() ? probs.get(idx).asInt() : 0;
                result.add(new WeatherData.HourlyForecast(h, weatherCodeToIcon(code), temp, prob));
            }
        } catch (Exception ignored) {}
        return result;
    }

    private List<WeatherData.DailyForecast> extractDailyForecast(JsonNode root) {
        List<WeatherData.DailyForecast> result = new ArrayList<>();
        try {
            JsonNode dates   = root.get("daily").get("time");
            JsonNode maxTemps = root.get("daily").get("temperature_2m_max");
            JsonNode minTemps = root.get("daily").get("temperature_2m_min");
            JsonNode codes   = root.get("daily").get("weather_code");

            int days = Math.min(dates.size(), 7);
            for (int i = 0; i < days; i++) {
                String dateStr = dates.get(i).asText();
                String dayName = i == 0 ? "Idag" : i == 1 ? "Imorgon" : toDayNameSv(dateStr);
                String icon = weatherCodeToIcon(codes.get(i).asInt());
                double max = Math.round(maxTemps.get(i).asDouble() * 10.0) / 10.0;
                double min = Math.round(minTemps.get(i).asDouble() * 10.0) / 10.0;
                result.add(new WeatherData.DailyForecast(dayName, icon, max, min));
            }
        } catch (Exception ignored) {}
        return result;
    }

    private String toDayNameSv(String dateStr) {
        try {
            DayOfWeek dow = LocalDate.parse(dateStr).getDayOfWeek();
            return switch (dow) {
                case MONDAY    -> "Mån";
                case TUESDAY   -> "Tis";
                case WEDNESDAY -> "Ons";
                case THURSDAY  -> "Tor";
                case FRIDAY    -> "Fre";
                case SATURDAY  -> "Lör";
                case SUNDAY    -> "Sön";
            };
        } catch (Exception e) {
            return dateStr;
        }
    }

    private String weatherCodeToIcon(int code) {
        if (code == 0)                               return "☀️";
        if (code <= 2)                               return "🌤️";
        if (code <= 3)                               return "☁️";
        if (code == 45 || code == 48)                return "🌫️";
        if (code >= 51 && code <= 57)                return "🌦️";
        if (code >= 61 && code <= 67)                return "🌧️";
        if (code >= 71 && code <= 77)                return "❄️";
        if (code >= 80 && code <= 82)                return "🌧️";
        if (code == 85 || code == 86)                return "❄️";
        if (code == 95)                              return "⛈️";
        if (code == 96 || code == 99)                return "⛈️";
        return "🌡️";
    }

    private boolean detectIsDark(JsonNode root) {
        try {
            String currentTime = root.get("current").get("time").asText();
            String sunrise = root.get("daily").get("sunrise").get(0).asText();
            String sunset  = root.get("daily").get("sunset").get(0).asText();
            return currentTime.compareTo(sunrise) < 0 || currentTime.compareTo(sunset) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String detectForecastWarning(JsonNode root) {
        try {
            String currentTime = root.get("current").get("time").asText();
            JsonNode times = root.get("hourly").get("time");
            JsonNode codes = root.get("hourly").get("weather_code");

            String currentHour = currentTime.substring(0, 13);
            int currentIndex = -1;
            for (int i = 0; i < times.size(); i++) {
                if (times.get(i).asText().startsWith(currentHour)) {
                    currentIndex = i;
                    break;
                }
            }
            if (currentIndex < 0) return null;

            for (int h = 1; h <= 6; h++) {
                int idx = currentIndex + h;
                if (idx >= codes.size()) break;
                int code = codes.get(idx).asInt();
                if (code >= 51) {
                    String timeStr = times.get(idx).asText();
                    int forecastHour = Integer.parseInt(timeStr.substring(11, 13));
                    String type;
                    if (code == 96 || code == 99) type = "Åska med hagel";
                    else if (code == 95)          type = "Åska";
                    else if ((code >= 71 && code <= 77) || code == 85 || code == 86) type = "Snöfall";
                    else                          type = "Regn";
                    return String.format("%s väntas om ca %d timm%s (ca kl %02d:00)",
                        type, h, h == 1 ? "e" : "ar", forecastHour);
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String toCardinal(int deg) {
        String[] dirs = {"Norr", "Nordost", "Öst", "Sydost", "Syd", "Sydväst", "Väst", "Nordväst"};
        return dirs[(int) Math.round(deg / 45.0) % 8];
    }

    private int weatherCodeToPrecipCategory(int code) {
        if (code == 0 || code <= 3)              return 0;
        if (code == 45 || code == 48)            return 9;
        if (code == 51 || code == 53 || code == 55) return 4;
        if (code == 56 || code == 57)            return 6;
        if (code == 61 || code == 80)            return 3;
        if (code == 63 || code == 81)            return 10;
        if (code == 65 || code == 82)            return 11;
        if (code == 66 || code == 67)            return 5;
        if (code == 71 || code == 85)            return 12;
        if (code == 73 || code == 77)            return 1;
        if (code == 75 || code == 86)            return 13;
        if (code == 95)                          return 7;
        if (code == 96 || code == 99)            return 8;
        return 0;
    }
}
