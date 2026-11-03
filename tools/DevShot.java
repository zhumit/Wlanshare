import com.wlanshare.PcPlatform;
import com.wlanshare.core.Doc;
import com.wlanshare.core.Node;
import com.wlanshare.core.Peer;
import com.wlanshare.core.Transfer;
import com.wlanshare.ui.ChatPanel;
import com.wlanshare.ui.DevicesPanel;
import com.wlanshare.ui.DocsPanel;
import com.wlanshare.ui.FilesPanel;
import com.wlanshare.ui.MainFrame;
import com.wlanshare.ui.SettingsPanel;
import com.wlanshare.ui.Theme;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** 用假数据把每个面板按 1:1 渲染成 PNG，用于界面走查。 */
public final class DevShot {

    public static void main(String[] args) throws Exception {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        Theme.install();

        final Node node = new Node(new PcPlatform());
        node.start();
        mock(node);

        final MainFrame frame = new MainFrame(node);
        frame.addSection("devices", "附近设备", "devices", new DevicesPanel(frame, node));
        frame.addSection("chat", "消息", "chat", new ChatPanel(frame, node));
        frame.addSection("files", "文件", "files", new FilesPanel(frame, node));
        frame.addSection("docs", "文档", "doc", new DocsPanel(frame, node));
        frame.addSection("settings", "设置", "settings", new SettingsPanel(frame, node));
        frame.setSize(1200, 780);
        frame.setVisible(true);

        String[] keys = {"devices", "chat", "files", "docs", "settings"};
        for (String k : keys) {
            if ("chat".equals(k)) {
                SwingUtilities.invokeAndWait(() -> frame.openChat("peer-a1"));
                Thread.sleep(400);
            }
            shot(frame, k, "build/ui-" + k + ".png");
        }

        // 文件页：发送目标跟随备注名 / 无昵称回退设备名
        show(frame, "files");
        SwingUtilities.invokeAndWait(() -> frame.openFilesFor("peer-a1"));
        Thread.sleep(500);
        shot(frame, "files", "build/ui-files-alias.png");
        SwingUtilities.invokeAndWait(() -> frame.openFilesFor("peer-d4"));
        Thread.sleep(500);
        shot(frame, "files", "build/ui-files-nonick.png");

        // 文档页：三种视图 + 折叠左栏
        show(frame, "docs");
        clickSegment(frame, 0);
        shot(frame, "docs", "build/ui-docs-edit.png");
        clickSegment(frame, 2);
        shot(frame, "docs", "build/ui-docs-preview.png");
        clickSegment(frame, 1);
        clickIcon(frame, "收起文档列表");
        shot(frame, "docs", "build/ui-docs-collapsed.png");

        // 消息页：折叠左栏
        show(frame, "chat");
        SwingUtilities.invokeAndWait(() -> frame.openChat("peer-a1"));
        Thread.sleep(300);
        clickIcon(frame, "收起会话列表");
        shot(frame, "chat", "build/ui-chat-collapsed.png");

        // 文档页：选中一段文字，检查选中配色
        show(frame, "docs");
        Thread.sleep(600);
        SwingUtilities.invokeAndWait(() -> {
            for (java.awt.Component c : all(frame.getContentPane())) {
                if (c instanceof javax.swing.JTextArea && c.isShowing()) {
                    javax.swing.JTextArea ta = (javax.swing.JTextArea) c;
                    ta.setCaretPosition(2);
                    ta.moveCaretPosition(26);
                    break;
                }
            }
        });
        Thread.sleep(400);
        shot(frame, "docs", "build/ui-docs-select.png");

        // 右键菜单：在消息正文（子组件）上派发右键，应当弹出
        show(frame, "chat");
        SwingUtilities.invokeAndWait(() -> frame.openChat("peer-a1"));
        Thread.sleep(700);
        SwingUtilities.invokeAndWait(() -> {
            int found = 0;
            for (java.awt.Component c : all(frame.getContentPane())) {
                if (c instanceof javax.swing.JTextArea && c.isShowing()) {
                    found++;
                    String parent = c.getParent() == null ? "-"
                            : c.getParent().getClass().getSimpleName();
                    System.out.println("MENU 候选: " + parent + " size=" + c.getSize());
                    java.awt.event.MouseEvent ev = new java.awt.event.MouseEvent(
                            c, java.awt.event.MouseEvent.MOUSE_RELEASED,
                            System.currentTimeMillis(),
                            java.awt.event.InputEvent.BUTTON3_DOWN_MASK,
                            16, 10, 1, true, java.awt.event.MouseEvent.BUTTON3);
                    c.dispatchEvent(ev);
                    javax.swing.MenuElement[] path =
                            javax.swing.MenuSelectionManager.defaultManager().getSelectedPath();
                    System.out.println("MENU 选中路径长度: " + path.length);
                    for (javax.swing.MenuElement el : path) {
                        if (el instanceof javax.swing.JPopupMenu) {
                            for (java.awt.Component item : ((javax.swing.JPopupMenu) el).getComponents()) {
                                if (item instanceof javax.swing.JMenuItem) {
                                    javax.swing.JMenuItem mi = (javax.swing.JMenuItem) item;
                                    System.out.println("MENU 项: " + mi.getText()
                                            + (mi.isEnabled() ? "" : " (禁用)"));
                                }
                            }
                        }
                    }
                    javax.swing.MenuSelectionManager.defaultManager().clearSelectedPath();
                    break;
                }
            }
            if (found == 0) {
                System.out.println("MENU: 未找到可见的消息正文");
            }
        });
        Thread.sleep(300);

        // 备注名对话框
        aliasDialogShot(frame, "build/ui-alias-dialog.png");

        // 侧边栏折叠
        show(frame, "chat");
        SwingUtilities.invokeAndWait(() -> frame.openChat("peer-a1"));
        Thread.sleep(300);
        clickIcon(frame, "收起侧边栏");
        shot(frame, "chat", "build/ui-sidebar-collapsed.png");
        clickIcon(frame, "展开侧边栏");
        System.out.println("done");
        frame.dispose();
        node.stop();
        System.exit(0);
    }

