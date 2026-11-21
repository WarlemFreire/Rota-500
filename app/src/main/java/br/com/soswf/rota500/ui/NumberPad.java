package br.com.soswf.rota500.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;
import java.util.function.LongConsumer;

/** Fixed, large keyboard with no system IME; the entered integer represents cents. */
final class NumberPad {
    private NumberPad() { }

    static void show(Context context, String question, int decimals, String unit,
            LongConsumer onSave) {
        show(context, question, decimals, unit, 0, onSave);
    }

    /**
     * decimals 2 sem unidade = dinheiro (centavos). Com unidade, o valor é exibido com as
     * casas decimais pedidas (ex.: litros 2 casas → 4250 = 42,50 L). initial já vem escalado.
     */
    static void show(Context context, String question, int decimals, String unit, long initial,
            LongConsumer onSave) {
        LinearLayout root = Ui.column(context);
        TextView amount = Ui.number(context, "", 46);
        amount.setGravity(Gravity.CENTER);
        amount.setTextColor(Ui.GREEN);
        root.addView(amount, new LinearLayout.LayoutParams(-1, Ui.dp(context, 72)));
        boolean money = decimals == 2 && unit.isEmpty();
        TextView hint = Ui.text(context, money ? "Digite o valor com centavos, sem vírgula"
                : decimals > 0 ? "Digite com " + decimals + " casas decimais, sem vírgula" : "Informe o valor inteiro", 12);
        hint.setTextColor(Ui.MUTED);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint);
        final long[] digits = {Math.max(0, initial)};
        Runnable render = () -> {
            if (money) amount.setText(String.format(Locale.forLanguageTag("pt-BR"),
                    "R$ %.2f", digits[0] / 100.0));
            else if (decimals > 0) amount.setText(String.format(Locale.forLanguageTag("pt-BR"),
                    "%." + decimals + "f %s", digits[0] / Math.pow(10, decimals), unit));
            else amount.setText(String.format(Locale.forLanguageTag("pt-BR"), "%,d %s", digits[0], unit));
        };
        render.run();
        String[][] keys = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"C", "0", "⌫"}};
        for (String[] rowKeys : keys) {
            LinearLayout row = new LinearLayout(context);
            for (String key : rowKeys) {
                Button keyButton = Ui.secondaryButton(context, key);
                keyButton.setTextSize(27);
                keyButton.setContentDescription("C".equals(key) ? "Limpar valor" : "⌫".equals(key) ? "Apagar último dígito" : key);
                keyButton.setMinHeight(Ui.dp(context, 64));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                        Ui.dp(context, 64), 1);
                params.setMargins(Ui.dp(context, 3), Ui.dp(context, 3), Ui.dp(context, 3), Ui.dp(context, 3));
                row.addView(keyButton, params);
                keyButton.setOnClickListener(v -> {
                    if ("C".equals(key)) digits[0] = 0;
                    else if ("⌫".equals(key)) digits[0] /= 10;
                    else if (digits[0] <= 99_999_999) digits[0] = digits[0] * 10 + Integer.parseInt(key);
                    render.run();
                });
            }
            root.addView(row);
        }
        Button save = Ui.primaryButton(context, "Salvar valor");
        root.addView(save, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 64)));
        Dialog dialog = Ui.sheet(context, question, root);
        save.setOnClickListener(v -> {
            dialog.dismiss();
            onSave.accept(digits[0]);
        });
    }
}
