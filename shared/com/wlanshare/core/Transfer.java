package com.wlanshare.core;

/** 一次文件传输（发送或接收）。 */
public class Transfer {

    public static final int SENDING = 0;
    public static final int RECEIVING = 1;

    public static final int PENDING = 0;
    public static final int ACTIVE = 1;
    public static final int DONE = 2;
    public static final int FAILED = 3;
    public static final int REJECTED = 4;

    public String fid = "";
    public int direction = SENDING;
    public String peerId = "";
    public String peerNick = "";
    public String name = "";
    /** 文件指纹：大小 + 头尾 CRC，用于断点续传匹配。 */
    public String key = "";
    /** 续传起点（已完成字节数）。 */
    public long offset;
    public long size;
    public volatile long done;
    public String path = "";
    public int state = PENDING;
    public long ts = System.currentTimeMillis();
    public String error = "";
    /** 是否是一次断点续传（UI 文案用）。 */
    public boolean resumed;
    /** 接收方指定的保存目录（为空则用默认接收目录）。 */
    public java.io.File saveTo;

    public float progress() {
        if (size <= 0) {
            return state == DONE ? 1f : 0f;
        }
        float f = (float) ((double) done / (double) size);
        if (f < 0f) {
            return 0f;
        }
        return f > 1f ? 1f : f;
    }

    public String sizeText() {
        return Node.human(size);
    }

    public String speedText(long elapsedMs) {
        if (elapsedMs <= 0) {
            return "";
        }
        double bps = (double) done * 1000.0 / (double) elapsedMs;
        return Node.human((long) bps) + "/s";
    }
}
