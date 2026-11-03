import com.wlanshare.ui.Theme;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** 验证最终方案：交给 Swing 绘制选区（深色选中文字），全选跨空行 + 折行。 */
public class SelShot2 {

    static String TEXT = "第一段标题这里是唯一内容甲\n"
            + "\n"
            + "第二段正文每行内容都不重复避免误判乙丙丁\n"
            + "\n"
            + "第三段列表项内容唯一\n"
            + "\n"
            + "第四段很长的内容会自动折行到下一行 abcdefghijklmnopqrstuvwxyz 0123456789\n"
            + "\n"
            + "第五段结尾内容唯一戊己庚辛\n";

    public static void main(String[] args) throws Exception {
        Theme.install();
        final JFrame[] holder = new JFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            JTextArea ta = new JTextArea(TEXT, 12, 38);
            Theme.styleText(ta);
            ta.setFont(Theme.font(15));
            ta.setLineWrap(true);
            ta.setWrapStyleWord(false);
            ta.setBackground(new Color(0xFBFCFE));
            ta.setForeground(new Color(0x1F2937));
            ta.setCaretColor(Theme.ACCENT);

            JFrame f = new JFrame("选区验证");
            f.add(new JScrollPane(ta));
            f.setSize(580, 340);
            f.setLocation(60, 80);
            f.setVisible(true);

            // 全选（跨空行与折行）
            ta.setCaretPosition(0);
            ta.moveCaretPosition(TEXT.length());
            ta.requestFocusInWindow();
            holder[0] = f;
        });
        Thread.sleep(1200);
        Robot r = new Robot();
        Rectangle b = holder[0].getBounds();
        BufferedImage img = r.createScreenCapture(b);
        ImageIO.write(img, "png", new File(args.length > 0 ? args[0] : "build/sel-wrap.png"));
        System.out.println("saved " + b.width + "x" + b.height);
        System.exit(0);
    }
}
