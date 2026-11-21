package br.com.soswf.rota500.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One timeline for earnings. Platform readings replace; one-off rides add. */
public final class MoneyLedger {
    public record Entry(long id, long at, String kind, String source, long cents,
            long baselineCents, boolean voided) { }

    public record Balance(long totalCents, long legacyCents, long extraCents,
            Map<String, Long> platforms) {
        public long platformCents(String source) { return platforms.getOrDefault(source, 0L); }
    }

    /** Uma leitura de total de plataforma; newCalendarDay indica que mudou a data desde a anterior. */
    public record Reading(long cents, boolean newCalendarDay) { }

    private MoneyLedger() { }

    /**
     * Base de cálculo depois que o motorista confirma que a plataforma zerou o total do dia.
     * O valor gravado continua cru (o que aparece na Uber/99); a base negativa soma o anterior.
     */
    public static long baselineAfterReset(long latestBaseline, long latestRawCents) {
        return latestBaseline - latestRawCents;
    }

    /**
     * Recalcula as bases de uma plataforma quando leituras de datas diferentes passam a
     * pertencer à mesma jornada. Um total menor logo após a virada de data é tratado
     * como zeramento da plataforma, não como correção.
     */
    public static long[] rebasedBaselines(List<Reading> readings, long firstBaseline) {
        long[] baselines = new long[readings.size()];
        long baseline = firstBaseline;
        long lastRaw = -1;
        for (int i = 0; i < readings.size(); i++) {
            Reading reading = readings.get(i);
            if (lastRaw >= 0 && reading.newCalendarDay() && reading.cents() < lastRaw) {
                baseline = baselineAfterReset(baseline, lastRaw);
            }
            baselines[i] = baseline;
            lastRaw = reading.cents();
        }
        return baselines;
    }

    /** Entries must be in timestamp and ID order. */
    public static Balance at(List<Entry> entries, long untilInclusive) {
        Map<String, Long> platforms = new LinkedHashMap<>();
        long extras = 0, legacy = 0;
        for (Entry entry : entries) {
            if (entry.at() > untilInclusive || entry.voided()) continue;
            switch (entry.kind()) {
                case "PLATFORM" -> platforms.put(entry.source(),
                        Math.max(0, entry.cents() - entry.baselineCents()));
                case "EXTRA" -> extras += entry.cents();
                case "LEGACY" -> legacy += entry.cents();
                default -> throw new IllegalArgumentException("Tipo de lançamento desconhecido");
            }
        }
        long total = legacy + extras;
        for (long amount : platforms.values()) total += amount;
        return new Balance(total, legacy, extras, Map.copyOf(platforms));
    }
}
