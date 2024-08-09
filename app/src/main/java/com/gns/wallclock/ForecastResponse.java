package com.gns.wallclock;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ForecastResponse {

    @SerializedName("list")
    private List<Weathers> lists;

    public List<Weathers> getLists(){
        return lists;
    }


    public static class Weathers{
        @SerializedName("dt")
        private Long date;

        public Long getDate(){
            return date*1000L;
        }
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
        public class Main {

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
}
