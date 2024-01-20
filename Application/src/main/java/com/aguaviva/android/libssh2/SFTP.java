package com.aguaviva.android.libssh2;

import java.io.FileNotFoundException;
import java.io.IOException;

public class SFTP extends SFTP_Auth{

    public SftpFile getFileHandler(String filename, String mode) throws FileNotFoundException {
        SftpFile mFile = new SftpFile();
        mFile.open(ssh2_sftp_session, getRoot() + filename, mode, this);
        return mFile;
    }

    public interface onGetFileListener {
        boolean listen(String file);
        void done();
    }
    public synchronized int ls(String path, SFTP.onGetFileListener listener) {

        String pathname = getRoot() + path;

        int sftp_handle_id = Ssh2.opendir(ssh2_sftp_session, pathname);
        if (sftp_handle_id>=0) {
            while (true) {
                String entry = Ssh2.readdir(sftp_handle_id);
                if (entry.equals(""))
                    break;

                // resolve symbolic links
                if (entry.startsWith("l")) {
                    String[] fields = entry.split(" ",4);
                    try {
                        entry = stat(path + "/" + fields[3]) + " " + fields[3];
                    }
                    catch(FileNotFoundException e) {

                    }
                }

                if (listener.listen(entry) == false)
                    break;
            }
            if (Ssh2.closedir(sftp_handle_id)< 0) {
                //Log.e(TAG, "closedir " + getLastError());
            }
            listener.done();
        } else {
            //Log.e(TAG, "Put: Can't Ssh2.opendir " + path + " " + getSftpLastError() + " " + getLastError());
        }

        return getLastErrorNum();
    }

    public synchronized String stat(String path) throws FileNotFoundException {
        String res = Ssh2.sftp_stat(ssh2_sftp_session, getRoot() + path);
        if (res.startsWith("*")) {
            throw new FileNotFoundException(getLastError());
        }
        return res;
    }

    public synchronized int exec(String command) {
        return Ssh2.exec(ssh2_session_id, command);
    }

    public synchronized int rename(String currentName, String newName) {
        return Ssh2.rename( ssh2_sftp_session, getRoot() + currentName, getRoot() + newName );
    }

    public synchronized int cp(String source, String target) {
        String command = String.format("cp %s %s", getRoot() + source, getRoot() + target);
        return exec(command);
    }

    public synchronized int rm(String file) {
        String path = connection.root + file;
        String command = String.format("rm %s", path.replace(" ", "\\ ").replace("\"", "\\\""));
        return exec(command);
    }
}
