package com.vaderklader.model;

import java.util.List;

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
    private String sunrise;
    private String sunset;
    private List<WeatherData.HourlyForecast> hourlyForecast;
    private List<WeatherData.DailyForecast> dailyForecast;
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
        this.sunrise = weather.getSunrise();
        this.sunset = weather.getSunset();
        this.hourlyForecast = weather.getHourlyForecast();
        this.dailyForecast = weather.getDailyForecast();
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
    public String getSunrise() { return sunrise; }
    public String getSunset() { return sunset; }
    public List<WeatherData.HourlyForecast> getHourlyForecast() { return hourlyForecast; }
    public List<WeatherData.DailyForecast> getDailyForecast() { return dailyForecast; }
    public String getOutfitSuggestion() { return outfitSuggestion; }
}
