package com.wlanshare.ui;

import javax.swing.JComponent;

/** 主内容区的一个分区（设备 / 消息 / 文件 / 文档 / 设置）。 */
public interface Section {

    JComponent view();

    /** what 变化时刷新，可能来自任意线程（已切回 EDT）。 */
    void refresh(String what);

    /** 切换到该分区时调用。 */
    default void onShow() {
    }
}
