package com.wlanshare.ui;

import com.wlanshare.core.Doc;
import com.wlanshare.core.DocMerge;
import com.wlanshare.core.Node;
import com.wlanshare.core.Peer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/** 文档：Markdown 协作编辑，左侧列表 + 右侧编辑/预览。 */
public class DocsPanel implements Section {

    private final MainFrame frame;
    private final Node node;
    private final JPanel view = new JPanel(new BorderLayout(16, 0));
    private final JPanel listBox = new JPanel();
    private final JPanel editorCard = new Theme.Card();
    private final JPanel emptyCard = new Theme.Card();
    private final EditorArea editor = new EditorArea();
    private final JEditorPane preview = new JEditorPane();
    private final JTextField titleField = new JTextField();
    private final JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final javax.swing.JLabel status = Theme.label("", 11, Theme.MUTED, false);
    private Theme.Card leftCard;
    private boolean listCollapsed;
    private JSplitPane split;
    private JPanel edWrap;
    private JPanel pvWrap;
    private Theme.Segmented modes;
    private Theme.IconBtn expandBtn;
    private JPanel rightHost;
    private String current;
    private boolean applying;
    private long lastLocalEdit;
    private Timer saveTimer;
    private Timer previewTimer;
    /** 标题输入的防抖定时器，以及「输入框里当前展示的是哪份文档的标题」。 */
    private Timer renameTimer;
    private String titleShownFor;
    /** 用户是否正在编辑标题（编辑期间不接受程序化回灌）。 */
    private boolean titleEditing;
    /** 协作光标：右侧名字标签、光标行、节流发送。 */
    private CursorGutter gutter;
    private Timer markTimer;
    private Timer cursorTimer;
    private int lastLine = -1;
    private String cursorDoc;

