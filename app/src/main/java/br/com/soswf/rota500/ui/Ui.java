package br.com.soswf.rota500.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

final class Ui {
    static final int NAVY = Color.rgb(11, 31, 42);
    static final int GREEN = Color.rgb(8, 122, 82);
    static final int RED = Color.rgb(179, 38, 30);
    static final int AMBER = Color.rgb(185, 106, 0);
    static final int SURFACE = Color.rgb(243, 246, 245);
    static final int MUTED = Color.rgb(79, 95, 103);
    static final int BORDER = Color.rgb(215, 224, 221);

    private Ui() { }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static TextView title(Context context, String text) {
        TextView view = text(context, text, 28);
        view.setTextColor(NAVY);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, dp(context, 4), 0, dp(context, 10));
        return view;
    }

    static TextView sectionTitle(Context context, String text) {
        TextView view = text(context, text, 19);
        view.setTextColor(NAVY);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(0, dp(context, 20), 0, dp(context, 8));
        return view;
    }

    static TextView text(Context context, String text, int size) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(32, 45, 55));
        view.setLineSpacing(0, 1.08f);
        view.setPadding(0, dp(context, 5), 0, dp(context, 5));
        return view;
    }

    static TextView pill(Context context, String text, int color) {
        TextView view = text(context, text, 13);
        view.setTextColor(Color.WHITE);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, 14), dp(context, 7), dp(context, 14), dp(context, 7));
        view.setBackground(rounded(color, 100, 0, context));
        return view;
    }

    static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(context, 18);
        card.setPadding(padding, padding, padding, padding);
        card.setBackground(rounded(Color.WHITE, 18, BORDER, context));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(context, 8), 0, dp(context, 8));
        card.setLayoutParams(params);
        card.setElevation(dp(context, 1));
        return card;
    }

    static Button button(Context context, String text) {
        return actionButton(context, text, GREEN, Color.WHITE);
    }

    static Button primaryButton(Context context, String text) {
        Button button = actionButton(context, text, GREEN, Color.WHITE);
        button.setTextSize(19);
        button.setMinHeight(dp(context, 64));
        return button;
    }

    static Button secondaryButton(Context context, String text) {
        return actionButton(context, text, Color.WHITE, NAVY);
    }

    static Button dangerButton(Context context, String text) {
        return actionButton(context, text, RED, Color.WHITE);
    }

    private static Button actionButton(Context context, String text, int background, int foreground) {
        Button button = new Button(context);
        button.setText(text);
        button.setTextSize(16);
        button.setTextColor(foreground);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(context, 54));
        button.setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10));
        button.setBackground(rounded(background, 14, background == Color.WHITE ? BORDER : 0, context));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(context, 5), 0, dp(context, 5));
        button.setLayoutParams(params);
        return button;
    }

    static View divider(Context context) {
        View view = new View(context);
        view.setBackgroundColor(BORDER);
        view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1)));
        return view;
    }

    static EditText field(Context context, LinearLayout parent, String label, String value, boolean numeric) {
        TextView labelView = text(context, label, 14);
        labelView.setTextColor(MUTED);
        labelView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labelView.setPadding(0, dp(context, 12), 0, dp(context, 3));
        parent.addView(labelView);
        EditText field = new EditText(context);
        field.setText(value);
        field.setTextSize(17);
        field.setTextColor(NAVY);
        field.setSingleLine(!label.toLowerCase().contains("observa"));
        field.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
        field.setBackground(rounded(Color.WHITE, 10, BORDER, context));
        if (numeric) field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(context, 3));
        parent.addView(field, params);
        return field;
    }

    static GradientDrawable rounded(int fill, int radiusDp, int stroke, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radiusDp));
        if (stroke != 0) drawable.setStroke(dp(context, 1), stroke);
        return drawable;
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
