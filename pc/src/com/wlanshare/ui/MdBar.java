package com.wlanshare.ui;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.undo.UndoManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;

/**
 * Markdown 编辑工具栏：一键插入常用语法，并提供撤销 / 重做与常用快捷键。
 * 纯 Swing 实现，不依赖任何第三方编辑器库。
 */
public class MdBar extends JPanel {

    private final JTextArea area;
    private final UndoManager undo = new UndoManager();
    private final JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
    private JScrollPane sh;

    public MdBar(JTextArea area) {
        super(new BorderLayout());
        this.area = area;
        setOpaque(false);

        undo.setLimit(400);
        area.getDocument().addUndoableEditListener(new UndoableEditListener() {
            @Override
            public void undoableEditHappened(UndoableEditEvent e) {
                undo.addEdit(e.getEdit());
            }
        });

        row.setOpaque(false);
        row.add(new Theme.IconBtn("undo", "撤销（Ctrl+Z）", new Runnable() {
            public void run() {
                undoEdit();
            }
        }, 24));
        row.add(new Theme.IconBtn("redo", "重做（Ctrl+Y）", new Runnable() {
            public void run() {
                redoEdit();
            }
        }, 24));
        JPanel gap = new JPanel();
        gap.setOpaque(false);
        gap.setPreferredSize(new Dimension(4, 1));
        row.add(gap);
        add("H", "标题（Ctrl+1 / 2 / 3）", new Runnable() {
            public void run() {
                heading(2);
            }
        });
        add("B", "加粗（Ctrl+B）", new Runnable() {
            public void run() {
                wrap("**", "**", "粗体文字");
            }
        });
        add("I", "斜体（Ctrl+I）", new Runnable() {
            public void run() {
                wrap("*", "*", "斜体文字");
            }
        });
        add("S", "删除线（Ctrl+Shift+X）", new Runnable() {
            public void run() {
                wrap("~~", "~~", "删除线");
            }
        });
        add("`", "行内代码（Ctrl+`）", new Runnable() {
            public void run() {
                wrap("`", "`", "code");
            }
        });
        add("•", "无序列表（Ctrl+Shift+8）", new Runnable() {
            public void run() {
                prefixLines("- ");
            }
        });
        add("1.", "有序列表（Ctrl+Shift+7）", new Runnable() {
            public void run() {
                prefixLines("1. ");
            }
        });
        add("“", "引用（Ctrl+Q）", new Runnable() {
            public void run() {
                prefixLines("> ");
            }
        });
        add("链接", "插入链接（Ctrl+K）", new Runnable() {
            public void run() {
                link();
            }
        });
        Theme.Btn tableBtn = add("▦", "插入表格（可选择行列）", null);
        tableBtn.addActionListener(e -> TablePicker.show(tableBtn, this::insertTable));
        add("{ }", "插入代码块", new Runnable() {
            public void run() {
                insertBlock("\n```\n代码\n```\n");
            }
        });
        add("</>", "插入 HTML 块（预览按原样渲染）", new Runnable() {
            public void run() {
                insertHtmlBlock();
            }
        });
        add("—", "分割线", new Runnable() {
            public void run() {
                insertBlock("\n---\n");
            }
        });

        final JScrollPane sp = new JScrollPane(row,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sh = sp;
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        // 工具条超出宽度时用滚轮横向滚动，不额外占用高度
        java.awt.event.MouseWheelListener wheel = new java.awt.event.MouseWheelListener() {
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                javax.swing.JScrollBar bar = sp.getHorizontalScrollBar();
                bar.setValue(bar.getValue() + e.getWheelRotation() * 42);
            }
        };
        sp.addMouseWheelListener(wheel);
        row.addMouseWheelListener(wheel);
        add(sp, BorderLayout.CENTER);

        // 右侧固定的「更多」菜单：工具栏被滚出视口的命令也能在这里找到
        Theme.Btn more = new Theme.Btn("更多", Theme.Kind.GHOST).small();
        more.setFont(Theme.font(11));
        more.setBorder(BorderFactory.createEmptyBorder(4, 7, 4, 7));
        more.setToolTipText("全部 Markdown 命令");
        more.addActionListener(e -> showMore(more));
        JPanel right = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(more);
        add(right, BorderLayout.EAST);

        setPreferredSize(new Dimension(260, 34));

        JPanel line = new JPanel();
        line.setBackground(Theme.BORDER2);
        line.setPreferredSize(new Dimension(1, 1));
        add(line, BorderLayout.SOUTH);

        bindKeys();
    }

