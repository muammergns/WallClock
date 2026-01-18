package com.gns.wallclock;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.gns.wallclock.databinding.ActivityMainBinding;
import com.gns.wallclock.databinding.SettingsLayoutBinding;

import java.io.IOException;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import okhttp3.ResponseBody;
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
    Runnable runnable,runWeather,runIcon;
    DateFormat clockFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy EEEE", Locale.getDefault());
    DateFormat dailyFormat = new SimpleDateFormat("HH",Locale.getDefault());
    String city;
    String apiKey;
    double temperature;
    Drawable mainWeatherDrawable;
    String iconId ="";
    Retrofit retrofit;
    OpenWeatherMapAPI api;
    int langNum;
    int unitNum;
    float clockTextSize;
    float dateTextSize;
    float dailyTextSize;
    SharedPreferences preferences;
    ArrayAdapter<String> langAdapter,metricAdapter;
    String defaultKey, defaultCity;

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

        //varsayılan değerler atanır.
        binding.dailyText1.setText("");
        binding.dailyText2.setText("");
        binding.dailyText3.setText("");
        binding.dailyText4.setText("");
        binding.dailyText5.setText("");
        binding.dailyText6.setText("");
        binding.dailyText7.setText("");
        binding.dailyText8.setText("");
        binding.dailyText9.setText("");
        binding.dailyText10.setText("");
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

        //eski cihazlarda SSl sorunu yaşamamak için eklendi
        String ssl = "http";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ssl+="s";
        }else {
            Toast.makeText(MainActivity.this, "Old device SSL disabled", Toast.LENGTH_SHORT).show();
        }

        retrofit = new Retrofit.Builder()
                .baseUrl(ssl+"://api.openweathermap.org/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        api = retrofit.create(OpenWeatherMapAPI.class);

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
            binding.dateText.setText(MessageFormat.format("{0} {1}{2}", dateFormat.format(date), Double.valueOf(temperature).intValue(),unitCodes[unitNum][0]));
            binding.clockText.setText(clockFormat.format(date));
            binding.dateText.setCompoundDrawablesWithIntrinsicBounds(null,null,mainWeatherDrawable,null);
        };

        //10 dakikada 1 hava durumu güncellenir.
        runWeather = () -> {
            handler.postDelayed(runWeather, 600000);
            getWeather();
            getDaily();
            binding.horizontalLayout.smoothScrollTo(0,0);
        };

        //hava durumu güncellendiğinde harici olarak hava durumu ikonu sunucudan çekilir.
        runIcon = this::getIcon;

        //uygulama açılışta ayarlar penceresi görüntülenir.
        //kendi kendine dokunma alan cihazlar için bu şekilde ayarlandı.
        //dokunma hiç çalışmıyorsa harici klavye-mouse bağlayarak giriş yapabılabilir.
        showSettings();
    }

    //ayarlar penceresi gösterilir.
    void showSettings(){
        //ayarlar penceresi için binding nesnesi oluşturulur.
        //açılışta sadece 1 defa çalışması yeterli olduğu için nesne burada oluşturulur.
        settingsLayoutBinding = SettingsLayoutBinding.inflate(getLayoutInflater());
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        //ayarlar penceresi için varsayılan değerler atanır.
        settingsLayoutBinding.apiCode.setText(preferences.getString("apikey", defaultKey));
        settingsLayoutBinding.cityName.setText(preferences.getString("city",defaultCity));
        settingsLayoutBinding.clockTextSize.setText(String.valueOf(preferences.getFloat("clockTextSize",300)));
        settingsLayoutBinding.dateTextSize.setText(String.valueOf(preferences.getFloat("dateTextSize",55)));
        settingsLayoutBinding.dailyTextSize.setText(String.valueOf(preferences.getFloat("dailyTextSize",36)));

        //ayarlar penceresi için adapterlar oluşturulur.
        settingsLayoutBinding.langSpinner.setAdapter(langAdapter);
        settingsLayoutBinding.metricSpinner.setAdapter(metricAdapter);
        settingsLayoutBinding.langSpinner.setSelection(langNum);
        settingsLayoutBinding.metricSpinner.setSelection(unitNum);

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
            preferences.edit().putString("apikey",apiKey).apply();
            preferences.edit().putString("city", city).apply();
            preferences.edit().putInt("langnum",langNum).apply();
            preferences.edit().putInt("unitnum", unitNum).apply();
            preferences.edit().putFloat("clockTextSize",clockTextSize).apply();
            preferences.edit().putFloat("dateTextSize",dateTextSize).apply();
            preferences.edit().putFloat("dailyTextSize",dailyTextSize).apply();
            setTextSizes();
            getWeather();
            getDaily();
            binding.horizontalLayout.smoothScrollTo(0,0);
            dialog.cancel();
        });
        builder.setNegativeButton("Exit", (dialog, which) -> dialog.cancel());

        //yine yanlışlıkla dokunmaları önlemek için bu şekilde ayarlanır.
        builder.setCancelable(false);

        //ayarlar penceresi gösterilir.
        AlertDialog dialog = builder.create();
        dialog.show();
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
        binding.clockText.setTextSize(clockTextSize);
        binding.dateText.setTextSize(dateTextSize);
        binding.dailyText1.setTextSize(dailyTextSize);
        binding.dailyText2.setTextSize(dailyTextSize);
        binding.dailyText3.setTextSize(dailyTextSize);
        binding.dailyText4.setTextSize(dailyTextSize);
        binding.dailyText5.setTextSize(dailyTextSize);
        binding.dailyText6.setTextSize(dailyTextSize);
        binding.dailyText7.setTextSize(dailyTextSize);
        binding.dailyText8.setTextSize(dailyTextSize);
        binding.dailyText9.setTextSize(dailyTextSize);
        binding.dailyText10.setTextSize(dailyTextSize);
    }

    void getWeather(){
        Call<WeatherResponse> call = api.getWeather(city, apiKey, unitCodes[unitNum][1], getLangCode(langNum));
        call.enqueue(new Callback<WeatherResponse>() {
            @Override
            public void onResponse(@NonNull Call<WeatherResponse> call, @NonNull Response<WeatherResponse> response) {
                if (response.isSuccessful()) {
                    WeatherResponse res = response.body();
                    if (res == null) return;
                    temperature = res.getMain().getTemperature();
                    iconId = res.getWeather().get(0).getIconId();
                    //eski cihazlarda http bağlantıda dahi SSL sorunu olduğu için pasif hale getirildi
                    //çok da önemli değil zaten
                    //todo buna daha sonra çok daha mantıklı bir çözüm bulunacak
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                        handler.post(runIcon);
                }
            }
            @Override
            public void onFailure(@NonNull Call<WeatherResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "onFailure: Weather not loaded", t);
            }
        });
    }

    void getDaily(){
        Call<ForecastResponse> call = api.getForecast(city,apiKey,unitCodes[unitNum][1],getLangCode(langNum));
        call.enqueue(new Callback<ForecastResponse>() {
            @Override
            public void onResponse(@NonNull Call<ForecastResponse> call, @NonNull Response<ForecastResponse> response) {
                ForecastResponse res = response.body();
                if (res == null) return;
                if (res.getLists().size()>=10){
                    binding.dailyText1.setText(getDailyText(res,0));
                    binding.dailyText2.setText(getDailyText(res,1));
                    binding.dailyText3.setText(getDailyText(res,2));
                    binding.dailyText4.setText(getDailyText(res,3));
                    binding.dailyText5.setText(getDailyText(res,4));
                    binding.dailyText6.setText(getDailyText(res,5));
                    binding.dailyText7.setText(getDailyText(res,6));
                    binding.dailyText8.setText(getDailyText(res,7));
                    binding.dailyText9.setText(getDailyText(res,8));
                    binding.dailyText10.setText(getDailyText(res,9));
                }
            }
            @Override
            public void onFailure(@NonNull Call<ForecastResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "onFailure: Daily not loaded", t);
            }
        });
    }

    String getDailyText(ForecastResponse res, int idx){
        String s1 = res.getLists().get(idx).getWeather().get(0).getDescription();
        StringBuilder newStr = new StringBuilder();
        for (String word : s1.split(" ")) {
            String firstChar = word.substring(0, 1).toUpperCase();
            String rest = word.substring(1);
            newStr.append(firstChar).append(rest).append(" ");
        }
        newStr = new StringBuilder(newStr.substring(0, newStr.length() - 1));
        s1 = newStr.toString();
        s1 += "\n";
        s1 += dailyFormat.format(new Date(res.getLists().get(idx).getDate()));
        s1 += ": ";
        s1 += String.valueOf(Double.valueOf(res.getLists().get(idx).getMain().getTemperature()).intValue());
        s1 += unitCodes[unitNum][0];
        return s1;
    }

    void getIcon(){
        Call<ResponseBody> call = api.getIcon(iconId);
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    ResponseBody responseBody = response.body();
                    try {
                        Log.i(TAG, "onResponse: icon loaded success start");
                        assert responseBody != null;
                        byte[] bytes = responseBody.bytes();
                        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap,80,80,true);
                        mainWeatherDrawable = new BitmapDrawable(getResources(), scaledBitmap);
                        Log.i(TAG, "onResponse: icon loaded success end");
                    } catch (IOException e) {
                        System.out.println("Icon not loaded catch:"+e.getLocalizedMessage());
                    }
                }else{
                    Log.d(TAG, "onResponse: icon not loaded");
                }
            }
            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e(TAG, "onFailure: Icon not loaded", t);
            }
        });
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
    }

    @Override
    protected void onPause() {
        super.onPause();
        //arkaplanda çalışmayı durdurur.
        handler.removeCallbacks(runnable);
        handler.removeCallbacks(runWeather);
        handler.removeCallbacks(runIcon);
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