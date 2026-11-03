package com.wlanshare.ui;

import com.wlanshare.core.Node;
import com.wlanshare.core.Peer;
import com.wlanshare.core.Transfer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;

/** 文件传输：拖拽发送 + 进度列表。 */
public class FilesPanel implements Section {

    private final MainFrame frame;
    private final Node node;
    private final JPanel view = new JPanel(new BorderLayout());
    private final JPanel listBox = new JPanel();
    private final JComboBox<PeerItem> target = new JComboBox<PeerItem>();
    private final JComponent drop;
    private String targetId;

    public FilesPanel(MainFrame frame, Node node) {
        this.frame = frame;
        this.node = node;
        view.setOpaque(false);

        Theme.Btn clearAll = new Theme.Btn("清空记录", Theme.Kind.GHOST).small();
        clearAll.setToolTipText("清除所有已结束的传输记录");
        clearAll.addActionListener(e -> clearAllRecords());
        Theme.Btn open = new Theme.Btn("接收文件夹", Theme.Kind.GHOST).small();
        open.addActionListener(e -> openRecv());
        view.add(UI.header("文件传输", "局域网直连传输，不经服务器中转",
                UI.rowRight(clearAll, open)), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 16));
        center.setOpaque(false);

        // 发送区
        Theme.Card send = new Theme.Card();
        send.setLayout(new BorderLayout(16, 0));
        send.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        send.setPreferredSize(new Dimension(100, 128));

