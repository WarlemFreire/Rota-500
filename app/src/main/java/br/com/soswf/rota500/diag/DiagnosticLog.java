package br.com.soswf.rota500.diag;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Registro do modo diagnóstico da leitura de ofertas. Fica só no celular (pasta interna do
 * app) até o motorista compartilhar. Guarda contadores por etapa e os últimos eventos, com o
 * texto que o OCR leu, para descobrir onde a leitura falha.
 */
public final class DiagnosticLog {
    public enum Stage {
        FRAME_BLACK("Quadro preto (captura bloqueada ou tela apagada)"),
        OCR_EMPTY("OCR não leu nenhum texto"),
        NOT_OFFER("Texto lido não parece oferta"),
        LOW_CONFIDENCE("Oferta descartada por leitura incompleta"),
        NO_FARE("Oferta sem valor reconhecido"),
        SAME_OFFER("Mesma oferta repetida (ignorada)"),
        DECIDED("Decisão mostrada");

        public final String label;
        Stage(String label) { this.label = label; }
    }

    private static final String PREFS = "rota500_diagnostic";
    private static final String FILE = "diagnostico.txt";
    private static final String FRAME = "diagnostico-quadro.jpg";
    private static final int KEEP_EVENTS = 300;
    private static final Object LOCK = new Object();
    private final Context context;
    private final SharedPreferences prefs;

    public DiagnosticLog(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean enabled() { return prefs.getBoolean("enabled", false); }

    public void setEnabled(boolean value) {
        prefs.edit().putBoolean("enabled", value).putLong("since", value ? System.currentTimeMillis() : 0).apply();
    }

    public long since() { return prefs.getLong("since", 0); }

    /** Pede para salvar o próximo quadro enviado ao OCR. */
    public void requestFrame() { prefs.edit().putBoolean("frame_requested", true).apply(); }
    public boolean frameRequested() { return prefs.getBoolean("frame_requested", false); }

    /** O serviço soma em memória e grava em lote, para não escrever a cada quadro. */
    public void addFrames(long total, long black) {
        if (!enabled() || total <= 0) return;
        prefs.edit().putLong("frames", prefs.getLong("frames", 0) + total)
                .putLong("black_frames", prefs.getLong("black_frames", 0) + black)
                .putLong("last_frame_at", System.currentTimeMillis()).apply();
    }

    public long lastFrameAt() { return prefs.getLong("last_frame_at", 0); }

    public long frames() { return prefs.getLong("frames", 0); }
    public long blackFrames() { return prefs.getLong("black_frames", 0); }
    public long count(Stage stage) { return prefs.getLong("stage_" + stage.name(), 0); }

    /**
     * Registra um resultado de OCR. Eventos iguais ao anterior (mesma etapa e mesmo texto) só
     * somam no contador, para o arquivo não encher com a mesma tela parada.
     */
    public void record(Stage stage, String details, List<String> lines) {
        if (!enabled()) return;
        String text = lines == null ? "" : String.join(" | ", lines);
        if (text.length() > 900) text = text.substring(0, 900) + "…";
        String signature = stage.name() + "|" + details + "|" + text;
        SharedPreferences.Editor editor = prefs.edit()
                .putLong("stage_" + stage.name(), count(stage) + 1)
                .putLong("last_at", System.currentTimeMillis());
        boolean repeated = signature.hashCode() == prefs.getInt("last_signature", 0);
        editor.putInt("last_signature", signature.hashCode()).apply();
        if (repeated) return;
        String line = System.currentTimeMillis() + "\t" + stage.name() + "\t" + clean(details) + "\t" + clean(text) + "\n";
        synchronized (LOCK) {
            try {
                File file = new File(context.getFilesDir(), FILE);
                try (FileOutputStream output = new FileOutputStream(file, true)) {
                    output.write(line.getBytes(StandardCharsets.UTF_8));
                }
                if (file.length() > 400_000) trim(file);
            } catch (Exception ignored) { }
        }
    }

    public void saveFrame(Bitmap bitmap) {
        prefs.edit().putBoolean("frame_requested", false).apply();
        try (FileOutputStream output = new FileOutputStream(frameFile())) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, output);
            prefs.edit().putLong("frame_at", System.currentTimeMillis()).apply();
        } catch (Exception ignored) { }
    }

    public File frameFile() { return new File(context.getFilesDir(), FRAME); }
    public long frameAt() { return prefs.getLong("frame_at", 0); }

    public record Event(long at, Stage stage, String details, String text) { }

    /** Últimos eventos, do mais novo para o mais antigo. */
    public List<Event> recent(int limit) {
        List<Event> events = new ArrayList<>();
        synchronized (LOCK) {
            try {
                File file = new File(context.getFilesDir(), FILE);
                if (!file.exists()) return events;
                List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
                for (int i = lines.size() - 1; i >= 0 && events.size() < limit; i--) {
                    String[] parts = lines.get(i).split("\t", 4);
                    if (parts.length < 4) continue;
                    try {
                        events.add(new Event(Long.parseLong(parts[0]), Stage.valueOf(parts[1]), parts[2], parts[3]));
                    } catch (RuntimeException ignored) { }
                }
            } catch (Exception ignored) { }
        }
        return events;
    }

    public void clear() {
        synchronized (LOCK) {
            new File(context.getFilesDir(), FILE).delete();
            frameFile().delete();
        }
        boolean on = enabled();
        prefs.edit().clear().putBoolean("enabled", on).putLong("since", on ? System.currentTimeMillis() : 0).apply();
    }

    /** Relatório em texto para o motorista compartilhar. */
    public String report(String appVersion, String device) {
        SimpleDateFormat time = new SimpleDateFormat("dd/MM HH:mm:ss", Locale.forLanguageTag("pt-BR"));
        StringBuilder out = new StringBuilder("Rota 500 · diagnóstico da leitura\n");
        out.append("Versão ").append(appVersion).append(" · ").append(device).append('\n');
        if (since() > 0) out.append("Ligado desde ").append(time.format(new Date(since()))).append('\n');
        out.append("Quadros analisados: ").append(frames()).append(" (pretos: ").append(blackFrames()).append(")\n");
        for (Stage stage : Stage.values()) out.append(stage.label).append(": ").append(count(stage)).append('\n');
        out.append("\nÚltimos eventos:\n");
        for (Event event : recent(60)) {
            out.append(time.format(new Date(event.at()))).append(" · ").append(event.stage().label);
            if (!event.details().isBlank()) out.append(" · ").append(event.details());
            out.append('\n');
            if (!event.text().isBlank()) out.append("   texto: ").append(event.text()).append('\n');
        }
        return out.toString();
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static void trim(File file) throws Exception {
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        if (lines.size() <= KEEP_EVENTS) return;
        Files.write(file.toPath(), lines.subList(lines.size() - KEEP_EVENTS, lines.size()), StandardCharsets.UTF_8);
    }
}
