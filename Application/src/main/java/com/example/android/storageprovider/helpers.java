package com.example.android.storageprovider;

import android.app.Activity;
import android.content.Context;
import android.util.Base64;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Spinner;

import com.example.android.common.logger.Log;

import org.json.JSONException;
import org.json.JSONObject;

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

    static void Init(Context context) {
        filesDir = context.getFilesDir();
        helpers.createDir(keysDir);
        helpers.createDir(connectionsDir);
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

    static public ArrayAdapter<String> populateListView(Activity activity, String categoryName, File[] files) {

        String[] connectionList = new String[(categoryName == null) ? files.length : files.length + 1];
        for (int i = 0; i < files.length; i++) {
            connectionList[i] = files[i].getName();
        }
        if (categoryName != null)
            connectionList[files.length] = categoryName;

        return new ArrayAdapter<String>(activity, android.R.layout.simple_list_item_1, connectionList);
    }

    // keys

    static File[] getFilesKeys() {
        return getFiles(keysDir, "^.*\\.(?!pub$)[^.]+$");
    }

    static void saveKeys(String keyname, String pubKeyFilename, String privKeyFilename) throws IOException {
        String filename = keysDir + "/" + keyname;
        helpers.saveString(filename, privKeyFilename);
        helpers.saveString(filename + ".pub", pubKeyFilename);
    }

    static void deleteKeys(String keyname) {
        String filename = filesDir + keysDir + "/" + keyname;
        File filePriv = new File(filename);
        filePriv.delete();
        File filePub = new File(filename + ".pub");
        filePub.delete();
    }

    static String GetPublicKeyPath(String keyname) {
        return keysDir + "/" + keyname + ".pub";
    }

    static String GetPrivateKeyPath(String keyname) {
        return keysDir + "/" + keyname;
    }

    static String loadPublicKey(String keyname) throws IOException {
        return helpers.loadString(GetPublicKeyPath(keyname));
    }

    static String loadPrivateKey(String keyname) throws IOException {
        return helpers.loadString(GetPrivateKeyPath(keyname));
    }

    // conenctions

    static File[] getFilesConnections() {
        return getFiles(connectionsDir, ".+");
    }

    static String loadConnection(String connectionName) throws IOException {
        return loadString(connectionsDir + "/" + connectionName);
    }

    static void saveConnection(String connectionName, String jsonData) throws IOException {
        saveString(connectionsDir + "/" + connectionName, jsonData);
    }

    static void deleteConnection(String connectionName) {
        String keyname = filesDir + connectionsDir + "/" + connectionName;
        File filePriv = new File(keyname);
        filePriv.delete();
    }
}