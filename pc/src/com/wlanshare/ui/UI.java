package com.wlanshare.ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** 通用小部件与布局助手。 */
public final class UI {

    private UI() {
    }

    public static JPanel transparent() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        return p;
    }

    public static JPanel row(Component... cs) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        p.setOpaque(false);
        for (Component c : cs) {
            p.add(c);
        }
        return p;
    }

    public static JPanel rowRight(Component... cs) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        p.setOpaque(false);
        for (Component c : cs) {
            p.add(c);
        }
        return p;
    }

    public static JPanel col(Component... cs) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        for (Component c : cs) {
            p.add(c);
        }
        return p;
    }

    public static Component vgap(int h) {
        return Box.createRigidArea(new Dimension(1, h));
    }

    public static Component hgap(int w) {
        return Box.createRigidArea(new Dimension(w, 1));
    }

    /** 统一的面板标题区：大标题 + 说明 + 右侧操作。 */
    public static JPanel header(String title, String sub, JComponent right) {
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));
        JPanel left = col();
        JLabel t = Theme.label(title, 21, Theme.TEXT, true);
        left.add(t);
        if (sub != null) {
            left.add(vgap(4));
            JLabel s = Theme.label(sub, 12, Theme.SUB, false);
            left.add(s);
        }
        p.add(left, BorderLayout.WEST);
        if (right != null) {
            JPanel wrap = new JPanel(new BorderLayout());
            wrap.setOpaque(false);
            wrap.add(right, BorderLayout.EAST);
            p.add(wrap, BorderLayout.EAST);
        }
        return p;
    }

    /** 空状态提示。 */
    public static JPanel empty(String icon, String title, String sub) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JComponent art = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.aa(g);
                g2.setColor(new Color(0xEDF1F9));
                g2.fillOval(0, 0, 88, 88);
                g2.dispose();
                Icons.draw(g, icon, 22, 22, 44, new Color(0x93A0B8));
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(88, 88);
            }
        };
        art.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(art);
        p.add(vgap(16));
        JLabel t = Theme.label(title, 14, Theme.TEXT, true);
        t.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.add(t);
        if (sub != null) {
            p.add(vgap(6));
            JLabel s = Theme.label(sub, 12, Theme.MUTED, false);
            s.setAlignmentX(Component.CENTER_ALIGNMENT);
            p.add(s);
        }
        return p;
    }

    /** 圆角输入框（内含无边框 JTextField）。 */
    public static class RoundField extends JPanel {
        public final JTextField field;
        private boolean focused;
        private final String hint;

        public RoundField(String placeholder, int columns) {
            super(new BorderLayout());
            setOpaque(false);
            this.hint = placeholder;
            field = new JTextField(columns) {
                {
                    Theme.styleText(this);
                }

                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    if (getText().isEmpty() && hint != null && !hint.isEmpty()) {
                        Graphics2D g2 = Theme.aa(g);
                        g2.setFont(getFont());
                        g2.setColor(Theme.MUTED);
                        FontMetrics fm = g2.getFontMetrics();
                        g2.drawString(hint, 1, getHeight() / 2 + fm.getAscent() / 2 - 2);
                        g2.dispose();
                    }
                }
            };
            field.setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 12));
            field.setOpaque(false);
            field.setFont(Theme.font(13));
            field.setForeground(Theme.TEXT);
            field.setCaretColor(Theme.ACCENT);
            field.setToolTipText(placeholder);
            field.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    focused = true;
                    repaint();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    focused = false;
                    repaint();
                }
            });
            add(field, BorderLayout.CENTER);
        }

        public String text() {
            return field.getText();
        }

        public void setText(String s) {
            field.setText(s);
            try {
                field.setCaretPosition(0);
            } catch (Exception ignored) {
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.aa(g);
            g2.setColor(focused ? Color.WHITE : new Color(0xF7F9FC));
            Theme.roundRect(g2, 0, 0, getWidth(), getHeight(), 12);
            g2.setColor(focused ? new Color(0x9FBCF5) : Theme.BORDER);
            g2.setStroke(new BasicStroke(1f));
            Theme.border(g2, 0, 0, getWidth(), getHeight(), 10);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** 开关。 */
    public static class Toggle extends JComponent {
        private boolean on;
        private float pos;

        public Toggle(boolean initial) {
            this.on = initial;
            this.pos = initial ? 1f : 0f;
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(44, 24));
            setMaximumSize(new Dimension(44, 24));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    on = !on;
                    animate();
                    if (listener != null) {
                        listener.accept(on);
                    }
                }
            });
        }

        private java.util.function.Consumer<Boolean> listener;

        public Toggle onChange(java.util.function.Consumer<Boolean> l) {
            this.listener = l;
            return this;
        }

        public boolean isOn() {
            return on;
        }

        public void setOn(boolean v) {
            if (on == v) {
                return;
            }
            on = v;
            pos = v ? 1f : 0f;
            repaint();
        }

        private void animate() {
            final javax.swing.Timer t = new javax.swing.Timer(12, null);
            final long start = System.currentTimeMillis();
            final float from = pos;
            final float to = on ? 1f : 0f;
            t.addActionListener(e -> {
                float p = Math.min(1f, (System.currentTimeMillis() - start) / 110f);
                pos = from + (to - from) * p;
                repaint();
                if (p >= 1f) {
                    t.stop();
                }
            });
            t.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.aa(g);
            int w = getWidth();
            int h = getHeight();
            Color off = new Color(0xD6DCE8);
            Color c = blend(off, Theme.ACCENT, pos);
            g2.setColor(c);
            Theme.roundRect(g2, 0, 1, w, h - 2, h - 2);
            int d = h - 6;
            int x = (int) (3 + pos * (w - d - 6));
            g2.setColor(new Color(0, 0, 0, 20));
            g2.fillOval(x, 5, d, d);
            g2.setColor(Color.WHITE);
            g2.fillOval(x, 4, d, d);
            g2.dispose();
        }

        private static Color blend(Color a, Color b, float t) {
            return new Color(
                    (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                    (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                    (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
        }
    }

    /** 无背景文本按钮（列表行内的操作）。 */
    public static class Link extends JLabel {
        public Link(String text, Color color) {
            super(text);
            setFont(Theme.bold(12));
            setForeground(color);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
        }
    }

    /** 细进度条。 */
    public static class Bar extends JComponent {
        private float v;
        private Color color = Theme.ACCENT;

        public Bar() {
            setPreferredSize(new Dimension(10, 6));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
        }

        public void set(float value, Color c) {
            this.v = Math.max(0f, Math.min(1f, value));
            if (c != null) {
                this.color = c;
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.aa(g);
            int h = 6;
            g2.setColor(new Color(0xEDF1F8));
            Theme.roundRect(g2, 0, 0, getWidth(), h, h);
            int w = (int) (getWidth() * v);
            if (w > 0) {
                g2.setColor(color);
                Theme.roundRect(g2, 0, 0, Math.max(h, w), h, h);
            }
            g2.dispose();
        }
    }

    /** 与应用风格一致的输入对话框；取消返回 null。 */
    public static String prompt(java.awt.Component parent, String title, String desc, String initial) {
        final String[] out = new String[]{null};
        java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(parent);
        final javax.swing.JDialog dlg = new javax.swing.JDialog(owner,
                java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setUndecorated(true);

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(Color.WHITE);
        root.setBorder(BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createLineBorder(Theme.BORDER),
                BorderFactory.createEmptyBorder(18, 20, 16, 20)));

        JPanel head = col();
        head.add(Theme.label(title, 14, Theme.TEXT, true));
        if (desc != null && !desc.isEmpty()) {
            head.add(vgap(5));
            head.add(Theme.label(desc, 11, Theme.MUTED, false));
        }
        root.add(head, BorderLayout.NORTH);

        final RoundField field = new RoundField(title, 18);
        field.setText(initial == null ? "" : initial);
        field.setPreferredSize(new Dimension(340, 38));
        JPanel mid = new JPanel(new BorderLayout());
        mid.setOpaque(false);
        mid.add(field, BorderLayout.CENTER);
        root.add(mid, BorderLayout.CENTER);

        JPanel btns = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        Theme.Btn cancel = new Theme.Btn("取消", Theme.Kind.GHOST).small();
        cancel.addActionListener(e -> dlg.dispose());
        final Theme.Btn ok = new Theme.Btn("确定", Theme.Kind.PRIMARY).small();
        ok.addActionListener(e -> {
            out[0] = field.text().trim();
            dlg.dispose();
        });
        field.field.addActionListener(e -> ok.doClick());
        btns.add(cancel);
        btns.add(ok);
        root.add(btns, BorderLayout.SOUTH);

        dlg.setContentPane(root);
        dlg.pack();
        dlg.setLocationRelativeTo(parent);
        javax.swing.SwingUtilities.invokeLater(() -> {
            field.field.requestFocusInWindow();
            field.field.selectAll();
        });
        dlg.setVisible(true);
        return out[0];
    }

    /** 横向可滚动/可缩放文本自动截断的标签。 */
    public static String fit(String text, FontMetrics fm, int width) {
        return Theme.ellipsis(text, fm, width);
    }

    /** 在系统文件管理器里定位文件（Windows 选中该文件，macOS 在 Finder 中显示）。 */
    public static boolean revealInFolder(java.io.File f) {
        if (f == null) {
            return false;
        }
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            java.io.File target = f.exists() ? f : f.getAbsoluteFile();
            if (os.contains("win")) {
                if (f.exists()) {
                    new ProcessBuilder("explorer.exe", "/select," + target.getAbsolutePath()).start();
                } else {
                    java.io.File parent = target.getParentFile();
                    if (parent == null || !parent.exists()) {
                        return false;
                    }
                    new ProcessBuilder("explorer.exe", parent.getAbsolutePath()).start();
                }
                return true;
            }
            if (os.contains("mac")) {
                new ProcessBuilder("open", "-R", target.getAbsolutePath()).start();
                return true;
            }
        } catch (Exception ignored) {
        }
        return openFolder(f);
    }

    /** 打开所在文件夹（目录本身则直接打开）。 */
    public static boolean openFolder(java.io.File f) {
        if (f == null) {
            return false;
        }
        try {
            java.io.File dir = f.isDirectory() ? f : f.getParentFile();
            if (dir == null || !dir.exists()) {
                return false;
            }
            java.awt.Desktop.getDesktop().open(dir);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** 与应用风格一致的确认框；危险操作用红色主按钮。 */
    public static boolean confirm(java.awt.Component parent, String title, String message, String okText) {
        final boolean[] out = new boolean[]{false};
        java.awt.Window owner = javax.swing.SwingUtilities.getWindowAncestor(parent);
        final javax.swing.JDialog dlg = new javax.swing.JDialog(owner,
                java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setUndecorated(true);

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(Color.WHITE);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                BorderFactory.createEmptyBorder(20, 22, 16, 22)));

        JPanel head = col();
        head.add(Theme.label(title, 14, Theme.TEXT, true));
        if (message != null && !message.isEmpty()) {
            head.add(vgap(6));
            javax.swing.JLabel msg = Theme.label("", 11, Theme.MUTED, false);
            msg.setText("<html><body style='width:300px'>"
                    + message.replace("&", "&amp;").replace("<", "&lt;") + "</body></html>");
            head.add(msg);
        }
        root.add(head, BorderLayout.CENTER);

        JPanel btns = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        Theme.Btn cancel = new Theme.Btn("取消", Theme.Kind.GHOST).small();
        cancel.addActionListener(e -> dlg.dispose());
        Theme.Btn ok = new Theme.Btn(okText == null ? "确定" : okText, Theme.Kind.DANGER).small();
        ok.addActionListener(e -> {
            out[0] = true;
            dlg.dispose();
        });
        btns.add(cancel);
        btns.add(ok);
        root.add(btns, BorderLayout.SOUTH);

        dlg.setContentPane(root);
        dlg.pack();
        dlg.setLocationRelativeTo(parent);
        javax.swing.SwingUtilities.invokeLater(ok::requestFocusInWindow);
        dlg.setVisible(true);
        return out[0];
    }
}
