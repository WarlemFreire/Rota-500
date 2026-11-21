package br.com.soswf.rota500.location;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

import br.com.soswf.rota500.ai.StrategyCoordinator;
import br.com.soswf.rota500.backup.BackupManager;
import br.com.soswf.rota500.capture.ScreenCaptureService;
import br.com.soswf.rota500.core.JourneyWatch;
import br.com.soswf.rota500.core.StrategyAdvice;
import br.com.soswf.rota500.data.AppDatabase;
import br.com.soswf.rota500.data.AppPreferences;
import br.com.soswf.rota500.data.DashboardStats;
import br.com.soswf.rota500.data.FinancialToday;
import br.com.soswf.rota500.ui.MainActivity;

/**
 * Mantém a jornada visível e o GPS ligado. Também vigia a jornada esquecida (Q31-Q43):
 * notificação permanente com tempo e bruto, perguntas após inatividade e encerramento
 * automático após 3 perguntas sem resposta.
 */
public final class TrackingService extends Service implements LocationListener {
    public static final String ACTION_START = "br.com.soswf.rota500.START_SHIFT";
    public static final String ACTION_STOP = "br.com.soswf.rota500.STOP_SHIFT";
    public static final String ACTION_REFRESH = "br.com.soswf.rota500.REFRESH_SHIFT";
    static final String ACTION_PAUSE = "br.com.soswf.rota500.PAUSE_SHIFT";
    static final String ACTION_RESUME = "br.com.soswf.rota500.RESUME_SHIFT";
    static final String ACTION_ACK = "br.com.soswf.rota500.ACK_SHIFT";
    static final String ACTION_RESHOW = "br.com.soswf.rota500.RESHOW_SHIFT";
    static final String CHANNEL = "rota500_tracking";
    static final String CHANNEL_QUESTIONS = "rota500_journey_questions";
    static final int NOTIFICATION_ID = 500;
    static final int QUESTION_ID = 503;
    static final int ENDED_ID = 504;
    private static final long WATCH_INTERVAL = 5 * 60 * 1000L;
    private static final long STRATEGY_INTERVAL = 15 * 60 * 1000L;

