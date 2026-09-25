package br.com.soswf.rota500.ui;

import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    private Ui() { }

    static TextView title(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(25);
        view.setTextColor(Color.rgb(16, 34, 53));
        view.setPadding(0, 10, 0, 14);
        return view;
    }

    static TextView text(Context context, String text, int size) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(32, 45, 55));
        view.setPadding(0, 7, 0, 7);
        return view;
    }

    static Button button(Context context, String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return button;
    }

    static EditText field(Context context, LinearLayout parent, String label, String value, boolean numeric) {
        parent.addView(text(context, label, 13));
        EditText field = new EditText(context);
        field.setText(value);
        field.setSingleLine(false);
        if (numeric) field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        parent.addView(field, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return field;
    }

    static double number(EditText field, double fallback) {
        try { return Double.parseDouble(field.getText().toString().trim().replace(',', '.')); }
        catch (RuntimeException ignored) { return fallback; }
    }

    static int integer(EditText field, int fallback) {
        try { return Integer.parseInt(field.getText().toString().trim()); }
        catch (RuntimeException ignored) { return fallback; }
    }
}
