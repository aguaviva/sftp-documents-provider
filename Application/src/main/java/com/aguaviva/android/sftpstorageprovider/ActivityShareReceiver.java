package com.aguaviva.android.sftpstorageprovider;


    import androidx.fragment.app.FragmentActivity;
    import androidx.localbroadcastmanager.content.LocalBroadcastManager;

    import android.content.BroadcastReceiver;
    import android.content.Context;
    import android.content.Intent;
    import android.content.IntentFilter;
    import android.database.Cursor;
    import android.net.Uri;
    import android.os.Bundle;
    import android.os.ParcelFileDescriptor;
    import android.provider.OpenableColumns;
    import android.text.method.ScrollingMovementMethod;
    import android.view.View;
    import android.widget.ProgressBar;
    import android.widget.ScrollView;
    import android.widget.TextView;

    import com.aguaviva.android.libssh2.SFTP;
    import com.aguaviva.android.libssh2.SftpFile;
    import com.aguaviva.android.libssh2.helpers;
    import com.aguaviva.android.libssh2.Connection;

    import org.json.JSONException;

    import java.io.File;
    import java.io.FileInputStream;
    import java.io.FileNotFoundException;
    import java.io.FileOutputStream;
    import java.io.IOException;
    import java.io.InputStream;
    import java.util.ArrayList;

public class ActivityShareReceiver extends FragmentActivity {

    TextView textTerminal;
    ScrollView scrollView;
    ProgressBar progressBar;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share_receiver);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        textTerminal = (TextView)findViewById(R.id.textTerminal);
        scrollView = (ScrollView)findViewById(R.id.scrollView);

        // filters
        Intent intent = getIntent();
        Uri data = intent.getData();
        String action = intent.getAction();

        ArrayList<Uri> imageUriList = null;
        if (Intent.ACTION_SEND.equals(action))
        {
            imageUriList = new ArrayList<>();
            Uri fileUri = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
            imageUriList.add(fileUri);
            sendFileList(imageUriList);
        }
        else if (Intent.ACTION_SEND_MULTIPLE.equals(action))
        {
            imageUriList = getIntent().getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            sendFileList(imageUriList);
        }
        else
        {
            return;
        }
    }

    public String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        }
        return result;
    }

    void sendFileList(ArrayList<Uri> uriList) {

        new Thread() {
            @Override
            public void run() {

                SFTP sftp = new SFTP();

                logTerminal("Connecting...");
                File[] connections = helpers.getFilesConnections();
                if (sftp.Connect(connections[0].getName(), true)) {
                    logTerminal("OK\n");

                    for (Uri uri : uriList) {
                        sendFile(uri, sftp);
                    }
                    logTerminal("Done\n");
                } else {
                    logTerminal("Can't connect\n");
                }
            }
        }.start();
    }

    void sendFile(Uri uri, SFTP sftp) {
        String filename = getFileName(uri);

        logTerminal("Sending " + filename + "...");
        try {
            SftpFile sftpFile = sftp.getFileHandler("/"+filename, "w");
            String fullpath = uri.getPath();
            ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
            InputStream fileStream = new FileInputStream(pfd.getFileDescriptor());

            byte[] buffer = new byte[64*1024];
            int read_bytes;
            while((read_bytes = fileStream.read(buffer)) > 0) {
                sftpFile.write(buffer, read_bytes);
            }

            logTerminal("OK\n");
            fileStream.close();
        } catch (IOException e) {
            logTerminal("Err\n");
            logTerminal(e.getMessage());
        }

    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
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
}
