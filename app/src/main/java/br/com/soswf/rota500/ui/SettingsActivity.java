package br.com.soswf.rota500.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

import br.com.soswf.rota500.data.AppDatabase;
import br.com.soswf.rota500.data.AppPreferences;

public final class SettingsActivity extends Activity {
    private static final int BACKUP_FOLDER_REQUEST = 40;
    private AppPreferences preferences;
    private EditText goal, plannedHours, minKm, minLongHour, costKm, desiredNet, weather;
    private EditText endpoint, model, apiKey, strategyHours;
    private CheckBox calibration, autoReject;
    private TextView riskList;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = new AppPreferences(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (18 * getResources().getDisplayMetrics().density);
        root.setPadding(p, p, p, p);
        scroll.addView(root);
        root.addView(Ui.title(this, "Regras e configurações"));

        goal = Ui.field(this, root, "Meta bruta diária (R$)", number(preferences.dailyGoal()), true);
        plannedHours = Ui.field(this, root, "Jornada planejada (horas, máximo eficiente 12)", number(preferences.plannedMinutes() / 60.0), true);
        minKm = Ui.field(this, root, "Régua mínima (R$/km total)", number(preferences.minimumGrossPerKm()), true);
        minLongHour = Ui.field(this, root, "Corrida longa: mínimo bruto (R$/h)", number(preferences.minimumLongRidePerHour()), true);
        costKm = Ui.field(this, root, "Custo total (R$/km: combustível, manutenção e desgaste)", number(preferences.operatingCostPerKm()), true);
        root.addView(Ui.text(this, "Se o custo por km superar a régua, o custo prevalece. Confira esse valor no seu carro antes de usar a recomendação.", 13));
        desiredNet = Ui.field(this, root, "Líquido desejado em particular (R$/h)", number(preferences.desiredNetPerHour()), true);
        weather = Ui.field(this, root, "Clima atual (correção manual)", preferences.weather(), false);

        root.addView(Ui.text(this, "IA estratégica opcional", 20));
        root.addView(Ui.text(this, "Use qualquer provedor barato com API HTTPS compatível com chat-completions. Apenas dados agregados são enviados. Sem configuração, o motor local continua funcionando.", 13));
        endpoint = Ui.field(this, root, "Endpoint completo HTTPS", preferences.aiEndpoint(), false);
        model = Ui.field(this, root, "Identificador do modelo econômico", preferences.aiModel(), false);
        apiKey = Ui.field(this, root, "Chave da API (não entra no backup)", preferences.aiApiKey(), false);
        apiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        strategyHours = Ui.field(this, root, "Intervalo da estratégia externa (1 a 24 horas)", number(preferences.strategyIntervalMinutes() / 60.0), true);

        root.addView(Ui.text(this, "Automação de recusa", 20));
        root.addView(Ui.text(this, "O aceite é sempre manual. A recusa usa somente as regras fixas, após atraso técnico, e pode parar de funcionar quando Uber ou 99 alterarem a tela.", 13));
        calibration = new CheckBox(this);
        calibration.setText("Calibração conferida em ofertas reais");
        calibration.setChecked(preferences.calibrationConfirmed());
        root.addView(calibration);
        autoReject = new CheckBox(this);
        autoReject.setText("Habilitar recusa automática pelas regras fixas");
        autoReject.setChecked(preferences.autoRejectEnabled());
        root.addView(autoReject);

        root.addView(Ui.text(this, "Áreas inseguras", 20));
        riskList = Ui.text(this, "", 14);
        root.addView(riskList);
        Button addRisk = Ui.button(this, "Marcar minha última localização como área insegura");
        addRisk.setOnClickListener(v -> showRiskDialog());
        root.addView(addRisk);
        Button folder = Ui.button(this, "Trocar pasta do backup (pode ser Google Drive)");
        folder.setOnClickListener(v -> selectBackupFolder());
        root.addView(folder);
        Button save = Ui.button(this, "Salvar configurações");
        save.setOnClickListener(v -> save());
        root.addView(save);
        setContentView(scroll);
        refreshRiskList();
    }

    private void save() {
        preferences.setDailyGoal(Ui.number(goal, 500));
        preferences.setPlannedMinutes((int) Math.round(Math.min(12, Math.max(1, Ui.number(plannedHours, 10))) * 60));
        preferences.setMinimumGrossPerKm(Ui.number(minKm, 1.20));
        preferences.setMinimumLongRidePerHour(Ui.number(minLongHour, 35));
        preferences.setOperatingCostPerKm(Ui.number(costKm, 1.50));
        preferences.setDesiredNetPerHour(Ui.number(desiredNet, 35));
        preferences.setWeather(weather.getText().toString().trim());
        preferences.setAiEndpoint(endpoint.getText().toString());
        preferences.setAiModel(model.getText().toString());
        preferences.setAiApiKey(apiKey.getText().toString());
        preferences.setStrategyIntervalMinutes((int) Math.round(Ui.number(strategyHours, 3) * 60));
        preferences.setCalibrationConfirmed(calibration.isChecked());
        if (autoReject.isChecked() && !calibration.isChecked()) {
            preferences.setAutoRejectEnabled(false);
            autoReject.setChecked(false);
            Toast.makeText(this, "Confirme a calibração antes de ativar a recusa.", Toast.LENGTH_LONG).show();
        } else preferences.setAutoRejectEnabled(autoReject.isChecked());
        Toast.makeText(this, "Configurações salvas.", Toast.LENGTH_SHORT).show();
    }

    private void showRiskDialog() {
        double[] coordinate = AppDatabase.get(this).latestCoordinate();
        if (coordinate == null) {
            Toast.makeText(this, "Ainda não há localização coletada. Inicie uma jornada e aguarde o GPS.", Toast.LENGTH_LONG).show();
            return;
        }
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        EditText name = Ui.field(this, fields, "Nome da área", "", false);
        EditText radius = Ui.field(this, fields, "Raio em metros", "600", true);
        new AlertDialog.Builder(this).setTitle("Adicionar área insegura").setView(fields)
                .setPositiveButton("Adicionar", (dialog, which) -> {
                    String value = name.getText().toString().trim();
                    if (!value.isBlank()) {
                        AppDatabase.get(this).addRiskZone(value, coordinate[0], coordinate[1], Ui.number(radius, 600));
                        refreshRiskList();
                    }
                }).setNegativeButton("Cancelar", null).show();
    }

    private void refreshRiskList() {
        riskList.setText(String.join("\n", AppDatabase.get(this).riskZoneSummaries()));
    }

    private void selectBackupFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, BACKUP_FOLDER_REQUEST);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BACKUP_FOLDER_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri, flags);
            preferences.setBackupTreeUri(uri.toString());
            Toast.makeText(this, "Pasta de backup salva.", Toast.LENGTH_SHORT).show();
        }
    }

    private String number(double value) { return String.format(Locale.forLanguageTag("pt-BR"), "%.2f", value); }
}
