package br.com.soswf.rota500.capture;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

import br.com.soswf.rota500.R;
import br.com.soswf.rota500.ui.MainActivity;

final class DecisionAlert {
    private static final String CHANNEL = "rota500_offer_decisions_v2";
    private static final int NOTIFICATION_ID = 502;
    private final Context context;
    private final Handler handler;
    private final WindowManager windowManager;
    private TextView overlay;

    DecisionAlert(Context context, Handler handler) {
        this.context = context;
        this.handler = handler;
        this.windowManager = context.getSystemService(WindowManager.class);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL, "Decisões de corridas", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Mostra ACEITAR, RECUSAR ou CONFERIR quando uma oferta é lida.");
        channel.setSound(null, null);
        channel.enableVibration(false);
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    void show(String title, String details, String tone) {
        int color = colorFor(tone);
        showNotification(title, details, color);
        if (!Settings.canDrawOverlays(context) || windowManager == null) return;
        hide();
        overlay = new TextView(context);
        overlay.setText(title + "\n" + details);
        overlay.setTextColor(Color.WHITE);
        overlay.setTextSize(20);
        overlay.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        overlay.setGravity(Gravity.CENTER);
        overlay.setLineSpacing(0, 1.05f);
        int horizontal = dp(18), vertical = dp(13);
        overlay.setPadding(horizontal, vertical, horizontal, vertical);
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(16));
        overlay.setBackground(background);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_SECURE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = dp(72);
        params.horizontalMargin = 0.035f;
        try {
            windowManager.addView(overlay, params);
            handler.postDelayed(this::hide, 8_000L);
        } catch (RuntimeException ignored) {
            overlay = null;
        }
    }

    void hide() {
        handler.removeCallbacks(this::hide);
        if (overlay == null || windowManager == null) return;
        try { windowManager.removeView(overlay); }
        catch (RuntimeException ignored) { }
        overlay = null;
    }

    private void showNotification(String title, String details, int color) {
        Intent open = new Intent(context, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(context, 2, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(details)
                .setStyle(new Notification.BigTextStyle().bigText(details))
                .setColor(color)
                .setCategory(Notification.CATEGORY_STATUS)
                .setPriority(Notification.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build();
        context.getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification);
    }

    private int colorFor(String tone) {
        if ("ACCEPT".equals(tone)) return Color.rgb(8, 122, 82);
        if ("REJECT".equals(tone)) return Color.rgb(179, 38, 30);
        return Color.rgb(185, 106, 0);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
