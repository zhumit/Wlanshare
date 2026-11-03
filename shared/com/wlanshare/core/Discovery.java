package com.wlanshare.core;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 局域网自动发现：组播 + 广播双通道。
 * 监听端口在 BASE_PORT..BASE_PORT+PORT_COUNT 之间取第一个可用端口（支持同机多实例）。
 */
public class Discovery {

    public static final int BASE_PORT = 45789;
    public static final int PORT_COUNT = 6;
    public static final String GROUP = "239.255.42.99";

    public interface Sink {
        void onHello(Map<String, Object> hello, String host);

        void onBye(String id);
    }

    public interface Announcer {
        Map<String, Object> frame();
    }

    private final Sink sink;
    private volatile Announcer announcer;
    private MulticastSocket socket;
    private int port = BASE_PORT;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread rx;
    private Thread tx;
    private volatile long lastSend;

    public Discovery(Sink sink) {
        this.sink = sink;
    }

    public int port() {
        return port;
    }

    public void setAnnouncer(Announcer a) {
        this.announcer = a;
    }

    public void start() {
        if (running.get()) {
            return;
        }
        for (int i = 0; i < PORT_COUNT && socket == null; i++) {
            int p = BASE_PORT + i;
            try {
                MulticastSocket ms = new MulticastSocket(null);
                // 独占绑定：同一台机器上多个客户端/多开实例会自动顺延到下一个端口，
                // 避免因 Windows 的端口复用导致广播只被其中一个进程收到。
                ms.setReuseAddress(false);
                ms.bind(new InetSocketAddress(p));
                ms.setTimeToLive(4);
                ms.setLoopbackMode(false);
                try {
                    ms.joinGroup(new InetSocketAddress(InetAddress.getByName(GROUP), p), null);
                } catch (Exception e) {
                    // 组播不可用时仍可用广播
                }
                socket = ms;
                port = p;
            } catch (Exception e) {
                // 端口占用，试下一个
            }
        }
        if (socket == null) {
            return;
        }
        running.set(true);
        rx = new Thread(this::receiveLoop, "discovery-rx");
        rx.setDaemon(true);
        rx.start();
        tx = new Thread(this::announceLoop, "discovery-tx");
        tx.setDaemon(true);
        tx.start();
    }

    public void announceNow() {
        sendOnce();
    }

    private void announceLoop() {
        while (running.get()) {
            sendOnce();
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void sendOnce() {
        Announcer a = announcer;
        if (a == null || socket == null) {
            return;
        }
        Map<String, Object> f;
        try {
            f = a.frame();
        } catch (Exception e) {
            return;
        }
        if (f == null) {
            return;
        }
        byte[] data = Json.write(f).getBytes(Wire.UTF8);
        List<InetAddress> targets = new ArrayList<InetAddress>();
        try {
            targets.add(InetAddress.getByName(GROUP));
        } catch (Exception ignored) {
        }
        try {
            targets.add(InetAddress.getByName("255.255.255.255"));
        } catch (Exception ignored) {
        }
        targets.addAll(broadcasts());
        for (int i = 0; i < PORT_COUNT; i++) {
            for (InetAddress addr : targets) {
                try {
                    socket.send(new DatagramPacket(data, data.length, addr, BASE_PORT + i));
                } catch (Exception ignored) {
                }
            }
        }
        lastSend = System.currentTimeMillis();
    }

    private List<InetAddress> broadcasts() {
        List<InetAddress> out = new ArrayList<InetAddress>();
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress b = ia.getBroadcast();
                    if (b != null) {
                        out.add(b);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void receiveLoop() {
        byte[] buf = new byte[8192];
        while (running.get()) {
            try {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                socket.receive(pkt);
                String s = new String(pkt.getData(), pkt.getOffset(), pkt.getLength(), Wire.UTF8);
                final Map<String, Object> m = Json.asObj(Json.parse(s));
                final String host = pkt.getAddress() == null ? "" : pkt.getAddress().getHostAddress();
                String t = Json.str(m, "t", "");
                if ("hello".equals(t)) {
                    sink.onHello(m, host);
                } else if ("bye".equals(t)) {
                    sink.onBye(Json.str(m, "id", ""));
                }
            } catch (Exception e) {
                if (!running.get()) {
                    return;
                }
            }
        }
    }

    public void stop() {
        running.set(false);
        Announcer a = announcer;
        if (a != null && socket != null) {
            try {
                Map<String, Object> f = Json.obj();
                f.put("t", "bye");
                f.putAll(a.frame());
                byte[] data = Json.write(f).getBytes(Wire.UTF8);
                for (int i = 0; i < PORT_COUNT; i++) {
                    socket.send(new DatagramPacket(data, data.length, InetAddress.getByName(GROUP), BASE_PORT + i));
                    socket.send(new DatagramPacket(data, data.length, InetAddress.getByName("255.255.255.255"), BASE_PORT + i));
                }
            } catch (Exception ignored) {
            }
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Exception ignored) {
        }
    }

    public long lastSendTime() {
        return lastSend;
    }

    public static String localIp() {
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            String fallback = null;
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress a = ia.getAddress();
                    if (a == null || a.getAddress().length != 4) {
                        continue;
                    }
                    String ip = a.getHostAddress();
                    if (ip.startsWith("169.254.")) {
                        fallback = fallback == null ? ip : fallback;
                        continue;
                    }
                    return ip;
                }
            }
            if (fallback != null) {
                return fallback;
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    public static DatagramSocket dummy() throws IOException {
        return new DatagramSocket(0);
    }
}
