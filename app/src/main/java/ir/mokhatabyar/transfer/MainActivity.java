package ir.mokhatabyar.transfer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int PICK = 101, SAVE = 102, ASK = 103;
    private static final int EXPORT = 1, PREVIEW = 2, IMPORT = 3;
    private static final int TEAL = Color.rgb(14,112,127);
    private static final int INK = Color.rgb(26,43,57);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView count, report, status;
    private Button exportBtn, saveBtn, shareBtn, pickBtn, importBtn, backupBtn;
    private Uri incoming;
    private VCardImport.Result plan;
    private int pendingAction;
    private boolean hasExport, busy;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(244,248,252));
        getWindow().setNavigationBarColor(Color.rgb(244,248,252));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        layout();
        if (allowed(Manifest.permission.READ_CONTACTS)) refreshCount();
    }

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private TextView text(String s, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(INK);
        t.setTextSize(size);
        t.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        t.setGravity(Gravity.RIGHT);
        t.setLineSpacing(dp(3), 1.0f);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private Button button(String s, int color) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setMinimumHeight(dp(52));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(14));
        b.setBackground(bg);
        return b;
    }
    private LinearLayout.LayoutParams margin(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2);
        p.topMargin = dp(top);
        return p;
    }
    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(17),dp(19),dp(17),dp(19));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(17));
        bg.setStroke(dp(1), Color.rgb(220,231,238));
        c.setBackground(bg);
        return c;
    }
    private void layout() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setFitsSystemWindows(true);
        scroll.setBackgroundColor(Color.rgb(244,248,252));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(20),dp(20),dp(20),dp(35));
        scroll.addView(root);
        root.addView(text("مخاطب‌یار", 30,true));
        root.addView(text("انتقال همه مخاطبین، بدون اینترنت و بدون ورود تکراری‌ها",14,false),margin(5));

        LinearLayout first = card();
        first.addView(text("۱. پشتیبان‌گیری از گوشی اول",19,true));
        count = text("برای مشاهده تعداد، مجوز مخاطبین را بدهید.",14,false);
        first.addView(count,margin(10));
        exportBtn = button("ساخت فایل از تمام مخاطبین",TEAL);
        exportBtn.setOnClickListener(v -> permission(EXPORT));
        first.addView(exportBtn,margin(11));
        saveBtn = button("ذخیره فایل VCF", TEAL);
        saveBtn.setOnClickListener(v -> chooseSave());
        first.addView(saveBtn,margin(9));
        shareBtn = button("ارسال فایل به گوشی دوم",Color.rgb(66,82,104));
        shareBtn.setOnClickListener(v -> share(ShareProvider.EXPORT));
        first.addView(shareBtn,margin(9));
        root.addView(first,margin(18));

        LinearLayout second = card();
        second.addView(text("۲. دریافت در گوشی دوم",19,true));
        second.addView(text("ابتدا فایل بررسی می‌شود. شماره‌های تکراری ثبت نخواهند شد.",14,false),margin(8));
        pickBtn = button("انتخاب و بررسی فایل VCF",TEAL);
        pickBtn.setOnClickListener(v -> chooseFile());
        second.addView(pickBtn,margin(10));
        report = text("هنوز فایلی انتخاب نشده است.",14,false);
        second.addView(report,margin(14));
        importBtn = button("افزودن فقط مخاطبین جدید",Color.rgb(23,119,85));
        importBtn.setOnClickListener(v -> permission(IMPORT));
        second.addView(importBtn,margin(10));
        root.addView(second,margin(15));

        backupBtn = button("اشتراک‌گذاری پشتیبان قبل از انتقال",Color.rgb(66,82,104));
        backupBtn.setOnClickListener(v -> share(ShareProvider.SAFETY));
        root.addView(backupBtn,margin(5));
        status = text("مخاطبین فقط روی دستگاه شما پردازش می‌شوند.",13,false);
        root.addView(status,margin(14));
        setContentView(scroll);
        updateButtons();
    }
    private boolean allowed(String p) { return checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED; }
    private void permission(int action) {
        pendingAction = action;
        if (allowed(Manifest.permission.READ_CONTACTS)
                && (action != IMPORT || allowed(Manifest.permission.WRITE_CONTACTS))) {
            runAction(action); return;
        }
        if (action == IMPORT) requestPermissions(new String[]{Manifest.permission.READ_CONTACTS,
                Manifest.permission.WRITE_CONTACTS},ASK);
        else requestPermissions(new String[]{Manifest.permission.READ_CONTACTS},ASK);
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] granted) {
        super.onRequestPermissionsResult(code,permissions,granted);
        if (code != ASK) return;
        boolean ok = granted.length > 0;
        for (int value : granted) if (value != PackageManager.PERMISSION_GRANTED) ok=false;
        if (ok) runAction(pendingAction);
        else toast("مجوز خواندن یا ثبت مخاطبین داده نشد.");
    }
    private void runAction(int action) {
        if (action == EXPORT) exportContacts();
        else if (action == PREVIEW) inspect();
        else if (action == IMPORT) confirmImport();
        refreshCount();
    }
    private void refreshCount() {
        if (!allowed(Manifest.permission.READ_CONTACTS)) return;
        worker.execute(() -> {
            try {
                int n = VCardExport.count(this);
                runOnUiThread(() -> count.setText("مخاطبین موجود در گوشی: " + n));
            } catch (Exception ignored) {}
        });
    }
    private void updateButtons() {
        exportBtn.setEnabled(!busy);
        saveBtn.setEnabled(!busy && hasExport);
        shareBtn.setEnabled(!busy && hasExport);
        pickBtn.setEnabled(!busy);
        importBtn.setEnabled(!busy && plan != null && plan.added > 0 && incoming != null);
        backupBtn.setEnabled(!busy && new File(getFilesDir(),ShareProvider.SAFETY_NAME).isFile());
    }
    private void state(boolean active, String s) {
        busy = active;
        status.setText(s);
        updateButtons();
    }
    private void exportContacts() {
        state(true,"در حال ساخت فایل VCF…");
        worker.execute(() -> {
            try {
                int total = VCardExport.export(this,new File(getCacheDir(),ShareProvider.EXPORT_NAME),
                        (n,all) -> runOnUiThread(() -> status.setText("ساخت فایل: " + n + " از " + all)));
                runOnUiThread(() -> {
                    hasExport = true;
                    state(false,total + " مخاطب در یک فایل آماده شد.");
                });
            } catch (Exception e) { error(e); }
        });
    }
    private void chooseSave() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/x-vcard");
        i.putExtra(Intent.EXTRA_TITLE,"Contacts-Backup.vcf");
        startActivityForResult(i,SAVE);
    }
    private void share(Uri uri) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/x-vcard");
        i.putExtra(Intent.EXTRA_STREAM,uri);
        i.setClipData(ClipData.newUri(getContentResolver(),"VCF",uri));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(i,"ارسال فایل مخاطبین"));
    }
    private void chooseFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i,PICK);
    }
    @Override protected void onActivityResult(int request, int code, Intent data) {
        super.onActivityResult(request,code,data);
        if (code != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (request == PICK) {
            incoming = uri;
            plan = null;
            report.setText("فایل انتخاب شد؛ آماده بررسی");
            updateButtons();
            try {
                getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            permission(PREVIEW);
        } else if (request == SAVE) {
            state(true,"در حال ذخیره فایل…");
            worker.execute(() -> {
                try (InputStream in = new FileInputStream(new File(getCacheDir(),ShareProvider.EXPORT_NAME));
                     OutputStream out = getContentResolver().openOutputStream(uri,"wt")) {
                    if (out == null) throw new IOException("ذخیره فایل ممکن نیست.");
                    byte[] b = new byte[32768];
                    int n;
                    while ((n=in.read(b))!=-1) out.write(b,0,n);
                    runOnUiThread(() -> state(false,"فایل VCF ذخیره شد."));
                } catch (Exception e) { error(e); }
            });
        }
    }
    private void inspect() {
        final Uri uri = incoming;
        if (uri == null) return;
        state(true,"در حال بررسی شماره‌ها…");
        worker.execute(() -> {
            try {
                VCardImport.Result r = VCardImport.inspect(this,uri,
                        (scanned,newCount,dup) -> runOnUiThread(() -> status.setText(scanned + " مخاطب بررسی شد…")));
                runOnUiThread(() -> {
                    if (!uri.equals(incoming)) return;
                    plan = r;
                    report.setText("کل مخاطبین فایل: " + r.seen
                            + "\nجدید: " + r.added
                            + "\nتکراری: " + r.duplicates
                            + "\nنیازمند بررسی دستی: " + r.review
                            + "\nخطا: " + r.failures
                            + (r.reviewNames.isEmpty() ? "" : "\n\nنمونه موارد نیازمند بررسی:\n• "
                              + android.text.TextUtils.join("\n• ",r.reviewNames)));
                    state(false,"بررسی کامل شد؛ هنوز چیزی وارد نشده است.");
                });
            } catch (Exception e) { error(e); }
        });
    }
    private void confirmImport() {
        if (plan == null || plan.added == 0) return;
        new AlertDialog.Builder(this)
                .setTitle("تأیید ثبت مخاطبین جدید")
                .setMessage(plan.added + " مخاطب جدید افزوده می‌شود. "
                        + plan.duplicates + " مخاطب تکراری و " + plan.review
                        + " مورد مبهم بدون تغییر می‌ماند.\n\n"
                        + "برنامه قبل از افزودن از مخاطبین فعلی پشتیبان می‌گیرد. ادامه می‌دهید؟")
                .setNegativeButton("انصراف",null)
                .setPositiveButton("شروع انتقال",(dialog,which) -> importContacts())
                .show();
    }
    private void importContacts() {
        final Uri uri = incoming;
        if (uri == null) return;
        state(true,"در حال پشتیبان‌گیری از مخاطبین گوشی…");
        worker.execute(() -> {
            try {
                int count = VCardExport.count(this);
                if (count > 0) {
                    File target = new File(getFilesDir(),ShareProvider.SAFETY_NAME);
                    VCardExport.export(this,target,
                            (n,total)->runOnUiThread(() -> status.setText("پشتیبان: " + n + " از " + total)));
                }
                VCardImport.Result r = VCardImport.importNewOnly(this,uri,
                        (scanned,added,dups)->runOnUiThread(() -> status.setText(
                                "بررسی: " + scanned + " / افزوده: " + added)));
                runOnUiThread(() -> {
                    plan=null;
                    report.setText("ثبت پایان یافت.\nافزوده‌شده: " + r.added
                            + "\nتکراریِ ردشده: " + r.duplicates
                            + "\nنیازمند بررسی: " + r.review
                            + "\nخطا: " + r.failures);
                    state(false,"ثبت پایان یافت. پشتیبان قبلی را در جای امن ذخیره کنید.");
                    refreshCount();
                });
            } catch (Exception e) { error(e); }
        });
    }
    private void error(Exception e) {
        runOnUiThread(() -> {
            state(false,"خطا: " + (e.getMessage()==null?"عملیات ناموفق بود":e.getMessage()));
            toast("عملیات کامل نشد.");
        });
    }
    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    @Override protected void onDestroy() { super.onDestroy(); worker.shutdown(); }
}