    public DocsPanel(MainFrame frame, Node node) {
        this.frame = frame;
        this.node = node;
        view.setOpaque(false);

        // ---- 左列表
        leftCard = new Theme.Card();
        leftCard.setLayout(new BorderLayout());
        leftCard.setPreferredSize(new Dimension(252, 100));
        leftCard.setBorder(BorderFactory.createEmptyBorder(12, 10, 10, 10));
        Theme.Btn add = new Theme.Btn("新建", Theme.Kind.SOFT).small();
        add.addActionListener(e -> newDoc());
        JPanel lh = new JPanel(new BorderLayout(6, 0));
        lh.setOpaque(false);
        lh.setBorder(BorderFactory.createEmptyBorder(0, 6, 10, 4));
        lh.add(Theme.label("协作文档", 13, Theme.TEXT, true), BorderLayout.WEST);
        JPanel lhRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        lhRight.setOpaque(false);
        lhRight.add(add);
        lhRight.add(new Theme.IconBtn("chevron-left", "收起文档列表", () -> toggleList()));
        lh.add(lhRight, BorderLayout.EAST);
        leftCard.add(lh, BorderLayout.NORTH);
        listBox.setOpaque(false);
        listBox.setLayout(new BoxLayout(listBox, BoxLayout.Y_AXIS));
        JPanel lw = new JPanel(new BorderLayout());
        lw.setOpaque(false);
        lw.add(listBox, BorderLayout.NORTH);
        leftCard.add(new Theme.SlimScroll(lw), BorderLayout.CENTER);
        view.add(leftCard, BorderLayout.WEST);

        // ---- 右编辑区
        editorCard.setLayout(new BorderLayout(0, 10));
        editorCard.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));

        JPanel head = new JPanel(new BorderLayout(10, 0));
        head.setOpaque(false);
        titleField.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        titleField.setOpaque(false);
        titleField.setFont(Theme.bold(17));
        titleField.setForeground(Theme.TEXT);
        titleField.setCaretColor(Theme.ACCENT);
        Theme.styleText(titleField);
        titleField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                rename();
            }

            public void removeUpdate(DocumentEvent e) {
                rename();
            }

            public void changedUpdate(DocumentEvent e) {
            }
        });
        // 离开输入框 / 回车时提交标题；把标题清空后走人则还原成原名
        titleField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                commitTitle();
            }
        });
        titleField.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("ENTER"), "commit-title");
        titleField.getActionMap().put("commit-title", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                commitTitle();
                editor.requestFocusInWindow();
            }
        });
        titleField.getInputMap().put(javax.swing.KeyStroke.getKeyStroke("ESCAPE"), "restore-title");
        titleField.getActionMap().put("restore-title", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                restoreTitle();
                editor.requestFocusInWindow();
            }
        });
        head.add(titleField, BorderLayout.CENTER);
        chips.setOpaque(false);
        head.add(chips, BorderLayout.SOUTH);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        expandBtn = new Theme.IconBtn("chevron-right", "展开文档列表", () -> toggleList());
        expandBtn.setVisible(false);
        actions.add(expandBtn);
        Theme.Btn share = new Theme.Btn("共享", Theme.Kind.GHOST).small();
        share.addActionListener(e -> shareMenu(share));
        Theme.Btn del = new Theme.Btn("删除", Theme.Kind.GHOST).small();
        del.addActionListener(e -> deleteDoc());
        actions.add(share);
        actions.add(del);
        head.add(actions, BorderLayout.EAST);
        editorCard.add(head, BorderLayout.NORTH);

        editor.setFont(new java.awt.Font("Microsoft YaHei UI", java.awt.Font.PLAIN, 14));
        editor.setLineWrap(true);
        editor.setWrapStyleWord(true);
        editor.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        editor.setBackground(new Color(0xFBFCFE));
        editor.setForeground(new Color(0x1F2937));
        editor.setCaretColor(Theme.ACCENT);
        Theme.styleText(editor);
        editor.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                onLocalEdit();
            }

            public void removeUpdate(DocumentEvent e) {
                onLocalEdit();
            }

            public void changedUpdate(DocumentEvent e) {
            }
        });

        // 光标移动 -> 节流广播所在行
        editor.addCaretListener(e -> {
            if (applying || current == null) {
                return;
            }
            int line = lineOf(editor.getText(), editor.getCaretPosition());
            if (line == lastLine) {
                return;
            }
            lastLine = line;
            if (cursorTimer == null) {
                cursorTimer = new Timer(260, ev -> {
                    if (current != null) {
                        node.sendDocCursor(current, lastLine);
                    }
                });
                cursorTimer.setRepeats(false);
            }
            cursorTimer.restart();
        });

        // 定时重画他人光标标记（远端行号会变）
        markTimer = new Timer(280, e -> applyCursorMarks());
        markTimer.start();

        preview.setContentType("text/html");
        preview.setEditable(false);
        preview.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        preview.setBackground(Color.WHITE);
        preview.setOpaque(true);
        preview.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        Theme.styleText(preview);

        edWrap = new JPanel(new BorderLayout());
        edWrap.setOpaque(false);
        edWrap.setBorder(BorderFactory.createLineBorder(Theme.BORDER2));
        JPanel barWrap = new JPanel(new BorderLayout());
        barWrap.setOpaque(false);
        barWrap.setBorder(BorderFactory.createEmptyBorder(3, 4, 2, 2));
        barWrap.add(new MdBar(editor), BorderLayout.CENTER);
        edWrap.add(barWrap, BorderLayout.NORTH);
        JPanel edBody = new JPanel(new BorderLayout());
        edBody.setOpaque(false);
        edBody.add(new Theme.SlimScroll(editor), BorderLayout.CENTER);
        gutter = new CursorGutter();
        edBody.add(gutter, BorderLayout.EAST);
        edWrap.add(edBody, BorderLayout.CENTER);

        // 预览：浅灰画布 + 白色「纸张」卡片，阅读感更接近现代文档应用
        Theme.SlimScroll pvScroll = new Theme.SlimScroll(preview);
        pvScroll.setOpaque(false);
        pvScroll.getViewport().setOpaque(false);
        Theme.Card page = new Theme.Card();
        page.radius(16);
        page.noStroke();
        page.setLayout(new BorderLayout());
        page.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        page.add(pvScroll, BorderLayout.CENTER);

        JPanel pvHost = new JPanel(new BorderLayout());
        pvHost.setOpaque(false);
        pvHost.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        pvHost.add(page, BorderLayout.CENTER);

        pvWrap = new JPanel(new BorderLayout());
        pvWrap.setOpaque(true);
        pvWrap.setBackground(new Color(0xF1F3F9));
        pvWrap.setBorder(BorderFactory.createLineBorder(new Color(0xE9EDF5)));
        pvWrap.add(pvHost, BorderLayout.CENTER);

        split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, edWrap, pvWrap);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setDividerSize(8);
        split.setResizeWeight(0.46);
        split.setOpaque(false);
        split.setContinuousLayout(true);
        SwingUtilities.invokeLater(() -> split.setDividerLocation(0.46));
        editorCard.add(split, BorderLayout.CENTER);

        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(6, 2, 0, 2));
        bar.add(status, BorderLayout.WEST);
        modes = new Theme.Segmented("编辑", "分栏", "预览");
        modes.setSelected(1);
        modes.onChange(i -> applyMode());
        JPanel rt = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rt.setOpaque(false);
        rt.add(Theme.label("Markdown", 10, Theme.MUTED, false));
        rt.add(modes);
        bar.add(rt, BorderLayout.EAST);
        editorCard.add(bar, BorderLayout.SOUTH);

        emptyCard.setLayout(new BorderLayout());
        JComponent empty = UI.empty("doc", "选择或新建一份文档", "文档会实时同步给好友，双方可以同时编辑");
        // BoxLayout 会把空态的图标和文字拉散（一个贴顶一个贴底），这里用 GridBag 居中
        JPanel emptyWrap = new JPanel(new java.awt.GridBagLayout());
        emptyWrap.setOpaque(false);
        emptyWrap.add(empty);
        emptyCard.add(emptyWrap, BorderLayout.CENTER);

        rightHost = new JPanel(new java.awt.CardLayout());
        rightHost.setOpaque(false);
        rightHost.add(editorCard, "editor");
        rightHost.add(emptyCard, "empty");
        view.add(rightHost, BorderLayout.CENTER);

        saveTimer = new Timer(420, e -> pushDoc());
        saveTimer.setRepeats(false);
        previewTimer = new Timer(260, e -> renderPreview());
        previewTimer.setRepeats(false);
    }

    @Override
    public JComponent view() {
        return view;
    }

    @Override
    public void onShow() {
        rebuildList();
        showDoc();
    }

    @Override
    public void refresh(String what) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> refresh(what));
            return;
        }
        if (what != null && what.contains("doc.cursor") && !what.contains("docs")) {
            applyCursorMarks();
            updateChips(node.doc(current));
            return;
        }
        rebuildList();
        showDoc();
        updateStatus();
    }

    /** 收起 / 展开左侧文档列表，让编辑区占满。 */
    private void toggleList() {
        listCollapsed = !listCollapsed;
        leftCard.setVisible(!listCollapsed);
        if (expandBtn != null) {
            expandBtn.setVisible(listCollapsed);
        }
        view.revalidate();
        view.repaint();
        SwingUtilities.invokeLater(this::applyMode);
    }

    /** 0 = 仅编辑，1 = 分栏，2 = 仅预览。 */
    private void applyMode() {
        if (split == null) {
            return;
        }
        int m = modes == null ? 1 : modes.selected();
        if (m == 0) {
            split.setDividerSize(0);
            split.setRightComponent(null);
            split.setLeftComponent(edWrap);
        } else if (m == 2) {
            split.setDividerSize(0);
            split.setLeftComponent(null);
            split.setRightComponent(pvWrap);
            renderPreview();
        } else {
            if (split.getLeftComponent() != edWrap) {
                split.setLeftComponent(edWrap);
            }
            if (split.getRightComponent() != pvWrap) {
                split.setRightComponent(pvWrap);
            }
            split.setDividerSize(8);
            SwingUtilities.invokeLater(() -> split.setDividerLocation(0.46));
        }
        split.revalidate();
        split.repaint();
        editorCard.revalidate();
        editorCard.repaint();
    }

    private void newDoc() {
        Doc d = node.createDoc("未命名文档");
        current = d.did;
        rebuildList();
        showDoc();
        SwingUtilities.invokeLater(() -> {
            titleField.requestFocusInWindow();
            titleField.selectAll();
        });
    }

    private void rebuildList() {
        listBox.removeAll();
        List<Doc> docs = node.docList();
        if (docs.isEmpty()) {
            JPanel empty = UI.empty("doc", "还没有文档", "点击「新建」创建，或等待好友共享");
            empty.setBorder(BorderFactory.createEmptyBorder(40, 0, 0, 0));
            listBox.add(empty);
        } else {
            for (Doc d : docs) {
                listBox.add(new DocRow(d));
                listBox.add(UI.vgap(2));
            }
        }
        if (current == null && !docs.isEmpty()) {
            current = docs.get(0).did;
        }
        if (current != null && node.doc(current) == null) {
            current = docs.isEmpty() ? null : docs.get(0).did;
        }
        listBox.revalidate();
        listBox.repaint();
    }

    private void showDoc() {
        if (cursorDoc != null && !cursorDoc.equals(current)) {
            node.sendDocCursor(cursorDoc, -1);
            cursorDoc = null;
            lastLine = -1;
        }
        Doc d = current == null ? null : node.doc(current);
        if (rightHost != null) {
            ((java.awt.CardLayout) rightHost.getLayout()).show(rightHost, d == null ? "empty" : "editor");
        }
        if (d == null) {
            // 没有文档可显示：标题框 / 状态栏一起清空，别留下上一份文档的名字
            titleShownFor = null;
            titleEditing = false;
            if (!titleField.getText().isEmpty()) {
                setTitleText("");
            }
            status.setText("");
            view.revalidate();
            view.repaint();
            return;
        }
        view.revalidate();
        view.repaint();

        // 切换文档时一定同步标题；同一份文档在用户输入过程中不回灌，避免把正在清空的输入框填回去
        boolean switched = !d.did.equals(titleShownFor);
        if (switched) {
            titleEditing = false;
            setTitleText(d.title);
        } else if (!titleEditing && !titleField.getText().equals(d.title)) {
            setTitleText(d.title);
        }
        boolean idle = System.currentTimeMillis() - lastLocalEdit > 900;
        if (idle && !editor.getText().equals(d.text)) {
            applying = true;
            int caret = editor.getCaretPosition();
            editor.setText(d.text);
            try {
                editor.setCaretPosition(Math.min(caret, d.text.length()));
            } catch (Exception ignored) {
            }
            applying = false;
            renderPreview();
        }
        updateChips(d);
        updateStatus();
        // 打开文档后先广播一次当前光标行，对方立刻就能看到我的位置
        if (current != null && d != null) {
            lastLine = lineOf(editor.getText(), editor.getCaretPosition());
            node.sendDocCursor(current, lastLine);
        }
    }

    /**
     * 编辑器：文字与选区完全交给 Swing 标准绘制（深色选中文字保证清晰可读），
     * 协作行标记只画在行首的竖条上，绘制在文字之后、位于行首空白处，不遮挡任何内容。
     */
    private class EditorArea extends JTextArea {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = Theme.aa(g);
            paintPeerBars(g2);
            g2.dispose();
        }
    }

    /** 在每行行首绘制他人光标所在的彩色竖条。 */
    private void paintPeerBars(Graphics2D g2) {
        Doc d = current == null ? null : node.doc(current);
        if (d == null || d.cursors.isEmpty()) {
            return;
        }
        String text = editor.getText();
        for (java.util.Map.Entry<String, Integer> e : d.cursors.entrySet()) {
            String pid = e.getKey();
            if (pid.equals(node.id)) {
                continue;
            }
            int off = CursorMarks.lineOffset(text, e.getValue());
            java.awt.geom.Rectangle2D r;
            try {
                r = editor.modelToView2D(off);
            } catch (Exception ex) {
                continue;
            }
            if (r == null) {
                continue;
            }
            int y = (int) r.getY();
            int h = (int) r.getHeight();
            if (y + h < 0 || y > editor.getHeight()) {
                continue;
            }
            // 只在行首画一条竖条：铺整行底色会与 Swing 的选区绘制冲突，导致选中文字被冲淡
            g2.setColor(CursorMarks.color(pid));
            g2.fillRoundRect(3, y + 1, 4, Math.max(4, h - 2), 2, 2);
        }
    }

    /** 行号（0 基）。 */
    private static int lineOf(String text, int off) {
        int line = 0;
        int p = Math.min(off, text.length());
        for (int i = 0; i < p; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /** 重画其他协作者的光标行：行背景 + 右侧名字。 */
    private void applyCursorMarks() {
        if (editor == null) {
            return;
        }
        Doc d = current == null ? null : node.doc(current);
        if (d == null) {
            return;
        }
        if (current != null && !current.equals(cursorDoc)) {
            cursorDoc = current;
        }
        // 行背景由 EditorArea 在文字下方绘制（Highlighter 会盖住文字），这里只触发重绘
        editor.repaint();
        if (gutter != null) {
            gutter.repaint();
        }
    }

    /** 取名字的主体部分（去掉括号备注），过长再截断。 */
    private static String shortName(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        int cut = t.length();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '（' || c == '(' || c == '·') {
                cut = i;
                break;
            }
        }
        String head = t.substring(0, cut).trim();
        if (head.isEmpty()) {
            head = t;
        }
        return head.length() <= 4 ? head : head.substring(0, 4);
    }

    /** 编辑器右侧的名字标签：显示谁的光标停在哪一行。 */
    private class CursorGutter extends JComponent {
        CursorGutter() {
            setOpaque(false);
            setPreferredSize(new Dimension(78, 80));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Doc d = current == null ? null : node.doc(current);
            if (d == null || d.cursors.isEmpty()) {
                return;
            }
            Graphics2D g2 = Theme.aa(g);
            java.awt.Point origin = SwingUtilities.convertPoint(editor, 0, 0, this);
            String text = editor.getText();
            g2.setFont(Theme.font(10));
            FontMetrics fm = Theme.fm(Theme.font(10));
            for (java.util.Map.Entry<String, Integer> e : d.cursors.entrySet()) {
                String pid = e.getKey();
                if (pid.equals(node.id)) {
                    continue;
                }
                int off = CursorMarks.lineOffset(text, e.getValue());
                java.awt.geom.Rectangle2D r;
                try {
                    r = editor.modelToView2D(off);
                } catch (Exception ex) {
                    continue;
                }
                if (r == null) {
                    continue;
                }
                int y = origin.y + (int) r.getY();
                int h = Math.max(17, (int) r.getHeight());
                if (y + h < 0 || y > getHeight()) {
                    continue;
                }
                String name = shortName(node.nameOf(pid));
                Color c = CursorMarks.color(pid);
                int w = Math.min(getWidth() - 3, fm.stringWidth(name) + 15);
                int bh = Math.min(h, 18);
                int by = y + (h - bh) / 2;
                Color fill = new Color(c.getRed(), c.getGreen(), c.getBlue(), 26);
                g2.setColor(fill);
                Theme.roundRect(g2, 1, by, w, bh, 6);
                g2.setColor(c);
                g2.fillRoundRect(1, by + 2, 3, bh - 4, 2, 2);
                g2.drawString(Theme.ellipsis(name, fm, w - 10), 7,
                        by + (bh + fm.getAscent() - fm.getDescent()) / 2);
            }
            g2.dispose();
        }
    }

    private void updateChips(Doc d) {
        chips.removeAll();
        if (d.editors.isEmpty()) {
            chips.add(Theme.label("仅自己", 10, Theme.MUTED, false));
        } else {
            int i = 0;
            for (String pid : d.editors.keySet()) {
                if (i++ >= 3) {
                    break;
                }
                javax.swing.JLabel l = Theme.label("● " + node.nameOf(pid) + " 正在编辑", 10, Theme.GREEN, true);
                l.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
                chips.add(l);
            }
        }
        chips.add(Theme.label("版本 " + d.ver, 10, new Color(0xB0B8C8), false));
        chips.revalidate();
        chips.repaint();
    }

    private void updateStatus() {
        Doc d = current == null ? null : node.doc(current);
        if (d == null) {
            return;
        }
        String conflict = DocMerge.hasConflict(d.text) ? " · 存在冲突需手动处理" : "";
        status.setText(d.wordCount() + " 字 · " + d.lineCount() + " 行 · 更新于 " + Theme.timeText(d.updatedAt) + conflict);
        status.setForeground(conflict.isEmpty() ? Theme.MUTED : Theme.AMBER);
    }

    private void rename() {
        if (applying || current == null) {
            return;
        }
        // 用户正在编辑标题：此时不回灌任何程序化标题（否则清空旧名会被「未命名文档」立刻填回去）
        titleEditing = true;
        if (titleField.getText().trim().isEmpty()) {
            return;
        }
        if (renameTimer == null) {
            renameTimer = new Timer(320, e -> commitTitle());
            renameTimer.setRepeats(false);
        }
        renameTimer.restart();
    }

    /** 提交标题：空标题还原原名，避免出现「无名文档」。 */
    private void commitTitle() {
        if (renameTimer != null) {
            renameTimer.stop();
        }
        Doc d = current == null ? null : node.doc(current);
        if (d == null) {
            titleEditing = false;
            return;
        }
        String t = titleField.getText().trim();
        if (t.isEmpty()) {
            restoreTitle();
            return;
        }
        titleEditing = false;
        if (t.equals(d.title)) {
            return;
        }
        node.renameDoc(current, t);
    }

    /** 把输入框里的标题还原成文档当前的名字。 */
    private void restoreTitle() {
        Doc d = current == null ? null : node.doc(current);
        if (d == null) {
            return;
        }
        setTitleText(d.title);
    }

    /** 程序化写入标题（不会被当成用户输入）。 */
    private void setTitleText(String title) {
        boolean was = applying;
        applying = true;
        titleField.setText(title);
        titleShownFor = current;
        applying = was;
        titleEditing = false;
    }

    private void onLocalEdit() {
        if (applying || current == null) {
            return;
        }
        lastLocalEdit = System.currentTimeMillis();
        previewTimer.restart();
        saveTimer.restart();
        Doc d = node.doc(current);
        if (d != null) {
            status.setText(editor.getText().length() + " 字符 · 正在同步…");
            status.setForeground(Theme.ACCENT);
        }
    }

    private void pushDoc() {
        if (current == null) {
            return;
        }
        node.updateDocText(current, editor.getText());
        SwingUtilities.invokeLater(this::updateStatus);
    }

    private void renderPreview() {
        String text = editor.getText();
        try {
            preview.setText(Markdown.toHtml(text));
            preview.setCaretPosition(0);
        } catch (Exception ignored) {
        }
    }

    private void shareMenu(JComponent anchor) {
        if (current == null) {
            return;
        }
        List<Peer> friends = node.friendPeerList();
        if (friends.isEmpty()) {
            frame.toast("还没有好友，无法共享");
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        for (Peer p : friends) {
            final String pname = node.nameOf(p.id);
            javax.swing.JMenuItem it = new javax.swing.JMenuItem("共享给 " + pname);
            it.setFont(Theme.font(12));
            it.addActionListener(e -> {
                node.shareDoc(current, p.id);
                frame.toast("已共享给 " + pname);
            });
            menu.add(it);
        }
        menu.show(anchor, 0, anchor.getHeight());
    }

    private void deleteDoc() {
        if (current == null) {
            return;
        }
        Doc d = node.doc(current);
        if (d == null) {
            return;
        }
        if (!UI.confirm(view, "删除文档",
                "《" + d.title + "》将从本机删除，其它设备上的副本不受影响，此操作无法撤销。", "删除")) {
            return;
        }
        node.deleteDoc(current);
        current = null;
        titleShownFor = null;
        titleEditing = false;
        rebuildList();
        showDoc();
        updateStatus();
    }

    private void open(String did) {
        current = did;
        lastLocalEdit = 0;
        node.awareDoc(did, false);
        rebuildList();
        showDoc();
        renderPreview();
    }

    private class DocRow extends JComponent {
        private final Doc doc;
        private boolean hover;

        DocRow(Doc doc) {
            this.doc = doc;
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
            setPreferredSize(new Dimension(230, 62));
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
                    open(doc.did);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            boolean sel = doc.did.equals(current);
            Graphics2D g2 = Theme.aa(g);
            if (sel) {
                g2.setColor(Theme.ACCENT_SOFT);
                Theme.roundRect(g2, 0, 0, getWidth(), getHeight(), 10);
            } else if (hover) {
                g2.setColor(new Color(0xF6F8FC));
                Theme.roundRect(g2, 0, 0, getWidth(), getHeight(), 10);
            }
            g2.dispose();
            Icons.draw(g, "doc", 10, 16, 20, sel ? Theme.ACCENT : new Color(0x9AA5B8));
            Graphics2D g3 = Theme.aa(g);
            g3.setFont(sel ? Theme.bold(12) : Theme.font(12));
            g3.setColor(sel ? Theme.ACCENT : Theme.TEXT);
            FontMetrics fm = g3.getFontMetrics();
            int w = getWidth() - 46;
            g3.drawString(Theme.ellipsis(doc.title, fm, w), 38, 24);
            g3.setFont(Theme.font(10));
            g3.setColor(Theme.MUTED);
            String meta = doc.wordCount() + " 字 · " + Theme.timeText(doc.updatedAt);
            g3.drawString(Theme.ellipsis(meta, Theme.fm(Theme.font(10)), w), 38, 40);
            if (!doc.editors.isEmpty()) {
                g3.setColor(Theme.GREEN);
                g3.drawString("协作中", 38, 54);
            }
            g3.dispose();
        }
    }
}
