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
    static final int SURFACE = Color.rgb(11, 16, 22);
    static final int CARD = Color.rgb(22, 30, 40);
    static final int CARD_ALT = Color.rgb(30, 41, 54);
    static final int NAVY = Color.rgb(244, 247, 251);
    static final int GREEN = Color.rgb(84, 224, 165);
    static final int RED = Color.rgb(255, 95, 100);
    static final int AMBER = Color.rgb(255, 202, 58);
    static final int BLUE = Color.rgb(72, 169, 255);
    static final int MUTED = Color.rgb(164, 178, 195);
    static final int BORDER = Color.rgb(42, 56, 73);

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
        view.setTextColor(NAVY);
        view.setLineSpacing(0, 1.08f);
        view.setFontFeatureSettings("tnum");
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
        card.setBackground(rounded(CARD, 18, BORDER, context));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(context, 8), 0, dp(context, 8));
        card.setLayoutParams(params);
        card.setElevation(0);
        return card;
    }

    static Button button(Context context, String text) {
        return actionButton(context, text, GREEN, SURFACE);
    }

    static Button primaryButton(Context context, String text) {
        Button button = actionButton(context, text, GREEN, SURFACE);
        button.setTextSize(19);
        button.setMinHeight(dp(context, 64));
        return button;
    }

    static Button secondaryButton(Context context, String text) {
        return actionButton(context, text, CARD_ALT, NAVY);
    }

    static Button dangerButton(Context context, String text) {
        return actionButton(context, text, RED, SURFACE);
    }

    private static Button actionButton(Context context, String text, int background, int foreground) {
        Button button = new Button(context);
        button.setText(text);
        button.setTextSize(16);
        button.setTextColor(foreground);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(context, 64));
        button.setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10));
        button.setBackground(ripple(context, background, 16));
        button.setStateListAnimator(null);
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
        field.setMinHeight(dp(context, 64));
        field.setTextColor(NAVY);
        field.setHintTextColor(MUTED);
        field.setSingleLine(!label.toLowerCase().contains("observa"));
        field.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
        field.setBackground(rounded(CARD_ALT, 10, BORDER, context));
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


    static android.graphics.drawable.Drawable ripple(Context context, int fill, int radius) {
        return new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x28FFFFFF),
                rounded(fill, radius, fill == CARD_ALT ? BORDER : 0, context),
                rounded(Color.WHITE, radius, 0, context));
    }

    static android.graphics.drawable.Drawable icon(Context context, String name, int color) {
        int id = context.getResources().getIdentifier("ic_ui_" + name, "drawable", context.getPackageName());
        android.graphics.drawable.Drawable drawable = context.getDrawable(id).mutate();
        drawable.setTint(color);
        drawable.setBounds(0, 0, dp(context, 22), dp(context, 22));
        return drawable;
    }

    static void buttonIcon(Button button, String name, int color) {
        button.setCompoundDrawablesRelative(icon(button.getContext(), name, color), null, null, null);
        button.setCompoundDrawablePadding(dp(button.getContext(), 10));
    }

    static TextView label(Context context, String value) {
        TextView label = text(context, value, 12);
        label.setTextColor(MUTED);
        label.setLetterSpacing(0.07f);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return label;
    }

    static TextView number(Context context, String value, int size) {
        TextView text = text(context, value, size);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setLetterSpacing(-0.03f);
        text.setMaxLines(1);
        text.setAutoSizeTextTypeUniformWithConfiguration(18, size, 1,
                android.util.TypedValue.COMPLEX_UNIT_SP);
        return text;
    }

    static LinearLayout column(Context context) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    static void install(android.app.Activity activity, View content) {
        activity.getWindow().setStatusBarColor(SURFACE);
        activity.getWindow().setNavigationBarColor(SURFACE);
        activity.getWindow().setDecorFitsSystemWindows(false);
        content.setBackgroundColor(SURFACE);
        int left = content.getPaddingLeft(), top = content.getPaddingTop();
        int right = content.getPaddingRight(), bottom = content.getPaddingBottom();
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
            view.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + Math.max(bars.bottom, insets.getInsets(android.view.WindowInsets.Type.ime()).bottom));
            return insets;
        });
        activity.setContentView(content);
        content.requestApplyInsets();
    }

    /** One bottom sheet, scrollable on small screens; Android Back dismisses it. */
    static android.app.Dialog sheet(Context context, String title, LinearLayout body) {
        android.app.Dialog dialog = new android.app.Dialog(context);
        LinearLayout root = column(context);
        root.setPadding(dp(context, 20), dp(context, 8), dp(context, 20), dp(context, 16));
        root.setBackground(rounded(CARD, 24, BORDER, context));
        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = text(context, title, 23);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        android.widget.ImageButton close = new android.widget.ImageButton(context);
        close.setImageDrawable(icon(context, "close", MUTED));
        close.setContentDescription("Fechar");
        close.setPadding(dp(context, 20), dp(context, 20), dp(context, 20), dp(context, 20));
        close.setBackground(ripple(context, CARD, 16));
        header.addView(close, new LinearLayout.LayoutParams(dp(context, 64), dp(context, 64)));
        close.setOnClickListener(v -> dialog.dismiss());
        root.addView(header);
        android.widget.ScrollView scroll = new android.widget.ScrollView(context) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                android.graphics.Rect visible = new android.graphics.Rect();
                getWindowVisibleDisplayFrame(visible);
                int height = visible.height() > 0 ? visible.height() : getResources().getDisplayMetrics().heightPixels;
                int max = Math.max(dp(context, 120), height - dp(context, 144));
                int limit = View.MeasureSpec.getMode(heightSpec) == View.MeasureSpec.UNSPECIFIED
                        ? max : Math.min(max, View.MeasureSpec.getSize(heightSpec));
                super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(limit, View.MeasureSpec.AT_MOST));
            }
        };
        scroll.setFillViewport(false);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(root);
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.BOTTOM);
            window.setDimAmount(0.65f);
            window.setNavigationBarColor(CARD);
        }
        dialog.show();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        }
        return dialog;
    }

    static LinearLayout pageHeader(android.app.Activity activity, String title, String subtitle) {
        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = secondaryButton(activity, "");
        back.setContentDescription("Voltar");
        back.setCompoundDrawables(icon(activity, "back", NAVY), null, null, null);
        back.setPadding(dp(activity, 20), 0, dp(activity, 20), 0);
        back.setBackground(ripple(activity, SURFACE, 16));
        header.addView(back, new LinearLayout.LayoutParams(dp(activity, 64), dp(activity, 64)));
        back.setOnClickListener(v -> activity.finish());
        LinearLayout names = column(activity);
        TextView heading = text(activity, title, 23);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        names.addView(heading);
        if (!subtitle.isBlank()) { TextView sub = text(activity, subtitle, 12); sub.setTextColor(MUTED); names.addView(sub); }
        header.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return header;
    }

    static LinearLayout disclosure(Context context, LinearLayout parent, String title, String icon, boolean expanded) {
        LinearLayout wrapper = card(context);
        wrapper.setPadding(dp(context, 14), 0, dp(context, 14), dp(context, 10));
        Button header = secondaryButton(context, title);
        header.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        header.setBackground(ripple(context, CARD, 14));
        buttonIcon(header, icon, GREEN);
        LinearLayout body = column(context);
        body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        header.setContentDescription(title + (expanded ? ", expandido" : ", recolhido"));
        header.setCompoundDrawablesRelative(icon(context, icon, GREEN), null,
                icon(context, expanded ? "close" : "plus", MUTED), null);
        header.setOnClickListener(v -> {
            boolean open = body.getVisibility() != View.VISIBLE;
            body.setVisibility(open ? View.VISIBLE : View.GONE);
            header.setContentDescription(title + (open ? ", expandido" : ", recolhido"));
            header.setCompoundDrawablesRelative(icon(context, icon, GREEN), null,
                    icon(context, open ? "close" : "plus", MUTED), null);
        });
        wrapper.addView(header);
        wrapper.addView(body);
        parent.addView(wrapper);
        return body;
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
