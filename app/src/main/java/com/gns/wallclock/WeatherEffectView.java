package com.gns.wallclock;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

//hava durumu ikon koduna göre saatin arkasında düşük kare hızlı efekt çizer.
//5-10 metre uzaktan anlaşılması için parçacıklar büyük ve az sayıdadır.
//ekran boyutundan bağımsız olması için boyutlar ekranın kısa kenarının %1'i (unit) cinsindendir.
//kare hızı 0 ise efekt tamamen kapalıdır ve zamanlayıcı çalışmaz.
public class WeatherEffectView extends View {
    private static final int NONE = 0, STARS = 1, CLOUDS = 2, RAIN = 3, SNOW = 4, FOG = 5;
    //diziler bu boyutta oluşturulur, çizim sırasında yeni nesne oluşturulmaz.
    private static final int MAX_COUNT = 40;
    //eski cihazların sürekli ısınmasını önlemek için kare hızı üst sınırı.
    private static final float MAX_FPS = 30;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    //parçacıkların konumu (piksel), hızı (piksel/saniye), boyutu ve fazı.
    private final float[] px = new float[MAX_COUNT];
    private final float[] py = new float[MAX_COUNT];
    private final float[] speed = new float[MAX_COUNT];
    private final float[] size = new float[MAX_COUNT];
    private final float[] phase = new float[MAX_COUNT];

    private int type = NONE;
    private int count;
    private float dropLength;
    private boolean night, thunder, flash;
    private int cloudColor, fogColor;
    private float fps = 2;
    private boolean running;
    private long lastTime, nextFlashTime;
    private float unit;

    public WeatherEffectView(Context context) {
        super(context);
        init();
    }

