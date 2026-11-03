package com.wlanshare;

import com.wlanshare.core.Platform;

import java.io.File;

/** Windows 端平台实现：数据放在 %APPDATA%\WlanShare。 */
public class PcPlatform implements Platform {

    private final File dir;

    public PcPlatform() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isEmpty()) {
            appData = System.getProperty("user.home");
        }
        dir = new File(appData, "WlanShare");
        dir.mkdirs();
    }

    @Override
    public File dataDir() {
        return dir;
    }

    @Override
    public String deviceName() {
        String host = System.getenv("COMPUTERNAME");
        if (host == null || host.isEmpty()) {
            try {
                host = java.net.InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                host = "Windows PC";
            }
        }
        return host;
    }

    @Override
    public int deviceType() {
        return 0;
    }

    @Override
    public String defaultNick() {
        String host = deviceName();
        if (host.length() > 14) {
            host = host.substring(0, 14);
        }
        return host;
    }
}