    /** 全部命令的下拉菜单（含快捷键提示）。 */
    private void showMore(java.awt.Component anchor) {
        // 「插入表格」要从这里再弹一个行列选择器，锚点用「更多」按钮本身
        final JComponent tableAnchor = anchor instanceof JComponent ? (JComponent) anchor : this;
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        menu.add(item("一级标题  Ctrl+1", new Runnable() {
            public void run() {
                heading(1);
            }
        }));
        menu.add(item("二级标题  Ctrl+2", new Runnable() {
            public void run() {
                heading(2);
            }
        }));
        menu.add(item("三级标题  Ctrl+3", new Runnable() {
            public void run() {
                heading(3);
            }
        }));
        menu.addSeparator();
        menu.add(item("加粗  Ctrl+B", new Runnable() {
            public void run() {
                wrap("**", "**", "粗体文字");
            }
        }));
        menu.add(item("斜体  Ctrl+I", new Runnable() {
            public void run() {
                wrap("*", "*", "斜体文字");
            }
        }));
        menu.add(item("删除线  Ctrl+Shift+X", new Runnable() {
            public void run() {
                wrap("~~", "~~", "删除线");
            }
        }));
        menu.add(item("行内代码  Ctrl+`", new Runnable() {
            public void run() {
                wrap("`", "`", "code");
            }
        }));
        menu.addSeparator();
        menu.add(item("无序列表", new Runnable() {
            public void run() {
                prefixLines("- ");
            }
        }));
        menu.add(item("有序列表", new Runnable() {
            public void run() {
                prefixLines("1. ");
            }
        }));
        menu.add(item("引用  Ctrl+Q", new Runnable() {
            public void run() {
                prefixLines("> ");
            }
        }));
        menu.add(item("插入链接  Ctrl+K", new Runnable() {
            public void run() {
                link();
            }
        }));
        menu.add(item("插入表格（选行列）", new Runnable() {
            public void run() {
                javax.swing.SwingUtilities.invokeLater(
                        () -> TablePicker.show(tableAnchor, MdBar.this::insertTable));
            }
        }));
        menu.add(item("插入代码块", new Runnable() {
            public void run() {
                insertBlock("\n```\n代码\n```\n");
            }
        }));
        menu.add(item("插入 HTML 块", new Runnable() {
            public void run() {
                insertHtmlBlock();
            }
        }));
        menu.add(item("插入分割线", new Runnable() {
            public void run() {
                insertBlock("\n---\n");
            }
        }));
        menu.addSeparator();
        menu.add(item("撤销  Ctrl+Z", new Runnable() {
            public void run() {
                undoEdit();
            }
        }));
        menu.add(item("重做  Ctrl+Y", new Runnable() {
            public void run() {
                redoEdit();
            }
        }));
        menu.show(anchor, anchor.getWidth() - 150, anchor.getHeight() + 4);
    }

    private javax.swing.JMenuItem item(String text, final Runnable action) {
        javax.swing.JMenuItem it = new javax.swing.JMenuItem(text);
        it.setFont(Theme.font(12));
        it.addActionListener(e -> action.run());
        return it;
    }

    private Theme.Btn add(String text, String tip, final Runnable action) {
        Theme.Btn b = new Theme.Btn(text, Theme.Kind.GHOST).small();
        b.setFont(Theme.bold(11));
        b.setBorder(BorderFactory.createEmptyBorder(4, 9, 4, 9));
        b.setToolTipText(tip);
        if (action != null) {
            b.addActionListener(e -> action.run());
        }
        row.add(b);
        return b;
    }

    /** 按行列插入表格：rows 含表头，cols 为列数。 */
    public void insertTable(int rows, int cols) {
        int r = Math.max(1, Math.min(TablePicker.MAX, rows));
        int c = Math.max(1, Math.min(TablePicker.MAX, cols));
        StringBuilder sb = new StringBuilder("\n");
        for (int i = 1; i <= c; i++) {
            sb.append("| 列 ").append(i).append(' ');
        }
        sb.append("|\n");
        for (int i = 0; i < c; i++) {
            sb.append("| --- ");
        }
        sb.append("|\n");
        for (int i = 1; i < r; i++) {
            for (int j = 0; j < c; j++) {
                sb.append("| 内容 ");
            }
            sb.append("|\n");
        }
        sb.append('\n');
        insertBlock(sb.toString());
    }

    private void bindKeys() {
        InputMap im = area.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = area.getActionMap();
        bind(im, am, "ctrl B", "md-bold", new Runnable() {
            public void run() {
                wrap("**", "**", "粗体文字");
            }
        });
        bind(im, am, "ctrl I", "md-italic", new Runnable() {
            public void run() {
                wrap("*", "*", "斜体文字");
            }
        });
        bind(im, am, "ctrl K", "md-link", new Runnable() {
            public void run() {
                link();
            }
        });
        bind(im, am, "ctrl Q", "md-quote", new Runnable() {
            public void run() {
                prefixLines("> ");
            }
        });
        bind(im, am, "ctrl shift X", "md-strike", new Runnable() {
            public void run() {
                wrap("~~", "~~", "删除线");
            }
        });
        bind(im, am, "ctrl 1", "md-h1", new Runnable() {
            public void run() {
                heading(1);
            }
        });
        bind(im, am, "ctrl 2", "md-h2", new Runnable() {
            public void run() {
                heading(2);
            }
        });
        bind(im, am, "ctrl 3", "md-h3", new Runnable() {
            public void run() {
                heading(3);
            }
        });
        bind(im, am, "ctrl Z", "md-undo", new Runnable() {
            public void run() {
                undoEdit();
            }
        });
        bind(im, am, "ctrl Y", "md-redo", new Runnable() {
            public void run() {
                redoEdit();
            }
        });
    }

