package ir.mokhatabyar.transfer;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Only reads contacts and exports them as one VCF: never modifies the address book. */
public final class MainActivity extends Activity {
    private static final int READ = 101;
    private static final int SAVE = 102;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final int ink = Color.rgb(23, 45, 62);
    private final int teal = Color.rgb(0, 117, 127);
    private TextView count, status, fileStatus;
    private Button generate, save, share;
    private ProgressBar bar;
    private File result;
    private volatile boolean busy;

    private int dp(int x) { return (int)(x * getResources().getDisplayMetrics().density + .5f); }

    private GradientDrawable bg(int color, int round, boolean bordered) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(round));
        if (bordered) shape.setStroke(dp(1), Color.rgb(223, 233, 237));
        return shape;
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        v.setGravity(Gravity.RIGHT);
        v.setLineSpacing(dp(3), 1f);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private LinearLayout.LayoutParams gap(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(top);
        return p;
    }

    private LinearLayout card() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        v.setPadding(dp(20), dp(20), dp(20), dp(20));
        v.setBackground(bg(Color.WHITE, 20, true));
        return v;
    }

    private Button button(String title, int color) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextSize(16);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setMinHeight(dp(56));
        b.setBackground(bg(color, 14, false));
        return b;
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(244, 249, 250));
        getWindow().setNavigationBarColor(Color.rgb(244, 249, 250));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        result = new File(getCacheDir(), ShareProvider.EXPORT_NAME);
        layout();
        buttons();
        if (permitted()) refreshCount();
    }

    private void layout() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(244, 249, 250));
        LinearLayout root = new LinearLayout(this);
        root.setPadding(dp(20), dp(30), dp(20), dp(30));
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(root);

        root.addView(label("مخاطب‌یار", 31, ink, true));
        root.addView(label("تمام مخاطبین در یک فایل", 16, teal, false), gap(4));

        LinearLayout stats = card();
        stats.addView(label("تعداد مخاطبین گوشی", 15, Color.GRAY, false));
        count = label(permitted() ? "در حال شمارش…" : "برای شمارش دسترسی بدهید", 29, teal, true);
        stats.addView(count, gap(12));
        stats.addView(label("بدون محدودیت انتخاب ۱۰۰۰ یا ۵۰۰۰ مخاطب", 13, Color.GRAY, false), gap(6));
        root.addView(stats, gap(23));

        LinearLayout actions = card();
        actions.addView(label("ساخت فایل پشتیبان", 20, ink, true));
        actions.addView(label("نام، شماره‌ها و جزئیات قابل خروجی، به‌صورت یک فایل VCF ذخیره می‌شوند.",
                14, Color.GRAY, false), gap(8));
        generate = button("ساخت فایل همه مخاطبین", teal);
        actions.addView(generate, gap(19));
        generate.setOnClickListener(v -> start());

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(false);
        bar.setMax(100);
        actions.addView(bar, gap(16));

        fileStatus = label("هنوز فایلی ساخته نشده است.", 13, Color.GRAY, false);
        actions.addView(fileStatus, gap(12));
        save = button("ذخیره فایل VCF", ink);
        share = button("ارسال به گوشی دیگر", Color.rgb(40, 126, 92));
        actions.addView(save, gap(14));
        actions.addView(share, gap(10));
        save.setOnClickListener(v -> saveAs());
        share.setOnClickListener(v -> shareFile());
        root.addView(actions, gap(16));

        status = label("این برنامه مخاطبین را ویرایش یا حذف نمی‌کند و به اینترنت نیاز ندارد.",
                13, Color.rgb(75, 94, 107), false);
        root.addView(status, gap(18));
        root.addView(label("در گوشی دوم فقط فایل VCF را با برنامه مخاطبین باز و وارد کنید؛ نصب برنامه لازم نیست.",
                13, ink, true), gap(14));
        setContentView(scroll);
    }

    private boolean permitted() {
        return checkSelfPermission(Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void refreshCount() {
        io.execute(() -> {
            try {
                int n = VCardExport.count(this);
                runOnUiThread(() -> count.setText(String.format(Locale.getDefault(), "%,d", n)));
            } catch (Exception e) {
                runOnUiThread(() -> count.setText("شمارش امکان‌پذیر نشد"));
            }
        });
    }

    private void buttons() {
        boolean ready = result != null && result.exists() && result.length() > 0;
        generate.setEnabled(!busy);
        save.setEnabled(!busy && ready);
        share.setEnabled(!busy && ready);
        bar.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (ready && !busy) {
            fileStatus.setText("فایل آماده • " +
                    String.format(Locale.getDefault(), "%.2f", result.length() / 1048576.0) + " مگابایت");
        }
    }

    private void start() {
        if (busy) return;
        if (!permitted()) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, READ);
            return;
        }
        busy = true;
        bar.setProgress(0);
        status.setText("در حال ساخت فایل مخاطبین…");
        fileStatus.setText("در حال خروجی گرفتن…");
        buttons();
        io.execute(() -> {
            try {
                int total = VCardExport.export(this, result, (done, all) ->
                        runOnUiThread(() -> {
                            bar.setProgress((int)(100L * done / Math.max(1, all)));
                            status.setText("خروجی گرفتن: " + done + " از " + all);
                        }));
                runOnUiThread(() -> {
                    busy = false;
                    status.setText("انجام شد! همه " + total + " مخاطب در یک فایل آماده‌اند.");
                    buttons();
                    Toast.makeText(this, "فایل مخاطبین ساخته شد", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    busy = false;
                    status.setText("خطا: " + e.getMessage());
                    fileStatus.setText("عملیات ناموفق بود. دوباره امتحان کنید.");
                    buttons();
                });
            }
        });
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code != READ) return;
        if (permitted()) {
            refreshCount();
            start();
        } else status.setText("برای تهیه فایل، اجازه خواندن مخاطبین لازم است.");
    }

    private void saveAs() {
        if (busy || !result.isFile()) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/x-vcard");
        intent.putExtra(Intent.EXTRA_TITLE, "Contacts-" +
                new SimpleDateFormat("yyyy-MM-dd-HH-mm", Locale.US).format(new Date()) + ".vcf");
        startActivityForResult(intent, SAVE);
    }

    @Override protected void onActivityResult(int code, int outcome, Intent intent) {
        super.onActivityResult(code, outcome, intent);
        if (code != SAVE || outcome != RESULT_OK || intent == null || intent.getData() == null) return;
        if (busy) return;
        Uri target = intent.getData();
        busy = true;
        buttons();
        status.setText("در حال ذخیره فایل…");
        io.execute(() -> {
            try (InputStream input = new FileInputStream(result);
                 OutputStream output = getContentResolver().openOutputStream(target, "w")) {
                if (output == null) throw new IOException("حافظه مقصد قابل نوشتن نیست.");
                byte[] buf = new byte[65536];
                int size;
                while ((size = input.read(buf)) != -1) output.write(buf, 0, size);
                output.flush();
                runOnUiThread(() -> {
                    busy = false;
                    status.setText("فایل مخاطبین ذخیره شد.");
                    buttons();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    busy = false;
                    status.setText("خطای ذخیره فایل: " + e.getMessage());
                    buttons();
                });
            }
        });
    }

    private void shareFile() {
        if (busy || !result.isFile()) return;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/x-vcard");
        intent.putExtra(Intent.EXTRA_STREAM, ShareProvider.EXPORT);
        intent.setClipData(ClipData.newUri(getContentResolver(), "Contacts.vcf", ShareProvider.EXPORT));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "اشتراک‌گذاری فایل مخاطبین"));
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }
}
