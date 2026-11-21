package br.com.soswf.rota500.core;

import java.util.List;

/** Distância rodada pelos pontos de GPS, com filtros contra pontos imprecisos e saltos. */
public final class GpsDistance {
    public static final double MAX_ACCURACY_M = 50;
    public static final double MAX_SPEED_KMH = 150;
    /** Deslocamento mínimo entre pontos aceitos para considerar que o carro andou. */
    public static final double MOVEMENT_M = 150;

    public record Point(long at, double latitude, double longitude, double accuracyM) { }

    private GpsDistance() { }

    /** Points in chronological order. Segments that overlap a pause are not counted. */
    public static double km(List<Point> points, List<WorkMath.Span> pauses) {
        double meters = 0;
        Point previous = null;
        for (Point point : points) {
            if (!usable(point) || insidePause(point.at(), pauses)) continue;
            if (previous != null) {
                if (point.at() <= previous.at()) continue;
                double distance = meters(previous, point);
                double hours = (point.at() - previous.at()) / 3_600_000.0;
                // Um salto impossível descarta o ponto novo e mantém a âncora anterior.
                if (distance / 1000.0 / hours > MAX_SPEED_KMH) continue;
                if (!overlapsPause(previous.at(), point.at(), pauses)) meters += distance;
            }
            previous = point;
        }
        return meters / 1000.0;
    }

    /** Última vez em que a posição mudou de forma relevante; 0 se nunca houve movimento. */
    public static long lastMovementAt(List<Point> points) {
        long last = 0;
        Point anchor = null;
        for (Point point : points) {
            if (!usable(point)) continue;
            if (anchor == null) { anchor = point; continue; }
            if (meters(anchor, point) >= MOVEMENT_M) {
                last = point.at();
                anchor = point;
            }
        }
        return last;
    }

    static double meters(Point a, Point b) {
        double lat1 = Math.toRadians(a.latitude()), lat2 = Math.toRadians(b.latitude());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * 6_371_000.0 * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    private static boolean usable(Point point) {
        return Double.isFinite(point.latitude()) && Double.isFinite(point.longitude())
                && point.accuracyM() >= 0 && point.accuracyM() <= MAX_ACCURACY_M;
    }

    private static boolean insidePause(long at, List<WorkMath.Span> pauses) {
        for (WorkMath.Span pause : pauses) if (at >= pause.start() && at < pause.end()) return true;
        return false;
    }

    private static boolean overlapsPause(long from, long to, List<WorkMath.Span> pauses) {
        for (WorkMath.Span pause : pauses) if (pause.start() < to && pause.end() > from) return true;
        return false;
    }
}
