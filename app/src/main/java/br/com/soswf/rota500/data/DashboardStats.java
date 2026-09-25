package br.com.soswf.rota500.data;

public final class DashboardStats {
    public final long sessionId;
    public final long startedAt;
    public final long grossCents;
    public final long activeGrossCents;
    public final int priorWorkedMinutes;
    public final int offersLastHour;
    public final int viableOffersLastHour;
    public final int idleMinutes;
    public final double robustGrossPerHour;
    public final double learnedGrossPerHour;
    public final double emptyKm;
    public final String currentZone;
    public final boolean unsafeZone;

    public DashboardStats(long sessionId, long startedAt, long grossCents, long activeGrossCents,
            int priorWorkedMinutes, int offersLastHour,
            int viableOffersLastHour, int idleMinutes, double robustGrossPerHour,
            double learnedGrossPerHour, double emptyKm, String currentZone, boolean unsafeZone) {
        this.sessionId = sessionId;
        this.startedAt = startedAt;
        this.grossCents = grossCents;
        this.activeGrossCents = activeGrossCents;
        this.priorWorkedMinutes = priorWorkedMinutes;
        this.offersLastHour = offersLastHour;
        this.viableOffersLastHour = viableOffersLastHour;
        this.idleMinutes = idleMinutes;
        this.robustGrossPerHour = robustGrossPerHour;
        this.learnedGrossPerHour = learnedGrossPerHour;
        this.emptyKm = emptyKm;
        this.currentZone = currentZone;
        this.unsafeZone = unsafeZone;
    }

    public boolean hasActiveSession() { return sessionId > 0; }
    public int workedMinutes(long now) {
        return priorWorkedMinutes + (startedAt <= 0 ? 0 : (int) Math.max(0, (now - startedAt) / 60_000L));
    }
}
