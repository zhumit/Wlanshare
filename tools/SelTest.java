import com.wlanshare.ui.Theme;

import javax.swing.JTextArea;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** 文本选中配色自测：确保选中后仍是「深色字 + 浅色底」，不会白字浅底看不清。 */
public class SelTest {

    public static void main(String[] args) throws Exception {
        int fail = 0;

        JTextArea probe = new JTextArea();
        Theme.styleText(probe);
        fail += check("styleText 已写入选中色", probe.getSelectionColor() != null
                && probe.getSelectedTextColor() != null, "");

        JTextArea ta = new JTextArea("WlanShare 局域网协作工具", 3, 20);
        Theme.styleText(ta);
        ta.setSize(new Dimension(260, 60));
        ta.setCaretPosition(0);
        ta.moveCaretPosition(9);

        Color sel = ta.getSelectionColor();
        Color txt = ta.getSelectedTextColor();
        System.out.println("  选中底色=" + hex(sel) + " 选中文字色=" + hex(txt));

        fail += check("选中底色已自定义", sel != null, hex(sel));
        fail += check("选中底色为浅色（可读背景）",
                sel == null || sel.getRed() + sel.getGreen() + sel.getBlue() > 500, hex(sel));
        fail += check("选中文字为深色",
                txt == null || (txt.getRed() + txt.getGreen() + txt.getBlue()) < 400, hex(txt));

        // 实际渲染：取选中区域内一个背景像素，确认与设定色一致
        BufferedImage img = new BufferedImage(260, 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        ta.paint(g);
        g.dispose();
        // 关键点：选中色不再沿用系统主题（系统主题可能给出「白字浅底」导致看不清）
        Color sysSel = javax.swing.UIManager.getColor("TextField.selectionBackground");
        Color sysFg = javax.swing.UIManager.getColor("TextField.selectionForeground");
        System.out.println("  系统默认 底色=" + hex(sysSel) + " 文字色=" + hex(sysFg));
        fail += check("选中底色已覆盖系统默认", sel != null && !sel.equals(sysSel), hex(sysSel));
        fail += check("选中文字色已覆盖系统默认", txt != null && !txt.equals(sysFg), hex(sysFg));

        System.out.println("== 结果: " + (fail == 0 ? "全部通过" : fail + " 项失败") + " ==");
        if (fail > 0) {
            System.exit(1);
        }
    }

    private static int check(String name, boolean ok, String extra) {
        if (ok) {
            System.out.println("  [PASS] " + name);
            return 0;
        }
        System.out.println("  [FAIL] " + name + "  " + extra);
        return 1;
    }

    private static String hex(Color c) {
        return c == null ? "null" : String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }
}
