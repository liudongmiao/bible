package me.piebridge.bible.activity;

import android.content.Context;
import android.os.AsyncTask;

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

import me.piebridge.bible.BuildConfig;
import me.piebridge.bible.utils.FileUtils;
import me.piebridge.bible.utils.LogUtils;

/**
 * Created by thom on 2018/11/8.
 */
public class ReportAsyncTask extends AsyncTask<Void, Void, File> {

    private static final String[][] LOGS = new String[][] {
            {"system.txt", "-b system"},
            {"events.txt", "-b events am_pss:s"},
            {"crash.txt", "-b crash"},
            {"bible.txt", "-b main *:v"}
    };

    private final WeakReference<Context> mReference;

    public ReportAsyncTask(Context context) {
        this.mReference = new WeakReference<>(context);
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

    @Override
    protected File doInBackground(Void... arguments) {
        Context context = mReference.get();
        if (context != null) {
            return fetchLogs(context);
        } else {
            return null;
        }
    }

    @Override
    protected void onPostExecute(File file) {
        Context context = mReference.get();
        if (file != null && context instanceof Report) {
            ((Report) context).reportBug(file);
        }
    }

    interface Report {
        void reportBug(File file);
    }

}
