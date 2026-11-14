package com.gns.wallclock;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;
import android.util.SparseIntArray;

//Weather Icons (erikflowers/weather-icons) yazı tipi. OpenWeatherMap durum kodu -> ikon karakteri tablosu setin wi-owm.less dosyasından alındı.
public class WeatherIcons {
    private static final int[] DAY = {
            200, 0xf010, 201, 0xf010, 202, 0xf010, 210, 0xf005, 211, 0xf005, 212, 0xf005, 221, 0xf005, 230, 0xf010, 231, 0xf010, 232, 0xf010,
            300, 0xf00b, 301, 0xf00b, 302, 0xf008, 310, 0xf008, 311, 0xf008, 312, 0xf008, 313, 0xf008, 314, 0xf008, 321, 0xf00b,
            500, 0xf00b, 501, 0xf008, 502, 0xf008, 503, 0xf008, 504, 0xf008, 511, 0xf006, 520, 0xf009, 521, 0xf009, 522, 0xf009, 531, 0xf00e,
            600, 0xf00a, 601, 0xf0b2, 602, 0xf00a, 611, 0xf006, 612, 0xf006, 615, 0xf006, 616, 0xf006, 620, 0xf006, 621, 0xf00a, 622, 0xf00a,
            701, 0xf003, 711, 0xf062, 721, 0xf0b6, 731, 0xf063, 741, 0xf003, 761, 0xf063, 762, 0xf063, 781, 0xf056,
            800, 0xf00d, 801, 0xf002, 802, 0xf002, 803, 0xf013, 804, 0xf013,
            900, 0xf056, 902, 0xf073, 903, 0xf076, 904, 0xf072, 906, 0xf004, 957, 0xf050};
    private static final int[] NIGHT = {
            200, 0xf02d, 201, 0xf02d, 202, 0xf02d, 210, 0xf025, 211, 0xf025, 212, 0xf025, 221, 0xf025, 230, 0xf02d, 231, 0xf02d, 232, 0xf02d,
            300, 0xf02b, 301, 0xf02b, 302, 0xf028, 310, 0xf028, 311, 0xf028, 312, 0xf028, 313, 0xf028, 314, 0xf028, 321, 0xf02b,
            500, 0xf02b, 501, 0xf028, 502, 0xf028, 503, 0xf028, 504, 0xf028, 511, 0xf026, 520, 0xf029, 521, 0xf029, 522, 0xf029, 531, 0xf02c,
            600, 0xf02a, 601, 0xf0b4, 602, 0xf02a, 611, 0xf026, 612, 0xf026, 615, 0xf026, 616, 0xf026, 620, 0xf026, 621, 0xf02a, 622, 0xf02a,
            701, 0xf04a, 711, 0xf062, 721, 0xf0b6, 731, 0xf063, 741, 0xf04a, 761, 0xf063, 762, 0xf063, 781, 0xf056,
            800, 0xf02e, 801, 0xf081, 802, 0xf086, 803, 0xf013, 804, 0xf013,
            900, 0xf056, 902, 0xf073, 903, 0xf076, 904, 0xf072, 906, 0xf024, 957, 0xf050};
    private static final int CLOUDY = 0xf013;
    private static final SparseIntArray day = toMap(DAY), night = toMap(NIGHT);
    private static Typeface typeface;

    private static SparseIntArray toMap(int[] pairs) {
        SparseIntArray map = new SparseIntArray(pairs.length / 2);
        for (int i = 0; i < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]);
        return map;
    }

    public static String glyph(int conditionId, boolean isNight) {
        return String.valueOf((char) (isNight ? night : day).get(conditionId, CLOUDY));
    }

    public static Typeface typeface(Context context) {
        if (typeface == null) typeface = Typeface.createFromAsset(context.getAssets(), "fonts/weathericons.ttf");
        return typeface;
    }

    //TypefaceSpan(Typeface) android 9 ile geldi, eski cihazlar için yazı tipi elle uygulanır.
    public static class Span extends MetricAffectingSpan {
        private final Typeface typeface;

        public Span(Typeface typeface) {
            this.typeface = typeface;
        }

        @Override
        public void updateDrawState(TextPaint paint) {
            apply(paint);
        }

        @Override
        public void updateMeasureState(TextPaint paint) {
            apply(paint);
        }

        private void apply(Paint paint) {
            paint.setTypeface(typeface);
        }
    }
}
