package me.piebridge.bible;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

public class Versions extends Activity {

    static Bible bible;
    static CharSequence filter;
    static SimpleAdapter adapter;
    static boolean resume = false;
    static Map<String, String> queue = new HashMap<String, String>();
    static List<Map<String, String>> data = new ArrayList<Map<String, String>>();

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.versions);
        bible = Bible.getBible(this);

        adapter = new SimpleAdapter(this, data, R.layout.version, new String[] { "code", "name", "text", "lang" },
                new int[] { R.id.code, R.id.name, R.id.action, 0 }) {
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
            json = getJsonVersions();
        } catch (IOException e) {
            json = "{versions:[]}";
        }
        data.clear();
        for (Map<String, String> map : parseVersions(json)) {
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

    String getJsonVersions() throws IOException {
        InputStream is = null;
        File file = new File(getFilesDir(), "versions.json");
        if (file.isFile()) {
            is = new FileInputStream(file);
        } else {
            is = getResources().openRawResource(R.raw.versions);
        }
        int length;
        byte[] buffer = new byte[8192];
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        while ((length = is.read(buffer)) >= 0) {
            bao.write(buffer, 0, length);
        }
        is.close();
        return bao.toString();
    }

    static void refresh(long id) {
        if (resume) {
            boolean changed = false;
            String code = queue.get(String.valueOf(id));
            if (code != null) {
                queue.remove(String.valueOf(id));
                queue.remove(code);
            }
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

    void clickVersion(final TextView view, final Map<String, String> map) {
        final String path = (String) map.get("path");
        final String code = (String) map.get("code");
        final String name = (String) map.get("name");
        final String action = (String) map.get("action");
        final String text = view.getText().toString();
        android.util.Log.d("me.piebridge.bible", "path: " + path);
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
