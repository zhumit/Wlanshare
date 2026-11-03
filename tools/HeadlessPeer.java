import com.wlanshare.core.Node;
import com.wlanshare.core.Peer;
import com.wlanshare.core.Platform;

import java.io.File;

/** 无界面测试对端：发现第一台设备后连发几条消息，用于验证未读红圈。 */
public class HeadlessPeer {

    public static void main(String[] args) throws Exception {
        String name = args.length > 0 ? args[0] : "测试对端";
        int count = args.length > 1 ? Integer.parseInt(args[1]) : 3;

        File dir = new File("build/hp-" + System.currentTimeMillis());
        dir.mkdirs();
        Node node = new Node(new Platform() {
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
        });
        node.start();
        System.out.println("peer up: " + node.id + " name=" + name);

        long end = System.currentTimeMillis() + 60000;
        Peer target = null;
        while (System.currentTimeMillis() < end) {
            for (Peer p : node.peerList()) {
                if (!p.id.equals(node.id)) {
                    target = p;
                    break;
                }
            }
            if (target != null) {
                break;
            }
            Thread.sleep(500);
        }
        if (target == null) {
            System.out.println("未发现任何设备");
            System.exit(1);
        }
        System.out.println("target=" + target.id + " " + node.nameOf(target.id));

        node.requestFriend(target.id);
        Thread.sleep(1500);
        // 持续发一段时间，等待对端连上来
        for (int round = 0; round < 10; round++) {
            for (int i = 1; i <= count; i++) {
                node.sendChat(target.id, "未读测试消息 " + (round * count + i));
            }
            System.out.println("round " + round + " 已发 " + count + " 条, 连接="
                    + (node.peers.get(target.id) != null && node.peers.get(target.id).connected));
            Thread.sleep(2500);
        }
        System.exit(0);
    }
}
