package me.piebridge.bible;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.drawable.AnimationDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.emilsjolander.components.stickylistheaders.StickyListHeadersAdapter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

public class Versions extends Activity {

    private static long mtime = 0;

    private Bible bible;
    private ListView list;
    private EditText query;
    private ImageView refresh;
    private SimpleAdapter adapter;
    private List<String> languages = new ArrayList<>();
    private List<String> names = new ArrayList<>();
    private List<Map<String, String>> data = new ArrayList<>();
    private List<Map<String, String>> filtered = new ArrayList<>();
    private List<Map<String, String>> updated = new ArrayList<>();
    private List<Map<String, String>> matched = new ArrayList<>();
    private List<Map<String, String>> halfmatched = new ArrayList<>();
    private List<HashMap<String, String>> versions = new ArrayList<>();
    private HashMap<String, String> request = new HashMap<>();

    public static final int STOP = 0;
    public static final int START = 1;
    public static final int DELETE = 2;
    public static final int DELETED = 3;
    public static final int COMPLETE = 4;
    public static final int CHECKZIP = 5;

    public static final String CHECKVERSION = "checkversion";
    public static final String TAG = "me.piebridge.bible$Versions";

    private static Handler resume = null;
    private static final Map<String, Integer> completed = new HashMap<>();
    private static final Map<String, String> queue = new HashMap<>();

