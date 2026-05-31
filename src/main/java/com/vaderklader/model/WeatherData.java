package com.vaderklader.model;

public class WeatherData {

    private double temperature;
    private double windSpeed;
    private double humidity;
    private double precipitation;
    private String precipitationDescription;

    public WeatherData(double temperature, double windSpeed, double humidity,
                       double precipitation, int precipitationCategory) {
        this.temperature = temperature;
        this.windSpeed = windSpeed;
        this.humidity = humidity;
        this.precipitation = precipitation;
        this.precipitationDescription = describePrecipitation(precipitationCategory);
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
            default -> "Okänd";
        };
    }

    public String toPromptDescription() {
        return String.format(
            "Temperatur: %.1f°C, Vindhastighet: %.1f m/s, Luftfuktighet: %.0f%%, Nederbörd: %s (%.1f mm/h)",
            temperature, windSpeed, humidity, precipitationDescription, precipitation
        );
    }

    public double getTemperature() { return temperature; }
    public double getWindSpeed() { return windSpeed; }
    public double getHumidity() { return humidity; }
    public double getPrecipitation() { return precipitation; }
    public String getPrecipitationDescription() { return precipitationDescription; }
}
