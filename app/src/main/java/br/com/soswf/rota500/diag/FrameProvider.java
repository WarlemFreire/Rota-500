package br.com.soswf.rota500.diag;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Entrega só o último quadro do diagnóstico para o app escolhido pelo motorista (WhatsApp,
 * e-mail…), com permissão temporária de leitura. Nenhum outro arquivo é exposto.
 */
public final class FrameProvider extends ContentProvider {
    public static final String AUTHORITY = "br.com.soswf.rota500.diagframe";
    public static final Uri FRAME = Uri.parse("content://" + AUTHORITY + "/quadro.jpg");

    @Override public boolean onCreate() { return true; }

    private File file() {
        return new DiagnosticLog(getContext()).frameFile();
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!FRAME.equals(uri) || !"r".equals(mode)) throw new FileNotFoundException("Arquivo indisponível");
        return ParcelFileDescriptor.open(file(), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri uri) { return "image/jpeg"; }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        if (!FRAME.equals(uri)) return null;
        MatrixCursor cursor = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        cursor.addRow(new Object[]{"rota500-quadro.jpg", file().length()});
        return cursor;
    }

    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
