package com.wlanshare.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/** 手绘矢量图标 + 头像，避免任何图片资源依赖。 */
public final class Icons {

    private Icons() {
    }

    private static BasicStroke st(float w) {
        return new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }

    /** 在 (x,y)-size 区域内绘制图标。 */
    public static void draw(Graphics g, String name, int x, int y, int size, Color c) {
        Graphics2D g2 = Theme.aa(g);
        g2.translate(x, y);
        double s = size / 24.0;
        g2.scale(s, s);
        g2.setColor(c);
        g2.setStroke(st(1.9f));
        Path2D p;
        switch (name) {
            case "devices":
                g2.draw(new RoundRectangle2D.Double(3, 4.5, 18, 12, 3, 3));
                g2.draw(new java.awt.geom.Line2D.Double(9, 20, 15, 20));
                g2.draw(new java.awt.geom.Line2D.Double(12, 16.5, 12, 20));
                break;
            case "monitor":
                g2.draw(new RoundRectangle2D.Double(2.5, 4, 19, 12.5, 2.5, 2.5));
                g2.draw(new java.awt.geom.Line2D.Double(8, 20, 16, 20));
                g2.draw(new java.awt.geom.Line2D.Double(12, 16.5, 12, 20));
                break;
            case "phone":
                g2.draw(new RoundRectangle2D.Double(7, 2.5, 10, 19, 2.6, 2.6));
                g2.draw(new java.awt.geom.Line2D.Double(10.5, 18.6, 13.5, 18.6));
                break;
            case "chat":
                p = new Path2D.Double();
                p.moveTo(4, 5.2);
                p.curveTo(4, 4, 5, 3.2, 6.2, 3.2);
                p.lineTo(17.8, 3.2);
                p.curveTo(19, 3.2, 20, 4, 20, 5.2);
                p.lineTo(20, 14.2);
                p.curveTo(20, 15.4, 19, 16.2, 17.8, 16.2);
                p.lineTo(10, 16.2);
                p.lineTo(6, 20);
                p.lineTo(6, 16.2);
                p.curveTo(4.8, 16.2, 4, 15.4, 4, 14.2);
                p.closePath();
                g2.draw(p);
                break;
            case "files":
                g2.draw(new RoundRectangle2D.Double(4, 3, 11, 15, 2, 2));
                g2.draw(new java.awt.geom.Line2D.Double(7, 7, 12, 7));
                g2.draw(new java.awt.geom.Line2D.Double(7, 10.5, 12, 10.5));
                g2.draw(new RoundRectangle2D.Double(9.5, 7.5, 10, 13.5, 2, 2));
                break;
            case "folder":
                p = new Path2D.Double();
                p.moveTo(3, 7);
                p.curveTo(3, 6, 3.8, 5.2, 4.8, 5.2);
                p.lineTo(9.2, 5.2);
                p.lineTo(11, 7.2);
                p.lineTo(19.2, 7.2);
                p.curveTo(20.2, 7.2, 21, 8, 21, 9);
                p.lineTo(21, 17.5);
                p.curveTo(21, 18.5, 20.2, 19.3, 19.2, 19.3);
                p.lineTo(4.8, 19.3);
                p.curveTo(3.8, 19.3, 3, 18.5, 3, 17.5);
                p.closePath();
                g2.draw(p);
                break;
            case "doc":
                p = new Path2D.Double();
                p.moveTo(6, 2.8);
                p.lineTo(14, 2.8);
                p.lineTo(19, 7.8);
                p.lineTo(19, 21.2);
                p.lineTo(6, 21.2);
                p.closePath();
                g2.draw(p);
                g2.draw(new java.awt.geom.Line2D.Double(13.6, 2.8, 13.6, 8.2));
                g2.draw(new java.awt.geom.Line2D.Double(13.6, 8.2, 19, 8.2));
                g2.draw(new java.awt.geom.Line2D.Double(9, 12.5, 16, 12.5));
                g2.draw(new java.awt.geom.Line2D.Double(9, 16, 16, 16));
                break;
            case "settings":
                g2.draw(new Ellipse2D.Double(9, 9, 6, 6));
                g2.draw(new Ellipse2D.Double(4.2, 4.2, 15.6, 15.6));
                g2.draw(new java.awt.geom.Line2D.Double(12, 1.6, 12, 4.4));
                g2.draw(new java.awt.geom.Line2D.Double(12, 19.6, 12, 22.4));
                g2.draw(new java.awt.geom.Line2D.Double(1.6, 12, 4.4, 12));
                g2.draw(new java.awt.geom.Line2D.Double(19.6, 12, 22.4, 12));
                break;
            case "send":
                p = new Path2D.Double();
                p.moveTo(3.4, 11.2);
                p.lineTo(20.4, 3.6);
                p.lineTo(13.4, 20.6);
                p.lineTo(11.2, 13.4);
                p.closePath();
                g2.draw(p);
                break;
            case "plus":
                g2.draw(new java.awt.geom.Line2D.Double(12, 5, 12, 19));
                g2.draw(new java.awt.geom.Line2D.Double(5, 12, 19, 12));
                break;
            case "close":
                g2.draw(new java.awt.geom.Line2D.Double(6.5, 6.5, 17.5, 17.5));
                g2.draw(new java.awt.geom.Line2D.Double(17.5, 6.5, 6.5, 17.5));
                break;
            case "check":
                p = new Path2D.Double();
                p.moveTo(5, 12.6);
                p.lineTo(10, 17.4);
                p.lineTo(19, 6.6);
                g2.draw(p);
                break;
            case "search":
                g2.draw(new Ellipse2D.Double(4, 4, 12, 12));
                g2.draw(new java.awt.geom.Line2D.Double(15.6, 15.6, 20.2, 20.2));
                break;
            case "wifi":
                p = new Path2D.Double();
                p.moveTo(3.2, 9.4);
                p.curveTo(8.2, 4.6, 15.8, 4.6, 20.8, 9.4);
                g2.draw(p);
                p = new Path2D.Double();
                p.moveTo(6.6, 13);
                p.curveTo(9.8, 10, 14.2, 10, 17.4, 13);
                g2.draw(p);
                p = new Path2D.Double();
                p.moveTo(10, 16.6);
                p.curveTo(11.2, 15.6, 12.8, 15.6, 14, 16.6);
                g2.draw(p);
                g2.fill(new Ellipse2D.Double(10.9, 18.6, 2.2, 2.2));
                break;
            case "upload":
                g2.draw(new java.awt.geom.Line2D.Double(12, 16.5, 12, 4));
                p = new Path2D.Double();
                p.moveTo(7, 8.6);
                p.lineTo(12, 3.6);
                p.lineTo(17, 8.6);
                g2.draw(p);
                g2.draw(new java.awt.geom.Line2D.Double(4.5, 20, 19.5, 20));
                break;
            case "download":
                g2.draw(new java.awt.geom.Line2D.Double(12, 3.6, 12, 16));
                p = new Path2D.Double();
                p.moveTo(7, 11.4);
                p.lineTo(12, 16.4);
                p.lineTo(17, 11.4);
                g2.draw(p);
                g2.draw(new java.awt.geom.Line2D.Double(4.5, 20, 19.5, 20));
                break;
            case "edit":
                p = new Path2D.Double();
                p.moveTo(4, 20);
                p.lineTo(4.8, 15.6);
                p.lineTo(15.6, 4.8);
                p.lineTo(19.2, 8.4);
                p.lineTo(8.4, 19.2);
                p.closePath();
                g2.draw(p);
                g2.draw(new java.awt.geom.Line2D.Double(13.4, 7, 17, 10.6));
                break;
            case "trash":
                g2.draw(new java.awt.geom.Line2D.Double(4.5, 6.5, 19.5, 6.5));
                g2.draw(new RoundRectangle2D.Double(4.5, 6.5, 15, 14, 2, 2));
                g2.draw(new java.awt.geom.Line2D.Double(9, 6.5, 9, 4));
                g2.draw(new java.awt.geom.Line2D.Double(9, 4, 15, 4));
                g2.draw(new java.awt.geom.Line2D.Double(15, 4, 15, 6.5));
                break;
            case "users":
                g2.draw(new Ellipse2D.Double(4, 4.6, 7.4, 7.4));
                g2.draw(new Ellipse2D.Double(13, 6.2, 6, 6));
                p = new Path2D.Double();
                p.moveTo(2.6, 19.4);
                p.curveTo(2.6, 15.6, 5.4, 13.8, 7.7, 13.8);
                p.curveTo(10, 13.8, 12.8, 15.6, 12.8, 19.4);
                g2.draw(p);
                p = new Path2D.Double();
                p.moveTo(14.4, 19.4);
                p.curveTo(14.4, 16.4, 16.2, 14.8, 18, 14.8);
                p.curveTo(19.8, 14.8, 21.4, 16.4, 21.4, 19.4);
                g2.draw(p);
                break;
            case "refresh":
                p = new Path2D.Double();
                p.append(new java.awt.geom.Arc2D.Double(3.6, 3.6, 16.8, 16.8, 60, 260, java.awt.geom.Arc2D.OPEN), false);
                g2.draw(p);
                p = new Path2D.Double();
                p.moveTo(19.4, 2.6);
                p.lineTo(19.4, 8);
                p.lineTo(14.4, 8);
                g2.draw(p);
                break;
            case "link":
                g2.draw(new java.awt.geom.Line2D.Double(9.6, 14.4, 14.4, 9.6));
                p = new Path2D.Double();
                p.moveTo(12.4, 6.4);
                p.lineTo(14.2, 4.6);
                p.curveTo(16, 2.8, 19, 2.8, 20.8, 4.6);
                p.curveTo(22.6, 6.4, 22.6, 9.4, 20.8, 11.2);
                p.lineTo(19, 13);
                g2.draw(p);
                p = new Path2D.Double();
                p.moveTo(11.6, 17.6);
                p.lineTo(9.8, 19.4);
                p.curveTo(8, 21.2, 5, 21.2, 3.2, 19.4);
                p.curveTo(1.4, 17.6, 1.4, 14.6, 3.2, 12.8);
                p.lineTo(5, 11);
                g2.draw(p);
                break;
            case "chevron-left":
                p = new Path2D.Double();
                p.moveTo(15, 5.5);
                p.lineTo(8.5, 12);
                p.lineTo(15, 18.5);
                g2.draw(p);
                break;
            case "chevron-right":
                p = new Path2D.Double();
                p.moveTo(9, 5.5);
                p.lineTo(15.5, 12);
                p.lineTo(9, 18.5);
                g2.draw(p);
                break;
            case "columns":
                g2.draw(new RoundRectangle2D.Double(3.5, 5, 17, 14, 2.5, 2.5));
                g2.draw(new java.awt.geom.Line2D.Double(12, 5, 12, 19));
                break;
            case "expand":
                // 四角外扩箭头：放大输入框
                g2.draw(new java.awt.geom.Line2D.Double(4.5, 9.5, 4.5, 4.5));
                g2.draw(new java.awt.geom.Line2D.Double(4.5, 4.5, 9.5, 4.5));
                g2.draw(new java.awt.geom.Line2D.Double(14.5, 4.5, 19.5, 4.5));
                g2.draw(new java.awt.geom.Line2D.Double(19.5, 4.5, 19.5, 9.5));
                g2.draw(new java.awt.geom.Line2D.Double(4.5, 14.5, 4.5, 19.5));
                g2.draw(new java.awt.geom.Line2D.Double(4.5, 19.5, 9.5, 19.5));
                g2.draw(new java.awt.geom.Line2D.Double(14.5, 19.5, 19.5, 19.5));
                g2.draw(new java.awt.geom.Line2D.Double(19.5, 19.5, 19.5, 14.5));
                break;
            case "quote":
                g2.draw(new java.awt.geom.Line2D.Double(4, 7, 20, 7));
                g2.draw(new java.awt.geom.Line2D.Double(4, 12, 15, 12));
                g2.draw(new java.awt.geom.Line2D.Double(4, 17, 11, 17));
                break;
            case "undo":
                p = new Path2D.Double();
                p.moveTo(9, 14);
                p.lineTo(4.5, 9.5);
                p.lineTo(9, 5);
                g2.draw(p);
                p = new Path2D.Double();
                p.moveTo(4.5, 9.5);
                p.lineTo(14, 9.5);
                p.curveTo(18.5, 9.5, 20.5, 12.5, 20.5, 15);
                p.curveTo(20.5, 18, 18, 20, 14.5, 20);
                p.lineTo(11, 20);
                g2.draw(p);
                break;
            case "redo":
                p = new Path2D.Double();
                p.moveTo(15, 14);
                p.lineTo(19.5, 9.5);
                p.lineTo(15, 5);
                g2.draw(p);
                p = new Path2D.Double();
                p.moveTo(19.5, 9.5);
                p.lineTo(10, 9.5);
                p.curveTo(5.5, 9.5, 3.5, 12.5, 3.5, 15);
                p.curveTo(3.5, 18, 6, 20, 9.5, 20);
                p.lineTo(13, 20);
                g2.draw(p);
                break;
            case "mic":
                g2.draw(new RoundRectangle2D.Double(9, 2.4, 6, 11.2, 6, 6));
                g2.draw(new java.awt.geom.Arc2D.Double(5, 5.4, 14, 14, 180, 180,
                        java.awt.geom.Arc2D.OPEN));
                g2.draw(new java.awt.geom.Line2D.Double(12, 19.4, 12, 21.8));
                g2.draw(new java.awt.geom.Line2D.Double(8.4, 21.8, 15.6, 21.8));
                break;
            case "stop":
                g2.fill(new RoundRectangle2D.Double(6.5, 6.5, 11, 11, 3, 3));
                break;
            case "clock":
                g2.draw(new Ellipse2D.Double(3, 3, 18, 18));
                g2.draw(new java.awt.geom.Line2D.Double(12, 7, 12, 12.4));
                g2.draw(new java.awt.geom.Line2D.Double(12, 12.4, 15.6, 14.6));
                break;
            default:
                g2.draw(new Ellipse2D.Double(4, 4, 16, 16));
        }
        g2.dispose();
    }

