package com.wlanshare.selftest;

import com.wlanshare.core.Doc;
import com.wlanshare.core.DocMerge;
import com.wlanshare.core.Node;
import com.wlanshare.core.Peer;
import com.wlanshare.core.Platform;
import com.wlanshare.core.Transfer;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Random;

/** 无界面自测：两个 Node 实例在同一台机器上互相发现、聊天、传文件、协同编辑。 */
public final class SelfTest {

    static int pass;
    static int fail;

    static void check(String name, boolean ok, String extra) {
        if (ok) {
            pass++;
            System.out.println("  [PASS] " + name + (extra == null ? "" : "  " + extra));
        } else {
            fail++;
            System.out.println("  [FAIL] " + name + (extra == null ? "" : "  " + extra));
        }
    }

    static class P implements Platform {
        final File dir;
        final String name;

        P(File dir, String name) {
            this.dir = dir;
            this.name = name;
        }

        public File dataDir() {
            return dir;
        }

        public String deviceName() {
            return name;
        }

        public int deviceType() {
            return 0;
        }
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    static boolean waitFor(java.util.function.BooleanSupplier s, long ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (s.getAsBoolean()) {
                return true;
            }
            sleep(150);
        }
        return s.getAsBoolean();
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== WlanShare 自测 ==");
        File base = new File("build/selftest");
        deleteRec(base);
        Node a = new Node(new P(new File(base, "a"), "PC-A"));
        Node b = new Node(new P(new File(base, "b"), "PC-B"));
        a.setNick("阿尔法");
        b.setNick("贝塔");
        a.start();
        b.start();
        final String aid = a.id;
        final String bid = b.id;
        System.out.println("A tcp=" + a.tcpPort + " udp=" + a.udpPort + " id=" + a.id);
        System.out.println("B tcp=" + b.tcpPort + " udp=" + b.udpPort + " id=" + b.id);

        System.out.println("\n[1] 自动发现");
        boolean found = waitFor(() -> a.peers.containsKey(bid) && b.peers.containsKey(aid), 20000);
        check("双端互相发现", found, found ? ("A 看到 " + a.peerList().size() + " 台 / B 看到 " + b.peerList().size() + " 台") : "超时");
        if (found) {
            Peer pa = a.peers.get(bid);
            check("昵称同步", "贝塔".equals(pa.nick), "A 看到 B 的昵称=" + pa.nick);
            check("设备名同步", "PC-B".equals(pa.device), pa.device);
            check("连接已建立", waitFor(() -> pa.connected, 6000), "");
        }

        System.out.println("\n[2] 好友");
        a.requestFriend(bid);
        boolean fr = waitFor(() -> a.isFriend(bid) && b.isFriend(aid), 6000);
        check("添加好友双向生效", fr, "");
        check("好友列表", a.friendPeerList().size() == 1 && b.friendPeerList().size() == 1, "");

        System.out.println("\n[3] 消息");
        a.sendChat(bid, "你好，这是一条测试消息 🚀");
        boolean got = waitFor(() -> {
            List<Node.ChatMsg> l = b.chatList(aid);
            return !l.isEmpty();
        }, 6000);
        check("消息送达", got, got ? b.chatList(aid).get(0).text : "超时");
        b.sendChat(aid, "收到！中文与 emoji 正常 😀");
        check("反向消息", waitFor(() -> a.chatList(bid).size() >= 1, 6000), "");

        System.out.println("\n[3.1] 未读红圈计数");
        a.activeChat = bid;
        b.activeChat = aid;
        int unreadBefore = b.unreadOf(aid);
        a.sendChat(bid, "这条不该计未读（B 正在看这个会话）");
        Thread.sleep(900);
        check("正在看的会话不计未读", b.unreadOf(aid) == unreadBefore, "未读=" + b.unreadOf(aid));
        b.activeChat = "";
        a.sendChat(bid, "这条应当计未读 1");
        a.sendChat(bid, "这条应当计未读 2");
        boolean counted = waitFor(() -> b.unreadOf(aid) >= 2, 6000);
        check("非当前会话累加未读", counted, "未读=" + b.unreadOf(aid));
        check("未读总数正确", b.unreadTotal() >= 2, "总数=" + b.unreadTotal());
        b.markRead(aid);
        check("查看后清零", waitFor(() -> b.unreadOf(aid) == 0, 6000), "未读=" + b.unreadOf(aid));

