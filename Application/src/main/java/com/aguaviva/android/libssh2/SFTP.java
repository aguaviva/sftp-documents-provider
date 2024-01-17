package com.aguaviva.android.libssh2;

import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.IOException;

public class SFTP extends SFTP_Auth{

    private static final String TAG = "SFTP";
    boolean is_active = false;

    interface onProgressListener {
        boolean listen(int progress);
    }

    public int get(String documentId, ParcelFileDescriptor writeEnd, onProgressListener listener)
    {
        assert is_active==false;
        String filename = connection.root + documentId;

        is_active = true;

        Log.i(TAG, "Get: Downloading " + filename);

        ParcelFileDescriptor.AutoCloseOutputStream outputStream = new ParcelFileDescriptor.AutoCloseOutputStream(writeEnd);

        long file_size = -1;
        String permissions = Ssh2.sftp_stat(ssh2_sftp_session, filename);
        String p[] = permissions.split(" ");
        if (p[0].startsWith("*") == false) {
            file_size = Long.parseLong(p[2]);
        } else {
            Log.e(TAG, "Ssh2.sftp_stat " + filename + " " + getLastError() + " " + Ssh2.sftp_last_error(ssh2_sftp_session));
        }

        int sftp_handle_id = Ssh2.openfile(ssh2_sftp_session, filename, Ssh2.LIBSSH2_FXF_READ, 0);
        if (sftp_handle_id >= 0) {
            //Log.i(TAG, "Permissions " + permissions);
            long last_progress = 0;
            int progress = 0;
            try {
                byte[] buffer = new byte[128 * 1024];
                long total_received = 0;
                while (true) {
                    int length = Ssh2.readfile(sftp_handle_id, buffer, 0, buffer.length);
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

            if (Ssh2.closefile(sftp_handle_id) != 0) {
                Log.e(TAG, "closefile " + filename + " " + getLastError());
            }
        }
        else {
            Log.e(TAG, "Can't Ssh2.openfile " + filename + " " + getLastError());
        }
        is_active = false;
        return getLastErrorNum();
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

        int sftp_handle_id = Ssh2.openfile( ssh2_sftp_session, filename, creation_flags, permissions_flags);
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
            if (Ssh2.closefile(sftp_handle_id) != 0) {
                Log.e(TAG, "Put: closefile " + filename + " " + getLastError());
            }
        }
        else {
            Log.e(TAG, "Put: Can't Ssh2.openfile " + filename + " " + getLastError());
        }
        is_active = false;

        return getLastErrorNum();
    }

    public interface onGetFileListener {
        boolean listen(String file);
        void done();
    }
    public int ls(String path, onGetFileListener listener) {

        String pathname = connection.root + path;

        int sftp_handle_id = Ssh2.opendir(ssh2_sftp_session, pathname);
        if (sftp_handle_id>=0) {
            while (true) {
                String entry = Ssh2.readdir(sftp_handle_id);
                if (entry.equals(""))
                    break;

                // resolve symbolic links
                if (entry.startsWith("l")) {
                    String[] fields = entry.split(" ",4);
                    entry = stat(path + "/" + fields[3]) + " " + fields[3];
                }

                if (listener.listen(entry) == false)
                    break;
            }
            if (Ssh2.closedir(sftp_handle_id)< 0) {
                Log.e(TAG, "closedir " + getLastError());
            }
            listener.done();
        } else {
            Log.e(TAG, "Put: Can't Ssh2.opendir " + path + " " + getSftpLastError() + " " + getLastError());
        }


        return getLastErrorNum();
    }

    public String stat(String path) {
        return  Ssh2.sftp_stat(ssh2_sftp_session, connection.root + path);
    }

    public int exec(String command) {
        return Ssh2.exec(ssh2_session_id, command);
    }

    public int rename(String currentName, String newName) {
        return Ssh2.rename( ssh2_sftp_session, connection.root + currentName, connection.root + newName );
    }

    public int cp(String source, String target) {
        String command = String.format("cp %s %s", connection.root + source, connection.root + target);
        return exec(command);
    }

    public int rm(String file) {
        String path = connection.root + file;
        String command = String.format("rm %s", path.replace(" ", "\\ ").replace("\"", "\\\""));
        return exec(command);
    }

}
