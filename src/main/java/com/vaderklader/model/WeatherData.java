package com.vaderklader.model;

public class WeatherData {

    private double temperature;
    private double feelsLike;
    private double windSpeed;
    private String windDirection;
    private double humidity;
    private double precipitation;
    private String precipitationDescription;
    private double uvIndex;
    private String forecastWarning;

    public WeatherData(double temperature, double feelsLike, double windSpeed, String windDirection,
                       double humidity, double precipitation, int precipitationCategory,
                       double uvIndex, String forecastWarning) {
        this.temperature = temperature;
        this.feelsLike = feelsLike;
        this.windSpeed = windSpeed;
        this.windDirection = windDirection;
        this.humidity = humidity;
        this.precipitation = precipitation;
        this.precipitationDescription = describePrecipitation(precipitationCategory);
        this.uvIndex = uvIndex;
        this.forecastWarning = forecastWarning;
    }

    private String describePrecipitation(int category) {
        return switch (category) {
            case 0 -> "Ingen nederbörd";
            case 1 -> "Snö";
            case 2 -> "Snö och regn";
            case 3 -> "Regn";
            case 4 -> "Duggregn";
            case 5 -> "Underkylt regn";
            case 6 -> "Underkylt duggregn";
            case 7 -> "Åska";
            case 8 -> "Åska med hagel";
            default -> "Okänd";
        };
    }

    public String toPromptDescription() {
        String uvInfo = uvIndex >= 3 ? String.format(", UV: %.0f (%s)", uvIndex, describeUv(uvIndex)) : "";
        return String.format(
            "Temp: %.1f°C (upplevd: %.1f°C), Vind: %.1f m/s från %s, Luftfuktighet: %.0f%%, Nederbörd: %s%s",
            temperature, feelsLike, windSpeed, windDirection, humidity, precipitationDescription, uvInfo
        );
    }

    private String describeUv(double uv) {
        if (uv >= 11) return "extremt — starkt undvik direkt sol";
        if (uv >= 8)  return "mycket högt — solskydd och skyddskläder";
        if (uv >= 6)  return "högt — solskydd och solhatt rekommenderas";
        if (uv >= 3)  return "måttligt — solglasögon rekommenderas";
        return "lågt";
    }

    public double getTemperature() { return temperature; }
    public double getFeelsLike() { return feelsLike; }
    public double getWindSpeed() { return windSpeed; }
    public String getWindDirection() { return windDirection; }
    public double getHumidity() { return humidity; }
    public double getPrecipitation() { return precipitation; }
    public String getPrecipitationDescription() { return precipitationDescription; }
    public double getUvIndex() { return uvIndex; }
    public String getForecastWarning() { return forecastWarning; }
}
