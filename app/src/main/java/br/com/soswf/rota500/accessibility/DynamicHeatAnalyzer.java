package br.com.soswf.rota500.accessibility;

import android.graphics.Bitmap;
import android.graphics.Color;

public final class DynamicHeatAnalyzer {
    public Result analyze(Bitmap bitmap) {
        if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) return new Result("Não lido", 0);
        int step = Math.max(4, Math.min(bitmap.getWidth(), bitmap.getHeight()) / 120);
        long samples = 0, warm = 0;
        int top = bitmap.getHeight() / 8;
        for (int y = top; y < bitmap.getHeight(); y += step) {
            for (int x = 0; x < bitmap.getWidth(); x += step) {
                int color = bitmap.getPixel(x, y);
                float[] hsv = new float[3];
                Color.colorToHSV(color, hsv);
                if (hsv[1] < 0.30f || hsv[2] < 0.35f) continue;
                samples++;
                if ((hsv[0] <= 70f || hsv[0] >= 330f) && hsv[1] >= 0.45f) warm++;
            }
        }
        double ratio = samples == 0 ? 0 : warm / (double) samples;
        String level = ratio >= 0.18 ? "Alto" : ratio >= 0.07 ? "Médio" : "Baixo/ausente";
        return new Result(level, ratio);
    }

    public static final class Result {
        public final String level;
        public final double warmRatio;
        Result(String level, double warmRatio) { this.level = level; this.warmRatio = warmRatio; }
    }
}
