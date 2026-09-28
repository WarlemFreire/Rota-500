package br.com.soswf.rota500.capture;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.view.WindowManager;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import br.com.soswf.rota500.R;
import br.com.soswf.rota500.accessibility.DynamicHeatAnalyzer;
import br.com.soswf.rota500.core.DecisionPresentation;
import br.com.soswf.rota500.core.DecisionRules;
import br.com.soswf.rota500.core.OcrCadence;
import br.com.soswf.rota500.core.OfferData;
import br.com.soswf.rota500.core.OfferRuleEngine;
import br.com.soswf.rota500.core.OfferTextParser;
import br.com.soswf.rota500.core.RuleDecision;
import br.com.soswf.rota500.data.AppDatabase;
import br.com.soswf.rota500.data.AppPreferences;
import br.com.soswf.rota500.ui.MainActivity;

public final class ScreenCaptureService extends Service {
    public static final String ACTION_START = "br.com.soswf.rota500.capture.START";
    public static final String ACTION_STOP = "br.com.soswf.rota500.capture.STOP";
    public static final String ACTION_STATE = "br.com.soswf.rota500.capture.STATE";
    public static final String ACTION_DECISION = "br.com.soswf.rota500.capture.DECISION";
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    private static final String CHANNEL = "rota500_screen_reader_v2";
    private static final int NOTIFICATION_ID = 501;
    // Only a small sample is read from most frames. OCR is reserved for a new screen.
    private static final int CAPTURE_MAX_WIDTH = 720;
    private static final long FRAME_CHECK_INTERVAL_MS = 120L;
    private static final long SAME_OFFER_COOLDOWN_MS = 8_000L;
    private static final int SAMPLE_COLUMNS = 20;
    private static final int SAMPLE_ROWS = 24;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final OfferTextParser parser = new OfferTextParser();
    private final OfferRuleEngine ruleEngine = new OfferRuleEngine();
    private OcrCadence cadence = new OcrCadence();
    private AppDatabase database;
    private AppPreferences preferences;
    private TextRecognizer recognizer;
    private DecisionAlert decisionAlert;
    private HandlerThread captureThread;
    private Handler captureHandler;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private boolean processing;
    private long lastFrameCheckAt;
    private long lastOfferAt;
    private long lastOfferSeenAt;
    private String lastOfferHash = "";
    private final int[] lastScreenSample = new int[SAMPLE_COLUMNS * SAMPLE_ROWS];
    private final int[] currentScreenSample = new int[SAMPLE_COLUMNS * SAMPLE_ROWS];
    private boolean hasScreenSample;

