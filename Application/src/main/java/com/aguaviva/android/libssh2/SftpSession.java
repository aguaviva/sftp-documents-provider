package com.aguaviva.android.libssh2;

import static android.os.Build.VERSION.SDK_INT;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.storage.StorageManager;

import java.io.IOException;

public class SftpSession {
    SFTP_Auth session = new SFTP_Auth();

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

    public boolean Connect(Connection connection) {
        return session.Connect(connection);
    }

    public ParcelFileDescriptor GetParcelFileDescriptor(StorageManager storageManager, String filename, String mode) throws IOException {
        SftpFile mFile = new SftpFile();
        mFile.open(session.ssh2_sftp_session, session.connection.root + filename, mode, this);
        SftpProxyFileDescriptorCallback fd =  new SftpProxyFileDescriptorCallback(mFile);

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
