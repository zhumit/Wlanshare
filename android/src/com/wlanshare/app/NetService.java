package com.wlanshare.app;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** 前台服务：让进程在后台存活，文件传输不会因为切到后台而中断。 */
public class NetService extends Service {

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            Notification n = ((App) getApplication()).buildNotification();
            startForeground(1001, n);
        } catch (Exception ignored) {
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
