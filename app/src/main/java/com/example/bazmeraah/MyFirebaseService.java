package com.example.bazmeraah;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class MyFirebaseService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        String title = "";
        String body = "";

        // 🔥 Notification payload
        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            body = remoteMessage.getNotification().getBody();
        }

        // 🔥 Data fallback
        if (remoteMessage.getData().size() > 0) {
            if (remoteMessage.getData().get("title") != null)
                title = remoteMessage.getData().get("title");

            if (remoteMessage.getData().get("body") != null)
                body = remoteMessage.getData().get("body");
        }

        showNotification(title, body);
    }

    private void showNotification(String title, String message) {

        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        String channelId = "bazm_channel_v5";

        // 🔥 SOUND URI
        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        // 🔥 CHANNEL (ANDROID 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Bazm Notifications",
                    NotificationManager.IMPORTANCE_HIGH // 🔥 MUST
            );

            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 500, 500});

            // 🔥 SOUND SET (IMPORTANT)
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();

            channel.setSound(soundUri, audioAttributes);

            manager.createNotificationChannel(channel);
        }

        // 🔥 NOTIFICATION BUILDER
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, channelId)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setSound(soundUri); // 🔔 SOUND

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }
}