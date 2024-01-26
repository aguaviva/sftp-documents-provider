package com.aguaviva.android.libssh2;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class Connection {
    public String hostname;
    public int port;
    public String username;
    public String keyname;
    public String root;
    public String fingerprint;

     public void loadJson(String jsonString) throws IOException, JSONException {

        JSONObject jsonObject = new JSONObject(jsonString);
        hostname = jsonObject.get("hostname").toString();
        username = jsonObject.get("username").toString();
        port = jsonObject.getInt("port");
        root = jsonObject.get("root").toString();

        keyname = jsonObject.get("keyname").toString();

        try {
            Object fingerprintObj = jsonObject.get("fingerprint");
            fingerprint = fingerprintObj.toString();
        } catch (JSONException e) {
            fingerprint = "";
        }
    }

    public String toJsonString() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("hostname", hostname);
        jsonObject.put("username", username);
        jsonObject.put("port", port);
        jsonObject.put("root", root);
        jsonObject.put("fingerprint", fingerprint);
        jsonObject.put("keyname", keyname);

        return jsonObject.toString();
    }

}

