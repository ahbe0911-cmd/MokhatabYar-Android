package ir.mokhatabyar.transfer;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import java.io.*;
import java.util.*;

/** Uses the Android contacts provider's vCard serializer, including optional fields. */
public final class VCardExport {
    private VCardExport() {}
    public interface Progress { void update(int done, int total); }

    public static int count(Context context) {
        try (Cursor c = context.getContentResolver().query(
                ContactsContract.Contacts.CONTENT_URI,
                new String[]{ContactsContract.Contacts._ID}, null, null, null)) {
            if (c == null) throw new IllegalStateException("خواندن مخاطبین ممکن نیست.");
            return c.getCount();
        }
    }

    public static int export(Context context, File target, Progress listener) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        List<String> lookups = new ArrayList<>();
        try (Cursor c = resolver.query(ContactsContract.Contacts.CONTENT_URI,
                new String[]{ContactsContract.Contacts.LOOKUP_KEY}, null, null, null)) {
            if (c == null) throw new IOException("دسترسی به مخاطبین ممکن نیست.");
            while (c.moveToNext()) {
                String key = c.getString(0);
                if (key != null && !key.isEmpty()) lookups.add(key);
            }
        }
        if (lookups.isEmpty()) throw new IOException("مخاطبی پیدا نشد.");
        File tmp = new File(target.getAbsolutePath() + ".tmp");
        int done = 0;
        try {
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(tmp), 65536)) {
                // One contact at a time avoids OEM-specific multi-vCard URI limits.
                for (String key : lookups) {
                    Uri uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, key);
                    try (AssetFileDescriptor fd = resolver.openAssetFileDescriptor(uri, "r")) {
                        if (fd == null) throw new IOException("خروجی مخاطب ممکن نیست.");
                        try (InputStream in = fd.createInputStream()) {
                            byte[] buf = new byte[32768];
                            int n;
                            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                            out.write('\r');
                            out.write('\n');
                        }
                    }
                    done++;
                    if (done % 30 == 0 || done == lookups.size()) listener.update(done, lookups.size());
                }
            }
            if (target.exists() && !target.delete()) throw new IOException("حذف پشتیبان قبلی ممکن نیست.");
            if (!tmp.renameTo(target)) throw new IOException("ذخیره خروجی ناموفق بود.");
            return done;
        } catch (Exception e) {
            tmp.delete();
            if (e instanceof IOException) throw (IOException)e;
            throw new IOException("خطا در خروجی گرفتن: " + e.getMessage(), e);
        }
    }
}
