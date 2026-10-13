package ir.mokhatabyar.transfer;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import ezvcard.Ezvcard;
import ezvcard.VCard;
import ezvcard.property.Email;
import ezvcard.property.FormattedName;
import ezvcard.property.Organization;
import ezvcard.property.Photo;
import ezvcard.property.StructuredName;
import ezvcard.property.Telephone;

/** Preview and import follow the same conservative decision logic. */
public final class VCardImport {
    private VCardImport() {}
    public interface Progress { void update(int scanned, int added, int duplicate); }
    public static final class Result {
        public int seen, added, duplicates, review, failures;
        public final List<String> reviewNames = new ArrayList<>();
    }
    public static Result inspect(Context context, Uri uri, Progress progress) throws IOException {
        return run(context, uri, false, progress);
    }
    public static Result importNewOnly(Context context, Uri uri, Progress progress) throws IOException {
        return run(context, uri, true, progress);
    }
    private static Result run(Context context, Uri uri, boolean write, Progress progress) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        DuplicateAnalyzer analyzer;
        try {
            analyzer = new DuplicateAnalyzer(DuplicateAnalyzer.readExisting(resolver));
        } catch (SecurityException | IllegalStateException ex) {
            throw new IOException("دسترسی به شماره‌های فعلی گوشی ممکن نیست.", ex);
        }
        Result result = new Result();
        try (InputStream stream = resolver.openInputStream(uri)) {
            if (stream == null) throw new IOException("خواندن فایل انتخاب‌شده ممکن نیست.");
            java.util.Iterator<VCard> cards = Ezvcard.parse(stream).iterator();
            while (cards.hasNext()) {
                VCard card = cards.next();
                result.seen++;
                try {
                    List<String> phones = numbers(card);
                    DuplicateAnalyzer.Choice decision = analyzer.classify(phones);
                    if (decision == DuplicateAnalyzer.Choice.DUPLICATE) result.duplicates++;
                    else if (decision == DuplicateAnalyzer.Choice.REVIEW) {
                        result.review++;
                        if (result.reviewNames.size() < 12) result.reviewNames.add(displayName(card));
                    } else if (!write) {
                        result.added++;
                        analyzer.accept(phones);
                    } else if (insert(resolver, card, phones)) {
                        result.added++;
                        analyzer.accept(phones);
                    } else {
                        result.failures++;
                    }
                } catch (Exception ex) {
                    // A malformed card never modifies existing contacts; continue to next card.
                    result.failures++;
                }
                if (result.seen % 20 == 0) progress.update(result.seen, result.added, result.duplicates);
            }
            progress.update(result.seen, result.added, result.duplicates);
        } catch (RuntimeException ex) {
            throw new IOException("ساختار فایل VCF قابل پردازش نیست: " + ex.getMessage(), ex);
        }
        return result;
    }
    private static List<String> numbers(VCard card) {
        List<String> out = new ArrayList<>();
        for (Telephone tel : card.getTelephoneNumbers()) {
            String n = tel.getText();
            if (n == null && tel.getUri() != null) n = tel.getUri().getNumber();
            if (n != null && !n.trim().isEmpty()) out.add(n.trim());
        }
        return out;
    }
    private static String trim(String s) { return s == null ? "" : s.trim(); }
    private static String displayName(VCard v) {
        FormattedName f = v.getFormattedName();
        if (f != null && !trim(f.getValue()).isEmpty()) return trim(f.getValue());
        StructuredName n = v.getStructuredName();
        if (n != null) {
            String s = (trim(n.getGiven()) + " " + trim(n.getFamily())).trim();
            if (!s.isEmpty()) return s;
        }
        return "(بدون نام)";
    }
    private static ContentProviderOperation.Builder data(String mime) {
        return ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, mime);
    }
    private static int telType(Telephone tel) {
        String t = tel.getTypes().toString().toUpperCase(Locale.ROOT);
        if (t.contains("CELL")) return ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE;
        if (t.contains("HOME")) return ContactsContract.CommonDataKinds.Phone.TYPE_HOME;
        if (t.contains("WORK")) return ContactsContract.CommonDataKinds.Phone.TYPE_WORK;
        return ContactsContract.CommonDataKinds.Phone.TYPE_OTHER;
    }
    private static int emailType(Email email) {
        String t = email.getTypes().toString().toUpperCase(Locale.ROOT);
        if (t.contains("HOME")) return ContactsContract.CommonDataKinds.Email.TYPE_HOME;
        if (t.contains("WORK")) return ContactsContract.CommonDataKinds.Email.TYPE_WORK;
        return ContactsContract.CommonDataKinds.Email.TYPE_OTHER;
    }
    private static boolean insert(ContentResolver resolver, VCard v, List<String> phones) throws Exception {
        if (phones.isEmpty()) return false;
        String name = displayName(v);
        if (name.equals("(بدون نام)")) name = phones.get(0);
        ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null).build());
        ops.add(data(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name).build());
        for (Telephone tel : v.getTelephoneNumbers()) {
            String n = tel.getText();
            if (n == null && tel.getUri() != null) n = tel.getUri().getNumber();
            if (trim(n).isEmpty()) continue;
            ops.add(data(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, n.trim())
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, telType(tel)).build());
        }
        for (Email email : v.getEmails()) {
            if (trim(email.getValue()).isEmpty()) continue;
            ops.add(data(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email.getValue())
                    .withValue(ContactsContract.CommonDataKinds.Email.TYPE, emailType(email)).build());
        }
        Organization org = v.getOrganization();
        if (org != null && !org.getValues().isEmpty()) {
            ops.add(data(ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, org.getValues().get(0)).build());
        }
        if (!v.getNotes().isEmpty()) {
            String note = v.getNotes().get(0).getValue();
            if (!trim(note).isEmpty()) ops.add(data(ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Note.NOTE, note).build());
        }
        if (!v.getPhotos().isEmpty()) {
            byte[] thumb = thumbnail(v.getPhotos().get(0));
            if (thumb != null) ops.add(data(ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, thumb).build());
        }
        resolver.applyBatch(ContactsContract.AUTHORITY, ops);
        return true;
    }
    private static byte[] thumbnail(Photo photo) {
        byte[] raw = photo.getData();
        if (raw == null || raw.length == 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(raw, 0, raw.length, options);
        if (options.outWidth <= 0 || options.outHeight <= 0) return null;
        int sample = 1;
        while (options.outWidth / sample > 512 || options.outHeight / sample > 512) sample *= 2;
        options.inJustDecodeBounds = false;
        options.inSampleSize = sample;
        Bitmap bmp = BitmapFactory.decodeByteArray(raw, 0, raw.length, options);
        if (bmp == null) return null;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 75, out);
            byte[] result = out.toByteArray();
            return result.length < 200000 ? result : null;
        } finally { bmp.recycle(); }
    }
}
