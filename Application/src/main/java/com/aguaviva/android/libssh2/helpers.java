package com.aguaviva.android.libssh2;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.regex.Pattern;

public class helpers {

    static private File filesDir;
    static String keysDir = "/.ssh";
    static String connectionsDir = "/connections";

    static public void Init(Context context) {
        filesDir = context.getFilesDir();
        helpers.createDir(filesDir + keysDir);
        helpers.createDir(filesDir + connectionsDir);
    }

    static public String loadString(String filename) throws IOException {
        File file = new File(filesDir, filename);
        FileReader fileReader = new FileReader(file);
        BufferedReader bufferedReader = new BufferedReader(fileReader);
        StringBuilder stringBuilder = new StringBuilder();
        String line = bufferedReader.readLine();
        while (line != null) {
            stringBuilder.append(line).append("\n");
            line = bufferedReader.readLine();
        }
        bufferedReader.close();// This responce will have Json Format String
        return stringBuilder.toString();
    }

    static public void saveString(String filename, String data) throws IOException {
        File file = new File(filesDir, filename);
        FileWriter fileWriter = new FileWriter(file);
        BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
        bufferedWriter.write(data);
        bufferedWriter.close();
    }


    static public boolean createDir(String dir) {
        File keys_dir = new File(dir);

        //Ssh2.init_ssh();
        if (!keys_dir.exists() && !keys_dir.isDirectory()) {
            // create empty directory
            if (keys_dir.mkdirs()) {
                Log.i("CreateDir", "App dir created");
            } else {
                Log.w("CreateDir", "Unable to create app dir!");
                return false;
            }
        } else {
            //Log.i("CreateDir","App dir already exists");
        }
        return true;
    }

    static public File[] getFiles(String dir, String pattern) {
        File directory = new File(filesDir, dir);
        final Pattern mPattern = Pattern.compile(pattern);
        FileFilter fileFilter = new FileFilter() {
            @Override
            public boolean accept(File file) {
                boolean b = mPattern.matcher(file.getPath()).matches();
                return b;
            }
        };
        return directory.listFiles(fileFilter);
    }

    // keys

    static public File[] getFilesKeys() {
        return getFiles(keysDir, "^.*\\.(?!pub$)[^.]+$");
    }

    static public void saveKeys(String keyname, String pubKeyFilename, String privKeyFilename) throws IOException {
        String filename = keysDir + "/" + keyname;
        helpers.saveString(filename, privKeyFilename);
        helpers.saveString(filename + ".pub", pubKeyFilename);
    }

    static public void deleteKeys(String keyname) {
        String filename = filesDir + keysDir + "/" + keyname;
        File filePriv = new File(filename);
        filePriv.delete();
        File filePub = new File(filename + ".pub");
        filePub.delete();
    }

    public static String GetPublicKeyPath(String keyname) {
        return keysDir + "/" + keyname + ".pub";
    }

    static String GetPrivateKeyPath(String keyname) {
        return keysDir + "/" + keyname;
    }

    public static String loadPublicKey(String keyname) throws IOException {
        return helpers.loadString(GetPublicKeyPath(keyname));
    }

    public static String loadPrivateKey(String keyname) throws IOException {
        return helpers.loadString(GetPrivateKeyPath(keyname));
    }

    // connections

    static public File[] getFilesConnections() {
        return getFiles(connectionsDir, ".+");
    }

    static public String loadConnectionString(String connectionName) throws IOException {
        return loadString(connectionsDir + "/" + connectionName);
    }

    static public void saveConnectionString(String connectionName, String jsonData) throws IOException {
        saveString(connectionsDir + "/" + connectionName, jsonData);
    }

    static public void deleteConnection(String connectionName) {
        String keyname = filesDir + connectionsDir + "/" + connectionName;
        File filePriv = new File(keyname);
        filePriv.delete();
    }

    static public Connection loadConnection(String connectionName) throws IOException, JSONException {
        Connection c = new Connection();
        c.loadJson(loadConnectionString(connectionName));
        return c;
    }

    static public void saveConnection(String connectionName, Connection connection) throws IOException, JSONException {
        saveConnectionString(connectionName, connection.toJsonString());
    }

    static public String GetPublicKeyFilename(String keyname){
        return filesDir + GetPublicKeyPath(keyname);
    }

    static public String GetPrivateKeyFilename(String keyname){
        return filesDir + GetPrivateKeyPath(keyname);
    }
}