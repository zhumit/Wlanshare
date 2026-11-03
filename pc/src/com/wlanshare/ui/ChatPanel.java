package com.wlanshare.ui;

import com.wlanshare.core.Node;
import com.wlanshare.core.Peer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.Document;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 消息：会话列表 + 气泡（支持引用 / 60 秒撤回 / 语音 / 搜索 / 会话清理）。 */
public class ChatPanel implements Section {

    /** 输入框自动长高的上限（超过后内部滚动）。 */
    private static final int MAX_INPUT_ROWS = 8;

    private final MainFrame frame;
    private final Node node;
    private final JPanel view = new JPanel(new BorderLayout(16, 0));
    private final JPanel convBox = new JPanel();
    private final JPanel msgBox = new JPanel();
    private final JTextArea input = new JTextArea(1, 20);
    private final JScrollPane msgScroll;
    private final JPanel chatArea = new JPanel(new BorderLayout(0, 12));
    private final JPanel headBox = new JPanel(new BorderLayout());
    private final JPanel inputCard = new Theme.Card();
    private final JPanel quoteBar = new JPanel(new BorderLayout(8, 0));

    private Theme.Card leftCard;
    private boolean listCollapsed;
    private String current;
    private int signature = -1;

    /** 会话列表头部：普通 / 搜索两种状态。 */
    private JPanel headHost;
    private UI.RoundField searchField;
    private boolean searching;
    private Timer searchTimer;

    private JScrollPane inputScroll;
    private JPanel inputHead;
    private Timer locateTimer;
    private String locateMid = "";

    private String quoteId = "";
    private String quoteText = "";
    private String quoteName = "";

    private Voice.Recorder recorder;
    private Timer recTimer;
    private Theme.Btn voiceBtn;
    private Theme.Btn cancelBtn;

    private String playingMid = "";
    private javax.sound.sampled.Clip clip;

    public ChatPanel(MainFrame frame, Node node) {
        this.frame = frame;
        this.node = node;
        view.setOpaque(false);

        // ---- 左：会话列表
        leftCard = new Theme.Card();
        leftCard.setLayout(new BorderLayout());
        leftCard.setPreferredSize(new Dimension(276, 100));
        leftCard.setBorder(BorderFactory.createEmptyBorder(12, 10, 10, 10));

        headHost = new JPanel(new CardLayout());
        headHost.setOpaque(false);
        headHost.add(buildListHead(), "normal");
        headHost.add(buildSearchHead(), "search");
        leftCard.add(headHost, BorderLayout.NORTH);

        convBox.setOpaque(false);
        convBox.setLayout(new BoxLayout(convBox, BoxLayout.Y_AXIS));
        JPanel convWrap = new JPanel(new BorderLayout());
        convWrap.setOpaque(false);
        convWrap.add(convBox, BorderLayout.NORTH);
        leftCard.add(new Theme.SlimScroll(convWrap), BorderLayout.CENTER);
        view.add(leftCard, BorderLayout.WEST);

        // ---- 右：聊天区
        chatArea.setOpaque(false);
        headBox.setOpaque(false);
        chatArea.add(headBox, BorderLayout.NORTH);

        msgBox.setOpaque(false);
        msgBox.setLayout(new BoxLayout(msgBox, BoxLayout.Y_AXIS));
        msgBox.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        JPanel msgWrap = new JPanel(new BorderLayout());
        msgWrap.setOpaque(false);
        msgWrap.add(msgBox, BorderLayout.NORTH);
        msgScroll = new Theme.SlimScroll(msgWrap);
        msgScroll.setOpaque(false);
        msgScroll.getViewport().setOpaque(false);
        chatArea.add(msgScroll, BorderLayout.CENTER);

        buildInput();
        chatArea.add(inputCard, BorderLayout.SOUTH);
        view.add(chatArea, BorderLayout.CENTER);

        rebuildHead();
        rebuildConvs();
    }

    // ---------------------------------------------------------------- 会话头 / 搜索

    private JPanel buildListHead() {
        JPanel lh = new JPanel(new BorderLayout(6, 0));
        lh.setOpaque(false);
        lh.setBorder(BorderFactory.createEmptyBorder(0, 6, 8, 2));
        lh.add(Theme.label("会话", 13, Theme.TEXT, true), BorderLayout.WEST);
        Theme.IconBtn searchBtn = new Theme.IconBtn("search", "搜索会话和聊天记录", this::enterSearch);
        Theme.IconBtn fold = new Theme.IconBtn("chevron-left", "收起会话列表", this::toggleList);
        lh.add(UI.rowRight(searchBtn, fold), BorderLayout.EAST);
        return lh;
    }

    private JPanel buildSearchHead() {
        JPanel sh = new JPanel(new BorderLayout(6, 0));
        sh.setOpaque(false);
        sh.setBorder(BorderFactory.createEmptyBorder(0, 2, 8, 0));
        searchField = new UI.RoundField("搜索会话或聊天记录", 8);
        searchField.setPreferredSize(new Dimension(150, 32));
        searchField.field.setFont(Theme.font(12));
        searchField.field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                queueSearch();
            }

            public void removeUpdate(DocumentEvent e) {
                queueSearch();
            }

