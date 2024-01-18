package com.aguaviva.android.sftpstorageprovider;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import com.aguaviva.android.libssh2.Connection;
import com.aguaviva.android.libssh2.SFTP;
import com.aguaviva.android.libssh2.helpers;

import androidx.fragment.app.FragmentActivity;

import org.json.JSONException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class ActivityTestConnection extends FragmentActivity {

    public static final String TAG = "ActivityTestConnection";

    public static final String FRAGTAG = "StorageProviderFragment";
    TextView textTerminal;
    ScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_connection);
        textTerminal = (TextView)findViewById(R.id.textTerminal);
        scrollView = (ScrollView)findViewById(R.id.scrollView);

        Intent myIntent = getIntent(); // gets the previously created intent
        String connectionName = myIntent.getStringExtra("connection_name");

        Thread thread = new Thread() {
            public void run() {
                SFTP sftp = new SFTP();
                test(sftp, connectionName);

            }
        };
        thread.start();

        Button buttonBack = (Button) findViewById(R.id.buttonBack);
        buttonBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                thread.interrupt();
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

    void test(SFTP sftp, String connectionName) {

            Connection connection;

            try {
                connection = helpers.loadConnection(connectionName);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            logTerminal(String.format("Resolving %s\n", connection.hostname));
            try {
                connection.hostname = InetAddress.getByName(connection.hostname).getHostAddress();
            } catch (UnknownHostException e) {
                logTerminal("Can't resolve\n");
                return;
            }
            logTerminal(String.format("Resolved %s\n", connection.hostname));

            if (sftp.Connect(connection, false)) {
                logTerminal(String.format("Connected\n"));

                logTerminal(String.format("Fingerprint: %s\n", sftp.GetFingerprint()));

                if (sftp.Auth()) {
                    sftp.ls("/", new SFTP.onGetFileListener() {
                        @Override
                        public boolean listen(String file) {
                            logTerminal(String.format("%s\n", file));
                            return true;
                        }

                        @Override
                        public void done() {

                        }
                    });

                    logTerminal("OK\n");
                } else {
                    logTerminal(String.format("Can't authenticate, bad username or keys\n"));
                }
                sftp.Disconnect();
            } else {
                logTerminal(String.format("Can't connect\n"));
            }
    }
}