        System.out.println("\n[4] 文件传输");
        File src = new File(base, "payload.bin");
        src.getParentFile().mkdirs();
        byte[] data = new byte[3 * 1024 * 1024 + 777];
        new Random(42).nextBytes(data);
        FileOutputStream fo = new FileOutputStream(src);
        fo.write(data);
        fo.close();
        long t0 = System.currentTimeMillis();
        Transfer tr = a.sendFile(bid, src);
        boolean done = waitFor(() -> tr.state == Transfer.DONE, 30000);
        long cost = System.currentTimeMillis() - t0;
        check("发送完成", done, done ? (data.length / 1024 / 1024.0) + "MB 用时 " + cost + "ms" : "state=" + tr.state + " err=" + tr.error);
        File received = null;
        for (Transfer t : b.transferList()) {
            if (t.direction == Transfer.RECEIVING) {
                received = new File(t.path);
            }
        }
        check("文件落地", received != null && received.exists(), received == null ? "无" : received.getAbsolutePath());
        if (received != null) {
            byte[] rb = new byte[(int) received.length()];
            java.io.DataInputStream fi = new java.io.DataInputStream(new java.io.FileInputStream(received));
            fi.readFully(rb);
            fi.close();
            check("内容一致", java.util.Arrays.equals(data, rb), "size=" + rb.length);
        }

        System.out.println("\n[5] 协作文档");
        a.requestFriend(bid);
        Doc d = a.createDoc("协作测试");
        sleep(800);
        Doc bd = b.doc(d.did);
        check("文档已共享到对端", bd != null, bd == null ? "未收到" : "《" + bd.title + "》ver=" + bd.ver);

        // 双方在不同位置并发编辑
        a.updateDocText(d.did, "# 协作测试\n\n第一段由 A 写入。\n\n公共段落。\n");
        sleep(600);
        Doc da = a.doc(d.did);
        Doc db = b.doc(d.did);
        check("增量同步", db != null && db.text.equals(da.text), db == null ? "无" : "A.ver=" + da.ver + " B.ver=" + db.ver);

        if (db != null) {
            String baseText = db.text;
            a.updateDocText(d.did, baseText + "A 追加：末尾来自 A。\n");
            b.updateDocText(d.did, "# 协作测试（B 改了标题）\n\n第一段由 A 写入。\n\n公共段落。\nB 追加：开头来自 B。\n");
            boolean converge = waitFor(() -> {
                Doc x = a.doc(d.did);
                Doc y = b.doc(d.did);
                return x != null && y != null && x.text.equals(y.text);
            }, 8000);
            check("并发编辑后收敛一致", converge, converge ? "" : "A 与 B 文本仍不同");
            if (converge) {
                String t = a.doc(d.did).text;
                check("双方修改都保留", t.contains("末尾来自 A") && t.contains("B 追加"), "");
                check("无冲突标记", !DocMerge.hasConflict(t), "");
                System.out.println("  合并结果：\n" + indent(t));
            }
        }

        System.out.println("\n[5.1] 协作光标行");
        a.sendDocCursor(d.did, 3);
        boolean gotCursor = waitFor(() -> {
            Doc x = b.doc(d.did);
            return x != null && Integer.valueOf(3).equals(x.cursors.get(aid));
        }, 8000);
        Doc bx = b.doc(d.did);
        check("光标行广播到对端", gotCursor,
                bx == null ? "无文档" : "B 看到 A 在第 " + (bx.cursors.get(aid) == null ? "-" : bx.cursors.get(aid) + 1) + " 行");

        b.sendDocCursor(d.did, 7);
        boolean gotBack = waitFor(() -> {
            Doc x = a.doc(d.did);
            return x != null && Integer.valueOf(7).equals(x.cursors.get(bid));
        }, 8000);
        check("双向广播光标行", gotBack, gotBack ? "" : "A 未收到 B 的行号");

        int ci = Node.colorIndex(aid);
        check("颜色序号稳定且在 0-5", ci == Node.colorIndex(aid) && ci >= 0 && ci < 6, "index=" + ci);

        a.sendDocCursor(d.did, -1);
        boolean cleared = waitFor(() -> {
            Doc x = b.doc(d.did);
            return x != null && !x.cursors.containsKey(aid);
        }, 8000);
        check("离开文档后标记被清除", cleared, cleared ? "" : "仍残留");

        a.sendDocCursor(d.did, 0);

                System.out.println("\n[6] 改名");
        b.setNick("贝塔改");
        boolean renamed = waitFor(() -> "贝塔改".equals(a.nameOf(bid)), 6000);
        check("改名实时同步", renamed, "A 看到=" + a.nameOf(bid));

        System.out.println("\n[7] 断点续传");
        File big = new File(base, "big.bin");
        byte[] bigData = new byte[8 * 1024 * 1024];
        new Random(7).nextBytes(bigData);
        FileOutputStream bo = new FileOutputStream(big);
        bo.write(bigData);
        bo.close();

