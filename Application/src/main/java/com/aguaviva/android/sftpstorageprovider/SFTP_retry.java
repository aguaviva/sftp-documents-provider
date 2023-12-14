package com.aguaviva.android.sftpstorageprovider;

import android.os.ParcelFileDescriptor;

public class SFTP_retry extends SFTP {

    @Override
    public int get(String documentId, ParcelFileDescriptor writeEnd, onProgressListener listener) {
        int res = -1;
        for(int i=0;i<3;i++) {
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
        for(int i=0;i<3;i++) {
            res = super.put(documentId, readEnd, listener);
            if (res>=0)
                break;

            Shutdown();
            Init(this.connection);
        }
        return res;
    }

    public String stat(String path) {
        String str = null;
        for(int i=0;i<3;i++) {
            str = super.stat(path);
            if (str.charAt(0)!='*')
                break;

            Shutdown();
            Init(this.connection);
        }
        return str;
    }
}




