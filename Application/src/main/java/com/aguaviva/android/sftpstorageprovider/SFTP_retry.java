package com.aguaviva.android.sftpstorageprovider;

import android.os.ParcelFileDescriptor;

public class SFTP_retry extends SFTP {

     int num_retries = 3;

    @Override
    public int get(String documentId, ParcelFileDescriptor writeEnd, onProgressListener listener) {
        int res = -1;
        for(int i=0;i<num_retries;i++) {
            res = super.get(documentId, writeEnd, listener);
            if (res>=0)
                break;

            Shutdown();
            Init(this.connection);
        }
        return res;
    }

    @Override
    public int put(String documentId, ParcelFileDescriptor readEnd, onProgressListener listener) {
        int res = -1;
        for(int i=0;i<num_retries;i++) {
            res = super.put(documentId, readEnd, listener);
            if (res>=0)
                break;

            Shutdown();
            Init(this.connection);
        }
        return res;
    }

    @Override
    public int ls(String path, onGetFileListener listener)  {
        int res = -1;
        for(int i=0;i<num_retries;i++) {
            res = super.ls(path, listener);
            if (res>=0)
                break;

            Shutdown();
            Init(this.connection);
        }
        return res;
    }

    @Override
    public String stat(String path) {
        String str = null;
        for(int i=0;i<num_retries;i++) {
            str = super.stat(path);
            if (str.charAt(0)!='*')
                break;

            Shutdown();
            Init(this.connection);
        }
        return str;
    }
}




