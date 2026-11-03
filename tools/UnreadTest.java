import com.wlanshare.core.Node;
import com.wlanshare.core.Platform;
import com.wlanshare.ui.MainFrame;

import java.io.File;

/** 验证：未读数进入窗口标题 + 提示音资源随包存在。 */
public class UnreadTest {

    public static void main(String[] args) throws Exception {
        File dir = new File("build/utest-" + System.currentTimeMillis());
        dir.mkdirs();
        Node node = new Node(new Platform() {
            public File dataDir() {
                return dir;
            }

            public String deviceName() {
                return "测试机";
            }

            public int deviceType() {
                return 0;
            }
        });
        node.start();

        MainFrame f = new MainFrame(node);
        f.setVisible(true);
        node.setListener(f);

        node.unread.put("peer-x", 3);
        node.unread.put("peer-y", 2);
        f.onState("unread");
        Thread.sleep(600);
        System.out.println("TITLE=" + f.getTitle());
        System.out.println("BADGE=" + node.unreadTotal());

        node.markRead("peer-x");
        f.onState("unread");
        Thread.sleep(500);
        System.out.println("TITLE2=" + f.getTitle());

        System.out.println("WAV=" + (UnreadTest.class.getResource("/sounds/msg.wav") != null));

        f.dispose();
        node.stop();
        System.exit(0);
    }
}
