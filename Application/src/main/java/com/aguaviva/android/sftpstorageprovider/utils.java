package com.aguaviva.android.sftpstorageprovider;

import android.app.Activity;
import android.widget.ArrayAdapter;

import java.io.File;

public class utils {


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
}