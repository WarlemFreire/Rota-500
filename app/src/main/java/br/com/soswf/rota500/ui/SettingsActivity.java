package br.com.soswf.rota500.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
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
    private EditText goal, plannedHours, minKm, minLongHour, desiredNet, weather;
    private EditText endpoint, model, apiKey, strategyHours;
    private TextView riskList;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = new AppPreferences(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.SURFACE);
        int p = (int) (18 * getResources().getDisplayMetrics().density);
        root.setPadding(p, p, p, p);
        scroll.addView(root);
        LinearLayout screen = Ui.column(this);
        screen.addView(Ui.pageHeader(this, "Ajustes", ""));
        LinearLayout goalFields = Ui.disclosure(this, root, "Meta e réguas", "chart", true);



        goal = Ui.field(this, goalFields, "Meta bruta diária (R$)", number(preferences.dailyGoal()), true);
        plannedHours = Ui.field(this, goalFields, "Jornada planejada (horas, máximo eficiente 12)", number(preferences.plannedMinutes() / 60.0), true);
        minKm = Ui.field(this, goalFields, "Régua mínima (R$/km total)", number(preferences.minimumGrossPerKm()), true);
        minLongHour = Ui.field(this, goalFields, "Corrida longa: mínimo bruto (R$/h)", number(preferences.minimumLongRidePerHour()), true);
        TextView costNote = Ui.text(this, "O custo por km (combustível + desgaste) fica em Gastos > Custos do carro. Se ele superar a régua, o custo prevalece.", 13);
        costNote.setTextColor(Ui.MUTED);
        goalFields.addView(costNote);
        desiredNet = Ui.field(this, goalFields, "Líquido desejado em particular (R$/h)", number(preferences.desiredNetPerHour()), true);
        weather = Ui.field(this, goalFields, "Clima atual (correção manual)", preferences.weather(), false);

        LinearLayout aiFields = Ui.disclosure(this, root, "IA estratégica", "chart", false);
        aiFields.addView(Ui.text(this, "Use qualquer provedor barato com API HTTPS compatível com chat-completions. Apenas dados agregados são enviados. Sem configuração, o motor local continua funcionando.", 13));
        endpoint = Ui.field(this, aiFields, "Endpoint completo HTTPS", preferences.aiEndpoint(), false);
        model = Ui.field(this, aiFields, "Identificador do modelo econômico", preferences.aiModel(), false);
        apiKey = Ui.field(this, aiFields, "Chave da API (não entra no backup)", preferences.aiApiKey(), false);
        apiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        strategyHours = Ui.field(this, aiFields, "Intervalo da estratégia externa (1 a 24 horas)", number(preferences.strategyIntervalMinutes() / 60.0), true);

        LinearLayout riskFields = Ui.disclosure(this, root, "Áreas inseguras", "route", false);
        riskList = Ui.text(this, "", 14);
        riskFields.addView(riskList);
        Button addRisk = Ui.secondaryButton(this, "Marcar última localização");
        addRisk.setOnClickListener(v -> showRiskDialog());
        riskFields.addView(addRisk);
        LinearLayout updateFields = Ui.disclosure(this, root, "Atualizações", "backup", false);
        android.widget.CheckBox testChannel = new android.widget.CheckBox(this);
        testChannel.setText("Receber versões de teste");
        testChannel.setTextColor(Ui.NAVY);
        testChannel.setTextSize(16);
        testChannel.setMinHeight(Ui.dp(this, 56));
        testChannel.setChecked(preferences.testChannel());
        testChannel.setOnCheckedChangeListener((view, checked) -> preferences.setTestChannel(checked));
        updateFields.addView(testChannel);
        TextView channelNote = Ui.text(this, "Versões de teste chegam antes, mas podem ter falhas. Busque em Mais > Buscar atualizações.", 13);
        channelNote.setTextColor(Ui.MUTED);
        updateFields.addView(channelNote);
        Button folder = Ui.secondaryButton(this, "Pasta do backup");
        folder.setOnClickListener(v -> selectBackupFolder());
        Ui.buttonIcon(folder, "backup", Ui.MUTED);
        root.addView(folder);
        Button save = Ui.button(this, "Salvar configurações");
        save.setOnClickListener(v -> save());
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout dock = Ui.column(this);
        dock.setPadding(p, 0, p, Ui.dp(this, 8));
        dock.addView(save);
        screen.addView(dock);
        Ui.install(this, screen);
        refreshRiskList();
    }

    private void save() {
        preferences.setDailyGoal(Ui.number(goal, 500));
        preferences.setPlannedMinutes((int) Math.round(Math.min(12, Math.max(1, Ui.number(plannedHours, 10))) * 60));
        preferences.setMinimumGrossPerKm(Ui.number(minKm, 1.20));
        preferences.setMinimumLongRidePerHour(Ui.number(minLongHour, 35));
        preferences.setDesiredNetPerHour(Ui.number(desiredNet, 35));
        preferences.setWeather(weather.getText().toString().trim());
        preferences.setAiEndpoint(endpoint.getText().toString());
        preferences.setAiModel(model.getText().toString());
        preferences.setAiApiKey(apiKey.getText().toString());
        preferences.setStrategyIntervalMinutes((int) Math.round(Ui.number(strategyHours, 3) * 60));
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
