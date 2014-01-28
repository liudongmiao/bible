package me.piebridge.bible;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.http.client.ClientProtocolException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

public class Versions extends Activity {

    static long mtime = 0;
    static Bible bible;
    static CharSequence filter;
    static SimpleAdapter adapter;
    static boolean resume = false;
    static List<Map<String, String>> versions;
    static Map<String, String> queue = new HashMap<String, String>();
    static List<Map<String, String>> data = new ArrayList<Map<String, String>>();

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.versions);
        bible = Bible.getBible(this);

        String[] from = { "code", "name", "text", "lang" };
        int[] to = {R.id.code, R.id.name, R.id.action, 0 };
        adapter = new SimpleAdapter(this, data, R.layout.version, from, to) {
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
                            clickVersion((TextView) view, map);
                        }
                    });
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
                    data.clear();
                    List<Map<String, String>> values = versions;
                    for (Map<String, String> map : values) {
                        if (filter == null) {
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
        };

        final ListView list = (ListView) findViewById(android.R.id.list);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                @SuppressWarnings("unchecked")
                Map<String, String> map = (Map<String, String>) list.getItemAtPosition(position);
                TextView action = (TextView) view.findViewById(R.id.action);
                clickVersion(action, map);
            }

        });

        final EditText editText = (EditText) findViewById(R.id.query);
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int before, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int after) {
                filter = s;
                adapter.getFilter().filter(s);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }

        });
    }

    static List<Map<String, String>> parseVersions(String string) {
        bible.checkVersions();
        Context context = bible.getContext();
        List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        try {
            JSONObject jsons = new JSONObject(string);
            List<String> installed = bible.get(Bible.TYPE.VERSIONPATH);
            JSONArray versions = jsons.getJSONArray("versions");
            for (int i = 0; i < versions.length(); ++i) {
                JSONObject version = versions.getJSONObject(i);
                Map<String, String> map = new HashMap<String, String>();
                String action;
                String code = version.getString("code");
                String lang = version.getString("lang");
                map.put("lang", lang);
                map.put("code", code.toUpperCase(Locale.US));
                map.put("name", version.getString("name"));
                map.put("path", String.format("bibledata-%s-%s.zip", lang, code));
                if (!installed.contains(code)) {
                    action = context.getString(R.string.install);
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
            }
        } catch (JSONException e) {
            e.printStackTrace();
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
        long now = System.currentTimeMillis() / 1000;
        if (mtime == 0 || now - mtime > 86400) {
            mtime = now;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        setVersions(bible.getRemoteVersions());
                    } catch (Exception e) {
                    }
                }

            }).start();
        }
        setVersions(json);
    }

    void setVersions(String json) {
        if (json == null || json.length() == 0) {
            return;
        }
        data.clear();
        versions = parseVersions(json);
        for (Map<String, String> map : versions) {
            data.add(map);
        }
        refresh(0);
        adapter.notifyDataSetChanged();
        adapter.getFilter().filter(filter);
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
                for (Map<String, String> map : data) {
                    if (String.valueOf(map.get("code")).equalsIgnoreCase(code)) {
                        changed = true;
                        String action = bible.getContext().getString(R.string.uninstall);
                        map.put("text", action);
                        map.put("action", action);
                    }
                }
                if (changed) {
                    adapter.notifyDataSetChanged();
                    adapter.getFilter().filter(filter);
                }
            }
        }
    }

    void clickVersion(final TextView view, final Map<String, String> map) {
        final String path = (String) map.get("path");
        final String code = (String) map.get("code");
        final String name = (String) map.get("name");
        final String action = (String) map.get("action");
        final String text = view.getText().toString();
        if (text.equals(getString(R.string.install))) {
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
            areYouSure(getString(R.string.deleteversion, code.toUpperCase(Locale.US)),
                    getString(R.string.deleteversiondetail, name),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            bible.deleteVersion(code);
                            bible.checkVersions();
                            String install = getString(R.string.install);
                            map.put("text", install);
                            map.put("action", install);
                            adapter.notifyDataSetChanged();
                        }
                    });
        }
    }

    void areYouSure(String title, String message, DialogInterface.OnClickListener handler) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton(android.R.string.yes, handler).setNegativeButton(android.R.string.no, null).create()
                .show();
    }
}
