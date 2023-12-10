package com.aguaviva.android.sftpstorageprovider;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Adapter;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.common.activities.SampleActivityBase;
import com.example.android.common.logger.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class ConnectionManagerActivity extends SampleActivityBase {

    public int RESULT_OK = 1;
    EditText editConnectionName;
    EditText editHostname;
    EditText editPort;
    EditText editUsername;
    EditText editRoot;
    Spinner keyList;
    TextView textTerminal;
    ScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection_manager);

        editConnectionName = (EditText) findViewById(R.id.editConnectionName);
        editHostname = (EditText) findViewById(R.id.editHostname);
        editPort = (EditText) findViewById(R.id.editPort);
        editUsername = (EditText) findViewById(R.id.editUsername);
        editRoot = (EditText) findViewById(R.id.editRoot);
        keyList = (Spinner)findViewById(R.id.spinnerKeys);
        textTerminal = (TextView)findViewById(R.id.textTerminal);
        scrollView = (ScrollView)findViewById(R.id.scrollView);
        ArrayAdapter<String> arrayAdapter= helpers.populateListView(this, "None", helpers.getFilesKeys() );
        keyList.setAdapter(arrayAdapter);

        // gets the previously created intent
        Intent myIntent = getIntent();
        String connectionName = myIntent.getStringExtra("connection_name");
        if (connectionName.equals("")==false) {
            try {
                editConnectionName.setText(connectionName);
                JsonToUI(helpers.loadConnection(connectionName));
            } catch (JSONException e) {
                Toast.makeText(getBaseContext(),  "Can't parse connection settings", Toast.LENGTH_LONG ).show();
                throw new RuntimeException(e);
            } catch (IOException e) {
                Toast.makeText(getBaseContext(),  "Can't load connection name", Toast.LENGTH_LONG ).show();
                throw new RuntimeException(e);
            }
        }

        Button buttonCheckConnection = (Button) findViewById(R.id.buttonCheckConnection);
        buttonCheckConnection.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {

                buttonCheckConnection.setActivated(false);

                new Thread() {
                    @Override
                    public void run() {
                        String username = editUsername.getText().toString();
                        String hostname = editHostname.getText().toString();
                        int port = -1;
                        try {
                            port = Integer.parseInt(editPort.getText().toString());
                        } catch (NumberFormatException e) {
                            logTerminal(String.format("Error, port should be an integer\n"));
                            return;
                        }

                        String root = editRoot.getText().toString();
                        String keyname = keyList.getSelectedItem().toString();
                        if (keyList.getSelectedItemPosition()== keyList.getAdapter().getCount()-1) {
                            logTerminal(String.format("Error, please select a key\n"));
                            return;
                        }

                        try {
                            String hostip = InetAddress.getByName(hostname).getHostAddress();
                            logTerminal(String.format("Resolved %s -> %s\n", hostname, hostip));

                            int session_id = Ssh2.session_connect(hostip, port);
                            if (session_id>=0) {
                                String banner = Ssh2.session_get_banner(session_id);
                                logTerminal(String.format("Banner: `%s`\n", banner));

                                byte[] hash = Ssh2.get_host_key_hash(session_id);
                                String fingerprint = new String(Base64.encode(hash, hash.length));
                                logTerminal(String.format("Fingerprint SHA256: %s", fingerprint));

                                int res = Ssh2.session_auth(session_id, username, getFilesDir() + helpers.GetPublicKeyPath(keyname), getFilesDir() + helpers.GetPrivateKeyPath(keyname), "");
                                if (res >= 0) {
                                    int ssh2_sftp_session = Ssh2.sftp_init(session_id);
                                    if (ssh2_sftp_session >= 0) {
                                        int sftp_handle_id = Ssh2.opendir(ssh2_sftp_session, root);
                                        if (sftp_handle_id >= 0) {
                                            while (true) {
                                                String entry = Ssh2.readdir(sftp_handle_id);
                                                if (entry.equals("")) {
                                                    break;
                                                } else {
                                                    logTerminal(String.format("%s\n", entry));
                                                }
                                            }
                                            Ssh2.closedir(sftp_handle_id);
                                        } else {
                                            logTerminal(String.format("Error, can't opendir %s\n", root));
                                            logTerminal(String.format("Details: %s\n", Ssh2.session_last_error(session_id)));
                                        }
                                        Ssh2.sftp_shutdown(ssh2_sftp_session);
                                    }
                                } else {
                                    logTerminal(String.format("Error, can't auth: %s\n", Ssh2.session_last_error(session_id)));
                                }

                            } else {
                                logTerminal(String.format("Error, can't connect to %s:%d\n", hostname, port));
                            }
                            Ssh2.session_disconnect(session_id);

                        } catch (UnknownHostException e) {
                            logTerminal(String.format("Can't resolve %s\n", hostname));
                        }
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                buttonCheckConnection.setActivated(true);
                            }
                        });
                        logTerminal(String.format("OK!\n"));
                    }
                }.start();
            }
        });

        Button buttonSave = (Button) findViewById(R.id.buttonSave);
        buttonSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    helpers.saveConnection(editConnectionName.getText().toString(), UiToJson());
                    finish();
                } catch (IOException e) {
                    Toast.makeText(getBaseContext(),  "Connection name has invalid characters", Toast.LENGTH_LONG ).show();
                    throw new RuntimeException(e);

                } catch (JSONException e) {
                    Toast.makeText(getBaseContext(),  "Connection settings have invalid characters", Toast.LENGTH_LONG ).show();
                    throw new RuntimeException(e);
                }
            }
        });

        Button buttonCancel = (Button) findViewById(R.id.buttonCancel);
        buttonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        Button buttonRemove = (Button) findViewById(R.id.buttonRemove);
        buttonRemove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                helpers.deleteConnection(editConnectionName.getText().toString());
                finish();
            }
        });
    }

    private void logTerminal(String s) {
        runOnUiThread(new Runnable(){
            @Override
            public void run(){
                textTerminal.append(s);
                scrollView.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    public String UiToJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("hostname", editHostname.getText());
        jsonObject.put("username", editUsername.getText());
        jsonObject.put("port", Integer.parseInt(editPort.getText().toString()));
        jsonObject.put("root", editRoot.getText().toString());
        if (keyList.getSelectedItemPosition()!= keyList.getAdapter().getCount()-1) {
            String selectedKeyName = keyList.getSelectedItem().toString();
            jsonObject.put("keyname", selectedKeyName);
        }
        return  jsonObject.toString();
    }

    public void JsonToUI(String json) throws JSONException {
        JSONObject jsonObject = new JSONObject(json);
        editHostname.setText(jsonObject.get("hostname").toString());
        editUsername.setText(jsonObject.get("username").toString());
        editPort.setText(String.valueOf(jsonObject.getInt("port")));
        editRoot.setText(jsonObject.get("root").toString());

        //select key in spinner
        if (jsonObject.has("keyname")) {
            String key = jsonObject.get("keyname").toString();

            Adapter adapter = keyList.getAdapter();
            keyList.setSelection(adapter.getCount()-1); //select none by default
            boolean foundKey = false;
            for (int i = 0; i < adapter.getCount()-1; i++) {
                if (key.equals((String)adapter.getItem(i))) {
                    keyList.setSelection(i);
                    foundKey = true;
                    break;
                }
            }
            if (foundKey==false) {
                Toast.makeText(getBaseContext(),  String.format("Key %s not found, please select new key", key), Toast.LENGTH_LONG ).show();
            }
        } else {
            Toast.makeText(getBaseContext(),  "Note there is no key selected!", Toast.LENGTH_LONG ).show();
        }

    }
}