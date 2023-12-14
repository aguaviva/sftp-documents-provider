package com.aguaviva.android.sftpstorageprovider;


import android.os.ParcelFileDescriptor;

import com.example.android.common.logger.Log;

import java.io.IOException;

public class SFTP {

    private static final String TAG = "MyCloudProvider-background";
    int ssh2_session_id = -1;
    int ssh2_sftp_session = -1;
    long last_active = 0;
    boolean is_active = false;
    Connection connection;

    interface onProgressListener {
        boolean listen(int progress);
    }

    public boolean Init(Connection connection) {
        is_active = false;

        this.connection = connection;

        ssh2_session_id = Ssh2.session_connect(connection.hostname, connection.port);
        if (ssh2_session_id>=0) {

            int res = Ssh2.session_auth(ssh2_session_id, connection.username, connection.pubKeyFilename, connection.privKeyFilename, "");
            if (res == 0) {
                ssh2_sftp_session = Ssh2.sftp_init(ssh2_session_id);
                if(ssh2_sftp_session>=0)
                {
                    Log.i(TAG, String.format("Transfer connected ssh2:%d sftp:%d", ssh2_session_id, ssh2_sftp_session));
                }
            }
            else {
                Log.e(TAG, String.format("Error session_auth %s", getLastError()));
                return false;
            }

        }
        else {
            Log.e(TAG, String.format("Error session_connect %s", getLastError()));
            return false;
        }

        int blocking = Ssh2.session_get_blocking(ssh2_session_id);
        Ssh2.session_set_blocking(ssh2_session_id, 1);

        last_active = System.currentTimeMillis();
        return true;
    }


    public boolean Shutdown() {
        if ((ssh2_sftp_session>=0) && (Ssh2.sftp_shutdown(ssh2_sftp_session) != 0)) {
            Log.w(TAG, "Failed sftp_shutdown " + ssh2_sftp_session + " " + getLastError());
            //return false;
        }
        ssh2_sftp_session = -1;

        if ((ssh2_session_id>=0) && (Ssh2.session_disconnect(ssh2_session_id) != 0)) {
            Log.w(TAG, "Failed sftp_shutdown " + ssh2_session_id);
            //return false;
        }
        ssh2_session_id = -1;

        return true;
    }

    public int get(String documentId, ParcelFileDescriptor writeEnd, onProgressListener listener)
    {
        assert is_active==false;
        String filename = connection.root + documentId;

        is_active = true;

        Log.i(TAG, "Get: Downloading " + filename);

        ParcelFileDescriptor.AutoCloseOutputStream outputStream = new ParcelFileDescriptor.AutoCloseOutputStream(writeEnd);

        int sftp_handle_id = -1;

        for(int i=0;i<3;i++) {
            sftp_handle_id = Ssh2.openfile(ssh2_sftp_session, filename, Ssh2.LIBSSH2_FXF_READ, 0);
            if (sftp_handle_id>=0)
                break;

            Shutdown();
            Init(this.connection);
        }

        if (sftp_handle_id >= 0) {
            String permissions = Ssh2.get_permissions(ssh2_sftp_session, filename);
            String p[] = permissions.split(" ");
            if (p[0].startsWith("*") == false) {
                long file_size = Long.parseLong(p[2]);
                //Log.i(TAG, "Permissions " + permissions);

                long last_progress = 0;
                int progress = 0;
                try {
                    byte[] buffer = new byte[128 * 1024];
                    long total_received = 0;
                    while (true) {
                        int length = Ssh2.readfile(sftp_handle_id, buffer);
                        if (length > 0) {
                            if (length == buffer.length)
                                outputStream.write(buffer);
                            else
                                outputStream.write(buffer, 0, length);

                            total_received += length;
                            progress = (int) ((total_received * 100) / file_size);
                            if (last_progress != progress) {
                                last_progress = progress;

                                if (listener!=null)
                                    listener.listen(progress);
                            }
                            //Log.i(TAG, "progress " + progress);
                        } else if (length == 0) {
                            break;
                        } else if (length < 0) {
                            Log.e(TAG, String.format("Ssh2.readfile err: %d ssh2:%d sftp:%d", length, ssh2_session_id, ssh2_sftp_session));
                            Log.e(TAG, String.format("Err: " + getLastError()));
                            break;
                        }

                    }
                    outputStream.flush();
                    outputStream.close();

                } catch (IOException e) {
                    Log.e(TAG, "IOException reading " + filename + " " + e.getMessage());
                }

                last_active = System.currentTimeMillis();
            }
            else {
                Log.e(TAG, "Ssh2.get_permissions " + filename + " " + getLastError());
            }

            if (Ssh2.closefile(sftp_handle_id) != 0) {
                Log.e(TAG, "closefile " + filename + " " + getLastError());
            }
        }
        else {
            int res = Ssh2.session_last_errorno(ssh2_sftp_session);
            Log.e(TAG, "Can't Ssh2.openfile " + filename + " " + getLastError());
        }

        is_active = false;

        return 0;
    }