    static void show(MainFrame f, String key) throws Exception {
        SwingUtilities.invokeAndWait(() -> f.show(key));
        Thread.sleep(400);
    }

    static <T> T find(java.awt.Container root, Class<T> type, String tip) {
        for (java.awt.Component c : root.getComponents()) {
            if (type.isInstance(c)) {
                if (tip == null || tip.equals(((javax.swing.JComponent) c).getToolTipText())) {
                    return type.cast(c);
                }
            }
            if (c instanceof java.awt.Container) {
                T r = find((java.awt.Container) c, type, tip);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    static void clickSegment(MainFrame f, int index) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            com.wlanshare.ui.Theme.Segmented seg =
                    find(f.getContentPane(), com.wlanshare.ui.Theme.Segmented.class, null);
            if (seg == null) {
                System.out.println("segment not found");
                return;
            }
            int each = (seg.getWidth() - 6) / 3;
            int x = 3 + index * each + each / 2;
            java.awt.event.MouseEvent ev = new java.awt.event.MouseEvent(seg,
                    java.awt.event.MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
                    x, seg.getHeight() / 2, 1, false, java.awt.event.MouseEvent.BUTTON1);
            seg.dispatchEvent(ev);
        });
        Thread.sleep(350);
    }

    static void clickIcon(MainFrame f, String tip) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            com.wlanshare.ui.Theme.IconBtn b =
                    find(f.getContentPane(), com.wlanshare.ui.Theme.IconBtn.class, tip);
            if (b == null) {
                System.out.println("icon not found: " + tip);
                return;
            }
            java.awt.event.MouseEvent ev = new java.awt.event.MouseEvent(b,
                    java.awt.event.MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
                    b.getWidth() / 2, b.getHeight() / 2, 1, false, java.awt.event.MouseEvent.BUTTON1);
            b.dispatchEvent(ev);
        });
        Thread.sleep(400);
    }

    /** 触发「备注」按钮并把弹出的对话框单独渲染成图。 */
    static void aliasDialogShot(MainFrame f, String file) throws Exception {
        final javax.swing.JButton[] target = new javax.swing.JButton[1];
        SwingUtilities.invokeAndWait(() -> {
            target[0] = findButton(f.getContentPane(), "备注");
        });
        if (target[0] == null) {
            System.out.println("备注按钮未找到");
            return;
        }
        SwingUtilities.invokeLater(() -> target[0].doClick());
        Thread.sleep(900);
        for (java.awt.Window win : java.awt.Window.getWindows()) {
            if (win instanceof javax.swing.JDialog && win.isVisible()) {
                final javax.swing.JDialog w = (javax.swing.JDialog) win;
                SwingUtilities.invokeAndWait(() -> {
                    java.awt.Dimension d = w.getContentPane().getSize();
                    java.awt.image.BufferedImage img =
                            new java.awt.image.BufferedImage(d.width, d.height,
                                    java.awt.image.BufferedImage.TYPE_INT_RGB);
                    java.awt.Graphics2D g = img.createGraphics();
                    w.getContentPane().paint(g);
                    g.dispose();
                    try {
                        ImageIO.write(img, "png", new File(file));
                    } catch (Exception ignored) {
                    }
                    w.dispose();
                });
                System.out.println("saved " + file);
                return;
            }
        }
        System.out.println("对话框未出现");
    }

    static java.util.List<java.awt.Component> all(java.awt.Container root) {
        java.util.List<java.awt.Component> out = new java.util.ArrayList<java.awt.Component>();
        for (java.awt.Component c : root.getComponents()) {
            out.add(c);
            if (c instanceof java.awt.Container) {
                out.addAll(all((java.awt.Container) c));
            }
        }
        return out;
    }

    static void findMenus(java.awt.Container root, java.util.List<javax.swing.JPopupMenu> out) {
        for (java.awt.Component c : root.getComponents()) {
            if (c instanceof javax.swing.JPopupMenu) {
                out.add((javax.swing.JPopupMenu) c);
            }
            if (c instanceof java.awt.Container) {
                findMenus((java.awt.Container) c, out);
            }
        }
    }

    static javax.swing.JButton findButton(java.awt.Container root, String text) {
        for (java.awt.Component c : root.getComponents()) {
            if (c instanceof javax.swing.JButton && text.equals(((javax.swing.JButton) c).getText())) {
                return (javax.swing.JButton) c;
            }
            if (c instanceof java.awt.Container) {
                javax.swing.JButton r = findButton((java.awt.Container) c, text);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    static void shot(MainFrame f, String key, String file) throws Exception {
        SwingUtilities.invokeAndWait(() -> f.show(key));
        Thread.sleep(500);
        SwingUtilities.invokeAndWait(() -> {
            Dimension d = f.getContentPane().getSize();
            BufferedImage img = new BufferedImage(d.width, d.height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            f.getContentPane().paint(g);
            g.dispose();
            try {
                ImageIO.write(img, "png", new File(file));
            } catch (Exception ignored) {
            }
        });
        System.out.println("saved " + file);
    }

    static void mock(Node node) {
        node.nick = "我的工作站";
        node.id = "dev-self-01";

        Peer a = peer("peer-a1", "小美的 MacBook", "MacBook Pro · macOS", 0, "192.168.1.23", true);
        Peer b = peer("peer-b2", "客厅盒子", "Xiaomi TV", 1, "192.168.1.31", false);
        Peer c = peer("peer-c3", "设计师阿泽", "DESKTOP-7K2M", 0, "192.168.1.45", true);
        // 没有设置昵称的设备：列表里应回退显示设备名
        Peer d = peer("peer-d4", "未命名", "ThinkCentre M720q", 0, "192.168.1.77", true);
        node.peers.put(a.id, a);
        node.peers.put(b.id, b);
        node.peers.put(c.id, c);
        node.peers.put(d.id, d);
        node.friends.put(a.id, a.nick);
        node.friends.put(c.id, c.nick);
        node.friends.put(d.id, d.nick);
        node.aliases.put(a.id, "小美（设计）");

        List<Node.ChatMsg> chat = new ArrayList<Node.ChatMsg>();
        long now = System.currentTimeMillis();

        Node.ChatMsg m1 = new Node.ChatMsg(false, "文件我放桌面了，你直接拖过来就行", now - 3600_000);
        chat.add(m1);

        Node.ChatMsg m2 = new Node.ChatMsg(true, "收到，我这边打开客户端了，你先发吧", now - 3400_000);
        chat.add(m2);

        Node.ChatMsg m3 = new Node.ChatMsg(false, "另外那份「产品说明.md」我们一起改，我正在编辑第二段", now - 200_000);
        m3.quoteId = m2.mid;
        m3.quoteText = "收到，我这边打开客户端了，你先发吧";
        m3.quoteName = "我";
        chat.add(m3);

        Node.ChatMsg m4 = new Node.ChatMsg(true, "", now - 120_000);
        m4.voice = true;
        m4.voiceDur = 7;
        m4.voicePath = "";
        chat.add(m4);

        Node.ChatMsg m5 = new Node.ChatMsg(false, "这段语音我听了，可以，按这个方向改", now - 60_000);
        m5.quoteId = m4.mid;
        m5.quoteText = "[语音] 7\"";
        m5.quoteName = "我";
        chat.add(m5);

        Node.ChatMsg own = new Node.ChatMsg(true, "那我先把第二段改掉，晚点发你", now - 30_000);
        own.quoteId = m3.mid;
        own.quoteText = "另外那份「产品说明.md」我们一起改，我正在编辑第二段";
        own.quoteName = "小美（设计）";
        chat.add(own);

        Node.ChatMsg m6 = new Node.ChatMsg(true, "这条已经被撤回了", now - 30_000);
        m6.recalled = true;
        chat.add(m6);

        node.chats.put(a.id, chat);
        // 未读红圈演示：小美 3 条、阿泽 1 条
        node.unread.put(a.id, 3);
        node.unread.put(b.id, 5);
        node.unread.put(c.id, 1);
        List<Node.ChatMsg> chat2 = new ArrayList<Node.ChatMsg>();
        chat2.add(new Node.ChatMsg(false, "设计稿 v3 已上传", now - 86_400_000L));
        node.chats.put(c.id, chat2);

        Transfer t1 = new Transfer();
        t1.fid = "t1";
        t1.direction = Transfer.SENDING;
        t1.peerId = a.id;
        t1.peerNick = a.nick;
        t1.name = "宣传片_4K_终版.mp4";
        t1.size = 1_820_000_000L;
        t1.done = 620_000_000L;
        t1.offset = 210_000_000L;
        t1.state = Transfer.ACTIVE;
        t1.ts = now - 120_000;
        node.transfers.put(t1.fid, t1);

        Transfer t2 = new Transfer();
        t2.fid = "t2";
        t2.direction = Transfer.RECEIVING;
        t2.peerId = c.id;
        t2.peerNick = c.nick;
        t2.name = "设计规范 2026.pdf";
        t2.size = 8_400_000L;
        t2.done = 8_400_000L;
        t2.path = "D:/Programs/WlanShare/build/selftest/b/recv/payload.bin";
        t2.state = Transfer.DONE;
        t2.ts = now - 900_000;
        node.transfers.put(t2.fid, t2);

        Transfer t3 = new Transfer();
        t3.fid = "t3";
        t3.direction = Transfer.SENDING;
        t3.peerId = b.id;
        t3.peerNick = b.nick;
        t3.name = "家庭照片打包.zip";
        t3.size = 240_000_000L;
        t3.done = 96_000_000L;
        t3.state = Transfer.FAILED;
        t3.error = "连接中断，可重试续传";
        t3.ts = now - 1_800_000;
        node.transfers.put(t3.fid, t3);

        Doc d1 = new Doc();
        d1.did = "doc-1";
        d1.title = "产品说明";
        d1.ver = 7;
        d1.owner = "我的工作站";
        d1.updatedAt = now - 30_000;
        d1.text = "# 产品说明\n\n"
                + "WlanShare 是一款**局域网文件与协作工具**，安装即用，不需要服务器。\n\n"
                + "## 核心能力\n\n"
                + "- 自动发现同一网络内的设备\n"
                + "- 文件直连传输，支持断点续传\n"
                + "- Markdown 文档多人同时编辑\n\n"
                + "| 能力 | 状态 | 大小 |\n"
                + "| --- | :---: | ---: |\n"
                + "| 文件直连传输 | 完成 | 1.7 GB |\n"
                + "| 文档协作 | 完成 | 32 KB |\n\n"
                + "> 所有数据只在设备之间流动。\n\n"
                + "```html\n"
                + "<div style=\"padding:12px 14px;background-color:#F5F8FE;border-left:3px solid #A9BFEC\">\n"
                + "  <b>HTML 区块</b><br>\n"
                + "  这一段是文档里的 HTML 块，按原样渲染。\n"
                + "</div>\n"
                + "```\n\n"
                + "### 快速开始\n\n"
                + "1. 在两台设备上分别打开客户端\n"
                + "2. 在「附近设备」里点击「加好友」\n"
                + "3. 拖拽文件即可发送\n\n"
                + "代码示例：\n\n"
                + "```\nNode node = new Node(platform);\nnode.start();\n```\n";
        d1.editors.put("peer-a1", "小美的 MacBook");
        // 模拟两人正在编辑不同行
        d1.cursors.put("peer-a1", 4);
        d1.cursors.put("peer-c3", 9);
        node.docs.put(d1.did, d1);

        Doc d2 = new Doc();
        d2.did = "doc-2";
        d2.title = "会议纪要 10-30";
        d2.ver = 3;
        d2.owner = "设计师阿泽";
        d2.updatedAt = now - 86_400_000L;
        d2.text = "# 会议纪要\n\n- 确定传输协议\n- 确认 UI 风格\n";
        node.docs.put(d2.did, d2);
    }

    static Peer peer(String id, String nick, String device, int type, String host, boolean online) {
        Peer p = new Peer();
        p.id = id;
        p.nick = nick;
        p.device = device;
        p.deviceType = type;
        p.host = host;
        p.port = 9000;
        p.connected = online;
        p.lastSeen = System.currentTimeMillis();
        return p;
    }
}
