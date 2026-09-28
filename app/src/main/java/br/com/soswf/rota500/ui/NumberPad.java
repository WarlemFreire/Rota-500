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
        Dialog dialog = new Dialog(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(context, 18), Ui.dp(context, 18), Ui.dp(context, 18), Ui.dp(context, 16));
        root.setBackground(Ui.rounded(Ui.CARD, 20, Ui.BORDER, context));
        TextView title = Ui.text(context, question, 19);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);
        TextView amount = Ui.text(context, "", 38);
        amount.setGravity(Gravity.CENTER);
        amount.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        amount.setTextColor(Ui.GREEN);
        root.addView(amount);
        final long[] digits = {0};
        Runnable render = () -> {
            if (decimals == 2) amount.setText(String.format(Locale.forLanguageTag("pt-BR"),
                    "R$ %.2f", digits[0] / 100.0));
            else amount.setText(String.format(Locale.forLanguageTag("pt-BR"), "%,d %s", digits[0], unit));
        };
        render.run();
        String[][] keys = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"C", "0", "⌫"}};
        for (String[] rowKeys : keys) {
            LinearLayout row = new LinearLayout(context);
            for (String key : rowKeys) {
                Button keyButton = Ui.secondaryButton(context, key);
                keyButton.setTextSize(25);
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
        Button save = Ui.primaryButton(context, "SALVAR");
        root.addView(save, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 64)));
        save.setOnClickListener(v -> {
            dialog.dismiss();
            onSave.accept(digits[0]);
        });
        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
        }
        dialog.show();
        window = dialog.getWindow();
        if (window != null) window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
