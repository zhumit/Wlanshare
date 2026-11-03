package com.wlanshare.core;

import java.io.File;

/** 平台差异（存储路径、设备名）由各端实现。 */
public interface Platform {

    /** 应用数据目录（配置、好友、聊天记录、文档）。 */
    File dataDir();

    /** 设备展示名，例如 "DESKTOP-XXX" 或 "Xiaomi 14"。 */
    String deviceName();

    /** 0 = 电脑, 1 = 手机 */
    int deviceType();

    /** 首次启动时的默认昵称。 */
    default String defaultNick() {
        return deviceName();
    }

    /** 是否为手机端（影响 UI 提示与默认目录）。 */
    default boolean mobile() {
        return deviceType() == 1;
    }
}
