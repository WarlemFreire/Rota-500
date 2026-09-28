package br.com.soswf.rota500.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

import br.com.soswf.rota500.ai.StrategyCoordinator;
import br.com.soswf.rota500.backup.BackupManager;
import br.com.soswf.rota500.capture.ScreenCaptureService;
import br.com.soswf.rota500.core.StrategyAdvice;
import br.com.soswf.rota500.data.AppDatabase;
import br.com.soswf.rota500.data.AppPreferences;
import br.com.soswf.rota500.data.DashboardStats;
import br.com.soswf.rota500.location.TrackingService;

public final class MainActivity extends Activity {
    private static final int LOCATION_REQUEST = 20;
    private static final int NOTIFICATION_REQUEST = 21;
    private static final int BACKUP_FOLDER_REQUEST = 30;
    private static final int RESTORE_FILE_REQUEST = 31;
    private static final int SCREEN_CAPTURE_REQUEST = 50;
    private AppDatabase database;
    private AppPreferences preferences;
    private TextView statusPill;
    private TextView captureStatus;
    private TextView grossValue;
    private TextView goalDetails;
    private TextView paceDetails;
    private TextView hourValue;
    private TextView activeTimeValue;
    private TextView offersValue;
    private ProgressBar goalProgress;
    private LinearLayout decisionCard;
    private TextView decisionTitle;
    private TextView decisionDetails;
    private TextView advice;
    private Button shiftButton;
    private Button captureButton;
    private Button overlayButton;
    private boolean pendingStart;
    private boolean pendingCaptureAfterNotification;
    private boolean receiverRegistered;

    private final BroadcastReceiver updates = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { refresh(); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        database = AppDatabase.get(this);
        preferences = new AppPreferences(this);
        buildUi();
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ScreenCaptureService.ACTION_STATE);
        filter.addAction(ScreenCaptureService.ACTION_DECISION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(updates, filter, RECEIVER_NOT_EXPORTED);
        else registerReceiver(updates, filter);
        receiverRegistered = true;
    }

    @Override protected void onStop() {
        if (receiverRegistered) {
            unregisterReceiver(updates);
            receiverRegistered = false;
        }
        super.onStop();
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
        maybeAskForContext();
    }

