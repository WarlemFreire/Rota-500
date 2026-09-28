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
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
import br.com.soswf.rota500.data.FinancialToday;
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
    private TextView blockValue;
    private TextView activeTimeValue;
    private TextView financialValue;
    private TextView sourceDetails;
    private ProgressBar goalProgress;
    private LinearLayout decisionCard;
    private TextView decisionTitle;
    private TextView decisionDetails;
    private TextView advice;
    private Button shiftButton;
    private Button captureButton;
    private Button overlayButton;
    private Button pauseButton;
    private Button undoButton;
    private LinearLayout activeActions;
    private long lastMoneyEventId;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideUndo = () -> { if (undoButton != null) undoButton.setVisibility(View.GONE); };
    private final Runnable refreshClock = new Runnable() {
        @Override public void run() {
            refreshMetricsOnly();
            uiHandler.postDelayed(this, 60_000L);
        }
    };
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
        uiHandler.removeCallbacks(refreshClock);
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
        uiHandler.removeCallbacks(refreshClock);
        uiHandler.postDelayed(refreshClock, 60_000L);
    }

    @Override protected void onDestroy() {
        uiHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout screen = Ui.column(this);
        LinearLayout header = new LinearLayout(this);
        header.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 12), 0);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView brand = Ui.text(this, "Rota 500", 22);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brand.setCompoundDrawables(Ui.icon(this, "route", Ui.GREEN), null, null, null);
        brand.setCompoundDrawablePadding(Ui.dp(this, 10));
        header.addView(brand, new LinearLayout.LayoutParams(0, Ui.dp(this, 64), 1));
        brand.setGravity(android.view.Gravity.CENTER_VERTICAL);
        statusPill = Ui.pill(this, "Parado", Ui.CARD_ALT);
        // The entire 64dp header target opens the journey controls.
        LinearLayout statusTarget = new LinearLayout(this);
        statusTarget.setGravity(android.view.Gravity.CENTER);
        statusTarget.setMinimumHeight(Ui.dp(this, 64));
        statusTarget.setBackground(Ui.ripple(this, Ui.SURFACE, 16));
        statusTarget.addView(statusPill);
        statusTarget.setOnClickListener(v -> showJourney());
        header.addView(statusTarget);
        screen.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = Ui.column(this);
        root.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), Ui.dp(this, 12));
        scroll.addView(root);

        TextView label = Ui.label(this, "GANHO BRUTO · HOJE");
        label.setGravity(android.view.Gravity.CENTER);
        root.addView(label);
        grossValue = Ui.number(this, "R$ 0", 60);
        grossValue.setGravity(android.view.Gravity.CENTER);
        grossValue.setPadding(0, 0, 0, Ui.dp(this, 4));
        root.addView(grossValue, new LinearLayout.LayoutParams(-1, Ui.dp(this, 82)));

        goalDetails = Ui.text(this, "", 14);
        goalDetails.setGravity(android.view.Gravity.CENTER);
        goalDetails.setTextColor(Ui.MUTED);
        root.addView(goalDetails);
        goalProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        goalProgress.setMax(1000);
        goalProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));
        goalProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.BORDER));
        LinearLayout.LayoutParams progress = new LinearLayout.LayoutParams(-1, Ui.dp(this, 6));
        progress.setMargins(0, Ui.dp(this, 8), 0, Ui.dp(this, 16));
        root.addView(goalProgress, progress);

        LinearLayout metrics = new LinearLayout(this);
        blockValue = addMetric(metrics, "R$/h · 2 horas", "—", Ui.AMBER);
        hourValue = addMetric(metrics, "R$/h · dia", "—", Ui.GREEN);
        activeTimeValue = addMetric(metrics, "Tempo ativo", "0:00", Ui.BLUE);
        root.addView(metrics);
        paceDetails = Ui.text(this, "", 13);
        paceDetails.setTextColor(Ui.MUTED);
        paceDetails.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 6));
        root.addView(paceDetails);

        decisionCard = Ui.card(this);
        decisionCard.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));
        decisionCard.addView(Ui.label(this, "ÚLTIMA OFERTA"));
        decisionTitle = Ui.text(this, "Aguardando oferta", 22);
        decisionTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        decisionCard.addView(decisionTitle);
        decisionDetails = Ui.text(this, "", 13);
        decisionDetails.setTextColor(Ui.MUTED);
        decisionCard.addView(decisionDetails);
        root.addView(decisionCard);

        LinearLayout financeCard = Ui.card(this);
        financeCard.setOrientation(LinearLayout.HORIZONTAL);
        financeCard.setGravity(android.view.Gravity.CENTER_VERTICAL);
        financeCard.setMinimumHeight(Ui.dp(this, 76));
        financeCard.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        financeCard.setBackground(Ui.ripple(this, Ui.CARD, 18));
        financeCard.setOnClickListener(v -> startActivity(new Intent(this, FinanceActivity.class)));
        LinearLayout financeLabel = Ui.column(this);
        financeLabel.addView(Ui.text(this, "Após gastos pagos", 14));
        sourceDetails = Ui.text(this, "", 12);
        sourceDetails.setTextColor(Ui.MUTED);
        financeLabel.addView(sourceDetails);
        financeCard.addView(financeLabel, new LinearLayout.LayoutParams(0, -2, 1));
        financialValue = Ui.number(this, "R$ 0", 24);
        financeCard.addView(financialValue, new LinearLayout.LayoutParams(Ui.dp(this, 134), Ui.dp(this, 48)));
        financialValue.setGravity(android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);
        root.addView(financeCard);

        captureStatus = Ui.text(this, "", 12);
        captureStatus.setGravity(android.view.Gravity.CENTER);
        root.addView(captureStatus);
        captureButton = Ui.secondaryButton(this, "Ligar leitura");
        Ui.buttonIcon(captureButton, "scan", Ui.GREEN);
        captureButton.setOnClickListener(v -> toggleCapture());
        root.addView(captureButton);
        overlayButton = Ui.secondaryButton(this, "Ativar aviso sobre a corrida");
        overlayButton.setOnClickListener(v -> requestOverlayPermission());
        root.addView(overlayButton);
        advice = Ui.text(this, "", 16);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout dock = Ui.column(this);
        dock.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), Ui.dp(this, 8));
        dock.setBackgroundColor(Ui.SURFACE);
        undoButton = Ui.secondaryButton(this, "Ganho salvo · Desfazer");
        Ui.buttonIcon(undoButton, "undo", Ui.GREEN);
        undoButton.setTextColor(Ui.GREEN);
        undoButton.setVisibility(View.GONE);
        undoButton.setOnClickListener(v -> {
            database.undoMoney(lastMoneyEventId);
            undoButton.setVisibility(View.GONE);
            refresh();
        });
        dock.addView(undoButton);
        activeActions = new LinearLayout(this);
        Button register = Ui.primaryButton(this, "Registrar ganho");
        Ui.buttonIcon(register, "plus", Ui.SURFACE);
        register.setMaxLines(1);
        register.setAutoSizeTextTypeUniformWithConfiguration(14, 19, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        register.setOnClickListener(v -> showSourceSheet());
        LinearLayout.LayoutParams gainParams = new LinearLayout.LayoutParams(0, Ui.dp(this, 64), 1);
        gainParams.setMargins(0, 0, Ui.dp(this, 10), 0);
        activeActions.addView(register, gainParams);
        pauseButton = Ui.secondaryButton(this, "Pausar");
        pauseButton.setTextSize(13);
        pauseButton.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        pauseButton.setCompoundDrawables(null, Ui.icon(this, "pause", Ui.NAVY), null, null);
        pauseButton.setOnClickListener(v -> togglePause());
        activeActions.addView(pauseButton, new LinearLayout.LayoutParams(Ui.dp(this, 90), Ui.dp(this, 64)));
        dock.addView(activeActions);
        shiftButton = Ui.primaryButton(this, "Iniciar jornada");
        Ui.buttonIcon(shiftButton, "play", Ui.SURFACE);
        shiftButton.setOnClickListener(v -> toggleShift());
        dock.addView(shiftButton);
        screen.addView(dock);
        screen.addView(Ui.divider(this));
        screen.addView(bottomNavigation(scroll));
        Ui.install(this, screen);
    }

    private TextView addMetric(LinearLayout row, String label, String initialValue, int accent) {
        LinearLayout card = Ui.column(this);
        card.setGravity(android.view.Gravity.CENTER);
        card.setPadding(Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10));
        card.setBackground(Ui.rounded(Ui.CARD, 18, Ui.BORDER, this));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMargins(row.getChildCount() == 0 ? 0 : Ui.dp(this, 8), 0, 0, 0);
        card.setMinimumHeight(Ui.dp(this, 94));
        TextView value = Ui.number(this, initialValue, 28);
        value.setTextColor(accent);
        value.setGravity(android.view.Gravity.CENTER);
        card.addView(value, new LinearLayout.LayoutParams(-1, Ui.dp(this, 42)));
        TextView name = Ui.text(this, label, 11);
        name.setTextColor(Ui.MUTED);
        name.setGravity(android.view.Gravity.CENTER);
        card.addView(name);
        row.addView(card, params);
        return value;
    }

    private LinearLayout bottomNavigation(ScrollView scroll) {
        LinearLayout nav = new LinearLayout(this);
        nav.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));
        nav.setBackgroundColor(Ui.CARD);
        nav.addView(navItem("Hoje", "home", true, v -> scroll.smoothScrollTo(0, 0)));
        nav.addView(navItem("Gastos", "wallet", false, v -> startActivity(new Intent(this, FinanceActivity.class))));
        nav.addView(navItem("Particular", "car", false, v -> showPrivateMenu()));
        nav.addView(navItem("Mais", "menu", false, v -> showMore()));
        return nav;
    }

    private TextView navItem(String label, String icon, boolean selected, View.OnClickListener listener) {
        TextView item = Ui.text(this, label, 12);
        item.setTextColor(selected ? Ui.GREEN : Ui.MUTED);
        item.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        item.setGravity(android.view.Gravity.CENTER);
        item.setCompoundDrawables(null, Ui.icon(this, icon, selected ? Ui.GREEN : Ui.MUTED), null, null);
        item.setCompoundDrawablePadding(Ui.dp(this, 5));
        item.setPadding(0, Ui.dp(this, 9), 0, Ui.dp(this, 5));
        item.setBackground(Ui.ripple(this, selected ? Ui.CARD_ALT : Ui.CARD, 16));
        item.setSelected(selected);
        item.setOnClickListener(listener);
        item.setLayoutParams(new LinearLayout.LayoutParams(0, Ui.dp(this, 64), 1));
        return item;
    }

    private void menuAction(LinearLayout body, android.app.Dialog dialog, String label, String icon, Runnable action) {
        Button button = Ui.secondaryButton(this, label);
        button.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
        Ui.buttonIcon(button, icon, Ui.GREEN);
        body.addView(button);
        button.setOnClickListener(v -> { dialog.dismiss(); action.run(); });
    }

    private void showPrivateMenu() {
        LinearLayout body = Ui.column(this);
        android.app.Dialog dialog = Ui.sheet(this, "Corridas particulares", body);
        menuAction(body, dialog, "Novo orçamento", "plus", () -> startActivity(new Intent(this, QuoteActivity.class)));
        menuAction(body, dialog, "Corridas cadastradas", "car", () -> startActivity(new Intent(this, PrivateRidesActivity.class)));
    }

    private void showMore() {
        LinearLayout body = Ui.column(this);
        android.app.Dialog dialog = Ui.sheet(this, "Mais opções", body);
        menuAction(body, dialog, "Jornada e leitura", "scan", this::showJourney);
        menuAction(body, dialog, "Estratégia do dia", "chart", this::showStrategy);
        menuAction(body, dialog, "Analisar mapa dinâmico", "route", this::requestDynamicMap);
        menuAction(body, dialog, "Ajustes", "settings", () -> startActivity(new Intent(this, SettingsActivity.class)));
        menuAction(body, dialog, "Salvar backup", "backup", this::backup);
        menuAction(body, dialog, "Restaurar backup", "undo", this::chooseRestoreFile);
    }

    private void showJourney() {
        LinearLayout body = Ui.column(this);
        android.app.Dialog dialog = Ui.sheet(this, "Sua jornada", body);
        if (database.activeSessionId() <= 0) {
            menuAction(body, dialog, "Iniciar jornada", "play", this::toggleShift);
            return;
        }
        menuAction(body, dialog, database.openPauseId() > 0 ? "Retomar jornada" : "Pausar jornada", "pause", this::togglePause);
        menuAction(body, dialog, preferences.captureActive() ? "Desligar leitura" : "Ligar leitura", "scan", this::toggleCapture);
        menuAction(body, dialog, "Permissão do aviso flutuante", "settings", this::requestOverlayPermission);
        Button end = Ui.secondaryButton(this, "Encerrar jornada");
        end.setTextColor(Ui.RED);
        body.addView(end);
        end.setOnClickListener(v -> { dialog.dismiss(); endShift(); });
    }

    private void showStrategy() {
        LinearLayout body = Ui.column(this);
        if (advice.getParent() instanceof ViewGroup) ((ViewGroup) advice.getParent()).removeView(advice);
        body.addView(advice);
        Button recalculate = Ui.primaryButton(this, "Atualizar estratégia");
        Ui.buttonIcon(recalculate, "chart", Ui.SURFACE);
        body.addView(recalculate);
        recalculate.setOnClickListener(v -> requestStrategy());
        Ui.sheet(this, "Estratégia do dia", body);
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
        if (!preferences.backupTreeUri().isBlank()) {
            new Thread(() -> {
                try {
                    new BackupManager(this).writeDataBackup(Uri.parse(preferences.backupTreeUri()));
                    preferences.setLastBackupAt(System.currentTimeMillis());
                    runOnUiThread(() -> toast("Jornada encerrada. Backup salvo."));
                } catch (Exception error) {
                    runOnUiThread(() -> toast("Jornada encerrada. Backup pendente: toque em Salvar backup."));
                }
            }, "rota500-shift-backup").start();
        }
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

    private void showSourceSheet() {
        if (database.activeSessionId() <= 0) {
            toast("Inicie a jornada primeiro.");
            return;
        }
        LinearLayout choices = Ui.column(this);
        FinancialToday day = database.financialToday(preferences.fuelCostPerKm(), preferences.wearCostPerKm());
        android.app.Dialog dialog = Ui.sheet(this, "Registrar ganho", choices);
        TextView hint = Ui.text(this, "Uber e 99: informe o total do dia. Particular: some uma corrida.", 14);
        hint.setTextColor(Ui.MUTED);
        choices.addView(hint);
        for (String source : new String[]{"UBER", "99", "PARTICULAR"}) {
            String text = "PARTICULAR".equals(source) ? "Particular · nova corrida"
                    : source + " · atual " + money(day.balance().platformCents(source) / 100.0);
            menuAction(choices, dialog, text, "PARTICULAR".equals(source) ? "plus" : "wallet", () -> enterMoney(source));
        }
    }

    private void enterMoney(String source) {
        if (database.activeSessionId() <= 0) { toast("Inicie a jornada primeiro."); return; }
        String prompt = "PARTICULAR".equals(source) ? "Valor desta corrida particular"
                : "Total acumulado da " + source + " hoje";
        NumberPad.show(this, prompt, 2, "", cents -> {
            if (cents <= 0 && "PARTICULAR".equals(source)) return;
            try {
                FinancialToday before = database.financialToday(preferences.fuelCostPerKm(), preferences.wearCostPerKm());
                lastMoneyEventId = "PARTICULAR".equals(source)
                        ? database.recordExtra(source, cents)
                        : database.recordPlatformTotal(source, cents);
                undoButton.setVisibility(View.VISIBLE);
                uiHandler.removeCallbacks(hideUndo);
                uiHandler.postDelayed(hideUndo, 8_000L);
                refresh();
                if (!"PARTICULAR".equals(source) && before.balance().legacyCents() > 0
                        && before.balance().platformCents(source) == 0
                        && database.financialToday(preferences.fuelCostPerKm(), preferences.wearCostPerKm())
                                .balance().totalCents() == before.balance().totalCents()) {
                    toast("Primeiro total de " + source + " usado como base; saldo anterior preservado.");
                }
            } catch (RuntimeException error) { toast("Não foi possível registrar o valor."); }
        });
    }

    private void togglePause() {
        if (database.openPauseId() > 0) {
            database.closePause();
            refresh();
            return;
        }
        if (database.activeSessionId() <= 0) return;
        String[] reasons = {"Almoço", "Abastecer", "Descanso", "Cochilo",
                "Banheiro", "Pessoal", "Espera estratégica", "Outro"};
        LinearLayout content = Ui.column(this);
        android.app.Dialog dialog = Ui.sheet(this, "Pausar para…", content);
        for (int i = 0; i < reasons.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            for (int j = i; j < i + 2; j++) {
                String reason = reasons[j];
                Button button = Ui.secondaryButton(this, reason);
                button.setTextSize(15);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
                params.setMargins(j == i ? 0 : Ui.dp(this, 8), Ui.dp(this, 4), 0, Ui.dp(this, 4));
                row.addView(button, params);
                button.setOnClickListener(v -> { database.openPause(reason); dialog.dismiss(); refresh(); });
            }
            content.addView(row);
        }
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
        boolean paused = active && database.openPauseId() > 0;
        statusPill.setText(paused ? "Pausado" : active ? "Em jornada" : "Parado");
        statusPill.setBackground(Ui.rounded(Ui.CARD_ALT, 100, 0, this));
        statusPill.setTextColor(paused ? Ui.AMBER : active ? Ui.GREEN : Ui.MUTED);
        captureStatus.setText(reading ? "Leitura ligada · GPS ativo"
                : active ? "Leitura desligada · GPS ativo" : "Pronto para começar");
        captureStatus.setTextColor(reading ? Ui.GREEN : active ? Ui.AMBER : Ui.MUTED);
        shiftButton.setVisibility(active ? View.GONE : View.VISIBLE);
        activeActions.setVisibility(active ? View.VISIBLE : View.GONE);
        captureButton.setVisibility(active && !reading ? View.VISIBLE : View.GONE);
        pauseButton.setText(paused ? "Retomar" : "Pausar");
        pauseButton.setTextColor(paused ? Ui.AMBER : Ui.NAVY);
        pauseButton.setCompoundDrawables(null, Ui.icon(this, paused ? "play" : "pause", paused ? Ui.AMBER : Ui.NAVY), null, null);
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
        FinancialToday day = database.financialToday(preferences.fuelCostPerKm(), preferences.wearCostPerKm());
        long activeMinutes = day.activeMinutes();
        double gross = day.balance().totalCents() / 100.0;
        double goal = preferences.dailyGoal();
        double remaining = Math.max(0, goal - gross);
        grossValue.setText(money(gross));
        goalDetails.setText(remaining > 0
                ? "Faltam " + money(remaining) + " · meta " + money(goal)
                : "Meta de " + money(goal) + " alcançada");
        goalProgress.setProgress(goal <= 0 ? 0 : (int) Math.min(1000, Math.round(gross / goal * 1000)));
        blockValue.setText(day.blockGrossPerHourCents() == null ? "—" : moneyRounded(day.blockGrossPerHourCents()));
        hourValue.setText(day.dayGrossPerHourCents() == null ? "—" : moneyRounded(day.dayGrossPerHourCents()));
        activeTimeValue.setText(String.format(Locale.forLanguageTag("pt-BR"), "%d:%02d", activeMinutes / 60, activeMinutes % 60));
        financialValue.setText(money(day.cashRemainderCents() / 100.0));
        financialValue.setTextColor(day.cashRemainderCents() < 0 ? Ui.RED : Ui.NAVY);
        sourceDetails.setText(money(day.cashSpentCents() / 100.0) + " em gastos hoje");
        if (stats.hasActiveSession()) {
            if (database.openPauseId() > 0) paceDetails.setText("Pausa em andamento · tempo ativo parado");
            else if (day.blockGrossPerHourCents() != null && day.dayGrossPerHourCents() != null
                    && day.blockGrossPerHourCents() < day.dayGrossPerHourCents() * 0.8) {
                paceDetails.setText("Ritmo caiu nas últimas 2 h · reavalie a região");
            } else paceDetails.setText("Ritmo do dia calculado sem as pausas");
        } else {
            paceDetails.setText("Comece uma jornada para acompanhar seu ritmo");
        }
    }

    private String moneyRounded(long cents) {
        return String.format(Locale.forLanguageTag("pt-BR"), "%.0f", cents / 100.0);
    }

    private void refreshDecision() {
        String title = preferences.lastDecisionTitle();
        String details = preferences.lastDecisionDetails();
        String tone = preferences.lastDecisionTone();
        decisionTitle.setText(preferences.lastDecisionAt() > 0 ? title : "Aguardando oferta");
        decisionDetails.setText(preferences.lastDecisionAt() > 0 ? details : "O conselho aparece aqui ao ler uma corrida.");
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
                preferences.setLastBackupAt(System.currentTimeMillis());
                runOnUiThread(() -> toast("Backup concluído."));
            } catch (Exception error) {
                runOnUiThread(() -> toast("Não foi possível salvar nessa pasta."));
            }
        }, "rota500-backup").start();
    }

    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
}
