package com.wlanshare.core;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 无中心节点的通信内核：发现、好友、聊天、文件传输、Markdown 协作。
 * 所有状态变更都投递到单线程 exec 队列执行，快照方法可被 UI 线程安全调用。
 */
public class Node implements Link.Handler, Discovery.Sink {

    public static final String VER = "1.0";
    public static final int CHUNK = 32 * 1024;
    private static final int PEER_TTL = 15000;
    /** 光标标记的超时时间：超过则视为已离开该行。 */
    private static final long CURSOR_TTL = 15000;
    /** 消息可撤回时间窗（毫秒）。 */
    public static final long RECALL_WINDOW = 60000;

    public interface Listener {
        /** what: peers / chat / files / docs / friends / self / net */
        void onState(String what);

        void onChat(String peerId, boolean mine, String text, long ts);

        void onToast(String msg);
    }

    public static class ChatMsg {
        public String mid = "";
        public boolean mine;
        public String text = "";
        public long ts;
        /** 引用：被引用消息的 id / 摘要 / 作者 */
        public String quoteId = "";
        public String quoteText = "";
        public String quoteName = "";
        /** 已撤回 */
        public boolean recalled;
        /** 语音消息 */
        public boolean voice;
        public int voiceDur;
        public String voicePath = "";

        public ChatMsg(boolean mine, String text, long ts) {
            this.mid = rand(10);
            this.mine = mine;
            this.text = text;
            this.ts = ts;
        }

        public String preview() {
            if (recalled) {
                return mine ? "你撤回了一条消息" : "对方撤回了一条消息";
            }
            if (voice) {
                return "[语音] " + voiceDur + "\"";
            }
            return text;
        }
    }

    /** 一次聊天记录搜索命中。 */
    public static class ChatHit {
        public String pid = "";
        public String mid = "";
        public String text = "";
        public long ts;
        public boolean mine;
    }

    /** 待发送帧（带可选二进制负载）。 */
    private static final class Out {
        final Map<String, Object> h;
        final byte[] p;

        Out(Map<String, Object> h, byte[] p) {
            this.h = h;
            this.p = p;
        }
    }

    private static final class Gate {
        boolean decided;
        boolean ok;
        long offset;
    }

    private final Platform platform;
    private volatile Listener listener;

    public String id = "";
    public volatile String nick = "";
    public final String deviceName;
    public final int deviceType;
    public int tcpPort;
    public int udpPort;
    public final File dataDir;
    public final File partsDir;
    public final File voiceDir;
    public volatile File recvDir;
    public volatile boolean autoAcceptFiles = true;
    public volatile boolean autoAcceptFriends = true;

    /** 收到的发现广播计数（用于自检与排障）。 */
    public final java.util.concurrent.atomic.AtomicLong hellos = new java.util.concurrent.atomic.AtomicLong();
    public final Map<String, Peer> peers = new ConcurrentHashMap<String, Peer>();
    public final Map<String, String> friends = new ConcurrentHashMap<String, String>();
    /** 本地备注名：peerId -> 备注（只影响本机显示）。 */
    public final Map<String, String> aliases = new ConcurrentHashMap<String, String>();
    public final Map<String, String> friendRequests = new ConcurrentHashMap<String, String>();
    public final Map<String, List<ChatMsg>> chats = new ConcurrentHashMap<String, List<ChatMsg>>();
    /** 未读消息数：peerId -> 条数（不持久化，重启清零）。 */
    public final Map<String, Integer> unread = new ConcurrentHashMap<String, Integer>();
    /** 当前正在查看的会话（由界面设置）；该会话收到消息不计未读。 */
    public volatile String activeChat = "";
    /** 被用户手动删除的会话：不再出现在列表里，对方再发消息时自动恢复。 */
    public final Set<String> hidden = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    public final Map<String, Transfer> transfers = new ConcurrentHashMap<String, Transfer>();
    public final Map<String, Doc> docs = new ConcurrentHashMap<String, Doc>();

    private final Map<String, Link> links = new ConcurrentHashMap<String, Link>();
    private final Map<String, Long> linkSeen = new ConcurrentHashMap<String, Long>();
    private final Set<String> connecting = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final Map<String, Gate> gates = new ConcurrentHashMap<String, Gate>();
    private final Map<String, List<Out>> pending = new ConcurrentHashMap<String, List<Out>>();
    private final Map<String, RandomAccessFile> recvStreams = new ConcurrentHashMap<String, RandomAccessFile>();

    private final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>();
    private final ThreadPoolExecutor exec;
    private ScheduledExecutorService sched;
    private ServerSocket server;
    private Discovery discovery;
    private volatile boolean started;

