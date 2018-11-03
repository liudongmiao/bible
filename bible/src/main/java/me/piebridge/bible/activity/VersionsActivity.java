package me.piebridge.bible.activity;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.appcompat.widget.SearchView;
import androidx.cardview.widget.CardView;
import androidx.collection.SimpleArrayMap;
import androidx.fragment.app.FragmentManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import me.piebridge.bible.BibleApplication;
import me.piebridge.bible.R;
import me.piebridge.bible.component.DownloadComponent;
import me.piebridge.bible.fragment.CopyrightFragment;
import me.piebridge.bible.fragment.DeleteVersionConfirmFragment;
import me.piebridge.bible.utils.DeprecationUtils;
import me.piebridge.bible.utils.FileUtils;
import me.piebridge.bible.utils.LogUtils;
import me.piebridge.bible.utils.NumberUtils;
import me.piebridge.bible.utils.ObjectUtils;

public class VersionsActivity extends ToolbarActivity implements SearchView.OnQueryTextListener, SearchView.OnCloseListener,
        View.OnClickListener {

    private static final int CC_UNKNOWN = 0;
    private static final int CC_PUBLIC_DOMAIN = 1;
    private static final int CC_NO_COMMERCIAL = 2;
    private static final int CC_JY_AUTHORIZED = 3;

    private static final long LATER = 250;

    private static final String FRAGMENT_CONFIRM = "fragment-confirm";
    private static final String FRAGMENT_COPYRIGHT = "fragment-copyright";

    private RecyclerView recyclerView;
    private VersionAdapter versionsAdaper;

    private SearchView mSearchView;

    private MainHandler mainHandler;
    private WorkHandler workHandler;

    private static final int ADD_VERSION = 0;
    private static final int DELETE_VERSION = 1;
    private static final int UPDATE_VERSIONS = 2;
    private static final int UPDATE_ACTIONS = 3;
    private static final int ADDED_VERSION = 4;

    private BroadcastReceiver receiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_versions);
        showBack(true);

        versionsAdaper = new VersionAdapter(this);

        recyclerView = findViewById(R.id.recycler);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(linearLayoutManager);
        recyclerView.addItemDecoration(new DividerItemDecoration(this, LinearLayoutManager.VERTICAL));

        mainHandler = new MainHandler(this);
        workHandler = new WorkHandler(this);

        handleIntent(getIntent());

        recyclerView.setAdapter(versionsAdaper);

        try {
            BibleApplication application = (BibleApplication) getApplication();
            versionsAdaper.setVersions(application.getLocalVersions());
        } catch (JSONException | IOException ignore) {
            // do nothing
        }

        workHandler.sendEmptyMessage(UPDATE_VERSIONS);

        receiver = new Receiver(this);
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        Uri uri = intent.getData();
        if (uri != null) {
            LogUtils.d("data uri: " + uri);
            handleUri(uri);
            return;
        }
        uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (uri != null) {
            LogUtils.d("stream uri: " + uri);
            handleUri(uri);
        }
    }

    private void handleUri(Uri uri) {
        String scheme = uri.getScheme();
        if ("file".equals(scheme)) {
            handleFile(uri);
        } else if ("content".equals(scheme)) {
            handleContent(uri);
        }
    }

    private void handleContent(Uri uri) {
        try (
                Cursor cursor = getContentResolver().query(uri, null, null, null, null)
        ) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                String name = cursor.getString(nameIndex);
                LogUtils.d("name: " + name);
                if (DownloadComponent.isBibleData(name)) {
                    try {
                        doHandleContent(uri, name);
                    } catch (IOException e) {
                        LogUtils.w("cannot get " + e + " from content", e);
                    }
                }
            }
        }
    }

    private void doHandleContent(Uri uri, String name) throws IOException {
        ParcelFileDescriptor openFileDescriptor = getContentResolver().openFileDescriptor(uri, "r");
        if (openFileDescriptor != null) {
            FileDescriptor fileDescriptor = openFileDescriptor.getFileDescriptor();
            File file = new File(getExternalFilesDir(null), name);
            try (
                    InputStream is = new BufferedInputStream(new FileInputStream(fileDescriptor));
                    OutputStream os = new FileOutputStream(file)
            ) {
                FileUtils.copy(is, os);
            }
            workHandler.obtainMessage(ADD_VERSION, file.getPath()).sendToTarget();
        }
    }

    private void handleFile(Uri uri) {
        final String path = uri.getPath();
        final String segment = uri.getLastPathSegment();
        if (path != null && DownloadComponent.isBibleData(segment)) {
            workHandler.obtainMessage(ADD_VERSION, path).sendToTarget();
        }
    }

    void checkZip(String path) {
        BibleApplication application = (BibleApplication) getApplication();
        File file = new File(path);
        String version = DownloadComponent.getVersion(file);
        LogUtils.d("file: " + file + ", version: " + version);
        if (!TextUtils.isEmpty(version) && application.addBibleData(file)) {
            application.cancelDownload(file.getName());
            mainHandler.obtainMessage(ADDED_VERSION, version).sendToTarget();
            updateActionsLater();
        }
    }

    void deleteVersion(String version) {
        BibleApplication application = (BibleApplication) getApplication();
        application.deleteVersion(version);
        updateActionsLater();
    }

    public void updateQuery(String query) {
        try {
            versionsAdaper.setQuery(query == null ? null : query.toLowerCase(Locale.US));
        } catch (JSONException ignore) {
            // do nothing
        }
        mSearchView.clearFocus();
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        updateQuery(query);
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.versions, menu);
        mSearchView = (SearchView) menu.findItem(R.id.search).getActionView();
        mSearchView.setOnQueryTextListener(this);
        mSearchView.setOnCloseListener(this);
        mSearchView.setOnSearchClickListener(this);
        return true;
    }

    @Override
    public boolean onClose() {
        updateQuery(null);
        setTitle(getTitle());
        return false;
    }

    @Override
    public void onBackPressed() {
        if (!mSearchView.isIconified()) {
            mSearchView.setIconified(true);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (!mSearchView.isIconified()) {
                    mSearchView.setIconified(true);
                    return true;
                }
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View v) {
        if (v == mSearchView) {
            setTitle(null);
        } else if (v instanceof CardView) {
            View action = v.findViewById(R.id.action);
            VersionItem versionItem = (VersionItem) action.getTag();
            launchVersion(versionItem.code);
        } else if (v instanceof ImageView) {
            VersionItem versionItem = (VersionItem) v.getTag();
            switch (v.getId()) {
                case R.id.action:
                    onClickAction(versionItem);
                    break;
                case R.id.copyright:
                    onClickCopyright(versionItem);
                    break;
                default:
                    break;
            }
        }
    }

    private void onClickCopyright(VersionItem versionItem) {
        CharSequence message;
        if (TextUtils.isEmpty(versionItem.info)) {
            message = getText(R.string.translation_copyright_message);
        } else {
            message = DeprecationUtils.fromHtmlLegacy(versionItem.info);
        }
        showCopyright(versionItem.name, message);
    }

    private void onClickAction(VersionItem versionItem) {
        int action = versionItem.action;
        switch (action) {
            case R.string.translation_install:
            case R.string.translation_update:
                if (canDownload(versionItem.copy)) {
                    downloadVersion(versionItem, action == R.string.translation_update);
                    updateActionsLater();
                } else {
                    onClickCopyright(versionItem);
                }
                break;
            case R.string.translation_cancel:
                BibleApplication application = (BibleApplication) getApplication();
                application.cancelDownload(versionItem.filename());
                updateActionsLater();
                break;
            case R.string.translation_uninstall:
                showDelete(versionItem);
                break;
            default:
                break;
        }
    }

    private void launchVersion(String version) {
        Intent intent = super.getSupportParentActivityIntent();
        if (intent != null) {
            intent.putExtra(AbstractReadingActivity.VERSION, version);
            startActivity(intent);
            finish();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, intentFilter);
    }

    @Override
    protected void onStop() {
        mainHandler.removeCallbacksAndMessages(null);
        workHandler.removeCallbacksAndMessages(null);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver);
        super.onStop();
    }

    @WorkerThread
    void updateVersions() {
        try {
            BibleApplication application = (BibleApplication) getApplication();
            String versions = application.getRemoteVersions();
            if (!TextUtils.isEmpty(versions)) {
                mainHandler.obtainMessage(UPDATE_VERSIONS, versions).sendToTarget();
            }
        } catch (IOException e) {
            LogUtils.w("cannot update versions", e);
        }
    }

    @MainThread
    void updateVersions(String versions) {
        try {
            if (versionsAdaper.setVersions(versions)) {
                Snackbar.make(findViewById(R.id.coordinator), R.string.translation_metadata_updated,
                        Snackbar.LENGTH_LONG).show();
            }
        } catch (JSONException ignore) {
            // do nothing
        }
    }

    @MainThread
    void updateActions() {
        versionsAdaper.updateActions();
    }

    String getVersionDate(VersionItem item) {
        BibleApplication application = (BibleApplication) getApplication();
        return application.getDate(item.code);
    }

    void downloadVersion(VersionItem item, boolean force) {
        BibleApplication application = (BibleApplication) getApplication();
        application.download(item.filename(), force);
        updateActionsLater();
    }

    private void updateActionsLater() {
        mainHandler.removeMessages(UPDATE_ACTIONS);
        mainHandler.sendEmptyMessageDelayed(UPDATE_ACTIONS, LATER);
    }

    boolean isDownloading(VersionItem item) {
        BibleApplication application = (BibleApplication) getApplication();
        return application.isDownloading(item.filename());
    }

    void onReceive(Intent intent) {
        LogUtils.d("local intent: " + intent + ", extras: " + intent.getExtras());
        String filename = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (filename != null) {
            File file = new File(getExternalCacheDir(), filename);
            if (file.exists()) {
                workHandler.obtainMessage(ADD_VERSION, file.getPath()).sendToTarget();
                return;
            }
        }
        updateActionsLater();
    }

    public void showDelete(VersionItem item) {
        final String tag = FRAGMENT_CONFIRM;
        FragmentManager manager = getSupportFragmentManager();
        DeleteVersionConfirmFragment fragment = (DeleteVersionConfirmFragment) manager.findFragmentByTag(tag);
        if (fragment != null) {
            fragment.dismiss();
        }
        fragment = new DeleteVersionConfirmFragment();
        fragment.setMessage(getString(R.string.reading_confirm),
                getString(R.string.translation_delete_confirm, item.name), item.code);
        fragment.show(manager, tag);
    }

    public void confirmDelete(String extra) {
        workHandler.obtainMessage(DELETE_VERSION, extra).sendToTarget();
    }

    void onAddedVersion(String version) {
        BibleApplication application = (BibleApplication) getApplication();
        String fullname = application.getFullname(version);
        if (!TextUtils.isEmpty(fullname)) {
            String message = getString(R.string.translation_added, fullname);
            Snackbar.make(findViewById(R.id.coordinator), message, Snackbar.LENGTH_LONG).show();
        }
    }

    private void showCopyright(CharSequence title, CharSequence message) {
        final String tag = FRAGMENT_COPYRIGHT;
        FragmentManager manager = getSupportFragmentManager();
        CopyrightFragment fragment = (CopyrightFragment) manager.findFragmentByTag(tag);
        if (fragment != null) {
            fragment.dismiss();
        }
        fragment = new CopyrightFragment();
        fragment.setMessage(title, message);
        fragment.show(manager, tag);
    }

    static boolean canDownload(int copy) {
        switch (copy) {
            case CC_PUBLIC_DOMAIN:
            case CC_NO_COMMERCIAL:
                return true;
            case CC_UNKNOWN:
            default:
                return false;
        }
    }

    static class Receiver extends BroadcastReceiver {

        private final WeakReference<VersionsActivity> mReference;

        public Receiver(VersionsActivity activity) {
            this.mReference = new WeakReference<>(activity);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            VersionsActivity activity = mReference.get();
            if (activity != null) {
                activity.onReceive(intent);
            }
        }

    }

    static class MainHandler extends Handler {

        private final WeakReference<VersionsActivity> mReference;

        public MainHandler(VersionsActivity activity) {
            super(activity.getMainLooper());
            this.mReference = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            VersionsActivity activity = mReference.get();
            if (activity == null) {
                return;
            }
            switch (msg.what) {
                case UPDATE_VERSIONS:
                    activity.updateVersions((String) msg.obj);
                    removeMessages(UPDATE_VERSIONS);
                    break;
                case UPDATE_ACTIONS:
                    activity.updateActions();
                    removeMessages(UPDATE_ACTIONS);
                    break;
                case ADDED_VERSION:
                    activity.onAddedVersion((String) msg.obj);
                    break;
                default:
                    break;
            }
        }

    }

    static class WorkHandler extends Handler {

        private final WeakReference<VersionsActivity> mReference;

        public WorkHandler(VersionsActivity activity) {
            super(newLooper());
            this.mReference = new WeakReference<>(activity);
        }

        private static Looper newLooper() {
            HandlerThread thread = new HandlerThread("Update");
            thread.start();
            return thread.getLooper();
        }

        @Override
        public void handleMessage(Message msg) {
            VersionsActivity activity = mReference.get();
            if (activity == null) {
                return;
            }
            switch (msg.what) {
                case ADD_VERSION:
                    activity.checkZip((String) msg.obj);
                    break;
                case DELETE_VERSION:
                    activity.deleteVersion((String) msg.obj);
                    break;
                case UPDATE_VERSIONS:
                    activity.updateVersions();
                    break;
                default:
                    break;
            }
        }

    }

    private static class VersionAdapter extends RecyclerView.Adapter {

        private static final int TYPE_VERSION = 0;

        private static final int TYPE_COUNT = 1;

        private String mJson;

        private String mQuery;

        private final List<VersionItem> mItems;

        private final WeakReference<VersionsActivity> mReference;

        public VersionAdapter(VersionsActivity activity) {
            this.mItems = new ArrayList<>();
            this.mReference = new WeakReference<>(activity);
        }

        public boolean setVersions(String json) throws JSONException {
            if (!ObjectUtils.equals(this.mJson, json)) {
                this.mJson = json;
                prepareData();
                return true;
            } else {
                return false;
            }
        }

        public void setQuery(String query) throws JSONException {
            if (!ObjectUtils.equals(this.mQuery, query)) {
                this.mQuery = query;
                prepareData();
            }
        }

        public synchronized void updateActions() {
            VersionsActivity activity = mReference.get();
            boolean changed = false;
            for (VersionItem item : mItems) {
                if (item.isVersion()) {
                    int action = getAction(activity, item);
                    if (action != item.action) {
                        LogUtils.d("item: " + item.code + ", old: " + item.action + ", new: " + action);
                        item.action = action;
                        item.shouldUpdate = true;
                        changed = true;
                    }
                }
            }
            if (changed) {
                DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffCallback(mItems, mItems));
                result.dispatchUpdatesTo(this);
            }
        }

        private synchronized void prepareData() throws JSONException {
            List<VersionItem> items = new ArrayList<>();

            JSONObject jsons = new JSONObject(mJson);
            JSONArray jsonVersions = jsons.getJSONArray("versions");
            JSONObject jsonLanguages = jsons.getJSONObject("languages");

            int length = jsonVersions.length();
            SimpleArrayMap<String, Integer> counter = new SimpleArrayMap<>();

            VersionsActivity activity = mReference.get();
            for (int i = 0; i < length; ++i) {
                JSONObject version = jsonVersions.getJSONObject(i);
                VersionItem item = new VersionItem();
                item.code = version.optString("code");
                item.date = version.optString("date");
                item.lang = version.optString("lang");
                item.name = version.optString("name");
                item.copy = formatCopy(version);
                item.info = formatInfo(version);
                item.action = getAction(activity, item);
                if (item.action == R.string.translation_update && canDownload(item.copy)) {
                    activity.downloadVersion(item, true);
                    item.action = R.string.translation_cancel;
                }

                String lang = jsonLanguages.optString(item.lang);
                if (accept(item, lang)) {
                    items.add(item);
                    int index = counter.indexOfKey(item.lang);
                    if (index >= 0) {
                        counter.put(item.lang, counter.valueAt(index) + 1);
                    } else {
                        counter.put(item.lang, 1);
                    }
                }
            }

            int size = counter.size();
            for (int i = 0; i < size; ++i) {
                String lang = counter.keyAt(i);
                int value = counter.valueAt(i);
                VersionItem item = new VersionItem();
                item.lang = lang;
                item.name = jsonLanguages.optString(item.lang);
                item.code = Integer.toString(value);
                items.add(item);
            }
            Collections.sort(items, new Comparator<VersionItem>() {

                @Override
                public int compare(VersionItem o1, VersionItem o2) {
                    if (ObjectUtils.equals(o1.lang, o2.lang)) {
                        return Collator.getInstance().compare(o1.code, o2.code);
                    } else {
                        final Locale locale = Locale.getDefault();
                        final String lang = locale.getLanguage().toLowerCase(Locale.US);
                        final String langFull = lang + "-" + locale.getCountry().toLowerCase(Locale.US);
                        if (ObjectUtils.equals(langFull, o1.lang)) {
                            return -1;
                        } else if (ObjectUtils.equals(langFull, o2.lang)) {
                            return 1;
                        }

                        if (o1.lang.startsWith(lang)) {
                            return -1;
                        } else if (o2.lang.startsWith(lang)) {
                            return 1;
                        }

                        if (o1.lang.startsWith("en")) {
                            return -1;
                        } else if (o2.lang.startsWith("en")) {
                            return 1;
                        }

                        return Collator.getInstance().compare(o1.lang, o2.lang);
                    }
                }

            });

            if (mItems.isEmpty()) {
                mItems.addAll(items);
                notifyItemRangeInserted(0, items.size());
            } else {
                DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffCallback(mItems, items));
                mItems.clear();
                mItems.addAll(items);
                result.dispatchUpdatesTo(this);
            }
        }

        private int formatCopy(JSONObject version) {
            String copy = version.optString("copy");
            if (TextUtils.isEmpty(copy)) {
                return CC_UNKNOWN;
            }
            switch (copy) {
                case "nc":
                    return CC_NO_COMMERCIAL;
                case "pd":
                    return CC_PUBLIC_DOMAIN;
                default:
                    return CC_UNKNOWN;
            }
        }

        private String formatInfo(JSONObject version) {
            String info = version.optString("info");
            if (info == null) {
                return null;
            }
            return info.intern();
        }

        private int getAction(VersionsActivity activity, VersionItem item) {
            String date = activity.getVersionDate(item);
            if (activity.isDownloading(item)) {
                return R.string.translation_cancel;
            } else if (TextUtils.isEmpty(date)) {
                return R.string.translation_install;
            } else if (isNewer(item.date, date)) {
                LogUtils.d("version: " + item.code + ", current: " + date + ", latest: " + item.date);
                return R.string.translation_update;
            } else {
                return R.string.translation_uninstall;
            }
        }

        private boolean isNewer(String latest, String current) {
            if (TextUtils.isDigitsOnly(latest) && TextUtils.isDigitsOnly(current)) {
                return NumberUtils.parseInt(latest) > NumberUtils.parseInt(current);
            } else {
                return false;
            }
        }

        private boolean accept(VersionItem item, String lang) {
            if (TextUtils.isEmpty(mQuery)) {
                return true;
            }
            if (ObjectUtils.equals("nc", mQuery) && item.copy == CC_NO_COMMERCIAL) {
                return true;
            }
            if (ObjectUtils.equals("pd", mQuery) && item.copy == CC_PUBLIC_DOMAIN) {
                return true;
            }
            if (ObjectUtils.equals("jy", mQuery) && item.copy == CC_JY_AUTHORIZED) {
                return true;
            }
            if (item.code.toLowerCase(Locale.US).contains(mQuery)) {
                return true;
            }
            if (item.name.toLowerCase(Locale.US).contains(mQuery)) {
                return true;
            }
            if (lang.toLowerCase(Locale.US).contains(mQuery)) {
                return true;
            }
            if ("uninstall".equals(mQuery) && item.action == R.string.translation_uninstall) {
                return true;
            }
            if (ObjectUtils.equals(mQuery, mReference.get().getString(item.action).toLowerCase(Locale.US))) {
                return true;
            }
            return false;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            CardView cardView;
            switch (viewType) {
                case TYPE_COUNT:
                    View view = inflater.inflate(R.layout.item, parent, false);
                    return new CountViewHolder(view);
                case TYPE_VERSION:
                    cardView = (CardView) inflater.inflate(R.layout.item_version, parent, false);
                    VersionViewHolder holder = new VersionViewHolder(cardView);
                    VersionsActivity activity = mReference.get();
                    holder.cardView.setOnClickListener(activity);
                    holder.actionView.setOnClickListener(activity);
                    holder.copyrightView.setOnClickListener(activity);
                    return holder;
                default:
                    throw new UnsupportedOperationException();
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof CountViewHolder) {
                bindCountViewHolder((CountViewHolder) holder, position);
            } else if (holder instanceof VersionViewHolder) {
                bindVersionViewHolder((VersionViewHolder) holder, position);
            }
        }

        private void bindVersionViewHolder(VersionViewHolder holder, int position) {
            VersionItem versionItem = mItems.get(position);
            holder.codeView.setText(versionItem.code);
            holder.nameView.setText(versionItem.name);
            VersionsActivity activity = mReference.get();
            if (activity != null) {
                holder.actionView.setContentDescription(activity.getString(versionItem.action));
            } else {
                holder.actionView.setContentDescription(null);
            }
            holder.actionView.setTag(versionItem);
            holder.copyrightView.setTag(versionItem);
            switch (versionItem.action) {
                case R.string.translation_install:
                    holder.actionView.setImageResource(R.drawable.ic_file_download_black_24dp);
                    holder.cardView.setEnabled(false);
                    break;
                case R.string.translation_cancel:
                    holder.actionView.setImageResource(R.drawable.ic_cancel_black_24dp);
                    holder.cardView.setEnabled(false);
                    break;
                case R.string.translation_uninstall:
                    holder.actionView.setImageResource(R.drawable.ic_delete_black_24dp);
                    holder.cardView.setEnabled(true);
                    break;
                case R.string.translation_update:
                    holder.actionView.setImageResource(R.drawable.ic_system_update_black_24dp);
                    holder.cardView.setEnabled(true);
                    break;
                default:
                    break;
            }
            switch (versionItem.copy) {
                case CC_NO_COMMERCIAL:
                    holder.copyrightView.setImageResource(R.drawable.ic_copyright_nc);
                    break;
                case CC_PUBLIC_DOMAIN:
                    holder.copyrightView.setImageResource(R.drawable.ic_copyright_pd);
                    break;
                case CC_JY_AUTHORIZED:
                    holder.copyrightView.setImageResource(R.drawable.ic_copyright_black_24dp);
                    break;
                case CC_UNKNOWN:
                default:
                    holder.copyrightView.setImageResource(R.drawable.ic_copyright_cc);
            }
        }

        private void bindCountViewHolder(CountViewHolder holder, int position) {
            VersionItem versionItem = mItems.get(position);
            holder.countView.setText(versionItem.code);
            holder.typeView.setText(versionItem.name);
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        @Override
        public int getItemViewType(int position) {
            VersionItem item = mItems.get(position);
            if (item.isVersion()) {
                return TYPE_VERSION;
            } else {
                return TYPE_COUNT;
            }
        }

    }

    private static class VersionItem {

        boolean shouldUpdate;

        String code;

        String date;

        String lang;

        String name;

        int action;

        int copy;

        String info;

        @Override
        public int hashCode() {
            return (code + "-" + lang + "-" + name).hashCode();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other instanceof VersionItem) {
                return isSame((VersionItem) other);
            } else {
                return false;
            }
        }

        private boolean isSame(VersionItem other) {
            return ObjectUtils.equals(code, other.code)
                    && ObjectUtils.equals(lang, other.lang)
                    && ObjectUtils.equals(name, other.name);
        }

        public boolean isVersion() {
            return !TextUtils.isDigitsOnly(code);
        }

        public String filename() {
            return String.format("bibledata-%s-%s.zip", lang, code);
        }
    }

    private static class DiffCallback extends DiffUtil.Callback {

        private final List<VersionItem> mOldList;

        private final List<VersionItem> mNewList;

        DiffCallback(List<VersionItem> oldList, List<VersionItem> newList) {
            mOldList = oldList;
            mNewList = newList;
        }

        @Override
        public int getOldListSize() {
            return mOldList.size();
        }

        @Override
        public int getNewListSize() {
            return mNewList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            VersionItem oldItem = mOldList.get(oldItemPosition);
            VersionItem newItem = mNewList.get(newItemPosition);
            return oldItem.equals(newItem);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            VersionItem oldItem = mOldList.get(oldItemPosition);
            VersionItem newItem = mNewList.get(newItemPosition);
            try {
                return oldItem.equals(newItem) && !oldItem.shouldUpdate;
            } finally {
                oldItem.shouldUpdate = false;
            }
        }

    }

    private static class VersionViewHolder extends RecyclerView.ViewHolder {

        final CardView cardView;

        final ImageView copyrightView;

        final ImageView actionView;

        final TextView codeView;

        final TextView nameView;

        public VersionViewHolder(CardView view) {
            super(view);
            this.cardView = view;
            this.copyrightView = view.findViewById(R.id.copyright);
            this.actionView = view.findViewById(R.id.action);
            this.codeView = view.findViewById(R.id.code);
            this.nameView = view.findViewById(R.id.name);
        }

    }

}
