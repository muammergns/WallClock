package com.gns.wallclock;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.util.Log;

//ivmeölçer ile sarsıntı algılar. hassasiyet 0 ise kapalıdır ve sensör hiç dinlenmez. hassasiyet 0-10 arasındadır, 10 en hassastır.
//iki aşamalı kontrol yapılır, ikisinden biri alarmı tetikler:
//1. salınım: ileri geri sallanma çok hassas şekilde aranır (masayı hafifçe sallamak gibi). birkaç kez yön değiştirmesi gerekir.
//2. pik: tek bir sert darbe (masaya vurmak gibi). daha az hassastır, yalnızca güçlü darbeleri yakalar.
//ölçümden yerçekimi çıkarılır. filtreler sensörün ölçüm hızına değil geçen zamana göre çalışır, böylece her cihazda aynı davranır.
//eşik değerleri tahmindir. sensör kalitesi cihazdan cihaza değiştiği için hassasiyet cihazda denenerek ayarlanır.
public class ShakeDetector implements SensorEventListener {
    public interface Listener {
        void onShake();
    }

    private static final String TAG = "ShakeDetector";
    //yerçekimini ayıran filtrenin süresi (saniye). 1 saniyelik salınımı yutmayacak kadar yavaştır.
    private static final float GRAVITY_TAU = 1f;
    //salınım ölçümünü yumuşatan filtrenin süresi (saniye). sensör gürültüsünü ve çok hızlı titreşimleri eler.
    private static final float SMOOTH_TAU = 0.05f;
    //salınım: bu süre içinde en az SWING_COUNT yön değişimi olursa sarsıntı sayılır.
    private static final long SWING_WINDOW_MS = 3000;
    private static final int SWING_COUNT = 4;
    //iki yön değişimi arasında en az bu kadar süre geçmelidir. bundan hızlı titreşimler sayılmaz.
    private static final long SWING_MIN_GAP_MS = 150;
    //dinlemeye başladıktan sonra filtreler oturana kadar ölçümler yok sayılır.
    private static final long WARMUP_MS = 500;
    //ölçülen değerler bu aralıkla loglanır (hassasiyeti ayarlayabilmek için).
    private static final long LOG_INTERVAL_MS = 5000;

    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final Listener listener;
    private float level;
    private float peakThreshold, swingThreshold;
    private boolean active, registered;

    //eksen başına (x, y, z) filtre ve yön değişimi durumu.
    private final float[] gravity = new float[3];
    private final float[] smooth = new float[3];
    private final int[] state = new int[3];
    private final int[] swings = new int[3];
    private final long[] lastFlip = new long[3];
    private final long[] swingStart = new long[3];
    private long lastTimestamp, startTime, lastLog;
    private float logPeak, logSwing;

    public ShakeDetector(Context context, Listener listener) {
        this.listener = listener;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer == null) Log.w(TAG, "ShakeDetector: no accelerometer, shake alert disabled");
    }

    //0 kapatır. hassasiyet 10 için salınım eşiği 0.03, pik eşiği 1.0 m/s²; 1 için 0.45 ve 7.75 m/s² olur.
    public void setSensitivity(float level) {
        this.level = Math.max(0, Math.min(10, level));
        swingThreshold = 0.5f - 0.047f * this.level;
        peakThreshold = 8.5f - 0.75f * this.level;
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
        long now = SystemClock.uptimeMillis();
        float dt = (event.timestamp - lastTimestamp) / 1e9f;
        boolean restart = lastTimestamp == 0 || dt <= 0 || dt > 0.5f;
        lastTimestamp = event.timestamp;
        //ilk ölçümde ya da ölçümler arasında uzun boşluk varsa filtreler baştan başlar.
        if (restart) {
            for (int i = 0; i < 3; i++) {
                gravity[i] = event.values[i];
                smooth[i] = 0;
                state[i] = 0;
                swings[i] = 0;
            }
            startTime = now;
            lastLog = now;
            logPeak = 0;
            logSwing = 0;
            return;
        }

        //filtreler ölçüm sayısına değil geçen süreye göre çalışır.
        float gravityGain = dt / (GRAVITY_TAU + dt);
        float smoothGain = dt / (SMOOTH_TAU + dt);
        float sumSquares = 0;
        float swing = 0;
        for (int i = 0; i < 3; i++) {
            gravity[i] += gravityGain * (event.values[i] - gravity[i]);
            float shake = event.values[i] - gravity[i];
            smooth[i] += smoothGain * (shake - smooth[i]);
            sumSquares += shake * shake;
            swing = Math.max(swing, Math.abs(smooth[i]));
        }
        float peak = (float) Math.sqrt(sumSquares);

        if (now - startTime < WARMUP_MS) return;

        //hassasiyeti ayarlayabilmek için ölçülen en yüksek değerler periyodik loglanır.
        logPeak = Math.max(logPeak, peak);
        logSwing = Math.max(logSwing, swing);
        if (now - lastLog >= LOG_INTERVAL_MS) {
            Log.d(TAG, "onSensorChanged: last 5 s peak=" + logPeak + " swing=" + logSwing
                    + " (thresholds: peak=" + peakThreshold + " swing=" + swingThreshold + ")");
            lastLog = now;
            logPeak = 0;
            logSwing = 0;
        }

        //2. aşama: tek bir sert darbe.
        if (peak >= peakThreshold) {
            trigger("peak", peak);
            return;
        }

        //1. aşama: ileri geri salınım. her eksende yön değişimleri sayılır.
        for (int i = 0; i < 3; i++) {
            int newState = state[i];
            if (smooth[i] > swingThreshold) newState = 1;
            else if (smooth[i] < -swingThreshold) newState = -1;
            if (newState == state[i]) continue;
            //sıfırdan ilk çıkış yön değişimi sayılmaz.
            boolean flipped = state[i] != 0;
            state[i] = newState;
            if (!flipped || now - lastFlip[i] < SWING_MIN_GAP_MS) continue;
            lastFlip[i] = now;
            if (swings[i] == 0 || now - swingStart[i] > SWING_WINDOW_MS) {
                swingStart[i] = now;
                swings[i] = 0;
            }
            swings[i]++;
            if (swings[i] >= SWING_COUNT) {
                trigger("swing", Math.abs(smooth[i]));
                return;
            }
        }
    }

    private void trigger(String type, float value) {
        Log.i(TAG, "trigger: " + type + " value=" + value + " peakThreshold=" + peakThreshold + " swingThreshold=" + swingThreshold);
        for (int i = 0; i < 3; i++) {
            state[i] = 0;
            swings[i] = 0;
        }
        listener.onShake();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
