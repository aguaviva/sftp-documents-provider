package com.aguaviva.android.sftpstorageprovider;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.Adapter;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;
import com.aguaviva.android.libssh2.Connection;
import com.aguaviva.android.libssh2.helpers;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class ActivityFormConnection extends FragmentActivity {

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
        ArrayAdapter<String> arrayAdapter= utils.populateListView(this, "None", helpers.getFilesKeys() );
        keyList.setAdapter(arrayAdapter);

        // gets the previously created intent
        Intent myIntent = getIntent();
        String connectionName = myIntent.getStringExtra("connection_name");
        if (connectionName.equals("")==false) {
            try {
                editConnectionName.setText(connectionName);
                JsonToUI(helpers.loadConnectionString(connectionName));
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

                Intent myIntent = new Intent(ActivityFormConnection.this, ActivityTestConnection.class);
                myIntent.putExtra("connection_name", connectionName);
                startActivityForResult(myIntent, 0);
            }
        });

        Button buttonSave = (Button) findViewById(R.id.buttonSave);
        buttonSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    helpers.saveConnection(editConnectionName.getText().toString(), UiToJson());
                    getContentResolver().notifyChange(DocumentsContract.buildRootsUri(BuildConfig.DOCUMENTS_AUTHORITY), null);
                    finish();
                } catch (IOException e) {
                    Toast.makeText(getBaseContext(),  "Connection name has invalid characters", Toast.LENGTH_LONG ).show();
                } catch (JSONException e) {
                    Toast.makeText(getBaseContext(),  "Connection settings have invalid characters", Toast.LENGTH_LONG ).show();
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
                new DialogYesNo().show(ActivityFormConnection.this, "Delete connection?", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which){
                            case DialogInterface.BUTTON_POSITIVE:
                                String connectionName =  editConnectionName.getText().toString();
                                helpers.deleteConnection(connectionName);
                                getContentResolver().notifyChange(DocumentsContract.buildRootsUri(BuildConfig.DOCUMENTS_AUTHORITY), null);
                                finish();
                                break;
                        }
                    }
                });
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

    public Connection UiToJson() throws JSONException {
        Connection c = new Connection();
        c.hostname = editHostname.getText().toString();
        c.username = editUsername.getText().toString();
        c.port = Integer.parseInt(editPort.getText().toString());
        c.root = editRoot.getText().toString();
        if (keyList.getSelectedItemPosition()!= keyList.getAdapter().getCount()-1) {
            String selectedKeyName = keyList.getSelectedItem().toString();
            c.keyname = selectedKeyName;
        }
        return  c;
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