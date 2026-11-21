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
import br.com.soswf.rota500.core.CopilotoImport;
import br.com.soswf.rota500.core.JourneyWatch;
import br.com.soswf.rota500.core.StrategyAdvice;
import br.com.soswf.rota500.data.AppDatabase;
import br.com.soswf.rota500.data.AppPreferences;
import br.com.soswf.rota500.data.DashboardStats;
import br.com.soswf.rota500.data.FinancialToday;
import br.com.soswf.rota500.location.TrackingService;
import br.com.soswf.rota500.core.UpdatePicker;
import br.com.soswf.rota500.update.UpdateManager;

public final class MainActivity extends Activity {
    /** Ações abertas a partir das notificações da jornada. */
    public static final String EXTRA_OPEN = "br.com.soswf.rota500.OPEN";
    public static final String OPEN_END = "END";
    public static final String OPEN_REGISTER = "REGISTER";
    public static final String OPEN_CONTINUE = "CONTINUE";
    public static final String OPEN_REOPEN = "REOPEN";
    private static final int LOCATION_REQUEST = 20;
    private static final int NOTIFICATION_REQUEST = 21;
    private static final int BACKUP_FOLDER_REQUEST = 30;
    private static final int RESTORE_FILE_REQUEST = 31;
    private static final int COPILOTO_FILE_REQUEST = 32;
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
    private TextView netValue;
    private TextView cashValue;
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
        handleOpenAction(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleOpenAction(intent);
    }

    private void handleOpenAction(Intent intent) {
        if (intent == null) return;
        if (UpdateManager.ACTION_INSTALL_STATUS.equals(intent.getAction())) {
            handleInstallStatus(intent);
            intent.setAction(null);
            return;
        }
        String action = intent.getStringExtra(EXTRA_OPEN);
        if (action == null) return;
        intent.removeExtra(EXTRA_OPEN);
        if (OPEN_REOPEN.equals(action)) {
            long ended = preferences.autoEndedSession();
            if (ended > 0 && database.reopenSession(ended)) {
                preferences.setAutoEndedSession(0);
                getSystemService(android.app.NotificationManager.class).cancel(504);
                ensureTracking();
                toast("Jornada reaberta.");
                refresh();
            } else toast("Não foi possível reabrir: já existe outra jornada ou ela não foi encontrada.");
            return;
        }
        if (database.activeSessionId() <= 0) {
            if (!OPEN_CONTINUE.equals(action)) toast("Nenhuma jornada aberta.");
            return;
        }
        switch (action) {
            case OPEN_END -> endShift();
            case OPEN_REGISTER -> showSourceSheet();
            case OPEN_CONTINUE -> {
                ensureTracking();
                toast("Jornada retomada. Ligue a leitura se precisar.");
            }
            default -> { }
        }
    }

    /** Religa o GPS e a notificação da jornada (ex.: depois de reiniciar o celular, Q32). */
    private void ensureTracking() {
        if (database.activeSessionId() <= 0) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        try {
            startForegroundService(new Intent(this, TrackingService.class).setAction(TrackingService.ACTION_START));
        } catch (RuntimeException ignored) { }
    }

