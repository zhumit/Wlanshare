package com.wlanshare.app;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;

import com.wlanshare.core.Platform;

import java.io.File;

/** Android 平台实现：数据放在应用外部私有目录，无需存储权限。 */
public class AndroidPlatform implements Platform {

    private final Context ctx;
    private WifiManager.MulticastLock lock;

    public AndroidPlatform(Context ctx) {
        this.ctx = ctx;
        try {
            WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                lock = wm.createMulticastLock("wlanshare");
                lock.setReferenceCounted(false);
                lock.acquire();
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public File dataDir() {
        File ext = ctx.getExternalFilesDir(null);
        File dir = new File(ext != null ? ext : ctx.getFilesDir(), "share");
        dir.mkdirs();
        return dir;
    }

    @Override
    public String deviceName() {
        String m = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER;
        String model = Build.MODEL == null ? "" : Build.MODEL;
        if (model.toLowerCase().startsWith(m.toLowerCase())) {
            return model;
        }
        return (m + " " + model).trim();
    }

    @Override
    public int deviceType() {
        return 1;
    }

    @Override
    public String defaultNick() {
        String model = Build.MODEL == null ? "" : Build.MODEL.trim();
        if (model.isEmpty()) {
            return "我的手机";
        }
        return model.length() > 14 ? model.substring(0, 14) : model;
    }
}
