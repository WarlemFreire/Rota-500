package br.com.soswf.rota500.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import br.com.soswf.rota500.data.AppPreferences;
import br.com.soswf.rota500.diag.DiagnosticLog;
import br.com.soswf.rota500.diag.FrameProvider;
import br.com.soswf.rota500.update.UpdateManager;

/**
 * Mostra onde a leitura de ofertas falha: se a captura chega preta, o que o OCR leu e por que
 * cada leitura foi descartada. Não muda nenhuma decisão de oferta.
 */
public final class DiagnosticsActivity extends Activity {
    private DiagnosticLog log;
    private AppPreferences preferences;
    private TextView status, verdict, counters, events, frameInfo;
    private ImageView frame;
    private Button toggle;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, 3_000L); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        log = new DiagnosticLog(this);
        preferences = new AppPreferences(this);
        LinearLayout screen = Ui.column(this);
        screen.addView(Ui.pageHeader(this, "Diagnóstico da leitura", "ONDE A LEITURA FALHA"));
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = Ui.column(this);
        root.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), Ui.dp(this, 16));
        scroll.addView(root);

        TextView how = Ui.text(this, "1. Ligue o diagnóstico. 2. Ligue a leitura e abra a Uber ou a 99. "
                + "3. Espere algumas ofertas. 4. Volte aqui e compartilhe o relatório.", 14);
        how.setTextColor(Ui.MUTED);
        root.addView(how);

        LinearLayout card = Ui.card(this);
        status = Ui.text(this, "", 14);
        card.addView(status);
        verdict = Ui.text(this, "", 17);
        verdict.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        card.addView(verdict);
        counters = Ui.text(this, "", 14);
        counters.setTextColor(Ui.MUTED);
        card.addView(counters);
        root.addView(card);

        toggle = Ui.primaryButton(this, "");
        toggle.setOnClickListener(v -> { log.setEnabled(!log.enabled()); refresh(); });
        root.addView(toggle);
        Button capture = Ui.secondaryButton(this, "Salvar o próximo quadro lido");
        Ui.buttonIcon(capture, "scan", Ui.GREEN);
        capture.setOnClickListener(v -> {
            if (!log.enabled()) { log.setEnabled(true); }
            log.requestFrame();
            toast("Abra a Uber ou a 99 com uma oferta na tela e volte aqui.");
            refresh();
        });
        root.addView(capture);
        Button share = Ui.secondaryButton(this, "Compartilhar relatório");
        Ui.buttonIcon(share, "backup", Ui.GREEN);
        share.setOnClickListener(v -> share());
        root.addView(share);

        root.addView(Ui.sectionTitle(this, "Último quadro lido"));
        frameInfo = Ui.text(this, "", 13);
        frameInfo.setTextColor(Ui.MUTED);
        root.addView(frameInfo);
        frame = new ImageView(this);
        frame.setAdjustViewBounds(true);
        frame.setBackground(Ui.rounded(Ui.CARD, 12, Ui.BORDER, this));
        root.addView(frame, new LinearLayout.LayoutParams(-1, -2));
        Button shareFrame = Ui.secondaryButton(this, "Compartilhar quadro");
        shareFrame.setOnClickListener(v -> shareFrame());
        root.addView(shareFrame);

        root.addView(Ui.sectionTitle(this, "Últimos eventos"));
        events = Ui.text(this, "", 13);
        root.addView(events);
        Button clear = Ui.secondaryButton(this, "Apagar diagnóstico");
        clear.setTextColor(Ui.RED);
        clear.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Apagar o diagnóstico?")
                .setMessage("Contadores, eventos e o quadro salvo serão apagados.")
                .setPositiveButton("Apagar", (d, w) -> { log.clear(); refresh(); })
                .setNegativeButton("Cancelar", null).show());
        root.addView(clear);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        Ui.install(this, screen);
    }

    @Override protected void onResume() { super.onResume(); handler.post(tick); }
    @Override protected void onPause() { handler.removeCallbacks(tick); super.onPause(); }

    private void refresh() {
        boolean on = log.enabled();
        boolean reading = preferences.captureActive();
        toggle.setText(on ? "Desligar diagnóstico" : "Ligar diagnóstico");
        status.setText((on ? "Diagnóstico ligado" : "Diagnóstico desligado") + " · "
                + (reading ? "leitura da tela ligada" : "leitura da tela DESLIGADA")
                + " · aviso flutuante " + (Settings.canDrawOverlays(this) ? "permitido" : "NÃO permitido"));
        status.setTextColor(on && reading ? Ui.GREEN : Ui.AMBER);
        long frames = log.frames(), black = log.blackFrames();
        StringBuilder text = new StringBuilder(String.format(Locale.forLanguageTag("pt-BR"),
                "Quadros analisados: %d · pretos: %d (%s)", frames, black,
                frames == 0 ? "—" : Math.round(black * 100.0 / frames) + "%"));
        if (log.lastFrameAt() > 0) text.append(" · último lote ").append(time(log.lastFrameAt()));
        for (DiagnosticLog.Stage stage : DiagnosticLog.Stage.values()) {
            text.append('\n').append(stage.label).append(": ").append(log.count(stage));
        }
        counters.setText(text);
        verdict.setText(verdict(frames, black, reading, on));
        StringBuilder list = new StringBuilder();
        for (DiagnosticLog.Event event : log.recent(25)) {
            list.append(time(event.at())).append(" · ").append(event.stage().label);
            if (!event.details().isBlank()) list.append("\n   ").append(event.details());
            if (!event.text().isBlank()) list.append("\n   texto: ").append(event.text());
            list.append("\n\n");
        }
        events.setText(list.length() == 0 ? "Nenhum evento ainda." : list.toString().trim());
        if (log.frameAt() > 0 && log.frameFile().exists()) {
            Bitmap bitmap = BitmapFactory.decodeFile(log.frameFile().getPath());
            frame.setImageBitmap(bitmap);
            frame.setVisibility(View.VISIBLE);
            frameInfo.setText("Recorte enviado ao OCR às " + time(log.frameAt())
                    + ". Se estiver preto, a Uber ou a 99 bloqueiam a captura.");
        } else {
            frame.setVisibility(View.GONE);
            frameInfo.setText(log.frameRequested() ? "Aguardando o próximo quadro lido…" : "Nenhum quadro salvo.");
        }
    }

    /** Uma conclusão simples a partir dos contadores, para o motorista não precisar interpretar. */
    private String verdict(long frames, long black, boolean reading, boolean on) {
        if (!on) return "Ligue o diagnóstico para começar.";
        if (!reading) return "Ligue a leitura da tela (Iniciar jornada > Ligar leitura).";
        if (frames == 0) return "Nenhum quadro chegou ainda. Se continuar assim, a captura não está funcionando.";
        if (black * 100 >= frames * 90) return "A captura chega preta. Se a tela estava ligada com a Uber/99 aberta, elas bloqueiam a leitura da tela.";
        long decided = log.count(DiagnosticLog.Stage.DECIDED);
        long discarded = log.count(DiagnosticLog.Stage.LOW_CONFIDENCE) + log.count(DiagnosticLog.Stage.NO_FARE);
        if (decided == 0 && discarded > 0) return "Ofertas são lidas, mas descartadas por leitura incompleta. Compartilhe o relatório.";
        if (decided == 0 && log.count(DiagnosticLog.Stage.NOT_OFFER) > 0) return "A tela é lida, mas nada foi reconhecido como oferta ainda.";
        if (decided > 0) return decided + " oferta(s) com decisão mostrada.";
        return "Aguardando uma oferta na tela.";
    }

    private void share() {
        String version = new UpdateManager(this).installedName() + " (build " + new UpdateManager(this).installedCode() + ")";
        String device = Build.MANUFACTURER + " " + Build.MODEL + ", Android " + Build.VERSION.RELEASE;
        Intent intent = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Diagnóstico da leitura do Rota 500")
                .putExtra(Intent.EXTRA_TEXT, log.report(version, device));
        startActivity(Intent.createChooser(intent, "Compartilhar relatório"));
    }

    private void shareFrame() {
        if (!log.frameFile().exists()) { toast("Nenhum quadro salvo ainda."); return; }
        Intent intent = new Intent(Intent.ACTION_SEND).setType("image/jpeg")
                .putExtra(Intent.EXTRA_STREAM, FrameProvider.FRAME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Compartilhar quadro"));
    }

    private String time(long at) {
        return new SimpleDateFormat("HH:mm:ss", Locale.forLanguageTag("pt-BR")).format(new Date(at));
    }

    private void toast(String text) { android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_LONG).show(); }
}