        String key = Node.fileKey(big);
        File partsDir = new File(base, "b/parts");
        partsDir.mkdirs();
        File part = new File(partsDir, key + ".part");
        int pre = 3 * 1024 * 1024 + 12345;
        FileOutputStream po = new FileOutputStream(part);
        po.write(bigData, 0, pre);
        po.close();
        check("预置 3MB 断点分片", part.length() == pre, Node.human(part.length()));

        Transfer rt = a.sendFile(bid, big);
        boolean done2 = waitFor(() -> rt.state == Transfer.DONE, 60000);
        check("续传完成", done2, done2 ? "" : "state=" + rt.state + " err=" + rt.error);
        check("发送方从断点开始", rt.offset == pre, "offset=" + Node.human(rt.offset) + " / " + Node.human(rt.size));

        Transfer recvT = null;
        for (Transfer t : b.transferList()) {
            if (t.direction == Transfer.RECEIVING && t.name.equals("big.bin")) {
                recvT = t;
            }
        }
        check("接收方从断点续写", recvT != null && recvT.offset == pre,
                recvT == null ? "无" : "offset=" + Node.human(recvT.offset));

        File recvBig = null;
        if (recvT != null) {
            recvBig = new File(recvT.path);
        }
        if (recvBig != null && recvBig.exists()) {
            byte[] gotBig = new byte[(int) recvBig.length()];
            java.io.DataInputStream di = new java.io.DataInputStream(new java.io.FileInputStream(recvBig));
            di.readFully(gotBig);
            di.close();
            check("续传后内容完全一致", java.util.Arrays.equals(bigData, gotBig), "size=" + gotBig.length);
        } else {
            check("续传文件已落地", false, recvBig == null ? "无" : recvBig.getAbsolutePath());
        }
        File[] parts = partsDir.listFiles();
        check("临时分片已清理", parts == null || parts.length == 0, parts == null ? "" : parts.length + " 个残留");

        File big2 = new File(base, "big2.bin");
        byte[] bigData2 = bigData.clone();
        bigData2[0] ^= 0x7f;
        FileOutputStream b2o = new FileOutputStream(big2);
        b2o.write(bigData2);
        b2o.close();
        check("内容不同则指纹不同（不会误续传）", !Node.fileKey(big2).equals(key),
                key.substring(0, 12) + "… vs " + Node.fileKey(big2).substring(0, 12) + "…");
        check("大小相同但内容不同的文件不复用断点",
                new File(partsDir, Node.fileKey(big2) + ".part").length() == 0, "");
        System.out.println("\n[8] 备注名");
        a.setAlias(bid, "老王");
        boolean aliasOk = waitFor(() -> "老王".equals(a.nameOf(bid)), 4000);
        check("设置备注名后显示生效", aliasOk, a.nameOf(bid));
        check("对方真实昵称不变", !"老王".equals(b.nick), "B 自称 " + b.nick);
        Node a2 = new Node(new P(new File(base, "a"), "PC-A"));
        a2.start();
        check("备注名重启后仍在", "老王".equals(a2.nameOf(bid)), a2.nameOf(bid));
        a2.stop();
        a.setAlias(bid, "");
        check("清空备注恢复原名", waitFor(() -> !"老王".equals(a.nameOf(bid)), 4000), a.nameOf(bid));

        System.out.println("\n[9] 展示名回退（备注 > 昵称 > 设备名）");
        Peer fake = new Peer();
        fake.id = "dev-x";
        fake.nick = "";
        fake.device = "ThinkCentre M720q";
        a.peers.put("dev-x", fake);
        check("无昵称时显示设备名", "ThinkCentre M720q".equals(a.nameOf("dev-x")), a.nameOf("dev-x"));
        fake.nick = "未命名";
        check("占位昵称也回退设备名", "ThinkCentre M720q".equals(a.nameOf("dev-x")), a.nameOf("dev-x"));
        fake.nick = "小黑的PC";
        check("有昵称时显示昵称", "小黑的PC".equals(a.nameOf("dev-x")), a.nameOf("dev-x"));
        a.setAlias("dev-x", "实验室机器");
        check("备注优先于昵称", waitFor(() -> "实验室机器".equals(a.nameOf("dev-x")), 4000),
                a.nameOf("dev-x"));
        a.peers.remove("dev-x");
        check("设备下线后仍显示备注", "实验室机器".equals(a.nameOf("dev-x")), a.nameOf("dev-x"));
        a.setAlias("dev-x", "");
        check("全都没有时退回未知设备", waitFor(() -> "未知设备".equals(a.nameOf("dev-x")), 4000),
                a.nameOf("dev-x"));

