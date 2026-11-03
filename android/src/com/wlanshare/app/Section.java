package com.wlanshare.app;

import android.view.View;

/** 一个底部导航分区。 */
public interface Section {

    View view();

    void refresh();

    default void onShow() {
    }

    /** 返回 true 表示已消费返回键（例如从聊天详情回到列表）。 */
    default boolean back() {
        return false;
    }
}