    /** Atualiza a notificação da jornada depois de uma mudança feita no app. */
    private void notifyJourneyChanged() {
        if (database.activeSessionId() <= 0) return;
        try { startService(new Intent(this, TrackingService.class).setAction(TrackingService.ACTION_REFRESH)); }
        catch (RuntimeException ignored) { }
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
        ensureTracking();
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

        // Q14, Q18: líquido estimado e dinheiro no bolso lado a lado, mesmo tamanho.
        LinearLayout financeCard = Ui.card(this);
        financeCard.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        financeCard.setBackground(Ui.ripple(this, Ui.CARD, 18));
        financeCard.setOnClickListener(v -> startActivity(new Intent(this, FinanceActivity.class)));
        LinearLayout financeRow = new LinearLayout(this);
        netValue = financeColumn(financeRow, "Líquido estimado");
        cashValue = financeColumn(financeRow, "No bolso hoje");
        financeCard.addView(financeRow);
        sourceDetails = Ui.text(this, "", 12);
        sourceDetails.setTextColor(Ui.MUTED);
        financeCard.addView(sourceDetails);
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

    private TextView financeColumn(LinearLayout row, String label) {
        LinearLayout column = Ui.column(this);
        TextView name = Ui.text(this, label, 13);
        name.setTextColor(Ui.MUTED);
        column.addView(name);
        TextView value = Ui.number(this, "R$ 0", 24);
        column.addView(value, new LinearLayout.LayoutParams(-1, Ui.dp(this, 44)));
        row.addView(column, new LinearLayout.LayoutParams(0, -2, 1));
        return value;
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
        menuAction(body, dialog, "Diagnóstico da leitura", "scan", () -> startActivity(new Intent(this, DiagnosticsActivity.class)));
        menuAction(body, dialog, "Ajustes", "settings", () -> startActivity(new Intent(this, SettingsActivity.class)));
        menuAction(body, dialog, "Salvar backup", "backup", this::backup);
        menuAction(body, dialog, "Restaurar backup", "undo", this::chooseRestoreFile);
        menuAction(body, dialog, "Buscar atualizações", "backup", this::checkUpdates);
        menuAction(body, dialog, "Importar do Copiloto", "plus", this::chooseCopilotoFile);
    }

    private void chooseCopilotoFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
                .addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, COPILOTO_FILE_REQUEST);
    }

    /** Lê o backup do Copiloto, mostra a prévia e só grava depois da confirmação. */
    private void previewCopiloto(Uri file) {
        toast("Lendo o backup do Copiloto…");
        new Thread(() -> {
            try {
                String json;
                try (java.io.InputStream input = getContentResolver().openInputStream(file);
                     java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream()) {
                    if (input == null) throw new IllegalStateException("Arquivo inacessível.");
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        if (bytes.size() + read > 50 * 1024 * 1024) throw new IllegalStateException("Arquivo maior que 50 MB.");
                        bytes.write(buffer, 0, read);
                    }
                    json = bytes.toString("UTF-8");
                }
                CopilotoImport.Plan plan = CopilotoImport.plan(json, database.sessionStarts(), java.time.ZoneId.systemDefault());
                runOnUiThread(() -> confirmCopiloto(plan));
            } catch (Exception error) {
                String message = error.getMessage() == null ? "Arquivo inválido." : error.getMessage();
                runOnUiThread(() -> toast("Não foi possível ler: " + message));
            }
        }, "rota500-copiloto-read").start();
    }

    private void confirmCopiloto(CopilotoImport.Plan plan) {
        if (plan.sessions().isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Nada novo para importar")
                    .setMessage(plan.skippedDuplicates() + " jornada(s) desse backup já estão no Rota 500.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        StringBuilder text = new StringBuilder(String.format(Locale.forLanguageTag("pt-BR"),
                "%d jornadas · bruto %s · %d gastos (%s) · %d pontos de GPS.",
                plan.sessions().size(), money(plan.grossCents() / 100.0), plan.expenses().size(),
                money(plan.expenseCents() / 100.0), plan.points().size()));
        if (plan.skippedDuplicates() > 0) text.append("\n").append(plan.skippedDuplicates()).append(" já importada(s) ficam de fora.");
        if (plan.wearPerKm() != null && plan.fuelPerKm() != null) {
            text.append(String.format(Locale.forLanguageTag("pt-BR"),
                    "\n\nCustos: desgaste R$ %.2f/km e combustível temporário R$ %.2f/km (%s).",
                    plan.wearPerKm(), plan.fuelPerKm(), plan.fuelSource()));
        }
        if (!plan.problems().isEmpty()) {
            text.append("\n\nAjustes e itens ignorados:");
            for (String problem : plan.problems()) text.append("\n• ").append(problem);
        }
        text.append("\n\nUma cópia do banco atual é feita antes.");
        new AlertDialog.Builder(this).setTitle("Importar do Copiloto?")
                .setMessage(text.toString())
                .setPositiveButton("Importar", (d, w) -> importCopiloto(plan))
                .setNegativeButton("Cancelar", null).show();
    }

    private void importCopiloto(CopilotoImport.Plan plan) {
        new Thread(() -> {
            try {
                if (!database.safetyCopy("antes-copiloto")) throw new IllegalStateException("Não foi possível copiar o banco atual.");
                int count = database.importCopiloto(plan);
                // Q46: custos reais do Copiloto.
                if (plan.wearPerKm() != null) preferences.setWearCostPerKm(plan.wearPerKm());
                if (plan.fuelPerKm() != null) preferences.setFuelCostPerKm(plan.fuelPerKm());
                runOnUiThread(() -> {
                    refresh();
                    toast(count + " jornadas importadas do Copiloto.");
                });
            } catch (Exception error) {
                String message = error.getMessage() == null ? "falha desconhecida" : error.getMessage();
                runOnUiThread(() -> toast("Importação cancelada, nada foi gravado: " + message));
            }
        }, "rota500-copiloto-import").start();
    }

    /** Q19-Q21: consulta as versões publicadas e instala a mais nova. */
    private void checkUpdates() {
        UpdateManager updates = new UpdateManager(this);
        toast("Procurando atualizações…");
        updates.check(preferences.testChannel(), check -> {
            if (isFinishing()) return;
            String current = "Instalada: " + check.installedName() + " (build " + check.installedCode() + ")";
            if (check.state() != UpdateManager.State.AVAILABLE) {
                new AlertDialog.Builder(this).setTitle("Atualizações")
                        .setMessage(check.message() + "\n\n" + current
                                + (preferences.testChannel() ? "\nCanal de teste ligado." : ""))
                        .setPositiveButton("OK", null).show();
                return;
            }
            UpdatePicker.Candidate candidate = check.candidate();
            new AlertDialog.Builder(this).setTitle("Nova versão " + candidate.versionName())
                    .setMessage(current + "\nNova: " + candidate.versionName() + " (build " + candidate.versionCode() + ")"
                            + (candidate.prerelease() ? "\nVersão de teste." : "")
                            + (preferences.backupTreeUri().isBlank() ? "\n\nSem pasta de backup configurada."
                            : "\n\nUm backup será salvo antes."))
                    .setPositiveButton("Atualizar", (d, w) -> installUpdate(updates, candidate))
                    .setNegativeButton("Depois", null).show();
        });
    }

    private void installUpdate(UpdateManager updates, UpdatePicker.Candidate candidate) {
        if (!updates.canInstall()) {
            new AlertDialog.Builder(this).setTitle("Permitir instalação")
                    .setMessage("Libere \"Instalar apps desconhecidos\" para o Rota 500 e toque em Atualizar de novo.")
                    .setPositiveButton("Abrir ajuste", (d, w) -> startActivity(updates.unknownSourcesSettings()))
                    .setNegativeButton("Cancelar", null).show();
            return;
        }
        updates.downloadAndInstall(candidate, new UpdateManager.InstallCallback() {
            @Override public void onProgress(String message) { toast(message); }
            @Override public void onError(String message) { toast(message); }
        });
    }

    private void handleInstallStatus(Intent intent) {
        int status = intent.getIntExtra(android.content.pm.PackageInstaller.EXTRA_STATUS,
                android.content.pm.PackageInstaller.STATUS_FAILURE);
        String message = UpdateManager.describeStatus(status,
                intent.getStringExtra(android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE));
        if (message != null) { toast(message); return; }
        Intent confirm = Build.VERSION.SDK_INT >= 33
                ? intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class)
                : intent.getParcelableExtra(Intent.EXTRA_INTENT);
        if (confirm != null) {
            try { startActivity(confirm); }
            catch (RuntimeException error) { toast("Não foi possível abrir o instalador."); }
        }
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

    /** Q36: depois de 1 h ou mais sem ganho, pergunta quando a jornada realmente terminou. */
    private void endShift() {
        long session = database.activeSessionId();
        if (session <= 0) return;
        long now = System.currentTimeMillis();
        long start = database.activeSessionStart();
        long lastGain = database.lastGainAt(session);
        if (now - Math.max(start, lastGain) < JourneyWatch.ASK_END_TIME_AFTER_MS) {
            finishShift(now);
            return;
        }
        long presumed = JourneyWatch.presumedEnd(start, lastGain, database.lastMovementAt(session), now);
        String label = lastGain > start ? "No último ganho" : "Quando o carro parou";
        LinearLayout body = Ui.column(this);
        android.app.Dialog dialog = Ui.sheet(this, "Quando você encerrou?", body);
        TextView hint = Ui.text(this, "Encerrar no horário real evita contar tempo parado no seu R$/h.", 14);
        hint.setTextColor(Ui.MUTED);
        body.addView(hint);
        menuAction(body, dialog, "Agora (" + clock(now) + ")", "clock", () -> finishShift(now));
        menuAction(body, dialog, label + " (" + clock(presumed) + ")", "undo", () -> finishShift(presumed));
    }

    private String clock(long at) {
        return DateFormat.getTimeInstance(DateFormat.SHORT, Locale.forLanguageTag("pt-BR")).format(new Date(at));
    }

    private void finishShift(long endAt) {
        DashboardStats stats = database.dashboardStats();
        database.endActiveSession(stats.currentZone, endAt);
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
        FinancialToday day = database.financialToday(preferences);
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
            if (!"PARTICULAR".equals(source) && database.platformMayHaveReset(source, cents)) {
                // Mesma jornada, depois da meia-noite, total menor que o último antes da virada.
                new AlertDialog.Builder(this).setTitle("O total da " + source + " zerou à meia-noite?")
                        .setMessage("Sim: o app soma " + money(cents / 100.0) + " ao que você já tinha. "
                                + "Não: o valor substitui o anterior (correção de digitação).")
                        .setPositiveButton("Sim, zerou", (d, w) -> saveMoney(source, cents, true))
                        .setNegativeButton("Não, corrigir", (d, w) -> saveMoney(source, cents, false))
                        .show();
                return;
            }
            saveMoney(source, cents, false);
        });
    }

    private void saveMoney(String source, long cents, boolean platformReset) {
        try {
            FinancialToday before = database.financialToday(preferences);
            lastMoneyEventId = "PARTICULAR".equals(source)
                    ? database.recordExtra(source, cents)
                    : database.recordPlatformTotal(source, cents, platformReset);
            notifyJourneyChanged();
            undoButton.setVisibility(View.VISIBLE);
            uiHandler.removeCallbacks(hideUndo);
            uiHandler.postDelayed(hideUndo, 8_000L);
            refresh();
            if (!"PARTICULAR".equals(source) && before.balance().legacyCents() > 0
                    && before.balance().platformCents(source) == 0
                    && database.financialToday(preferences)
                            .balance().totalCents() == before.balance().totalCents()) {
                toast("Primeiro total de " + source + " usado como base; saldo anterior preservado.");
            }
        } catch (RuntimeException error) { toast("Não foi possível registrar o valor."); }
    }

    private void togglePause() {
        if (database.openPauseId() > 0) {
            String reason = database.openPauseReason();
            database.closePause();
            notifyJourneyChanged();
            refresh();
            if ("Abastecer".equals(reason)) {
                new AlertDialog.Builder(this).setTitle("Lançar o abastecimento?")
                        .setMessage("Valor, litros e odômetro ajudam a calcular o custo real por km.")
                        .setPositiveButton("Lançar", (d, w) -> startActivity(new Intent(this, FinanceActivity.class)
                                .putExtra(FinanceActivity.EXTRA_REFUEL, true)))
                        .setNegativeButton("Agora não", null).show();
            }
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
                button.setOnClickListener(v -> {
                    database.openPause(reason);
                    dialog.dismiss();
                    notifyJourneyChanged();
                    refresh();
                });
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
        FinancialToday day = database.financialToday(preferences);
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
        cashValue.setText(money(day.cashRemainderCents() / 100.0));
        cashValue.setTextColor(day.cashRemainderCents() < 0 ? Ui.RED : Ui.NAVY);
        netValue.setText(day.estimatedNetCents() == null ? "—" : money(day.estimatedNetCents() / 100.0));
        netValue.setTextColor(day.estimatedNetCents() != null && day.estimatedNetCents() < 0 ? Ui.RED : Ui.GREEN);
        sourceDetails.setText(money(day.cashSpentCents() / 100.0) + " em gastos pagos"
                + (day.distanceKm() == null ? "" : String.format(Locale.forLanguageTag("pt-BR"), " · %.1f km %s",
                day.distanceKm(), day.distanceFromGps() ? "pelo GPS" : "pelo odômetro")));
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
        if (requestCode == COPILOTO_FILE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            previewCopiloto(data.getData());
            return;
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
