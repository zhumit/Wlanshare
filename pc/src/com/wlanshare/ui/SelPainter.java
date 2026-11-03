package com.wlanshare.ui;

import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * 选区绘制接管：背景 + 文字都由程序按**视觉行**绘制，主题不再参与。
 *
 * 为什么不用系统选区：
 *  1) 某些主题会用选区背景色把选中文字盖掉（尤其从行首开始选时）；
 *  2) 系统的 Highlighter 在长行自动换行时容易画错位置。
 *
 * 为什么按视觉行而不是逻辑行：
 *  JTextArea 的 {@code getLineStartOffset/getLineEndOffset} 是按换行符划分的**逻辑行**，
 *  一行很长被自动换行时整段仍属同一逻辑行，按逻辑行绘制会把文字画到同一行上，
 *  造成「选中后错行、文字重叠」。这里用 modelToView2D 逐字符求 y，把相同 y 的字符
 *  归为同一个视觉行，再各自绘制。
 */
public final class SelPainter {

    /** 选区背景。 */
    public static final Color BG = new Color(0xCFE2FF);
    /** 选区文字。 */
    public static final Color FG = new Color(0x13294B);

    /** 一个视觉行片段。 */
    private static final class Seg {
        int x;
        int y;
        int w;
        int h;
        String text;
    }

    private SelPainter() {
    }

    /** 交出选区绘制权：主题不再画选区背景与选中文字。 */
    public static void takeOver(JTextComponent c) {
        c.setSelectionColor(new Color(0, 0, 0, 0));
        c.setSelectedTextColor(null);
    }

    /** 在文字下方绘制选区（背景 + 深色文字）。 */
    public static void paint(JTextArea c, Graphics2D g2) {
        int s = c.getSelectionStart();
        int e = c.getSelectionEnd();
        if (s == e || s < 0 || e < 0) {
            return;
        }
        if (s > e) {
            int t = s;
            s = e;
            e = t;
        }
        // 每次重算：文本会随输入/远端同步变化，缓存坐标很容易错位造成文字重叠
        List<Seg> segs = compute(c, s, e);
        g2.setFont(c.getFont());
        FontMetrics fm = g2.getFontMetrics();
        for (Seg seg : segs) {
            g2.setColor(BG);
            g2.fillRect(seg.x, seg.y, seg.w, seg.h);
            g2.setColor(FG);
            g2.drawString(seg.text, seg.x, seg.y + fm.getAscent());
        }
    }

    /** 把选区切成视觉行片段。 */
    private static List<Seg> compute(JTextArea c, int s, int e) {
        List<Seg> out = new ArrayList<Seg>();
        String text = c.getText();
        Insets ins = c.getInsets();
        int i = s;
        while (i < e && i < text.length()) {
            // 换行符不属于任何可见字符，直接跳过，避免被画进选区文字
            if (text.charAt(i) == '\n') {
                i++;
                continue;
            }
            Rectangle2D r0;
            try {
                r0 = c.modelToView2D(i);
            } catch (BadLocationException ex) {
                i++;
                continue;
            }
            if (r0 == null) {
                i++;
                continue;
            }
            int y = (int) r0.getY();
            Rectangle2D rLast = r0;
            int j = i + 1;
            while (j < e && j < text.length() && text.charAt(j) != '\n') {
                Rectangle2D rj;
                try {
                    rj = c.modelToView2D(j);
                } catch (BadLocationException ex) {
                    break;
                }
                if (rj == null || (int) rj.getY() != y) {
                    break;
                }
                rLast = rj;
                j++;
            }
            int vx = r0.getWidth() > 1 ? (int) r0.getX() : 0;
            int right = (int) (rLast.getX() + rLast.getWidth());
            Seg seg = new Seg();
            seg.x = vx + ins.left;
            seg.y = (int) rLast.getY() + ins.top;
            seg.h = Math.max(4, (int) rLast.getHeight());
            seg.w = Math.max(3, right - vx);
            seg.text = text.substring(i, j); // 不含换行符
            out.add(seg);
            i = j;
        }
        return out;
    }
}