    @Override public void onCreate() {
        super.onCreate();
        database = AppDatabase.get(this);
        preferences = new AppPreferences(this);
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        decisionAlert = new DecisionAlert(this, mainHandler);
        captureThread = new HandlerThread("rota500-screen-capture");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            stopCapture();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(intent.getAction()) || database.activeSessionId() <= 0) {
            stopCapture();
            return START_NOT_STICKY;
        }
        startForeground(NOTIFICATION_ID, notification("Lendo ofertas no próprio celular"));
        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
        Intent resultData = Build.VERSION.SDK_INT >= 33
                ? intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class)
                : intent.getParcelableExtra(EXTRA_RESULT_DATA);
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            stopCapture();
            return START_NOT_STICKY;
        }
        try {
            beginProjection(resultCode, resultData);
            preferences.setCaptureActive(true);
            broadcast(ACTION_STATE);
            return START_STICKY;
        } catch (RuntimeException error) {
            preferences.setCaptureActive(false);
            broadcast(ACTION_STATE);
            stopCapture();
            return START_NOT_STICKY;
        }
    }

    private void beginProjection(int resultCode, Intent resultData) {
        releaseProjection();
        cadence = new OcrCadence();
        hasScreenSample = false;
        lastFrameCheckAt = 0;
        MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
        projection = manager.getMediaProjection(resultCode, resultData);
        if (projection == null) throw new IllegalStateException("Captura não autorizada");
        projection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() {
                preferences.setCaptureActive(false);
                broadcast(ACTION_STATE);
                releaseProjection();
                stopSelf();
            }
        }, mainHandler);

        WindowManager windowManager = getSystemService(WindowManager.class);
        Rect bounds = windowManager.getMaximumWindowMetrics().getBounds();
        int nativeWidth = Math.max(1, bounds.width());
        int nativeHeight = Math.max(1, bounds.height());
        float scale = nativeWidth > CAPTURE_MAX_WIDTH ? CAPTURE_MAX_WIDTH / (float) nativeWidth : 1f;
        int width = Math.max(1, Math.round(nativeWidth * scale));
        int height = Math.max(1, Math.round(nativeHeight * scale));
        int density = Math.max(72, Math.round(getResources().getConfiguration().densityDpi * scale));
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        imageReader.setOnImageAvailableListener(this::onImageAvailable, captureHandler);
        virtualDisplay = projection.createVirtualDisplay(
                "Rota500ScreenReader", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, captureHandler);
    }

    private void onImageAvailable(ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null) return;
            long now = System.currentTimeMillis();
            if (processing || now - lastFrameCheckAt < FRAME_CHECK_INTERVAL_MS) return;
            lastFrameCheckAt = now;
            int changedPoints = screenChangePoints(image);
            rememberCurrentScreenSample();
            boolean dynamicPending = preferences.dynamicCapturePending();
            if (!cadence.shouldRead(now, changedPoints, dynamicPending)) return;
            if (database.activeSessionId() <= 0) {
                mainHandler.post(this::stopCapture);
                return;
            }
            processing = true;
            Bitmap bitmap = bitmapFrom(image);
            if (bitmap == null) {
                processing = false;
                return;
            }
            processBitmap(bitmap);
        } catch (RuntimeException ignored) {
            processing = false;
        } finally {
            if (image != null) image.close();
        }
    }

    private int screenChangePoints(Image image) {
        if (image.getPlanes().length == 0) return SAMPLE_COLUMNS * SAMPLE_ROWS;
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int width = image.getWidth();
        int height = image.getHeight();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int startY = height * 28 / 100;
        int usableHeight = Math.max(1, height - startY - 1);
        int changedPoints = 0;
        int index = 0;
        try {
            for (int row = 0; row < SAMPLE_ROWS; row++) {
                int y = startY + (row * usableHeight / Math.max(1, SAMPLE_ROWS - 1));
                for (int column = 0; column < SAMPLE_COLUMNS; column++) {
                    int x = column * Math.max(1, width - 1) / Math.max(1, SAMPLE_COLUMNS - 1);
                    int offset = y * rowStride + x * pixelStride;
                    int red = buffer.get(offset) & 0xff;
                    int green = buffer.get(offset + 1) & 0xff;
                    int blue = buffer.get(offset + 2) & 0xff;
                    int color = (red << 16) | (green << 8) | blue;
                    currentScreenSample[index] = color;
                    if (hasScreenSample && colorDistance(color, lastScreenSample[index]) >= 70) {
                        changedPoints++;
                    }
                    index++;
                }
            }
        } catch (RuntimeException ignored) {
            return SAMPLE_COLUMNS * SAMPLE_ROWS;
        }
        return hasScreenSample ? changedPoints : SAMPLE_COLUMNS * SAMPLE_ROWS;
    }

    private int colorDistance(int first, int second) {
        int red = Math.abs(((first >> 16) & 0xff) - ((second >> 16) & 0xff));
        int green = Math.abs(((first >> 8) & 0xff) - ((second >> 8) & 0xff));
        int blue = Math.abs((first & 0xff) - (second & 0xff));
        return red + green + blue;
    }

    private void rememberCurrentScreenSample() {
        System.arraycopy(currentScreenSample, 0, lastScreenSample, 0, currentScreenSample.length);
        hasScreenSample = true;
    }

    private Bitmap bitmapFrom(Image image) {
        if (image.getPlanes().length == 0) return null;
        Image.Plane plane = image.getPlanes()[0];
        ByteBuffer buffer = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int width = image.getWidth();
        int height = image.getHeight();
        int rowPadding = Math.max(0, rowStride - pixelStride * width);
        Bitmap padded = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
        padded.copyPixelsFromBuffer(buffer);
        if (padded.getWidth() == width) return padded;
        Bitmap cropped = Bitmap.createBitmap(padded, 0, 0, width, height);
        padded.recycle();
        return cropped;
    }

    private void processBitmap(Bitmap bitmap) {
        if (preferences.dynamicCapturePending()) {
            preferences.clearDynamicCaptureRequest();
            DynamicHeatAnalyzer.Result result = new DynamicHeatAnalyzer().analyze(bitmap);
            database.insertDynamicObservation("Experimental: " + result.level, result.warmRatio);
        }
        Bitmap ocrBitmap = prepareForOcr(bitmap);
        if (ocrBitmap != bitmap) bitmap.recycle();
        try {
            InputImage input = InputImage.fromBitmap(ocrBitmap, 0);
            java.util.concurrent.Executor captureExecutor = runnable -> captureHandler.post(runnable);
            recognizer.process(input)
                    .addOnSuccessListener(captureExecutor, this::handleRecognizedText)
                    .addOnCompleteListener(captureExecutor, task -> {
                        ocrBitmap.recycle();
                        processing = false;
                    });
        } catch (RuntimeException error) {
            ocrBitmap.recycle();
            processing = false;
        }
    }

    private Bitmap prepareForOcr(Bitmap source) {
        try {
            // As ofertas da Uber e da 99 ocupam a parte central/inferior da tela.
            // Remover a faixa superior e limitar a largura reduz bastante o tempo do OCR.
            int top = Math.max(0, source.getHeight() / 5);
            Bitmap cropped = Bitmap.createBitmap(
                    source, 0, top, source.getWidth(), source.getHeight() - top);
            if (cropped.getWidth() <= 720) return cropped;
            int scaledHeight = Math.max(1, Math.round(cropped.getHeight() * 720f / cropped.getWidth()));
            Bitmap scaled = Bitmap.createScaledBitmap(cropped, 720, scaledHeight, true);
            if (scaled != cropped) cropped.recycle();
            return scaled;
        } catch (RuntimeException ignored) {
            return source;
        }
    }

    private void handleRecognizedText(Text result) {
        List<String> lines = new ArrayList<>();
        for (Text.TextBlock block : result.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                if (!line.getText().isBlank()) lines.add(line.getText());
            }
        }
        long now = System.currentTimeMillis();
        if (!looksLikeOffer(lines)) {
            // Ao sair uma oferta, libera até uma oferta seguinte com valores iguais.
            if (lastOfferSeenAt > 0 && now - lastOfferSeenAt >= 500L) lastOfferHash = "";
            return;
        }
        lastOfferSeenAt = now;
        String joined = String.join(" ", lines);
        OfferData offer = parser.parse(platformFor(joined), lines);
        DecisionRules rules = preferences.decisionRules();
        boolean unsafe = database.isDestinationUnsafe(offer.destination);
        RuleDecision decision = ruleEngine.evaluate(offer, rules, unsafe);
        // Uma leitura parcial não deve virar uma orientação vaga. O quadro seguinte
        // será analisado imediatamente e só uma decisão binária será mostrada.
        if (decision.outcome == RuleDecision.Outcome.REVIEW) return;
        if (offer.fareCents <= 0) return;
        // Ofertas diferentes não têm espera entre si. Uma nova decisão substitui
        // o aviso anterior imediatamente, mesmo quando as corridas vêm em sequência.
        if (offer.sourceHash.equals(lastOfferHash)
                && now - lastOfferAt < SAME_OFFER_COOLDOWN_MS) return;
        cadence.offerSeen(now);
        lastOfferHash = offer.sourceHash;
        lastOfferAt = now;
        // O banco evita duplicata, mas uma oferta igual após desaparecer da tela
        // ainda precisa voltar a ser mostrada ao motorista.
        database.insertOffer(offer, decision, unsafe);
        publishDecision(offer, decision, rules);
    }

    private void publishDecision(OfferData offer, RuleDecision decision, DecisionRules rules) {
        DecisionPresentation presentation = DecisionPresentation.from(offer, decision, rules);
        preferences.setLastDecision(
                presentation.finalAdvice, presentation.compactDetails(), presentation.tone);
        mainHandler.post(() -> decisionAlert.show(presentation));
        broadcast(ACTION_DECISION);
    }

    private boolean looksLikeOffer(List<String> lines) {
        String text = String.join(" ", lines).toLowerCase(Locale.forLanguageTag("pt-BR"));
        boolean money = text.contains("r$");
        boolean distance = text.contains(" km") || text.contains("quilômetr");
        boolean time = text.contains(" min") || text.contains("minuto");
        boolean action = text.contains("aceitar") || text.contains("recusar")
                || text.contains("rejeitar") || text.contains("solicitação")
                || text.contains("solicitacao") || text.contains("nova corrida")
                || text.contains("nova viagem") || text.contains("selecionar");
        return money && distance && time && action;
    }

    private String platformFor(String text) {
        String value = text.toLowerCase(Locale.ROOT);
        if (value.contains("uber")) return "UBER";
        if (value.contains("99pop") || value.contains("99 motorista") || value.contains("99 moto")) return "99";
        return "APP";
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL, "Leitura das ofertas", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Mantém a leitura local da tela durante a jornada.");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification notification(String text) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop = new Intent(this, ScreenCaptureService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 3, stop,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Rota 500: leitura ligada")
                .setContentText(text)
                .setContentIntent(pending)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Parar leitura", stopPending).build())
                .build();
    }

    private void broadcast(String action) {
        sendBroadcast(new Intent(action).setPackage(getPackageName()));
    }

    private void stopCapture() {
        preferences.setCaptureActive(false);
        broadcast(ACTION_STATE);
        decisionAlert.hide();
        releaseProjection();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void releaseProjection() {
        if (imageReader != null) {
            imageReader.setOnImageAvailableListener(null, null);
            imageReader.close();
            imageReader = null;
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        MediaProjection current = projection;
        projection = null;
        if (current != null) {
            try { current.stop(); }
            catch (RuntimeException ignored) { }
        }
    }

    @Override public void onDestroy() {
        preferences.setCaptureActive(false);
        decisionAlert.hide();
        releaseProjection();
        if (recognizer != null) recognizer.close();
        if (captureThread != null) captureThread.quitSafely();
        broadcast(ACTION_STATE);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
