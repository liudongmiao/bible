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
    ImageView refresh;
    static EditText query;

    static Bible bible;
    static SimpleAdapter adapter;
    static boolean resume = false;
    static List<Map<String, String>> versions;
    static List<String> languages = new ArrayList<String>();
    static List<String> names = new ArrayList<String>();
    static Map<String, String> queue = new HashMap<String, String>();
    static List<Map<String, String>> data = new ArrayList<Map<String, String>>();
    Map<String, String> request = new HashMap<String, String>();

    final static int STOP = 0;
    final static int START = 1;

    final static String TAG = "me.piebridge.bible$Versions";

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.versions);
        bible = Bible.getBible(this);

        String[] from = { "code", "name", "text", "lang" };
        int[] to = {R.id.code, R.id.name, R.id.action, 0 };
        adapter = new Adapter(this, data, R.layout.version, from, to);

        final ListView list = (ListView) findViewById(android.R.id.list);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                @SuppressWarnings("unchecked")
                Map<String, String> map = (Map<String, String>) list.getItemAtPosition(position);
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
            request.put("code", getString(R.string.not_found));
            request.put("name", getString(R.string.request_version));
            Versions.languages.add(request.get("lang"));
            Versions.names.add(request.get("code"));
        }
    }

    static List<Map<String, String>> parseVersions(String string) {
        bible.checkVersions();
        Context context = bible.getContext();
        List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        try {
            JSONObject jsons = new JSONObject(string);
            List<String> installed = bible.get(Bible.TYPE.VERSIONPATH);
            JSONArray versions = jsons.getJSONArray("versions");
            JSONObject languages = null;
            try {
                languages = jsons.getJSONObject("languages");
            } catch (JSONException e) {
                // do nothing
            }
            for (int i = 0; i < versions.length(); ++i) {
                JSONObject version = versions.getJSONObject(i);
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
                if (!Versions.languages.contains(lang)) {
                    Versions.languages.add(lang);
                    String name = lang;
                    if (languages != null) {
                        try {
                            name = languages.getString(lang);
                        } catch (JSONException e) {
                        }
                    }
                    Versions.names.add(name);
                }
            }
        } catch (JSONException e) {
        }
        return list;
    }

    @Override
    protected void onResume() {
        super.onResume();
        resume = true;
        String json;
        try {
            json = bible.getLocalVersions();
        } catch (IOException e) {
            json = "{versions:[]}";
        }
        setVersions(json);
        long now = System.currentTimeMillis() / 1000;
        if (mtime == 0 || mtime - now > 86400) {
            mtime = now;
            handler.sendEmptyMessageDelayed(START, 400);
        }
    }

    boolean refreshing = false;

    private Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
            case STOP:
                setVersions((String) msg.obj);
                refreshing = false;
                ((AnimationDrawable) refresh.getDrawable()).stop();
                return false;
            case START:
                Log.d(TAG, "will refreshVersions");
                refreshVersions();
                refreshing = true;
                ((AnimationDrawable) refresh.getDrawable()).start();
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
        versions = parseVersions(json);
        versions.add(request);
        synchronized (data) {
            data.clear();
            for (Map<String, String> map : versions) {
                data.add(map);
            }
        }
        refresh(0);
        adapter.notifyDataSetChanged();
        query.setText("");
    }

    @Override
    protected void onPause() {
        super.onPause();
        resume = false;
    }

    static void refresh(long id) {
        if (resume) {
            boolean changed = false;
            String code = queue.get(String.valueOf(id));
            if (code != null) {
                queue.remove(String.valueOf(id));
                queue.remove(code);
                synchronized (data) {
                    data.clear();
                    for (Map<String, String> map : versions) {
                        if (String.valueOf(map.get("code")).equalsIgnoreCase(code)) {
                            changed = true;
                            String action = bible.getContext().getString(R.string.uninstall);
                            map.put("text", action);
                            map.put("action", action);
                        }
                        data.add(map);
                    }
                }
                if (changed) {
                    adapter.notifyDataSetChanged();
                    query.setText("");
                }
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
            long id;
            if (queue.containsKey(code)) {
                id = Long.parseLong(queue.get(code));
            } else {
                id = bible.download(path);
            }
            if (id > 0) {
                queue.put(code, String.valueOf(id));
                queue.put(String.valueOf(id), code);
                String cancel = getString(R.string.cancel_install);
                map.put("text", cancel);
                adapter.notifyDataSetChanged();
            }
        } else if (text.equals(getString(R.string.cancel_install))) {
            if (queue.containsKey(code)) {
                long id = Long.parseLong(queue.get(code));
                if (id > 0) {
                    bible.cancel(id);
                    queue.remove(String.valueOf(id));
                    queue.remove(code);
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
                            final String install = getString(R.string.install);
                            bible.deleteVersion(code, new Runnable() {
                                public void run() {
                                    map.put("text", install);
                                    map.put("action", install);
                                    adapter.notifyDataSetChanged();
                                }
                            });
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
            if (data.size() <= position) {
                // why ?
                synchronized (data) {
                    data.clear();
                    for (Map<String, String> map : versions) {
                        data.add(map);
                    }
                }
            }
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
                        clickVersion((TextView) view, map, true);
                    }
                });
                @SuppressWarnings("unchecked")
                Map<String, String> map = (Map<String, String>) getItem(position);
                if (map.get("action") == null) {
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
                synchronized (data) {
                    data.clear();
                    for (Map<String, String> map : versions) {
                        if (filter == null || map.get("action") == null) {
                            data.add(map);
                        } else {
                            for (String value : map.values()) {
                                if (value.toLowerCase(Locale.US).contains(filter)) {
                                    data.add(map);
                                    break;
                                }
                            }
                        }
                    }
                }
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
                    name = Versions.names.get((int) section);
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
            return Versions.languages.indexOf(language);
        }
    }
}
