package com.aguaviva.android.sftpstorageprovider;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

public class DialogYesNo {

    public void show(Context context, String question, DialogInterface.OnClickListener dialogClickListener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(question).setPositiveButton("Yes", dialogClickListener)
                .setNegativeButton("No", dialogClickListener).show();
    }
}
