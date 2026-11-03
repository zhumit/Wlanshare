import com.wlanshare.core.Doc;
import com.wlanshare.core.Platform;
import com.wlanshare.core.Node;
import com.wlanshare.ui.DocsPanel;
import com.wlanshare.ui.MainFrame;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 端到端验证「多人协作行标记」：
 * 两个真实客户端互连 -> 建好友 -> 共享文档 -> A 移动光标 -> B 窗口出现行标记与名字。
 */
public class CursorDemo {

    public static void main(String[] args) throws Exception {
        File base = new File("build/demo-" + System.currentTimeMillis());
        base.mkdirs();
        final Node a = new Node(new DemoPlatform(new File(base, "a"), "工作站A"));
        final Node b = new Node(new DemoPlatform(new File(base, "b"), "笔记本B"));
        a.start();
        b.start();

        System.out.println("A=" + a.id + " B=" + b.id);

        // 1. 互相发现
        boolean found = waitFor(() -> a.peers.containsKey(b.id) && b.peers.containsKey(a.id), 30000);
        System.out.println("互相发现: " + found);
        if (!found) {
            System.exit(1);
        }

        // 2. 加好友
        a.requestFriend(b.id);
        boolean friends = waitFor(() -> a.friends.containsKey(b.id) && b.friends.containsKey(a.id), 15000);
        System.out.println("互为好友: " + friends);

        // 3. A 创建文档并共享给 B
        Doc doc = a.createDoc("协作演示");
        a.shareDoc(doc.did, b.id);
        final String did = doc.did;
        boolean shared = waitFor(() -> b.doc(did) != null, 15000);
        System.out.println("文档已同步到 B: " + shared);

        // 4. B 打开文档页
        final MainFrame frameB = new MainFrame(b);
        frameB.addSection("docs", "文档", "doc", new DocsPanel(frameB, b));
        frameB.setSize(1100, 720);
        frameB.setLocation(120, 60);
        frameB.setVisible(true);
        SwingUtilities.invokeAndWait(() -> frameB.show("docs"));
        Thread.sleep(1500);

        // 5. A 把光标放到第 3 行
        a.sendDocCursor(did, 2);
        Thread.sleep(1200);

        Doc bdoc = b.doc(did);
        System.out.println("B 收到的光标: " + bdoc.cursors);
        System.out.println("B 看到的名字: " + (bdoc.cursors.isEmpty() ? "无"
                : b.nameOf(bdoc.cursors.keySet().iterator().next())));

        // 6. 截图 B 的窗口（先置顶，避免被其他窗口遮挡）
        SwingUtilities.invokeAndWait(() -> {
            frameB.toFront();
            frameB.requestFocus();
        });
        Thread.sleep(1200);
        // 离屏渲染窗口内容，避免被其它窗口遮挡
        SwingUtilities.invokeAndWait(() -> {
            frameB.validate();
            frameB.getContentPane().validate();
        });
        Thread.sleep(600);
        java.awt.Component content = frameB.getContentPane();
        int cw = Math.max(1, content.getWidth());
        int ch = Math.max(1, content.getHeight());
        System.out.println("content=" + cw + "x" + ch);
        BufferedImage img = new BufferedImage(cw, ch,
                BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        content.paint(g);
        g.dispose();
        ImageIO.write(img, "png", new File(args.length > 0 ? args[0] : "build/cursor-demo.png"));
        System.out.println("saved " + img.getWidth() + "x" + img.getHeight());

        a.stop();
        b.stop();
        System.exit(0);
    }

    /** 独立的测试平台：不同数据目录 -> 不同设备 ID。 */
    static class DemoPlatform implements Platform {
        private final File dir;
        private final String name;

        DemoPlatform(File dir, String name) {
            this.dir = dir;
            this.name = name;
            dir.mkdirs();
        }

        public File dataDir() {
            return dir;
        }

        public String deviceName() {
            return name;
        }

        public int deviceType() {
            return 0;
        }

        public String defaultNick() {
            return name;
        }
    }

    private static boolean waitFor(java.util.function.BooleanSupplier c, int ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            if (c.getAsBoolean()) {
                return true;
            }
            Thread.sleep(200);
        }
        return false;
    }
}
