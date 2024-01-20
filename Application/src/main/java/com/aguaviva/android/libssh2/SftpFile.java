package com.aguaviva.android.libssh2;

import android.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;

public class SftpFile {
    Object lock;
    private static final String TAG = "SftpFile";
    String filename;
    long bytes_read = 0;
    long bytes_written = 0;
    int ssh2_sftp_session = -1;
    int sftp_handle_id = -1;
    public void open(int ssh2_sftp_session, String filename, String mode, Object lock) throws FileNotFoundException {
        this.lock = lock;
        this.filename = filename;
        this.ssh2_sftp_session = ssh2_sftp_session;

        Log.v(TAG, "open " + mode + ": " + filename);
        int flags = 0;
        if (mode.startsWith("r")) {
            flags |= Ssh2.LIBSSH2_FXF_READ;
        } else if (mode.startsWith("w")) {
            flags |= Ssh2.LIBSSH2_FXF_WRITE;
        }

        synchronized (lock) {
            sftp_handle_id = Ssh2.openfile(ssh2_sftp_session, filename, flags, 0);
            if (sftp_handle_id < 0) {
                Log.e(TAG, "open " + filename);
                throw new FileNotFoundException(Ssh2.session_last_error(ssh2_sftp_session));
            }
        }
    }
    public void close() throws IOException {
        Log.v(TAG, "close: " + filename + "  ( r: " + bytes_read + " w: " + bytes_written+" )");

        int res = 0;

        synchronized (lock) {
            res = Ssh2.closefile(sftp_handle_id);
            sftp_handle_id = -1;

            if ( res < 0) {
                Log.e(TAG, "close " + filename);
                throwIOException();
            }
        }

    }
    public void seek(long offset) throws IOException {
        int res = 0;

        synchronized (lock) {
            res = Ssh2.seekfile(sftp_handle_id, offset);
            if ( res < 0) {
                Log.e(TAG, "seek " + filename);
                throwIOException();
            }
        }
    }
    public long size() throws IOException {
        String stat;

        synchronized (lock) {
            stat = Ssh2.sftp_stat(ssh2_sftp_session, filename);
            if (stat.startsWith("*")) {
                throwIOException();
            }
        }

        String p[] = stat.split(" ");
        return Long.parseLong(p[2]);
    }
    public int read(byte[] buffer, int length) throws IOException {
        int offset = 0;
        synchronized (lock) {
            while (length > 0) {
                int bytes_read = Ssh2.readfile(sftp_handle_id, buffer, offset, length);
                if (bytes_read < 0) {
                    throwIOException();
                } else if (bytes_read==0) {
                    break;
                }

                offset += bytes_read;
                length -= bytes_read;
            }
        }

        bytes_read += offset;

        return offset;
    }
    public int write(byte[] buffer, int length) throws IOException {
        int offset = 0;
        synchronized (lock) {
            while (length > 0) {
                int bytes_written = Ssh2.writefile(sftp_handle_id, buffer, offset, length);
                if (bytes_written < 0) {
                    throwIOException();
                }
                offset += bytes_written;
                length -= bytes_written;
            }
        }
        bytes_written += offset;

        return offset;
    }

    private String throwIOException() throws IOException {
        throw new IOException(Ssh2.session_last_error(ssh2_sftp_session));
    }
}