    /** 圆形渐变头像 + 首字母。 */
    public static void avatar(Graphics g, int x, int y, int size, char initial, String id, boolean online) {
        Graphics2D g2 = Theme.aa(g);
        Color c = Theme.AVATARS[Math.abs(hash(id)) % Theme.AVATARS.length];
        Color c2 = c.brighter();
        g2.setPaint(new GradientPaint(x, y, c2, x + size, y + size, c));
        g2.fillOval(x, y, size, size);
        String s = String.valueOf(initial);
        g2.setFont(Theme.bold(Math.max(11, size / 2 - 1)));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(Color.WHITE);
        int tx = x + (size - fm.stringWidth(s)) / 2;
        int ty = y + (size + fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(s, tx, ty);
        if (online) {
            int d = Math.max(9, size / 3);
            int ox = x + size - d - 1;
            int oy = y + size - d - 1;
            g2.setColor(Color.WHITE);
            g2.fillOval(ox - 2, oy - 2, d + 4, d + 4);
            g2.setColor(Theme.GREEN);
            g2.fillOval(ox, oy, d, d);
        }
        g2.dispose();
    }

    private static int hash(String s) {
        int h = 0;
        if (s == null) {
            return 0;
        }
        for (int i = 0; i < s.length(); i++) {
            h = 31 * h + s.charAt(i);
        }
        return h;
    }

    /** 应用图标（窗口 / 任务栏用），返回 BufferedImage。 */
    public static java.awt.image.BufferedImage appIcon(int size) {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(size, size,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, new Color(0x3D7BFF), size, size, new Color(0x6D5EF8)));
        g.fillRoundRect(0, 0, size, size, size / 3, size / 3);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(size / 14f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int cx = size / 2;
        g.drawLine(cx - size / 8, size / 3, cx + size / 8, size / 3);
        g.drawLine(cx - size / 8, size / 2, cx + size / 8, size / 2);
        g.drawLine(cx - size / 8, size * 2 / 3, cx + size / 8, size * 2 / 3);
        g.fillOval(cx + size / 12, size / 3 - size / 24, size / 14, size / 14);
        g.fillOval(cx + size / 12, size / 2 - size / 24, size / 14, size / 14);
        g.fillOval(cx + size / 12, size * 2 / 3 - size / 24, size / 14, size / 14);
        g.dispose();
        return img;
    }
}