    private void bind(InputMap im, ActionMap am, String key, String name, final Runnable action) {
        im.put(KeyStroke.getKeyStroke(key), name);
        am.put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private void undoEdit() {
        if (undo.canUndo()) {
            undo.undo();
        }
    }

    private void redoEdit() {
        if (undo.canRedo()) {
            undo.redo();
        }
    }

    /** 用前后缀包裹选区；没有选区时插入占位文字并选中它，方便直接改写。 */
    public void wrap(String prefix, String suffix, String placeholder) {
        int s = area.getSelectionStart();
        int e = area.getSelectionEnd();
        String sel = area.getSelectedText();
        if (sel == null || sel.isEmpty()) {
            String ph = placeholder == null ? "" : placeholder;
            area.replaceSelection(prefix + ph + suffix);
            area.select(s + prefix.length(), s + prefix.length() + ph.length());
        } else {
            area.replaceSelection(prefix + sel + suffix);
            area.select(s + prefix.length(), s + prefix.length() + sel.length());
        }
        area.requestFocusInWindow();
    }

    /** 给所选行（未选则当前行）加行首标记，已存在则去掉。 */
    public void prefixLines(String prefix) {
        int s = area.getSelectionStart();
        int e = area.getSelectionEnd();
        String text = area.getText();
        int from = lineStart(text, s);
        int to = e == s ? lineEnd(text, s) : lineEnd(text, e);
        String block = text.substring(from, to);
        if (block.isEmpty()) {
            area.replaceSelection(prefix);
            area.setCaretPosition(area.getSelectionEnd());
            return;
        }
        StringBuilder sb = new StringBuilder();
        String[] lines = block.split("\n", -1);
        boolean allMarked = true;
        for (String line : lines) {
            if (!line.trim().isEmpty() && !line.startsWith(prefix)) {
                allMarked = false;
                break;
            }
        }
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (i > 0) {
                sb.append('\n');
            }
            if (line.trim().isEmpty()) {
                sb.append(line);
            } else if (allMarked) {
                sb.append(line.startsWith(prefix) ? line.substring(prefix.length()) : line);
            } else if (line.startsWith(prefix)) {
                sb.append(line);
            } else {
                sb.append(prefix).append(line);
            }
        }
        area.replaceRange(sb.toString(), from, to);
        area.select(from, from + sb.length());
        area.requestFocusInWindow();
    }

    /** 标题：把当前行（或选中行）改成 1~3 级标题。 */
    public void heading(int level) {
        int s = area.getSelectionStart();
        String text = area.getText();
        int from = lineStart(text, s);
        int to = lineEnd(text, s);
        String line = text.substring(from, to);
        String stripped = line.replaceFirst("^#{1,6}\\s+", "");
        String prefix = new String(new char[level]).replace('\0', '#') + " ";
        if (line.startsWith(prefix)) {
            area.replaceRange(stripped, from, to);
            area.setCaretPosition(from + stripped.length());
        } else {
            area.replaceRange(prefix + stripped, from, to);
            area.setCaretPosition(from + prefix.length() + stripped.length());
        }
        area.requestFocusInWindow();
    }

    public void link() {
        int s = area.getSelectionStart();
        int e = area.getSelectionEnd();
        String sel = area.getSelectedText();
        String label = sel == null || sel.isEmpty() ? "链接文字" : sel;
        String ins = "[" + label + "](https://)";
        area.replaceSelection(ins);
        // 光标落在 url 位置，直接粘贴即可
        int urlStart = s + 1 + label.length() + 2;
        area.select(urlStart, urlStart + 8);
        area.requestFocusInWindow();
    }

    private void insertBlock(String block) {
        area.replaceSelection(block);
        area.requestFocusInWindow();
    }

    /** 插入一个 ```html 围栏块；光标停在正文第一行，方便直接改写。 */
    public void insertHtmlBlock() {
        String head = "\n```html\n<div style=\"padding:12px 14px;background-color:#F5F8FE;"
                + "border-left:3px solid #A9BFEC\">\n";
        String body = "  <b>HTML 区块</b><br>\n  可以直接写 div / table / img 与内联样式，预览会按原样渲染。\n";
        String tail = "</div>\n```\n";
        int at = area.getSelectionStart();
        area.replaceSelection(head + body + tail);
        area.select(at + head.length(), at + head.length() + body.length());
        area.requestFocusInWindow();
    }

    private static int lineStart(String text, int pos) {
        int p = Math.min(pos, text.length());
        int i = text.lastIndexOf('\n', Math.max(0, p - 1));
        return i < 0 ? 0 : i + 1;
    }

    private static int lineEnd(String text, int pos) {
        int p = Math.min(pos, text.length());
        int i = text.indexOf('\n', p);
        return i < 0 ? text.length() : i;
    }
}
