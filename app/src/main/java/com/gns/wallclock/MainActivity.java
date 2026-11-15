package com.gns.wallclock;

import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.gns.wallclock.databinding.ActivityMainBinding;
import com.gns.wallclock.databinding.SettingsLayoutBinding;

import java.text.DateFormat;
import java.text.MessageFormat;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    ActivityMainBinding binding;
    SettingsLayoutBinding settingsLayoutBinding;
    Handler handler = new Handler(Looper.getMainLooper());
    Runnable runnable,runWeather;
    DateFormat clockFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy EEEE", Locale.getDefault());
    DateFormat dailyFormat = new SimpleDateFormat("HH:mm",Locale.getDefault());
    DateFormat quakeDateFormat = new SimpleDateFormat("dd.MM.yy",Locale.getDefault());
    String city;
    String apiKey;
    double temperature;
    String iconId ="";
    OpenWeatherMapAPI apiHttps, apiHttp;
    boolean useHttp;//son başarılı bağlantı http ile mi kuruldu
    int langNum;
    int unitNum;
    float clockTextSize;
    float dateTextSize;
    float dailyTextSize, quakeTextSize, quakeMinMagnitude;
    SharedPreferences preferences;
    ArrayAdapter<String> langAdapter,metricAdapter;
    String defaultKey, defaultCity;
    //üst üste bu kadar hata alınırsa ilgili veri temizlenir. 10 dakikada bir denendiği için 3 hata = 30 dakika.
    static final int MAX_FAILURES = 3;
    int weatherFailures, dailyFailures;
    boolean hasWeather;//güncel hava durumu bilgisi alınabildi mi
    float effectFps;
    //sarsıntı alarmı. sarsıntı algılanınca ALARM_MS boyunca arka plan kırmızı olur ve bip sesi çalar.
    static final int ALARM_MS = 3000;
    ShakeDetector shakeDetector;
    float shakeLevel, stableTolerance;
    ToneGenerator toneGenerator;
    boolean alarmActive, resumed;
    Runnable runAlarmEnd, runFlash, runFlashEnd;
    TextView[] dailyTexts;
    static final long EMSC_INTERVAL_MS = 3600000;
    static final double PLACE_MAX_KM = 30;
    QuakeFinder quakeFinder;
    boolean quakeBusy;
    double emscLat, emscLon;
    long emscTime;
    QuakeFinder.Quake emscQuake;
    AlertDialog settingsDialog;
    //deprem satırı iki parçadır: başta sarsıntı durumu (geri sayım ya da algılanan değer), sonra deprem bilgisi.
    String shakeStatus = "", quakeInfo = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //uygulamayı tam ekranda başlatır.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

        //görünüme erişebilmek için binding nesnesi oluşturulur.
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        dailyTexts = new TextView[]{binding.dailyText1, binding.dailyText2, binding.dailyText3, binding.dailyText4,
                binding.dailyText5, binding.dailyText6, binding.dailyText7, binding.dailyText8};
        //varsayılan değerler atanır.
        clearDaily();
        binding.quakeText.setText("");
        binding.dateText.setText("");
        binding.clockText.setText("");

        //shared preferences nesnesi oluşturulur ve varsayılan değerler atanır.
        //böylece kalıcı olarak ayarlanmış değerler okunur.
        preferences = getPreferences(MODE_PRIVATE);
        defaultKey = getString(R.string.api_key);
        defaultCity = getString(R.string.my_city);
        apiKey = preferences.getString("apikey", defaultKey);
        city = preferences.getString("city", defaultCity);
        langNum = preferences.getInt("langnum",40);
        unitNum = preferences.getInt("unitnum",1);

        setTextSizes();//metin boyutları ayarlanır. Farklı ekran boyutunda çalışabilmek için.

        //efekt kare hızı ayarlanır. 0 ise efekt kapalıdır.
        effectFps = preferences.getFloat("effectFps",2);
        binding.effectView.setFps(effectFps);

        //sarsıntı hassasiyeti ayarlanır. 0 ise alarm kapalıdır.
        shakeLevel = preferences.getFloat("shakeLevel",40);
        shakeDetector = new ShakeDetector(this, new ShakeDetector.Listener() {
            @Override
            public void onShake() {
                startAlarm();
            }

            @Override
            public void onCalibrating(boolean align, int secondsLeft) {
                //hizalama ve ölçümde sadece kalan süre gösterilir.
                setShakeStatus(getString(R.string.shake_countdown, secondsLeft));
            }

            @Override
            public void onShakeLevel(float shake) {
                setShakeStatus(getString(R.string.shake_value, Math.round(shake * 1000)));
            }
        });
        shakeDetector.setSensitivity(shakeLevel);
        stableTolerance = preferences.getFloat("stableTolerance",20);
        shakeDetector.setStableTolerance(stableTolerance);
        runAlarmEnd = this::stopAlarm;
        //alarm sırasında yarım saniyede bir 100 ms beyaz ışık patlaması gösterilir.
        runFlash = () -> {
            handler.postDelayed(runFlash, 500);
            binding.getRoot().setBackgroundColor(ContextCompat.getColor(this, R.color.white));
            handler.postDelayed(runFlashEnd, 100);
        };
        runFlashEnd = () -> binding.getRoot().setBackgroundColor(ContextCompat.getColor(this, R.color.alarm_red));

        //önce https denenir. bağlantı kurulamazsa http denenir (bkz. getWeather, getDaily).
        //eski cihazlarda SSL sorunu yaşamamak için böyle yapıldı.
        apiHttps = createApi("https://api.openweathermap.org/");
        apiHttp = createApi("http://api.openweathermap.org/");
        quakeFinder = new QuakeFinder(this);
        quakeMinMagnitude = preferences.getFloat("quakeMinMagnitude",2.5f);

        //ayarlar penceresi için adapter oluşturulur.
        List<String> langlist = new ArrayList<>();
        for (String[] s : languageCodes)
            langlist.add(s[1]);
        langAdapter = new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,langlist);
        List<String> metriclist = new ArrayList<>();
        for (String[] s : unitCodes)
            metriclist.add(s[1]);
        metricAdapter = new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,metriclist);

        //10 saniyede 1 saat ve tarih güncellenir.
        runnable = () ->{
            handler.postDelayed(runnable, 10000);
            Date date = Calendar.getInstance().getTime();
            //hava durumu bilgisi yoksa sıcaklık gösterilmez.
            if (hasWeather)
                binding.dateText.setText(MessageFormat.format("{0} {1}{2}", dateFormat.format(date), Double.valueOf(temperature).intValue(),unitCodes[unitNum][0]));
            else
                binding.dateText.setText(dateFormat.format(date));
            binding.clockText.setText(clockFormat.format(date));
        };

        //10 dakikada 1 hava durumu güncellenir.
        runWeather = () -> {
            handler.postDelayed(runWeather, 600000);
            getWeather();
            getDaily();
            binding.horizontalLayout.smoothScrollTo(0,0);
        };

    }

    //ayarlar penceresi gösterilir. pencere kapanınca sarsıntı kalibrasyonu başlar.
    void showSettings(){
        if (settingsDialog != null) return;
        //ayarlar penceresi için binding nesnesi oluşturulur.
        //pencere her açılışta kayıtlı değerlerle yeniden oluşturulur.
        settingsLayoutBinding = SettingsLayoutBinding.inflate(getLayoutInflater());
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        //ayarlar penceresi için varsayılan değerler atanır.
        settingsLayoutBinding.apiCode.setText(preferences.getString("apikey", defaultKey));
        settingsLayoutBinding.cityName.setText(preferences.getString("city",defaultCity));
        settingsLayoutBinding.clockTextSize.setText(String.valueOf(preferences.getFloat("clockTextSize",300)));
        settingsLayoutBinding.dateTextSize.setText(String.valueOf(preferences.getFloat("dateTextSize",55)));
        settingsLayoutBinding.dailyTextSize.setText(String.valueOf(preferences.getFloat("dailyTextSize",36)));
        settingsLayoutBinding.quakeTextSize.setText(String.valueOf(preferences.getFloat("quakeTextSize",30)));
        settingsLayoutBinding.quakeMinMagnitude.setText(String.valueOf(preferences.getFloat("quakeMinMagnitude",2.5f)));
        settingsLayoutBinding.effectFps.setText(String.valueOf(preferences.getFloat("effectFps",2)));
        settingsLayoutBinding.shakeLevel.setText(String.valueOf(preferences.getFloat("shakeLevel",40)));
        settingsLayoutBinding.stableTolerance.setText(String.valueOf(preferences.getFloat("stableTolerance",20)));

        //ayarlar penceresi için adapterlar oluşturulur.
        settingsLayoutBinding.langSpinner.setAdapter(langAdapter);
        settingsLayoutBinding.metricSpinner.setAdapter(metricAdapter);
        settingsLayoutBinding.langSpinner.setSelection(langNum);
        settingsLayoutBinding.metricSpinner.setSelection(unitNum);

        //ayarlar üç sayfadır, üstteki düğmelerle seçilir. açılışta Basic seçilidir.
        settingsLayoutBinding.tabBasic.setOnClickListener(v -> showSettingsPage(0));
        settingsLayoutBinding.tabDisplay.setOnClickListener(v -> showSettingsPage(1));
        settingsLayoutBinding.tabLimits.setOnClickListener(v -> showSettingsPage(2));
        showSettingsPage(0);

        //ayarlar penceresi görünüm oluşturulur.
        builder.setView(settingsLayoutBinding.getRoot());

        //ayarlar penceresi için butonlar oluşturulur.
        builder.setPositiveButton("Save", (dialog, which) -> {
            apiKey = settingsLayoutBinding.apiCode.getText().toString();
            city = settingsLayoutBinding.cityName.getText().toString();
            langNum = settingsLayoutBinding.langSpinner.getSelectedItemPosition();
            unitNum = settingsLayoutBinding.metricSpinner.getSelectedItemPosition();
            clockTextSize = getFloat(settingsLayoutBinding.clockTextSize,300);
            dateTextSize = getFloat(settingsLayoutBinding.dateTextSize,55);
            dailyTextSize = getFloat(settingsLayoutBinding.dailyTextSize,36);
            quakeTextSize = getFloat(settingsLayoutBinding.quakeTextSize,30);
            float minMagnitude = getFloat(settingsLayoutBinding.quakeMinMagnitude,2.5f);
            if (minMagnitude != quakeMinMagnitude) emscTime = 0;
            quakeMinMagnitude = minMagnitude;
            effectFps = getFloat(settingsLayoutBinding.effectFps,2);
            shakeLevel = getFloat(settingsLayoutBinding.shakeLevel,40);
            stableTolerance = getFloat(settingsLayoutBinding.stableTolerance,20);
            preferences.edit().putString("apikey",apiKey).apply();
            preferences.edit().putString("city", city).apply();
            preferences.edit().putInt("langnum",langNum).apply();
            preferences.edit().putInt("unitnum", unitNum).apply();
            preferences.edit().putFloat("clockTextSize",clockTextSize).apply();
            preferences.edit().putFloat("dateTextSize",dateTextSize).apply();
            preferences.edit().putFloat("dailyTextSize",dailyTextSize).apply();
            preferences.edit().putFloat("quakeTextSize",quakeTextSize).apply();
            preferences.edit().putFloat("quakeMinMagnitude",quakeMinMagnitude).apply();
            preferences.edit().putFloat("effectFps",effectFps).apply();
            preferences.edit().putFloat("shakeLevel",shakeLevel).apply();
            preferences.edit().putFloat("stableTolerance",stableTolerance).apply();
            setTextSizes();
            binding.effectView.setFps(effectFps);
            shakeDetector.setSensitivity(shakeLevel);
            shakeDetector.setStableTolerance(stableTolerance);
            getWeather();
            getDaily();
            binding.horizontalLayout.smoothScrollTo(0,0);
            dialog.cancel();
        });
        builder.setNegativeButton("Exit", (dialog, which) -> dialog.cancel());

        //yine yanlışlıkla dokunmaları önlemek için bu şekilde ayarlanır.
        builder.setCancelable(false);

        //ayarlar penceresi gösterilir.
        settingsDialog = builder.create();
        //pencere sadece Save ya da Exit ile kapanır. arka plana geçerken de kapatılır, o zaman kalibrasyon başlamaz.
        //kapanış bildirimi gecikmeli gelir. arada uygulama ön plana dönüp yeni pencere açmış olabilir, bu yüzden
        //sadece o anki pencerenin kapanışı kalibrasyonu başlatır.
        AlertDialog dialog = settingsDialog;
        dialog.setOnDismissListener(d -> {
            if (settingsDialog != dialog) return;
            settingsDialog = null;
            if (resumed) startCalibration();
        });
        settingsDialog.show();
    }

    //seçilen sayfa görünür, düğmesi basılamaz hale gelir. böylece hangi sayfanın açık olduğu görülür.
    void showSettingsPage(int page){
        View[] pages = {settingsLayoutBinding.pageBasic, settingsLayoutBinding.pageDisplay, settingsLayoutBinding.pageLimits};
        View[] tabs = {settingsLayoutBinding.tabBasic, settingsLayoutBinding.tabDisplay, settingsLayoutBinding.tabLimits};
        for (int i = 0; i < pages.length; i++) {
            pages[i].setVisibility(i == page ? View.VISIBLE : View.GONE);
            tabs[i].setEnabled(i != page);
        }
    }

    //sarsıntı kapalıysa deprem satırında sarsıntı durumu gösterilmez.
    void startCalibration(){
        shakeDetector.calibrate();
        if (!shakeDetector.isEnabled()) setShakeStatus("");
    }

    void setShakeStatus(String status){
        shakeStatus = status;
        updateQuakeText();
    }

    void updateQuakeText(){
        if (shakeStatus.isEmpty()) binding.quakeText.setText(quakeInfo);
        else if (quakeInfo.isEmpty()) binding.quakeText.setText(shakeStatus);
        else binding.quakeText.setText(shakeStatus + " · " + quakeInfo);
    }

    //ayarlar penceresinden edittext değerlerini sorunsuz almak için kullanılır.
    float getFloat(EditText et, float def){
        try {
            return Float.parseFloat(et.getText().toString());
        }catch (Exception e){
            return def;
        }
    }

    //ayarlar penceresinden gelen değerler kullanılarak metin boyutları ayarlanır.
    void setTextSizes(){
        clockTextSize = preferences.getFloat("clockTextSize",300);
        dateTextSize = preferences.getFloat("dateTextSize",55);
        dailyTextSize = preferences.getFloat("dailyTextSize",36);
        quakeTextSize = preferences.getFloat("quakeTextSize",30);
        binding.quakeText.setTextSize(quakeTextSize);
        binding.clockText.setTextSize(clockTextSize);
        binding.dateText.setTextSize(dateTextSize);
        for (TextView t : dailyTexts) t.setTextSize(dailyTextSize);
    }

    void getWeather(){
        getWeather(useHttp, false);
    }

    //seçilen bağlantı (http/https) ile istek atılır. bağlantı kurulamazsa diğeri bir kez denenir.
    void getWeather(boolean http, boolean retried){
        OpenWeatherMapAPI api = http ? apiHttp : apiHttps;
        Call<WeatherResponse> call = api.getWeather(city, apiKey, unitCodes[unitNum][1], getLangCode(langNum));
        call.enqueue(new Callback<WeatherResponse>() {
            @Override
            public void onResponse(@NonNull Call<WeatherResponse> call, @NonNull Response<WeatherResponse> response) {
                //sunucu cevap verdi, yani bu bağlantı yöntemi çalışıyor. cevap hata olsa bile bağlantı sorunu değildir.
                setConnection(http);
                WeatherResponse res = response.isSuccessful() ? response.body() : null;
                //geçerli veri alınamayan her durum (http hatası, boş yanıt) hata sayılır.
                if (res == null || res.getMain() == null || res.getWeather() == null || res.getWeather().isEmpty()) {
                    weatherFailed();
                    return;
                }
                weatherFailures = 0;
                hasWeather = true;
                temperature = res.getMain().getTemperature();
                String icon = res.getWeather().get(0).getIconId();
                iconId = icon == null ? "" : icon;
                applyBackground();
                if (res.getCoord() != null) getQuake(res.getCoord().getLat(), res.getCoord().getLon());
            }
            @Override
            public void onFailure(@NonNull Call<WeatherResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "onFailure: Weather not loaded", t);
                //bağlantı kurulamadı. diğer yöntem denenmediyse önce o denenir, henüz hata sayılmaz.
                if (!retried) {
                    getWeather(!http, true);
                    return;
                }
                weatherFailed();
            }
        });
    }

    void getDaily(){
        getDaily(useHttp, false);
    }

    //seçilen bağlantı (http/https) ile istek atılır. bağlantı kurulamazsa diğeri bir kez denenir.
    void getDaily(boolean http, boolean retried){
        OpenWeatherMapAPI api = http ? apiHttp : apiHttps;
        Call<ForecastResponse> call = api.getForecast(city,apiKey,unitCodes[unitNum][1],getLangCode(langNum));
        call.enqueue(new Callback<ForecastResponse>() {
            @Override
            public void onResponse(@NonNull Call<ForecastResponse> call, @NonNull Response<ForecastResponse> response) {
                //sunucu cevap verdi, yani bu bağlantı yöntemi çalışıyor. cevap hata olsa bile bağlantı sorunu değildir.
                setConnection(http);
                ForecastResponse res = response.isSuccessful() ? response.body() : null;
                //geçerli veri alınamayan her durum (http hatası, boş yanıt, eksik liste) hata sayılır.
                if (res == null || res.getLists() == null || res.getLists().size() < dailyTexts.length) {
                    dailyFailed();
                    return;
                }
                dailyFailures = 0;
                for (int i = 0; i < dailyTexts.length; i++) dailyTexts[i].setText(getDailyText(res, i));
            }
            @Override
            public void onFailure(@NonNull Call<ForecastResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "onFailure: Daily not loaded", t);
                //bağlantı kurulamadı. diğer yöntem denenmediyse önce o denenir, henüz hata sayılmaz.
                if (!retried) {
                    getDaily(!http, true);
                    return;
                }
                dailyFailed();
            }
        });
    }

    CharSequence getDailyText(ForecastResponse res, int idx){
        ForecastResponse.Weathers item = res.getLists().get(idx);
        ForecastResponse.Weathers.Weather weather = item.getWeather().get(0);
        boolean night = weather.getIconId() != null && weather.getIconId().endsWith("n");
        SpannableStringBuilder text = new SpannableStringBuilder(dailyFormat.format(new Date(item.getDate()))).append("\n");
        int iconStart = text.length();
        text.append(WeatherIcons.glyph(weather.getConditionId(), night));
        text.setSpan(new WeatherIcons.Span(WeatherIcons.typeface(this)), iconStart, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text.append(" ").append(String.valueOf((int) item.getMain().getTemperature())).append("°");
    }

    void getQuake(double lat, double lon){
        if (quakeBusy) return;
        quakeBusy = true;
        double minMagnitude = quakeMinMagnitude;
        new Thread(() -> {
            QuakeFinder.Quake quake = null;
            boolean found = false;
            try {
                quake = quakeFinder.findAfad(lat, lon, minMagnitude);
                found = quake != null;
            } catch (IOException e) {
                Log.e(TAG, "getQuake: AFAD not loaded", e);
            }
            if (!found) {
                try {
                    if (emscTime == 0 || System.currentTimeMillis() - emscTime >= EMSC_INTERVAL_MS || emscLat != lat || emscLon != lon) {
                        emscQuake = quakeFinder.findEmsc(lat, lon, minMagnitude);
                        if (emscQuake != null) emscQuake.place = placeName(emscQuake.lat, emscQuake.lon);
                        emscTime = System.currentTimeMillis();
                        emscLat = lat;
                        emscLon = lon;
                    }
                    quake = emscQuake;
                    found = true;
                } catch (IOException e) {
                    Log.e(TAG, "getQuake: EMSC not loaded", e);
                }
            }
            QuakeFinder.Quake result = quake;
            boolean show = found;
            handler.post(() -> {
                quakeBusy = false;
                if (show) {
                    quakeInfo = getQuakeText(result);
                    updateQuakeText();
                }
            });
        }).start();
    }

    //EMSC ilçe adı vermez, OpenWeatherMap'ten alınır. bulunamazsa koordinat yazılır. arka plan iş parçacığında çağrılır.
    //denizdeki noktalar için servis ülkenin kendisini (orta noktasıyla) döndürüyor, uzaktaki ad yok sayılır.
    String placeName(double lat, double lon){
        for (boolean http : new boolean[]{useHttp, !useHttp}) {
            try {
                Response<List<OpenWeatherMapAPI.Place>> response = (http ? apiHttp : apiHttps).reverse(lat, lon, 1, apiKey).execute();
                List<OpenWeatherMapAPI.Place> places = response.body();
                if (response.isSuccessful() && places != null && !places.isEmpty()) {
                    OpenWeatherMapAPI.Place place = places.get(0);
                    if (QuakeFinder.distanceKm(lat, lon, place.lat, place.lon) > PLACE_MAX_KM) break;
                    String local = place.localNames == null ? null : place.localNames.get(getLangCode(langNum));
                    if (local != null && !local.isEmpty()) return local;
                    if (place.name != null && !place.name.isEmpty()) return place.name;
                }
                break;
            } catch (IOException e) {
                Log.e(TAG, "placeName: place not loaded", e);
            }
        }
        return String.format(Locale.US, "%.2f, %.2f", lat, lon);
    }

    String getQuakeText(QuakeFinder.Quake quake){
        if (quake == null) return "";
        return String.format(Locale.US, "%s %.1f · %d km · %s · %s", quake.scale, quake.magnitude, Math.round(quake.distanceKm),
                quakeDateFormat.format(new Date(quake.time)), quake.place == null ? "" : quake.place);
    }

    //sarsıntı algılanınca 3 saniye boyunca arka plan kırmızı olur, beyaz ışık patlamaları görünür ve bip sesi çalar.
    //alarm sırasında da algılama sürer. yeni alarm, alarmın başından pencere süresi + alarm süresi kadar sonra verilebilir (bkz. ShakeDetector).
    void startAlarm(){
        if (alarmActive) return;
        alarmActive = true;
        binding.effectView.setVisibility(View.INVISIBLE);
        binding.getRoot().setBackgroundColor(ContextCompat.getColor(this, R.color.alarm_red));
        handler.post(runFlash);
        try {
            //ses seviyesine dokunulmaz. alarm kanalında, tonun en yüksek seviyesiyle çalar.
            toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME);
            toneGenerator.startTone(ToneGenerator.TONE_DTMF_S, ALARM_MS);
        } catch (RuntimeException e) {
            Log.e(TAG, "startAlarm: tone not played", e);
        }
        handler.postDelayed(runAlarmEnd, ALARM_MS);
    }

    void stopAlarm(){
        if (!alarmActive) return;
        alarmActive = false;
        handler.removeCallbacks(runAlarmEnd);
        handler.removeCallbacks(runFlash);
        handler.removeCallbacks(runFlashEnd);
        if (toneGenerator != null) {
            toneGenerator.stopTone();
            toneGenerator.release();
            toneGenerator = null;
        }
        binding.effectView.setVisibility(View.VISIBLE);
        applyBackground();
    }

    OpenWeatherMapAPI createApi(String baseUrl){
        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(OpenWeatherMapAPI.class);
    }

    //son başarılı bağlantı yöntemi hatırlanır. sonraki istekler önce bununla denenir.
    //bellekte tutulur, uygulama yeniden başlayınca https ile başlanır.
    void setConnection(boolean http){
        if (useHttp != http)
            Log.i(TAG, "setConnection: switched to " + (http ? "http" : "https"));
        useHttp = http;
    }

    //arka plan rengi ve efekt güncel hava durumu ikon koduna göre ayarlanır.
    //gündüz mavi, gece siyah. ikon kodu yoksa (açılış ya da veri alınamadığında) siyah.
    void applyBackground(){
        //alarm sırasında arka plan kırmızı kalır. alarm bitince bu metot yeniden çağrılır.
        if (alarmActive) return;
        int color = ContextCompat.getColor(this, R.color.black);
        if (iconId != null && iconId.length() >= 3 && !iconId.endsWith("n"))
            color = ContextCompat.getColor(this, R.color.sky_day);
        binding.getRoot().setBackgroundColor(color);
        binding.effectView.setWeather(iconId, color);
    }

    //hata sayısı eşiğe ulaşana kadar son durum korunur. ulaşınca açılıştaki hale dönülür.
    void weatherFailed(){
        if (weatherFailures < MAX_FAILURES) weatherFailures++;
        if (weatherFailures < MAX_FAILURES) return;
        hasWeather = false;
        iconId = "";
        applyBackground();
    }

    void dailyFailed(){
        if (dailyFailures < MAX_FAILURES) dailyFailures++;
        if (dailyFailures >= MAX_FAILURES) clearDaily();
    }

    void clearDaily(){
        for (TextView t : dailyTexts) t.setText("");
    }


    String getLangCode(int code){
        try {
            return languageCodes[code][0];
        }catch (Exception e){
            return "tr";
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        //uygulama sadece önplandayken çalıştır.
        handler.post(runnable);
        handler.post(runWeather);
        binding.effectView.start();
        resumed = true;
        //uygulama açılışta ve her ön plana gelişte ayarlar penceresi görüntülenir, yeni açılmış gibi davranır.
        //arka plana atıldıysa cihaza dokunulmuştur, sarsıntı kalibrasyonu pencere kapanınca baştan yapılır.
        //kendi kendine dokunma alan cihazlar için bu şekilde ayarlandı.
        //dokunma hiç çalışmıyorsa harici klavye-mouse bağlayarak giriş yapabılabilir.
        showSettings();
    }

    @Override
    protected void onPause() {
        super.onPause();
        //arkaplanda çalışmayı durdurur.
        handler.removeCallbacks(runnable);
        handler.removeCallbacks(runWeather);
        binding.effectView.stop();
        resumed = false;
        //pencere hemen boşaltılır, böylece geri dönüşte yeni pencere açılır.
        if (settingsDialog != null) {
            AlertDialog dialog = settingsDialog;
            settingsDialog = null;
            dialog.dismiss();
        }
        shakeDetector.stop();
        stopAlarm();
        setShakeStatus("");
    }

    String[][] unitCodes = {
            {"°K","standard"},
            {"°C","metric"},
            {"°F","imperial"}
    };
    String[][] languageCodes = {
            {"af", "Afrikaans"},
            {"al", "Shqip"},
            {"ar", "العربية"},
            {"az", "Azərbaycanca"},
            {"bg", "Български"},
            {"ca", "Català"},
            {"cz", "Čeština"},
            {"da", "Dansk"},
            {"de", "Deutsch"},
            {"el", "Ελληνικά"},
            {"en", "English"},
            {"eu", "Euskara"},
            {"fa", "فارسی"},
            {"fi", "Suomi"},
            {"fr", "Français"},
            {"gl", "Galego"},
            {"he", "עברית"},
            {"hi", "हिन्दी"},
            {"hr", "Hrvatski"},
            {"hu", "Magyar"},
            {"id", "Bahasa Indonesia"},
            {"it", "Italiano"},
            {"ja", "日本語"},
            {"kr", "한국어"},
            {"la", "Latviešu"},
            {"lt", "Lietuvių"},
            {"mk", "Македонски"},
            {"no", "Norsk"},
            {"nl", "Nederlands"},
            {"pl", "Polski"},
            {"pt", "Português"},
            {"pt_br", "Português Brasileiro"},
            {"ro", "Română"},
            {"ru", "Русский"},
            {"se", "Svenska"},
            {"sk", "Slovenčina"},
            {"sl", "Slovenščina"},
            {"es", "Español"},
            {"sr", "Српски"},
            {"th", "ภาษาไทย"},
            {"tr", "Türkçe"},
            {"uk", "Українська"},
            {"vi", "Tiếng Việt"},
            {"zh_cn", "简体中文"},
            {"zh_tw", "繁體中文"},
            {"zu", "IsiZulu"}
    };


}