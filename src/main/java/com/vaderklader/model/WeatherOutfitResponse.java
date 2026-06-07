package com.vaderklader.model;

public class WeatherOutfitResponse {

    private double lat;
    private double lon;
    private double temperature;
    private double feelsLike;
    private double windSpeed;
    private double humidity;
    private String precipitationDescription;
    private String outfitSuggestion;

    public WeatherOutfitResponse(double lat, double lon, WeatherData weather, String outfitSuggestion) {
        this.lat = lat;
        this.lon = lon;
        this.temperature = weather.getTemperature();
        this.feelsLike = weather.getFeelsLike();
        this.windSpeed = weather.getWindSpeed();
        this.humidity = weather.getHumidity();
        this.precipitationDescription = weather.getPrecipitationDescription();
        this.outfitSuggestion = outfitSuggestion;
    }

    public double getLat() { return lat; }
    public double getLon() { return lon; }
    public double getTemperature() { return temperature; }
    public double getFeelsLike() { return feelsLike; }
    public double getWindSpeed() { return windSpeed; }
    public double getHumidity() { return humidity; }
    public String getPrecipitationDescription() { return precipitationDescription; }
    public String getOutfitSuggestion() { return outfitSuggestion; }
}
