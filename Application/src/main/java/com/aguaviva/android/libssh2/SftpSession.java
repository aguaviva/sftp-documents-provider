package com.aguaviva.android.libssh2;

import static android.os.Build.VERSION.SDK_INT;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.storage.StorageManager;

import java.io.IOException;

public class SftpSession extends SFTP {

    static class BaseHandler extends Handler {

        BaseHandler(Looper looper) {
            super(looper);
        }

        //abstract void processMessage(Message msg);

        @Override
        public void handleMessage(Message msg) {
            synchronized (msg.obj) {
                //processMessage(msg);
                //msg.obj.notify();
            }
        }
    }

    BaseHandler mHandler;

    public SftpSession(Looper looper) {
        mHandler =new BaseHandler(looper);
        mHandler.post(new Runnable() {
            @Override
            public void run () {
                // update the ui from here
            }
        });
    }

    public interface Listener {
        public void close();
    }
    private Listener listener;

    public ParcelFileDescriptor GetParcelFileDescriptor(Context context, StorageManager storageManager, String filename, String mode, Listener listener) throws IOException {

        SftpFile mFile = getFileHandler(filename, mode);
        SftpProxyFileDescriptorCallback fd =  new SftpProxyFileDescriptorCallback(mFile, context, new SftpProxyFileDescriptorCallback.Listener() {
            @Override
            public void close() {
                listener.close();
            }
        });

        if (SDK_INT >= Build.VERSION_CODES.O) {
            return storageManager.openProxyFileDescriptor(
                    ParcelFileDescriptor.parseMode(mode),
                    fd,
                    mHandler);

        } else {
            return null;
        }
    }
}
