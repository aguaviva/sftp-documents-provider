package com.aguaviva.android.libssh2;

import android.util.Base64;
import android.util.Log;

import org.json.JSONException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class SFTP_Auth {
    private static final String TAG = "MyCloudProvider-Auth";
    public int ssh2_session_id = -1;
    public int ssh2_sftp_session = -1;
    Connection connection;

    public boolean Connect(Connection connection, boolean authenticate) {
        // bail out if already connected
        if (ssh2_session_id >= 0) {
            if ((this.connection.hostname != null) && connection.hostname.equals(this.connection.hostname)) {
                Log.i(TAG, String.format("Already connected %d %d", ssh2_session_id, ssh2_sftp_session));
                return true;
            } else {
                Disconnect();
            }
        }

        this.connection = connection;

        ssh2_session_id = Ssh2.session_connect(connection.hostname, connection.port);
        if (ssh2_session_id < 0) {
            Log.e(TAG, String.format("Error session_connect %s", getLastError()));
            return false;
        }

        int blocking = Ssh2.session_get_blocking(ssh2_session_id);
        Ssh2.session_set_blocking(ssh2_session_id, 1);

        if (authenticate) {
            if (Auth()) {
                return true;
            } else {
                Disconnect();
                return false;
            }
        }

        return true;
    }

    public boolean Connect(String connectionName, boolean authenticate) throws RuntimeException {
        Log.i(TAG, String.format("Connecting Begin: %s", connectionName));

        Connection connection;
        try {
            connection = helpers.loadConnection(connectionName);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Log.i(TAG, String.format("Resolving %s", connection.hostname));
        try {
            connection.hostname = InetAddress.getByName(connection.hostname).getHostAddress();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        Log.i(TAG, String.format("Resolved %s", connection.hostname));

        boolean res =  Connect(connection, authenticate);
        Log.i(TAG, String.format("Connecting End: %s  res: %s", connectionName, res?"OK":"Error"));
        return res;
    }

    public boolean Disconnect() {

        sftp_shutdown();

        if (ssh2_session_id<0) {
            Log.w(TAG, "Error session_disconnect: Already disconnected");
            return false;
        }

        ssh2_session_id = -1;

        if (Ssh2.session_disconnect(ssh2_session_id) <0) {
            Log.w(TAG, "Error session_disconnect " + ssh2_session_id);
            return false;
        }

        return true;
    }

    public String GetFingerprint() {
        byte[] hash = Ssh2.get_host_key_hash(ssh2_session_id);
        String fingerprint = new String(Base64.encode(hash, hash.length));
        return fingerprint;
    }

    public boolean Auth() {
        String pubKeyPath = helpers.GetPublicKeyFilename(connection.keyname);
        String privKeyPath = helpers.GetPrivateKeyFilename(connection.keyname);
        int res = Ssh2.session_auth(ssh2_session_id, connection.username, pubKeyPath, privKeyPath, "");
        if (res < 0) {
            Log.e(TAG, String.format("Error auth %s", getLastError()));
            return false;
        }

        if (sftp_init()==false) {
            return false;
        }

        return true;
    }

    private boolean sftp_init() {
        ssh2_sftp_session = Ssh2.sftp_init(ssh2_session_id);
        if (ssh2_sftp_session < 0) {
            Log.i(TAG, String.format("Error sftp_init: %s", getLastError()));
            return false;
        }
        return true;
    }

    private boolean sftp_shutdown() {
        if (ssh2_sftp_session < 0) {
            Log.w(TAG, "Error sftp_shutdown: Already shutdown");
            return false;
        }

        ssh2_sftp_session = -1;
        if (Ssh2.sftp_shutdown(ssh2_sftp_session) < 0) {
            Log.w(TAG, "Error sftp_shutdown " + ssh2_sftp_session + " " + getLastError());
            return false;
        }
        return true;
    }

    public String getRoot() { return connection.root; }
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
