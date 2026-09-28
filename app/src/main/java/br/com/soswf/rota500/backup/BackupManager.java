package br.com.soswf.rota500.backup;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.OutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import br.com.soswf.rota500.data.AppDatabase;
import br.com.soswf.rota500.data.AppPreferences;
import org.json.JSONObject;

public final class BackupManager {
    private final Context context;

    public BackupManager(Context context) { this.context = context.getApplicationContext(); }

    public Uri writeDataBackup(Uri treeUri) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri));
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        Uri file = DocumentsContract.createDocument(resolver, parent, "application/json", "rota500-backup-" + stamp + ".json");
        if (file == null) throw new IllegalStateException("Não foi possível criar o arquivo");
        JSONObject snapshot = AppDatabase.get(context).exportData();
        snapshot.put("data_settings", new AppPreferences(context).exportDataSettings());
        byte[] data = snapshot.toString(2).getBytes(StandardCharsets.UTF_8);
        try (OutputStream output = resolver.openOutputStream(file, "w")) {
            if (output == null) throw new IllegalStateException("Pasta sem permissão de escrita");
            output.write(data);
        }
        return file;
    }

    public void restoreDataBackup(Uri fileUri) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream input = context.getContentResolver().openInputStream(fileUri)) {
            if (input == null) throw new IllegalStateException("Arquivo inacessível");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (bytes.size() + read > 100 * 1024 * 1024) throw new IllegalArgumentException("Backup maior que 100 MB");
                bytes.write(buffer, 0, read);
            }
        }
        JSONObject snapshot = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        AppDatabase.get(context).restoreData(snapshot);
        new AppPreferences(context).restoreDataSettings(snapshot.optJSONObject("data_settings"));
    }
}
