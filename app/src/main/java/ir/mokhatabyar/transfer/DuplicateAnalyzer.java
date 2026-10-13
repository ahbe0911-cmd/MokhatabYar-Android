package ir.mokhatabyar.transfer;

import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.ContactsContract;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/** Exact normalized-number comparison: does not merge by name or number suffix. */
public final class DuplicateAnalyzer {
    public enum Choice { ADD, DUPLICATE, REVIEW }
    private final Set<String> known;
    private final Set<String> needsReview = new HashSet<>();

    public DuplicateAnalyzer(Set<String> existing) { known = new HashSet<>(existing); }

    public static Set<String> readExisting(ContentResolver resolver) {
        Set<String> numbers = new HashSet<>();
        try (Cursor c = resolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, null)) {
            if (c == null) throw new IllegalStateException("خواندن شماره‌های گوشی ممکن نیست.");
            while (c.moveToNext()) {
                String key = normalize(c.getString(0));
                if (key != null) numbers.add(key);
            }
        }
        return numbers;
    }

    public static String normalize(String raw) {
        if (raw == null) return null;
        StringBuilder digits = new StringBuilder();
        boolean plus = false;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch == '+' && digits.length() == 0) { plus = true; continue; }
            int d = Character.digit(ch, 10);
            if (d >= 0) digits.append((char)('0' + d));
            else if (ch == '*' || ch == '#' || Character.isLetter(ch)) return null;
        }
        String s = digits.toString();
        if (s.length() < 8 || s.length() > 16) return null;
        if (s.startsWith("0098")) return "98" + s.substring(4);
        if (s.startsWith("98") && (s.length() == 11 || s.length() == 12)) return s;
        if (!plus && s.startsWith("0") && s.length() >= 10 && s.length() <= 12)
            return "98" + s.substring(1);
        if (!plus && s.startsWith("9") && s.length() == 10) return "98" + s;
        return plus ? "+" + s : s;
    }

    public Choice classify(Iterable<String> numbers) {
        Set<String> keys = new LinkedHashSet<>();
        boolean invalid = false;
        for (String n : numbers) {
            String k = normalize(n);
            if (k == null) invalid = true;
            else keys.add(k);
        }
        if (keys.isEmpty()) return Choice.REVIEW;
        int matches = 0;
        boolean blocked = false;
        for (String key : keys) {
            if (known.contains(key)) matches++;
            if (needsReview.contains(key)) blocked = true;
        }
        if (invalid || blocked || (matches > 0 && matches < keys.size())) {
            needsReview.addAll(keys);
            return Choice.REVIEW;
        }
        if (matches == keys.size()) return Choice.DUPLICATE;
        return Choice.ADD;
    }
    public void accept(Iterable<String> numbers) {
        for (String n : numbers) {
            String k = normalize(n);
            if (k != null) known.add(k);
        }
    }
}