    private void buildUi() {
        getWindow().setStatusBarColor(Ui.SURFACE);
        getWindow().setNavigationBarColor(Ui.SURFACE);

        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(Ui.SURFACE);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.SURFACE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int horizontal = Ui.dp(this, 18);
        root.setPadding(horizontal, Ui.dp(this, 14), horizontal, Ui.dp(this, 24));
        scroll.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView appName = Ui.title(this, "ROTA 500");
        appName.setTextSize(24);
        header.addView(appName, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        statusPill = Ui.pill(this, "PARADO", Ui.CARD_ALT);
        header.addView(statusPill);
        root.addView(header);

        captureStatus = Ui.text(this, "GPS e leitura desligados", 13);
        captureStatus.setTextColor(Ui.MUTED);
        captureStatus.setPadding(0, 0, 0, Ui.dp(this, 14));
        root.addView(captureStatus);

        TextView goalLabel = Ui.text(this, "GANHO BRUTO DE HOJE", 13);
        goalLabel.setTextColor(Ui.MUTED);
        goalLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        goalLabel.setGravity(android.view.Gravity.CENTER);
        root.addView(goalLabel);
        grossValue = Ui.text(this, "R$ 0", 58);
        grossValue.setTextColor(Ui.NAVY);
        grossValue.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        grossValue.setGravity(android.view.Gravity.CENTER);
        grossValue.setPadding(0, 0, 0, Ui.dp(this, 12));
        grossValue.setOnClickListener(v -> showGrossDialog());
        root.addView(grossValue);

        LinearLayout metrics = new LinearLayout(this);
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        hourValue = addMetric(metrics, "R$/HORA", "R$ 0", Ui.AMBER);
        activeTimeValue = addMetric(metrics, "JORNADA", "0:00", Ui.BLUE);
        offersValue = addMetric(metrics, "BOAS/OFERTAS", "0/0", Ui.GREEN);
        root.addView(metrics);

        LinearLayout goalCard = Ui.card(this);
        TextView progressLabel = Ui.text(this, "META DO DIA", 12);
        progressLabel.setTextColor(Ui.MUTED);
        progressLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        goalCard.addView(progressLabel);
        goalDetails = Ui.text(this, "Meta: R$ 500", 17);
        goalDetails.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        goalCard.addView(goalDetails);
        goalProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        goalProgress.setMax(1000);
        goalProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));
        goalProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.BORDER));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 12));
        progressParams.setMargins(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        goalCard.addView(goalProgress, progressParams);
        paceDetails = Ui.text(this, "Toque no valor acima para atualizar o ganho", 13);
        paceDetails.setTextColor(Ui.MUTED);
        goalCard.addView(paceDetails);
        root.addView(goalCard);

        decisionCard = Ui.card(this);
        TextView decisionLabel = Ui.text(this, "ÚLTIMA OFERTA", 12);
        decisionLabel.setTextColor(Ui.MUTED);
        decisionLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        decisionCard.addView(decisionLabel);
        decisionTitle = Ui.text(this, "AGUARDANDO OFERTA", 30);
        decisionTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        decisionCard.addView(decisionTitle);
        decisionDetails = Ui.text(this, "Abra a Uber ou a 99 com a leitura ligada.", 15);
        decisionDetails.setTextColor(Ui.MUTED);
        decisionCard.addView(decisionDetails);
        root.addView(decisionCard);

        shiftButton = Ui.primaryButton(this, "INICIAR JORNADA");
        shiftButton.setOnClickListener(v -> toggleShift());
        captureButton = Ui.secondaryButton(this, "Ligar leitura da tela");
        captureButton.setOnClickListener(v -> toggleCapture());
        root.addView(actionRow(shiftButton, captureButton));

        overlayButton = Ui.secondaryButton(this, "Mostrar decisão sobre Uber e 99");
        overlayButton.setOnClickListener(v -> requestOverlayPermission());
        root.addView(overlayButton);

        root.addView(Ui.sectionTitle(this, "ESTRATÉGIA"));
        LinearLayout strategyCard = Ui.card(this);
        advice = Ui.text(this, "", 16);
        strategyCard.addView(advice);
        Button strategy = Ui.secondaryButton(this, "Recalcular agora");
        strategy.setOnClickListener(v -> requestStrategy());
        strategyCard.addView(strategy);
        root.addView(strategyCard);

        root.addView(Ui.sectionTitle(this, "CONTROLES RÁPIDOS"));
        Button gross = Ui.secondaryButton(this, "Atualizar ganho");
        gross.setOnClickListener(v -> showGrossDialog());
        Button dynamic = Ui.secondaryButton(this, "Analisar o mapa dinâmico");
        dynamic.setOnClickListener(v -> requestDynamicMap());
        root.addView(actionRow(gross, dynamic));
        Button backup = Ui.secondaryButton(this, "Salvar backup");
        backup.setOnClickListener(v -> backup());
        Button restore = Ui.secondaryButton(this, "Restaurar backup");
        restore.setOnClickListener(v -> chooseRestoreFile());
        root.addView(actionRow(backup, restore));

        TextView note = Ui.text(this,
                "Leitura local e inteligente: o OCR acelera quando a tela muda e descansa quando nada acontece.", 12);
        note.setTextColor(Ui.MUTED);
        note.setPadding(0, Ui.dp(this, 16), 0, 0);
        root.addView(note);

        screen.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        screen.addView(bottomNavigation(scroll));
        setContentView(screen);
    }

    private TextView addMetric(LinearLayout row, String label, String initialValue, int accent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(android.view.Gravity.CENTER);
        card.setPadding(Ui.dp(this, 7), Ui.dp(this, 12), Ui.dp(this, 7), Ui.dp(this, 12));
        card.setBackground(Ui.rounded(Ui.CARD, 18, Ui.BORDER, this));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, Ui.dp(this, 112), 1);
        params.setMargins(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 10));
        card.setLayoutParams(params);

        View stripe = new View(this);
        stripe.setBackgroundColor(accent);
        LinearLayout.LayoutParams stripeParams = new LinearLayout.LayoutParams(Ui.dp(this, 34), Ui.dp(this, 4));
        stripeParams.setMargins(0, 0, 0, Ui.dp(this, 7));
        card.addView(stripe, stripeParams);

        TextView name = Ui.text(this, label, 10);
        name.setTextColor(Ui.MUTED);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setGravity(android.view.Gravity.CENTER);
        card.addView(name);

        TextView value = Ui.text(this, initialValue, 23);
        value.setTextColor(Ui.NAVY);
        value.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        value.setGravity(android.view.Gravity.CENTER);
        card.addView(value);
        row.addView(card);
        return value;
    }

    private LinearLayout actionRow(Button left, Button right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        leftParams.setMargins(0, 0, Ui.dp(this, 5), 0);
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        rightParams.setMargins(Ui.dp(this, 5), 0, 0, 0);
        row.addView(left, leftParams);
        row.addView(right, rightParams);
        return row;
    }

    private LinearLayout bottomNavigation(ScrollView scroll) {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(android.view.Gravity.CENTER);
        nav.setPadding(Ui.dp(this, 8), Ui.dp(this, 7), Ui.dp(this, 8), Ui.dp(this, 8));
        nav.setBackground(Ui.rounded(Ui.CARD, 0, Ui.BORDER, this));
        nav.addView(navItem("●\nAGORA", Ui.GREEN, v -> scroll.smoothScrollTo(0, 0)));
        nav.addView(navItem("◇\nPARTICULAR", Ui.MUTED,
                v -> startActivity(new Intent(this, QuoteActivity.class))));
        nav.addView(navItem("≡\nCORRIDAS", Ui.MUTED,
                v -> startActivity(new Intent(this, PrivateRidesActivity.class))));
        nav.addView(navItem("⚙\nAJUSTES", Ui.MUTED,
                v -> startActivity(new Intent(this, SettingsActivity.class))));
        return nav;
    }

    private TextView navItem(String label, int color, View.OnClickListener listener) {
        TextView item = Ui.text(this, label, 10);
        item.setTextColor(color);
        item.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        item.setGravity(android.view.Gravity.CENTER);
        item.setOnClickListener(listener);
        item.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        item.setLayoutParams(new LinearLayout.LayoutParams(0, Ui.dp(this, 58), 1));
        return item;
    }

    private void toggleShift() {
        if (database.activeSessionId() > 0) {
            endShift();
            return;
        }
        pendingStart = true;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_REQUEST);
            return;
        }
        startShift();
    }

    private void startShift() {
        pendingStart = false;
        database.beginSession(database.dashboardStats().currentZone);
        Intent tracking = new Intent(this, TrackingService.class).setAction(TrackingService.ACTION_START);
        startForegroundService(tracking);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingCaptureAfterNotification = true;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_REQUEST);
        } else requestScreenCapture();
        refresh();
    }

    private void endShift() {
        DashboardStats stats = database.dashboardStats();
        database.endActiveSession(stats.currentZone);
        startService(new Intent(this, TrackingService.class).setAction(TrackingService.ACTION_STOP));
        startService(new Intent(this, ScreenCaptureService.class).setAction(ScreenCaptureService.ACTION_STOP));
        preferences.setCaptureActive(false);
        refresh();
    }

    private void toggleCapture() {
        if (database.activeSessionId() <= 0) {
            toast("Inicie a jornada primeiro.");
            return;
        }
        if (preferences.captureActive()) {
            startService(new Intent(this, ScreenCaptureService.class).setAction(ScreenCaptureService.ACTION_STOP));
            preferences.setCaptureActive(false);
            refresh();
        } else requestScreenCapture();
    }

    private void requestScreenCapture() {
        if (database.activeSessionId() <= 0) return;
        MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
        Intent request;
        if (Build.VERSION.SDK_INT >= 34) {
            request = manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay());
        } else request = manager.createScreenCaptureIntent();
        startActivityForResult(request, SCREEN_CAPTURE_REQUEST);
    }

    private void requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            toast("O aviso sobre a Uber e a 99 já está ativado.");
            return;
        }
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == LOCATION_REQUEST && pendingStart) {
            boolean granted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            if (granted) startShift();
            else {
                pendingStart = false;
                toast("A localização é necessária durante a jornada.");
            }
        }
        if (requestCode == NOTIFICATION_REQUEST && pendingCaptureAfterNotification) {
            pendingCaptureAfterNotification = false;
            requestScreenCapture();
        }
    }

    private void showGrossDialog() {
        if (database.activeSessionId() <= 0) {
            toast("Inicie a jornada primeiro.");
            return;
        }
        EditText input = new EditText(this);
        input.setTextSize(22);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format(Locale.forLanguageTag("pt-BR"), "%.2f",
                database.dashboardStats().activeGrossCents / 100.0));
        int padding = Ui.dp(this, 20);
        input.setPadding(padding, padding, padding, padding);
        new AlertDialog.Builder(this)
                .setTitle("Quanto já entrou hoje?")
                .setMessage("Some Uber, 99 e corridas particulares desta jornada.")
                .setView(input)
                .setPositiveButton("Salvar", (dialog, which) -> {
                    double value = Ui.number(input, -1);
                    if (value >= 0) {
                        database.setActiveGrossCents(Math.round(value * 100));
                        refresh();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void requestStrategy() {
        advice.setText("Calculando a estratégia…");
        new StrategyCoordinator(this).requestBestAdvice((result, source, warning) -> {
            advice.setText(formatAdvice(result, source, warning));
            refreshMetricsOnly();
        });
    }

    private void requestDynamicMap() {
        if (database.activeSessionId() <= 0) {
            toast("Inicie a jornada primeiro.");
            return;
        }
        if (!preferences.captureActive()) {
            toast("Ligue a leitura da tela antes de abrir o mapa.");
            return;
        }
        preferences.requestDynamicCapture();
        toast("Abra agora o mapa dinâmico da Uber ou da 99.");
    }

    private void maybeAskForContext() {
        if (database.activeSessionId() <= 0 || !database.isStationaryNow() || !preferences.canAskForContext()) return;
        if ("Não informado".equals(preferences.weather())) {
            preferences.markContextPrompt();
            new AlertDialog.Builder(this).setTitle("Como está o tempo?")
                    .setItems(new String[]{"Chovendo", "Sem chuva", "Não sei"}, (dialog, which) ->
                            preferences.setWeather(new String[]{"Chovendo", "Sem chuva", "Não informado"}[which]))
                    .show();
        } else if (database.dashboardStats().idleMinutes >= 20
                && "Não lido".equals(database.latestDynamicLevel())) {
            preferences.markContextPrompt();
            new AlertDialog.Builder(this).setTitle("Conferir o movimento da região?")
                    .setMessage("Com o carro parado, abra o mapa dinâmico da Uber ou da 99.")
                    .setPositiveButton("Analisar mapa", (dialog, which) -> requestDynamicMap())
                    .setNegativeButton("Depois", null)
                    .show();
        }
    }

    private String formatAdvice(StrategyAdvice result, String source, String warning) {
        return result.title + "\n" + result.explanation + String.format(Locale.forLanguageTag("pt-BR"),
                "\nProjeção: R$ %.2f  •  confiança %.0f%%%s", result.projectedFinalGross,
                result.confidence * 100, warning.isEmpty() ? "" : "\n" + warning);
    }

    private void refresh() {
        DashboardStats stats = database.dashboardStats();
        boolean active = stats.hasActiveSession();
        boolean reading = active && preferences.captureActive();
        statusPill.setText(active ? "EM JORNADA" : "PARADO");
        statusPill.setBackground(Ui.rounded(active ? Ui.GREEN : Ui.CARD_ALT, 100,
                active ? 0 : Ui.BORDER, this));
        statusPill.setTextColor(active ? Ui.SURFACE : Ui.MUTED);
        captureStatus.setText(reading ? "● GPS ativo  •  leitura inteligente ligada"
                : active ? "● GPS ativo  •  leitura desligada" : "GPS e leitura desligados");
        captureStatus.setTextColor(reading ? Ui.GREEN : Ui.MUTED);
        shiftButton.setText(active ? "ENCERRAR JORNADA" : "INICIAR JORNADA");
        shiftButton.setBackground(Ui.rounded(active ? Ui.RED : Ui.GREEN, 14, 0, this));
        shiftButton.setTextColor(Ui.SURFACE);
        captureButton.setVisibility(active ? View.VISIBLE : View.GONE);
        captureButton.setText(reading ? "Parar leitura" : "Ligar leitura");
        captureButton.setTextColor(reading ? Ui.GREEN : Ui.NAVY);
        overlayButton.setVisibility(active && !Settings.canDrawOverlays(this) ? View.VISIBLE : View.GONE);
        refreshMetricsOnly();
        refreshDecision();
        if (active && advice.getText().toString().isBlank()) {
            StrategyAdvice local = new StrategyCoordinator(this).localAdvice();
            advice.setText(formatAdvice(local, "motor local", ""));
        }
        if (!active) advice.setText("Inicie uma jornada para receber uma estratégia baseada no ritmo do dia.");
    }

    private void refreshMetricsOnly() {
        DashboardStats stats = database.dashboardStats();
        int worked = stats.workedMinutes(System.currentTimeMillis());
        double gross = stats.grossCents / 100.0;
        double goal = preferences.dailyGoal();
        double remaining = Math.max(0, goal - gross);
        grossValue.setText(money(gross));
        goalDetails.setText(remaining > 0
                ? "Faltam " + money(remaining) + " para a meta de " + money(goal)
                : "Meta de " + money(goal) + " alcançada");
        goalProgress.setProgress(goal <= 0 ? 0 : (int) Math.min(1000, Math.round(gross / goal * 1000)));
        hourValue.setText(stats.robustGrossPerHour > 0
                ? String.format(Locale.forLanguageTag("pt-BR"), "R$ %.0f", stats.robustGrossPerHour)
                : "—");
        activeTimeValue.setText(String.format(Locale.forLanguageTag("pt-BR"), "%d:%02d", worked / 60, worked % 60));
        offersValue.setText(String.format(Locale.forLanguageTag("pt-BR"), "%d/%d",
                stats.viableOffersLastHour, stats.offersLastHour));
        if (stats.hasActiveSession()) {
            paceDetails.setText(String.format(Locale.forLanguageTag("pt-BR"),
                    "%.0f%% da meta  •  %d ofertas dentro da régua na última hora",
                    goal <= 0 ? 0 : Math.min(100, gross / goal * 100), stats.viableOffersLastHour));
        } else {
            activeTimeValue.setText("0:00");
            paceDetails.setText("Toque no valor acima para atualizar o ganho");
        }
    }

    private void refreshDecision() {
        String title = preferences.lastDecisionTitle();
        String details = preferences.lastDecisionDetails();
        String tone = preferences.lastDecisionTone();
        decisionTitle.setText(title.toUpperCase(Locale.forLanguageTag("pt-BR")));
        decisionDetails.setText(details);
        int color = "ACCEPT".equals(tone) ? Ui.GREEN : "REJECT".equals(tone) ? Ui.RED :
                "REVIEW".equals(tone) ? Ui.AMBER : Ui.NAVY;
        decisionTitle.setTextColor(color);
        decisionCard.setBackground(Ui.rounded(Ui.CARD, 18,
                "NEUTRAL".equals(tone) ? Ui.BORDER : color, this));
        if (preferences.lastDecisionAt() > 0) {
            String time = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.forLanguageTag("pt-BR"))
                    .format(new Date(preferences.lastDecisionAt()));
            decisionDetails.setText(details + "\nLida às " + time);
        }
    }

    private String money(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.005) {
            return String.format(Locale.forLanguageTag("pt-BR"), "R$ %.0f", value);
        }
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", value);
    }

    private void backup() {
        String stored = preferences.backupTreeUri();
        if (stored.isBlank()) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, BACKUP_FOLDER_REQUEST);
        } else writeBackup(Uri.parse(stored));
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SCREEN_CAPTURE_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                Intent service = new Intent(this, ScreenCaptureService.class)
                        .setAction(ScreenCaptureService.ACTION_START)
                        .putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                        .putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data);
                startForegroundService(service);
                toast("Leitura ligada. Agora abra a Uber ou a 99.");
            } else toast("A jornada continua, mas a leitura da tela ficou desligada.");
            refresh();
            return;
        }
        if (requestCode == BACKUP_FOLDER_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri, flags);
            preferences.setBackupTreeUri(uri.toString());
            writeBackup(uri);
        }
        if (requestCode == RESTORE_FILE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri file = data.getData();
            new AlertDialog.Builder(this).setTitle("Substituir os dados locais?")
                    .setMessage("O arquivo substituirá jornadas, ofertas e corridas particulares deste celular.")
                    .setPositiveButton("Restaurar", (dialog, which) -> restoreBackup(file))
                    .setNegativeButton("Cancelar", null).show();
        }
    }

    private void chooseRestoreFile() {
        if (database.activeSessionId() > 0) {
            toast("Encerre a jornada antes de restaurar um backup.");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json")
                .addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, RESTORE_FILE_REQUEST);
    }

    private void restoreBackup(Uri uri) {
        new Thread(() -> {
            try {
                new BackupManager(this).restoreDataBackup(uri);
                runOnUiThread(() -> {
                    refresh();
                    toast("Dados restaurados.");
                });
            } catch (Exception error) {
                runOnUiThread(() -> toast("Não foi possível restaurar esse arquivo."));
            }
        }, "rota500-restore").start();
    }

    private void writeBackup(Uri tree) {
        toast("Criando backup…");
        new Thread(() -> {
            try {
                new BackupManager(this).writeDataBackup(tree);
                runOnUiThread(() -> toast("Backup concluído."));
            } catch (Exception error) {
                runOnUiThread(() -> toast("Não foi possível salvar nessa pasta."));
            }
        }, "rota500-backup").start();
    }

    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
}
