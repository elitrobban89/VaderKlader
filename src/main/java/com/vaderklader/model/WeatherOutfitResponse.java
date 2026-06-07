package com.vaderklader.model;

public class WeatherOutfitResponse {

    private double lat;
    private double lon;
    private double temperature;
    private double feelsLike;
    private double windSpeed;
    private String windDirection;
    private double humidity;
    private double precipitation;
    private String precipitationDescription;
    private double uvIndex;
    private String forecastWarning;
    private String outfitSuggestion;

    public WeatherOutfitResponse(double lat, double lon, WeatherData weather, String outfitSuggestion) {
        this.lat = lat;
        this.lon = lon;
        this.temperature = weather.getTemperature();
        this.feelsLike = weather.getFeelsLike();
        this.windSpeed = weather.getWindSpeed();
        this.windDirection = weather.getWindDirection();
        this.humidity = weather.getHumidity();
        this.precipitation = weather.getPrecipitation();
        this.precipitationDescription = weather.getPrecipitationDescription();
        this.uvIndex = weather.getUvIndex();
        this.forecastWarning = weather.getForecastWarning();
        this.outfitSuggestion = outfitSuggestion;
    }

    public double getLat() { return lat; }
    public double getLon() { return lon; }
    public double getTemperature() { return temperature; }
    public double getFeelsLike() { return feelsLike; }
    public double getWindSpeed() { return windSpeed; }
    public String getWindDirection() { return windDirection; }
    public double getHumidity() { return humidity; }
    public double getPrecipitation() { return precipitation; }
    public String getPrecipitationDescription() { return precipitationDescription; }
    public double getUvIndex() { return uvIndex; }
    public String getForecastWarning() { return forecastWarning; }
    public String getOutfitSuggestion() { return outfitSuggestion; }
}
