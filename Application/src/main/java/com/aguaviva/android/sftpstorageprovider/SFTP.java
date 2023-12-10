package com.aguaviva.android.sftpstorageprovider;

import android.content.Context;
import android.os.ParcelFileDescriptor;

import com.example.android.common.logger.Log;

import java.io.IOException;

public class SFTP {

    private static final String TAG = "MyCloudProvider-background";
    int ssh2_session_id = -1;
    int ssh2_sftp_session = -1;

    long last_active = 0;

    boolean is_active = false;

    String CHANNEL_ID = "dsfsdf1";
    int PROGRESS_MAX = 100;
    int notificationId = 1;

    String root;

    ProgressNotification pn;

    public SFTP(Context context) {
        pn = new ProgressNotification(context, 1);
    }

    public boolean Init(String hostname, int port, String username, String pubKeyFilename, String privKeyFilename, String root)
    {
        is_active = false;

        this.root = root;

        ssh2_session_id = Ssh2.session_connect(hostname, port);
        if (ssh2_session_id>=0) {

            int res = Ssh2.session_auth(ssh2_session_id, username, pubKeyFilename, privKeyFilename, "");
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
        if (Ssh2.sftp_shutdown(ssh2_sftp_session) != 0) {
            Log.w(TAG, "Failed sftp_shutdown " + ssh2_sftp_session + " " + getLastError());
            return false;
        }

        if (Ssh2.session_disconnect(ssh2_session_id) != 0) {
            Log.w(TAG, "Failed sftp_shutdown " + ssh2_session_id);
            return false;
        }

        return true;
    }

    public void get(String documentId, ParcelFileDescriptor writeEnd)
    {
        assert is_active==false;

        is_active = true;

        pn.start_notification("Downloading " + root+documentId);
        //Log.i(TAG, "Get: Downloading " + documentId);

        ParcelFileDescriptor.AutoCloseOutputStream outputStream = new ParcelFileDescriptor.AutoCloseOutputStream(writeEnd);

        int sftp_handle_id = Ssh2.openfile(ssh2_sftp_session, root+documentId, Ssh2.LIBSSH2_FXF_READ);
        if (sftp_handle_id >= 0) {
            String permissions = Ssh2.get_permissions(ssh2_sftp_session, root+documentId);
            String p[] = permissions.split(" ");
            if (p[0]!="*") {
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
                                pn.update_progress(progress);
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
                    Log.e(TAG, "IOException reading " + root+documentId + " " + e.getMessage());
                }
                //Log.i(TAG, "done ");

                pn.stop_progress();

                last_active = System.currentTimeMillis();
            }
            else {
                Log.e(TAG, "Ssh2.get_permissions " + root+documentId + " " + getLastError());
            }

            if (Ssh2.closefile(sftp_handle_id) != 0) {
                Log.e(TAG, "closefile " + root+documentId + " " + getLastError());
            }
        }
        else {
            Log.e(TAG, "Can't Ssh2.get_permissions " + root+documentId + " " + getLastError());
        }

        is_active = false;

    }

    public void put(String documentId, ParcelFileDescriptor readEnd)
    {
        assert is_active==false;

        is_active = true;

        pn.start_notification("Uploading " + root+documentId);
        Log.i(TAG, "Put: Uploading " + root+documentId);

        ParcelFileDescriptor.AutoCloseInputStream inputStream = new ParcelFileDescriptor.AutoCloseInputStream(readEnd);

        int sftp_handle_id = Ssh2.openfile(ssh2_sftp_session, root+documentId, Ssh2.LIBSSH2_FXF_WRITE | Ssh2.LIBSSH2_FXF_CREAT);
        if (sftp_handle_id > 0) {

            long file_size = 100000;

            long last_progress = 0;
            int progress = 0;
            try {
                byte[] buffer = new byte[128 * 1024];
                long total_sent = 0;
                while (true) {

                    int length = inputStream.read(buffer);
                    if (length > 0) {
                        int res = Ssh2.writefile(sftp_handle_id, buffer, length);
                        if (res < 0) {
                            Log.e(TAG, String.format("Put: Ssh2.writefile err: %d ssh2:%d sftp:%d", length, ssh2_session_id, ssh2_sftp_session));
                            Log.e(TAG, String.format("Put: Err: " + Ssh2.session_last_error(ssh2_session_id)));
                            break;
                        }

                        total_sent += length;
                        progress = (int) ((total_sent * 100) / file_size);
                        if (last_progress != progress) {
                            last_progress = progress;
                            pn.update_progress(progress);
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
                Log.e(TAG, "Put: IOException writing " + root+documentId + " " + e.getMessage());
            }
            //Log.i(TAG, "done ");

            pn.stop_progress();

            last_active = System.currentTimeMillis();

            if (Ssh2.closefile(sftp_handle_id) != 0) {
                Log.e(TAG, "Put: closefile " + root+documentId + " " + getLastError());

            }
        }
        else {
            Log.e(TAG, "Put: Can't Ssh2.openfile " + root+documentId + " " + getLastError());
        }
        is_active = false;
    }

    interface Listener {
        boolean listen(String file);
    }
    public void ls(String path, Listener listener) throws Exception {

        int sftp_handle_id = Ssh2.opendir(ssh2_sftp_session, root+path);
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
        return Ssh2.get_permissions(ssh2_sftp_session, root+path);
    }

    public int exec(String command) {
        return Ssh2.exec(ssh2_session_id, command);
    }

    public int rename(String currentName, String newName) {
        return Ssh2.rename( ssh2_sftp_session, root+currentName, root+newName );
    }

    public String getLastError() {
        return Ssh2.session_last_error(ssh2_session_id);
    }

}
