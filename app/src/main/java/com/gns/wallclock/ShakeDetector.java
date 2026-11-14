package com.gns.wallclock;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

//ivmeölçer ile deprem benzeri sarsıntı algılar. ayar 0 ise kapalıdır ve sensör hiç dinlenmez. ayar 0-10 arasındadır, 100'e bölünerek m/s² cinsinden sarsıntı eşiği olur, küçük değer daha hassastır.
//tek bir değerin büyüklüğüne değil sarsıntının ne kadar sürdüğüne bakılır. ölçümler sehpada tıklatma ve çarpmaların en fazla
//2.7 saniye, deprem taklitlerinin 13 saniyeden uzun sürdüğünü gösterdi. tepe değeri ve frekans bu ikisini ayırmıyordu.
//1. yerçekimi çıkarılır, kalan sarsıntının gücü yarım saniyelik ortalama ile ölçülür (kısa ortalama).
//2. sakin ortamın gürültüsü uzun ortalama ile cihazın kendisinde ölçülür. böylece sabit m/s² eşiğine gerek kalmaz.
//3. gürültü güçte çıkarılarak sarsıntının kendisi bulunur: sarsıntı = √(kısa² − gürültü²). titreşimler değerde değil güçte toplandığı için
//çıkarma da güçte yapılır. böylece aynı sarsıntı gürültüsü farklı cihazlarda aynı eşikle karşılaştırılır.
//4. sarsıntı eşiği SHAKE_MIN_MS boyunca kesintisiz aşarsa alarm verilir.
//filtreler sensörün ölçüm hızına değil geçen zamana göre çalışır, böylece her cihazda aynı davranır.
public class ShakeDetector implements SensorEventListener {
    public interface Listener {
        void onShake();
    }

    private static final String TAG = "ShakeDetector";
    //yerçekimini ayıran filtrenin süresi (saniye). 1 saniyelik salınımı yutmayacak kadar yavaştır.
    private static final float GRAVITY_TAU = 1f;
    //kısa ortalama: sarsıntı gücünün yumuşatma süresi (saniye).
    private static final float SHORT_TAU = 0.5f;
    //sakin ortam gürültüsünün öğrenilme süreleri (saniye). gürültü aşağı hızlı, yukarı yavaş öğrenilir.
    //böylece açılışta ya da güçlü sarsıntıdan sonra şişen gürültü birkaç saniyede gerçek değerine iner.
    //sarsıntı varken gürültü hiç değişmez, böylece süren sarsıntı kendini gürültü sanıp susturmaz.
    private static final float NOISE_TAU_DOWN = 2f;
    private static final float NOISE_TAU_UP = 30f;
    //dinlemeye başlayınca bu süre boyunca sadece gürültü öğrenilir, alarm verilmez.
    private static final long WARMUP_MS = 3000;
    //sarsıntı bu süre kesintisiz devam ederse alarm verilir.
    private static final long SHAKE_MIN_MS = 3000;
    //bu süreden kısa düşüşler sarsıntıyı kesmiş sayılmaz (elle sallarken ya da deprem dalgaları arasında güç anlık düşebilir).
    private static final long GAP_MAX_MS = 500;
    //ölçülen değerler bu aralıkla loglanır (hassasiyeti ayarlayabilmek için).
    private static final long LOG_INTERVAL_MS = 5000;

    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final Listener listener;
    private float level;
    //gürültüden ayrılmış sarsıntı bu değeri (m/s²) aşarsa sarsıntı var sayılır.
    private float shakeThreshold;
    private boolean active, registered;

    //eksen başına yerçekimi.
    private final float[] gravity = new float[3];
    //güçler ivmenin karesi cinsindendir (m²/s⁴), oran karşılaştırılırken kareköke çevrilir.
    private float shortPower, noisePower;
    //zamanlar sensör saatine göre milisaniyedir.
    private long lastTimestamp, startTime, shakeStart, lastAbove, lastLog;
    private boolean shaking;
    private float logShake;

    public ShakeDetector(Context context, Listener listener) {
        this.listener = listener;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer == null) Log.w(TAG, "ShakeDetector: no accelerometer, shake alert disabled");
    }

    //0 kapatır. 4 için eşik 0.04 m/s² olur.
    public void setSensitivity(float level) {
        this.level = Math.max(0, Math.min(10, level));
        shakeThreshold = this.level / 100f;
        if (active) {
            unregister();
            register();
        }
    }

    //uygulama ön plana gelince ya da alarm bitince çağrılır.
    public void start() {
        active = true;
        register();
    }

    //uygulama arka plana gidince ya da alarm başlayınca çağrılır.
    public void stop() {
        active = false;
        unregister();
    }

    private void register() {
        if (level <= 0 || accelerometer == null || registered) return;
        lastTimestamp = 0;
        registered = sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        if (!registered) Log.w(TAG, "register: accelerometer listener not registered");
    }

    private void unregister() {
        if (!registered) return;
        sensorManager.unregisterListener(this);
        registered = false;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        long now = event.timestamp / 1000000;
        float dt = (event.timestamp - lastTimestamp) / 1e9f;
        boolean restart = lastTimestamp == 0 || dt <= 0 || dt > 0.5f;
        lastTimestamp = event.timestamp;
        //ilk ölçümde ya da ölçümler arasında uzun boşluk varsa filtreler baştan başlar.
        if (restart) {
            System.arraycopy(event.values, 0, gravity, 0, 3);
            shortPower = 0;
            noisePower = 0;
            shaking = false;
            startTime = now;
            lastLog = now;
            logShake = 0;
            return;
        }

        float power = 0;
        float gravityGain = dt / (GRAVITY_TAU + dt);
        for (int i = 0; i < 3; i++) {
            gravity[i] += gravityGain * (event.values[i] - gravity[i]);
            float shake = event.values[i] - gravity[i];
            power += shake * shake;
        }
        shortPower += dt / (SHORT_TAU + dt) * (power - shortPower);

        //ısınma süresince gürültü kısa ortalamayla hızla öğrenilir.
        if (now - startTime < WARMUP_MS) {
            noisePower = shortPower;
            return;
        }

        float shake = (float) Math.sqrt(Math.max(0, shortPower - noisePower));
        boolean above = shake >= shakeThreshold;
        if (!above && !shaking) {
            float noiseTau = shortPower < noisePower ? NOISE_TAU_DOWN : NOISE_TAU_UP;
            noisePower += dt / (noiseTau + dt) * (shortPower - noisePower);
        }

        //eşiği ayarlayabilmek için ölçülen en yüksek sarsıntı periyodik loglanır.
        logShake = Math.max(logShake, shake);
        if (now - lastLog >= LOG_INTERVAL_MS) {
            Log.d(TAG, "onSensorChanged: last 5 s max shake=" + logShake + " noise=" + Math.sqrt(noisePower)
                    + " (threshold=" + shakeThreshold + ")");
            lastLog = now;
            logShake = 0;
        }

        if (above) {
            if (!shaking) {
                shaking = true;
                shakeStart = now;
            }
            lastAbove = now;
        } else if (shaking && now - lastAbove > GAP_MAX_MS) {
            shaking = false;
        }

        if (shaking && now - shakeStart >= SHAKE_MIN_MS) {
            Log.i(TAG, "trigger: shaking for " + (now - shakeStart) + " ms shake=" + shake
                    + " noise=" + Math.sqrt(noisePower) + " threshold=" + shakeThreshold);
            //sarsıntı sürerse bir sonraki alarm için yeniden SHAKE_MIN_MS beklenir.
            shakeStart = now;
            listener.onShake();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
