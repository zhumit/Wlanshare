package com.wlanshare.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.Random;

/** 一条 TCP 连接，负责帧收发与心跳。 */
public class Link {

    public interface Handler {
        void onFrame(Link link, Wire.Frame frame);

        void onClosed(Link link, String reason);
    }

    private static final Random RND = new Random();

    public final String cid;
    public final Socket socket;
    public final boolean outgoing;
    public volatile String peerId = "";
    public volatile String peerNick = "";
    public volatile boolean closed;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final Handler handler;
    private final Object writeLock = new Object();
    private Thread reader;

    public Link(Socket socket, boolean outgoing, Handler handler) throws IOException {
        this.socket = socket;
        this.outgoing = outgoing;
        this.handler = handler;
        this.cid = Long.toHexString(RND.nextLong() & 0x7fffffffffffffffL);
        this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream(), 64 * 1024));
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), 64 * 1024));
        try {
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
        } catch (Exception ignored) {
        }
    }

    public void start() {
        reader = new Thread(new Runnable() {
            @Override
            public void run() {
                String why = "连接已关闭";
                try {
                    while (!closed) {
                        Wire.Frame f = Wire.read(in);
                        handler.onFrame(Link.this, f);
                    }
                } catch (Exception e) {
                    why = e.getMessage() == null ? e.toString() : e.getMessage();
                } finally {
                    close(why);
                }
            }
        }, "link-rx-" + cid);
        reader.setDaemon(true);
        reader.start();
    }

    public void send(Map<String, Object> header) {
        send(header, null);
    }

    public void send(Map<String, Object> header, byte[] payload) {
        if (closed) {
            return;
        }
        synchronized (writeLock) {
            try {
                Wire.write(out, header, payload);
            } catch (IOException e) {
                close("发送失败: " + e.getMessage());
            }
        }
    }

    public void close(String reason) {
        if (closed) {
            return;
        }
        closed = true;
        try {
            socket.close();
        } catch (Exception ignored) {
        }
        synchronized (writeLock) {
            try {
                out.close();
            } catch (Exception ignored) {
            }
        }
        if (handler != null) {
            handler.onClosed(this, reason);
        }
    }

    public String key() {
        return peerId == null || peerId.isEmpty() ? "cid:" + cid : peerId;
    }
}
