package br.com.soswf.rota500.ui;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
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
    private TextView summary, gross, paid, estimate, distance, fixed;
    private final TextView[] categoryTotals = new TextView[5];
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
        LinearLayout screen = Ui.column(this);
        screen.addView(Ui.pageHeader(this, "Gastos", "HOJE"));
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root = Ui.column(this);
        root.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), Ui.dp(this, 16));
        scroll.addView(root);

        LinearLayout result = Ui.card(this);
        result.addView(Ui.label(this, "APÓS GASTOS PAGOS"));
        summary = Ui.number(this, "R$ 0", 42);
        result.addView(summary, new LinearLayout.LayoutParams(-1, Ui.dp(this, 62)));
        LinearLayout numbers = new LinearLayout(this);
        gross = summaryMetric(numbers, "Ganho bruto", Ui.NAVY);
        paid = summaryMetric(numbers, "Gastos pagos", Ui.AMBER);
        result.addView(numbers);
        root.addView(result);

        root.addView(Ui.sectionTitle(this, "Onde o dinheiro saiu"));
        TextView tip = Ui.text(this, "Toque na categoria para lançar um gasto.", 13);
        tip.setTextColor(Ui.MUTED);
        root.addView(tip);
        String[] names = {"Combustível", "Alimentação", "Manutenção", "Parcela do carro", "Outros gastos"};
        String[] categories = {"COMBUSTIVEL", "ALIMENTACAO", "MANUTENCAO", "FINANCIAMENTO", "OUTROS"};
        String[] icons = {"fuel", "food", "tool", "car", "wallet"};
        LinearLayout expenses = Ui.card(this);
        expenses.setPadding(Ui.dp(this, 12), Ui.dp(this, 2), Ui.dp(this, 12), Ui.dp(this, 2));
        for (int i = 0; i < names.length; i++) {
            final String category = categories[i], name = names[i];
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(Ui.dp(this, 72));
            row.setPadding(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
            row.setBackground(Ui.ripple(this, Ui.CARD, 12));
            row.setOnClickListener(v -> expense(category, name));
            TextView label = Ui.text(this, name, 15);
            label.setCompoundDrawables(Ui.icon(this, icons[i], Ui.MUTED), null, null, null);
            label.setCompoundDrawablePadding(Ui.dp(this, 12));
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            TextView total = Ui.number(this, "R$ 0", 19);
            total.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            row.addView(total, new LinearLayout.LayoutParams(Ui.dp(this, 108), Ui.dp(this, 48)));
            TextView plus = Ui.text(this, "+", 24);
            plus.setTextColor(Ui.GREEN);
            plus.setGravity(Gravity.CENTER);
            row.addView(plus, new LinearLayout.LayoutParams(Ui.dp(this, 30), -2));
            categoryTotals[i] = total;
            expenses.addView(row);
            if (i < names.length - 1) expenses.addView(Ui.divider(this));
        }
        root.addView(expenses);

        root.addView(Ui.sectionTitle(this, "Distância e resultado"));
        LinearLayout operating = Ui.card(this);
        operating.addView(Ui.label(this, "LÍQUIDO OPERACIONAL ESTIMADO"));
        estimate = Ui.number(this, "—", 30);
        operating.addView(estimate, new LinearLayout.LayoutParams(-1, Ui.dp(this, 48)));
        distance = Ui.text(this, "", 13);
        distance.setTextColor(Ui.MUTED);
        operating.addView(distance);
        Button odo = Ui.secondaryButton(this, "Registrar odômetro");
        Ui.buttonIcon(odo, "route", Ui.GREEN);
        odo.setOnClickListener(v -> odometer());
        operating.addView(odo);
        root.addView(operating);

        fixed = Ui.text(this, "", 14);
        fixed.setTextColor(Ui.MUTED);
        root.addView(fixed);
        Button config = Ui.secondaryButton(this, "Custos por km e fixos mensais");
        Ui.buttonIcon(config, "settings", Ui.MUTED);
        config.setOnClickListener(v -> costSettings());
        root.addView(config);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout dock = Ui.column(this);
        dock.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), Ui.dp(this, 8));
        undo = Ui.secondaryButton(this, "Gasto salvo · Desfazer");
        Ui.buttonIcon(undo, "undo", Ui.GREEN);
        undo.setTextColor(Ui.GREEN);
        undo.setVisibility(View.GONE);
        undo.setOnClickListener(v -> {
            if ("odômetro".equals(undoKind)) database.undoOdometer(undoId);
            else database.undoExpense(undoId);
            undo.setVisibility(View.GONE);
            refresh();
        });
        dock.addView(undo);
        screen.addView(dock);
        Ui.install(this, screen);
    }

    private TextView summaryMetric(LinearLayout row, String label, int color) {
        LinearLayout column = Ui.column(this);
        TextView name = Ui.text(this, label, 13);
        name.setTextColor(Ui.MUTED);
        column.addView(name);
        TextView number = Ui.number(this, "", 23);
        number.setTextColor(color);
        column.addView(number, new LinearLayout.LayoutParams(-1, Ui.dp(this, 38)));
        row.addView(column, new LinearLayout.LayoutParams(0, -2, 1));
        return number;
    }

    private void odometer() {
        if (database.activeSessionId() <= 0) { toast("Inicie uma jornada primeiro."); return; }
        NumberPad.show(this, "Odômetro atual", 0, "km", value -> {
            if (value <= 0) return;
            try { offerUndo("odômetro", database.recordOdometer(value)); refresh(); }
            catch (IllegalArgumentException error) { toast("Odômetro inválido."); }
        });
    }

    private void costSettings() {
        LinearLayout body = Ui.column(this);
        Dialog dialog = Ui.sheet(this, "Custos do carro", body);
        body.addView(Ui.label(this, "ESTIMATIVAS POR QUILÔMETRO"));
        setting(body, dialog, "Combustível · " + rate(preferences.fuelCostPerKm()) + "/km", "fuel", () ->
                NumberPad.show(this, "Combustível por km", 2, "", value -> {
                    preferences.setFuelCostPerKm(value / 100.0); refresh();
                }));
        setting(body, dialog, "Desgaste · " + rate(preferences.wearCostPerKm()) + "/km", "tool", () ->
                NumberPad.show(this, "Desgaste por km", 2, "", value -> {
                    preferences.setWearCostPerKm(value / 100.0); refresh();
                }));
        body.addView(Ui.label(this, "PLANEJAMENTO MENSAL"));
        setting(body, dialog, "Parcela · " + money(preferences.monthlyFinancingCents()), "car", () ->
                NumberPad.show(this, "Parcela mensal do carro", 2, "", value -> {
                    preferences.setMonthlyFinancingCents(value); refresh();
                }));
        setting(body, dialog, "Outros fixos · " + money(preferences.otherMonthlyCents()), "card", () ->
                NumberPad.show(this, "Seguro, impostos e outros/mês", 2, "", value -> {
                    preferences.setOtherMonthlyCents(value); refresh();
                }));
        setting(body, dialog, preferences.workDaysPerMonth() + " dias de trabalho/mês", "clock", () ->
                NumberPad.show(this, "Dias previstos no mês", 0, "dias", value -> {
                    if (value < 1 || value > 31) { toast("Informe de 1 a 31 dias."); return; }
                    preferences.setWorkDaysPerMonth((int) value); refresh();
                }));
        TextView note = Ui.text(this, "Fixos mensais são planejamento. Registre uma parcela em Gastos apenas quando pagar.", 13);
        note.setTextColor(Ui.MUTED);
        body.addView(note);
    }

    private void setting(LinearLayout body, Dialog dialog, String title, String icon, Runnable action) {
        Button button = Ui.secondaryButton(this, title);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        Ui.buttonIcon(button, icon, Ui.MUTED);
        body.addView(button);
        button.setOnClickListener(v -> { dialog.dismiss(); action.run(); });
    }

    private void expense(String category, String label) {
        NumberPad.show(this, label, 2, "", value -> {
            if (value <= 0) return;
            offerUndo("gasto", database.recordExpense(category, value));
            refresh();
        });
    }

    private void offerUndo(String kind, long id) {
        undoKind = kind;
        undoId = id;
        undo.setText("odômetro".equals(kind) ? "Odômetro salvo · Desfazer" : "Gasto salvo · Desfazer");
        undo.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideUndo);
        handler.postDelayed(hideUndo, 8_000L);
    }

    private void refresh() {
        FinancialToday day = database.financialToday(preferences.fuelCostPerKm(), preferences.wearCostPerKm());
        summary.setText(money(day.cashRemainderCents()));
        summary.setTextColor(day.cashRemainderCents() < 0 ? Ui.RED : Ui.GREEN);
        gross.setText(money(day.balance().totalCents()));
        paid.setText(money(day.cashSpentCents()));
        long[] amounts = {day.fuelCents(), day.foodCents(), day.maintenanceCents(), day.financingCents(), day.otherCents()};
        for (int i = 0; i < amounts.length; i++) categoryTotals[i].setText(money(amounts[i]));
        estimate.setText(day.estimatedNetCents() == null ? "—" : money(day.estimatedNetCents()));
        estimate.setTextColor(day.estimatedNetCents() != null && day.estimatedNetCents() < 0 ? Ui.RED : Ui.NAVY);
        distance.setText(day.odometerKm() == null ? "Registre o odômetro no início e no fim para estimar."
                : String.format(Locale.forLanguageTag("pt-BR"), "%.1f km registrados · custos estimados por km", day.odometerKm()));
        long monthly = preferences.monthlyFinancingCents() + preferences.otherMonthlyCents();
        fixed.setText(monthly > 0 ? "Fixos: reserve " + money(Math.round(monthly / (double) preferences.workDaysPerMonth()))
                + " por dia trabalhado. Não descontado acima." : "Configure os custos do carro para planejar sua reserva diária.");
    }

    private String money(long cents) { return String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", cents / 100.0); }
    private String rate(double value) { return String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f", value); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
}