        System.out.println("\n[10] 引用 / 撤回 / 语音");
        int before = b.chatList(aid).size();
        a.sendChat(bid, "原始消息，稍后会撤回");
        final String[] originId = new String[1];
        boolean gotOrigin = waitFor(() -> {
            List<Node.ChatMsg> l = b.chatList(aid);
            if (l.size() > before) {
                originId[0] = l.get(l.size() - 1).mid;
                return true;
            }
            return false;
        }, 6000);
        check("消息送达并带 id", gotOrigin && originId[0] != null && !originId[0].isEmpty(),
                gotOrigin ? originId[0] : "超时");

        if (gotOrigin) {
            b.sendChat(aid, "这条是引用回复", originId[0], "原始消息，稍后会撤回", "A");
            boolean quoted = waitFor(() -> {
                List<Node.ChatMsg> l = a.chatList(bid);
                return !l.isEmpty() && "这条是引用回复".equals(l.get(l.size() - 1).text);
            }, 6000);
            List<Node.ChatMsg> al = a.chatList(bid);
            Node.ChatMsg qm = al.isEmpty() ? null : al.get(al.size() - 1);
            check("引用信息送达", quoted && qm != null && originId[0].equals(qm.quoteId)
                    && "原始消息，稍后会撤回".equals(qm.quoteText),
                    qm == null ? "无" : qm.quoteName + " / " + qm.quoteText);

            check("可撤回窗口内允许撤回", a.canRecall(bid, originId[0]), "");

            a.recallChat(bid, originId[0]);
            boolean recalled = waitFor(() -> {
                for (Node.ChatMsg m : a.chatList(bid)) {
                    if (m.mid.equals(originId[0])) {
                        return m.recalled;
                    }
                }
                return false;
            }, 5000);
            check("本端标记已撤回", recalled, "");
            boolean peerRecalled = waitFor(() -> {
                for (Node.ChatMsg m : b.chatList(aid)) {
                    if (m.mid.equals(originId[0])) {
                        return m.recalled;
                    }
                }
                return false;
            }, 6000);
            check("对端同步撤回", peerRecalled, "");
            check("撤回后不可再次撤回", !a.canRecall(bid, originId[0]), "");
        }

        // 语音：0.5 秒静音 PCM 包成 WAV
        int pcmLen = 16000;
        byte[] wav = new byte[44 + pcmLen];
        byte[] head = "RIFF".getBytes();
        System.arraycopy(head, 0, wav, 0, 4);
        wav[4] = (byte) (36 + pcmLen);
        wav[8] = 'W';
        wav[9] = 'A';
        wav[10] = 'V';
        wav[11] = 'E';
        int before2 = b.chatList(aid).size();
        a.sendVoice(bid, wav, 1, "", "", "");
        final Node.ChatMsg[] voiceMsg = new Node.ChatMsg[1];
        boolean gotVoice = waitFor(() -> {
            List<Node.ChatMsg> l = b.chatList(aid);
            if (l.size() > before2) {
                Node.ChatMsg last = l.get(l.size() - 1);
                if (last.voice) {
                    voiceMsg[0] = last;
                    return true;
                }
            }
            return false;
        }, 8000);
        check("语音消息送达", gotVoice, gotVoice ? "时长 " + voiceMsg[0].voiceDur + "s" : "超时");
        if (gotVoice) {
            File vf = new File(voiceMsg[0].voicePath);
            check("语音文件已落地", vf.exists() && vf.length() == wav.length,
                    vf.exists() ? vf.length() + " 字节" : "文件不存在");
        }

        System.out.println("\n[10] 合并算法单元测试");
        String m1 = DocMerge.merge("a\nb\nc\n", "A\nb\nc\n", "a\nb\nC\n");
        check("非重叠修改合并", "A\nb\nC\n".equals(m1), m1.replace("\n", "\\n"));
        String m2 = DocMerge.merge("a\nb\nc\n", "a\nB1\nc\n", "a\nB2\nc\n");
        check("重叠修改产生冲突标记", DocMerge.hasConflict(m2), "");
        String m3 = DocMerge.merge("x\n", "x\ny\n", "x\ny\n");
        check("相同修改不冲突", "x\ny\n".equals(m3), "");

        a.stop();
        b.stop();
        System.out.println("\n== 结果: " + pass + " 通过, " + fail + " 失败 ==");
        System.exit(fail == 0 ? 0 : 1);
    }

    static String indent(String s) {
        StringBuilder sb = new StringBuilder();
        for (String line : s.split("\n")) {
            sb.append("    | ").append(line).append('\n');
        }
        return sb.toString();
    }

    static void deleteRec(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                deleteRec(k);
            }
        }
        f.delete();
    }
}
