package me.piebridge.bible;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
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
import android.widget.Button;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.ListView;
import android.widget.SimpleAdapter;

public class Versions extends Activity {

    static Bible bible;
    static String query;
    static SimpleAdapter adapter;
    static boolean resume = false;
    static HashMap<String, String> queue = new HashMap<String, String>();
    static ArrayList<HashMap<String, Object>> data = new ArrayList<HashMap<String, Object>>();

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.versions);
        bible = Bible.getBible(this);

        adapter = new SimpleAdapter(this, data, R.layout.version, new String[] { "code", "name", "text", "lang" },
                new int[] { R.id.code, R.id.name, R.id.action, 0 }) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                Button button = (Button) view.findViewById(R.id.action);
                if (button != null) {
                    button.setTag(position);
                    button.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            int position = (Integer) view.getTag();
                            @SuppressWarnings("unchecked")
                            Map<String, Object> map = (HashMap<String, Object>) getItem(position);
                            clickButton(view, map);
                        }
                    });
                }
                return view;
            }
        };

        final ListView list = (ListView) findViewById(android.R.id.list);
        list.setAdapter(adapter);

        final Filter filter = adapter.getFilter();
        final EditText editText = (EditText) findViewById(R.id.query);
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int before, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int after) {
                query = s.toString();
                filter.filter(s);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }

        });
    }

    static ArrayList<HashMap<String, Object>> parseVersions(String string) {
        bible.checkVersions();
        Context context = bible.getContext();
        ArrayList<HashMap<String, Object>> list = new ArrayList<HashMap<String, Object>>();
        try {
            JSONObject jsons = new JSONObject(string);
            ArrayList<String> installed = bible.get(Bible.TYPE.VERSIONPATH);
            JSONArray versions = jsons.getJSONArray("versions");
            for (int i = 0; i < versions.length(); ++i) {
                JSONObject version = versions.getJSONObject(i);
                HashMap<String, Object> map = new HashMap<String, Object>();
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
        for (HashMap<String, Object> map : parseVersions(json)) {
            data.add(map);
        }
        refresh(0);
        adapter.notifyDataSetChanged();
        adapter.getFilter().filter(query);
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
            for (HashMap<String, Object> map : data) {
                if (((String) map.get("code")).equalsIgnoreCase(code)) {
                    changed = true;
                    String action = bible.getContext().getString(R.string.uninstall);
                    map.put("text", action);
                    map.put("action", action);
                }
            }
            if (changed) {
                adapter.notifyDataSetChanged();
                adapter.getFilter().filter(query);
            }
        }
    }

    void clickButton(View view, final Map<String, Object> map) {
        final Button button = (Button) view;
        final String path = (String) map.get("path");
        final String code = (String) map.get("code");
        final String name = (String) map.get("name");
        final String action = (String) map.get("action");
        final String text = button.getText().toString();
        if (text.equals(getString(R.string.install))) {
            long id = bible.download(path);
            if (id > 0) {
                queue.put(code, String.valueOf(id));
                queue.put(String.valueOf(id), code);
                String cancel = getString(R.string.cancel_install);
                map.put("text", cancel);
                button.setText(cancel);
            }
        } else if (text.equals(getString(R.string.cancel_install))) {
            long id = Long.parseLong(queue.get(code));
            if (id > 0) {
                bible.cancel(id);
            }
            map.put("text", action);
            button.setText(action);
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
                            button.setText(install);
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
