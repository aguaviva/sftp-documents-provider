package com.aguaviva.android.libssh2;

import android.os.ParcelFileDescriptor;
import android.util.Log;

public class SFTP_retry extends SFTP {

    private static final String TAG = "SFTP_retry";

    int num_retries = 3;

    @Override
    public int get(String documentId, ParcelFileDescriptor writeEnd, onProgressListener listener) {
        int res = -1;
        for(int i=0;i<num_retries;i++) {
            res = super.get(documentId, writeEnd, listener);
            if (res>=0)
                return res;
            Log.e(TAG, "* get retrying" + super.getLastError() + " " + String.valueOf(getSftpLastError()));

            Disconnect();
            Connect(this.connection, true);
        }
        Log.e(TAG, "* get gave up" + super.getLastError());
        return res;
    }

    @Override
    public int put(String documentId, ParcelFileDescriptor readEnd, onProgressListener listener) {
        int res = -1;
        for(int i=0;i<num_retries;i++) {
            res = super.put(documentId, readEnd, listener);
            if (res>=0)
                return res;
            Log.e(TAG, "* put retrying" + super.getLastError() + " " + String.valueOf(getSftpLastError()));
            Disconnect();
            Connect(this.connection, true);
        }
        Log.e(TAG, "* put gave up" + super.getLastError());
        return res;
    }

    @Override
    public int ls(String path, onGetFileListener listener)  {
        int res = -1;
        for(int i=0;i<num_retries;i++) {
            res = super.ls(path, listener);
            if (res>=0)
                return res;

            Log.e(TAG, "* ls retrying" + super.getLastError() + " " + String.valueOf(getSftpLastError()));

            Disconnect();
            Connect(this.connection, true);
        }

        Log.e(TAG, "* ls gaveup" + super.getLastError());
        return res;
    }

    @Override
    public String stat(String path) {
        String str = null;
        for(int i=0;i<num_retries;i++) {
            str = super.stat(path);
            if (str.charAt(0)!='*')
                return str;

            Log.e(TAG, String.format("* Stat retrying %d/%d. Err: '%s' code: %d", i, num_retries, getLastError(), getSftpLastError()));
            Disconnect();
            Connect(this.connection, true);
        }
        Log.e(TAG, String.format("* Stat gave up. Err: '%s' code: %d", super.getLastError(), getSftpLastError()));
        return str;
    }
}




