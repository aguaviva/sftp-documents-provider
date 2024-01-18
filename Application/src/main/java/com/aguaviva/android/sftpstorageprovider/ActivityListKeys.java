package com.aguaviva.android.sftpstorageprovider;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import com.aguaviva.android.libssh2.helpers;
import androidx.fragment.app.FragmentActivity;


public class ActivityListKeys extends FragmentActivity {

    ListView simpleList;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_key_manager);

        simpleList = (ListView) findViewById(R.id.listKeys);
        rebuildKeysList();
    }

    private void rebuildKeysList() {
        ArrayAdapter<String> arrayAdapter= utils.populateListView(this, "+ Add Keys", helpers.getFilesKeys() );
        simpleList.setAdapter(arrayAdapter);
        simpleList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View parent, int pos, long id) {
                if (pos==adapterView.getCount()-1)
                {
                    Intent myIntent = new Intent(ActivityListKeys.this, ActivityFormKey.class);
                    myIntent.putExtra("key_name", "");
                    startActivityForResult(myIntent,0);
                } else {
                    adapterView.setSelection(pos);
                }
            }
        });

        simpleList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View parent, int pos, long id) {
                Intent myIntent = new Intent(ActivityListKeys.this, ActivityFormKey.class);
                if (pos==adapterView.getCount()-1) {
                    myIntent.putExtra("key_name", "");
                }
                else {
                    myIntent.putExtra("key_name", ((TextView) parent).getText().toString());
                }
                startActivityForResult(myIntent,0);
                return true;
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        rebuildKeysList();
    }

}
