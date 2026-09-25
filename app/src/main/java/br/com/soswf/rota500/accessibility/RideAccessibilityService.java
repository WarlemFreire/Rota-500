package br.com.soswf.rota500.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import br.com.soswf.rota500.core.OfferData;
import br.com.soswf.rota500.core.OfferRuleEngine;
import br.com.soswf.rota500.core.OfferTextParser;
import br.com.soswf.rota500.core.RuleDecision;
import br.com.soswf.rota500.data.AppDatabase;
import br.com.soswf.rota500.data.AppPreferences;

public final class RideAccessibilityService extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final OfferTextParser parser = new OfferTextParser();
    private final OfferRuleEngine rulesEngine = new OfferRuleEngine();
    private AppDatabase database;
    private AppPreferences preferences;
    private WindowManager windowManager;
    private TextView overlay;
    private String lastVisibleHash = "";
    private long lastHandledAt;

    @Override public void onServiceConnected() {
        database = AppDatabase.get(this);
        preferences = new AppPreferences(this);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (database == null || database.activeSessionId() <= 0 || event == null) {
            hideOverlay();
            return;
        }
        CharSequence packageName = event.getPackageName();
        String platform = platformFor(packageName == null ? "" : packageName.toString());
        if (platform.isEmpty()) {
            hideOverlay();
            return;
        }
        if (preferences.dynamicCapturePending()) {
            preferences.clearDynamicCaptureRequest();
            captureDynamicMap();
        }
        long now = System.currentTimeMillis();
        if (now - lastHandledAt < 450) return;
        lastHandledAt = now;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        List<String> texts = visibleTexts(root);
        if (!looksLikeOffer(texts)) return;
        OfferData offer = parser.parse(platform, texts);
        if (offer.fareCents <= 0 || offer.sourceHash.equals(lastVisibleHash)) return;
        lastVisibleHash = offer.sourceHash;
        boolean unsafe = database.isDestinationUnsafe(offer.destination);
        RuleDecision decision = rulesEngine.evaluate(offer, preferences.decisionRules(), unsafe);
        boolean inserted = database.insertOffer(offer, decision, unsafe);
        if (!inserted) return;
        showDecision(offer, decision);
        if (decision.outcome == RuleDecision.Outcome.AUTO_REJECT
                && preferences.autoRejectEnabled() && preferences.calibrationConfirmed()) {
            scheduleReject(offer.sourceHash);
        }
    }

    @Override public void onInterrupt() { hideOverlay(); }
    @Override public void onDestroy() { hideOverlay(); handler.removeCallbacksAndMessages(null); super.onDestroy(); }

    private void scheduleReject(String expectedHash) {
        handler.postDelayed(() -> {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;
            String packageName = root.getPackageName() == null ? "" : root.getPackageName().toString();
            String platform = platformFor(packageName);
            if (platform.isEmpty()) return;
            OfferData stillVisible = parser.parse(platform, visibleTexts(root));
            if (!expectedHash.equals(stillVisible.sourceHash)) return;
            AccessibilityNodeInfo reject = findRejectButton(root);
            if (reject != null && reject.isVisibleToUser() && reject.isClickable()) {
                reject.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        }, preferences.rejectDelayMillis());
    }

    private AccessibilityNodeInfo findRejectButton(AccessibilityNodeInfo root) {
        Deque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.removeFirst();
            String label = ((node.getText() == null ? "" : node.getText().toString()) + " "
                    + (node.getContentDescription() == null ? "" : node.getContentDescription().toString()))
                    .toLowerCase(Locale.forLanguageTag("pt-BR"));
            if (label.contains("recusar") || label.contains("rejeitar") || label.contains("não aceitar") || label.contains("nao aceitar")) {
                AccessibilityNodeInfo clickable = node;
                while (clickable != null && !clickable.isClickable()) clickable = clickable.getParent();
                if (clickable != null) return clickable;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.add(child);
            }
        }
        return null;
    }

    private List<String> visibleTexts(AccessibilityNodeInfo root) {
        List<String> texts = new ArrayList<>();
        Deque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty() && texts.size() < 180) {
            AccessibilityNodeInfo node = queue.removeFirst();
            if (node.isVisibleToUser()) {
                addText(texts, node.getText());
                addText(texts, node.getContentDescription());
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.add(child);
            }
        }
        return texts;
    }

    private void addText(List<String> texts, CharSequence value) {
        if (value != null && !value.toString().isBlank()) texts.add(value.toString());
    }

    private boolean looksLikeOffer(List<String> texts) {
        String joined = String.join(" ", texts).toLowerCase(Locale.forLanguageTag("pt-BR"));
        boolean money = joined.contains("r$");
        boolean routeData = joined.contains(" km") && joined.contains(" min");
        boolean action = joined.contains("aceitar") || joined.contains("recusar")
                || joined.contains("rejeitar") || joined.contains("solicitação")
                || joined.contains("solicitacao") || joined.contains("nova corrida");
        return money && routeData && action;
    }

    private String platformFor(String packageName) {
        String value = packageName.toLowerCase(Locale.ROOT);
        if (value.contains("uber")) return "UBER";
        if (value.contains("taxis99") || value.contains("99driver") || value.contains("didi")) return "99";
        return "";
    }

    private void showDecision(OfferData offer, RuleDecision decision) {
        if (overlay == null) {
            overlay = new TextView(this);
            overlay.setTextColor(Color.WHITE);
            overlay.setTextSize(15);
            overlay.setPadding(22, 14, 22, 14);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            params.y = 80;
            windowManager.addView(overlay, params);
        }
        int color = switch (decision.outcome) {
            case ACCEPTABLE -> Color.rgb(24, 112, 64);
            case AUTO_REJECT -> Color.rgb(160, 32, 32);
            case REVIEW -> Color.rgb(163, 100, 0);
        };
        overlay.setBackgroundColor(color);
        String prefix = decision.outcome == RuleDecision.Outcome.ACCEPTABLE ? "DENTRO DA RÉGUA — aceite manual" :
                decision.outcome == RuleDecision.Outcome.AUTO_REJECT ? "RECUSAR" : "REVISAR LEITURA";
        overlay.setText(prefix + "\n" + offer.compactSummary() + "\n" + decision.reason
                + "\nRisco do trajeto ainda não verificado");
        handler.postDelayed(this::hideOverlay, 8_000L);
    }

    private void hideOverlay() {
        if (overlay != null && windowManager != null) {
            try { windowManager.removeView(overlay); } catch (RuntimeException ignored) { }
            overlay = null;
        }
    }

    private void captureDynamicMap() {
        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            @Override public void onSuccess(ScreenshotResult result) {
                HardwareBuffer buffer = result.getHardwareBuffer();
                Bitmap hardware = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
                if (hardware != null) {
                    Bitmap software = hardware.copy(Bitmap.Config.ARGB_8888, false);
                    DynamicHeatAnalyzer.Result observation = new DynamicHeatAnalyzer().analyze(software);
                    database.insertDynamicObservation("Experimental: " + observation.level, observation.warmRatio);
                    software.recycle();
                }
                buffer.close();
            }
            @Override public void onFailure(int errorCode) {
                database.insertDynamicObservation("Falha na captura: " + errorCode, 0);
            }
        });
    }
}
