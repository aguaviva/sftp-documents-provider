/*
 * Copyright 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aguaviva.android.sftpstorageprovider;


import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;

import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import com.aguaviva.android.libssh2.helpers;


/**
 * A simple launcher activity containing a summary sample description
 * and a few action bar buttons.
 */
public class ActivityListConnections extends FragmentActivity {

    public static final String TAG = "MainActivity";

    public static final String FRAGTAG = "StorageProviderFragment";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (getSupportFragmentManager().findFragmentByTag(FRAGTAG) == null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            StorageProviderFragment fragment = new StorageProviderFragment();
            transaction.add(fragment, FRAGTAG);
            transaction.commit();
        }

        helpers.Init(this);
        rebuildConnectionsList();
    }

    private void rebuildConnectionsList() {
        ListView simpleList = (ListView)findViewById(R.id.list_view);
        ArrayAdapter<String> arrayAdapter= utils.populateListView(this, "+ Add Connections", helpers.getFilesConnections() );
        simpleList.setAdapter(arrayAdapter);
        simpleList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int pos, long l) {
                if (pos==adapterView.getCount()-1)
                {
                    Intent myIntent = new Intent(ActivityListConnections.this, ActivityFormConnection.class);
                    myIntent.putExtra("connection_name", "");
                    startActivityForResult(myIntent, 0);
                }
            }
        });
        simpleList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View parent, int pos, long id) {
                Intent myIntent = new Intent(ActivityListConnections.this, ActivityFormConnection.class);
                if (pos==adapterView.getCount()-1) {
                    myIntent.putExtra("connection_name", "");
                } else {
                    myIntent.putExtra("connection_name", ((TextView) parent).getText().toString());
                }
                startActivityForResult(myIntent, 0);
                return true;
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    public void onActivityResult(int requestCode, int resultCode,Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        rebuildConnectionsList();
    }

}
