import com.wlanshare.ui.Theme;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;

/** 读取真实屏幕上的选区像素，确认选中文字到底是什么颜色。 */
public class SelPixel {

    public static void main(String[] args) throws Exception {
        final String text = "第一行普通内容\n第二行是别人正在编辑的行\n第三行普通内容";
        final JFrame[] holder = new JFrame[1];
        final JTextArea[] holderTa = new JTextArea[1];
        SwingUtilities.invokeAndWait(() -> {
            JTextArea ta = new JTextArea(text, 6, 30);
            Theme.styleText(ta);
            ta.setFont(Theme.font(15));
            ta.setLineWrap(true);
            ta.setBackground(new Color(0xFBFCFE));
            ta.setForeground(new Color(0x1F2937));
            ta.setCaretColor(Theme.ACCENT);
            JFrame f = new JFrame("像素采样");
            f.add(new JScrollPane(ta));
            f.setSize(520, 220);
            f.setLocation(60, 100);
            f.setVisible(true);
            ta.setCaretPosition(7);
            ta.moveCaretPosition(22);
            ta.requestFocusInWindow();
            holder[0] = f;
            holderTa[0] = ta;
        });
        Thread.sleep(1200);
        JTextArea ta = holderTa[0];
        System.out.println("selectedTextColor=" + hex(ta.getSelectedTextColor()));
        System.out.println("selectionColor=" + hex(ta.getSelectionColor()));
        System.out.println("foreground=" + hex(ta.getForeground()));

        Robot r = new Robot();
        Rectangle b = holder[0].getBounds();
        BufferedImage img = r.createScreenCapture(b);
        ImageIO.write(img, "png", new File("build/selpixel.png"));

        // 第二行文字大致在窗口内 y = 标题栏(30) + 边距 + 行高
        int best = -1;
        int bestX = -1;
        int bestY = -1;
        java.util.Map<Integer, Integer> hist = new java.util.HashMap<>();
        for (int y = 40; y < 120; y++) {
            for (int x = 20; x < 320; x++) {
                int rgb = img.getRGB(x, y) & 0xFFFFFF;
                int r0 = (rgb >> 16) & 0xFF, g0 = (rgb >> 8) & 0xFF, b0 = rgb & 0xFF;
                // 统计暗像素（文字笔画）
                if (r0 + g0 + b0 < 330) {
                    hist.put(rgb, hist.getOrDefault(rgb, 0) + 1);
                }
            }
        }
        for (java.util.Map.Entry<Integer, Integer> en : hist.entrySet()) {
            if (en.getValue() > best) {
                best = en.getValue();
                bestX = en.getKey();
                bestY = en.getValue();
            }
        }
        System.out.println("选区内最常见的暗色像素=#" + String.format("%06X", bestX)
                + " 出现 " + bestY + " 次");
        System.exit(0);
    }

    private static String hex(Color c) {
        return c == null ? "null" : String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }
}
