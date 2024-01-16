package com.aguaviva.android.libssh2;

import android.annotation.TargetApi;
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

    public SftpProxyFileDescriptorCallback(
            SftpFile mFile) {
        this.mFile = mFile;
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
        try {
            mFile.seek(offset);
            bytes_read = mFile.read(data,size);
        } catch (IOException e) {
            throwErrnoException(e);
        } finally {
            //mBufferPool.recycleBuffer(buffer);
        }

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
        } catch (IOException e) {
            Log.e(TAG, "Failed to close file", e);
        }
    }

    private void throwErrnoException(IOException e) throws ErrnoException {
        // Hack around that SambaProxyFileCallback throws ErrnoException rather than IOException
        // assuming the underlying cause is an ErrnoException.
        if (e.getCause() instanceof ErrnoException) {
            throw (ErrnoException) e.getCause();
        } else {
            throw new ErrnoException("I/O", OsConstants.EIO, e);
        }
    }
}