    public WeatherEffectView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running || fps <= 0) return;
            advance();
            invalidate();
            handler.postDelayed(this, frameDelay());
        }
    };

    private long frameDelay() {
        return Math.max(1, (long) (1000f / fps));
    }

    //0 efekti kapatır. üst sınırın üstündeki değerler sınıra çekilir.
    public void setFps(float fps) {
        this.fps = Math.max(0, Math.min(MAX_FPS, fps));
        if (running) restart();
        invalidate();
    }

    //uygulama ön plana gelince çağrılır.
    public void start() {
        running = true;
        restart();
    }

    //uygulama arka plana gidince çağrılır.
    public void stop() {
        running = false;
        handler.removeCallbacks(tick);
    }

    private void restart() {
        handler.removeCallbacks(tick);
        lastTime = SystemClock.uptimeMillis();
        if (fps > 0) handler.postDelayed(tick, frameDelay());
    }

    //ikon kodu openweathermap ikon kodudur (ör: 10d). son harf d gündüz, n gece.
    //skyColor arka plan rengidir, bulut ve sis rengi ondan türetilir.
    public void setWeather(String iconId, int skyColor) {
        night = iconId != null && iconId.endsWith("n");
        String code = iconId != null && iconId.length() >= 2 ? iconId.substring(0, 2) : "";
        type = NONE;
        count = 0;
        dropLength = 0;
        thunder = false;
        flash = false;
        switch (code) {
            case "01":
                if (night) {
                    type = STARS;
                    count = 16;
                }
                break;
            case "02":
                type = CLOUDS;
                count = 2;
                break;
            case "03":
                type = CLOUDS;
                count = 4;
                break;
            case "04":
                type = CLOUDS;
                count = 6;
                break;
            case "09":
                type = RAIN;
                count = 14;
                dropLength = 5;
                break;
            case "10":
                type = RAIN;
                count = 26;
                dropLength = 8;
                break;
            case "11":
                type = RAIN;
                count = 26;
                dropLength = 8;
                thunder = true;
                break;
            case "13":
                type = SNOW;
                count = 24;
                break;
            case "50":
                type = FOG;
                count = 4;
                break;
            default:
                break;
        }
        cloudColor = blend(skyColor, Color.WHITE, night ? 0.20f : 0.15f);
        fogColor = blend(skyColor, Color.WHITE, night ? 0.14f : 0.10f);
        nextFlashTime = SystemClock.uptimeMillis() + 3000 + random.nextInt(5000);
        populate();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        unit = Math.min(w, h) / 100f;
        populate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacks(tick);
    }

    //parçacıkların başlangıç değerleri rastgele atanır.
    private void populate() {
        if (unit <= 0) return;
        float w = getWidth(), h = getHeight();
        for (int i = 0; i < count; i++) {
            switch (type) {
                case STARS:
                    px[i] = random.nextFloat() * w;
                    py[i] = random.nextFloat() * h;
                    size[i] = (0.8f + random.nextFloat() * 0.8f) * unit;
                    phase[i] = random.nextFloat();
                    break;
                case CLOUDS:
                    size[i] = (30 + random.nextFloat() * 20) * unit;
                    px[i] = random.nextFloat() * (w + size[i]) - size[i] * 0.5f;
                    py[i] = h * (0.05f + 0.8f * (i + random.nextFloat() * 0.8f) / count);
                    speed[i] = (2.5f + random.nextFloat() * 2.5f) * unit;
                    break;
                case RAIN:
                    size[i] = dropLength * unit;
                    px[i] = random.nextFloat() * (w + 0.3f * h);
                    py[i] = random.nextFloat() * h;
                    speed[i] = (30 + random.nextFloat() * 25) * unit;
                    break;
                case SNOW:
                    size[i] = (1.2f + random.nextFloat()) * unit;
                    px[i] = random.nextFloat() * w;
                    py[i] = random.nextFloat() * h;
                    speed[i] = (5 + random.nextFloat() * 5) * unit;
                    phase[i] = random.nextFloat() * 6.28f;
                    break;
                case FOG:
                    size[i] = w * 0.7f;
                    px[i] = random.nextFloat() * w;
                    py[i] = h * (i + 0.5f) / count;
                    speed[i] = (1.5f + random.nextFloat() * 1.5f) * unit;
                    break;
                default:
                    break;
            }
        }
    }

    //hareket geçen süreye göre hesaplanır, böylece kare hızından bağımsız aynı hızda akar.
    private void advance() {
        long now = SystemClock.uptimeMillis();
        float dt = Math.min((now - lastTime) / 1000f, 2f);
        lastTime = now;
        float w = getWidth(), h = getHeight();
        for (int i = 0; i < count; i++) {
            switch (type) {
                case STARS:
                    //her karede yıldızların bir kısmının parlaklığı değişir. kırpışma böyle sağlanır.
                    if (random.nextFloat() < 0.35f) phase[i] = random.nextFloat();
                    break;
                case CLOUDS:
                    px[i] += speed[i] * dt;
                    if (px[i] - size[i] * 0.5f > w) px[i] = -size[i] * 0.5f;
                    break;
                case RAIN:
                    py[i] += speed[i] * dt;
                    px[i] -= 0.3f * speed[i] * dt;
                    if (py[i] > h || px[i] < -size[i]) {
                        py[i] = -size[i];
                        px[i] = random.nextFloat() * (w + 0.3f * h);
                    }
                    break;
                case SNOW:
                    py[i] += speed[i] * dt;
                    phase[i] += 1.2f * dt;
                    if (py[i] - size[i] > h) {
                        py[i] = -size[i];
                        px[i] = random.nextFloat() * w;
                    }
                    break;
                case FOG:
                    px[i] += speed[i] * dt;
                    if (px[i] > w) px[i] = -size[i];
                    break;
                default:
                    break;
            }
        }
        //şimşek seyrek ve yumuşaktır. iki parlama arası en az 5 saniye vardır.
        flash = false;
        if (thunder && now >= nextFlashTime) {
            flash = true;
            nextFlashTime = now + 5000 + random.nextInt(9000);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (fps <= 0 || type == NONE || unit <= 0) return;
        if (flash) canvas.drawColor(Color.argb(48, 255, 255, 255));
        switch (type) {
            case STARS:
                for (int i = 0; i < count; i++) {
                    paint.setColor(Color.argb(70 + (int) (phase[i] * 150), 255, 255, 255));
                    canvas.drawCircle(px[i], py[i], size[i], paint);
                }
                break;
            case CLOUDS:
                paint.setColor(cloudColor);
                for (int i = 0; i < count; i++)
                    drawCloud(canvas, px[i], py[i], size[i]);
                break;
            case RAIN:
                paint.setColor(Color.argb(160, 190, 220, 255));
                paint.setStrokeWidth(0.8f * unit);
                for (int i = 0; i < count; i++)
                    canvas.drawLine(px[i], py[i], px[i] - 0.3f * size[i], py[i] + size[i], paint);
                break;
            case SNOW:
                paint.setColor(Color.argb(210, 255, 255, 255));
                for (int i = 0; i < count; i++)
                    canvas.drawCircle(px[i] + (float) Math.sin(phase[i]) * 2 * unit, py[i], size[i], paint);
                break;
            case FOG:
                paint.setColor(fogColor);
                for (int i = 0; i < count; i++) {
                    rect.set(px[i], py[i] - 4.5f * unit, px[i] + size[i], py[i] + 4.5f * unit);
                    canvas.drawRoundRect(rect, 4.5f * unit, 4.5f * unit, paint);
                }
                break;
            default:
                break;
        }
    }

    //bulut düz renkli çizilir. üst üste binen saydam daireler lekeli görünür, ayrıca eski cihazlarda pahalıdır.
    private void drawCloud(Canvas canvas, float cx, float cy, float w) {
        rect.set(cx - 0.5f * w, cy, cx + 0.5f * w, cy + 0.3f * w);
        canvas.drawRoundRect(rect, 0.15f * w, 0.15f * w, paint);
        canvas.drawCircle(cx - 0.20f * w, cy + 0.02f * w, 0.20f * w, paint);
        canvas.drawCircle(cx + 0.05f * w, cy - 0.06f * w, 0.26f * w, paint);
        canvas.drawCircle(cx + 0.28f * w, cy + 0.06f * w, 0.18f * w, paint);
    }

    private static int blend(int base, int over, float t) {
        return Color.rgb(
                Math.round(Color.red(base) * (1 - t) + Color.red(over) * t),
                Math.round(Color.green(base) * (1 - t) + Color.green(over) * t),
                Math.round(Color.blue(base) * (1 - t) + Color.blue(over) * t));
    }
}
