package com.wlanshare.ui;

import com.wlanshare.core.Node;
import com.wlanshare.core.Peer;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 主窗口：左侧导航 + 右侧内容 + 顶部提示条。 */
public class MainFrame extends JFrame implements Node.Listener {

    public final Node node;
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Map<String, Section> sections = new LinkedHashMap<String, Section>();
    private final Map<String, NavItem> navItems = new LinkedHashMap<String, NavItem>();
    private final Set<String> pending = new HashSet<String>();
    private Timer flush;
    private Sidebar sidebar;
    private String current = "";
    private Toast toast;

    public MainFrame(Node node) {
        super("WlanShare · 局域网协作");
        this.node = node;
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 660));
        setSize(1200, 780);
        setLocationRelativeTo(null);
        setIconImage(Icons.appIcon(64));
        getContentPane().setBackground(Theme.BG);

        content.setOpaque(true);
        content.setBackground(Theme.BG);
        content.setBorder(javax.swing.BorderFactory.createEmptyBorder(22, 26, 18, 26));

        sidebar = new Sidebar();
        add(sidebar, BorderLayout.WEST);
        add(content, BorderLayout.CENTER);

        flush = new Timer(110, e -> {
            Set<String> copy;
            synchronized (pending) {
                copy = new HashSet<String>(pending);
                pending.clear();
            }
            for (Section s : sections.values()) {
                s.refresh(String.join(",", copy));
            }
            sidebar.syncStats();
        });
        flush.setRepeats(false);

        toast = new Toast();
        getLayeredPane().add(toast, JLayeredPane.POPUP_LAYER);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                node.stop();
                dispose();
                System.exit(0);
            }
        });
    }

    public void addSection(String key, String label, String icon, Section s) {
        sections.put(key, s);
        content.add(s.view(), key);
        NavItem item = new NavItem(key, label, icon);
        navItems.put(key, item);
        sidebar.navBox.add(item);
        if (current.isEmpty()) {
            show(key);
        }
    }

    public void show(String key) {
        Section s = sections.get(key);
        if (s == null) {
            return;
        }
        current = key;
        cards.show(content, key);
        for (Map.Entry<String, NavItem> e : navItems.entrySet()) {
            e.getValue().setActive(e.getKey().equals(key));
        }
        s.onShow();
        s.refresh("all");
        sidebar.syncStats();
    }

    public String currentSection() {
        return current;
    }

    public void openChat(String peerId) {
        show("chat");
        Section s = sections.get("chat");
        if (s instanceof ChatPanel) {
            ((ChatPanel) s).openWith(peerId);
        }
    }

    public void openFilesFor(String peerId) {
        show("files");
        Section s = sections.get("files");
        if (s instanceof FilesPanel) {
            ((FilesPanel) s).setTarget(peerId);
        }
    }

    public void openDocs() {
        show("docs");
    }

    // ---------------------------------------------------------------- Node.Listener

    @Override
    public void onState(String what) {
        SwingUtilities.invokeLater(() -> {
            synchronized (pending) {
                pending.add(what);
            }
            if (!flush.isRunning()) {
                flush.restart();
            }
        });
    }

    @Override
    public void onChat(String peerId, boolean mine, String text, long ts) {
        SwingUtilities.invokeLater(() -> {
            Section s = sections.get("chat");
            if (s != null) {
                s.refresh("chat");
            }
        });
    }

    @Override
    public void onToast(String msg) {
        SwingUtilities.invokeLater(() -> toast(msg));
    }

    public void toast(String msg) {
        if (msg == null || msg.isEmpty()) {
            return;
        }
        toast.show(msg);
    }

    // ---------------------------------------------------------------- 侧栏

    private class Sidebar extends JPanel {
        final JPanel navBox = new JPanel();
        final JComponent me = new Identity();
        int friendCount;
        int peerCount;
        boolean collapsed;
        JLabel brandName;
        JLabel navTitle;
        JLabel statsLabel;
        JLabel hintLabel;
        JComponent foldBtn;
        JComponent expandBtn;
        JPanel top;
        JPanel bottom;

        Sidebar() {
            setLayout(new BorderLayout());
            setBackground(Theme.SIDEBAR);
            setPreferredSize(new Dimension(238, 100));
            setBorder(javax.swing.BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER));

            top = new JPanel();
            top.setOpaque(false);
            top.setLayout(new javax.swing.BoxLayout(top, javax.swing.BoxLayout.Y_AXIS));
            top.setBorder(javax.swing.BorderFactory.createEmptyBorder(22, 18, 10, 18));

            JPanel brand = new JPanel(new BorderLayout(10, 0));
            brand.setOpaque(false);
            JComponent logo = new JComponent() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = Theme.aa(g);
                    g2.setPaint(new java.awt.GradientPaint(0, 0, new Color(0x3D7BFF), 30, 30, new Color(0x6D5EF8)));
                    g2.fillRoundRect(0, 0, 30, 30, 10, 10);
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine(9, 11, 21, 11);
                    g2.drawLine(9, 15, 21, 15);
                    g2.drawLine(9, 19, 21, 19);
                    g2.dispose();
                }

                @Override
                public Dimension getPreferredSize() {
                    return new Dimension(30, 30);
                }
            };
            brand.add(logo, BorderLayout.WEST);
            brandName = Theme.label("WlanShare", 15, Theme.TEXT, true);
            brand.add(brandName, BorderLayout.CENTER);
            foldBtn = new Theme.IconBtn("chevron-left", "收起侧边栏", () -> setCollapsed(true));
            brand.add(UI.rowRight(foldBtn), BorderLayout.EAST);
            top.add(fill(brand, 30));
            top.add(UI.vgap(20));
            top.add(fill(me, 58));
            top.add(UI.vgap(18));

            navTitle = Theme.label("导航", 11, Theme.MUTED, true);
            navTitle.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 8, 6, 0));
            navTitle.setAlignmentX(LEFT_ALIGNMENT);
            top.add(fill(navTitle, 20));

            navBox.setOpaque(false);
            navBox.setLayout(new javax.swing.BoxLayout(navBox, javax.swing.BoxLayout.Y_AXIS));
            navBox.setAlignmentX(LEFT_ALIGNMENT);
            JPanel navWrap = new JPanel(new BorderLayout());
            navWrap.setOpaque(false);
            navWrap.add(navBox, BorderLayout.CENTER);
            navWrap.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            navWrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
            top.add(navWrap);
            add(top, BorderLayout.NORTH);

            bottom = new JPanel();
            bottom.setOpaque(false);
            bottom.setLayout(new javax.swing.BoxLayout(bottom, javax.swing.BoxLayout.Y_AXIS));
            bottom.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 20, 18, 20));
            statsLabel = Theme.label(" ", 11, Theme.MUTED, false);
            statsLabel.setAlignmentX(LEFT_ALIGNMENT);
            expandBtn = new Theme.IconBtn("chevron-right", "展开侧边栏", () -> setCollapsed(false));
            expandBtn.setVisible(false);
            expandBtn.setAlignmentX(LEFT_ALIGNMENT);
            bottom.add(statsLabel);
            bottom.add(expandBtn);
            bottom.add(UI.vgap(6));
            JLabel hint = Theme.label("同一局域网内自动发现", 10, new Color(0xB3BAC9), false);
            hint.setAlignmentX(LEFT_ALIGNMENT);
            hintLabel = hint;
            bottom.add(hint);
            add(bottom, BorderLayout.SOUTH);
        }

        /** 折叠 / 展开整条侧边栏。 */
        void setCollapsed(boolean c) {
            collapsed = c;
            setPreferredSize(new Dimension(c ? 74 : 238, 100));
            top.setBorder(javax.swing.BorderFactory.createEmptyBorder(22, c ? 12 : 18, 10, c ? 12 : 18));
            bottom.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, c ? 14 : 20, 18, c ? 14 : 20));
            brandName.setVisible(!c);
            navTitle.setVisible(!c);
            statsLabel.setVisible(!c);
            hintLabel.setVisible(!c);
            if (foldBtn != null) {
                foldBtn.setVisible(!c);
            }
            if (expandBtn != null) {
                expandBtn.setVisible(c);
            }
            me.revalidate();
            revalidate();
            repaint();
            MainFrame.this.validate();
            MainFrame.this.repaint();
        }

        private JComponent fill(JComponent c, int h) {
            JPanel w = new JPanel(new BorderLayout());
            w.setOpaque(false);
            w.add(c, BorderLayout.CENTER);
            w.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            w.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
            w.setPreferredSize(new Dimension(206, h));
            w.putClientProperty("fill", Boolean.TRUE);
            return w;
        }

        void syncStats() {
            int peers = node.peerList().size();
            int friends = node.friends.size();
            statsLabel.setText("已发现 " + peers + " 台设备 · " + friends + " 位好友");
            me.repaint();
            NavItem chat = navItems.get("chat");
            if (chat != null) {
                chat.badge = node.friendRequests.size();
                chat.repaint();
            }
        }

        private class Identity extends JComponent {
            Identity() {
                setAlignmentX(LEFT_ALIGNMENT);
                setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
                setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        MainFrame.this.show("settings");
                    }
                });
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(200, 56);
            }

            @Override
            protected void paintComponent(Graphics g) {
                if (collapsed) {
                    int size = 36;
                    Icons.avatar(g, (getWidth() - size) / 2, (getHeight() - size) / 2,
                            size, initial(), node.id, true);
                    return;
                }
                Graphics2D g2 = Theme.aa(g);
                g2.setColor(new Color(0xF6F8FC));
                Theme.roundRect(g2, 0, 0, getWidth(), getHeight(), 14);
                g2.dispose();
                Icons.avatar(g, 10, 10, 36, initial(), node.id, true);
                Graphics2D g3 = Theme.aa(g);
                g3.setFont(Theme.bold(13));
                g3.setColor(Theme.TEXT);
                FontMetrics fm = g3.getFontMetrics();
                String nick = Theme.ellipsis(node.nick, fm, getWidth() - 66);
                g3.drawString(nick, 56, 24);
                g3.setFont(Theme.font(11));
                g3.setColor(Theme.MUTED);
                FontMetrics fm2 = g3.getFontMetrics();
                g3.drawString(Theme.ellipsis(node.localIp(), fm2, getWidth() - 66), 56, 40);
                g3.dispose();
            }

            private char initial() {
                String s = node.nick == null ? "?" : node.nick.trim();
                return s.isEmpty() ? '?' : Character.toUpperCase(s.charAt(0));
            }
        }
    }

    /** 侧栏导航项。 */
    class NavItem extends JComponent {
        final String key;
        final String label;
        final String icon;
        boolean active;
        boolean hover;
        int badge;

        NavItem(String key, String label, String icon) {
            this.key = key;
            this.label = label;
            this.icon = icon;
            setAlignmentX(LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            setPreferredSize(new Dimension(200, 40));
            setToolTipText(label);
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
                    MainFrame.this.show(key);
                }
            });
        }

        void setActive(boolean v) {
            active = v;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            boolean mini = sidebar != null && sidebar.collapsed;
            Graphics2D g2 = Theme.aa(g);
            int w = getWidth();
            int h = getHeight();
            if (active) {
                g2.setColor(Theme.ACCENT_SOFT);
                Theme.roundRect(g2, 0, 2, w, h - 4, 10);
            } else if (hover) {
                g2.setColor(new Color(0xF5F7FB));
                Theme.roundRect(g2, 0, 2, w, h - 4, 10);
            }
            g2.dispose();
            if (mini) {
                int ix = (w - 20) / 2;
                Icons.draw(g, icon, ix, (h - 20) / 2, 20, active ? Theme.ACCENT : new Color(0x8A94A8));
                if (badge > 0) {
                    Graphics2D gb = Theme.aa(g);
                    gb.setColor(Theme.RED);
                    gb.fillOval(w - 20, 8, 8, 8);
                    gb.dispose();
                }
                return;
            }
            Icons.draw(g, icon, 12, 12, 18, active ? Theme.ACCENT : new Color(0x8A94A8));
            Graphics2D g3 = Theme.aa(g);
            g3.setFont(active ? Theme.bold(13) : Theme.font(13));
            g3.setColor(active ? Theme.ACCENT : new Color(0x4B5567));
            g3.drawString(label, 42, 26);
            if (badge > 0) {
                String b = String.valueOf(badge);
                g3.setFont(Theme.bold(10));
                FontMetrics fm = g3.getFontMetrics();
                int bw = Math.max(18, fm.stringWidth(b) + 10);
                g3.setColor(Theme.RED);
                Theme.roundRect(g3, w - bw - 8, 12, bw, 16, 8);
                g3.setColor(Color.WHITE);
                g3.drawString(b, w - bw - 8 + (bw - fm.stringWidth(b)) / 2, 24);
            }
            g3.dispose();
        }
    }

    /** 底部浮出的提示条。 */
    class Toast extends JComponent {
        private String text = "";
        private float alpha;
        private Timer timer;
        private int tick;

        Toast() {
            setVisible(false);
        }

        void show(String msg) {
            text = msg;
            setVisible(true);
            if (timer != null) {
                timer.stop();
            }
            tick = 0;
            alpha = 1f;
            layoutToast();
            timer = new Timer(40, e -> {
                tick++;
                if (tick > 55) {
                    alpha -= 0.12f;
                    if (alpha <= 0) {
                        timer.stop();
                        setVisible(false);
                    }
                }
                repaint();
            });
            timer.start();
        }

        void layoutToast() {
            FontMetrics fm = getFontMetrics(Theme.bold(12));
            int w = Math.min(560, fm.stringWidth(text) + 44);
            java.awt.Container parent = getParent();
            int pw = parent == null ? 800 : parent.getWidth();
            int ph = parent == null ? 600 : parent.getHeight();
            setBounds((pw - w) / 2, ph - 78, w, 38);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.aa(g);
            g2.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, Math.max(0f, alpha)));
            g2.setColor(new Color(0x1F2637));
            Theme.roundRect(g2, 0, 0, getWidth(), getHeight(), 12);
            g2.setColor(Color.WHITE);
            g2.setFont(Theme.bold(12));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(Theme.ellipsis(text, fm, getWidth() - 30), 16,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    @Override
    public void setVisible(boolean b) {
        super.setVisible(b);
        if (b) {
            addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    if (toast.isVisible()) {
                        toast.layoutToast();
                    }
                }
            });
        }
    }

    public Peer peer(String id) {
        return node.peers.get(id);
    }
}
