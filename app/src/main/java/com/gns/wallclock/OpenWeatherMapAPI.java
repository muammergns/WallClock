package com.gns.wallclock;

import java.util.List;
import java.util.Map;

import com.google.gson.annotations.SerializedName;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface OpenWeatherMapAPI {
    @GET("/data/2.5/weather")
    Call<WeatherResponse> getWeather(@Query("q") String cityName, @Query("appid") String apiKey, @Query("units") String units,@Query("lang") String lang);

    @GET("/geo/1.0/reverse")
    Call<List<Place>> reverse(@Query("lat") double lat, @Query("lon") double lon, @Query("limit") int limit, @Query("appid") String apiKey);

    class Place {
        @SerializedName("name") String name;
        @SerializedName("local_names") Map<String, String> localNames;
        @SerializedName("lat") double lat;
        @SerializedName("lon") double lon;
    }

    @GET("/data/2.5/forecast")
    Call<ForecastResponse> getForecast(@Query("q") String cityName, @Query("appid") String apiKey, @Query("units") String units,@Query("lang") String lang);

}
