package br.com.soswf.rota500.core;

import java.util.List;

public final class WorkMath {
    public record Span(long start, long end) { }
    public record Odometer(long at, double km) { }

    private WorkMath() { }

    public static long activeMillis(long from, long until, List<Span> sessions, List<Span> pauses) {
        long active = 0;
        for (Span session : sessions) {
            long start = Math.max(from, session.start());
            long end = Math.min(until, session.end());
            if (end <= start) continue;
            active += end - start;
            for (Span pause : pauses) {
                long overlap = Math.max(0, Math.min(end, pause.end()) - Math.max(start, pause.start()));
                active -= overlap;
            }
        }
        return Math.max(0, active);
    }

    /** Invalid entries are ignored so a later good odometer anchor repairs the reading. */
    public static Double odometerKm(List<Odometer> points) {
        Odometer first = null, previous = null;
        for (Odometer point : points) {
            if (!Double.isFinite(point.km()) || point.km() <= 0) continue;
            if (previous != null) {
                double elapsedHours = Math.max(0.5, (point.at() - previous.at()) / 3_600_000.0);
                if (point.at() <= previous.at() || point.km() < previous.km()
                        || (point.km() - previous.km()) / elapsedHours > 120) continue;
            }
            if (first == null) first = point;
            previous = point;
        }
        return first == null || previous == first ? null : previous.km() - first.km();
    }
}
