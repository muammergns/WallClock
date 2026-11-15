package com.gns.wallclock;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

//ivmeölçer ile deprem benzeri sarsıntı algılar. ayar mm/s² cinsinden sarsıntı eşiğidir (0-1000), 0 ise sensör hiç dinlenmez.
//sarsıntı = √(düzleştirilmiş güç − gürültü²). güç, yerçekimi çıkarılmış ivmenin karesidir ve son SMOOTH_MS içindeki
//değerlere en küçük karelerle uydurulan doğrunun şu anki noktasıyla düzleştirilir. gürültü kalibrasyonda ölçülür ve sabit kalır.
//sarsıntı eşiği aşarsa alarm verilir. alarmın başından TRIGGER_HOLD_MS boyunca yeni alarm verilmez, okuma bu sırada da sürer.
public class ShakeDetector implements SensorEventListener {
    public interface Listener {
        void onShake();
        //kalibrasyon sırasında kalan süre değiştikçe çağrılır. align true ise cihaza dokunulabilir, false ise dokunulmamalıdır.
        void onCalibrating(boolean align, int secondsLeft);
        //algılama sırasında saniyede bir, son saniyenin en yüksek sarsıntısı ile çağrılır (m/s²).
        void onShakeLevel(float shake);
    }

    private enum Phase {IDLE, ALIGN, WARMUP, MEASURE, DETECT}

    private static final String TAG = "ShakeDetector";
    //yerçekimini ayıran filtrenin süresi (saniye).
    private static final float GRAVITY_TAU = 1f;
    //düzleştirme penceresi. uzadıkça dalgalanma azalır, sarsıntıya tepki gecikir.
    private static final long SMOOTH_MS = 10000;
    //pencerede tutulabilecek en fazla ölçüm. ölçüm hızı 200 Hz'e kadar 10 saniye sığar.
    private static final int SMOOTH_CAPACITY = 2000;
    //alarmın başından itibaren yeni alarm verilmeyen süre: pencere süresi + alarm süresi. bu sürede alarm öncesi değerler pencereden çıkar.
    private static final long TRIGGER_HOLD_MS = SMOOTH_MS + MainActivity.ALARM_MS;
    //kalibrasyon süreleri: hizalamada cihaza dokunulabilir, ısınma ve ölçümde dokunulmaz.
    private static final long ALIGN_MS = 20000;
    private static final long WARMUP_MS = 20000;
    private static final long MEASURE_MS = 20000;
    //algılanan sarsıntı ekrana bu aralıkla bildirilir.
    private static final long REPORT_MS = 1000;
    //ölçülen değerler bu aralıkla loglanır (hassasiyeti ayarlayabilmek için).
    private static final long LOG_INTERVAL_MS = 5000;

    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final Listener listener;
    private float level;
    //gürültüden ayrılmış sarsıntı bu değeri (m/s²) aşarsa sarsıntı var sayılır.
    private float shakeThreshold;
    //ısınma ve ölçüm gürültüleri arasındaki fark bu değerden (m/s²) küçükse ölçüm kabul edilir. 0 ise her ölçüm kabul edilir.
    private float stableTolerance;
    private boolean registered;
    private Phase phase = Phase.IDLE;

    //eksen başına yerçekimi.
    private final float[] gravity = new float[3];
    //güçler ivmenin karesi cinsindendir (m²/s⁴).
    private float shortPower, noisePower;
    //düzleştirme penceresi: ölçüm zamanları (ms) ve güçleri, halka şeklinde.
    private final long[] smoothTime = new long[SMOOTH_CAPACITY];
    private final float[] smoothPower = new float[SMOOTH_CAPACITY];
    private int smoothHead, smoothCount;
    //kalibrasyonda gücün zamana göre toplamı ve süresi. ortalama güç = toplam / süre.
    private double warmSum, warmTime, measureSum, measureTime;
    private float warmPower;
    //zamanlar sensör saatine göre milisaniyedir. phaseStart < 0 ise aşama ilk ölçümde başlar.
    private long lastTimestamp, phaseStart, calibStart, lastLog, lastReport;
    //bu zamandan önce alarm verilmez (bkz. TRIGGER_HOLD_MS).
    private long detectFrom;
    private float logShake, reportShake;
    private int lastSeconds;

