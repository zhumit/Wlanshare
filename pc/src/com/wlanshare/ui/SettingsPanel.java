package com.wlanshare.ui;

import com.wlanshare.core.Node;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

/** 设置：昵称、接收目录、自动策略、网络信息。 */
public class SettingsPanel implements Section {

    private final MainFrame frame;
    private final Node node;
    private final JPanel view = new JPanel(new BorderLayout());
    private final JPanel box = new JPanel();
    private final UI.RoundField nick = new UI.RoundField("你的昵称", 16);
    private final UI.RoundField recv = new UI.RoundField("接收文件保存目录", 24);
    private final JPanel infoBox = new JPanel();

    public SettingsPanel(MainFrame frame, Node node) {
        this.frame = frame;
        this.node = node;
        view.setOpaque(false);
        view.add(UI.header("设置", "所有配置保存在本机，不上传任何服务器", null), BorderLayout.NORTH);

        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));

        // 个人资料
        Theme.Card c1 = card();
        JPanel r1 = new JPanel(new BorderLayout(12, 0));
        r1.setOpaque(false);
        r1.add(labelCol("昵称", "局域网内其他设备看到的名字"), BorderLayout.WEST);
        nick.setPreferredSize(new Dimension(240, 38));
        nick.setMaximumSize(new Dimension(240, 38));
        Theme.Btn save = new Theme.Btn("保存", Theme.Kind.PRIMARY).small();
        save.addActionListener(e -> {
            String v = nick.text().trim();
            if (v.isEmpty()) {
                frame.toast("昵称不能为空");
                return;
            }
            node.setNick(v);
            frame.toast("昵称已更新为 " + v);
        });
        nick.field.addActionListener(e -> save.doClick());
        r1.add(UI.row(nick, save), BorderLayout.CENTER);
        c1.add(r1, BorderLayout.NORTH);

        JPanel devRow = row("设备名称", "自动读取本机名称", Theme.label(node.deviceName, 12, Theme.SUB, false));
        c1.add(devRow, BorderLayout.CENTER);
        box.add(c1);
        box.add(UI.vgap(14));

        // 文件
        Theme.Card c2 = card();
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

        recv.setPreferredSize(new Dimension(340, 38));
        recv.setMaximumSize(new Dimension(340, 38));
        Theme.Btn browse = new Theme.Btn("浏览", Theme.Kind.GHOST).small();
        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setDialogTitle("选择接收文件保存目录");
            if (fc.showOpenDialog(view) == JFileChooser.APPROVE_OPTION) {
                node.setRecvDir(fc.getSelectedFile().getAbsolutePath());
                recv.setText(node.recvDir.getAbsolutePath());
                frame.toast("接收目录已更新");
            }
        });
        JPanel recvRow = row("接收目录", "对方发来的文件保存在这里",
                UI.row(recv, browse));
        col.add(recvRow);

        UI.Toggle t1 = new UI.Toggle(node.autoAcceptFiles);
        t1.onChange(v -> {
            node.setAutoAcceptFiles(v);
            frame.toast(v ? "已开启自动接收" : "收到文件时会先询问");
        });
        col.add(row("自动接收文件", "关闭后需要手动确认每个文件", t1));

        UI.Toggle t2 = new UI.Toggle(node.autoAcceptFriends);
        t2.onChange(v -> {
            node.setAutoAcceptFriends(v);
            frame.toast(v ? "已开启自动同意好友申请" : "好友申请需要手动处理");
        });
        col.add(row("自动同意好友", "安装客户端即可被发现并添加", t2));
        c2.add(col, BorderLayout.CENTER);
        box.add(c2);
        box.add(UI.vgap(14));

        // 网络信息
        Theme.Card c3 = card();
        JPanel c3col = new JPanel();
        c3col.setOpaque(false);
        c3col.setLayout(new BoxLayout(c3col, BoxLayout.Y_AXIS));
        infoBox.setOpaque(false);
        infoBox.setLayout(new BoxLayout(infoBox, BoxLayout.Y_AXIS));
        c3col.add(infoBox);
        c3.add(c3col, BorderLayout.CENTER);
        box.add(c3);
        box.add(UI.vgap(14));

        // 提示
        Theme.Card c4 = card();
        c4.setLayout(new BorderLayout());
        JPanel tips = new JPanel();
        tips.setOpaque(false);
        tips.setLayout(new BoxLayout(tips, BoxLayout.Y_AXIS));
        tips.add(Theme.label("使用提示", 13, Theme.TEXT, true));
        tips.add(UI.vgap(8));
        for (String t : new String[]{
                "1. 双方需连在同一个局域网 / WiFi（同一路由器下即可）。",
                "2. 首次运行 Windows 会弹出防火墙提示，请选择「允许访问」，否则无法被发现。",
                "3. 若始终搜不到设备，请检查路由器是否开启了「AP 隔离 / 客户端隔离」。",
                "4. 传输为设备间直连，不经过任何服务器，断网也可在同一局域网内使用。",
                "5. 文档协作支持离线编辑，重新连上后会自动合并双方修改。"
        }) {
            javax.swing.JLabel l = Theme.label(t, 11, Theme.SUB, false);
            l.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
            tips.add(l);
        }
        c4.add(tips, BorderLayout.CENTER);
        box.add(c4);
        box.add(UI.vgap(14));

        Theme.Card c5 = card();
        c5.setLayout(new BorderLayout());
        JPanel about = new JPanel(new BorderLayout());
        about.setOpaque(false);
        about.add(Theme.label("WlanShare 1.0 · 局域网文件与协作工具", 11, Theme.MUTED, false), BorderLayout.WEST);
        about.add(Theme.label("无需服务器 · 无需账号", 11, Theme.MUTED, false), BorderLayout.EAST);
        c5.add(about, BorderLayout.CENTER);
        box.add(c5);
        box.add(UI.vgap(20));

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(box, BorderLayout.NORTH);
        view.add(new Theme.SlimScroll(wrap), BorderLayout.CENTER);

        nick.setText(node.nick);
        recv.setText(node.recvDir.getAbsolutePath());
    }

    private Theme.Card card() {
        Theme.Card c = new Theme.Card();
        c.setLayout(new BorderLayout());
        c.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1000));
        return c;
    }

    private JComponent labelCol(String title, String desc) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setPreferredSize(new Dimension(150, 44));
        p.add(Theme.label(title, 12, Theme.TEXT, true));
        p.add(UI.vgap(3));
        p.add(Theme.label(desc, 10, Theme.MUTED, false));
        return p;
    }

    private JPanel row(String title, String desc, JComponent control) {
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
        p.add(labelCol(title, desc), BorderLayout.WEST);
        JPanel right = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        right.setOpaque(false);
        right.add(control);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(right, BorderLayout.CENTER);
        p.add(wrap, BorderLayout.CENTER);
        return p;
    }

    private JPanel infoRow(String k, String v, boolean copy) {
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        javax.swing.JLabel key = Theme.label(k, 11, Theme.MUTED, false);
        key.setPreferredSize(new Dimension(110, 20));
        p.add(key, BorderLayout.WEST);
        p.add(Theme.label(v, 12, Theme.TEXT, false), BorderLayout.CENTER);
        if (copy) {
            UI.Link l = new UI.Link("复制", Theme.ACCENT);
            l.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(v), null);
                    frame.toast("已复制");
                }
            });
            p.add(UI.rowRight(l), BorderLayout.EAST);
        }
        return p;
    }

    @Override
    public JComponent view() {
        return view;
    }

    @Override
    public void onShow() {
        nick.setText(node.nick);
        recv.setText(node.recvDir.getAbsolutePath());
        refreshInfo();
    }

    @Override
    public void refresh(String what) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> refresh(what));
            return;
        }
        refreshInfo();
    }

    private void refreshInfo() {
        infoBox.removeAll();
        infoBox.add(infoRow("本机地址", node.localIp(), true));
        infoBox.add(infoRow("传输端口", String.valueOf(node.tcpPort), false));
        infoBox.add(infoRow("发现端口", String.valueOf(node.udpPort), false));
        infoBox.add(infoRow("设备标识", node.id, true));
        infoBox.add(infoRow("已发现设备", node.peerList().size() + " 台", false));
        infoBox.add(infoRow("广播收包", node.hellos.get() + " 条", false));
        infoBox.revalidate();
        infoBox.repaint();
    }
}
