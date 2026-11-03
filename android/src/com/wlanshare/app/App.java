package com.wlanshare.app;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;

import com.wlanshare.core.Node;

/** 进程级单例：保持 Node 只有一个实例，Activity 重建也不会断开连接。 */
public class App extends Application {

    public static final String CHANNEL = "wlanshare";
    public static App inst;
    public Node node;

    @Override
    public void onCreate() {
        super.onCreate();
        inst = this;
        createChannel();
        node = new Node(new AndroidPlatform(this));
        node.start();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm == null) {
                return;
            }
            NotificationChannel ch = new NotificationChannel(CHANNEL, "局域网服务",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ch.setDescription("保持设备可被局域网发现");
            nm.createNotificationChannel(ch);
        }
    }

    public Notification buildNotification() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, flags);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        b.setContentTitle("WlanShare 正在运行")
                .setContentText("同一 WiFi 下的设备可以发现你")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pi)
                .setOngoing(true);
        return b.build();
    }
}
