package br.com.soswf.rota500.location;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import br.com.soswf.rota500.ai.StrategyCoordinator;
import br.com.soswf.rota500.backup.BackupManager;
import br.com.soswf.rota500.core.StrategyAdvice;
import br.com.soswf.rota500.data.AppDatabase;
import br.com.soswf.rota500.data.AppPreferences;
import br.com.soswf.rota500.data.DashboardStats;
import br.com.soswf.rota500.ui.MainActivity;

public final class TrackingService extends Service implements LocationListener {
    public static final String ACTION_START = "br.com.soswf.rota500.START_SHIFT";
    public static final String ACTION_STOP = "br.com.soswf.rota500.STOP_SHIFT";
    private static final String CHANNEL = "rota500_tracking";
    private static final int NOTIFICATION_ID = 500;
    private static final long CHECK_INTERVAL = 15 * 60 * 1000L;

    private LocationManager locationManager;
    private AppDatabase database;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastExternalStrategyAt;
    private final Runnable strategyTick = new Runnable() {
        @Override public void run() {
            if (database != null && database.activeSessionId() > 0) {
                DashboardStats stats = database.dashboardStats();
                if (stats.workedMinutes(System.currentTimeMillis()) >= 1080) {
                    database.endActiveSession(stats.currentZone);
                    backupAfterShift();
                    stopTracking();
                    return;
                }
                if (database.openPauseId() > 0) {
                    updateNotification("Jornada pausada");
                    handler.postDelayed(this, CHECK_INTERVAL);
                    return;
                }
                StrategyCoordinator coordinator = new StrategyCoordinator(TrackingService.this);
                long configured = new AppPreferences(TrackingService.this).strategyIntervalMinutes() * 60_000L;
                if (lastExternalStrategyAt == 0 || System.currentTimeMillis() - lastExternalStrategyAt >= configured) {
                    lastExternalStrategyAt = System.currentTimeMillis();
                    coordinator.requestBestAdvice((advice, source, warning) -> updateNotification(advice.title + " • atualize o bruto"));
                } else {
                    StrategyAdvice advice = coordinator.localAdvice();
                    updateNotification(advice.title + " • atualize o bruto");
                }
                handler.postDelayed(this, CHECK_INTERVAL);
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        database = AppDatabase.get(this);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopTracking();
            return START_NOT_STICKY;
        }
        if (database.activeSessionId() <= 0) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(NOTIFICATION_ID, notification("Coletando dados da jornada"));
        startTracking();
        handler.removeCallbacks(strategyTick);
        handler.postDelayed(strategyTick, 60_000L);
        return START_STICKY;
    }

    private void startTracking() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 15_000L, 30f, this);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30_000L, 75f, this);
        } catch (IllegalArgumentException ignored) { }
    }

    private void stopTracking() {
        handler.removeCallbacksAndMessages(null);
        if (locationManager != null) locationManager.removeUpdates(this);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void backupAfterShift() {
        String folder = new AppPreferences(this).backupTreeUri();
        if (folder.isBlank()) return;
        new Thread(() -> {
            try {
                new BackupManager(this).writeDataBackup(android.net.Uri.parse(folder));
                new AppPreferences(this).setLastBackupAt(System.currentTimeMillis());
            } catch (Exception ignored) { }
        }, "rota500-auto-backup").start();
    }

    @Override public void onLocationChanged(Location location) {
        database.insertLocation(location);
        if (database.isCoordinateUnsafe(location.getLatitude(), location.getLongitude())) {
            updateNotification("Atenção: área marcada como insegura");
        }
    }

    @Override public void onProviderEnabled(String provider) { }
    @Override public void onProviderDisabled(String provider) { }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() { stopTracking(); super.onDestroy(); }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Jornada e localização", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Mantém a coleta de localização ativa somente durante a jornada.");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification notification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(br.com.soswf.rota500.R.drawable.ic_launcher)
                .setContentTitle("Rota 500 em jornada")
                .setContentText(text)
                .setContentIntent(pending)
                .setOngoing(true)
                .build();
    }

    private void updateNotification(String text) {
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification(text));
    }
}