    public int put(String documentId, ParcelFileDescriptor readEnd, onProgressListener listener)
    {
        assert is_active==false;
        String filename = connection.root + documentId;

        is_active = true;

        Log.i(TAG, "Put: Uploading " + filename);

        ParcelFileDescriptor.AutoCloseInputStream inputStream = new ParcelFileDescriptor.AutoCloseInputStream(readEnd);

        int creation_flags =
            Ssh2.LIBSSH2_FXF_WRITE |
            Ssh2.LIBSSH2_FXF_CREAT |
            Ssh2.LIBSSH2_FXF_TRUNC;

        int permissions_flags =
            Ssh2.LIBSSH2_SFTP_S_IRUSR |
            Ssh2.LIBSSH2_SFTP_S_IWUSR |
            Ssh2.LIBSSH2_SFTP_S_IRGRP |
            Ssh2.LIBSSH2_SFTP_S_IROTH;

        int sftp_handle_id = -1;

        for(int i=0;i<3;i++) {
            sftp_handle_id = Ssh2.openfile( ssh2_sftp_session, filename, creation_flags, permissions_flags);
            if (sftp_handle_id>=0)
                break;

            Shutdown();
            Init(this.connection);
        }

        if (sftp_handle_id > 0) {

            long file_size = 100000;

            long last_progress = 0;
            int progress = 0;
            try {
                byte[] buffer = new byte[128 * 1024];
                long total_sent = 0;
                while (true) {

                    int length  = inputStream.read(buffer);
                    if (length  > 0) {
                        int offset = 0;
                        while(length>0) {
                            int nwritten = Ssh2.writefile(sftp_handle_id, buffer, offset, length);
                            if (nwritten < 0) {
                                break;
                            }
                            offset += nwritten;
                            length -= nwritten;
                            total_sent += nwritten;
                        }

                        progress = (int) ((total_sent * 100) / file_size);
                        if (last_progress != progress) {
                            last_progress = progress;

                            if (listener!=null)
                                listener.listen(progress);
                        }
                        //Log.i(TAG, "progress " + progress);
                    } else if (length == 0) {
                        break;
                    } else if (length == -1) {
                        break; // done
                    } else if (length < 0) {
                        Log.e(TAG, String.format("Put: inputStream err %d %s", length, documentId));
                        break;
                    }

                }
                inputStream.close();

            } catch (IOException e) {
                Log.e(TAG, "Put: IOException writing " + filename + " " + e.getMessage());
            }
            //Log.i(TAG, "done ");

            last_active = System.currentTimeMillis();

            if (Ssh2.closefile(sftp_handle_id) != 0) {
                Log.e(TAG, "Put: closefile " + filename + " " + getLastError());

            }
        }
        else {
            Log.e(TAG, "Put: Can't Ssh2.openfile " + filename + " " + getLastError());
        }
        is_active = false;

        return 0;
    }

    interface onGetFileListener {
        boolean listen(String file);
    }
    public void ls(String path, onGetFileListener listener) throws Exception {

        String pathname = connection.root + path;

        int sftp_handle_id = -1;

        // attempt 3 times to get open the directory
        for(int i=0;i<3;i++) {
            sftp_handle_id = Ssh2.opendir(ssh2_sftp_session, pathname);
            if (sftp_handle_id>=0)
                break;

            Shutdown();
            Init(this.connection);
        }

        if (sftp_handle_id>=0) {
            while (true) {
                String entry = Ssh2.readdir(sftp_handle_id);
                if (entry.equals(""))
                    break;

                if (listener.listen(entry)==false)
                    break;
            }
            if (Ssh2.closedir(sftp_handle_id)< 0) {
                Log.e(TAG, "closedir " + getLastError());
                throw new Exception(getLastError());
            }
        } else {
            throw new Exception(getLastError());
        }

    }

    public String stat(String path) {

        String str = null;
        for(int i=0;i<3;i++) {
            str =  Ssh2.get_permissions(ssh2_sftp_session, connection.root + path);
            if (str.charAt(0)!='*')
                break;

            Shutdown();
            Init(this.connection);
        }
        return str;
    }

    public int exec(String command) {
        return Ssh2.exec(ssh2_session_id, command);
    }

    public int rename(String currentName, String newName) {
        return Ssh2.rename( ssh2_sftp_session, connection.root + currentName, connection.root + newName );
    }

    public int cp(String source, String target) {
        String command = String.format("cp %s %s", connection.root + source, connection.root + target);
        return Ssh2.exec(ssh2_session_id, command);
    }

    public int rm(String file) {
        String command = String.format("rm %s", connection.root + file);
        return Ssh2.exec(ssh2_session_id, command);
    }

    public String getLastError() {
        return Ssh2.session_last_error(ssh2_session_id);
    }

}
