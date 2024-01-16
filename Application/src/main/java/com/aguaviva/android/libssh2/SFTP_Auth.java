package com.aguaviva.android.libssh2;

import android.util.Log;

public class SFTP_Auth {
    private static final String TAG = "MyCloudProvider-Auth";
    public int ssh2_session_id = -1;
    public int ssh2_sftp_session = -1;
    Connection connection;
    public boolean Connect(Connection connection) {
        // bail out if already connected
        if (ssh2_session_id >= 0) {
            if ((this.connection.hostname != null) && connection.hostname.equals(this.connection.hostname)) {
                Log.i(TAG, String.format("Already connected ", ssh2_session_id, ssh2_sftp_session));
                return true;
            } else {
                Disconnect();
            }
        }

        this.connection = connection;

        ssh2_session_id = Ssh2.session_connect(connection.hostname, connection.port);
        if (ssh2_session_id>=0) {

            int res = Ssh2.session_auth(ssh2_session_id, connection.username, connection.pubKeyFilename, connection.privKeyFilename, "");
            if (res == 0) {
                ssh2_sftp_session = Ssh2.sftp_init(ssh2_session_id);
                if(ssh2_sftp_session>=0)
                {
                    Log.i(TAG, String.format("Transfer connected ssh2:%d sftp:%d", ssh2_session_id, ssh2_sftp_session));
                    int blocking = Ssh2.session_get_blocking(ssh2_session_id);
                    Ssh2.session_set_blocking(ssh2_session_id, 1);

                    return true;
                }
            }

            Log.e(TAG, String.format("Error session_auth %s", getLastError()));
            Ssh2.session_disconnect(ssh2_session_id);
            ssh2_session_id = -1;
        }

        Log.e(TAG, String.format("Error session_connect %s", getLastError()));
        return false;
    }


    public boolean Disconnect() {
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

    public String getLastError() {
        return Ssh2.session_last_error(ssh2_session_id);
    }
    public int getLastErrorNum() {
        return Ssh2.session_last_errorno(ssh2_session_id);
    }
    public int getSftpLastError() {
        return Ssh2.sftp_last_error(ssh2_sftp_session);
    }
}
