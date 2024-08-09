package com.gns.wallclock;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class WeatherResponse {
    @SerializedName("main")
    private Main main;

    public Main getMain() {
        return main;
    }
    @SerializedName("weather")
    private List<Weather> weather;

    public List<Weather> getWeather() {
        return weather;
    }
    public static class Main {

        @SerializedName("temp")
        private double temperature;

        public double getTemperature() {
            return temperature;
        }

    }

    public class Weather {

        @SerializedName("description")
        private String description;
        @SerializedName("icon")
        private String iconId;

        public String getDescription() {
            return description;
        }

        public String getIconId(){
            return iconId;
        }
    }

}
