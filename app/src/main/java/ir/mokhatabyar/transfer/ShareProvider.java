package ir.mokhatabyar.transfer;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Only exposes two app-owned VCF files with an explicit temporary URI grant. */
public final class ShareProvider extends ContentProvider {
    public static final String AUTH = "ir.mokhatabyar.transfer.share";
    public static final Uri EXPORT = Uri.parse("content://" + AUTH + "/contacts.vcf");
    public static final Uri SAFETY = Uri.parse("content://" + AUTH + "/before-import.vcf");
    public static final String EXPORT_NAME = "all-contacts.vcf";
    public static final String SAFETY_NAME = "before-import.vcf";

    private File file(Uri uri) {
        if (EXPORT.equals(uri)) return new File(getContext().getCacheDir(), EXPORT_NAME);
        if (SAFETY.equals(uri)) return new File(getContext().getFilesDir(), SAFETY_NAME);
        return null;
    }
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return file(uri) != null ? "text/x-vcard" : null; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = file(uri);
        if (f == null || !"r".equals(mode) || !f.isFile()) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File f = file(uri);
        if (f == null) return null;
        String[] cols = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor c = new MatrixCursor(cols);
        Object[] row = new Object[cols.length];
        for (int i=0;i<cols.length;i++) {
            if (cols[i].equals(OpenableColumns.DISPLAY_NAME)) row[i] = SAFETY.equals(uri) ? "Before-Import-Backup.vcf" : "Contacts-Backup.vcf";
            else if (cols[i].equals(OpenableColumns.SIZE)) row[i] = f.length();
        }
        c.addRow(row);
        return c;
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
}