    public Node(Platform platform) {
        this.platform = platform;
        this.deviceName = platform.deviceName();
        this.deviceType = platform.deviceType();
        this.dataDir = platform.dataDir();
        this.partsDir = new File(dataDir, "parts");
        this.voiceDir = new File(dataDir, "voice");
        this.recvDir = new File(dataDir, "recv");
        this.exec = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, queue, new java.util.concurrent.ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "wlanshare-core");
                t.setDaemon(true);
                return t;
            }
        });
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    private void post(Runnable r) {
        exec.execute(r);
    }

    private void notifyState(String what) {
        Listener l = listener;
        if (l != null) {
            try {
                l.onState(what);
            } catch (Exception ignored) {
            }
        }
    }

    private void toast(String s) {
        Listener l = listener;
        if (l != null) {
            try {
                l.onToast(s);
            } catch (Exception ignored) {
            }
        }
    }

    // ------------------------------------------------------------------ 生命周期

    public void start() {
        if (started) {
            return;
        }
        started = true;
        dataDir.mkdirs();
        recvDir.mkdirs();
        new File(dataDir, "docs").mkdirs();
        new File(dataDir, "chat").mkdirs();
        partsDir.mkdirs();
        voiceDir.mkdirs();
        loadConfig();
        loadFriends();
        loadAliases();
        loadDocs();
        if (id == null || id.isEmpty()) {
            id = rand(8);
            saveConfig();
        }
        if (nick == null || nick.isEmpty()) {
            nick = platform.defaultNick();
        }
        try {
            server = new ServerSocket(0);
            tcpPort = server.getLocalPort();
        } catch (IOException e) {
            tcpPort = 0;
        }
        if (server != null) {
            Thread t = new Thread(this::acceptLoop, "wlanshare-accept");
            t.setDaemon(true);
            t.start();
        }
        discovery = new Discovery(this);
        discovery.setAnnouncer(new Discovery.Announcer() {
            @Override
            public Map<String, Object> frame() {
                Map<String, Object> m = Json.obj();
                m.put("t", "hello");
                m.put("id", id);
                m.put("nick", nick);
                m.put("device", deviceName);
                m.put("dt", deviceType);
                m.put("port", tcpPort);
                m.put("ver", VER);
                return m;
            }
        });
        discovery.start();
        udpPort = discovery.port();
        sched = Executors.newScheduledThreadPool(2);
        sched.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                expirePeers();
            }
        }, 4, 4, TimeUnit.SECONDS);
        sched.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                heartbeat();
            }
        }, 12, 12, TimeUnit.SECONDS);
        notifyState("self");
    }

    public void stop() {
        for (RandomAccessFile f : recvStreams.values()) {
            closeQuietly(f);
        }
        recvStreams.clear();
        try {
            if (discovery != null) {
                discovery.stop();
            }
        } catch (Exception ignored) {
        }
        for (Link l : links.values()) {
            l.close("退出");
        }
        links.clear();
        try {
            if (server != null) {
                server.close();
            }
        } catch (Exception ignored) {
        }
        if (sched != null) {
            sched.shutdownNow();
        }
        started = false;
    }

    private void acceptLoop() {
        while (started && server != null && !server.isClosed()) {
            try {
                Socket s = server.accept();
                final Link l = new Link(s, false, this);
                l.start();
            } catch (Exception e) {
                if (!started) {
                    return;
                }
            }
        }
    }

    private void heartbeat() {
        long now = System.currentTimeMillis();
        for (Link l : links.values()) {
            Long seen = linkSeen.get(l.cid);
            if (seen != null && now - seen > 60000) {
                l.close("心跳超时");
                continue;
            }
            Map<String, Object> m = Json.obj();
            m.put("t", "ping");
            m.put("ts", now);
            l.send(m);
        }
    }

    private void expirePeers() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Peer p : peers.values()) {
            Link l = links.get(p.id);
            if (l != null && !l.closed) {
                // 有活跃连接就不算离线（广播被拦截但 TCP 可用的情况）
                p.lastSeen = now;
                continue;
            }
            if (now - p.lastSeen > PEER_TTL) {
                peers.remove(p.id);
                changed = true;
            }
        }
        if (changed) {
            notifyState("peers");
        }
        // 光标标记超时清除（对方可能直接关掉客户端）
        for (Doc d : docs.values()) {
            java.util.Iterator<Map.Entry<String, Long>> it = d.cursorAt.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Long> e = it.next();
                if (now - e.getValue() > CURSOR_TTL) {
                    it.remove();
                    d.cursors.remove(e.getKey());
                }
            }
        }
    }

    // ------------------------------------------------------------------ 发现

    @Override
    public void onHello(final Map<String, Object> hello, final String host) {
        hellos.incrementAndGet();
        post(new Runnable() {
            @Override
            public void run() {
                String pid = Json.str(hello, "id", "");
                if (pid.isEmpty() || pid.equals(id)) {
                    return;
                }
                Peer p = peers.get(pid);
                boolean isNew = p == null;
                String oldNick = p == null ? null : p.nick;
                if (isNew) {
                    p = Peer.fromHello(hello, host);
                    peers.put(pid, p);
                } else {
                    p.nick = Json.str(hello, "nick", p.nick);
                    p.device = Json.str(hello, "device", p.device);
                    p.deviceType = Json.i32(hello, "dt", p.deviceType);
                    int port = Json.i32(hello, "port", p.port);
                    if (port > 0) {
                        p.port = port;
                    }
                    p.host = host;
                }
                p.lastSeen = System.currentTimeMillis();
                p.friend = friends.containsKey(pid);
                Link l = links.get(pid);
                p.connected = l != null && !l.closed;
                if (isNew || (oldNick != null && !oldNick.equals(p.nick))) {
                    notifyState("peers");
                }
                if (!p.connected) {
                    connect(p);
                }
            }
        });
    }

    @Override
    public void onBye(final String pid) {
        post(new Runnable() {
            @Override
            public void run() {
                peers.remove(pid);
                Link l = links.remove(pid);
                if (l != null) {
                    l.close("对方离开");
                }
                notifyState("peers");
            }
        });
    }

    private void connect(final Peer p) {
        if (p == null || p.port <= 0 || p.host == null || p.host.isEmpty()) {
            return;
        }
        if (!connecting.add(p.id)) {
            return;
        }
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket s = new Socket();
                    s.connect(new InetSocketAddress(p.host, p.port), 2500);
                    Link l = new Link(s, true, Node.this);
                    l.peerId = p.id;
                    l.peerNick = p.nick;
                    l.send(helloFrame());
                    register(l);
                    l.start();
                } catch (Exception ignored) {
                } finally {
                    connecting.remove(p.id);
                }
            }
        }, "connect-" + p.id);
        t.setDaemon(true);
        t.start();
    }

    private Map<String, Object> helloFrame() {
        Map<String, Object> m = Json.obj();
        m.put("t", "hello");
        m.put("id", id);
        m.put("nick", nick);
        m.put("device", deviceName);
        m.put("dt", deviceType);
        m.put("port", tcpPort);
        m.put("ver", VER);
        return m;
    }

    // ------------------------------------------------------------------ 连接管理

    @Override
    public void onFrame(final Link link, final Wire.Frame frame) {
        linkSeen.put(link.cid, System.currentTimeMillis());
        post(new Runnable() {
            @Override
            public void run() {
                try {
                    handle(link, frame);
                } catch (Exception e) {
                    // 单帧异常不影响连接
                }
            }
        });
    }

    @Override
    public void onClosed(final Link link, String reason) {
        post(new Runnable() {
            @Override
            public void run() {
                Link cur = links.get(link.peerId);
                if (cur == link) {
                    links.remove(link.peerId);
                    Peer p = peers.get(link.peerId);
                    if (p != null) {
                        p.connected = false;
                    }
                    abortRecvFor(link.peerId);
                    clearPeerCursors(link.peerId);
                    notifyState("peers");
                }
            }
        });
    }

    private void register(Link l) {
        Link old = links.get(l.peerId);
        if (old != null && old != l) {
            if (!old.closed && old.cid.compareTo(l.cid) <= 0) {
                l.close("重复连接");
                return;
            }
            links.put(l.peerId, l);
            old.close("重复连接");
        } else {
            links.put(l.peerId, l);
        }
        Peer p = peers.get(l.peerId);
        if (p == null) {
            p = new Peer();
            p.id = l.peerId;
            p.nick = l.peerNick;
            p.lastSeen = System.currentTimeMillis();
            peers.put(p.id, p);
        }
        p.connected = true;
        p.friend = friends.containsKey(p.id);
        flushPending(p.id);
        notifyState("peers");
    }

    private void flushPending(String pid) {
        List<Out> list = pending.remove(pid);
        if (list == null) {
            return;
        }
        Link l = links.get(pid);
        if (l == null) {
            return;
        }
        for (Out o : list) {
            l.send(o.h, o.p);
        }
    }

    private void queueOrSend(String pid, Map<String, Object> m) {
        queueOrSend(pid, m, null);
    }

    private void queueOrSend(String pid, Map<String, Object> m, byte[] payload) {
        Link l = links.get(pid);
        if (l != null && !l.closed) {
            l.send(m, payload);
            return;
        }
        List<Out> list = pending.get(pid);
        if (list == null) {
            list = new CopyOnWriteArrayList<Out>();
            pending.put(pid, list);
        }
        if (list.size() < 60) {
            list.add(new Out(m, payload));
        }
    }

    // ------------------------------------------------------------------ 帧分发

    private void handle(Link link, Wire.Frame f) {
        String t = f.type();
        Map<String, Object> h = f.h;
        if ("hello".equals(t)) {
            String pid = Json.str(h, "id", "");
            if (pid.isEmpty() || pid.equals(id)) {
                link.close("自身连接");
                return;
            }
            link.peerId = pid;
            link.peerNick = Json.str(h, "nick", "未命名");
            Peer p = peers.get(pid);
            if (p == null) {
                p = Peer.fromHello(h, link.socket.getInetAddress().getHostAddress());
                p.port = Json.i32(h, "port", 0);
                peers.put(pid, p);
            } else {
                p.nick = Json.str(h, "nick", p.nick);
                p.device = Json.str(h, "device", p.device);
                p.deviceType = Json.i32(h, "dt", p.deviceType);
                p.host = link.socket.getInetAddress().getHostAddress();
                int port = Json.i32(h, "port", 0);
                if (port > 0) {
                    p.port = port;
                }
            }
            p.lastSeen = System.currentTimeMillis();
            register(link);
            return;
        }
        if (link.peerId == null || link.peerId.isEmpty()) {
            return;
        }
        Peer alive = peers.get(link.peerId);
        if (alive != null) {
            alive.lastSeen = System.currentTimeMillis();
            alive.connected = true;
        }
        switch (t) {
            case "ping":
                break;
            case "nick":
                onNick(link, Json.str(h, "nick", ""));
                break;
            case "friend.req":
                onFriendReq(link);
                break;
            case "friend.ack":
                onFriendAck(link, h);
                break;
            case "friend.del":
                friends.remove(link.peerId);
                saveFriends();
                notifyState("friends");
                break;
            case "chat":
                onChat(link, h, f.p);
                break;
            case "chat.recall":
                onRecall(link, h);
                break;
            case "file.offer":
                onFileOffer(link, h);
                break;
            case "file.accept":
                onFileAccept(link, h);
                break;
            case "file.reject":
                onFileReject(link, h);
                break;
            case "file.chunk":
                onFileChunk(link, f);
                break;
            case "file.done":
                onFileDone(link, h);
                break;
            case "doc.create":
                onDocCreate(link, h);
                break;
            case "doc.update":
                onDocUpdate(link, h);
                break;
            case "doc.sync":
                onDocSync(link, h);
                break;
            case "doc.aware":
                onDocAware(link, h);
                break;
            case "doc.request":
                onDocRequest(link, h);
                break;
            case "doc.cursor":
                onDocCursor(link, h);
                break;
            default:
                break;
        }
    }

    private void onNick(Link link, String newNick) {
        if (newNick == null || newNick.trim().isEmpty()) {
            return;
        }
        Peer p = peers.get(link.peerId);
        if (p != null) {
            p.nick = newNick;
        }
        link.peerNick = newNick;
        if (friends.containsKey(link.peerId)) {
            friends.put(link.peerId, newNick);
            saveFriends();
        }
        notifyState("peers");
        notifyState("friends");
    }

    // ------------------------------------------------------------------ 好友

    public void requestFriend(String pid) {
        post(new Runnable() {
            @Override
            public void run() {
                Peer p = peers.get(pid);
                String n = p != null ? p.nick : pid;
                friends.put(pid, n);
                saveFriends();
                Map<String, Object> m = Json.obj();
                m.put("t", "friend.req");
                m.put("id", id);
                m.put("nick", nick);
                queueOrSend(pid, m);
                notifyState("friends");
                notifyState("peers");
            }
        });
    }

    public void removeFriend(final String pid) {
        post(new Runnable() {
            @Override
            public void run() {
                friends.remove(pid);
                saveFriends();
                Map<String, Object> m = Json.obj();
                m.put("t", "friend.del");
                queueOrSend(pid, m);
                notifyState("friends");
                notifyState("peers");
            }
        });
    }

    public void acceptFriend(final String pid) {
        post(new Runnable() {
            @Override
            public void run() {
                String n = friendRequests.remove(pid);
                if (n == null) {
                    Peer p = peers.get(pid);
                    n = p != null ? p.nick : pid;
                }
                friends.put(pid, n);
                saveFriends();
                Map<String, Object> m = Json.obj();
                m.put("t", "friend.ack");
                m.put("accept", true);
                m.put("id", id);
                m.put("nick", nick);
                queueOrSend(pid, m);
                notifyState("friends");
                notifyState("peers");
            }
        });
    }

    public void rejectFriend(final String pid) {
        post(new Runnable() {
            @Override
            public void run() {
                friendRequests.remove(pid);
                Map<String, Object> m = Json.obj();
                m.put("t", "friend.ack");
                m.put("accept", false);
                m.put("id", id);
                queueOrSend(pid, m);
                notifyState("friends");
            }
        });
    }

    private void onFriendReq(Link link) {
        String n = link.peerNick;
        if (autoAcceptFriends) {
            friends.put(link.peerId, n);
            saveFriends();
            Map<String, Object> m = Json.obj();
            m.put("t", "friend.ack");
            m.put("accept", true);
            m.put("id", id);
            m.put("nick", nick);
            link.send(m);
            toast(n + " 已添加你为好友");
            notifyState("friends");
            notifyState("peers");
        } else {
            friendRequests.put(link.peerId, n);
            notifyState("friends");
            toast("收到好友申请：" + n);
        }
    }

    private void onFriendAck(Link link, Map<String, Object> h) {
        boolean accept = Json.bool(h, "accept", false);
        Peer p = peers.get(link.peerId);
        String n = p != null ? p.nick : link.peerNick;
        if (accept) {
            friends.put(link.peerId, n);
            saveFriends();
            notifyState("friends");
            notifyState("peers");
        } else {
            toast(n + " 拒绝了你的好友申请");
        }
    }

    // ------------------------------------------------------------------ 聊天

    private void onChat(Link link, Map<String, Object> h, byte[] payload) {
        String text = Json.str(h, "text", "");
        boolean voice = Json.bool(h, "voice", false);
        if (text.isEmpty() && !voice) {
            return;
        }
        long ts = Json.num(h, "ts", System.currentTimeMillis());
        ChatMsg m = new ChatMsg(false, text, ts);
        m.mid = Json.str(h, "mid", m.mid);
        m.quoteId = Json.str(h, "quoteId", "");
        m.quoteText = Json.str(h, "quoteText", "");
        m.quoteName = Json.str(h, "quoteName", "");
        m.voice = voice;
        m.voiceDur = Json.i32(h, "dur", 0);
        if (voice && payload != null && payload.length > 0) {
            m.voicePath = saveVoice(m.mid, payload);
        }
        chatsFor(link.peerId).add(m);
        appendChatLog(link.peerId, m);
        ensureVisible(link.peerId);
        // 不在当前查看的会话里 -> 记一条未读
        if (!link.peerId.equals(activeChat)) {
            bumpUnread(link.peerId);
        }
        Listener l = listener;
        if (l != null) {
            try {
                l.onChat(link.peerId, false, m.preview(), ts);
            } catch (Exception ignored) {
            }
        }
        notifyState("chat");
        notifyState("unread");
    }

    private String saveVoice(String mid, byte[] data) {
        try {
            voiceDir.mkdirs();
            File f = new File(voiceDir, mid + ".wav");
            java.io.FileOutputStream out = new java.io.FileOutputStream(f);
            try {
                out.write(data);
            } finally {
                out.close();
            }
            return f.getAbsolutePath();
        } catch (Exception e) {
            return "";
        }
    }

    public void sendChat(final String pid, final String text) {
        sendChat(pid, text, "", "", "");
    }

    /** 发送文本；quoteId 非空表示引用某条消息。 */
    public void sendChat(final String pid, final String text, final String quoteId,
                         final String quoteText, final String quoteName) {
        final String body = text == null ? "" : text.trim();
        if (body.isEmpty()) {
            return;
        }
        post(new Runnable() {
            @Override
            public void run() {
                ChatMsg m = new ChatMsg(true, body, System.currentTimeMillis());
                m.quoteId = nz(quoteId);
                m.quoteText = nz(quoteText);
                m.quoteName = nz(quoteName);
                chatsFor(pid).add(m);
                appendChatLog(pid, m);
                ensureVisible(pid);
                Map<String, Object> f = Json.obj();
                f.put("t", "chat");
                f.put("mid", m.mid);
                f.put("text", body);
                f.put("ts", m.ts);
                if (!m.quoteId.isEmpty()) {
                    f.put("quoteId", m.quoteId);
                    f.put("quoteText", m.quoteText);
                    f.put("quoteName", m.quoteName);
                }
                queueOrSend(pid, f);
                Listener l = listener;
                if (l != null) {
                    try {
                        l.onChat(pid, true, body, m.ts);
                    } catch (Exception ignored) {
                    }
                }
                notifyState("chat");
            }
        });
    }

    /** 发送语音（WAV 字节 + 时长秒数）。 */
    public void sendVoice(final String pid, final byte[] wav, final int dur,
                          final String quoteId, final String quoteText, final String quoteName) {
        if (wav == null || wav.length == 0) {
            return;
        }
        post(new Runnable() {
            @Override
            public void run() {
                ChatMsg m = new ChatMsg(true, "", System.currentTimeMillis());
                m.voice = true;
                m.voiceDur = Math.max(1, dur);
                m.quoteId = nz(quoteId);
                m.quoteText = nz(quoteText);
                m.quoteName = nz(quoteName);
                m.voicePath = saveVoice(m.mid, wav);
                chatsFor(pid).add(m);
                appendChatLog(pid, m);
                ensureVisible(pid);
                Map<String, Object> f = Json.obj();
                f.put("t", "chat");
                f.put("mid", m.mid);
                f.put("text", "");
                f.put("ts", m.ts);
                f.put("voice", true);
                f.put("dur", m.voiceDur);
                if (!m.quoteId.isEmpty()) {
                    f.put("quoteId", m.quoteId);
                    f.put("quoteText", m.quoteText);
                    f.put("quoteName", m.quoteName);
                }
                queueOrSend(pid, f, wav);
                notifyState("chat");
            }
        });
    }

    /** 撤回自己 60 秒内发出的消息。 */
    public void recallChat(final String pid, final String mid) {
        post(new Runnable() {
            @Override
            public void run() {
                List<ChatMsg> list = chatsFor(pid);
                for (ChatMsg m : list) {
                    if (!m.mid.equals(mid)) {
                        continue;
                    }
                    if (!m.mine || m.recalled) {
                        return;
                    }
                    if (System.currentTimeMillis() - m.ts > RECALL_WINDOW) {
                        toast("超过 60 秒，不能撤回了");
                        return;
                    }
                    m.recalled = true;
                    m.voicePath = "";
                    rewriteChatLog(pid);
                    Map<String, Object> f = Json.obj();
                    f.put("t", "chat.recall");
                    f.put("mid", mid);
                    queueOrSend(pid, f);
                    notifyState("chat");
                    toast("已撤回");
                    return;
                }
            }
        });
    }

    public boolean canRecall(String pid, String mid) {
        for (ChatMsg m : chatList(pid)) {
            if (m.mid.equals(mid)) {
                return m.mine && !m.recalled
                        && System.currentTimeMillis() - m.ts <= RECALL_WINDOW;
            }
        }
        return false;
    }

    /** 剩余可撤回秒数，0 表示不可撤回。 */
    public int recallLeft(String pid, String mid) {
        for (ChatMsg m : chatList(pid)) {
            if (m.mid.equals(mid)) {
                if (!m.mine || m.recalled) {
                    return 0;
                }
                long left = RECALL_WINDOW - (System.currentTimeMillis() - m.ts);
                return left <= 0 ? 0 : (int) (left / 1000) + 1;
            }
        }
        return 0;
    }

    private void onRecall(Link link, Map<String, Object> h) {
        String mid = Json.str(h, "mid", "");
        if (mid.isEmpty()) {
            return;
        }
        for (ChatMsg m : chatsFor(link.peerId)) {
            if (m.mid.equals(mid)) {
                m.recalled = true;
                m.voicePath = "";
                rewriteChatLog(link.peerId);
                notifyState("chat");
                return;
            }
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    public List<ChatMsg> chatList(String pid) {
        List<ChatMsg> l = chats.get(pid);
        if (l == null) {
            return new ArrayList<ChatMsg>();
        }
        return new ArrayList<ChatMsg>(l);
    }

    public int unreadGuess(String pid) {
        List<ChatMsg> l = chats.get(pid);
        return l == null ? 0 : l.size();
    }

    /** 未读消息总数。 */
    public int unreadTotal() {
        int n = 0;
        for (Integer v : unread.values()) {
            if (v != null) {
                n += v;
            }
        }
        return n;
    }

    /** 某个会话的未读数。 */
    public int unreadOf(String pid) {
        Integer v = pid == null ? null : unread.get(pid);
        return v == null ? 0 : v;
    }

    /** 已读完某个会话：清掉红圈。 */
    public void markRead(final String pid) {
        if (pid == null || pid.isEmpty()) {
            return;
        }
        if (unread.remove(pid) != null) {
            notifyState("unread");
        }
    }

    /** 全部标记已读。 */
    public void markAllRead() {
        if (!unread.isEmpty()) {
            unread.clear();
            notifyState("unread");
        }
    }

    private void bumpUnread(String pid) {
        Integer v = unread.get(pid);
        unread.put(pid, v == null ? 1 : v + 1);
    }

    private List<ChatMsg> chatsFor(String pid) {
        List<ChatMsg> l = chats.get(pid);
        if (l == null) {
            l = new CopyOnWriteArrayList<ChatMsg>();
            chats.put(pid, l);
        }
        return l;
    }

    /** 会话是否已被手动删除（从列表里隐藏）。 */
    public boolean isHidden(String pid) {
        return hidden.contains(pid);
    }

    /** 重新把会话放回列表（打开会话、收到新消息时调用）。 */
    public void unhide(String pid) {
        if (pid != null && hidden.remove(pid)) {
            saveConfig();
            notifyState("chat");
        }
    }

    /** 清空单个会话的聊天记录（内存 + 磁盘）。 */
    public void clearChat(final String pid) {
        if (pid == null || pid.isEmpty()) {
            return;
        }
        post(new Runnable() {
            @Override
            public void run() {
                chats.remove(pid);
                deleteChatLog(pid);
                notifyState("chat");
            }
        });
    }

    /**
     * 删除整个会话：清空记录 + 去掉本机备注 + 去掉好友记录 + 从列表隐藏。
     * 用于「历史不要了」和「对方改名后残留的旧条目」两种情况。
     */
    public void deleteConversation(final String pid) {
        if (pid == null || pid.isEmpty()) {
            return;
        }
        post(new Runnable() {
            @Override
            public void run() {
                chats.remove(pid);
                deleteChatLog(pid);
                aliases.remove(pid);
                saveAliases();
                if (friends.remove(pid) != null) {
                    saveFriends();
                }
                hidden.add(pid);
                saveConfig();
                notifyState("chat");
                notifyState("friends");
                notifyState("peers");
            }
        });
    }

    /** 新消息到达时把会话从「已删除」状态恢复。 */
    private void ensureVisible(String pid) {
        if (pid != null && hidden.remove(pid)) {
            saveConfig();
        }
    }

    private void deleteChatLog(String pid) {
        try {
            new File(new File(dataDir, "chat"), pid + ".jsonl").delete();
        } catch (Exception ignored) {
        }
    }

    /**
     * 在所有会话的聊天记录里搜索关键字（房间里的历史会被懒加载）。
     * 命中按时间倒序返回，最多 limit 条。
     */
    public List<ChatHit> searchChat(String keyword, int limit) {
        List<ChatHit> out = new ArrayList<ChatHit>();
        String q = keyword == null ? "" : keyword.trim().toLowerCase();
        if (q.isEmpty()) {
            return out;
        }
        // 磁盘上的历史会话（还没读过内存的）也要能搜到
        File[] fs = new File(dataDir, "chat").listFiles();
        if (fs != null) {
            for (File f : fs) {
                String n = f.getName();
                if (!n.endsWith(".jsonl")) {
                    continue;
                }
                String pid = n.substring(0, n.length() - 6);
                if (hidden.contains(pid) || chats.containsKey(pid)) {
                    continue;
                }
                loadChatHistory(pid);
            }
        }
        for (Map.Entry<String, List<ChatMsg>> e : chats.entrySet()) {
            String pid = e.getKey();
            if (hidden.contains(pid)) {
                continue;
            }
            for (ChatMsg m : e.getValue()) {
                if (m.recalled) {
                    continue;
                }
                if (m.text != null && m.text.toLowerCase().contains(q)) {
                    ChatHit h = new ChatHit();
                    h.pid = pid;
                    h.mid = m.mid;
                    h.text = m.text;
                    h.ts = m.ts;
                    h.mine = m.mine;
                    out.add(h);
                }
            }
        }
        Collections.sort(out, new Comparator<ChatHit>() {
            @Override
            public int compare(ChatHit a, ChatHit b) {
                return Long.compare(b.ts, a.ts);
            }
        });
        if (limit > 0 && out.size() > limit) {
            return new ArrayList<ChatHit>(out.subList(0, limit));
        }
        return out;
    }

    // ------------------------------------------------------------------ 文件

    public Transfer sendFile(final String pid, final File file) {
        Transfer t = new Transfer();
        t.fid = rand(6);
        t.direction = Transfer.SENDING;
        t.peerId = pid;
        t.peerNick = nameOf(pid);
        t.name = file.getName();
        t.size = file.length();
        t.path = file.getAbsolutePath();
        t.state = Transfer.ACTIVE;
        t.ts = System.currentTimeMillis();
        transfers.put(t.fid, t);
        notifyState("files");
        startSender(t, file);
        return t;
    }

    /** 删除单条传输记录（正在传输中的不允许删除）。返回是否删除成功。 */
    public boolean removeTransfer(final String fid) {
        Transfer t = transfers.get(fid);
        if (t == null) {
            return false;
        }
        if (t.state == Transfer.ACTIVE || t.state == Transfer.PENDING) {
            return false;
        }
        transfers.remove(fid);
        notifyState("files");
        return true;
    }

    /** 清空所有已结束的传输记录；正在传输中的保留。返回清掉的条数。 */
    public int clearTransfers() {
        int n = 0;
        for (Transfer t : new ArrayList<Transfer>(transfers.values())) {
            if (t.state == Transfer.ACTIVE || t.state == Transfer.PENDING) {
                continue;
            }
            transfers.remove(t.fid);
            n++;
        }
        notifyState("files");
        return n;
    }

    /** 失败后重试：沿用原记录，接收方会从已有字节处继续。 */
    public void retryTransfer(final String fid) {
        post(new Runnable() {
            @Override
            public void run() {
                Transfer t = transfers.get(fid);
                if (t == null || t.direction != Transfer.SENDING) {
                    return;
                }
                File f = new File(t.path);
                if (!f.isFile()) {
                    toast("源文件已不存在");
                    return;
                }
                t.state = Transfer.ACTIVE;
                t.error = "";
                t.done = 0;
                t.offset = 0;
                t.size = f.length();
                t.ts = System.currentTimeMillis();
                notifyState("files");
                startSender(t, f);
            }
        });
    }

    private void startSender(final Transfer t, final File file) {
        final Gate g = new Gate();
        gates.put(t.fid, g);
        Thread th = new Thread(new Runnable() {
            @Override
            public void run() {
                streamOut(t, file, g);
            }
        }, "send-" + t.fid);
        th.setDaemon(true);
        th.start();
    }

    private void streamOut(Transfer t, File file, Gate gate) {
        try {
            if (!file.isFile()) {
                t.state = Transfer.FAILED;
                t.error = "文件不存在";
                notifyState("files");
                return;
            }
            t.size = file.length();
            if (t.key == null || t.key.isEmpty()) {
                t.key = fileKey(file);
            }
            final Map<String, Object> offer = Json.obj();
            offer.put("t", "file.offer");
            offer.put("fid", t.fid);
            offer.put("name", t.name);
            offer.put("size", t.size);
            offer.put("key", t.key);
            post(new Runnable() {
                @Override
                public void run() {
                    queueOrSend(t.peerId, offer);
                    notifyState("files");
                }
            });

            synchronized (gate) {
                long deadline = System.currentTimeMillis() + 20000;
                while (!gate.decided) {
                    long wait = deadline - System.currentTimeMillis();
                    if (wait <= 0) {
                        break;
                    }
                    gate.wait(wait);
                }
            }
            if (!gate.decided || !gate.ok) {
                t.state = gate.decided ? Transfer.REJECTED : Transfer.FAILED;
                if (!gate.decided) {
                    t.error = "对方无响应";
                }
                notifyState("files");
                return;
            }
            Link l = links.get(t.peerId);
            if (l == null || l.closed) {
                t.state = Transfer.FAILED;
                t.error = "连接已断开";
                notifyState("files");
                return;
            }

            long offset = Math.max(0, Math.min(gate.offset, t.size));
            t.offset = offset;
            t.done = offset;
            t.resumed = offset > 0;
            notifyState("files");
            if (offset > 0 && offset < t.size) {
                toast("断点续传：" + t.name + " 从 " + Math.round(offset * 100.0 / t.size) + "% 继续");
            }

            if (offset < t.size) {
                RandomAccessFile raf = new RandomAccessFile(file, "r");
                try {
                    raf.seek(offset);
                    byte[] buf = new byte[CHUNK];
                    int n;
                    long last = offset;
                    while ((n = raf.read(buf)) > 0) {
                        if (l.closed) {
                            throw new IOException("连接已断开");
                        }
                        byte[] data = new byte[n];
                        System.arraycopy(buf, 0, data, 0, n);
                        Map<String, Object> h = Json.obj();
                        h.put("t", "file.chunk");
                        h.put("fid", t.fid);
                        l.send(h, data);
                        t.done = t.done + n;
                        if (t.done - last > 512 * 1024) {
                            last = t.done;
                            notifyState("files");
                        }
                    }
                } finally {
                    try {
                        raf.close();
                    } catch (Exception ignored) {
                    }
                }
            }
            Map<String, Object> h = Json.obj();
            h.put("t", "file.done");
            h.put("fid", t.fid);
            l.send(h);
            t.state = Transfer.DONE;
            t.done = t.size;
            notifyState("files");
            toast("已发送：" + t.name);
        } catch (Exception e) {
            t.state = Transfer.FAILED;
            t.error = e.getMessage() == null ? "传输中断" : e.getMessage();
            notifyState("files");
        } finally {
            gates.remove(t.fid);
        }
    }

    private void onFileOffer(final Link link, Map<String, Object> h) {
        final String fid = Json.str(h, "fid", "");
        String name = Json.str(h, "name", "文件");
        long size = Json.num(h, "size", 0);
        String key = Json.str(h, "key", "");
        if (fid.isEmpty()) {
            return;
        }
        Transfer t = new Transfer();
        t.fid = fid;
        t.direction = Transfer.RECEIVING;
        t.peerId = link.peerId;
        t.peerNick = nameOf(link.peerId);
        t.name = safeName(name);
        t.size = size;
        t.key = key;
        t.state = Transfer.ACTIVE;
        t.ts = System.currentTimeMillis();
        t.path = partFile(t.key.isEmpty() ? fid : t.key).getAbsolutePath();
        transfers.put(fid, t);
        if (autoAcceptFiles) {
            acceptIncoming(link, t);
        } else {
            t.state = Transfer.PENDING;
            notifyState("files");
            toast(t.peerNick + " 想发送文件：" + t.name);
        }
    }

    public void acceptTransfer(final String fid) {
        post(new Runnable() {
            @Override
            public void run() {
                Transfer t = transfers.get(fid);
                if (t == null || t.state != Transfer.PENDING) {
                    return;
                }
                Link l = links.get(t.peerId);
                if (l == null) {
                    t.state = Transfer.FAILED;
                    t.error = "连接已断开";
                    notifyState("files");
                    return;
                }
                t.state = Transfer.ACTIVE;
                acceptIncoming(l, t);
            }
        });
    }

    public void rejectTransfer(final String fid) {
        post(new Runnable() {
            @Override
            public void run() {
                Transfer t = transfers.get(fid);
                if (t == null) {
                    return;
                }
                t.state = Transfer.REJECTED;
                Map<String, Object> m = Json.obj();
                m.put("t", "file.reject");
                m.put("fid", fid);
                queueOrSend(t.peerId, m);
                notifyState("files");
            }
        });
    }

    /** 打开（或复用）.part 文件，把已有长度作为续传起点回给发送方。 */
    private void acceptIncoming(Link link, Transfer t) {
        try {
            partsDir.mkdirs();
        voiceDir.mkdirs();
            recvDir.mkdirs();
            File part = partFile(t.key.isEmpty() ? t.fid : t.key);
            RandomAccessFile raf = new RandomAccessFile(part, "rw");
            long have = raf.length();
            if (have > t.size) {
                raf.setLength(0);
                have = 0;
            }
            raf.seek(have);
            t.offset = have;
            t.done = have;
            t.resumed = have > 0;
            t.path = part.getAbsolutePath();
            recvStreams.put(t.fid, raf);
            Map<String, Object> m = Json.obj();
            m.put("t", "file.accept");
            m.put("fid", t.fid);
            m.put("offset", have);
            link.send(m);
            if (have > 0 && have < t.size) {
                toast("继续接收：" + t.name + "（已完成 " + Math.round(have * 100.0 / t.size) + "%）");
            }
            notifyState("files");
        } catch (Exception e) {
            t.state = Transfer.FAILED;
            t.error = "无法写入文件";
            Map<String, Object> m = Json.obj();
            m.put("t", "file.reject");
            m.put("fid", t.fid);
            link.send(m);
            notifyState("files");
        }
    }

    private void onFileAccept(Link link, Map<String, Object> h) {
        Gate g = gates.get(Json.str(h, "fid", ""));
        if (g != null) {
            synchronized (g) {
                g.decided = true;
                g.ok = true;
                g.offset = Json.num(h, "offset", 0);
                g.notifyAll();
            }
        }
    }

    private void onFileReject(Link link, Map<String, Object> h) {
        Gate g = gates.get(Json.str(h, "fid", ""));
        if (g != null) {
            synchronized (g) {
                g.decided = true;
                g.ok = false;
                g.notifyAll();
            }
        }
    }

    private void onFileChunk(Link link, Wire.Frame f) {
        String fid = Json.str(f.h, "fid", "");
        Transfer t = transfers.get(fid);
        if (t == null) {
            return;
        }
        RandomAccessFile out = recvStreams.get(fid);
        if (out == null) {
            return;
        }
        try {
            if (t.size > 0 && t.done + f.p.length > t.size) {
                throw new IOException("数据超出预期大小");
            }
            out.write(f.p);
            t.done = t.done + f.p.length;
            if (t.done % (512 * 1024) < f.p.length) {
                notifyState("files");
            }
        } catch (IOException e) {
            t.state = Transfer.FAILED;
            t.error = "写入失败，可重试续传";
            closeQuietly(out);
            recvStreams.remove(fid);
            notifyState("files");
        }
    }

    private void onFileDone(Link link, Map<String, Object> h) {
        String fid = Json.str(h, "fid", "");
        Transfer t = transfers.get(fid);
        RandomAccessFile out = recvStreams.remove(fid);
        long len = 0;
        if (out != null) {
            try {
                len = out.length();
                out.getFD().sync();
            } catch (Exception ignored) {
            }
            closeQuietly(out);
        }
        if (t == null) {
            return;
        }
        if (t.size > 0 && len != t.size) {
            t.state = Transfer.FAILED;
            t.error = "数据不完整，可重试续传";
            notifyState("files");
            return;
        }
        try {
            File part = new File(t.path);
            File target = uniqueFile(recvDir, t.name);
            moveFile(part, target);
            t.path = target.getAbsolutePath();
            t.state = Transfer.DONE;
            t.done = t.size;
            t.resumed = false;
            notifyState("files");
            toast("已接收：" + t.name);
        } catch (Exception e) {
            t.state = Transfer.FAILED;
            t.error = "保存失败";
            notifyState("files");
        }
    }

    /** 连接断开时收尾该连接上未完成的接收任务（保留 .part 以便续传）。 */
    private void abortRecvFor(String peerId) {
        for (Transfer t : transfers.values()) {
            if (t.direction == Transfer.RECEIVING && peerId.equals(t.peerId)
                    && (t.state == Transfer.ACTIVE || t.state == Transfer.PENDING)) {
                RandomAccessFile raf = recvStreams.remove(t.fid);
                closeQuietly(raf);
                t.state = Transfer.FAILED;
                t.error = "连接中断，可重试续传";
            }
        }
        notifyState("files");
    }

    private static void closeQuietly(RandomAccessFile f) {
        if (f != null) {
            try {
                f.close();
            } catch (Exception ignored) {
            }
        }
    }

    private File partFile(String key) {
        partsDir.mkdirs();
        voiceDir.mkdirs();
        String k = key == null || key.isEmpty() ? "unknown" : key.replaceAll("[^A-Za-z0-9_-]", "");
        return new File(partsDir, k + ".part");
    }

    private static void moveFile(File src, File dst) throws IOException {
        if (dst.exists()) {
            dst.delete();
        }
        if (src.renameTo(dst)) {
            return;
        }
        InputStream in = new FileInputStream(src);
        BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(dst));
        try {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
            }
            out.close();
        }
        src.delete();
    }

    /** 文件指纹：大小 + 头 64KB + 尾 64KB 的 CRC32，用于匹配断点。 */
    public static String fileKey(File f) {
        long size = f.length();
        try {
            java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            RandomAccessFile raf = new RandomAccessFile(f, "r");
            try {
                int n = (int) Math.min(64 * 1024L, size);
                byte[] buf = new byte[Math.max(n, 1)];
                int rd = raf.read(buf, 0, n);
                if (rd > 0) {
                    crc.update(buf, 0, rd);
                }
                if (size > 64 * 1024L) {
                    raf.seek(size - 64 * 1024L);
                    rd = raf.read(buf, 0, (int) Math.min(64 * 1024L, size));
                    if (rd > 0) {
                        crc.update(buf, 0, rd);
                    }
                }
            } finally {
                raf.close();
            }
            return Long.toHexString(size) + "-" + Long.toHexString(crc.getValue());
        } catch (Exception e) {
            return Long.toHexString(size) + "-0";
        }
    }
    // ------------------------------------------------------------------ 文档

    public Doc createDoc(String title) {
        Doc d = new Doc();
        d.did = rand(8);
        d.title = (title == null || title.trim().isEmpty()) ? "未命名文档" : title.trim();
        d.text = "# " + d.title + "\n\n";
        d.ver = 1;
        d.owner = nick;
        d.updatedAt = System.currentTimeMillis();
        d.putSnapshot(1, d.text);
        docs.put(d.did, d);
        saveDoc(d);
        final Map<String, Object> m = Json.obj();
        m.put("t", "doc.create");
        m.put("did", d.did);
        m.put("title", d.title);
        m.put("text", d.text);
        m.put("ver", d.ver);
        m.put("owner", d.owner);
        for (String pid : friends.keySet()) {
            queueOrSend(pid, m);
        }
        notifyState("docs");
        return d;
    }

    public void renameDoc(final String did, final String title) {
        post(new Runnable() {
            @Override
            public void run() {
                Doc d = docs.get(did);
                if (d == null) {
                    return;
                }
                d.title = title == null || title.trim().isEmpty() ? "未命名文档" : title.trim();
                d.updatedAt = System.currentTimeMillis();
                saveDoc(d);
                notifyState("docs");
            }
        });
    }

    public void deleteDoc(final String did) {
        if (did == null || did.isEmpty()) {
            return;
        }
        // 先从内存摘掉并写「删除标记」：这样列表立即更新，另一个实例 / 下次启动也不会再把这个文档写回来
        docs.remove(did);
        try {
            new File(new File(dataDir, "docs"), did + ".json").delete();
            File mark = goneFile(did);
            mark.getParentFile().mkdirs();
            writeAll(mark, String.valueOf(System.currentTimeMillis()));
        } catch (Exception ignored) {
        }
        notifyState("docs");
    }

    /** 删除标记文件：文档被删后用来挡住「另一个实例 / 远端同步 / 待落地保存」的复活。 */
    private File goneFile(String did) {
        return new File(new File(new File(dataDir, "docs"), ".deleted"), did + ".gone");
    }

    /** 该文档是否已被本机删除（超过 7 天的标记自动失效，允许再次接收共享）。 */
    private boolean docDeleted(String did) {
        File f = goneFile(did);
        if (!f.exists()) {
            return false;
        }
        long ts = 0;
        try {
            ts = Long.parseLong(readAll(f).trim());
        } catch (Exception ignored) {
        }
        if (ts > 0 && System.currentTimeMillis() - ts > 7L * 24 * 3600 * 1000) {
            f.delete();
            return false;
        }
        return true;
    }

    /** 本地编辑（由 UI 在用户输入后调用）。 */
    public void updateDocText(final String did, final String text) {
        post(new Runnable() {
            @Override
            public void run() {
                Doc d = docs.get(did);
                if (d == null || text == null || text.equals(d.text)) {
                    return;
                }
                int baseVer = d.ver;
                d.text = text;
                d.ver = baseVer + 1;
                d.updatedAt = System.currentTimeMillis();
                d.putSnapshot(d.ver, text);
                saveDoc(d);
                Map<String, Object> m = Json.obj();
                m.put("t", "doc.update");
                m.put("did", did);
                m.put("baseVer", baseVer);
                m.put("ver", d.ver);
                m.put("text", text);
                m.put("from", nick);
                m.put("hops", 0);
                for (String pid : docTargets(d)) {
                    queueOrSend(pid, m);
                }
                notifyState("docs");
            }
        });
    }

    /** 邀请某人协作此文档。 */
    public void shareDoc(final String did, final String pid) {
        post(new Runnable() {
            @Override
            public void run() {
                Doc d = docs.get(did);
                if (d == null) {
                    return;
                }
                Map<String, Object> m = Json.obj();
                m.put("t", "doc.create");
                m.put("did", d.did);
                m.put("title", d.title);
                m.put("text", d.text);
                m.put("ver", d.ver);
                m.put("owner", d.owner);
                queueOrSend(pid, m);
            }
        });
    }

    public void requestDoc(final String did, final String pid) {
        post(new Runnable() {
            @Override
            public void run() {
                Map<String, Object> m = Json.obj();
                m.put("t", "doc.request");
                m.put("did", did);
                queueOrSend(pid, m);
            }
        });
    }

    private void onDocCreate(Link link, Map<String, Object> h) {
        String did = Json.str(h, "did", "");
        if (did.isEmpty() || docDeleted(did)) {
            return;
        }
        Doc d = docs.get(did);
        if (d == null) {
            d = new Doc();
            d.did = did;
            d.title = Json.str(h, "title", "共享文档");
            d.text = Json.str(h, "text", "");
            d.ver = Json.i32(h, "ver", 1);
            d.owner = Json.str(h, "owner", "");
            d.updatedAt = System.currentTimeMillis();
            d.putSnapshot(d.ver, d.text);
            docs.put(did, d);
            saveDoc(d);
            toast(link.peerNick + " 共享了文档《" + d.title + "》");
            notifyState("docs");
        } else if (Json.i32(h, "ver", 0) > d.ver) {
            d.text = Json.str(h, "text", d.text);
            d.ver = Json.i32(h, "ver", d.ver);
            d.putSnapshot(d.ver, d.text);
            saveDoc(d);
            notifyState("docs");
        }
        d.editors.put(link.peerId, link.peerNick);
    }

    private void onDocRequest(Link link, Map<String, Object> h) {
        Doc d = docs.get(Json.str(h, "did", ""));
        if (d == null) {
            return;
        }
        Map<String, Object> m = Json.obj();
        m.put("t", "doc.sync");
        m.put("did", d.did);
        m.put("title", d.title);
        m.put("text", d.text);
        m.put("ver", d.ver);
        m.put("hops", 0);
        link.send(m);
    }

    private void onDocUpdate(Link link, Map<String, Object> h) {
        String did = Json.str(h, "did", "");
        Doc d = docs.get(did);
        if (d == null) {
            return;
        }
        d.editors.put(link.peerId, link.peerNick);
        int baseVer = Json.i32(h, "baseVer", -1);
        int ver = Json.i32(h, "ver", d.ver + 1);
        String text = Json.str(h, "text", "");
        int hops = Json.i32(h, "hops", 0);
        if (baseVer == d.ver) {
            d.text = text;
            d.ver = ver;
            d.updatedAt = System.currentTimeMillis();
            d.putSnapshot(ver, text);
            saveDoc(d);
            notifyState("docs");
            return;
        }
        String base = d.snapshot(baseVer);
        if (base == null) {
            if (ver > d.ver) {
                d.text = text;
                d.ver = ver;
                d.putSnapshot(ver, text);
                saveDoc(d);
                notifyState("docs");
            }
            return;
        }
        String merged = DocMerge.merge(base, d.text, text, id, link.peerId, "你的修改", link.peerNick + " 的修改");
        int newVer = Math.max(d.ver, ver) + 1;
        boolean changed = !merged.equals(d.text);
        d.text = merged;
        d.ver = newVer;
        d.updatedAt = System.currentTimeMillis();
        d.putSnapshot(newVer, merged);
        saveDoc(d);
        notifyState("docs");
        if (changed && hops < 3 && !merged.equals(text)) {
            Map<String, Object> m = Json.obj();
            m.put("t", "doc.sync");
            m.put("did", did);
            m.put("title", d.title);
            m.put("text", merged);
            m.put("ver", newVer);
            m.put("hops", hops + 1);
            for (String pid : d.editors.keySet()) {
                if (!pid.equals(link.peerId)) {
                    queueOrSend(pid, m);
                }
            }
            queueOrSend(link.peerId, m);
        }
    }

    private void onDocSync(Link link, Map<String, Object> h) {
        String did = Json.str(h, "did", "");
        if (did.isEmpty() || docDeleted(did)) {
            return;
        }
        Doc d = docs.get(did);
        if (d == null) {
            d = new Doc();
            d.did = did;
            d.title = Json.str(h, "title", "共享文档");
            d.ver = 0;
            docs.put(did, d);
        }
        int ver = Json.i32(h, "ver", 0);
        String text = Json.str(h, "text", "");
        int hops = Json.i32(h, "hops", 0);
        d.editors.put(link.peerId, link.peerNick);
        if (ver > d.ver) {
            d.text = text;
            d.ver = ver;
            d.putSnapshot(ver, text);
            saveDoc(d);
            notifyState("docs");
        } else if (!text.equals(d.text)) {
            String merged = DocMerge.merge(d.snapshot(d.ver), d.text, text, id, link.peerId,
                    "你的修改", link.peerNick + " 的修改");
            if (!merged.equals(d.text)) {
                d.text = merged;
                d.ver = d.ver + 1;
                d.putSnapshot(d.ver, merged);
                saveDoc(d);
                notifyState("docs");
                if (hops < 3) {
                    Map<String, Object> m = Json.obj();
                    m.put("t", "doc.sync");
                    m.put("did", did);
                    m.put("title", d.title);
                    m.put("text", merged);
                    m.put("ver", d.ver);
                    m.put("hops", hops + 1);
                    queueOrSend(link.peerId, m);
                }
            }
        }
    }

    private void onDocAware(Link link, Map<String, Object> h) {
        Doc d = docs.get(Json.str(h, "did", ""));
        if (d != null) {
            String n = Json.str(h, "nick", link.peerNick);
            if (Json.bool(h, "leaving", false)) {
                d.editors.remove(link.peerId);
            } else {
                d.editors.put(link.peerId, n);
            }
            notifyState("docs");
        }
    }

    /**
     * 广播自己的光标所在行（0 基）；line &lt; 0 表示离开文档。
     * 由界面在光标移动时节流调用。
     */
    public void sendDocCursor(final String did, final int line) {
        post(new Runnable() {
            @Override
            public void run() {
                Doc d = docs.get(did);
                if (d == null) {
                    return;
                }
                Map<String, Object> f = Json.obj();
                f.put("t", "doc.cursor");
                f.put("did", did);
                f.put("line", line);
                // 光标标记发给所有在线设备（不必先加好友），对方本地有该文档即可显示
                for (String pid : cursorTargets(d)) {
                    queueOrSend(pid, f);
                }
            }
        });
    }

    private void onDocCursor(Link link, Map<String, Object> h) {
        Doc d = docs.get(Json.str(h, "did", ""));
        if (d == null) {
            return;
        }
        int line = Json.i32(h, "line", -1);
        if (line < 0) {
            d.cursors.remove(link.peerId);
            d.cursorAt.remove(link.peerId);
        } else {
            d.cursors.put(link.peerId, line);
            d.cursorAt.put(link.peerId, System.currentTimeMillis());
        }
        notifyState("doc.cursor");
    }

    /** 连接断开时清掉该设备留下的光标标记。 */
    private void clearPeerCursors(String pid) {
        for (Doc d : docs.values()) {
            if (d.cursors.remove(pid) != null) {
                d.cursorAt.remove(pid);
            }
        }
    }

    /** 由设备 id 派生的稳定颜色序号（0-5），两端调色板一致。 */
    public static int colorIndex(String pid) {
        int h = 0;
        String s = pid == null ? "" : pid;
        for (int i = 0; i < s.length(); i++) {
            h = 31 * h + s.charAt(i);
        }
        return Math.abs(h) % 6;
    }

    public void awareDoc(final String did, final boolean leaving) {
        post(new Runnable() {
            @Override
            public void run() {
                Doc d = docs.get(did);
                if (d == null) {
                    return;
                }
                Map<String, Object> m = Json.obj();
                m.put("t", "doc.aware");
                m.put("did", did);
                m.put("nick", nick);
                m.put("leaving", leaving);
                for (String pid : d.editors.keySet()) {
                    if (leaving) {
                        queueOrSend(pid, m);
                    } else {
                        queueOrSend(pid, m);
                    }
                }
            }
        });
    }

    // ------------------------------------------------------------------ 设置

    public void setNick(final String n) {
        final String v = n == null ? "" : n.trim();
        if (v.isEmpty()) {
            return;
        }
        post(new Runnable() {
            @Override
            public void run() {
                nick = v;
                saveConfig();
                Map<String, Object> m = Json.obj();
                m.put("t", "nick");
                m.put("nick", v);
                for (Link l : links.values()) {
                    l.send(m);
                }
                if (discovery != null) {
                    discovery.announceNow();
                }
                notifyState("self");
                notifyState("peers");
            }
        });
    }

    public void setRecvDir(String path) {
        if (path == null || path.trim().isEmpty()) {
            return;
        }
        recvDir = new File(path.trim());
        recvDir.mkdirs();
        saveConfig();
        notifyState("self");
    }

    public void setAutoAcceptFiles(boolean v) {
        autoAcceptFiles = v;
        saveConfig();
        notifyState("self");
    }

    public void setAutoAcceptFriends(boolean v) {
        autoAcceptFriends = v;
        saveConfig();
        notifyState("self");
    }

    // ------------------------------------------------------------------ 查询

    public List<Peer> peerList() {
        List<Peer> l = new ArrayList<Peer>(peers.values());
        for (Peer p : l) {
            p.friend = friends.containsKey(p.id);
            Link lk = links.get(p.id);
            p.connected = lk != null && !lk.closed;
        }
        Collections.sort(l, new Comparator<Peer>() {
            @Override
            public int compare(Peer a, Peer b) {
                if (a.friend != b.friend) {
                    return a.friend ? -1 : 1;
                }
                if (a.connected != b.connected) {
                    return a.connected ? -1 : 1;
                }
                return a.nick.compareToIgnoreCase(b.nick);
            }
        });
        return l;
    }

    public List<Peer> friendPeerList() {
        List<Peer> out = new ArrayList<Peer>();
        for (String pid : friends.keySet()) {
            Peer p = peers.get(pid);
            if (p == null) {
                p = new Peer();
                p.id = pid;
                p.nick = friends.get(pid);
                p.device = "不在线";
                p.deviceType = -1;
            }
            Link lk = links.get(pid);
            p.connected = lk != null && !lk.closed;
            p.friend = true;
            out.add(p);
        }
        Collections.sort(out, new Comparator<Peer>() {
            @Override
            public int compare(Peer a, Peer b) {
                if (a.connected != b.connected) {
                    return a.connected ? -1 : 1;
                }
                return a.nick.compareToIgnoreCase(b.nick);
            }
        });
        return out;
    }

    public List<Transfer> transferList() {
        List<Transfer> l = new ArrayList<Transfer>(transfers.values());
        Collections.sort(l, new Comparator<Transfer>() {
            @Override
            public int compare(Transfer a, Transfer b) {
                return Long.compare(b.ts, a.ts);
            }
        });
        return l;
    }

    public List<Doc> docList() {
        List<Doc> l = new ArrayList<Doc>(docs.values());
        Collections.sort(l, new Comparator<Doc>() {
            @Override
            public int compare(Doc a, Doc b) {
                return Long.compare(b.updatedAt, a.updatedAt);
            }
        });
        return l;
    }

    public Doc doc(String did) {
        return docs.get(did);
    }

    /** 文档同步的发送对象：所有好友 + 已加入该文档的协作者。 */
    private java.util.Set<String> docTargets(Doc d) {
        java.util.Set<String> out = new java.util.LinkedHashSet<String>();
        out.addAll(friends.keySet());
        out.addAll(d.editors.keySet());
        out.remove(id);
        return out;
    }

    /** 光标标记的发送对象：所有在线设备 + 好友 + 该文档的协作者。 */
    private java.util.Set<String> cursorTargets(Doc d) {
        java.util.Set<String> out = new java.util.LinkedHashSet<String>();
        out.addAll(peers.keySet());
        out.addAll(friends.keySet());
        out.addAll(d.editors.keySet());
        out.remove(id);
        return out;
    }

    /** 显示名：本地备注名优先，其次对方真实昵称。 */
    /**
     * 展示名：本机备注 &gt; 对方昵称 &gt; 设备名称。
     * 「未命名」这类占位昵称视为没有昵称，直接显示设备名称。
     */
    public String nameOf(String pid) {
        String alias = aliases.get(pid);
        if (alias != null && !alias.trim().isEmpty()) {
            return alias;
        }
        Peer p = peers.get(pid);
        if (p != null && hasRealNick(p.nick)) {
            return p.nick.trim();
        }
        String f = friends.get(pid);
        if (f != null && hasRealNick(f)) {
            return f.trim();
        }
        if (p != null && p.device != null && !p.device.trim().isEmpty()) {
            return p.device.trim();
        }
        return "未知设备";
    }

    /** 头像首字：跟随展示名（本机备注优先），备注改名后头像同步变化。 */
    public char initialOf(String pid) {
        String n = nameOf(pid);
        if (n == null) {
            return '?';
        }
        String s = n.trim();
        if (s.isEmpty() || "未知设备".equals(s)) {
            return '?';
        }
        int cp = s.codePointAt(0);
        if (Character.isLetterOrDigit(cp)) {
            return Character.toUpperCase(Character.toChars(cp)[0]);
        }
        return '#';
    }

    /** 昵称是否有效（空串、「未命名」等占位都不算）。 */
    private static boolean hasRealNick(String nick) {        if (nick == null) {
            return false;
        }
        String s = nick.trim();
        return !s.isEmpty() && !"未命名".equals(s) && !"未知设备".equals(s);
    }

    /** 对方真实昵称（用于备注名对话框的提示）。 */
    /** 对方自己的名字（忽略本机备注），无昵称时用设备名称。 */
    public String realNameOf(String pid) {
        Peer p = peers.get(pid);
        if (p != null && hasRealNick(p.nick)) {
            return p.nick.trim();
        }
        String f = friends.get(pid);
        if (f != null && hasRealNick(f)) {
            return f.trim();
        }
        if (p != null && p.device != null && !p.device.trim().isEmpty()) {
            return p.device.trim();
        }
        return "未知设备";
    }

    public String aliasOf(String pid) {
        String a = aliases.get(pid);
        return a == null ? "" : a;
    }

    /** 设置本地备注名；传空串表示恢复对方昵称。 */
    public void setAlias(final String pid, final String alias) {
        final String v = alias == null ? "" : alias.trim();
        post(new Runnable() {
            @Override
            public void run() {
                if (v.isEmpty()) {
                    aliases.remove(pid);
                } else {
                    aliases.put(pid, v);
                }
                saveAliases();
                notifyState("friends");
                notifyState("peers");
                notifyState("chat");
                notifyState("docs");
            }
        });
    }

    public boolean isFriend(String pid) {
        return friends.containsKey(pid);
    }

    // ------------------------------------------------------------------ 持久化

    private File cfgFile() {
        return new File(dataDir, "config.json");
    }

    private void loadConfig() {
        try {
            File f = cfgFile();
            if (!f.exists()) {
                return;
            }
            Map<String, Object> m = Json.asObj(Json.parse(readAll(f)));
            id = Json.str(m, "id", "");
            nick = Json.str(m, "nick", "");
            String rd = Json.str(m, "recvDir", "");
            if (!rd.isEmpty()) {
                recvDir = new File(rd);
            }
            autoAcceptFiles = Json.bool(m, "autoFiles", true);
            autoAcceptFriends = Json.bool(m, "autoFriends", true);
            for (Object o : Json.arr(m, "hidden")) {
                if (o != null) {
                    hidden.add(String.valueOf(o));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void saveConfig() {
        try {
            Map<String, Object> m = Json.obj();
            m.put("id", id);
            m.put("nick", nick);
            m.put("recvDir", recvDir.getAbsolutePath());
            m.put("autoFiles", autoAcceptFiles);
            m.put("autoFriends", autoAcceptFriends);
            m.put("hidden", new ArrayList<Object>(hidden));
            writeAll(cfgFile(), Json.write(m));
        } catch (Exception ignored) {
        }
    }

    private void loadFriends() {
        try {
            File f = new File(dataDir, "friends.json");
            if (!f.exists()) {
                return;
            }
            for (Object o : Json.arr(Json.asObj(Json.parse("{\"a\":" + readAll(f) + "}")), "a")) {
                Map<String, Object> m = Json.asObj(o);
                String fid = Json.str(m, "id", "");
                if (!fid.isEmpty()) {
                    friends.put(fid, Json.str(m, "nick", "好友"));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void saveFriends() {
        List<Object> arr = new ArrayList<Object>();
        for (Map.Entry<String, String> e : friends.entrySet()) {
            Map<String, Object> m = Json.obj();
            m.put("id", e.getKey());
            m.put("nick", e.getValue());
            arr.add(m);
        }
        try {
            writeAll(new File(dataDir, "friends.json"), Json.write(arr));
        } catch (Exception ignored) {
        }
    }

    private void loadAliases() {
        try {
            File f = new File(dataDir, "aliases.json");
            if (!f.exists()) {
                return;
            }
            for (Object o : Json.arr(Json.asObj(Json.parse("{\"a\":" + readAll(f) + "}")), "a")) {
                Map<String, Object> m = Json.asObj(o);
                String pid = Json.str(m, "id", "");
                String alias = Json.str(m, "alias", "");
                if (!pid.isEmpty() && !alias.isEmpty()) {
                    aliases.put(pid, alias);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void saveAliases() {
        List<Object> arr = new ArrayList<Object>();
        for (Map.Entry<String, String> e : aliases.entrySet()) {
            Map<String, Object> m = Json.obj();
            m.put("id", e.getKey());
            m.put("alias", e.getValue());
            arr.add(m);
        }
        try {
            writeAll(new File(dataDir, "aliases.json"), Json.write(arr));
        } catch (Exception ignored) {
        }
    }

    private void loadDocs() {
        try {
            File dir = new File(dataDir, "docs");
            File[] fs = dir.listFiles();
            if (fs == null) {
                return;
            }
            for (File f : fs) {
                if (!f.getName().endsWith(".json")) {
                    continue;
                }
                String name = f.getName();
                String id = name.substring(0, name.length() - 5);
                if (docDeleted(id)) {
                    // 上次删除后又被别的实例写回来的残留：清掉，别再出现在列表里
                    f.delete();
                    continue;
                }
                Doc d = Doc.fromJson(Json.asObj(Json.parse(readAll(f))));
                if (!d.did.isEmpty()) {
                    docs.put(d.did, d);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void saveDoc(Doc d) {
        try {
            if (docDeleted(d.did)) {
                // 已经在本机删掉的文档，不再写盘（同机多开时另一个实例会走到这里）
                new File(new File(dataDir, "docs"), d.did + ".json").delete();
                return;
            }
            File dir = new File(dataDir, "docs");
            dir.mkdirs();
            writeAll(new File(dir, d.did + ".json"), Json.write(d.toJson()));
        } catch (Exception ignored) {
        }
    }

    private Map<String, Object> chatJson(ChatMsg m) {
        Map<String, Object> o = Json.obj();
        o.put("mid", m.mid);
        o.put("mine", m.mine);
        o.put("text", m.text);
        o.put("ts", m.ts);
        if (!m.quoteId.isEmpty()) {
            o.put("quoteId", m.quoteId);
            o.put("quoteText", m.quoteText);
            o.put("quoteName", m.quoteName);
        }
        if (m.recalled) {
            o.put("recalled", true);
        }
        if (m.voice) {
            o.put("voice", true);
            o.put("dur", m.voiceDur);
            o.put("voicePath", m.voicePath);
        }
        return o;
    }

    private void appendChatLog(String pid, ChatMsg m) {
        try {
            File dir = new File(dataDir, "chat");
            dir.mkdirs();
            FileWriter w = new FileWriter(new File(dir, pid + ".jsonl"), true);
            try {
                w.write(Json.write(chatJson(m)));
                w.write("\n");
            } finally {
                w.close();
            }
        } catch (Exception ignored) {
        }
    }

    /** 撤回后整体重写会话记录，保证状态一致。 */
    private void rewriteChatLog(String pid) {
        try {
            File dir = new File(dataDir, "chat");
            dir.mkdirs();
            StringBuilder sb = new StringBuilder();
            for (ChatMsg m : chatsFor(pid)) {
                sb.append(Json.write(chatJson(m))).append('\n');
            }
            writeAll(new File(dir, pid + ".jsonl"), sb.toString());
        } catch (Exception ignored) {
        }
    }

    public void loadChatHistory(String pid) {
        try {
            File f = new File(new File(dataDir, "chat"), pid + ".jsonl");
            if (!f.exists()) {
                return;
            }
            List<ChatMsg> list = chatsFor(pid);
            if (!list.isEmpty()) {
                return;
            }
            String all = readAll(f);
            for (String line : all.split("\n")) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                Map<String, Object> o = Json.asObj(Json.parse(line));
                ChatMsg m = new ChatMsg(Json.bool(o, "mine", false),
                        Json.str(o, "text", ""), Json.num(o, "ts", 0));
                m.mid = Json.str(o, "mid", m.mid);
                m.quoteId = Json.str(o, "quoteId", "");
                m.quoteText = Json.str(o, "quoteText", "");
                m.quoteName = Json.str(o, "quoteName", "");
                m.recalled = Json.bool(o, "recalled", false);
                m.voice = Json.bool(o, "voice", false);
                m.voiceDur = Json.i32(o, "dur", 0);
                m.voicePath = Json.str(o, "voicePath", "");
                list.add(m);
            }
        } catch (Exception ignored) {
        }
    }

    private static String readAll(File f) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(f, "r");
        try {
            byte[] b = new byte[(int) raf.length()];
            raf.readFully(b);
            return new String(b, Wire.UTF8);
        } finally {
            raf.close();
        }
    }

    private static void writeAll(File f, String s) throws IOException {
        File parent = f.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(f));
        try {
            out.write(s.getBytes(Wire.UTF8));
        } finally {
            out.close();
        }
    }

    // ------------------------------------------------------------------ 工具

    private static final Random RND = new Random();
    private static final AtomicInteger SEQ = new AtomicInteger();

    public static String rand(int len) {
        String chars = "abcdefghijkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(chars.charAt(RND.nextInt(chars.length())));
        }
        return sb.toString();
    }

    public static String safeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "file";
        }
        String s = name.replace('\\', '_').replace('/', '_').replace("..", "_");
        s = s.replaceAll("[\\r\\n\\t\\x00]", "");
        if (s.length() > 120) {
            s = s.substring(0, 120);
        }
        return s;
    }

    public static File uniqueFile(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) {
            return f;
        }
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 1000; i++) {
            f = new File(dir, base + " (" + i + ")" + ext);
            if (!f.exists()) {
                return f;
            }
        }
        return new File(dir, base + "-" + System.currentTimeMillis() + ext);
    }

    public static String human(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(java.util.Locale.US, "%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format(java.util.Locale.US, "%.1f MB", mb);
        }
        return String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0);
    }

    public String localIp() {
        return Discovery.localIp();
    }
}
