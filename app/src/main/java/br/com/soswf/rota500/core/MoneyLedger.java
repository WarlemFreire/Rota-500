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

    private MoneyLedger() { }

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
