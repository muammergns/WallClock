package com.gns.wallclock;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface OpenWeatherMapAPI {
    @GET("/data/2.5/weather")
    Call<WeatherResponse> getWeather(@Query("q") String cityName, @Query("appid") String apiKey, @Query("units") String units,@Query("lang") String lang);

    @GET("/data/2.5/forecast")
    Call<ForecastResponse> getForecast(@Query("q") String cityName, @Query("appid") String apiKey, @Query("units") String units,@Query("lang") String lang);

}