            public void changedUpdate(DocumentEvent e) {
            }
        });
        searchField.field.getInputMap().put(KeyStroke.getKeyStroke("ESCAPE"), "exit-search");
        searchField.field.getActionMap().put("exit-search", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                exitSearch();
            }
        });
        sh.add(searchField, BorderLayout.CENTER);
        sh.add(UI.rowRight(new Theme.IconBtn("close", "关闭搜索", this::exitSearch)), BorderLayout.EAST);
        return sh;
    }

    private void enterSearch() {
        searching = true;
        ((CardLayout) headHost.getLayout()).show(headHost, "search");
        searchField.setText("");
        rebuildConvs();
        headHost.revalidate();
        headHost.repaint();
        SwingUtilities.invokeLater(() -> searchField.field.requestFocusInWindow());
    }

    private void exitSearch() {
        searching = false;
        if (searchField != null) {
            searchField.setText("");
        }
        ((CardLayout) headHost.getLayout()).show(headHost, "normal");
        headHost.revalidate();
        headHost.repaint();
        rebuildConvs();
        if (current != null) {
            input.requestFocusInWindow();
        }
    }

    /** 输入停顿 220ms 再查，避免每次按键都扫一遍历史文件。 */
    private void queueSearch() {
        if (!searching) {
            return;
        }
        if (searchTimer == null) {
            searchTimer = new Timer(220, e -> rebuildConvs());
            searchTimer.setRepeats(false);
        }
        searchTimer.restart();
        // 立即反馈：清空时马上回到提示态
        if (searchField.text().trim().isEmpty()) {
            rebuildConvs();
        }
    }

    // ---------------------------------------------------------------- 输入区

    private void buildInput() {
        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        input.setOpaque(false);
        input.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        input.setFont(Theme.font(13));
        Theme.styleText(input);
        input.setForeground(Theme.TEXT);
        input.setCaretColor(Theme.ACCENT);

        // Enter 发送、Shift+Enter 换行；行数不再被固定高度限制
        input.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "send-msg");
        input.getInputMap().put(KeyStroke.getKeyStroke("shift ENTER"), "insert-break");
        input.getActionMap().put("insert-break", new DefaultEditorKit.InsertBreakAction());
        input.getActionMap().put("send-msg", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doSend();
            }
        });
        input.getInputMap().put(KeyStroke.getKeyStroke("ESCAPE"), "cancel-quote");
        input.getActionMap().put("cancel-quote", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                clearQuote();
            }
        });

        // 文本变化 → 输入框按实际行数自动长高
        input.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                resizeInput();
            }

            public void removeUpdate(DocumentEvent e) {
                resizeInput();
            }

            public void changedUpdate(DocumentEvent e) {
            }
        });

        inputScroll = new Theme.SlimScroll(input);
        inputScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        inputScroll.getVerticalScrollBar().setUnitIncrement(20);
        inputScroll.setPreferredSize(new Dimension(10, 26));

        // 引用条
        quoteBar.setOpaque(false);
        quoteBar.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 2));
        JComponent quoteTextCmp = new JComponent() {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(160, 26);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.aa(g);
                g2.setColor(new Color(0xE8EEF9));
                Theme.roundRect(g2, 0, 0, getWidth(), getHeight(), 8);
                g2.setColor(new Color(0xBFD0EE));
                g2.fillRoundRect(0, 4, 3, getHeight() - 8, 3, 3);
                g2.setFont(Theme.font(11));
                g2.setColor(Theme.SUB);
                FontMetrics fm = g2.getFontMetrics();
                String t = (quoteName.isEmpty() ? "" : quoteName + "：") + quoteText;
                g2.drawString(Theme.ellipsis(t, fm, getWidth() - 24), 12,
                        (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        quoteBar.add(quoteTextCmp, BorderLayout.CENTER);
        quoteBar.add(UI.rowRight(new Theme.IconBtn("close", "取消引用", this::clearQuote)),
                BorderLayout.EAST);
        quoteBar.setVisible(false);

        // 顶部：左边引用条，右上角放大输入框
        inputHead = new JPanel(new BorderLayout(6, 0));
        inputHead.setOpaque(false);
        inputHead.add(quoteBar, BorderLayout.CENTER);
        inputHead.add(UI.rowRight(
                new Theme.IconBtn("expand", "放大输入框（半屏编辑）", this::openBigInput, 24)),
                BorderLayout.EAST);

        JPanel sendRow = new JPanel(new BorderLayout());
        sendRow.setOpaque(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        voiceBtn = new Theme.Btn("", Theme.Kind.GHOST).small();
        voiceBtn.setToolTipText("点击开始录音，再点一次发送");
        voiceBtn.iconOnly("mic", 17, Theme.SUB);
        voiceBtn.addActionListener(e -> toggleRecord());
        cancelBtn = new Theme.Btn("取消", Theme.Kind.GHOST).small();
        cancelBtn.setVisible(false);
        cancelBtn.addActionListener(e -> cancelRecord());
        left.add(voiceBtn);
        left.add(cancelBtn);
        left.add(Theme.label("Enter 发送 · Shift+Enter 换行 · 长按消息可引用/撤回", 10, Theme.MUTED, false));
        sendRow.add(left, BorderLayout.WEST);

        Theme.Btn send = new Theme.Btn("发送", Theme.Kind.PRIMARY).small();
        send.addActionListener(e -> doSend());
        sendRow.add(UI.rowRight(send), BorderLayout.EAST);

        inputCard.setLayout(new BorderLayout(0, 2));
        inputCard.setBorder(BorderFactory.createEmptyBorder(6, 10, 8, 10));
        inputCard.add(inputHead, BorderLayout.NORTH);
        inputCard.add(inputScroll, BorderLayout.CENTER);
        inputCard.add(sendRow, BorderLayout.SOUTH);
        inputCard.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                resizeInput();
            }
        });
    }

    /** 按文本实际行数调整输入框高度（1 行起，最多 MAX_INPUT_ROWS 行，超出后内部滚动）。 */
    private void resizeInput() {
        if (inputScroll == null) {
            return;
        }
        FontMetrics fm = Theme.fm(input.getFont());
        int w = input.getWidth();
        if (w <= 0) {
            w = Math.max(260, view.getWidth() - 330);
        }
        String t = input.getText();
        int lines = t.isEmpty() ? 1 : Theme.wrapLines(t, fm, Math.max(60, w - 10));
        lines = Math.max(1, Math.min(MAX_INPUT_ROWS, lines));
        int h = lines * fm.getHeight() + 8;
        if (inputScroll.getPreferredSize().height != h) {
            inputScroll.setPreferredSize(new Dimension(10, h));
            inputScroll.revalidate();
            inputCard.revalidate();
            chatArea.revalidate();
            chatArea.repaint();
        }
    }

    /** 把放大框里的文本写回主输入框。 */
    private void applyBigText(JTextArea big) {
        String t = big.getText();
        if (!t.equals(input.getText())) {
            int caret = big.getCaretPosition();
            input.setText(t);
            try {
                input.setCaretPosition(Math.min(caret, t.length()));
            } catch (Exception ignored) {
            }
        }
        resizeInput();
    }

    /** 半屏放大输入：Enter 发送、Shift+Enter 换行、Esc 收起。 */
    private void openBigInput() {
        java.awt.Window owner = SwingUtilities.getWindowAncestor(view);
        final JDialog dlg = new JDialog(owner, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setUndecorated(true);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(Color.WHITE);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                BorderFactory.createEmptyBorder(16, 20, 14, 20)));

        JPanel head = new JPanel(new BorderLayout(10, 0));
        head.setOpaque(false);
        String who = current == null ? "" : "给 " + node.nameOf(current);
        head.add(Theme.label("写消息" + (who.isEmpty() ? "" : " · " + who), 14, Theme.TEXT, true),
                BorderLayout.WEST);
        final JTextArea big = new JTextArea(input.getText());
        head.add(UI.rowRight(new Theme.IconBtn("close", "收起（Esc）", () -> dlg.dispose())),
                BorderLayout.EAST);
        root.add(head, BorderLayout.NORTH);

        // 无边框窗口：按住标题区可以拖动
        final java.awt.Point drag = new java.awt.Point();
        head.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                drag.setLocation(e.getPoint());
            }
        });
        head.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                java.awt.Point p = dlg.getLocation();
                dlg.setLocation(p.x + e.getX() - drag.x, p.y + e.getY() - drag.y);
            }
        });

        big.setLineWrap(true);
        big.setWrapStyleWord(true);
        big.setFont(Theme.font(14));
        big.setForeground(Theme.TEXT);
        big.setCaretColor(Theme.ACCENT);
        big.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        big.setBackground(new Color(0xFAFBFE));
        Theme.styleText(big);
        big.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "big-send");
        big.getInputMap().put(KeyStroke.getKeyStroke("shift ENTER"), "insert-break");
        big.getActionMap().put("insert-break", new DefaultEditorKit.InsertBreakAction());
        final boolean[] sent = new boolean[]{false};
        big.getActionMap().put("big-send", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                applyBigText(big);
                sent[0] = true;
                dlg.dispose();
                doSend();
            }
        });
        big.getInputMap().put(KeyStroke.getKeyStroke("ESCAPE"), "big-close");
        big.getActionMap().put("big-close", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dlg.dispose();
            }
        });

        JPanel editor = new JPanel(new BorderLayout());
        editor.setOpaque(false);
        editor.setBorder(BorderFactory.createLineBorder(Theme.BORDER2));
        editor.add(new Theme.SlimScroll(big), BorderLayout.CENTER);
        root.add(editor, BorderLayout.CENTER);

        JPanel foot = new JPanel(new BorderLayout(8, 0));
        foot.setOpaque(false);
        foot.add(Theme.label("Enter 发送 · Shift+Enter 换行 · Esc 收起", 10, Theme.MUTED, false),
                BorderLayout.WEST);
        Theme.Btn back = new Theme.Btn("收起", Theme.Kind.GHOST).small();
        back.addActionListener(e -> dlg.dispose());
        Theme.Btn send = new Theme.Btn("发送", Theme.Kind.PRIMARY).small();
        send.addActionListener(e -> {
            applyBigText(big);
            sent[0] = true;
            dlg.dispose();
            doSend();
        });
        foot.add(UI.rowRight(back, send), BorderLayout.EAST);
        root.add(foot, BorderLayout.SOUTH);

        dlg.setContentPane(root);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int w = Math.min(940, Math.max(560, (int) (screen.width * 0.56)));
        int h = Math.min(720, Math.max(360, (int) (screen.height * 0.52)));
        dlg.setSize(w, h);
        dlg.setLocationRelativeTo(view);
        SwingUtilities.invokeLater(() -> {
            big.requestFocusInWindow();
            big.setCaretPosition(big.getText().length());
        });
        dlg.setVisible(true);
        if (!sent[0]) {
            applyBigText(big);
        }
        input.requestFocusInWindow();
    }

    private void clearQuote() {
        quoteId = "";
        quoteText = "";
        quoteName = "";
        quoteBar.setVisible(false);
        inputHead.revalidate();
        inputCard.revalidate();
        inputCard.repaint();
    }

    private void setQuote(Node.ChatMsg m) {
        quoteId = m.mid;
        quoteText = m.voice ? "[语音] " + m.voiceDur + "\"" : m.text.replace("\r", " ").replace("\n", " ");
        if (quoteText.length() > 60) {
            quoteText = quoteText.substring(0, 60) + "…";
        }
        quoteName = m.mine ? "我" : node.nameOf(current);
        quoteBar.setVisible(true);
        inputHead.revalidate();
        inputCard.revalidate();
        inputCard.repaint();
        input.requestFocusInWindow();
    }

    // ---------------------------------------------------------------- 录音

    private void toggleRecord() {
        if (recorder == null) {
            startRecord();
        } else {
            stopRecordAndSend();
        }
    }

    private void startRecord() {
        if (current == null) {
            frame.toast("请先选择一个联系人");
            return;
        }
        if (!Voice.canRecord()) {
            frame.toast("没有检测到麦克风");
            return;
        }
        recorder = new Voice.Recorder();
        if (!recorder.start()) {
            recorder = null;
            frame.toast("麦克风被占用，无法录音");
            return;
        }
        cancelBtn.setVisible(true);
        if (recTimer == null) {
            recTimer = new Timer(200, e -> {
                if (recorder == null) {
                    return;
                }
                voiceState(recorder.seconds());
            });
        }
        recTimer.start();
        voiceState(0);
    }

    private void stopRecordAndSend() {
        if (recorder == null) {
            return;
        }
        int dur = recorder.seconds();
        byte[] wav = recorder.stop();
        recorder = null;
        stopRecTimer();
        cancelBtn.setVisible(false);
        voiceState(-1);
        if (wav == null) {
            frame.toast("说话时间太短");
            return;
        }
        node.sendVoice(current, wav, Math.max(1, dur), quoteId, quoteText, quoteName);
        clearQuote();
    }

    private void cancelRecord() {
        if (recorder != null) {
            recorder.cancel();
            recorder = null;
        }
        stopRecTimer();
        cancelBtn.setVisible(false);
        voiceState(-1);
    }

    /**
     * 语音按钮状态：sec &gt;= 0 时显示红色停止图标与计时，sec &lt; 0 时只显示话筒图标。
     */
    private void voiceState(int sec) {
        boolean rec = sec >= 0;
        voiceBtn.setText(rec ? sec / 60 + ":" + String.format("%02d", sec % 60) : "");
        voiceBtn.setIconName(rec ? "stop" : "mic", rec ? 12 : 17,
                rec ? Color.WHITE : Theme.SUB);
        voiceBtn.palette(rec ? Theme.RED : null, rec ? Color.WHITE : null);
        voiceBtn.setToolTipText(rec ? "点击结束并发送" : "点击开始录音，再点一次发送");
        voiceBtn.revalidate();
        if (voiceBtn.getParent() != null) {
            voiceBtn.getParent().revalidate();
        }
        voiceBtn.repaint();
    }

    private void stopRecTimer() {
        if (recTimer != null) {
            recTimer.stop();
        }
    }

    // ---------------------------------------------------------------- Section

    @Override
    public JComponent view() {
        return view;
    }

    /** 当前打开的会话（未打开时为空串）。 */
    public String currentChat() {
        return current == null ? "" : current;
    }

    public void openWith(String peerId) {
        openWith(peerId, null);
    }

    /** 打开会话；mid 非空时滚动到该条消息并高亮（搜索定位用）。 */
    public void openWith(String peerId, String mid) {
        if (peerId == null) {
            return;
        }
        if (searching) {
            searching = false;
            if (searchField != null) {
                searchField.setText("");
            }
            ((CardLayout) headHost.getLayout()).show(headHost, "normal");
            headHost.revalidate();
            headHost.repaint();
        }
        current = peerId;
        node.activeChat = peerId;
        node.markRead(peerId);
        node.unhide(peerId);
        node.loadChatHistory(peerId);
        signature = -1;
        locateMid = mid == null ? "" : mid;
        rebuildConvs();
        rebuildHead();
        rebuildMessages();
        clearQuote();
        input.requestFocusInWindow();
        if (!locateMid.isEmpty()) {
            SwingUtilities.invokeLater(this::highlightLocated);
        }
    }

    @Override
    public void onShow() {
        rebuildConvs();
        rebuildHead();
        rebuildMessages();
    }

    @Override
    public void refresh(String what) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> refresh(what));
            return;
        }
        rebuildConvs();
        rebuildHead();
        rebuildMessages();
    }

    private void toggleList() {
        listCollapsed = !listCollapsed;
        leftCard.setVisible(!listCollapsed);
        view.revalidate();
        view.repaint();
        rebuildHead();
    }

    private void renamePeer(final String pid) {
        if (pid == null) {
            return;
        }
        String real = node.realNameOf(pid);
        String cur = node.aliasOf(pid);
        String v = UI.prompt(view, "重命名备注",
                "给「" + real + "」设置一个只在本机显示的名字，留空即恢复原名",
                cur.isEmpty() ? real : cur);
        if (v != null) {
            node.setAlias(pid, v);
            refresh("chat");
        }
    }

    private void doSend() {
        if (current == null) {
            frame.toast("请先在左侧选择一个联系人");
            return;
        }
        if (recorder != null) {
            frame.toast("正在录音，先点「停止」发送语音");
            return;
        }
        String t = input.getText();
        if (t == null || t.trim().isEmpty()) {
            return;
        }
        node.sendChat(current, t, quoteId, quoteText, quoteName);
        input.setText("");
        resizeInput();
        clearQuote();
        SwingUtilities.invokeLater(this::scrollBottom);
    }

    private Map<String, Peer> conversations() {
        Map<String, Peer> map = new LinkedHashMap<String, Peer>();
        for (Peer p : node.friendPeerList()) {
            if (!node.isHidden(p.id)) {
                map.put(p.id, p);
            }
        }
        for (Peer p : node.peerList()) {
            if (!node.isHidden(p.id) && !map.containsKey(p.id)) {
                map.put(p.id, p);
            }
        }
        return map;
    }

    private void rebuildConvs() {
        if (searching) {
            rebuildSearchResults();
            return;
        }
        Map<String, Peer> convs = conversations();
        convBox.removeAll();
        if (convs.isEmpty()) {
            JPanel empty = UI.empty("users", "还没有联系人", "附近设备出现后会自动列在这里");
            empty.setBorder(BorderFactory.createEmptyBorder(40, 0, 0, 0));
            convBox.add(empty);
        } else {
            for (Peer p : convs.values()) {
                convBox.add(new ConvRow(p));
                convBox.add(UI.vgap(2));
            }
        }
        if (current == null && !convs.isEmpty()) {
            current = convs.keySet().iterator().next();
            node.loadChatHistory(current);
            signature = -1;
            rebuildHead();
            rebuildMessages();
        } else if (current != null && !convs.isEmpty() && !convs.containsKey(current)) {
            // 当前会话被删掉后自动切到第一个
            current = convs.keySet().iterator().next();
            node.loadChatHistory(current);
            signature = -1;
            rebuildHead();
            rebuildMessages();
        }
        convBox.revalidate();
        convBox.repaint();
    }

    /** 搜索态：会话名命中 + 聊天记录命中，点击直接定位。 */
    private void rebuildSearchResults() {
        convBox.removeAll();
        String q = searchField == null ? "" : searchField.text().trim();
        if (q.isEmpty()) {
            JPanel hint = UI.empty("search", "搜索会话与聊天记录", "输入昵称或消息关键词，点击结果直接跳到那条消息");
            hint.setBorder(BorderFactory.createEmptyBorder(28, 0, 0, 0));
            convBox.add(hint);
            convBox.revalidate();
            convBox.repaint();
            return;
        }
        String lower = q.toLowerCase();
        int shown = 0;
        List<Peer> named = new ArrayList<Peer>();
        for (Peer p : conversations().values()) {
            String name = node.nameOf(p.id);
            String real = node.realNameOf(p.id);
            if (name.toLowerCase().contains(lower) || real.toLowerCase().contains(lower)) {
                named.add(p);
            }
        }
        if (!named.isEmpty()) {
            convBox.add(sectionLabel("设备 / 好友"));
            for (Peer p : named) {
                convBox.add(new SearchRow(p.id, null));
                convBox.add(UI.vgap(2));
                shown++;
            }
        }
        List<Node.ChatHit> hits = node.searchChat(q, 60);
        if (!hits.isEmpty()) {
            convBox.add(sectionLabel("聊天记录"));
            for (Node.ChatHit h : hits) {
                convBox.add(new SearchRow(h.pid, h));
                convBox.add(UI.vgap(2));
                shown++;
            }
        }
        if (shown == 0) {
            JPanel empty = UI.empty("search", "没有找到「" + q + "」", "换个关键词试试，或搜索对方昵称");
            empty.setBorder(BorderFactory.createEmptyBorder(28, 0, 0, 0));
            convBox.add(empty);
        }
        convBox.revalidate();
        convBox.repaint();
    }

    private JComponent sectionLabel(String text) {
        javax.swing.JLabel l = Theme.label(text, 10, Theme.MUTED, true);
        l.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 0));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        wrap.add(l, BorderLayout.WEST);
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        return wrap;
    }

    /** 高亮并滚动到搜索命中的那条消息。 */
    private void highlightLocated() {
        if (locateMid.isEmpty()) {
            return;
        }
        String mid = locateMid;
        locateMid = "";
        MessageRow target = null;
        for (Component c : msgBox.getComponents()) {
            if (c instanceof MessageRow && mid.equals(((MessageRow) c).m.mid)) {
                target = (MessageRow) c;
                break;
            }
        }
        if (target == null) {
            return;
        }
        final MessageRow row = target;
        row.setHighlight(true);
        java.awt.Rectangle r = row.getBounds();
        r.y -= 80;
        r.height += 160;
        msgBox.scrollRectToVisible(r);
        if (locateTimer != null) {
            locateTimer.stop();
        }
        locateTimer = new Timer(2400, e -> row.setHighlight(false));
        locateTimer.setRepeats(false);
        locateTimer.start();
    }

    private void clearHistory(final String pid) {
        if (!UI.confirm(view, "清空聊天记录",
                "将删除与「" + node.nameOf(pid) + "」的全部本机聊天记录，无法恢复。",
                "清空")) {
            return;
        }
        node.clearChat(pid);
        signature = -1;
        rebuildConvs();
        rebuildMessages();
        frame.toast("已清空聊天记录");
    }

    private void deleteConversation(final String pid) {
        if (!UI.confirm(view, "删除会话",
                "将删除「" + node.nameOf(pid) + "」的聊天记录、本机备注与好友关系，并从列表中移除。"
                        + "对方再次发来消息时会重新出现。",
                "删除")) {
            return;
        }
        node.deleteConversation(pid);
        if (pid.equals(current)) {
            current = null;
        }
        signature = -1;
        rebuildConvs();
        rebuildHead();
        rebuildMessages();
        frame.toast("会话已删除");
    }

    private void rebuildHead() {
        headBox.removeAll();
        if (current == null) {
            JPanel empty = UI.empty("chat", "选择左侧的联系人", "也可以从「附近设备」点开发消息");
            headBox.add(empty, BorderLayout.CENTER);
            inputCard.setVisible(false);
            headBox.revalidate();
            headBox.repaint();
            chatArea.revalidate();
            return;
        }
        inputCard.setVisible(true);
        Peer p = node.peers.get(current);
        final String nick = node.nameOf(current);
        boolean online = p != null && p.connected;
        final String dev = p == null ? "设备离线"
                : (p.deviceType == 1 ? "手机" : "电脑") + " · " + p.device;
        final boolean on = online;
        final char ini = node.initialOf(current);

        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        JComponent who = new JComponent() {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(220, 54);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Icons.avatar(g, 0, 4, 42, ini, current, on);
                Graphics2D g2 = Theme.aa(g);
                g2.setFont(Theme.bold(15));
                g2.setColor(Theme.TEXT);
                g2.drawString(nick, 54, 25);
                g2.setFont(Theme.font(11));
                g2.setColor(on ? Theme.GREEN : Theme.MUTED);
                g2.drawString((on ? "在线" : "离线") + " · " + dev, 54, 42);
                g2.dispose();
            }
        };
        bar.add(who, BorderLayout.CENTER);

        JPanel acts = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        acts.setOpaque(false);
        if (listCollapsed) {
            acts.add(new Theme.IconBtn("chevron-right", "展开会话列表", () -> toggleList()));
        }
        Theme.Btn remark = new Theme.Btn("备注", Theme.Kind.GHOST).small();
        remark.setToolTipText("给这个设备起一个本机显示的备注名");
        remark.addActionListener(e -> renamePeer(current));
        acts.add(remark);
        Theme.Btn file = new Theme.Btn("传文件", Theme.Kind.GHOST).small();
        file.addActionListener(e -> frame.openFilesFor(current));
        acts.add(file);
        bar.add(acts, BorderLayout.EAST);
        headBox.add(bar, BorderLayout.NORTH);
        headBox.revalidate();
        headBox.repaint();
    }

    private void rebuildMessages() {
        if (current == null) {
            return;
        }
        List<Node.ChatMsg> list = node.chatList(current);
        int sig = (current + "#" + list.size() + "#" + stateHash(list)).hashCode();
        if (sig == signature) {
            return;
        }
        signature = sig;
        msgBox.removeAll();
        if (list.isEmpty()) {
            JPanel empty = UI.empty("chat", "开始聊天吧", "消息只在局域网内点对点传输，不经过服务器");
            empty.setBorder(BorderFactory.createEmptyBorder(70, 0, 0, 0));
            msgBox.add(empty);
        } else {
            long lastTs = 0;
            for (Node.ChatMsg m : list) {
                if (m.ts - lastTs > 5 * 60 * 1000) {
                    msgBox.add(UI.vgap(6));
                    msgBox.add(TimeDivider.create(m.ts));
                    msgBox.add(UI.vgap(6));
                    lastTs = m.ts;
                }
                msgBox.add(new MessageRow(m));
                msgBox.add(UI.vgap(6));
            }
        }
        msgBox.revalidate();
        msgBox.repaint();
        SwingUtilities.invokeLater(this::scrollBottom);
    }

    private int stateHash(List<Node.ChatMsg> list) {
        int h = 1;
        for (Node.ChatMsg m : list) {
            h = h * 31 + (m.recalled ? 7 : 0) + (m.mid == null ? 0 : m.mid.hashCode());
        }
        return h;
    }

    private void scrollBottom() {
        JScrollBar bar = msgScroll.getVerticalScrollBar();
        bar.setValue(bar.getMaximum());
    }

    // ---------------------------------------------------------------- 会话行

    private class ConvRow extends JComponent {
        private final Peer p;
        private boolean hover;

        ConvRow(Peer p) {
            this.p = p;
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
            setPreferredSize(new Dimension(240, 58));
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
                    openWith(p.id);
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
                    JPopupMenu menu = new JPopupMenu();
                    JMenuItem rn = new JMenuItem("重命名备注");
                    rn.setFont(Theme.font(12));
                    rn.addActionListener(ev -> renamePeer(p.id));
                    menu.add(rn);
                    menu.addSeparator();
                    JMenuItem cl = new JMenuItem("清空聊天记录");
                    cl.setFont(Theme.font(12));
                    cl.addActionListener(ev -> clearHistory(p.id));
                    menu.add(cl);
                    JMenuItem dl = new JMenuItem("删除会话");
                    dl.setFont(Theme.font(12));
                    dl.setToolTipText("清空记录并从列表移除，适合删除改名后残留的旧条目");
                    dl.addActionListener(ev -> deleteConversation(p.id));
                    menu.add(dl);
                    menu.show(ConvRow.this, e.getX(), e.getY());
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            boolean sel = p.id.equals(current);
            Graphics2D g2 = Theme.aa(g);
            if (sel) {
                g2.setColor(Theme.ACCENT_SOFT);
                Theme.roundRect(g2, 0, 0, getWidth(), getHeight(), 10);
            } else if (hover) {
                g2.setColor(new Color(0xF6F8FC));
                Theme.roundRect(g2, 0, 0, getWidth(), getHeight(), 10);
            }
            g2.dispose();

            // 头像首字跟随备注名
            Icons.avatar(g, 10, 12, 34, node.initialOf(p.id), p.id, p.connected);
            Graphics2D g3 = Theme.aa(g);
            g3.setFont(sel ? Theme.bold(13) : Theme.font(13));
            g3.setColor(sel ? Theme.ACCENT : Theme.TEXT);
            FontMetrics fm = g3.getFontMetrics();
            int maxText = getWidth() - 74;
            g3.drawString(Theme.ellipsis(node.nameOf(p.id), fm, maxText), 54, 26);

            List<Node.ChatMsg> msgs = node.chatList(p.id);
            String preview;
            if (msgs.isEmpty()) {
                preview = p.connected ? "在线，可以开始聊天" : "离线";
            } else {
                Node.ChatMsg last = msgs.get(msgs.size() - 1);
                preview = (last.mine ? "我：" : "") + last.preview().replace("\n", " ");
            }
            g3.setFont(Theme.font(11));
            g3.setColor(Theme.MUTED);
            FontMetrics fm2 = g3.getFontMetrics();
            g3.drawString(Theme.ellipsis(preview, fm2, maxText), 54, 43);
            if (!msgs.isEmpty()) {
                g3.setFont(Theme.font(10));
                g3.setColor(new Color(0xAEB6C6));
                String time = Theme.timeText(msgs.get(msgs.size() - 1).ts);
                g3.drawString(time, getWidth() - g3.getFontMetrics().stringWidth(time) - 10, 18);
            }
            // 未读红圈（带条数）；正在看的会话不显示
            int unread = node.unreadOf(p.id);
            if (unread > 0 && !sel) {
                String txt = unread > 99 ? "99+" : String.valueOf(unread);
                g3.setFont(Theme.bold(10));
                FontMetrics fb = g3.getFontMetrics();
                int bh = 17;
                int bw = Math.max(bh, fb.stringWidth(txt) + 10);
                int bx = getWidth() - bw - 10;
                int by = 32;
                g3.setColor(new Color(0xF2404D));
                Theme.roundRect(g3, bx, by, bw, bh, bh / 2);
                g3.setColor(Color.WHITE);
                g3.drawString(txt, bx + (bw - fb.stringWidth(txt)) / 2,
                        by + (bh + fb.getAscent() - fb.getDescent()) / 2);
            }
            g3.dispose();
        }
    }

    /** 搜索结果行：会话名命中（hit == null）或聊天记录命中。 */
    private class SearchRow extends JComponent {
        private final String pid;
        private final Node.ChatHit hit;
        private boolean hover;

        SearchRow(String pid, Node.ChatHit hit) {
            this.pid = pid;
            this.hit = hit;
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
            setPreferredSize(new Dimension(240, 54));
            setAlignmentX(LEFT_ALIGNMENT);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setToolTipText(hit == null ? "打开会话" : "跳到这条消息");
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
                    openWith(SearchRow.this.pid, SearchRow.this.hit == null ? null : SearchRow.this.hit.mid);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.aa(g);
            if (hover) {
                g2.setColor(new Color(0xF4F7FD));
                Theme.roundRect(g2, 0, 0, getWidth(), getHeight(), 10);
            }
            g2.dispose();
            Peer peer = node.peers.get(pid);
            Icons.avatar(g, 8, 12, 30, node.initialOf(pid), pid, peer != null && peer.connected);

            Graphics2D g3 = Theme.aa(g);
            g3.setFont(Theme.bold(12));
            g3.setColor(Theme.TEXT);
            FontMetrics fm = g3.getFontMetrics();
            int right = 10;
            if (hit != null) {
                g3.setFont(Theme.font(10));
                String time = Theme.timeText(hit.ts);
                FontMetrics ftm = g3.getFontMetrics();
                g3.setColor(new Color(0xAEB6C6));
                g3.drawString(time, getWidth() - ftm.stringWidth(time) - 10, 20);
                right = ftm.stringWidth(time) + 16;
            }
            g3.setFont(Theme.bold(12));
            g3.setColor(Theme.TEXT);
            g3.drawString(Theme.ellipsis(node.nameOf(pid), fm, getWidth() - 50 - right), 46, 22);

            g3.setFont(Theme.font(11));
            g3.setColor(Theme.MUTED);
            FontMetrics fm2 = g3.getFontMetrics();
            String second;
            if (hit == null) {
                second = "会话 · " + (peer != null && peer.connected ? "在线" : "离线");
            } else {
                second = (hit.mine ? "我：" : "") + snippet(hit.text, searchField == null ? "" : searchField.text());
            }
            g3.drawString(Theme.ellipsis(second, fm2, getWidth() - 46 - 8), 46, 39);
            g3.dispose();
        }

        private String snippet(String text, String q) {
            String t = text.replace("\r", " ").replace("\n", " ");
            int idx = q == null || q.isEmpty() ? -1 : t.toLowerCase().indexOf(q.trim().toLowerCase());
            int start = idx > 10 ? idx - 8 : 0;
            String s = t.substring(Math.min(start, t.length()));
            s = Theme.ellipsis(s, Theme.fm(Theme.font(11)), getWidth() - 60);
            return start > 0 ? "…" + s : s;
        }
    }

    /** 时间分隔。 */
    static class TimeDivider extends JComponent {
        private final long ts;

        static TimeDivider create(long ts) {
            TimeDivider d = new TimeDivider(ts);
            d.setAlignmentX(0.5f);
            return d;
        }

        TimeDivider(long ts) {
            this.ts = ts;
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(120, 20);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(120, 20);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.aa(g);
            g2.setFont(Theme.font(10));
            g2.setColor(new Color(0xA7B0C0));
            String t = Theme.timeText(ts);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(t, (getWidth() - fm.stringWidth(t)) / 2, 14);
            g2.dispose();
        }
    }

    // ---------------------------------------------------------------- 消息行

    private class MessageRow extends JPanel {
        private final Node.ChatMsg m;
        /** 气泡上的鼠标动作（坐标已换算到气泡坐标系）。 */
        private interface BubbleMouse {
            void press(MouseEvent e, java.awt.Point p);

            void release(MouseEvent e, java.awt.Point p);

            void click(MouseEvent e, java.awt.Point p);
        }

        private final Timer hold;
        private boolean longFired;
        private Bubble bubble;

        void setHighlight(boolean v) {
            if (bubble != null) {
                bubble.setHighlight(v);
            }
            repaint();
        }

        MessageRow(final Node.ChatMsg msg) {
            this.m = msg;
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setAlignmentX(LEFT_ALIGNMENT);

            if (m.recalled) {
                JComponent tip = new JComponent() {
                    @Override
                    public Dimension getPreferredSize() {
                        return new Dimension(240, 30);
                    }

                    @Override
                    public Dimension getMaximumSize() {
                        return new Dimension(Integer.MAX_VALUE, 30);
                    }

                    @Override
                    protected void paintComponent(Graphics g) {
                        Graphics2D g2 = Theme.aa(g);
                        g2.setFont(Theme.font(12));
                        String t = m.mine ? "你撤回了一条消息" : "对方撤回了一条消息";
                        FontMetrics fm = g2.getFontMetrics();
                        int bw = fm.stringWidth(t) + 26;
                        int bh = Math.min(getHeight() - 2, 24);
                        int bx = (getWidth() - bw) / 2;
                        int by = (getHeight() - bh) / 2;
                        g2.setColor(new Color(0xE5E9F2));
                        Theme.roundRect(g2, bx, by, bw, bh, bh / 2);
                        g2.setColor(new Color(0x77829A));
                        int ty = by + (bh + fm.getAscent() - fm.getDescent()) / 2;
                        g2.drawString(t, bx + (bw - fm.stringWidth(t)) / 2, ty);
                        g2.dispose();
                    }
                };
                add(tip);
                hold = null;
                return;
            }

            int area = msgBox.getWidth() > 0 ? msgBox.getWidth() : 640;
            int maxW = Math.max(240, Math.min(620, area - 150));
            bubble = new Bubble(m, maxW);

            JComponent time = new JComponent() {
                @Override
                public Dimension getPreferredSize() {
                    FontMetrics fm = Theme.fm(Theme.font(10));
                    return new Dimension(fm.stringWidth(Theme.timeText(m.ts)) + 8, 18);
                }

                @Override
                public Dimension getMaximumSize() {
                    return getPreferredSize();
                }

                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = Theme.aa(g);
                    g2.setFont(Theme.font(10));
                    g2.setColor(new Color(0xB2BAC8));
                    g2.drawString(Theme.timeText(m.ts), 4, getHeight() - 6);
                    g2.dispose();
                }
            };

            if (m.mine) {
                add(Box.createHorizontalGlue());
                add(time);
                add(Box.createRigidArea(new Dimension(8, 1)));
                add(bubble);
            } else {
                JComponent avatar = new JComponent() {
                    @Override
                    public Dimension getPreferredSize() {
                        return new Dimension(34, 34);
                    }

                    @Override
                    public Dimension getMaximumSize() {
                        return getPreferredSize();
                    }

                    @Override
                    protected void paintComponent(Graphics g) {
                        Icons.avatar(g, 0, 2, 30, node.initialOf(current), current, false);
                    }
                };
                add(avatar);
                add(Box.createRigidArea(new Dimension(8, 1)));
                add(bubble);
                add(Box.createRigidArea(new Dimension(8, 1)));
                add(time);
                add(Box.createHorizontalGlue());
            }

            hold = new Timer(550, e -> {
                longFired = true;
                showMenu(bubble, 10, 10);
            });
            hold.setRepeats(false);

            final java.util.concurrent.atomic.AtomicLong lastMenu =
                    new java.util.concurrent.atomic.AtomicLong(0);
            BubbleMouse handler = new BubbleMouse() {
                @Override
                public void press(MouseEvent e, java.awt.Point p) {
                    longFired = false;
                    if (isMenuTrigger(e)) {
                        maybeMenu(bubble, p, lastMenu);
                        return;
                    }
                    hold.restart();
                }

                @Override
                public void release(MouseEvent e, java.awt.Point p) {
                    if (hold != null) {
                        hold.stop();
                    }
                    // Windows 下右键的 popup 触发点在 released 阶段
                    if (isMenuTrigger(e)) {
                        maybeMenu(bubble, p, lastMenu);
                    }
                }

                @Override
                public void click(MouseEvent e, java.awt.Point p) {
                    if (longFired) {
                        longFired = false;
                        return;
                    }
                    if (m.voice) {
                        playVoice(m);
                    }
                }
            };
            attachBubbleMouse(bubble, bubble, handler);
        }

        /** 是否应弹出菜单（右键 / 系统 popup 触发）。 */
        private boolean isMenuTrigger(MouseEvent e) {
            return e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e);
        }

        /** 同一次右键会在 press/release 各触发一次，用时间戳去重。 */
        private void maybeMenu(JComponent bubble, java.awt.Point p,
                               java.util.concurrent.atomic.AtomicLong last) {
            long now = System.currentTimeMillis();
            if (now - last.get() < 350) {
                return;
            }
            last.set(now);
            showMenu(bubble, Math.max(4, p.x), Math.max(4, p.y));
        }

        /** 把鼠标处理递归挂到气泡内的所有子组件上（文本区、语音条等会吞事件）。 */
        private void attachBubbleMouse(final JComponent bubble, JComponent c,
                                       final BubbleMouse h) {
            c.addMouseListener(new MouseAdapter() {
                private java.awt.Point at(MouseEvent e) {
                    java.awt.Component src = (java.awt.Component) e.getSource();
                    return SwingUtilities.convertPoint(src, e.getX(), e.getY(), bubble);
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    h.press(e, at(e));
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    h.release(e, at(e));
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    h.click(e, at(e));
                }
            });
            for (java.awt.Component child : c.getComponents()) {
                if (child instanceof JComponent) {
                    attachBubbleMouse(bubble, (JComponent) child, h);
                }
            }
        }

        private void showMenu(JComponent src, int x, int y) {
            JPopupMenu menu = new JPopupMenu();
            if (!m.recalled) {
                JMenuItem quote = new JMenuItem("引用这条消息");
                quote.setFont(Theme.font(12));
                quote.addActionListener(e -> setQuote(m));
                menu.add(quote);
            }
            if (m.voice && !m.recalled) {
                JMenuItem play = new JMenuItem("播放语音");
                play.setFont(Theme.font(12));
                play.addActionListener(e -> playVoice(m));
                menu.add(play);
            }
            if (!m.voice && !m.recalled) {
                JMenuItem copy = new JMenuItem("复制文字");
                copy.setFont(Theme.font(12));
                copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(m.text), null));
                menu.add(copy);
            }
            if (!m.recalled && m.mine) {
                int left = node.recallLeft(current, m.mid);
                JMenuItem recall = new JMenuItem(left > 0 ? "撤回（" + left + "s）" : "撤回（已超时）");
                recall.setFont(Theme.font(12));
                recall.setEnabled(left > 0);
                recall.addActionListener(e -> node.recallChat(current, m.mid));
                menu.add(recall);
            }
            menu.show(src, x, y);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    // ---------------------------------------------------------------- 气泡

    private class Bubble extends JPanel {
        private final Node.ChatMsg m;
        private final boolean mine;
        /** 搜索定位后的临时高亮描边。 */
        private boolean highlight;

        Bubble(Node.ChatMsg msg, int maxW) {
            this.m = msg;
            this.mine = msg.mine;
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(9, 12, 9, 12));
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            int inner = Math.max(80, maxW - 24);
            int contentW;
            JComponent body;
            if (m.voice) {
                contentW = Math.min(inner, 110 + Math.min(m.voiceDur, 40) * 4);
                body = new VoiceBody(contentW, m.voiceDur);
            } else {
                JTextArea ta = new JTextArea(m.text);
                ta.setEditable(false);
                ta.setFocusable(false);
                ta.setOpaque(false);
                ta.setLineWrap(true);
                ta.setWrapStyleWord(true);
                ta.setFont(Theme.font(13));
                ta.setForeground(mine ? Color.WHITE : new Color(0x27303F));
                ta.setBorder(null);
                FontMetrics fm = Theme.fm(ta.getFont());
                int natural = 0;
                for (String line : m.text.split("\n", -1)) {
                    natural = Math.max(natural, fm.stringWidth(line));
                }
                // 短文本留出 12px 余量，避免 4 个汉字被折成两行
                contentW = Math.min(inner, Math.max(34, natural + 12));
                ta.setSize(new Dimension(contentW, 4096));
                Dimension d = ta.getPreferredSize();
                ta.setPreferredSize(new Dimension(contentW, d.height));
                ta.setMaximumSize(new Dimension(contentW, d.height));
                ta.setAlignmentX(LEFT_ALIGNMENT);
                body = ta;
            }

            add(body);
            if (!m.quoteText.isEmpty()) {
                add(Box.createRigidArea(new Dimension(1, 7)));
                add(new QuoteBlock(contentW));
            }

            int w = contentW + 24;
            int h = getPreferredSize().height;
            setPreferredSize(new Dimension(w, h));
            setMinimumSize(new Dimension(w, h));
            setMaximumSize(new Dimension(w, h));
        }

        void setHighlight(boolean v) {
            if (highlight != v) {
                highlight = v;
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Theme.aa(g);
            if (mine) {
                g2.setPaint(new java.awt.GradientPaint(0, 0, new Color(0x3D7BFF),
                        getWidth(), getHeight(), new Color(0x2F62E8)));
            } else {
                g2.setColor(new Color(0xF1F4FA));
            }
            Theme.roundRect(g2, 0, 0, getWidth(), getHeight(), 14);
            if (highlight) {
                g2.setColor(new Color(0xF2A33C));
                g2.setStroke(new BasicStroke(2f));
                Theme.border(g2, 1, 1, getWidth() - 1, getHeight() - 1, 14);
                // 左侧标记条，滚动时更醒目
                g2.fillRoundRect(0, 6, 3, getHeight() - 12, 3, 3);
            }
            g2.dispose();
            super.paintComponent(g);
        }

        /** 引用块。 */
        private class QuoteBlock extends JComponent {
            private final int w;

            QuoteBlock(int w) {
                this.w = w;
                setOpaque(false);
                setAlignmentX(LEFT_ALIGNMENT);
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(w, 34);
            }

            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.aa(g);
                g2.setColor(mine ? new Color(255, 255, 255, 46) : new Color(0xE4E9F3));
                Theme.roundRect(g2, 0, 0, getWidth(), getHeight(), 8);
                g2.setColor(mine ? new Color(255, 255, 255, 165) : new Color(0xAEBACF));
                g2.fillRoundRect(0, 5, 3, getHeight() - 10, 3, 3);

                g2.setFont(Theme.bold(10));
                g2.setColor(mine ? new Color(255, 255, 255, 220) : new Color(0x6B7488));
                FontMetrics fm = g2.getFontMetrics();
                String who = m.quoteName.isEmpty() ? "引用" : m.quoteName;
                g2.drawString(Theme.ellipsis(who, fm, getWidth() - 20), 11, 14);

                g2.setFont(Theme.font(11));
                g2.setColor(mine ? new Color(255, 255, 255, 190) : new Color(0x7D879C));
                FontMetrics fm2 = g2.getFontMetrics();
                g2.drawString(Theme.ellipsis(m.quoteText, fm2, getWidth() - 20), 11, 27);
                g2.dispose();
            }
        }

        /** 语音条。 */
        private class VoiceBody extends JComponent {
            private final int w;
            private final int dur;

            VoiceBody(int w, int dur) {
                this.w = w;
                this.dur = dur;
                setOpaque(false);
                setAlignmentX(LEFT_ALIGNMENT);
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(w, 30);
            }

            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Theme.aa(g);
                boolean playing = m.mid.equals(playingMid);
                Color fg = mine ? Color.WHITE : Theme.ACCENT;
                g2.setColor(fg);
                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                if (playing) {
                    g2.fillRoundRect(1, 7, 4, 14, 2, 2);
                    g2.fillRoundRect(8, 7, 4, 14, 2, 2);
                } else {
                    java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
                    p.moveTo(2, 6);
                    p.lineTo(13, 14);
                    p.lineTo(2, 22);
                    p.closePath();
                    g2.fill(p);
                }

                int bars = 7;
                int x0 = 24;
                int gap = 4;
                for (int i = 0; i < bars; i++) {
                    int hgt = 6 + ((i * 7) % 12);
                    g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 90));
                    g2.fillRoundRect(x0 + i * gap, (30 - hgt) / 2, 2, hgt, 2, 2);
                }

                g2.setFont(Theme.bold(11));
                g2.setColor(fg);
                String t = dur / 60 + ":" + String.format("%02d", dur % 60);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(t, getWidth() - fm.stringWidth(t) - 2, 20);
                g2.dispose();
            }
        }
    }

    private void playVoice(Node.ChatMsg m) {
        if (m.voicePath == null || m.voicePath.isEmpty() || !new File(m.voicePath).exists()) {
            frame.toast("语音文件不存在");
            return;
        }
        if (clip != null && clip.isRunning()) {
            clip.stop();
            clip.close();
            clip = null;
        }
        if (m.mid.equals(playingMid)) {
            playingMid = "";
            msgBox.repaint();
            return;
        }
        playingMid = m.mid;
        msgBox.repaint();
        clip = Voice.play(new File(m.voicePath), () -> SwingUtilities.invokeLater(() -> {
            playingMid = "";
            msgBox.repaint();
        }));
        if (clip == null) {
            playingMid = "";
            msgBox.repaint();
            frame.toast("无法播放这段语音");
        }
    }
}
