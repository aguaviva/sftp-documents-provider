package com.example.android.storageprovider;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.example.android.common.activities.SampleActivityBase;

import java.io.IOException;

public class KeyFormActivity extends SampleActivityBase {
    EditText editKeyName;
    EditText editTextPublicKey;
    EditText editTextPrivateKey;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_key_form);

        editKeyName = (EditText) findViewById(R.id.editKeyName);
        editTextPublicKey = (EditText) findViewById(R.id.editTextPublicKey);
        editTextPrivateKey = (EditText) findViewById(R.id.editTextPrivateKey);

        Intent myIntent = getIntent(); // gets the previously created intent
        String keyname = myIntent.getStringExtra("key_name");
        if (keyname.equals("")==false) {
            editKeyName.setText(keyname);

            // fill UI from keys folder
            try {
                editTextPublicKey.setText(helpers.loadPublicKey(keyname));
                editTextPrivateKey.setText(helpers.loadPrivateKey(keyname));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        Button buttonSave = (Button) findViewById(R.id.buttonSave);
        buttonSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    helpers.saveKeys(editKeyName.getText().toString(), editTextPublicKey.getText().toString(), editTextPrivateKey.getText().toString());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                finish();
            }
        });

        Button buttonCancel = (Button) findViewById(R.id.buttonCancel);
        buttonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });


        Button buttonRemove = (Button) findViewById(R.id.buttonRemove);
        buttonRemove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                helpers.deleteKeys(editKeyName.getText().toString());
                finish();
            }
        });

    }

}
