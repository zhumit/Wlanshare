import com.wlanshare.ui.Theme;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** 用真实窗口验证选中配色：选中后文字仍清晰可读。 */
public class SelShot {

    public static void main(String[] args) throws Exception {
        final JFrame[] holder = new JFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            JFrame f = new JFrame("选中效果验证");
            JTextArea ta = new JTextArea(
                    "WlanShare 是一款局域网文件与协作工具。选中这段文字，看看是否依然清晰可读。",
                    4, 30);
            Theme.styleText(ta);
            ta.setFont(Theme.font(15));
            ta.setLineWrap(true);
            ta.setWrapStyleWord(true);
            ta.setBackground(new java.awt.Color(0xFBFCFE));
            ta.setForeground(new java.awt.Color(0x1F2937));
            ta.setCaretColor(Theme.ACCENT);
            f.add(new JScrollPane(ta));
            f.setSize(660, 200);
            f.setLocation(80, 120);
            f.setVisible(true);
            ta.setCaretPosition(4);
            ta.moveCaretPosition(30);
            ta.requestFocusInWindow();
            holder[0] = f;
        });
        Thread.sleep(1200);
        Robot r = new Robot();
        Rectangle b = holder[0].getBounds();
        BufferedImage img = r.createScreenCapture(b);
        ImageIO.write(img, "png", new File(args.length > 0 ? args[0] : "build/sel-shot.png"));
        System.out.println("saved " + b.width + "x" + b.height);
        System.exit(0);
    }
}