        drop = new JComponent() {
            boolean hover;

            {
                setTransferHandler(new DropHandler());
                setCursor(new Cursor(Cursor.HAND_CURSOR));
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hover = true;
                        repaint();
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hover = false;
                        repaint();
                    }

                    @Override
                    public void mouseClicked(MouseEvent e) {
                        chooseFiles();
                    }
                });
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(400, 100);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.aa(g);
                g2.setColor(new Color(0xFAFBFE));
                Theme.roundRect(g2, 0, 0, getWidth(), getHeight(), 12);
                g2.setColor(new Color(0xC9D5EA));
                g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        1f, new float[]{6f, 6f}, 0f));
                Theme.border(g2, 0, 0, getWidth(), getHeight(), 12);
                g2.dispose();

                Icons.draw(g, "upload", 18, getHeight() / 2 - 14, 28, new Color(0x7E8CA6));
                Graphics2D g3 = Theme.aa(g);
                g3.setFont(Theme.bold(13));
                g3.setColor(Theme.TEXT);
                g3.drawString("拖拽文件到这里，或点击选择", 56, getHeight() / 2 - 2);
                g3.setFont(Theme.font(11));
                g3.setColor(Theme.MUTED);
                g3.drawString("支持多选，单个文件不限大小（建议 < 2GB）", 56, getHeight() / 2 + 16);
                g3.dispose();
            }
        };
        send.add(drop, BorderLayout.CENTER);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.add(Theme.label("发送给", 11, Theme.SUB, true));
        right.add(UI.vgap(6));
        target.setPreferredSize(new Dimension(190, 34));
        target.setMaximumSize(new Dimension(190, 34));
        target.setFont(Theme.font(12));
        target.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                                                                   int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setText(value instanceof PeerItem ? ((PeerItem) value).name : "选择接收人");
                setFont(Theme.font(12));
                setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                return this;
            }
        });
        target.addActionListener(e -> {
            Object o = target.getSelectedItem();
            targetId = o instanceof PeerItem ? ((PeerItem) o).id : null;
        });
        right.add(target);
        right.add(UI.vgap(10));
        Theme.Btn pick = new Theme.Btn("选择文件", Theme.Kind.PRIMARY).small();
        pick.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        pick.addActionListener(e -> chooseFiles());
        right.add(pick);
        JPanel rightWrap = new JPanel(new BorderLayout());
        rightWrap.setOpaque(false);
        rightWrap.add(right, BorderLayout.NORTH);
        send.add(rightWrap, BorderLayout.EAST);
        center.add(send, BorderLayout.NORTH);

        // 传输列表
        Theme.Card listCard = new Theme.Card();
        listCard.setLayout(new BorderLayout());
        listCard.setBorder(BorderFactory.createEmptyBorder(14, 16, 10, 14));
        JPanel lt = UI.row(Theme.label("传输记录", 13, Theme.TEXT, true),
                Theme.label("右键单条记录可打开所在文件夹 / 重新发送 / 删除", 10, Theme.MUTED, false));
        lt.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        listCard.add(lt, BorderLayout.NORTH);
        listBox.setOpaque(false);
        listBox.setLayout(new BoxLayout(listBox, BoxLayout.Y_AXIS));
        JPanel listWrap = new JPanel(new BorderLayout());
        listWrap.setOpaque(false);
        listWrap.add(listBox, BorderLayout.NORTH);
        listCard.add(new Theme.SlimScroll(listWrap), BorderLayout.CENTER);
        center.add(listCard, BorderLayout.CENTER);

        view.add(center, BorderLayout.CENTER);
    }

    @Override
    public JComponent view() {
        return view;
    }

    public void setTarget(String peerId) {
        this.targetId = peerId;
        refreshTargets();
        for (int i = 0; i < target.getItemCount(); i++) {
            if (peerId.equals(target.getItemAt(i).id)) {
                target.setSelectedIndex(i);
                break;
            }
        }
        SwingUtilities.invokeLater(() -> {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("选择要发送的文件");
            fc.setMultiSelectionEnabled(true);
            if (fc.showOpenDialog(view) == JFileChooser.APPROVE_OPTION) {
                sendFiles(fc.getSelectedFiles());
            }
        });
    }

    @Override
    public void onShow() {
        refreshTargets();
        rebuild();
    }

    @Override
    public void refresh(String what) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> refresh(what));
            return;
        }
        refreshTargets();
        rebuild();
    }

    private void refreshTargets() {
        String keep = targetId;
        target.removeAllItems();
        // 发送文件不要求加好友：列出所有在线设备
        List<Peer> peers = node.peerList();
        if (peers.isEmpty()) {
            peers = node.friendPeerList();
        }
        for (Peer p : peers) {
            target.addItem(new PeerItem(p.id, node.nameOf(p.id)));
        }
        if (keep != null) {
            for (int i = 0; i < target.getItemCount(); i++) {
                if (keep.equals(target.getItemAt(i).id)) {
                    target.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    private void chooseFiles() {
        if (node.peerList().isEmpty()) {
            frame.toast("还没发现其他设备，请确认对方已打开客户端");
            return;
        }
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("选择要发送的文件");
        fc.setMultiSelectionEnabled(true);
        if (fc.showOpenDialog(view) == JFileChooser.APPROVE_OPTION) {
            sendFiles(fc.getSelectedFiles());
        }
    }

    private void sendFiles(File[] files) {
        if (files == null || files.length == 0) {
            return;
        }
        if (targetId == null) {
            frame.toast("请先选择接收人");
            return;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                frame.toast("暂不支持文件夹，请选择文件");
                continue;
            }
            node.sendFile(targetId, f);
            frame.toast("正在发送 " + f.getName());
        }
    }

    private void openRecv() {
        try {
            File dir = node.recvDir;
            dir.mkdirs();
            java.awt.Desktop.getDesktop().open(dir);
        } catch (Exception e) {
            frame.toast("无法打开文件夹：" + e.getMessage());
        }
    }

    /** 清空所有已结束的记录（传输中的保留）。 */
    private void clearAllRecords() {
        List<Transfer> list = node.transferList();
        int finished = 0;
        for (Transfer t : list) {
            if (t.state != Transfer.ACTIVE && t.state != Transfer.PENDING) {
                finished++;
            }
        }
        if (finished == 0) {
            frame.toast(node.transfers.isEmpty() ? "还没有传输记录" : "正在传输中的记录不能清空");
            return;
        }
        if (!UI.confirm(view, "清空传输记录",
                "将清除 " + finished + " 条已结束的记录（不会删除已经传好的文件）。正在传输中的记录会保留。",
                "清空")) {
            return;
        }
        int n = node.clearTransfers();
        rebuild();
        frame.toast("已清除 " + n + " 条记录");
    }

    private void removeRecord(Transfer t) {
        if (t.state == Transfer.ACTIVE || t.state == Transfer.PENDING) {
            frame.toast("这条还在传输中，传完再删");
            return;
        }
        if (!UI.confirm(view, "删除这条记录",
                "只从列表里移除这条记录，硬盘上的文件不会删除。", "删除")) {
            return;
        }
        node.removeTransfer(t.fid);
        rebuild();
    }

    private void rebuild() {
        listBox.removeAll();
        List<Transfer> list = node.transferList();
        if (list.isEmpty()) {
            JPanel empty = UI.empty("files", "还没有传输记录", "选择文件发送给任意在线设备，或等待对方发来文件");
            empty.setBorder(BorderFactory.createEmptyBorder(26, 0, 26, 0));
            listBox.add(empty);
        } else {
            for (Transfer t : list) {
                listBox.add(new Row(t));
                listBox.add(UI.vgap(2));
            }
        }
        listBox.revalidate();
        listBox.repaint();
    }

    private class Row extends JComponent {
        private final Transfer t;
        private boolean retryable;
        private boolean hover;

        Row(Transfer t) {
            this.t = t;
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
            setPreferredSize(new Dimension(400, 64));
            boolean openable = t.state == Transfer.DONE && t.direction == Transfer.RECEIVING;
            retryable = t.state == Transfer.FAILED && t.direction == Transfer.SENDING;
            setToolTipText("左键：" + (retryable ? "重试续传" : openable ? "打开文件" : "—")
                    + " · 右键：更多操作");
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.isPopupTrigger() || javax.swing.SwingUtilities.isRightMouseButton(e)) {
                        return;
                    }
                    if (retryable) {
                        node.retryTransfer(t.fid);
                        frame.toast("重新发送，将从对方已有的位置继续");
                        return;
                    }
                    if (t.state == Transfer.DONE) {
                        openFile();
                    }
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    popup(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    popup(e);
                }

                private void popup(MouseEvent e) {
                    if (!e.isPopupTrigger()) {
                        return;
                    }
                    showMenu(Row.this, e.getX(), e.getY());
                }
            });
        }

        private void openFile() {
            try {
                File f = new File(t.path);
                if (!f.exists()) {
                    frame.toast("文件已不在这里：" + t.path);
                    return;
                }
                java.awt.Desktop.getDesktop().open(f);
            } catch (Exception ex) {
                frame.toast("无法打开：" + ex.getMessage());
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.aa(g);
            g2.setColor(hover ? new Color(0xF3F7FF) : new Color(0xFAFBFD));
            Theme.roundRect(g2, 0, 0, getWidth(), getHeight(), 10);
            g2.dispose();

            boolean sending = t.direction == Transfer.SENDING;
            Icons.draw(g, sending ? "upload" : "download", 12, 20, 22,
                    sending ? Theme.ACCENT : Theme.GREEN);

            Graphics2D g3 = Theme.aa(g);
            g3.setFont(Theme.bold(12));
            g3.setColor(Theme.TEXT);
            FontMetrics fm = g3.getFontMetrics();
            int maxW = Math.max(120, getWidth() - 300);
            g3.drawString(Theme.ellipsis(t.name, fm, maxW), 44, 24);

            g3.setFont(Theme.font(10));
            g3.setColor(Theme.MUTED);
            String peerName = node.nameOf(t.peerId);
            if ("未知设备".equals(peerName) && t.peerNick != null && !t.peerNick.isEmpty()) {
                peerName = t.peerNick;
            }
            String meta = (sending ? "发送给 " : "来自 ") + peerName + " · " + Node.human(t.size);
            g3.drawString(Theme.ellipsis(meta, Theme.fm(Theme.font(10)), maxW), 44, 41);

            // 进度条
            int barX = 44;
            int barW = Math.max(80, getWidth() - 300);
            int barY = 48;
            if (t.state == Transfer.ACTIVE || t.state == Transfer.PENDING) {
                g3.setColor(new Color(0xE6EBF5));
                Theme.roundRect(g3, barX, barY, barW, 6, 6);
                g3.setColor(sending ? Theme.ACCENT : Theme.GREEN);
                int w = (int) (barW * t.progress());
                if (w > 0) {
                    Theme.roundRect(g3, barX, barY, Math.max(6, w), 6, 6);
                }
            }

            // 状态
            String status;
            Color c;
            switch (t.state) {
                case Transfer.DONE:
                    status = "已完成 · " + Node.human(t.size);
                    c = Theme.GREEN;
                    break;
                case Transfer.FAILED:
                    status = "失败" + (t.error.isEmpty() ? "" : " · " + t.error);
                    c = Theme.RED;
                    break;
                case Transfer.REJECTED:
                    status = "对方已拒绝";
                    c = Theme.AMBER;
                    break;
                case Transfer.PENDING:
                    status = "等待对方确认";
                    c = Theme.AMBER;
                    break;
                default:
                    status = (t.offset > 0 ? "续传中 " : "")
                            + Math.round(t.progress() * 100) + "%  " + Node.human(t.done) + " / " + Node.human(t.size);
                    c = Theme.ACCENT;
            }
            g3.setFont(Theme.font(10));
            g3.setColor(c);
            FontMetrics fm2 = g3.getFontMetrics();
            g3.drawString(status, getWidth() - fm2.stringWidth(status) - 16, 24);

            g3.setFont(Theme.font(10));
            g3.setColor(new Color(0xAEB6C6));
            String time = Theme.timeText(t.ts);
            g3.drawString(time, getWidth() - g3.getFontMetrics().stringWidth(time) - 16, 41);
            if (t.state == Transfer.DONE) {
                g3.setColor(Theme.MUTED);
                String hint = t.direction == Transfer.RECEIVING ? "点击打开" : "已完成";
                g3.drawString(hint, getWidth() - g3.getFontMetrics().stringWidth(hint) - 16, 55);
            } else if (retryable) {
                g3.setColor(Theme.ACCENT);
                String hint = "点击重试续传";
                g3.drawString(hint, getWidth() - g3.getFontMetrics().stringWidth(hint) - 16, 55);
            }
            g3.dispose();
        }

        /** 右键菜单：打开 / 打开所在文件夹 / 再次发送 / 删除记录。 */
        private void showMenu(java.awt.Component at, int x, int y) {
            final File f = new File(t.path == null ? "" : t.path);
            javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();

            javax.swing.JMenuItem openIt = new javax.swing.JMenuItem("打开文件");
            openIt.setFont(Theme.font(12));
            openIt.setEnabled(f.isFile());
            openIt.addActionListener(e -> openFile());
            menu.add(openIt);

            javax.swing.JMenuItem reveal = new javax.swing.JMenuItem("打开所在文件夹");
            reveal.setFont(Theme.font(12));
            File dir = f.getAbsoluteFile().getParentFile();
            reveal.setEnabled(dir != null && dir.exists());
            reveal.addActionListener(e -> {
                if (!UI.revealInFolder(f)) {
                    frame.toast("找不到这个文件所在的文件夹");
                }
            });
            menu.add(reveal);

            // 再次发送给任意设备（断点续传仍然生效）
            javax.swing.JMenu again = new javax.swing.JMenu("再次发送");
            again.setFont(Theme.font(12));
            List<Peer> peers = node.peerList();
            if (peers.isEmpty()) {
                peers = node.friendPeerList();
            }
            if (peers.isEmpty() || !f.isFile()) {
                again.setEnabled(false);
            } else {
                for (Peer p : peers) {
                    final String pid = p.id;
                    javax.swing.JMenuItem it = new javax.swing.JMenuItem("发送给 " + node.nameOf(pid));
                    it.setFont(Theme.font(12));
                    it.addActionListener(e -> {
                        if (!f.isFile()) {
                            frame.toast("源文件已不存在");
                            return;
                        }
                        node.sendFile(pid, f);
                        frame.toast("正在发送 " + f.getName() + " 给 " + node.nameOf(pid));
                    });
                    again.add(it);
                }
            }
            menu.add(again);
            menu.addSeparator();

            javax.swing.JMenuItem del = new javax.swing.JMenuItem("删除这条记录");
            del.setFont(Theme.font(12));
            del.setEnabled(t.state != Transfer.ACTIVE && t.state != Transfer.PENDING);
            del.addActionListener(e -> removeRecord(t));
            menu.add(del);

            menu.show(at, x, y);
        }
    }

    /** 拖放支持。 */
    private class DropHandler extends TransferHandler {
        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            try {
                List<File> files = (List<File>) support.getTransferable()
                        .getTransferData(DataFlavor.javaFileListFlavor);
                sendFiles(files.toArray(new File[0]));
                return true;
            } catch (Exception e) {
                frame.toast("拖入文件失败：" + e.getMessage());
                return false;
            }
        }
    }

    private static class PeerItem {
        final String id;
        final String name;

        PeerItem(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
