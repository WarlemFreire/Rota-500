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
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import br.com.soswf.rota500.R;
import br.com.soswf.rota500.core.DecisionPresentation;
import br.com.soswf.rota500.ui.MainActivity;

final class DecisionAlert {
    private static final String CHANNEL = "rota500_offer_decisions_v3";
    private static final int NOTIFICATION_ID = 502;
    private final Context context;
    private final Handler handler;
    private final WindowManager windowManager;
    private final Runnable hideTask = this::hideNow;
    private View overlay;

    DecisionAlert(Context context, Handler handler) {
        this.context = context;
        this.handler = handler;
        this.windowManager = context.getSystemService(WindowManager.class);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL, "Decisões de corridas", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Mostra ACEITAR ou RECUSAR quando uma oferta é lida.");
        channel.setSound(null, null);
        channel.enableVibration(false);
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    void show(DecisionPresentation decision) {
        int color = colorFor(decision.tone);
        showNotification(decision.finalAdvice, decision.compactDetails(), color);
        if (!Settings.canDrawOverlays(context) || windowManager == null) return;
        hide();

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(14), dp(10), dp(14), dp(9));
        panel.setAlpha(1f);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(17, 24, 39));
        background.setCornerRadius(dp(14));
        background.setStroke(dp(2), color);
        panel.setBackground(background);
        panel.setElevation(dp(12));

        LinearLayout metrics = new LinearLayout(context);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.setGravity(Gravity.CENTER);
        metrics.addView(metric("KM", decision.kmValue, decision.kmLight));
        metrics.addView(metric("HORA", decision.hourValue, decision.hourLight));
        metrics.addView(metric("NOTA", decision.ratingValue, decision.ratingLight));
        panel.addView(metrics);

        TextView finalAdvice = new TextView(context);
        finalAdvice.setText(decision.finalAdvice);
        finalAdvice.setTextColor(color);
        finalAdvice.setTextSize(23);
        finalAdvice.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        finalAdvice.setGravity(Gravity.CENTER);
        finalAdvice.setPadding(0, dp(5), 0, 0);
        panel.addView(finalAdvice, new LinearLayout.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT));
        overlay = panel;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = dp(60);
        try {
            windowManager.addView(overlay, params);
            handler.postDelayed(hideTask, 6_000L);
        } catch (RuntimeException ignored) {
            overlay = null;
        }
    }

    void hide() {
        handler.removeCallbacks(hideTask);
        hideNow();
    }

    private void hideNow() {
        if (overlay == null || windowManager == null) return;
        try { windowManager.removeView(overlay); }
        catch (RuntimeException ignored) { }
        overlay = null;
    }

    private LinearLayout metric(String label, String value, DecisionPresentation.Light light) {
        LinearLayout cell = new LinearLayout(context);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        int width = dp(78);
        cell.setPadding(dp(4), 0, dp(4), 0);
        cell.setLayoutParams(new LinearLayout.LayoutParams(width, WindowManager.LayoutParams.WRAP_CONTENT));

        View dot = new View(context);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(colorFor(light));
        dot.setBackground(circle);
        cell.addView(dot, new LinearLayout.LayoutParams(dp(14), dp(14)));

        TextView name = new TextView(context);
        name.setText(label);
        name.setTextColor(Color.rgb(181, 190, 204));
        name.setTextSize(10);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setGravity(Gravity.CENTER);
        cell.addView(name);

        TextView number = new TextView(context);
        number.setText(value);
        number.setTextColor(Color.WHITE);
        number.setTextSize(15);
        number.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        number.setGravity(Gravity.CENTER);
        cell.addView(number);
        return cell;
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

    private int colorFor(DecisionPresentation.Light light) {
        if (light == DecisionPresentation.Light.GREEN) return Color.rgb(34, 197, 94);
        if (light == DecisionPresentation.Light.RED) return Color.rgb(239, 68, 68);
        return Color.rgb(250, 184, 35);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
