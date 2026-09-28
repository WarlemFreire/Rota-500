package br.com.soswf.rota500.core;

/** Controls expensive OCR while keeping the first changed offer responsive. */
public final class OcrCadence {
    private static final long QUIET_MS = 700L;
    private static final long BURST_MS = 2_500L;
    private static final long RECENT_OFFER_MS = 2_500L;
    private static final long NEW_SCENE_INTERVAL_MS = 180L;
    private static final long BURST_INTERVAL_MS = 550L;
    private static final long MOTION_INTERVAL_MS = 2_200L;
    private static final long IDLE_INTERVAL_MS = 4_500L;
    private static final int CHANGE_POINTS = 3;
    private static final int SCENE_POINTS = 90;

    private long lastReadAt;
    private long lastMotionAt;
    private long burstUntil;
    private long urgentUntil;
    private long recentOfferUntil;
    private boolean initial = true;
    private boolean lastStrongChange;

    public boolean shouldRead(long now, int changedPoints, boolean force) {
        boolean moving = changedPoints >= CHANGE_POINTS;
        boolean strongChange = changedPoints >= SCENE_POINTS;
        boolean newScene = moving && (initial || now - lastMotionAt >= QUIET_MS
                || (strongChange && !lastStrongChange));
        if (moving) {
            if (newScene) {
                burstUntil = now + BURST_MS;
                urgentUntil = now + 800L;
            }
            lastMotionAt = now;
        }
        lastStrongChange = strongChange;
        long interval = now < burstUntil || now < recentOfferUntil
                ? BURST_INTERVAL_MS : moving ? MOTION_INTERVAL_MS : IDLE_INTERVAL_MS;
        if (now < urgentUntil) interval = NEW_SCENE_INTERVAL_MS;
        if (!force && !initial && now - lastReadAt < interval) return false;
        initial = false;
        lastReadAt = now;
        urgentUntil = 0;
        return true;
    }

    public void offerSeen(long now) {
        recentOfferUntil = now + RECENT_OFFER_MS;
    }
}
