package br.com.soswf.rota500.ui;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

import br.com.soswf.rota500.data.AppDatabase;
import br.com.soswf.rota500.data.AppPreferences;
import br.com.soswf.rota500.data.FinancialToday;

public final class FinanceActivity extends Activity {
    private AppDatabase database;
    private AppPreferences preferences;
    private TextView summary, costs, estimate, fixed;
    private Button undo;
    private long undoId;
    private String undoKind = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideUndo = () -> { if (undo != null) undo.setVisibility(View.GONE); };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        database = AppDatabase.get(this);
        preferences = new AppPreferences(this);
        build();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }
    @Override protected void onDestroy() { handler.removeCallbacks(hideUndo); super.onDestroy(); }

    private void build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.SURFACE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18), Ui.dp(this, 30));
        scroll.addView(root);
        root.addView(Ui.title(this, "Gastos e resultado"));

        LinearLayout result = Ui.card(this);
        summary = Ui.text(this, "", 21);
        result.addView(summary);
        costs = Ui.text(this, "", 15);
        costs.setTextColor(Ui.MUTED);
        result.addView(costs);
        estimate = Ui.text(this, "", 16);
        result.addView(estimate);
        root.addView(result);

        root.addView(Ui.sectionTitle(this, "Lançar gasto"));
        root.addView(pair("COMBUSTÍVEL", "COMBUSTIVEL", "ALIMENTAÇÃO", "ALIMENTACAO"));
        root.addView(pair("MANUTENÇÃO", "MANUTENCAO", "PARCELA PAGA", "FINANCIAMENTO"));
        Button other = Ui.secondaryButton(this, "Outro gasto: pedágio, lavagem, seguro...");
        other.setOnClickListener(v -> expense("OUTROS", "Outro gasto"));
        root.addView(other);

        root.addView(Ui.sectionTitle(this, "Distância e custo por km"));
        TextView explanation = Ui.text(this,
                "Anote o odômetro no início e no fim. Com apenas um ponto, o km e o líquido estimado ficam em branco.", 13);
        explanation.setTextColor(Ui.MUTED);
        root.addView(explanation);
        Button odo = Ui.secondaryButton(this, "REGISTRAR ODÔMETRO");
        odo.setOnClickListener(v -> {
            if (database.activeSessionId() <= 0) { toast("Inicie uma jornada primeiro."); return; }
            NumberPad.show(this, "Odômetro atual (km)", 0, "km", value -> {
                if (value <= 0) return;
                try {
                    long id = database.recordOdometer(value);
                    offerUndo("odômetro", id);
                    refresh();
                } catch (IllegalArgumentException error) { toast("Odômetro inválido."); }
            });
        });
        root.addView(odo);
        Button fuelRate = Ui.secondaryButton(this, "Combustível estimado por km");
        fuelRate.setOnClickListener(v -> NumberPad.show(this, "Combustível por km (R$)", 2, "", value -> {
            preferences.setFuelCostPerKm(value / 100.0);
            refresh();
        }));
        root.addView(fuelRate);
        Button wearRate = Ui.secondaryButton(this, "Desgaste estimado por km");
        wearRate.setOnClickListener(v -> NumberPad.show(this, "Desgaste por km (R$)", 2, "", value -> {
            preferences.setWearCostPerKm(value / 100.0);
            refresh();
        }));
        root.addView(wearRate);

        root.addView(Ui.sectionTitle(this, "Compromissos mensais"));
        fixed = Ui.text(this, "", 15);
        root.addView(fixed);
        Button monthly = Ui.secondaryButton(this, "Parcela mensal do carro");
        monthly.setOnClickListener(v -> NumberPad.show(this, "Parcela mensal (R$)", 2, "", value -> {
            preferences.setMonthlyFinancingCents(value);
            refresh();
        }));
        root.addView(monthly);
        Button otherMonthly = Ui.secondaryButton(this, "Seguro, impostos e outros fixos/mês");
        otherMonthly.setOnClickListener(v -> NumberPad.show(this, "Outros fixos por mês (R$)", 2, "", value -> {
            preferences.setOtherMonthlyCents(value);
            refresh();
        }));
        root.addView(otherMonthly);
        Button workDays = Ui.secondaryButton(this, "Dias de trabalho por mês");
        workDays.setOnClickListener(v -> NumberPad.show(this, "Dias previstos no mês", 0, "dias", value -> {
            if (value < 1 || value > 31) { toast("Informe de 1 a 31 dias."); return; }
            preferences.setWorkDaysPerMonth((int) value);
            refresh();
        }));
        root.addView(workDays);

        undo = Ui.secondaryButton(this, "DESFAZER ÚLTIMO LANÇAMENTO");
        undo.setVisibility(View.GONE);
        undo.setOnClickListener(v -> {
            if ("odômetro".equals(undoKind)) database.undoOdometer(undoId);
            else database.undoExpense(undoId);
            undo.setVisibility(View.GONE);
            refresh();
        });
        root.addView(undo);
        setContentView(scroll);
    }

    private LinearLayout pair(String firstLabel, String firstKey, String secondLabel, String secondKey) {
        LinearLayout row = new LinearLayout(this);
        Button first = Ui.secondaryButton(this, firstLabel);
        Button second = Ui.secondaryButton(this, secondLabel);
        first.setOnClickListener(v -> expense(firstKey, firstLabel));
        second.setOnClickListener(v -> expense(secondKey, secondLabel));
        row.addView(first, new LinearLayout.LayoutParams(0, Ui.dp(this, 66), 1));
        row.addView(second, new LinearLayout.LayoutParams(0, Ui.dp(this, 66), 1));
        return row;
    }

    private void expense(String category, String label) {
        NumberPad.show(this, "Gasto: " + label, 2, "", value -> {
            if (value <= 0) return;
            long id = database.recordExpense(category, value);
            offerUndo("gasto", id);
            refresh();
        });
    }

    private void offerUndo(String kind, long id) {
        undoKind = kind;
        undoId = id;
        undo.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideUndo);
        handler.postDelayed(hideUndo, 8_000L);
    }

    private void refresh() {
        FinancialToday day = database.financialToday(preferences.fuelCostPerKm(), preferences.wearCostPerKm());
        summary.setText("Bruto " + money(day.balance().totalCents())
                + "\nApós gastos lançados " + money(day.cashRemainderCents()));
        costs.setText("Combustível " + money(day.fuelCents())
                + "  •  Alimentação " + money(day.foodCents())
                + "\nManutenção " + money(day.maintenanceCents())
                + "  •  Parcela paga " + money(day.financingCents())
                + "\nOutros " + money(day.otherCents()));
        estimate.setText("Km pelo odômetro: " + (day.odometerKm() == null ? "—" : String.format(
                Locale.forLanguageTag("pt-BR"), "%.1f km", day.odometerKm()))
                + "\nLíquido operacional estimado: " + (day.estimatedNetCents() == null ? "—" : money(day.estimatedNetCents()))
                + "\nCombustível/km configurado " + rate(preferences.fuelCostPerKm())
                + "  •  desgaste/km " + rate(preferences.wearCostPerKm()));
        long monthly = preferences.monthlyFinancingCents() + preferences.otherMonthlyCents();
        fixed.setText("Parcela " + money(preferences.monthlyFinancingCents()) + "/mês"
                + "  •  outros fixos " + money(preferences.otherMonthlyCents()) + "/mês"
                + "\nPara cobrir em " + preferences.workDaysPerMonth() + " dias: "
                + money(Math.round(monthly / (double) preferences.workDaysPerMonth())) + " por dia de trabalho."
                + "\nEsse planejamento não é descontado de cada corrida.");
    }

    private String money(long cents) { return String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", cents / 100.0); }
    private String rate(double value) { return value <= 0 ? "—" : String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", value); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
}
