package me.piebridge.bible;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.emilsjolander.components.stickylistheaders.StickyListHeadersAdapter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
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

public class Versions extends Activity {

    static long mtime = 0;

    Bible bible;
    ListView list;
    EditText query;
    ImageView refresh;
    SimpleAdapter adapter;
    List<String> languages = new ArrayList<String>();
    List<String> names = new ArrayList<String>();
    List<Map<String, String>> data = new ArrayList<Map<String, String>>();
    List<Map<String, String>> filtered = new ArrayList<Map<String, String>>();
    List<Map<String, String>> versions = new ArrayList<Map<String, String>>();
    Map<String, String> request = new HashMap<String, String>();

    final static int STOP = 0;
    final static int START = 1;
    final static int DELETE = 2;
    final static int DELETED = 3;
    final static int COMPLETE = 4;

    static Handler resume = null;
    static List<String> completed = new ArrayList<String>();
    static Map<String, String> queue = new HashMap<String, String>();

    final static String TAG = "me.piebridge.bible$Versions";

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.versions);
        bible = Bible.getBible(this);

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
                adapter.getFilter().filter(s);
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
            request.put("code", getString(R.string.request_version));
            languages.add(request.get("lang"));
            names.add(getString(R.string.not_found));
        }
    }

    List<Map<String, String>> parseVersions(List<Map<String, String>> list, String string) {
        bible.checkVersions();
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
                Map<String, String> map = new HashMap<String, String>();
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
        long now = System.currentTimeMillis() / 1000;
        if (mtime == 0 || mtime - now > 86400) {
            mtime = now;
            handler.sendEmptyMessageDelayed(START, 400);
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
                bible.deleteVersion(code, new Runnable() {
                    public void run() {
                        handler.sendMessage(handler.obtainMessage(DELETED, code));
                    }
                });
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
                            if (completed.contains(version)) {
                                map.put("text", uninstall);
                                map.put("action", uninstall);
                                completed.remove(version);
                            }
                        }
                    }
                }
                filterVersion(query.getText().toString());
                adapter.notifyDataSetChanged();
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
            String[] from = { "code", "name", "text", "lang" };
            int[] to = {R.id.code, R.id.name, R.id.action, 0 };
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

    public static void onDownloadComplete(long id) {
        String code = queue.get(String.valueOf(id));
        Log.d(TAG, "download complete: " + code);
        if (code != null) {
            synchronized (queue) {
                queue.remove(String.valueOf(id));
                queue.remove(code);
            }
            synchronized (completed) {
                completed.add(code.toUpperCase(Locale.US));
            }
            if (resume != null) {
                resume.sendEmptyMessage(COMPLETE);
            }
        }
    }

    void clickVersion(final TextView view, final Map<String, String> map, final boolean button) {
        final String path = (String) map.get("path");
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
        } else if (text.equals(getString(R.string.install))) {
            final long id =  bible.download(path);
            if (id > 0) {
                synchronized (queue) {
                    queue.put(code, String.valueOf(id));
                    queue.put(String.valueOf(id), code);
                }
                String cancel = getString(R.string.cancel_install);
                map.put("text", cancel);
                adapter.notifyDataSetChanged();
            }
        } else if (text.equals(getString(R.string.cancel_install))) {
            if (queue.containsKey(code)) {
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
                Intent intent = new Intent(this, Chapter.class);
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

    class Adapter extends SimpleAdapter implements StickyListHeadersAdapter {

        private LayoutInflater mInflater;

        public Adapter(Context context, List<? extends Map<String, ?>> data, int resource, String[] from, int[] to) {
            super(context, data, resource, from, to);
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            final View code = view.findViewById(R.id.name);
            final TextView action = (TextView) view.findViewById(R.id.action);
            if (code != null && action != null) {
                action.setTag(position);
                action.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        int position = (Integer) view.getTag();
                        @SuppressWarnings("unchecked")
                        Map<String, String> map = (Map<String, String>) getItem(position);
                        clickVersion((TextView) view, map, true);
                    }
                });
                @SuppressWarnings("unchecked")
                Map<String, String> map = (Map<String, String>) getItem(position);
                if (map.get("action") == null) {
                    code.setVisibility(View.GONE);
                    action.setVisibility(View.GONE);
                } else {
                    code.setVisibility(View.VISIBLE);
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
        filtering = false;
        Log.d(TAG, "filtering Version: " + filter);
        synchronized (versions) {
            filtered.clear();
            for (Map<String, String> map : versions) {
                if (filtering) {
                    filtering = false;
                    return;
                }
                if (filter == null || map.get("action") == null) {
                    filtered.add(map);
                } else {
                    for (String value : map.values()) {
                        if (filtering) {
                            filtering = false;
                            return;
                        }
                        if (value.toLowerCase(Locale.US).contains(filter)) {
                            filtered.add(map);
                            break;
                        }
                    }
                    String language = map.get("lang");
                    if (language != null) {
                        int index = languages.indexOf(language);
                        if (index != -1 && names.size() > index) {
                            String name = names.get(index);
                            if (name.toLowerCase(Locale.US).contains(filter)) {
                                filtered.add(map);
                            }
                        }

                    }
                }
            }
        }
        synchronized (data) {
            data.clear();
            data.addAll(filtered);
        }
        Log.d(TAG, "filtered Version: " + filter);
    }
}
