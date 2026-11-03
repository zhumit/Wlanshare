package com.wlanshare.core;

import java.util.Map;

/** 局域网中的一个客户端（来自发现广播或已建立连接）。 */
public class Peer {

    public String id = "";
    public String nick = "";
    public String device = "";
    public int deviceType; // 0 = 电脑, 1 = 手机
    public String host = "";
    public int port;
    public long lastSeen;
    public boolean friend;
    public boolean connected;

    public Peer() {
    }

    public static Peer fromHello(Map<String, Object> h, String host) {
        Peer p = new Peer();
        p.id = Json.str(h, "id", "");
        p.nick = Json.str(h, "nick", "未命名");
        p.device = Json.str(h, "device", "未知设备");
        p.deviceType = Json.i32(h, "dt", 0);
        p.port = Json.i32(h, "port", 0);
        p.host = host == null ? "" : host;
        p.lastSeen = System.currentTimeMillis();
        return p;
    }

    public Map<String, Object> toHello(String id, String nick, String device, int deviceType, int port) {
        Map<String, Object> m = Json.obj();
        m.put("t", "hello");
        m.put("id", id);
        m.put("nick", nick);
        m.put("device", device);
        m.put("dt", deviceType);
        m.put("port", port);
        m.put("ver", Node.VER);
        return m;
    }

    public char initial() {
        String s = nick == null ? "" : nick.trim();
        if (s.isEmpty()) {
            return '?';
        }
        char c = s.charAt(0);
        return Character.isLetterOrDigit(c) ? Character.toUpperCase(c) : '#';
    }

    /** 稳定的头像色（0-5），由 id 派生。 */
    public int colorIndex() {
        int h = 0;
        String s = id == null ? "" : id;
        for (int i = 0; i < s.length(); i++) {
            h = 31 * h + s.charAt(i);
        }
        int v = Math.abs(h) % 6;
        return v;
    }
}
