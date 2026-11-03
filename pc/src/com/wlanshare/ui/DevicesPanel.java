package com.wlanshare.ui;

import com.wlanshare.core.Node;
import com.wlanshare.core.Peer;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/** 附近设备：自动发现的局域网客户端。 */
public class DevicesPanel implements Section {

    private final MainFrame frame;
    private final Node node;
    private final JPanel view = new JPanel(new BorderLayout());
    private final JPanel grid = new JPanel(new GridLayout(0, 2, 14, 14));
    private final UI.RoundField search = new UI.RoundField("搜索昵称或设备名", 12);
    private String filter = "";

    public DevicesPanel(MainFrame frame, Node node) {
        this.frame = frame;
        this.node = node;
        view.setOpaque(false);

        Theme.Btn refresh = new Theme.Btn("刷新", Theme.Kind.GHOST).small();
        refresh.addActionListener(e -> {
            node.peerList();
            refresh("peers");
            frame.toast("已刷新附近设备");
        });
        search.setPreferredSize(new Dimension(220, 38));
        search.field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                filter = search.text().trim();
                rebuild();
            }

            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                filter = search.text().trim();
                rebuild();
            }

            public void changedUpdate(javax.swing.event.DocumentEvent e) {
            }
        });

        JPanel actions = UI.row(search, refresh);
        view.add(UI.header("附近设备", "同一局域网内安装了客户端的设备会自动出现在这里", actions), BorderLayout.NORTH);

        grid.setOpaque(false);
        JPanel pad = new JPanel(new BorderLayout());
        pad.setOpaque(false);
        pad.add(grid, BorderLayout.NORTH);

        Theme.SlimScroll scroll = new Theme.SlimScroll(pad);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(scroll, BorderLayout.CENTER);
        view.add(center, BorderLayout.CENTER);
    }

    @Override
    public JComponent view() {
        return view;
    }

    @Override
    public void onShow() {
        refresh("peers");
    }

    @Override
    public void refresh(String what) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> refresh(what));
            return;
        }
        rebuild();
    }

    private void rebuild() {
        grid.removeAll();
        List<Peer> peers = node.peerList();
        int shown = 0;
        for (Peer p : peers) {
            if (!filter.isEmpty()) {
                String s = (node.nameOf(p.id) + p.nick + p.device + p.host).toLowerCase();
                if (!s.contains(filter.toLowerCase())) {
                    continue;
                }
            }
            grid.add(new PeerCard(p));
            shown++;
        }
        if (shown == 0) {
            grid.setLayout(new BorderLayout());
            JPanel empty = UI.empty("wifi",
                    filter.isEmpty() ? "正在搜索附近设备…" : "没有匹配的设备",
                    filter.isEmpty() ? "请确认双方在同一 WiFi / 局域网，并允许 Windows 防火墙访问" : "换个关键词试试");
            grid.add(empty, BorderLayout.CENTER);
            JPanel wrap = new JPanel(new BorderLayout());
            wrap.setOpaque(false);
            wrap.setBorder(BorderFactory.createEmptyBorder(60, 0, 0, 0));
            wrap.add(empty, BorderLayout.NORTH);
            grid.removeAll();
            grid.add(wrap, BorderLayout.CENTER);
        } else {
            grid.setLayout(new GridLayout(0, 2, 14, 14));
        }
        grid.revalidate();
        grid.repaint();
    }

    /** 单台设备卡片。 */
    private class PeerCard extends Theme.Card {
        private final Peer p;

        PeerCard(Peer p) {
            super();
            this.p = p;
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));

            JComponent info = new JComponent() {
                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(200, 62);
                }

                @Override
                protected void paintComponent(Graphics g) {
                    Icons.avatar(g, 0, 6, 46, node.initialOf(p.id), p.id, p.connected);
                    Graphics2D g2 = Theme.aa(g);
                    g2.setFont(Theme.bold(14));
                    g2.setColor(Theme.TEXT);
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(Theme.ellipsis(node.nameOf(p.id), fm, getWidth() - 60), 58, 26);
                    g2.setFont(Theme.font(11));
                    g2.setColor(Theme.SUB);
                    FontMetrics fm2 = g2.getFontMetrics();
                    String alias = node.aliasOf(p.id);
                    String dev = (p.deviceType == 1 ? "手机" : p.deviceType == 0 ? "电脑" : "设备") + " · " + p.device
                            + (alias.isEmpty() ? "" : "（" + p.nick + "）");
                    g2.drawString(Theme.ellipsis(dev, fm2, getWidth() - 60), 58, 42);
                    g2.setColor(Theme.MUTED);
                    String ip = p.host.isEmpty() ? "未知地址" : p.host;
                    g2.drawString(Theme.ellipsis(ip + (p.connected ? " · 已连接" : " · 待连接"), fm2, getWidth() - 60), 58, 57);
                    g2.dispose();
                }
            };
            add(info, BorderLayout.CENTER);

            JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            btns.setOpaque(false);
            if (p.friend) {
                JLabel tag = Theme.label("已是好友", 11, Theme.GREEN, true);
                tag.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
                btns.add(tag);
            } else {
                JComponent add = chip("加好友", "plus", Theme.ACCENT, () -> {
                    node.requestFriend(p.id);
                    frame.toast("已向 " + p.nick + " 发送好友申请");
                });
                btns.add(add);
            }
            btns.add(chip("发消息", "chat", new Color(0x5B6478), () -> frame.openChat(p.id)));
            btns.add(chip("传文件", "upload", new Color(0x5B6478), () -> frame.openFilesFor(p.id)));
            add(btns, BorderLayout.SOUTH);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    frame.openChat(p.id);
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    popup(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    popup(e);
                }

                private void popup(MouseEvent e) {
                    if (!e.isPopupTrigger()) {
                        return;
                    }
                    javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
                    javax.swing.JMenuItem rn = new javax.swing.JMenuItem("重命名备注");
                    rn.setFont(Theme.font(12));
                    rn.addActionListener(ev -> {
                        String cur = node.aliasOf(p.id);
                        String v = UI.prompt(view, "重命名备注",
                                "给「" + p.nick + "」设置一个只在本机显示的名字，留空即恢复原名",
                                cur.isEmpty() ? p.nick : cur);
                        if (v != null) {
                            node.setAlias(p.id, v);
                        }
                    });
                    menu.add(rn);
                    if (!p.friend) {
                        javax.swing.JMenuItem ad = new javax.swing.JMenuItem("加为好友");
                        ad.setFont(Theme.font(12));
                        ad.addActionListener(ev -> node.requestFriend(p.id));
                        menu.add(ad);
                    }
                    menu.show(PeerCard.this, e.getX(), e.getY());
                }
            });
        }

        private JComponent chip(String text, String icon, Color color, Runnable action) {
            JComponent c = new JComponent() {
                boolean hover;

                {
                    setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
                    addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseEntered(MouseEvent e) {
                            hover = true;
                            repaint();
                        }

                        @Override
                        public void mouseExited(MouseEvent e) {
                            hover = false;
                            repaint();
                        }

                        @Override
                        public void mouseClicked(MouseEvent e) {
                            action.run();
                        }
                    });
                }

                @Override
                public Dimension getPreferredSize() {
                    FontMetrics fm = Theme.fm(Theme.font(11));
                    return new Dimension(fm.stringWidth(text) + 34, 26);
                }

                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = Theme.aa(g);
                    g2.setColor(hover ? new Color(0xEDF2FD) : new Color(0xF4F6FB));
                    Theme.roundRect(g2, 0, 0, getWidth(), getHeight(), 8);
                    g2.dispose();
                    Icons.draw(g, icon, 6, 5, 15, color);
                    Graphics2D g3 = Theme.aa(g);
                    g3.setFont(Theme.bold(11));
                    g3.setColor(color);
                    g3.drawString(text, 24, 17);
                    g3.dispose();
                }
            };
            return c;
        }
    }
}
