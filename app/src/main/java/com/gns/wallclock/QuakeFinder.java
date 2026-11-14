package com.gns.wallclock;

import android.content.Context;

import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import okhttp3.OkHttpClient;

import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Query;

//hissedilmiş olabilecek en son depremi bulur. hissedilme uzaklığı büyüklüğe göre artar, ara değerler doğrusal hesaplanır.
//AFAD sadece son birkaç haftayı verir, daha eskisi EMSC'den alınır. iki servis de http isteğini https'e yönlendirdiği için http yedeği yoktur.
public class QuakeFinder {
    private static final double[][] FELT_KM = {{2.5, 10}, {3, 25}, {4, 75}, {5, 150}, {6, 300}};
    private static final double MAX_KM = 300;
    private static final int LIMIT = 1000;
    private static final long AFAD_DAYS = 30;
    private static final double KM_PER_DEGREE = 111.19;

    public static class Quake {
        public final double magnitude, distanceKm, lat, lon;
        public final long time;
        public String place;

        Quake(double magnitude, double distanceKm, double lat, double lon, long time, String place) {
            this.magnitude = magnitude;
            this.distanceKm = distanceKm;
            this.lat = lat;
            this.lon = lon;
            this.time = time;
            this.place = place;
        }
    }

    interface AfadAPI {
        @GET("apigateway/deprem/apiv2/event/filter")
        Call<List<AfadEvent>> find(@Query("start") String start, @Query("end") String end, @Query("lat") double lat, @Query("lon") double lon,
                                   @Query("maxrad") int maxRadiusMeters, @Query("minmag") double minMagnitude,
                                   @Query("orderby") String orderBy, @Query("limit") int limit);
    }

    interface EmscAPI {
        @GET("fdsnws/event/1/query")
        Call<EmscResponse> find(@Query("lat") double lat, @Query("lon") double lon, @Query("maxradius") double maxRadiusDegrees,
                                @Query("minmag") double minMagnitude, @Query("orderby") String orderBy, @Query("limit") int limit,
                                @Query("format") String format);
    }

    static class AfadEvent {
        @SerializedName("magnitude") String magnitude;
        @SerializedName("latitude") String latitude;
        @SerializedName("longitude") String longitude;
        @SerializedName("date") String date;
        @SerializedName("district") String district;
        @SerializedName("location") String location;
    }

    static class EmscResponse {
        @SerializedName("features") List<Feature> features;

        static class Feature {
            @SerializedName("properties") Properties properties;
        }

        static class Properties {
            @SerializedName("mag") double mag;
            @SerializedName("lat") double lat;
            @SerializedName("lon") double lon;
            @SerializedName("time") String time;
        }
    }

    private final AfadAPI afad;
    private final EmscAPI emsc;

    public QuakeFinder(Context context) {
        OkHttpClient client = TrustedClient.create(context);
        afad = new Retrofit.Builder().baseUrl("https://servisnet.afad.gov.tr/").client(client)
                .addConverterFactory(GsonConverterFactory.create()).build().create(AfadAPI.class);
        emsc = new Retrofit.Builder().baseUrl("https://www.seismicportal.eu/").client(client)
                .addConverterFactory(GsonConverterFactory.create()).build().create(EmscAPI.class);
    }

    private static SimpleDateFormat utcFormat() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format;
    }

    //kurala uyan deprem yoksa null döner. servise ulaşılamazsa IOException atar.
    public Quake findAfad(double lat, double lon, double minMagnitude) throws IOException {
        SimpleDateFormat format = utcFormat();
        long now = System.currentTimeMillis();
        String start = format.format(new Date(now - AFAD_DAYS * 24 * 3600 * 1000L));
        String end = format.format(new Date(now + 24 * 3600 * 1000L));
        List<AfadEvent> events = body(afad.find(start, end, lat, lon, (int) (MAX_KM * 1000), minMagnitude, "timedesc", LIMIT).execute());
        Quake latest = null;
        if (events == null) return null;
        for (AfadEvent e : events) {
            try {
                double eLat = Double.parseDouble(e.latitude), eLon = Double.parseDouble(e.longitude);
                String place = e.district != null && !e.district.isEmpty() ? e.district : e.location;
                latest = newerFelt(latest, new Quake(Double.parseDouble(e.magnitude), distanceKm(lat, lon, eLat, eLon), eLat, eLon, format.parse(e.date).getTime(), place));
            } catch (NumberFormatException | ParseException | NullPointerException ex) {
                throw new IOException("AFAD: unexpected event format", ex);
            }
        }
        return latest;
    }

    public Quake findEmsc(double lat, double lon, double minMagnitude) throws IOException {
        SimpleDateFormat format = utcFormat();
        EmscResponse res = body(emsc.find(lat, lon, MAX_KM / KM_PER_DEGREE, minMagnitude, "time", LIMIT, "json").execute());
        Quake latest = null;
        if (res == null || res.features == null) return null;
        for (EmscResponse.Feature f : res.features) {
            EmscResponse.Properties p = f.properties;
            try {
                latest = newerFelt(latest, new Quake(p.mag, distanceKm(lat, lon, p.lat, p.lon), p.lat, p.lon, format.parse(p.time.substring(0, 19)).getTime(), null));
            } catch (ParseException | NullPointerException | IndexOutOfBoundsException ex) {
                throw new IOException("EMSC: unexpected event format", ex);
            }
        }
        return latest;
    }

    static double feltKm(double magnitude) {
        if (magnitude <= FELT_KM[0][0]) return FELT_KM[0][1];
        for (int i = 1; i < FELT_KM.length; i++) {
            double[] a = FELT_KM[i - 1], b = FELT_KM[i];
            if (magnitude <= b[0]) return a[1] + (b[1] - a[1]) * (magnitude - a[0]) / (b[0] - a[0]);
        }
        return FELT_KM[FELT_KM.length - 1][1];
    }

    private static <T> T body(Response<T> response) throws IOException {
        if (response.isSuccessful() && response.body() != null) return response.body();
        //EMSC sonuç yoksa 204 döner.
        if (response.code() == 204) return null;
        throw new IOException("HTTP " + response.code());
    }

    private static Quake newerFelt(Quake latest, Quake q) {
        if (q.distanceKm > feltKm(q.magnitude)) return latest;
        return latest == null || q.time > latest.time ? q : latest;
    }

    static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
