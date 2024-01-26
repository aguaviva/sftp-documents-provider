package com.aguaviva.android.sftpstorageprovider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class connectionPool {
/*
    private static final int NUM_CONNECTIONS = 5;
    Map<String, Connection> connections;
    Map<String, List<SFTP>> connected;
    Map<String, List<SFTP>> busy = new HashMap<String, List<SFTP>>();
    List<SFTP> disconnectted;

    public void init(Map<String, Connection> connections) {
        this.connections = connections;

        for (int i = 0; i < NUM_CONNECTIONS; i++) {
            SFTP sftp = new SFTP_retry();
            disconnectted.add(sftp);
        }
    }

    public SFTP get(Map<String, List<SFTP>> database, String connectionName) {
        List<SFTP> sftpList = database.get(connectionName);
        if (sftpList != null)
            return null;
        SFTP sftp = sftpList.remove(0);
        database.put(connectionName, sftpList);
        return sftp;
    }

    public void put(Map<String, List<SFTP>> database, String connectionName, SFTP sftp) {
        List<SFTP> sftpList = database.get(connectionName);
        if (sftpList != null)
            sftpList = new ArrayList<SFTP>();
        sftpList.add(sftp);
        database.put(connectionName, sftpList);
    }


    public SFTP getConnection(String connectionName) {

        // do we have a connected one?
        //
        synchronized (this) {
            SFTP sftp =  get(connected, connectionName);
            if (sftp != null) {
                put(connected, connectionName, sftp);
                return sftp;
            }
        }

        // take a disconnected one
        //
        synchronized (disconnectted) {
            if (disconnectted.size() > 0) {
                SFTP sftp = disconnectted.remove(0);
                Connection connection = connections.get(connectionName);
                sftp.Init(connection);
                return sftp;
            }
        }

        // ruse a connected one from another host
        //
    }
*/
}
