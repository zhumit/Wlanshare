package com.wlanshare.ui;

import com.wlanshare.core.Node;

import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;

/** 协作光标标记：颜色调色板、行偏移换算、行背景绘制。 */
public final class CursorMarks {

    /** 每位协作者的颜色，顺序与 Node.colorIndex 对应。 */
    private static final Color[] PALETTE = {
            new Color(0x2B6BF3), new Color(0x12A27B), new Color(0xE08700),
            new Color(0x8B5CF6), new Color(0xD6455D), new Color(0x0E9AA7)
    };

    private CursorMarks() {
    }

    public static Color color(String pid) {
        return PALETTE[Node.colorIndex(pid)];
    }

    /** 半透明底色，用于行背景。 */
    public static Color soft(String pid) {
        Color c = color(pid);
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), 32);
    }

    /** 行号（0 基）对应的字符偏移；越界时返回文本末尾。 */
    public static int lineOffset(String text, int line) {
        int p = 0;
        for (int i = 0; i < line; i++) {
            int n = text.indexOf('\n', p);
            if (n < 0) {
                return text.length();
            }
            p = n + 1;
        }
        return p;
    }

    /** 从偏移开始到行尾（不含换行符）。 */
    public static int lineEnd(String text, int off) {
        int n = text.indexOf('\n', off);
        return n < 0 ? text.length() : n;
    }

    /** 把一段区域涂成圆角半透明色块。 */
    public static class Band implements Highlighter.HighlightPainter {
        private final Color color;

        public Band(Color color) {
            this.color = color;
        }

        @Override
        public void paint(Graphics g, int p0, int p1, Shape bounds, JTextComponent c) {
            Rectangle r;
            try {
                r = c.modelToView2D(p0).getBounds();
                Rectangle end = c.modelToView2D(Math.max(p0, p1 - 1)).getBounds();
                r = r.union(end);
            } catch (BadLocationException e) {
                return;
            }
            if (r.height <= 0) {
                return;
            }
            int w = Math.max(10, c.getVisibleRect().width - 6);
            Graphics2D g2 = Theme.aa(g);
            g2.setColor(color);
            g2.fillRoundRect(4, r.y + 1, w, r.height - 2, 8, 8);
            g2.dispose();
        }
    }
}