    public ShakeDetector(Context context, Listener listener) {
        this.listener = listener;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer == null) Log.w(TAG, "ShakeDetector: no accelerometer, shake alert disabled");
    }

    //0 kapatır. değer mm/s²'dir, 40 için eşik 0.04 m/s² olur. dinlemeyi başlatmaz, yeni değer bir sonraki kalibrasyonla geçerli olur.
    public void setSensitivity(float level) {
        this.level = Math.max(0, Math.min(1000, level));
        shakeThreshold = this.level / 1000f;
    }

    //değer mm/s²'dir. 0 kontrolü kapatır. yeni değer bir sonraki kalibrasyonla geçerli olur.
    public void setStableTolerance(float mm) {
        stableTolerance = Math.max(0, mm) / 1000f;
    }

    //algılama açık ve sensör varsa true döner.
    public boolean isEnabled() {
        return level > 0 && accelerometer != null;
    }

    //ayar penceresi kapanınca çağrılır. kalibrasyon baştan başlar, gürültü ölçüm sonunda yeniden belirlenir.
    public void calibrate() {
        unregister();
        if (!isEnabled()) {
            phase = Phase.IDLE;
            return;
        }
        phase = Phase.ALIGN;
        phaseStart = -1;
        shortPower = 0;
        smoothCount = 0;
        lastSeconds = -1;
        register();
    }

    //uygulama arka plana gidince çağrılır. yeniden başlamak için calibrate gerekir.
    public void stop() {
        unregister();
        phase = Phase.IDLE;
    }

    private void register() {
        if (registered) return;
        lastTimestamp = 0;
        registered = sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        if (!registered) Log.w(TAG, "register: accelerometer listener not registered");
    }

    private void unregister() {
        if (!registered) return;
        sensorManager.unregisterListener(this);
        registered = false;
    }

    private void startPhase(Phase next, long now) {
        phase = next;
        phaseStart = now;
        switch (next) {
            case WARMUP:
                calibStart = now;
                warmSum = 0;
                warmTime = 0;
                break;
            case MEASURE:
                warmPower = warmTime > 0 ? (float) (warmSum / warmTime) : 0;
                measureSum = 0;
                measureTime = 0;
                break;
            case DETECT:
                detectFrom = now;
                lastLog = now;
                lastReport = now;
                logShake = 0;
                reportShake = 0;
                break;
        }
    }

    //kalan süre saniye olarak değiştikçe bildirilir.
    private void reportCountdown(boolean align, long leftMs) {
        int seconds = (int) ((Math.max(0, leftMs) + 999) / 1000);
        if (seconds == lastSeconds) return;
        lastSeconds = seconds;
        listener.onCalibrating(align, seconds);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        long now = event.timestamp / 1000000;
        float dt = (event.timestamp - lastTimestamp) / 1e9f;
        boolean gap = lastTimestamp == 0 || dt <= 0 || dt > 0.5f;
        lastTimestamp = event.timestamp;
        //ilk ölçümde ya da ölçümler arasında uzun boşluk varsa yerçekimi baştan alınır.
        if (gap) {
            System.arraycopy(event.values, 0, gravity, 0, 3);
            if (phaseStart < 0) phaseStart = now;
            return;
        }

        float power = 0;
        float gravityGain = dt / (GRAVITY_TAU + dt);
        for (int i = 0; i < 3; i++) {
            gravity[i] += gravityGain * (event.values[i] - gravity[i]);
            float shake = event.values[i] - gravity[i];
            power += shake * shake;
        }
        shortPower = smooth(now, power);

        switch (phase) {
            case ALIGN:
                //hizalama: filtreler oturur, değerler kullanılmaz.
                if (now - phaseStart >= ALIGN_MS) startPhase(Phase.WARMUP, now);
                else reportCountdown(true, ALIGN_MS - (now - phaseStart));
                break;
            case WARMUP:
                warmSum += power * dt;
                warmTime += dt;
                if (now - phaseStart >= WARMUP_MS) startPhase(Phase.MEASURE, now);
                reportCountdown(false, WARMUP_MS + MEASURE_MS - (now - calibStart));
                break;
            case MEASURE:
                measure(now, power, dt);
                break;
            case DETECT:
                detect(now, dt);
                break;
        }
    }

    //güç pencereye eklenir, pencereye uydurulan doğrunun şu anki değeri döner.
    //zaman şu andan geriye saniye olarak alınır, doğru y = a + b·t olduğundan şu anki değer a'dır.
    private float smooth(long now, float power) {
        int tail = (smoothHead + SMOOTH_CAPACITY - smoothCount) % SMOOTH_CAPACITY;
        while (smoothCount > 0 && (now - smoothTime[tail] > SMOOTH_MS || smoothCount == SMOOTH_CAPACITY)) {
            tail = (tail + 1) % SMOOTH_CAPACITY;
            smoothCount--;
        }
        smoothTime[smoothHead] = now;
        smoothPower[smoothHead] = power;
        smoothHead = (smoothHead + 1) % SMOOTH_CAPACITY;
        smoothCount++;

        double st = 0, stt = 0, sp = 0, stp = 0;
        for (int i = 0, k = tail; i < smoothCount; i++, k = (k + 1) % SMOOTH_CAPACITY) {
            double t = (smoothTime[k] - now) / 1000.0, p = smoothPower[k];
            st += t;
            stt += t * t;
            sp += p;
            stp += t * p;
        }
        int n = smoothCount;
        double denom = n * stt - st * st;
        //tek ölçüm ya da aynı zamanlı ölçümler varsa doğru çizilemez, ortalama kullanılır.
        double value = denom > 1e-9 ? (stt * sp - st * stp) / denom : sp / n;
        return (float) Math.max(0, value);
    }

    private void measure(long now, float power, float dt) {
        //ölçüm sırasında dokunma: sarsıntı ısınmanın gürültüsüne göre eşiği aşarsa ısınma baştan başlar.
        float shake = (float) Math.sqrt(Math.max(0, shortPower - warmPower));
        if (shake >= shakeThreshold) {
            Log.i(TAG, "measure: touched, restart. shake=" + shake + " warmup noise=" + Math.sqrt(warmPower) + " threshold=" + shakeThreshold);
            startPhase(Phase.WARMUP, now);
            reportCountdown(false, WARMUP_MS + MEASURE_MS);
            return;
        }
        measureSum += power * dt;
        measureTime += dt;
        if (now - phaseStart < MEASURE_MS) {
            reportCountdown(false, WARMUP_MS + MEASURE_MS - (now - calibStart));
            return;
        }
        //ısınma ve ölçüm gürültülerinin farkı toleranstan küçükse ölçüm kabul edilir. ısınmanın değeri sadece bu karşılaştırma içindir.
        float measurePower = measureTime > 0 ? (float) (measureSum / measureTime) : 0;
        double warmNoise = Math.sqrt(warmPower), measureNoise = Math.sqrt(measurePower);
        boolean stable = stableTolerance <= 0 || Math.abs(warmNoise - measureNoise) < stableTolerance;
        Log.i(TAG, "measure: warmup noise=" + warmNoise + " measure noise=" + measureNoise + " stable=" + stable);
        if (stable) {
            noisePower = measurePower;
            startPhase(Phase.DETECT, now);
            listener.onShakeLevel(0);
        } else {
            startPhase(Phase.WARMUP, now);
            reportCountdown(false, WARMUP_MS + MEASURE_MS);
        }
    }

    private void detect(long now, float dt) {
        float shake = (float) Math.sqrt(Math.max(0, shortPower - noisePower));
        boolean above = shake >= shakeThreshold;

        //son saniyenin en yüksek sarsıntısı ekrana bildirilir.
        reportShake = Math.max(reportShake, shake);
        if (now - lastReport >= REPORT_MS) {
            listener.onShakeLevel(reportShake);
            lastReport = now;
            reportShake = 0;
        }

        //eşiği ayarlayabilmek için ölçülen en yüksek sarsıntı periyodik loglanır.
        logShake = Math.max(logShake, shake);
        if (now - lastLog >= LOG_INTERVAL_MS) {
            Log.d(TAG, "detect: last 5 s max shake=" + logShake + " noise=" + Math.sqrt(noisePower)
                    + " (threshold=" + shakeThreshold + ")");
            lastLog = now;
            logShake = 0;
        }

        if (above && now >= detectFrom) {
            Log.i(TAG, "trigger: shake=" + shake + " noise=" + Math.sqrt(noisePower) + " threshold=" + shakeThreshold);
            detectFrom = now + TRIGGER_HOLD_MS;
            listener.onShake();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
