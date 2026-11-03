package com.wlanshare.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** 全局视觉规范：浅色、卡片、圆角、克制的蓝色主色。 */
public final class Theme {

    public static final Color BG = new Color(0xF4F6FB);
    public static final Color CARD = Color.WHITE;
    public static final Color BORDER = new Color(0xE5E9F2);
    public static final Color BORDER2 = new Color(0xEFF2F8);
    public static final Color TEXT = new Color(0x151C2C);
    public static final Color SUB = new Color(0x6B7488);
    public static final Color MUTED = new Color(0x9AA3B5);
    public static final Color ACCENT = new Color(0x2B6BF3);
    public static final Color ACCENT_SOFT = new Color(0xEAF0FF);
    public static final Color GREEN = new Color(0x1FBF6B);
    public static final Color RED = new Color(0xEE4B5E);
    public static final Color AMBER = new Color(0xF2A33C);
    public static final Color PURPLE = new Color(0x7A5CF0);
    public static final Color SIDEBAR = new Color(0xFFFFFF);

    public static final Color[] AVATARS = {
            new Color(0x4C7DF0), new Color(0x7A5CF0), new Color(0x14B8A6),
            new Color(0xF2994A), new Color(0xEB5757), new Color(0x2D9CDB)
    };

    private static String family = null;

    private Theme() {
    }

    public static void install() {
        String[] candidates = {"Microsoft YaHei UI", "Microsoft YaHei", "PingFang SC", "Source Han Sans SC", "SansSerif"};
        String[] available = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        for (String c : candidates) {
            for (String a : available) {
                if (a.equalsIgnoreCase(c)) {
                    family = a;
                    break;
                }
            }
            if (family != null) {
                break;
            }
        }
        if (family == null) {
            family = "SansSerif";
        }
        Font base = new Font(family, Font.PLAIN, 13);
        UIManager.put("Label.font", base);
        UIManager.put("Button.font", base);
        UIManager.put("TextField.font", base);
        UIManager.put("TextArea.font", base);
        UIManager.put("TextPane.font", base);
        UIManager.put("ComboBox.font", base);
        UIManager.put("CheckBox.font", base);
        UIManager.put("ToolTip.font", new Font(family, Font.PLAIN, 12));
        UIManager.put("Panel.background", BG);
        UIManager.put("OptionPane.background", BG);
        UIManager.put("ToolTip.background", new Color(0x1F2937));
        UIManager.put("ToolTip.foreground", Color.WHITE);
        UIManager.put("ToolTip.border", BorderFactory.createEmptyBorder(6, 10, 6, 10));
    }

    public static String family() {
        return family == null ? "SansSerif" : family;
    }

    public static Font font(int size) {
        return new Font(family(), Font.PLAIN, size);
    }

    public static Font bold(int size) {
        return new Font(family(), Font.BOLD, size);
    }

