package br.com.soswf.rota500.update;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import br.com.soswf.rota500.backup.BackupManager;
import br.com.soswf.rota500.core.UpdatePicker;
import br.com.soswf.rota500.data.AppPreferences;

/**
 * Botão "Buscar atualizações" (Q19-Q21): consulta as Releases públicas do repositório,
 * baixa o APK e abre o instalador do Android. Não roda sozinho (Q23).
 */
public final class UpdateManager {
    public static final String RELEASES_URL = "https://api.github.com/repos/WarlemFreire/Rota-500/releases?per_page=30";
    /** Resultado da instalação entregue de volta à MainActivity. */
    public static final String ACTION_INSTALL_STATUS = "br.com.soswf.rota500.INSTALL_STATUS";
    private static final long MAX_APK_BYTES = 150L * 1024 * 1024;

    public enum State { UP_TO_DATE, AVAILABLE, NO_ACCESS, ERROR }

    public record Check(State state, UpdatePicker.Candidate candidate, String installedName,
            long installedCode, String message) { }

    public interface CheckCallback { void onResult(Check check); }
    public interface InstallCallback { void onProgress(String message); void onError(String message); }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());

    public UpdateManager(Context context) { this.context = context.getApplicationContext(); }

    public long installedCode() {
        try { return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).getLongVersionCode(); }
        catch (Exception error) { return 0; }
    }

    public String installedName() {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName == null ? "?" : info.versionName;
        } catch (Exception error) { return "?"; }
    }

    public void check(boolean testChannel, CheckCallback callback) {
        long installed = installedCode();
        String name = installedName();
        new Thread(() -> {
            Check result;
            try {
                HttpURLConnection connection = (HttpURLConnection) URI.create(RELEASES_URL).toURL().openConnection();
                connection.setConnectTimeout(12_000);
                connection.setReadTimeout(20_000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "Rota500-app");
                int code = connection.getResponseCode();
                if (code == 404 || code == 401) {
                    result = new Check(State.NO_ACCESS, null, name, installed,
                            "Sem acesso às versões. O repositório ainda é privado.");
                } else if (code == 403 || code == 429) {
                    result = new Check(State.ERROR, null, name, installed,
                            "O GitHub limitou as consultas. Tente de novo em alguns minutos.");
                } else if (code < 200 || code >= 300) {
                    result = new Check(State.ERROR, null, name, installed, "Falha na consulta (HTTP " + code + ").");
                } else {
                    String body;
                    // readAllBytes só existe a partir do Android 13; o app vai até o Android 11.
                    try (InputStream input = connection.getInputStream();
                         java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream()) {
                        byte[] buffer = new byte[16 * 1024];
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            if (bytes.size() + read > 5 * 1024 * 1024) throw new IllegalStateException("Resposta grande demais");
                            bytes.write(buffer, 0, read);
                        }
                        body = bytes.toString(StandardCharsets.UTF_8.name());
                    }
                    UpdatePicker.Candidate best = UpdatePicker.best(parse(body), installed, testChannel);
                    result = best == null
                            ? new Check(State.UP_TO_DATE, null, name, installed, "Você já está na versão mais nova.")
                            : new Check(State.AVAILABLE, best, name, installed, "");
                }
                connection.disconnect();
            } catch (Exception error) {
                result = new Check(State.ERROR, null, name, installed, "Sem conexão com o GitHub.");
            }
            Check finalResult = result;
            main.post(() -> callback.onResult(finalResult));
        }, "rota500-update-check").start();
    }

    static List<UpdatePicker.Candidate> parse(String body) throws Exception {
        List<UpdatePicker.Candidate> result = new ArrayList<>();
        JSONArray releases = new JSONArray(body);
        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.getJSONObject(i);
            if (release.optBoolean("draft", false)) continue;
            JSONArray assets = release.optJSONArray("assets");
            if (assets == null) continue;
            for (int a = 0; a < assets.length(); a++) {
                JSONObject asset = assets.getJSONObject(a);
                String assetName = asset.optString("name");
                long code = UpdatePicker.versionCodeFromAsset(assetName);
                if (code <= 0) continue;
                String version = release.optString("name", release.optString("tag_name"));
                result.add(new UpdatePicker.Candidate(version.startsWith("v") ? version.substring(1) : version,
                        code, release.optBoolean("prerelease", false),
                        asset.optString("browser_download_url"), assetName, asset.optLong("size", 0)));
            }
        }
        return result;
    }

    /** Q20 exige a permissão de instalar apps desconhecidos uma vez. */
    public boolean canInstall() { return context.getPackageManager().canRequestPackageInstalls(); }

    public Intent unknownSourcesSettings() {
        return new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + context.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /** Backup (se houver pasta), download e entrega ao instalador do Android. */
    public void downloadAndInstall(UpdatePicker.Candidate candidate, InstallCallback callback) {
        new Thread(() -> {
            try {
                String folder = new AppPreferences(context).backupTreeUri();
                if (!folder.isBlank()) {
                    post(callback, "Salvando backup antes de atualizar…");
                    new BackupManager(context).writeDataBackup(Uri.parse(folder));
                    new AppPreferences(context).setLastBackupAt(System.currentTimeMillis());
                }
                post(callback, "Baixando " + candidate.versionName() + "…");
                File apk = download(candidate);
                post(callback, "Abrindo o instalador…");
                install(apk);
            } catch (Exception error) {
                String message = error.getMessage() == null ? "Falha ao atualizar." : error.getMessage();
                main.post(() -> callback.onError(message));
            }
        }, "rota500-update-install").start();
    }

    private void post(InstallCallback callback, String message) { main.post(() -> callback.onProgress(message)); }

    private File download(UpdatePicker.Candidate candidate) throws Exception {
        if (!candidate.downloadUrl().startsWith("https://")) throw new IllegalStateException("Endereço de download inválido.");
        File folder = new File(context.getCacheDir(), "updates");
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IllegalStateException("Sem espaço para baixar.");
        File[] old = folder.listFiles();
        if (old != null) for (File file : old) file.delete();
        File target = new File(folder, "rota500-update.apk");
        HttpURLConnection connection = (HttpURLConnection) URI.create(candidate.downloadUrl()).toURL().openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(60_000);
        connection.setRequestProperty("User-Agent", "Rota500-app");
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) throw new IllegalStateException("Download falhou (HTTP " + code + ").");
        long total = 0;
        try (InputStream input = connection.getInputStream(); OutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_APK_BYTES) throw new IllegalStateException("Arquivo maior que o esperado.");
                output.write(buffer, 0, read);
            }
        } finally { connection.disconnect(); }
        if (candidate.sizeBytes() > 0 && total != candidate.sizeBytes()) {
            throw new IllegalStateException("Download incompleto. Tente de novo.");
        }
        return target;
    }

    private void install(File apk) throws Exception {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        params.setSize(apk.length());
        int sessionId = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(sessionId)) {
            try (InputStream input = new FileInputStream(apk);
                 OutputStream output = session.openWrite("rota500.apk", 0, apk.length())) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                session.fsync(output);
            }
            Intent status = new Intent(context, br.com.soswf.rota500.ui.MainActivity.class)
                    .setAction(ACTION_INSTALL_STATUS)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            // O instalador precisa preencher extras, por isso o PendingIntent é mutável.
            // FLAG_MUTABLE só existe a partir do Android 12; antes disso o PendingIntent já é mutável.
            int flags = android.os.Build.VERSION.SDK_INT >= 31
                    ? PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                    : PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent pending = PendingIntent.getActivity(context, 40, status, flags);
            session.commit(pending.getIntentSender());
        }
    }

    /** Traduz o retorno do instalador. Retorna null quando o Android pede confirmação. */
    public static String describeStatus(int status, String systemMessage) {
        return switch (status) {
            case PackageInstaller.STATUS_SUCCESS -> "Atualização instalada.";
            case PackageInstaller.STATUS_PENDING_USER_ACTION -> null;
            case PackageInstaller.STATUS_FAILURE_ABORTED -> "Atualização cancelada.";
            case PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                    "Esta versão tem outra assinatura. Faça backup, desinstale o Rota 500, instale de novo e restaure.";
            case PackageInstaller.STATUS_FAILURE_STORAGE -> "Sem espaço no celular para instalar.";
            default -> "Falha na instalação" + (systemMessage == null ? "." : ": " + systemMessage);
        };
    }
}
