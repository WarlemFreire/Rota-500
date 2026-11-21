package br.com.soswf.rota500.location;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

import br.com.soswf.rota500.data.AppDatabase;
import br.com.soswf.rota500.ui.MainActivity;

/**
 * Q32: depois de reiniciar o celular (ou de atualizar o app), a jornada continua aberta no
 * banco, mas o GPS e a notificação param. O Android não deixa religar a localização sem o
 * app aberto, então o aviso pede para continuar ou encerrar.
 */
public final class JourneyBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        AppDatabase database = AppDatabase.get(context);
        if (database.activeSessionId() <= 0) return;
        TrackingService.createChannels(context);
        String since = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.forLanguageTag("pt-BR"))
                .format(new Date(database.activeSessionStart()));
        Notification notification = new Notification.Builder(context, TrackingService.CHANNEL_QUESTIONS)
                .setSmallIcon(br.com.soswf.rota500.R.drawable.ic_launcher)
                .setContentTitle("Jornada aberta desde " + since)
                .setContentText("O GPS parou. Continue para religar ou encerre a jornada.")
                .setOngoing(true)
                .setContentIntent(TrackingService.openApp(context, MainActivity.OPEN_CONTINUE, 30))
                .addAction(new Notification.Action.Builder(null, "Continuar",
                        TrackingService.openApp(context, MainActivity.OPEN_CONTINUE, 31)).build())
                .addAction(new Notification.Action.Builder(null, "Encerrar",
                        TrackingService.openApp(context, MainActivity.OPEN_END, 32)).build())
                .build();
        TrackingService.notify(context, TrackingService.NOTIFICATION_ID, notification);
    }
}
