package com.example.bazmeraah;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.IBinder;
import android.media.ToneGenerator;
import android.media.AudioManager;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.content.Context;
import android.os.Handler;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

import androidx.core.app.NotificationCompat;

public class WifiMonitorService extends Service {

    BroadcastReceiver wifiReceiver;
    boolean wasConnected = true;
    ToneGenerator toneGen;

    Handler handler = new Handler();
    boolean isBeeping = false;

    TextToSpeech tts;
    boolean hasSpoken = false; // 🔥 NEW

    @Override
    public void onCreate() {
        super.onCreate();

        // 🔥 FOREGROUND SERVICE
        String channelId = "wifi_monitor";

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "WiFi Monitor",
                    NotificationManager.IMPORTANCE_LOW
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Monitoring Active")
                .setContentText("Stick connection monitoring...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();

        startForeground(1, notification);

        // 🔊 Tone
        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        // 🗣 Text To Speech
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
            }
        });

        // 📡 Receiver
        wifiReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo networkInfo = cm.getActiveNetworkInfo();

                boolean isConnected = (networkInfo != null && networkInfo.isConnected());

                if (wasConnected && !isConnected) {

                    new Handler().postDelayed(() -> {

                        ConnectivityManager cm2 = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                        NetworkInfo ni2 = cm2.getActiveNetworkInfo();

                        if (ni2 == null || !ni2.isConnected()) {

                            startBeep(); // 🔥 alert start
                        }

                    }, 2000);
                }

                wasConnected = isConnected;
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(wifiReceiver, filter);
    }

    // 🔊 Beep + ONE-TIME Voice (10 sec)
    private void startBeep() {

        if (isBeeping) return;

        isBeeping = true;
        hasSpoken = false; // reset

        long startTime = System.currentTimeMillis();

        handler.post(new Runnable() {
            @Override
            public void run() {

                // 🔊 BEEP
                toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500);

                // 🗣 VOICE (ONLY ONCE)
                if (tts != null && !hasSpoken) {
                    tts.speak("WiFi disconnected", TextToSpeech.QUEUE_FLUSH, null, null);
                    hasSpoken = true;
                }

                if (System.currentTimeMillis() - startTime < 10000) {
                    handler.postDelayed(this, 1000);
                } else {
                    isBeeping = false;
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (wifiReceiver != null) {
            unregisterReceiver(wifiReceiver);
        }

        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}