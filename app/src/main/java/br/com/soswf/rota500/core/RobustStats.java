package br.com.soswf.rota500.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RobustStats {
    private RobustStats() {}

    public static double median(List<Double> raw) {
        if (raw == null || raw.isEmpty()) return 0;
        List<Double> values = new ArrayList<>(raw);
        Collections.sort(values);
        int middle = values.size() / 2;
        return values.size() % 2 == 0
                ? (values.get(middle - 1) + values.get(middle)) / 2.0
                : values.get(middle);
    }

    public static double trimmedMean(List<Double> raw, double trimFraction) {
        if (raw == null || raw.isEmpty()) return 0;
        List<Double> values = new ArrayList<>(raw);
        Collections.sort(values);
        int trim = Math.min((int) Math.floor(values.size() * trimFraction), (values.size() - 1) / 2);
        double sum = 0;
        int count = 0;
        for (int i = trim; i < values.size() - trim; i++) {
            sum += values.get(i);
            count++;
        }
        return count == 0 ? 0 : sum / count;
    }
}