    /** 与具体组件无关的字体度量（组件尚未显示时也准确）。 */
    public static FontMetrics fm(Font f) {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(1, 1,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setFont(f);
        FontMetrics m = g.getFontMetrics();
        g.dispose();
        return m;
    }

    /**
     * 统一文本选中样式：淡蓝底 + 深色字。
     * 系统主题可能给出「白字浅底」的选中配色，选中后几乎看不清文字。
     */
    public static void styleText(javax.swing.text.JTextComponent c) {
        c.setSelectionColor(new Color(0xCFE2FF));
        c.setSelectedTextColor(new Color(0x13294B));
    }

    public static Graphics2D aa(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    public static void roundRect(Graphics2D g, int x, int y, int w, int h, int r) {
        g.fillRoundRect(x, y, w, h, r, r);
    }

    public static void border(Graphics2D g, int x, int y, int w, int h, int r) {
        g.drawRoundRect(x, y, w - 1, h - 1, r, r);
    }

    /** 卡片：白底 + 圆角 + 细边 */
    public static class Card extends JPanel {
        private int radius = 14;
        private Color fill = CARD;
        private boolean stroked = true;

        public Card() {
            setOpaque(false);
            setLayout(null);
        }

        public Card radius(int r) {
            this.radius = r;
            return this;
        }

        public Card fill(Color c) {
            this.fill = c;
            return this;
        }

        public Card noStroke() {
            this.stroked = false;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            g2.setColor(fill);
            roundRect(g2, 0, 0, getWidth(), getHeight(), radius);
            if (stroked) {
                g2.setColor(BORDER);
                g2.setStroke(new BasicStroke(1f));
                border(g2, 0, 0, getWidth(), getHeight(), radius);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public enum Kind {PRIMARY, GHOST, SOFT, DANGER}

    /** 扁平按钮，自带 hover / 圆角。 */
    public static class Btn extends JButton {
        private final Kind kind;
        private int radius = 10;
        private boolean hover;
        private Color bgOverride;
        private Color fgOverride;

        public Btn(String text, Kind kind) {
            super(text);
            this.kind = kind;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setFont(bold(13));
            setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
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
            });
        }

        public Btn small() {
            radius = 8;
            setFont(bold(12));
            setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
            return this;
        }

        /** 只显示一个矢量图标（不显示文字）。 */
        public Btn iconOnly(String iconName, int size, Color color) {
            setText("");
            setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
            setVerticalTextPosition(javax.swing.SwingConstants.CENTER);
            setIconTextGap(5);
            setIcon(vectorIcon(iconName, size, color));
            return this;
        }

        /** 运行中切换图标（保留文字）。 */
        public void setIconName(String iconName, int size, Color color) {
            setIcon(vectorIcon(iconName, size, color));
        }

        /** 覆盖底色与前景色，传 null 恢复按 kind 取色。 */
        public Btn palette(Color bg, Color fg) {
            this.bgOverride = bg;
            this.fgOverride = fg;
            repaint();
            return this;
        }

        private static javax.swing.ImageIcon vectorIcon(String iconName, int size, Color color) {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                    size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            Icons.draw(g, iconName, 0, 0, size, color);
            g.dispose();
            return new javax.swing.ImageIcon(img);
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return d;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            int w = getWidth();
            int h = getHeight();
            Color bg;
            Color fg;
            if (bgOverride != null) {
                bg = bgOverride;
                fg = fgOverride == null ? Color.WHITE : fgOverride;
            } else {
                switch (kind) {
                case PRIMARY:
                    bg = hover ? new Color(0x1F5BE0) : ACCENT;
                    fg = Color.WHITE;
                    break;
                case SOFT:
                    bg = hover ? new Color(0xDBE6FF) : ACCENT_SOFT;
                    fg = ACCENT;
                    break;
                case DANGER:
                    bg = hover ? new Color(0xD93A4C) : RED;
                    fg = Color.WHITE;
                    break;
                default:
                    bg = hover ? new Color(0xF0F3FA) : Color.WHITE;
                    fg = isEnabled() ? TEXT : MUTED;
                    break;
                }
            }
            g2.setColor(bg);
            roundRect(g2, 0, 0, w, h, radius);
            if (kind == Kind.GHOST) {
                g2.setColor(BORDER);
                border(g2, 0, 0, w, h, radius);
            }
            g2.dispose();
            setForeground(fg);
            super.paintComponent(g);
        }
    }

    /** 纯图标按钮（折叠 / 展开 / 备注等）。 */
    public static class IconBtn extends JComponent {
        private final String icon;
        private final String tip;
        private final Runnable action;
        private boolean hover;
        private final int size;

        public IconBtn(String icon, String tip, Runnable action) {
            this(icon, tip, action, 26);
        }

        public IconBtn(String icon, String tip, Runnable action, int size) {
            this.icon = icon;
            this.tip = tip;
            this.action = action;
            this.size = size;
            setToolTipText(tip);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
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
                    if (IconBtn.this.action != null) {
                        IconBtn.this.action.run();
                    }
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(size, size);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(size, size);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            if (hover) {
                g2.setColor(new Color(0xEFF3FB));
                roundRect(g2, 0, 0, getWidth(), getHeight(), 8);
            }
            g2.dispose();
            int pad = Math.max(4, (getWidth() - 16) / 2);
            Icons.draw(g, icon, pad, pad, 16, hover ? ACCENT : SUB);
        }
    }

    /** 分段控制器（编辑 / 分栏 / 预览）。 */
    public static class Segmented extends JComponent {
        private final String[] items;
        private int selected;
        private java.util.function.IntConsumer listener;
        private int hover = -1;
        private final int pad = 3;

        public Segmented(String... items) {
            this.items = items;
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int seg = segmentAt(e.getX());
                    if (seg != hover) {
                        hover = seg;
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = -1;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    int seg = segmentAt(e.getX());
                    if (seg >= 0 && seg != selected) {
                        selected = seg;
                        repaint();
                        if (listener != null) {
                            listener.accept(seg);
                        }
                    }
                }
            });
        }

        public Segmented onChange(java.util.function.IntConsumer l) {
            this.listener = l;
            return this;
        }

        public void setSelected(int i) {
            if (i >= 0 && i < items.length && i != selected) {
                selected = i;
                repaint();
            }
        }

        public int selected() {
            return selected;
        }

        private int segmentAt(int x) {
            if (x < pad) {
                return -1;
            }
            int w = getWidth() - pad * 2;
            int each = w / items.length;
            int idx = (x - pad) / Math.max(1, each);
            return idx >= 0 && idx < items.length ? idx : -1;
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = Theme.fm(bold(11));
            int w = pad * 2;
            for (String s : items) {
                w += fm.stringWidth(s) + 22;
            }
            return new Dimension(w, 30);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = aa(g);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(new Color(0xF0F3FA));
            roundRect(g2, 0, 0, w, h, 9);

            int each = (w - pad * 2) / items.length;
            for (int i = 0; i < items.length; i++) {
                int x = pad + i * each;
                int bw = (i == items.length - 1) ? (w - pad - x) : each;
                if (i == selected) {
                    g2.setColor(Color.WHITE);
                    roundRect(g2, x, pad, bw, h - pad * 2, 7);
                    g2.setColor(BORDER);
                    border(g2, x, pad, bw, h - pad * 2, 7);
                } else if (i == hover) {
                    g2.setColor(new Color(0xE4E9F5));
                    roundRect(g2, x, pad, bw, h - pad * 2, 7);
                }
                g2.setFont(i == selected ? bold(11) : font(11));
                g2.setColor(i == selected ? ACCENT : SUB);
                FontMetrics fm = g2.getFontMetrics();
                String t = items[i];
                g2.drawString(t, x + (bw - fm.stringWidth(t)) / 2,
                        pad + (h - pad * 2 + fm.getAscent() - fm.getDescent()) / 2);
            }
            g2.dispose();
        }
    }

    /** 细滚动条 */
    public static class SlimScroll extends JScrollPane {
        public SlimScroll(JComponent view) {
            super(view);
            setBorder(BorderFactory.createEmptyBorder());
            setOpaque(false);
            getViewport().setOpaque(false);
            setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
            JScrollBar bar = getVerticalScrollBar();
            bar.setPreferredSize(new Dimension(10, 10));
            bar.setUnitIncrement(24);
            bar.setOpaque(false);
            bar.setUI(new BasicScrollBarUI() {
                @Override
                protected void configureScrollBarColors() {
                    thumbColor = new Color(0xD3DAE8);
                    trackColor = new Color(0, 0, 0, 0);
                }

                @Override
                protected JButton createDecreaseButton(int o) {
                    return zero();
                }

                @Override
                protected JButton createIncreaseButton(int o) {
                    return zero();
                }

                private JButton zero() {
                    JButton b = new JButton();
                    b.setPreferredSize(new Dimension(0, 0));
                    b.setMinimumSize(new Dimension(0, 0));
                    b.setMaximumSize(new Dimension(0, 0));
                    return b;
                }

                @Override
                protected void paintThumb(Graphics g, JComponent c, java.awt.Rectangle r) {
                    Graphics2D g2 = aa(g);
                    g2.setColor(new Color(0xC9D2E3));
                    int w = 6;
                    g2.fillRoundRect(r.x + (r.width - w) / 2, r.y + 2, w, r.height - 4, w, w);
                    g2.dispose();
                }

                @Override
                protected void paintTrack(Graphics g, JComponent c, java.awt.Rectangle r) {
                }
            });
        }
    }

    public static JLabel label(String text, int size, Color color, boolean bold) {
        JLabel l = new JLabel(text);
        l.setFont(bold ? bold(size) : font(size));
        l.setForeground(color);
        return l;
    }

    /** 单行省略文本 */
    public static String ellipsis(String s, FontMetrics fm, int width) {
        if (s == null) {
            return "";
        }
        if (fm.stringWidth(s) <= width) {
            return s;
        }
        String dots = "…";
        int lo = 0;
        int hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (fm.stringWidth(s.substring(0, mid) + dots) <= width) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return s.substring(0, lo) + dots;
    }

    /** 行数估算（对话气泡用） */
    public static int wrapLines(String text, FontMetrics fm, int width) {
        if (text == null || text.isEmpty()) {
            return 1;
        }
        int lines = 0;
        for (String para : text.split("\n", -1)) {
            if (para.isEmpty()) {
                lines++;
                continue;
            }
            int cur = 0;
            int n = 1;
            for (int i = 0; i < para.length(); ) {
                int cp = para.codePointAt(i);
                String ch = new String(Character.toChars(cp));
                int w = fm.stringWidth(ch);
                if (cur + w > width) {
                    n++;
                    cur = w;
                } else {
                    cur += w;
                }
                i += Character.charCount(cp);
            }
            lines += n;
        }
        return Math.max(1, lines);
    }

    public static String timeText(long ts) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(ts);
        java.util.Calendar now = java.util.Calendar.getInstance();
        boolean today = c.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR)
                && c.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR);
        String hm = String.format("%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE));
        if (today) {
            return hm;
        }
        return (c.get(java.util.Calendar.MONTH) + 1) + "月" + c.get(java.util.Calendar.DAY_OF_MONTH) + "日 " + hm;
    }
}
