package com.wlanshare;

import com.wlanshare.core.Node;
import com.wlanshare.ui.ChatPanel;
import com.wlanshare.ui.DevicesPanel;
import com.wlanshare.ui.DocsPanel;
import com.wlanshare.ui.FilesPanel;
import com.wlanshare.ui.MainFrame;
import com.wlanshare.ui.SettingsPanel;
import com.wlanshare.ui.Theme;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** PC 客户端入口。 */
public final class Main {

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("sun.java2d.uiScale.enabled", "true");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        Theme.install();

        final Node node = new Node(new PcPlatform());
        node.start();

        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame(node);
            node.setListener(frame);
            frame.addSection("devices", "附近设备", "devices", new DevicesPanel(frame, node));
            frame.addSection("chat", "消息", "chat", new ChatPanel(frame, node));
            frame.addSection("files", "文件", "files", new FilesPanel(frame, node));
            frame.addSection("docs", "文档", "doc", new DocsPanel(frame, node));
            frame.addSection("settings", "设置", "settings", new SettingsPanel(frame, node));
            frame.show("devices");
            frame.setVisible(true);
            if (node.peerList().isEmpty()) {
                frame.toast("正在搜索附近的设备…");
            }
        });
    }
}