    private boolean checkversion;
    private boolean showing = false;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.versions);
        bible = Bible.getInstance(this);

        list = (ListView) findViewById(android.R.id.list);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                @SuppressWarnings("unchecked")
                Map<String, String> map = (Map<String, String>) adapter.getItem(position);
                TextView action = (TextView) view.findViewById(R.id.action);
                clickVersion(action, map, false);
            }

        });

        query = (EditText) findViewById(R.id.query);
        query.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int before, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int after) {
                filtering = true;
                if (adapter != null) {
                    adapter.getFilter().filter(s);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }

        });

        refresh = (ImageView) findViewById(R.id.refresh);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handler.sendEmptyMessage(START);
            }
        });

        if (request.size() == 0) {
            String none = "none";
            request.put("lang", none);
            request.put("code", getString(R.string.request_other_version));
            request.put("name", getString(R.string.request_version_message));
            languages.add(request.get("lang"));
            names.add(getString(R.string.not_found));
        }

        if (queue.size() == 0) {
            readQueue();
        }
        Intent intent = getIntent();
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri uri = intent.getData();
            final String path = uri.getLastPathSegment();
            if ("file".equals(uri.getScheme()) && path != null && path.startsWith("bibledata")) {
                handler.sendMessage(handler.obtainMessage(CHECKZIP, new File(uri.getPath())));
            }
        }
    }

    public static final String QUEUE = "download_queue";

    @SuppressLint("InlinedApi")
    private void readQueue() {
        SharedPreferences sp = getSharedPreferences(QUEUE, Context.MODE_PRIVATE);
        Map<String, ?> map = sp.getAll();
        if (map == null || map.size() == 0) {
            return;
        }
        for (Entry<String, ?> entry : map.entrySet()) {
            String key = entry.getKey();
            long value;
            try {
                value = Long.parseLong(String.valueOf(entry.getValue()));
            } catch (NumberFormatException e) {
                continue;
            }
            DownloadInfo info = DownloadInfo.getDownloadInfo(this, value);
            if (info == null) {
                continue;
            }
            switch (info.status) {
                case DownloadManager.STATUS_PAUSED:
                case DownloadManager.STATUS_PENDING:
                case DownloadManager.STATUS_RUNNING:
                    queue.put(key, String.valueOf(value));
                    queue.put(String.valueOf(value), key);
                    break;
                case DownloadManager.STATUS_FAILED:
                case DownloadManager.STATUS_SUCCESSFUL:
                default:
                    break;
            }
        }
    }

    private void saveQueue() {
        final Editor editor = getSharedPreferences(QUEUE, Context.MODE_PRIVATE).edit();
        editor.clear();
        for (Entry<String, String> entry : queue.entrySet()) {
            String key = entry.getKey();
            if (!TextUtils.isEmpty(key) && TextUtils.isDigitsOnly(key)) {
                editor.putLong(entry.getValue(), Long.parseLong(key));
            }
        }
        editor.apply();
    }

    List<HashMap<String, String>> parseVersions(List<HashMap<String, String>> list, String string) {
        Context context = bible.getContext();
        try {
            JSONObject jsons = new JSONObject(string);
            List<String> installed = bible.get(Bible.TYPE.VERSIONPATH);
            JSONArray jsonVersions = jsons.getJSONArray("versions");
            JSONObject jsonLanguages = null;
            try {
                jsonLanguages = jsons.getJSONObject("languages");
            } catch (JSONException e) {
                // do nothing
            }
            for (int i = 0; i < jsonVersions.length(); ++i) {
                JSONObject version = jsonVersions.getJSONObject(i);
                HashMap<String, String> map = new HashMap<>();
                String action;
                String code = version.getString("code");
                String lang = version.getString("lang");
                String date = version.getString("date");
                map.put("lang", lang);
                map.put("code", code.toUpperCase(Locale.US));
                map.put("name", version.getString("name"));
                String path = null;
                if (date != null && date.length() > 0) {
                    path = String.format("bibledata-%s-%s.zip", lang, code);
                }
                map.put("path", path);
                if (!installed.contains(code)) {
                    action = context.getString(path == null ? R.string.request : R.string.install);
                    String cancel = context.getString(R.string.cancel_install);
                    if (queue.containsKey(map.get("code"))) {
                        map.put("text", cancel);
                    } else {
                        map.put("text", action);
                    }
                } else if (canUpdate(date, code)) {
                    action = context.getString(R.string.update);
                    map.put("text", action);
                } else {
                    action = context.getString(R.string.uninstall);
                    map.put("text", action);
                }
                map.put("action", action);
                list.add(map);
                if (!languages.contains(lang)) {
                    languages.add(lang);
                    String name = lang;
                    if (jsonLanguages != null) {
                        try {
                            name = jsonLanguages.getString(lang);
                        } catch (JSONException e) {
                        }
                    }
                    names.add(name);
                }
            }
        } catch (JSONException e) {
        }
        return list;
    }

    private boolean canUpdate(String date, String code) {
        if (bible != null) {
            String current = bible.getDate(code);
            try {
                return Integer.parseInt(date) > Integer.parseInt(current);
            } catch (NumberFormatException e) {
            }
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        resume = handler;
        String json;
        try {
            json = bible.getLocalVersions();
        } catch (IOException e) {
            json = "{versions:[]}";
        }
        setVersions(json);
        if (completed.size() > 0) {
            handler.sendEmptyMessage(COMPLETE);
        }
        if (!showing) {
            final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
            Map<String, ?> map = sp.getAll();
            if (map.containsKey(CHECKVERSION)) {
                checkversion = Boolean.valueOf(String.valueOf(map.get(CHECKVERSION)));
            } else {
                areYouSure(getString(R.string.checkversion),
                        getString(R.string.checkversion_detail, getString(android.R.string.yes)),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                checkversion = true;
                                showing = false;
                                sp.edit().putBoolean(CHECKVERSION, true).apply();
                                handler.sendEmptyMessage(START);
                            }
                        }, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                checkversion = false;
                                showing = false;
                                sp.edit().putBoolean(CHECKVERSION, false).apply();
                            }
                        });
                showing = true;
            }
        }
        if (!showing && Boolean.TRUE.equals(checkversion)) {
            long now = System.currentTimeMillis() / 1000;
            if (mtime == 0 || mtime - now > 86400) {
                mtime = now;
                handler.sendEmptyMessageDelayed(START, 400);
            }
        }
    }

    boolean refreshing = false;

    private Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(final Message msg) {
            switch (msg.what) {
                case STOP:
                    if (msg.obj != null) {
                        setVersions((String) msg.obj);
                    }
                    refreshing = false;
                    ((AnimationDrawable) refresh.getDrawable()).stop();
                    return false;
                case START:
                    Log.d(TAG, "start refresh versions");
                    refreshVersions();
                    refreshing = true;
                    ((AnimationDrawable) refresh.getDrawable()).start();
                    return false;
                case DELETE:
                    if (bible == null) {
                        return false;
                    }
                    final String code = (String) msg.obj;
                    Log.d(TAG, "delete " + code);
                    bible.deleteVersion(code);
                    handler.sendMessage(handler.obtainMessage(DELETED, code));
                    return false;
                case DELETED:
                    Log.d(TAG, "deleted " + msg.obj);
                    final String install = getString(R.string.install);
                    synchronized (versions) {
                        for (Map<String, String> map : versions) {
                            if (map.get("code").equalsIgnoreCase((String) msg.obj)) {
                                map.put("text", install);
                                map.put("action", install);
                            }
                        }
                    }
                    filterVersion(query.getText().toString());
                    adapter.notifyDataSetChanged();
                    return false;
                case COMPLETE:
                    String uninstall = getString(R.string.uninstall);
                    synchronized (versions) {
                        for (Map<String, String> map : versions) {
                            if (completed.size() == 0) {
                                break;
                            }
                            synchronized (completed) {
                                String version = map.get("code").toUpperCase(Locale.US);
                                if (completed.containsKey(version)) {
                                    String action;
                                    if (DownloadManager.STATUS_SUCCESSFUL != completed.get(version)) {
                                        action = getString(R.string.install);
                                    } else {
                                        action = uninstall;
                                    }
                                    map.put("text", action);
                                    map.put("action", action);
                                    completed.remove(version);
                                }
                            }
                        }
                    }
                    filterVersion(query.getText().toString());
                    adapter.notifyDataSetChanged();
                    return false;
                case CHECKZIP:
                    final File path = (File) msg.obj;
                    bible.checkZipPath(path);
                    bible.checkBibleData(false, new Runnable() {
                        @Override
                        public void run() {
                            onDownloadComplete(path.getPath());
                        }
                    });
                    return false;
                default:
                    return false;
            }
        }
    });

    void refreshVersions() {
        if (!refreshing) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String json = null;
                    try {
                        json = bible.getRemoteVersions();
                    } catch (Exception e) {
                        Log.e(TAG, "", e);
                    } finally {
                        handler.sendMessage(handler.obtainMessage(STOP, json));
                    }
                }
            }).start();
        }
    }

    void setVersions(String json) {
        if (json == null || json.length() == 0) {
            return;
        }
        synchronized (versions) {
            versions.clear();
            parseVersions(versions, json);
            versions.add(request);
        }
        if (adapter == null) {
            String[] from = {"code", "name", "text", "lang"};
            int[] to = {R.id.code, R.id.name, R.id.action, 0};
            adapter = new Adapter(this, data, R.layout.version, from, to);
            list.setAdapter(adapter);
        }
        adapter.getFilter().filter(query.getText().toString());
    }

    @Override
    protected void onPause() {
        super.onPause();
        resume = null;
    }

    @Override
    protected void onDestroy() {
        saveQueue();
        super.onDestroy();
    }

    public static void onDownloadComplete(DownloadInfo info) {
        long id = info.id;
        String code = queue.get(String.valueOf(id));
        Log.d(TAG, "download complete: " + code);
        if (code != null) {
            synchronized (queue) {
                queue.remove(String.valueOf(id));
                queue.remove(code);
            }
            synchronized (completed) {
                completed.put(code.toUpperCase(Locale.US), info.status);
            }
            if (resume != null) {
                resume.sendEmptyMessage(COMPLETE);
            }
        }
    }

    @SuppressLint("InlinedApi")
    private void onDownloadComplete(String path) {
        if (path == null || !path.endsWith(".zip")) {
            return;
        }
        int sep = path.lastIndexOf("-");
        if (sep == -1) {
            return;
        }
        String code = path.substring(sep + 1, path.length() - 4);
        Log.d(TAG, "download complete: " + code);
        synchronized (queue) {
            String id = queue.get(code);
            if (id != null) {
                queue.remove(id);
            }
            queue.remove(code);
        }
        synchronized (completed) {
            completed.put(code.toUpperCase(Locale.US), DownloadManager.STATUS_SUCCESSFUL);
        }
        if (resume != null) {
            resume.sendEmptyMessage(COMPLETE);
        }
    }

    void clickVersion(final TextView view, final Map<String, String> map, final boolean button) {
        final String code = (String) map.get("code");
        final String name = (String) map.get("name");
        final String action = (String) map.get("action");
        final String text = view.getText().toString();
        if (action == null) {
            bible.email(this);
        }
        if (text.equals(getString(R.string.request))) {
            String content = code.toUpperCase(Locale.US) + ", " + name;
            bible.email(this, content);
        } else if (text.equals(getString(R.string.install)) || text.equals(getString(R.string.update))) {
            download(map);
        } else if (text.equals(getString(R.string.cancel_install))) {
            if (queue.containsKey(code) && queue.get(code) != null) {
                long id = Long.parseLong(queue.get(code));
                if (id > 0) {
                    bible.cancel(id);
                    synchronized (queue) {
                        queue.remove(String.valueOf(id));
                        queue.remove(code);
                    }
                }
            }
            map.put("text", action);
            adapter.notifyDataSetChanged();
        } else if (text.equals(getString(R.string.uninstall))) {
            if (!button) {
                bible.setVersion(code.toLowerCase(Locale.US));
                Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
                intent.putExtra("version", code.toLowerCase(Locale.US));
                startActivity(intent);
            } else {
                areYouSure(getString(R.string.deleteversion, code.toUpperCase(Locale.US)),
                        getString(R.string.deleteversiondetail, name),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                handler.sendMessage(handler.obtainMessage(DELETE, code));
                            }
                        });
            }
        }
    }

    void areYouSure(String title, String message, DialogInterface.OnClickListener handler) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton(android.R.string.yes, handler).setNegativeButton(android.R.string.no, null).create()
                .show();
    }

    void areYouSure(String title, String message, DialogInterface.OnClickListener positive, DialogInterface.OnClickListener negative) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton(android.R.string.yes, positive)
                .setNegativeButton(android.R.string.no, negative)
                .setCancelable(false)
                .create().show();
    }

    class Adapter extends SimpleAdapter implements StickyListHeadersAdapter {

        private LayoutInflater mInflater;

        public Adapter(Context context, List<? extends Map<String, ?>> data, int resource, String[] from, int[] to) {
            super(context, data, resource, from, to);
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            final TextView action = (TextView) view.findViewById(R.id.action);
            if (action != null) {
                action.setTag(position);
                action.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        int position = (Integer) view.getTag();
                        @SuppressWarnings("unchecked")
                        Map<String, String> map = (Map<String, String>) getItem(position);
                        if (map != null) {
                            clickVersion((TextView) view, map, true);
                        }
                    }
                });
                @SuppressWarnings("unchecked")
                Map<String, String> map = (Map<String, String>) getItem(position);
                if (map == null || map.get("action") == null) {
                    action.setVisibility(View.GONE);
                } else {
                    action.setVisibility(View.VISIBLE);
                }
            }
            return view;
        }

        Filter mFilter;

        public Filter getFilter() {
            if (mFilter == null) {
                mFilter = new SimpleFilter();
            }
            return mFilter;
        }

        class SimpleFilter extends Filter {
            @Override
            protected FilterResults performFiltering(CharSequence prefix) {
                FilterResults results = new FilterResults();
                String filter = null;
                if (prefix != null && prefix.length() > 0) {
                    filter = prefix.toString().toLowerCase(Locale.US);
                }
                filterVersion(filter);
                results.count = data.size();
                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                if (results.count > 0) {
                    notifyDataSetChanged();
                } else {
                    notifyDataSetInvalidated();
                }
            }
        }

        @Override
        public View getHeaderView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.sticky, parent, false);
            }
            long section = getHeaderId(position);
            String name = null;
            if (section != -1) {
                try {
                    name = names.get((int) section);
                } catch (IndexOutOfBoundsException e) {
                    // do nothing, shouldn't happened
                }
            }
            if (name == null) {
                @SuppressWarnings("unchecked")
                Map<String, String> map = (Map<String, String>) getItem(position);
                name = map.get("lang");
                if (name == null) {
                    name = request.get("name");
                }
            }
            TextView tv = (TextView) convertView.findViewById(android.R.id.title);
            tv.setText(name.toUpperCase(Locale.US));
            return convertView;
        }

        @Override
        public long getHeaderId(int position) {
            @SuppressWarnings("unchecked")
            Map<String, String> map = (Map<String, String>) getItem(position);
            String language = map.get("lang");
            if (language == null) {
                language = request.get("lang");
            }
            return languages.indexOf(language);
        }
    }

    volatile boolean filtering = false;

    void filterVersion(String filter) {
        final Locale locale = Locale.getDefault();
        final String lang = locale.getLanguage().toLowerCase(Locale.US);
        final String fullname = lang + "-" + locale.getCountry().toLowerCase(Locale.US);
        filtering = false;
        Log.d(TAG, "filtering Version: " + filter);
        synchronized (versions) {
            updated.clear();
            matched.clear();
            halfmatched.clear();
            filtered.clear();
            for (HashMap<String, String> map : versions) {
                if (filtering) {
                    filtering = false;
                    return;
                }
                String language = map.get("lang");
                List<Map<String, String>> list;
                if (fullname.equals(language)) {
                    list = matched;
                } else if (language.startsWith(lang)) {
                    list = halfmatched;
                } else {
                    list = filtered;
                }
                if (filter == null || map.get("action") == null) {
                    list.add(map);
                    addIfUpdated(updated, map);
                } else {
                    int index = languages.indexOf(language);
                    if (index != -1 && names.size() > index) {
                        String name = names.get(index);
                        if (name.toLowerCase(Locale.US).contains(filter)) {
                            list.add(map);
                            addIfUpdated(updated, map);
                            continue;
                        }
                    }
                    for (String value : map.values()) {
                        if (filtering) {
                            filtering = false;
                            return;
                        }
                        if (value.toLowerCase(Locale.US).contains(filter)) {
                            list.add(map);
                            addIfUpdated(updated, map);
                            break;
                        }
                    }
                }
            }
        }
        synchronized (data) {
            data.clear();
            data.addAll(updated);
            data.addAll(matched);
            data.addAll(halfmatched);
            data.addAll(filtered);
        }
        Log.d(TAG, "filtered Version: " + filter);
    }

    void addIfUpdated(List<Map<String, String>> updated, HashMap<String, String> map) {
        String update = bible.getContext().getString(R.string.update);
        if (update.equals(map.get("text"))) {
            @SuppressWarnings("unchecked")
            HashMap<String, String> clone = (HashMap<String, String>) map.clone();
            clone.put("lang", update);
            updated.add(clone);
        }
    }

    void download(final Map<String, String> map) {
        String id = null;
        final String path = (String) map.get("path");
        final String code = (String) map.get("code");
        DownloadInfo info = bible.download(path);
        if (info != null) {
            id = String.valueOf(info.id);
        }
        synchronized (queue) {
            queue.put(code, id);
            if (id != null) {
                queue.put(id, code);
            }
        }
        if (id != null) {
            String cancel = getString(R.string.cancel_install);
            map.put("text", cancel);
            adapter.notifyDataSetChanged();
        } else {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Bible.BIBLEDATA_PREFIX + path)));
        }
    }
}
