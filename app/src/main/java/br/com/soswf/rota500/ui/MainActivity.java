package br.com.soswf.rota500.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

import br.com.soswf.rota500.ai.StrategyCoordinator;
import br.com.soswf.rota500.backup.BackupManager;
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
    private AppDatabase database;
    private AppPreferences preferences;
    private TextView status;
    private TextView metrics;
    private TextView advice;
    private Button shiftButton;
    private boolean pendingStart;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        database = AppDatabase.get(this);
        preferences = new AppPreferences(this);
        buildUi();
    }

    @Override protected void onResume() { super.onResume(); refresh(); maybeAskForContext(); }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (18 * getResources().getDisplayMetrics().density);
        root.setPadding(p, p, p, p);
        scroll.addView(root);
        root.addView(Ui.title(this, "Rota 500"));
        status = Ui.text(this, "", 17);
        metrics = Ui.text(this, "", 20);
        advice = Ui.text(this, "", 17);
        root.addView(status);
        root.addView(metrics);
        root.addView(advice);

        shiftButton = Ui.button(this, "Iniciar jornada");
        shiftButton.setOnClickListener(v -> toggleShift());
        root.addView(shiftButton);
        Button gross = Ui.button(this, "Atualizar total bruto (Uber + 99 + particulares)");
        gross.setOnClickListener(v -> showGrossDialog());
        root.addView(gross);
        Button strategy = Ui.button(this, "Recalcular estratégia");
        strategy.setOnClickListener(v -> requestStrategy());
        root.addView(strategy);
        Button quote = Ui.button(this, "Orçar corrida particular");
        quote.setOnClickListener(v -> startActivity(new Intent(this, QuoteActivity.class)));
        root.addView(quote);
        Button privateRides = Ui.button(this, "Gerenciar corridas particulares");
        privateRides.setOnClickListener(v -> startActivity(new Intent(this, PrivateRidesActivity.class)));
        root.addView(privateRides);
        Button accessibility = Ui.button(this, "Ativar leitura de tela");
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibility);
        Button dynamic = Ui.button(this, "Ler mapa dinâmico na próxima tela");
        dynamic.setOnClickListener(v -> {
            if (database.activeSessionId() <= 0) { toast("Inicie a jornada primeiro."); return; }
            preferences.requestDynamicCapture();
            toast("Agora abra o mapa do app de motorista. A imagem será analisada na memória e descartada.");
        });
        root.addView(dynamic);
        Button backup = Ui.button(this, "Fazer backup de dados");
        backup.setOnClickListener(v -> backup());
        root.addView(backup);
        Button restore = Ui.button(this, "Restaurar backup de dados");
        restore.setOnClickListener(v -> chooseRestoreFile());
        root.addView(restore);
        Button settings = Ui.button(this, "Regras e configurações");
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        root.addView(settings);
        root.addView(Ui.text(this, "O aplicativo não aceita corridas automaticamente. Segurança prevalece sobre faturamento.", 13));
        setContentView(scroll);
    }

    private void toggleShift() {
        if (database.activeSessionId() > 0) {
            database.endActiveSession(database.dashboardStats().currentZone);
            Intent stop = new Intent(this, TrackingService.class).setAction(TrackingService.ACTION_STOP);
            startService(stop);
            refresh();
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
        Intent start = new Intent(this, TrackingService.class).setAction(TrackingService.ACTION_START);
        startForegroundService(start);
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_REQUEST);
        }
        refresh();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == LOCATION_REQUEST && pendingStart) {
            boolean granted = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            if (granted) startShift();
            else { pendingStart = false; toast("A localização é necessária durante a jornada."); }
        }
    }

    private void showGrossDialog() {
        if (database.activeSessionId() <= 0) { toast("Inicie a jornada primeiro."); return; }
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format(Locale.forLanguageTag("pt-BR"), "%.2f",
                database.dashboardStats().activeGrossCents / 100.0));
        new AlertDialog.Builder(this).setTitle("Bruto acumulado nesta jornada")
                .setMessage("Some Uber, 99 e particulares desta jornada. O painel soma as jornadas do dia.")
                .setView(input)
                .setPositiveButton("Salvar", (dialog, which) -> {
                    double value = Ui.number(input, -1);
                    if (value >= 0) { database.setActiveGrossCents(Math.round(value * 100)); refresh(); }
                }).setNegativeButton("Cancelar", null).show();
    }

    private void requestStrategy() {
        advice.setText("Calculando estratégia…");
        new StrategyCoordinator(this).requestBestAdvice((result, source, warning) -> {
            advice.setText(formatAdvice(result, source, warning));
            refreshMetricsOnly();
        });
    }

    private void maybeAskForContext() {
        if (database.activeSessionId() <= 0 || !database.isStationaryNow() || !preferences.canAskForContext()) return;
        if ("Não informado".equals(preferences.weather())) {
            preferences.markContextPrompt();
            new AlertDialog.Builder(this).setTitle("Como está o tempo agora?")
                    .setItems(new String[]{"Chovendo", "Sem chuva", "Não sei"}, (dialog, which) -> {
                        preferences.setWeather(new String[]{"Chovendo", "Sem chuva", "Não informado"}[which]);
                    }).show();
        } else if (database.dashboardStats().idleMinutes >= 20 && "Não lido".equals(database.latestDynamicLevel())) {
            preferences.markContextPrompt();
            new AlertDialog.Builder(this).setTitle("Verificar movimento da área")
                    .setMessage("Se você estiver parado, abra o mapa dinâmico da Uber ou 99. A leitura de cores é experimental.")
                    .setPositiveButton("Vou abrir o mapa", (dialog, which) -> preferences.requestDynamicCapture())
                    .setNegativeButton("Agora não", null).show();
        }
    }

    private String formatAdvice(StrategyAdvice result, String source, String warning) {
        return result.title + "\n" + result.explanation + String.format(Locale.forLanguageTag("pt-BR"),
                "\nProjeção: R$ %.2f • confiança %.0f%% • %s%s", result.projectedFinalGross,
                result.confidence * 100, source, warning.isEmpty() ? "" : "\n" + warning);
    }

    private void refresh() {
        DashboardStats stats = database.dashboardStats();
        boolean active = stats.hasActiveSession();
        shiftButton.setText(active ? "Encerrar jornada" : "Iniciar jornada");
        status.setText(active ? "Jornada ativa — localização coletada em primeiro plano" : "Jornada encerrada — localização desligada");
        refreshMetricsOnly();
        if (active && advice.getText().toString().isBlank()) {
            StrategyAdvice local = new StrategyCoordinator(this).localAdvice();
            advice.setText(formatAdvice(local, "motor-local", ""));
        }
    }

    private void refreshMetricsOnly() {
        DashboardStats stats = database.dashboardStats();
        int worked = stats.workedMinutes(System.currentTimeMillis());
        double gross = stats.grossCents / 100.0;
        double remaining = Math.max(0, preferences.dailyGoal() - gross);
        metrics.setText(String.format(Locale.forLanguageTag("pt-BR"),
                "R$ %.2f / R$ %.2f\nFaltam R$ %.2f • %dh%02d trabalhadas\n%d ofertas na última hora • %d dentro da régua\nRitmo robusto: R$ %.2f/h",
                gross, preferences.dailyGoal(), remaining, worked / 60, worked % 60,
                stats.offersLastHour, stats.viableOffersLastHour, stats.robustGrossPerHour));
    }

    private void backup() {
        String stored = preferences.backupTreeUri();
        if (stored.isBlank()) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, BACKUP_FOLDER_REQUEST);
        } else writeBackup(Uri.parse(stored));
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BACKUP_FOLDER_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri, flags);
            preferences.setBackupTreeUri(uri.toString());
            writeBackup(uri);
        }
        if (requestCode == RESTORE_FILE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri file = data.getData();
            new AlertDialog.Builder(this).setTitle("Substituir dados locais?")
                    .setMessage("O backup selecionado substituirá jornadas, ofertas e corridas particulares deste celular. Faça um backup dos dados atuais antes, se quiser guardá-los.")
                    .setPositiveButton("Restaurar", (dialog, which) -> restoreBackup(file))
                    .setNegativeButton("Cancelar", null).show();
        }
    }

    private void chooseRestoreFile() {
        if (database.activeSessionId() > 0) { toast("Encerre a jornada antes de restaurar dados."); return; }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json")
                .addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, RESTORE_FILE_REQUEST);
    }

    private void restoreBackup(Uri uri) {
        new Thread(() -> {
            try {
                new BackupManager(this).restoreDataBackup(uri);
                runOnUiThread(() -> { refresh(); toast("Dados restaurados."); });
            } catch (Exception error) {
                runOnUiThread(() -> toast("Falha ao restaurar: confira o arquivo JSON do Rota 500."));
            }
        }, "rota500-restore").start();
    }

    private void writeBackup(Uri tree) {
        toast("Criando backup…");
        new Thread(() -> {
            try {
                new BackupManager(this).writeDataBackup(tree);
                runOnUiThread(() -> toast("Backup de dados concluído."));
            } catch (Exception error) {
                runOnUiThread(() -> toast("Falha no backup. Escolha novamente a pasta nas configurações do sistema."));
            }
        }, "rota500-backup").start();
    }

    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
}
