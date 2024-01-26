package com.aguaviva.android.libssh2;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.ProxyFileDescriptorCallback;
//import android.support.annotation.Nullable;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.system.StructStat;
import android.util.Log;

import com.aguaviva.android.libssh2.SftpFile;
import com.aguaviva.android.libssh2.Ssh2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

@TargetApi(26)
public class SftpProxyFileDescriptorCallback extends ProxyFileDescriptorCallback {
    private static final String TAG = "SftpProxyFileCallback";
    SftpFile mFile;
    Context context;

    public interface Listener {
        public void close();
    }
    private Listener listener;

    public SftpProxyFileDescriptorCallback(SftpFile mFile, Context context, Listener listener) {
        this.mFile = mFile;
        this.context = context;
        this.listener = listener;
    }

    @Override
    public long onGetSize() throws ErrnoException {
        long size;
        try {
            return mFile.size();
        } catch (IOException e) {
            throwErrnoException(e);
        }

        return 0;
    }

    @Override
    public int onRead(long offset, int size, byte[] data) throws ErrnoException {
        //final ByteBuffer buffer = mBufferPool.obtainBuffer();
        int bytes_read = 0;

/*
        long time_start = System.currentTimeMillis();
*/
        try {
            mFile.seek(offset);
            bytes_read = mFile.read(data,size);
        } catch (IOException e) {
            throwErrnoException(e);
        }
/*
        long time_end = System.currentTimeMillis();
        long time_delta = (time_end - time_start);
        float kbps = (bytes_read / 1024.0f) / ((float)time_delta/1000.0f);
        String filename = mFile.filename;
        Log.i(TAG, String.format("%s %7.2f Kb/s", filename.substring(Math.max(0,filename.lastIndexOf("/"))), kbps));

        Intent intent = new Intent("LOG_MESSAGE");    //action: "msg"
        intent.putExtra("TIME", time_end);
        intent.putExtra("DELTA", time_delta);
        intent.putExtra("DATA", (float)(bytes_read / 1024.0f));
        context.getApplicationContext().sendBroadcast(intent);
*/

        return bytes_read;
    }

    @Override
    public int onWrite(long offset, int size, byte[] data) throws ErrnoException {
        int bytes_write = 0;

        try {
            mFile.seek(offset);
            bytes_write = mFile.write(data,size);
        } catch (IOException e) {
            throwErrnoException(e);
        }
        return bytes_write;
    }

    @Override
    public void onFsync() throws ErrnoException {
        // Nothing to do
    }

    @Override
    public void onRelease() {
        try {
            mFile.close();
            if (listener != null)
                listener.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed to close file", e);
        }
    }

    private void throwErrnoException(IOException e) throws ErrnoException {
        // Hack around that ProxyFileCallback throws ErrnoException rather than IOException
        // assuming the underlying cause is an ErrnoException.
        if (e.getCause() instanceof ErrnoException) {
            throw (ErrnoException) e.getCause();
        } else {
            throw new ErrnoException("I/O", OsConstants.EIO, e);
        }
    }
}