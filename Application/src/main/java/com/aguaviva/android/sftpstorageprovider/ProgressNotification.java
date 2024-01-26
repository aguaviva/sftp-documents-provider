package com.aguaviva.android.sftpstorageprovider;

import static android.provider.Settings.System.getString;

import static androidx.core.content.ContextCompat.getSystemService;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class ProgressNotification {
    int notificationId;
    NotificationCompat.Builder builder;
    NotificationManagerCompat notificationManager;

    private int PROGRESS_MAX = 100;
    private String CHANNEL_ID = "TransferNotification";

    public ProgressNotification(Context context, int _notificationId){
        notificationId = _notificationId;
        builder = new NotificationCompat.Builder(context, CHANNEL_ID);

        notificationManager = NotificationManagerCompat.from(context);

        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is not in the Support Library.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "asd";
            String description = "shite";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            notificationManager.createNotificationChannel(channel);
        }


        /*
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID);
        builder.setContentTitle("Picture Download")
                .setContentText("Download in progress")
                .setSmallIcon(R.drawable.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        // Issue the initial notification with zero progress.
        int PROGRESS_MAX = 100;
        int PROGRESS_CURRENT = 0;
        builder.setProgress(PROGRESS_MAX, PROGRESS_CURRENT, false);
        notificationManager.notify(notificationId, builder.build());
         */
    }

    public void start_notification(String filename)
    {
        int index = filename.lastIndexOf("/");
        if (index<0)
            index=0;

        builder.setContentTitle(filename.substring(index))
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentText(filename.substring(0,index))
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        // Issue the initial notification with zero progress.
        int PROGRESS_CURRENT = 0;
        builder.setProgress(PROGRESS_MAX, 0, false);
        notificationManager.notify(notificationId, builder.build());
    }

    public void update_progress(int progress)
    {
        builder.setProgress(PROGRESS_MAX, progress, false);
        notificationManager.notify(notificationId, builder.build());
    }

    public void stop_progress()
    {
        builder.setContentText("Download complete")
                .setProgress(0,0,false);
        notificationManager.notify(notificationId, builder.build());    }

}
