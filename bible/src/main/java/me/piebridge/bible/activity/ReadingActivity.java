package me.piebridge.bible.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import me.piebridge.bible.BibleApplication;
import me.piebridge.bible.BuildConfig;
import me.piebridge.bible.R;
import me.piebridge.bible.fragment.ReadingFragment;
import me.piebridge.bible.fragment.SwitchVersionConfirmFragment;
import me.piebridge.bible.provider.VersionProvider;
import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.FileUtils;
import me.piebridge.bible.utils.LogUtils;
import me.piebridge.bible.utils.ObjectUtils;

/**
 * Created by thom on 15/10/18.
 */
public class ReadingActivity extends AbstractReadingActivity {

    public static final String WEBVIEW_DATA = "webview-data";

    private static final int SIZE = 1189;
    private static final String FRAGMENT_CONFIRM = "fragment-confirm";

    private TextView bookView;
    private TextView chapterView;

    private BroadcastReceiver receiver;

    private String mTitle;

    private static final int MESSAGE_REPORT_BUG = 1;
    private Handler workHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        receiver = new Receiver(this);
        workHandler = new WorkHandler(this, new MainHandler(this));
    }

    @Override
    protected void initializeHeader(View header) {
        setupDrawer();
        bookView = header.findViewById(R.id.book);
        header.findViewById(R.id.book_button).setOnClickListener(this);

        chapterView = header.findViewById(R.id.chapter);
        header.findViewById(R.id.chapter_button).setOnClickListener(this);

        initializeVersion(header);
    }

    @Override
    protected void updateHeader(Bundle bundle, String osis) {
        String book = BibleUtils.getBook(osis);
        BibleApplication application = (BibleApplication) getApplication();
        String bookName = application.getHuman(book);
        String chapterVerse = BibleUtils.getChapterVerse(this, bundle);
        mTitle = BibleUtils.getBookChapterVerse(bookName, chapterVerse);

        bookView.setText(bookName);
        chapterView.setText(chapterVerse);
        updateTaskDescription(mTitle);

        ReadingFragment currentFragment = getCurrentFragment();
        if (currentFragment != null) {
            currentFragment.onSelected();
        }
    }

    @Override
    protected int getToolbarLayout() {
        return R.id.toolbar_reading;
    }

    @Override
    protected int getContentLayout() {
        return R.layout.drawer_reading;
    }

    @Override
    protected int retrieveOsisCount() {
        Uri uri = VersionProvider.CONTENT_URI_CHAPTERS.buildUpon().build();
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getInt(cursor.getColumnIndex(BaseColumns._COUNT));
            }
        } catch (SQLiteException e) {
            LogUtils.d("cannot get chapter size", e);
        }
        return SIZE;
    }

    protected String getInitialOsis() {
        return PreferenceManager.getDefaultSharedPreferences(this).getString(OSIS, null);
    }

    protected int getInitialPosition() {
        return POSITION_UNKNOWN;
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, intentFilter);
    }

    @Override
    protected void onStop() {
        saveOsis();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        setCheckedItem(R.id.menu_reading);
        String currentVersion = getCurrentVersion();
        BibleApplication application = (BibleApplication) getApplication();
        String databaseVersion = application.getVersion();
        if (currentVersion != null && !currentVersion.equals(databaseVersion)) {
            LogUtils.d("version changed from " + currentVersion + " to " + databaseVersion);
            refreshAdapter();
        }
        if (BibleUtils.isDemoVersion(databaseVersion)) {
            String version = BibleUtils.removeDemo(databaseVersion);
            int id = getResources().getIdentifier(version, "string", BuildConfig.APPLICATION_ID);
            if (id != 0) {
                String filename = getString(id);
                if (!application.isDownloading(filename)) {
                    LogUtils.d("will download " + filename + " for " + databaseVersion);
                    application.download(filename, true);
                }
            }
        }
    }

    private void saveOsis() {
        String osis = getCurrentOsis();
        if (!TextUtils.isEmpty(osis)) {
            String book = BibleUtils.getBook(osis);
            String chapter = BibleUtils.getChapter(osis);
            BibleApplication application = (BibleApplication) getApplication();
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                    .putString(OSIS, osis)
                    .putString(book, chapter)
                    .putString("version", application.getVersion()).apply();
        }
    }

    @Override
    protected void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        ReadingFragment currentFragment = getCurrentFragment();
        if (currentFragment != null) {
            intent.putExtra(WEBVIEW_DATA, FileUtils.compress(currentFragment.getBody()));
        }

        startActivityForResult(intent, REQUEST_CODE_SETTINGS);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_SETTINGS) {
            ReadingFragment currentFragment = getCurrentFragment();
            if (currentFragment != null && isChanged(currentFragment.getArguments())) {
                LogUtils.d("settings changed, refresh");
                refresh();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String version = intent.getStringExtra(AbstractReadingActivity.VERSION);
        if (!TextUtils.isEmpty(version) && !ObjectUtils.equals(version, getCurrentVersion())) {
            BibleApplication application = (BibleApplication) getApplication();
            if (application.setVersion(version)) {
                refreshAdapter();
            }
        }
    }

    protected void onReceive(Intent intent) {
        if (Intent.ACTION_CONFIGURATION_CHANGED.equals(intent.getAction())) {
            refreshAdapter();
        }
    }

    @Override
    protected void switchToVersion(String version) {
        BibleApplication application = (BibleApplication) getApplication();
        String currentOsis = getCurrentOsis();
        if (TextUtils.isEmpty(currentOsis) || application.hasChapter(version, currentOsis)) {
            super.switchToVersion(version);
        } else {
            showSwitchToVersion(version);
        }
    }

    private void showSwitchToVersion(String version) {
        final String tag = FRAGMENT_CONFIRM;
        FragmentManager manager = getSupportFragmentManager();
        SwitchVersionConfirmFragment fragment = (SwitchVersionConfirmFragment) manager.findFragmentByTag(tag);
        if (fragment != null) {
            fragment.dismiss();
        }
        fragment = new SwitchVersionConfirmFragment();
        BibleApplication application = (BibleApplication) getApplication();
        fragment.setMessage(getString(R.string.reading_confirm),
                getString(R.string.reading_no_chapter_confirm, application.getFullname(version), mTitle), version);
        fragment.show(manager, tag);
    }

    public void confirmSwitchToVersion(String extra) {
        super.switchToVersion(extra);
    }

    public boolean hasEmailClient() {
        return getPackageManager().resolveActivity(getEmailIntent(), PackageManager.MATCH_DEFAULT_ONLY) != null;
    }

    private Intent getEmailIntent() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setData(Uri.parse("mailto:"));
        return intent;
    }

    public void sendEmail() {
        Intent intent = getEmailIntent();
        fillEmail(intent, false);
        startActivity(intent);
    }

    private String getSubject() {
        Locale locale = Locale.getDefault();
        return getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME +
                "(Android " + locale.getLanguage() + "_" + locale.getCountry() + "-" + Build.VERSION.RELEASE + ")";
    }

    private void fillEmail(Intent intent, boolean fingerprint) {
        intent.putExtra(Intent.EXTRA_SUBJECT, getSubject());
        if (fingerprint) {
            intent.putExtra(Intent.EXTRA_TEXT, Build.FINGERPRINT);
        }
        intent.putExtra(Intent.EXTRA_EMAIL, new String[] {String.valueOf(new char[] {
                'b', 'i', 'b', 'l', 'e', '@', 'j', 'i', 'a', 'n', 'y', 'u', '.', 'i', 'o'
        })});
    }

    public void reportBug() {
        workHandler.sendEmptyMessage(MESSAGE_REPORT_BUG);
    }

    void doReportBug(File path) {
        if (path != null) {
            Uri uri;
            try {
                uri = FileProvider.getUriForFile(this,
                        BuildConfig.APPLICATION_ID + ".fileprovider", path);
            } catch (IllegalArgumentException e) {
                return;
            }

            Intent intent = new Intent(Intent.ACTION_SEND);
            fillEmail(intent, true);
            intent.setDataAndType(null, "message/rfc822");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            }
        }
    }

    private static class Receiver extends BroadcastReceiver {

        private WeakReference<ReadingActivity> mReference;

        public Receiver(ReadingActivity activity) {
            this.mReference = new WeakReference<>(activity);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            ReadingActivity readingActivity = mReference.get();
            if (readingActivity != null) {
                readingActivity.onReceive(intent);
            }
        }

    }

    static class MainHandler extends Handler {

        private final WeakReference<ReadingActivity> mReference;

        public MainHandler(ReadingActivity activity) {
            super(activity.getMainLooper());
            this.mReference = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_REPORT_BUG:
                    ReadingActivity activity = mReference.get();
                    if (activity != null) {
                        activity.doReportBug((File) msg.obj);
                    }
                    break;
            }
        }

    }

    static class WorkHandler extends Handler {

        private static final String[][] LOGS = new String[][] {
                {"system.txt", "-b system"},
                {"events.txt", "-b events am_pss:s"},
                {"crash.txt", "-b crash"},
                {"bible.txt", "-b main *:v"}
        };

        private final Handler mainHandler;
        private final WeakReference<ReadingActivity> mReference;

        WorkHandler(ReadingActivity activity, Handler handler) {
            super(newLooper());
            mainHandler = handler;
            mReference = new WeakReference<>(activity);
        }

        private static Looper newLooper() {
            HandlerThread thread = new HandlerThread("Reading");
            thread.start();
            return thread.getLooper();
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_REPORT_BUG:
                    ReadingActivity activity = mReference.get();
                    File log = null;
                    if (activity != null) {
                        log = fetchLogs(activity);
                    }
                    mainHandler.obtainMessage(MESSAGE_REPORT_BUG, log).sendToTarget();
                    break;
            }
        }

        public static File fetchLogs(Context context) {
            File path = null;
            File dir;
            if (context != null && (dir = context.getExternalFilesDir("logs")) != null) {
                DateFormat df = new SimpleDateFormat("yyyyMMdd.HHmm", Locale.US);
                String date = df.format(Calendar.getInstance().getTime());
                File cacheDir = context.getCacheDir();
                try {
                    for (String[] log : LOGS) {
                        File file = new File(cacheDir, date + "." + log[0]);
                        String command = "/system/bin/logcat -d -v threadtime -f "
                                + file.getPath() + " " + log[1];
                        Runtime.getRuntime().exec(command).waitFor();
                    }
                    Runtime.getRuntime().exec("sync").waitFor();
                    path = zipLog(context, dir, date);
                } catch (IOException | InterruptedException e) {
                    LogUtils.w("Can't get logs", e);
                }
            }
            return path;
        }

        private static File zipLog(Context context, File dir, String date) {
            String[] names = dir.list();
            if (names != null) {
                for (String name : names) {
                    File file = new File(dir, name);
                    if (name.startsWith("logs-v") && file.isFile() && file.delete()) {
                        LogUtils.d("delete file " + file.getName());
                    }
                }
            }
            try {
                File path = new File(dir, "logs-v" + BuildConfig.VERSION_NAME + "-" + date + ".zip");
                try (
                        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(path))
                ) {
                    for (String[] log : LOGS) {
                        zipLog(context, zos, date, log[0]);
                    }
                    File parent = context.getExternalFilesDir(null).getParentFile();
                    names = parent.list();
                    if (names != null) {
                        Arrays.sort(names);
                        for (String name : names) {
                            if (name.startsWith("server.") && name.endsWith(".txt")) {
                                zipLog(zos, new File(parent, name));
                            }
                        }
                    }
                }
                return path;
            } catch (IOException e) {
                LogUtils.w("Can't report bug", e);
                return null;
            }
        }


        private static void zipLog(Context context, ZipOutputStream zos, String date, String path)
                throws IOException {
            File file = new File(context.getCacheDir(), date + "." + path);
            if (!file.exists()) {
                LogUtils.w("Can't find " + file.getPath());
                return;
            }
            zipLog(zos, file);
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }

        private static void zipLog(ZipOutputStream zos, File file) throws IOException {
            ZipEntry zipEntry = new ZipEntry(file.getName());
            zipEntry.setTime(file.lastModified());
            zos.putNextEntry(zipEntry);
            FileUtils.copy(new FileInputStream(file), zos);
            zos.closeEntry();
        }
    }

}