    private LocationManager locationManager;
    private AppDatabase database;
    private AppPreferences preferences;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastExternalStrategyAt;
    private long lastStrategyAt;
    private String strategyLine = "";
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (database.activeSessionId() <= 0) { stopTracking(); return; }
            updateStrategy();
            if (watchJourney()) return;
            updateNotification();
            handler.postDelayed(this, WATCH_INTERVAL);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        database = AppDatabase.get(this);
        preferences = new AppPreferences(this);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        createChannels(this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopTracking();
            return START_NOT_STICKY;
        }
        if (database.activeSessionId() <= 0) {
            stopTracking();
            return START_NOT_STICKY;
        }
        try {
            startForeground(NOTIFICATION_ID, journeyNotification());
        } catch (RuntimeException error) {
            // Sem permissão de localização, ou iniciado em segundo plano: o Android 14+ recusa o
            // serviço de localização. A notificação da jornada continua visível mesmo assim.
            notify(this, NOTIFICATION_ID, journeyNotification());
        }
        if (ACTION_PAUSE.equals(action) && database.openPauseId() <= 0) database.openPause("Outro");
        if (ACTION_RESUME.equals(action)) database.closePause();
        if (ACTION_ACK.equals(action)) {
            preferences.acknowledgeWatch(System.currentTimeMillis());
            cancel(this, QUESTION_ID);
        }
        if (ACTION_START.equals(action)) startTracking();
        handler.removeCallbacks(tick);
        handler.post(tick);
        return START_STICKY;
    }

    private void startTracking() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 15_000L, 30f, this);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30_000L, 75f, this);
        } catch (IllegalArgumentException | SecurityException ignored) { }
    }

    private void stopTracking() {
        handler.removeCallbacksAndMessages(null);
        if (locationManager != null) locationManager.removeUpdates(this);
        cancel(this, QUESTION_ID);
        stopForeground(STOP_FOREGROUND_REMOVE);
        // Remove também o aviso postado pelo receptor de reinício, que não pertence ao serviço.
        cancel(this, NOTIFICATION_ID);
        stopSelf();
    }

    private void updateStrategy() {
        long now = System.currentTimeMillis();
        if (lastStrategyAt != 0 && now - lastStrategyAt < STRATEGY_INTERVAL) return;
        lastStrategyAt = now;
        if (database.openPauseId() > 0) { strategyLine = ""; return; }
        StrategyCoordinator coordinator = new StrategyCoordinator(this);
        long configured = preferences.strategyIntervalMinutes() * 60_000L;
        if (lastExternalStrategyAt == 0 || now - lastExternalStrategyAt >= configured) {
            lastExternalStrategyAt = now;
            coordinator.requestBestAdvice((advice, source, warning) -> {
                strategyLine = advice.title;
                updateNotification();
            });
        } else {
            StrategyAdvice advice = coordinator.localAdvice();
            strategyLine = advice.title;
        }
    }

    /** @return true quando a jornada foi encerrada por inatividade. */
    private boolean watchJourney() {
        long session = database.activeSessionId();
        long now = System.currentTimeMillis();
        long start = database.activeSessionStart();
        boolean paused = database.openPauseId() > 0;
        long ack = preferences.watchAcknowledgedAt();
        long reference;
        long elapsed;
        if (paused) {
            reference = Math.max(database.openPauseStart(), ack);
            elapsed = now - reference;
        } else {
            reference = Math.max(Math.max(start, database.lastGainAt(session)),
                    Math.max(database.lastPauseEnd(session), ack));
            elapsed = database.sessionActiveMillis(session, reference, now, now);
        }
        int prompts = preferences.watchPrompts();
        if (reference != preferences.watchReference() || paused != preferences.watchPaused()) {
            // Ganho novo, pausa, retomada ou "Sim": a contagem recomeça.
            prompts = 0;
            cancel(this, QUESTION_ID);
        }
        JourneyWatch.Decision decision = JourneyWatch.evaluate(paused, elapsed, prompts);
        switch (decision.action()) {
            case ASK_ACTIVE, ASK_PAUSE -> {
                preferences.setWatch(reference, decision.promptNumber(), paused);
                ask(paused, elapsed);
            }
            case AUTO_END -> {
                long endAt = paused ? database.openPauseStart()
                        : JourneyWatch.presumedEnd(start, database.lastGainAt(session),
                        database.lastMovementAt(session), now);
                autoEnd(session, endAt);
                return true;
            }
            default -> preferences.setWatch(reference, prompts, paused);
        }
        return false;
    }

    private void ask(boolean paused, long elapsed) {
        long hours = Math.max(1, elapsed / 3_600_000L);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_QUESTIONS)
                .setSmallIcon(br.com.soswf.rota500.R.drawable.ic_launcher)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(openApp(this, null, 10));
        if (paused) {
            builder.setContentTitle("Ainda em pausa?")
                    .setContentText("Pausa aberta há " + hours + " h. Retome ou encerre a jornada.")
                    .addAction(action("Retomar", ACTION_RESUME, 11))
                    .addAction(new Notification.Action.Builder(null, "Encerrar",
                            openApp(this, MainActivity.OPEN_END, 12)).build());
        } else {
            builder.setContentTitle("Ainda trabalhando?")
                    .setContentText(hours + " h sem registrar ganho. Sem resposta, a jornada será encerrada.")
                    .addAction(action("Sim", ACTION_ACK, 13))
                    .addAction(new Notification.Action.Builder(null, "Registrar ganho",
                            openApp(this, MainActivity.OPEN_REGISTER, 14)).build())
                    .addAction(new Notification.Action.Builder(null, "Encerrar",
                            openApp(this, MainActivity.OPEN_END, 15)).build());
        }
        notify(this, QUESTION_ID, builder.build());
    }

    private void autoEnd(long session, long endAt) {
        DashboardStats stats = database.dashboardStats();
        database.endActiveSession(stats.currentZone, endAt);
        preferences.setAutoEndedSession(session);
        preferences.setWatch(0, 0, false);
        startService(new Intent(this, ScreenCaptureService.class).setAction(ScreenCaptureService.ACTION_STOP));
        preferences.setCaptureActive(false);
        backupAfterShift();
        String time = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.forLanguageTag("pt-BR")).format(new Date(endAt));
        Notification ended = new Notification.Builder(this, CHANNEL_QUESTIONS)
                .setSmallIcon(br.com.soswf.rota500.R.drawable.ic_launcher)
                .setContentTitle("Jornada encerrada por inatividade")
                .setContentText("Três perguntas sem resposta. Encerrada às " + time + ".")
                .setAutoCancel(true)
                .setContentIntent(openApp(this, null, 16))
                .addAction(new Notification.Action.Builder(null, "Reabrir",
                        openApp(this, MainActivity.OPEN_REOPEN, 17)).build())
                .build();
        notify(this, ENDED_ID, ended);
        stopTracking();
    }

    private void backupAfterShift() {
        String folder = preferences.backupTreeUri();
        if (folder.isBlank()) return;
        new Thread(() -> {
            try {
                new BackupManager(this).writeDataBackup(android.net.Uri.parse(folder));
                preferences.setLastBackupAt(System.currentTimeMillis());
            } catch (Exception ignored) { }
        }, "rota500-auto-backup").start();
    }

    @Override public void onLocationChanged(Location location) {
        database.insertLocation(location);
        if (database.isCoordinateUnsafe(location.getLatitude(), location.getLongitude())) {
            strategyLine = "Atenção: área marcada como insegura";
            updateNotification();
        }
    }

    @Override public void onProviderEnabled(String provider) { }
    @Override public void onProviderDisabled(String provider) { }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (locationManager != null) locationManager.removeUpdates(this);
        super.onDestroy();
    }

    static void createChannels(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Jornada e localização", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Mostra a jornada aberta, com tempo ativo e ganho do dia.");
        manager.createNotificationChannel(channel);
        // Q41: perguntas aparecem no topo e vibram uma vez, sem som.
        NotificationChannel questions = new NotificationChannel(CHANNEL_QUESTIONS, "Perguntas sobre a jornada",
                NotificationManager.IMPORTANCE_HIGH);
        questions.setDescription("Pergunta se você ainda está trabalhando quando a jornada fica muito tempo sem ganho.");
        questions.setSound(null, null);
        questions.enableVibration(true);
        questions.setVibrationPattern(new long[]{0, 350});
        manager.createNotificationChannel(questions);
    }

    /** Q31: tempo ativo e bruto do dia, botões Pausar/Retomar e Encerrar, estratégia embaixo. */
    private Notification journeyNotification() {
        boolean paused = database.openPauseId() > 0;
        FinancialToday day = database.financialToday(preferences);
        long minutes = day.activeMinutes();
        String summary = String.format(Locale.forLanguageTag("pt-BR"), "Tempo ativo %d:%02d · R$ %s hoje",
                minutes / 60, minutes % 60, wholeMoney(day.balance().totalCents()));
        String second = paused ? "Pausado: " + database.openPauseReason() : strategyLine;
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(br.com.soswf.rota500.R.drawable.ic_launcher)
                .setContentTitle(paused ? "Rota 500 · jornada pausada" : "Rota 500 · em jornada")
                .setContentText(summary)
                .setContentIntent(openApp(this, null, 0))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setDeleteIntent(service(ACTION_RESHOW, 18))
                .addAction(paused ? action("Retomar", ACTION_RESUME, 19) : action("Pausar", ACTION_PAUSE, 20))
                .addAction(new Notification.Action.Builder(null, "Encerrar",
                        openApp(this, MainActivity.OPEN_END, 21)).build());
        if (!second.isBlank()) builder.setStyle(new Notification.BigTextStyle().bigText(summary + "\n" + second));
        return builder.build();
    }

    private void updateNotification() {
        if (database.activeSessionId() <= 0) return;
        notify(this, NOTIFICATION_ID, journeyNotification());
    }

    private Notification.Action action(String label, String serviceAction, int code) {
        return new Notification.Action.Builder(null, label, service(serviceAction, code)).build();
    }

    private PendingIntent service(String action, int code) {
        return PendingIntent.getService(this, code, new Intent(this, TrackingService.class).setAction(action),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static PendingIntent openApp(Context context, String openAction, int code) {
        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (openAction != null) open.putExtra(MainActivity.EXTRA_OPEN, openAction);
        return PendingIntent.getActivity(context, code, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static void notify(Context context, int id, Notification notification) {
        try { context.getSystemService(NotificationManager.class).notify(id, notification); }
        catch (RuntimeException ignored) { }
    }

    static void cancel(Context context, int id) {
        context.getSystemService(NotificationManager.class).cancel(id);
    }

    private static String wholeMoney(long cents) {
        return String.format(Locale.forLanguageTag("pt-BR"), "%,.0f", cents / 100.0);
    }
}
