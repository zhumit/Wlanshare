package com.wlanshare.app;

import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.wlanshare.core.Doc;
import com.wlanshare.core.Node;
import com.wlanshare.core.Peer;

import java.util.List;

/** 文档：列表 + Markdown 编辑/预览，支持多人同时编辑自动合并。 */
public class DocsView implements Section {

    private final MainActivity act;
    private final Node node;
    private final FrameLayout root;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean editing;
    private String current;
    private EditText editor;
    private EditText titleField;
    private WebView preview;
    private TextView status;
    private boolean previewing;
    private boolean applying;
    private long lastLocal;
    private Runnable pushTask;
    /** 光标行：最后广播的行号与节流任务。 */
    private int lastCursorLine = -1;
    private Runnable cursorTask;
    private Runnable markTask;
    private final java.util.List<Object> cursorSpans = new java.util.ArrayList<Object>();

    public DocsView(MainActivity act, Node node) {
        this.act = act;
        this.node = node;
        root = new FrameLayout(act);
        root.setBackgroundColor(Ui.BG);
        pushTask = new Runnable() {
            @Override
            public void run() {
                if (current != null && editor != null) {
                    node.updateDocText(current, editor.getText().toString());
                    updateStatus();
                }
            }
        };
        showList();
    }

    @Override
    public View view() {
        return root;
    }

    @Override
    public void onShow() {
        if (!editing) {
            showList();
        }
    }

    @Override
    public void refresh() {
        if (editing) {
            pullRemote();
            applyCursorMarks();
            updateStatus();
        } else {
            showList();
        }
    }

    @Override
    public boolean back() {
        if (editing) {
            showList();
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- 列表

    private void showList() {
        if (current != null) {
            node.sendDocCursor(current, -1);
        }
        lastCursorLine = -1;
        editing = false;
        current = null;
        root.removeAllViews();
        ScrollView sv = new ScrollView(act);
        LinearLayout list = Ui.column(act);
        list.setPadding(Ui.dp(act, 14), Ui.dp(act, 2), Ui.dp(act, 14), Ui.dp(act, 14));

        LinearLayout head = Ui.row(act);
        head.addView(Ui.text(act, "协作文档", 13, Ui.TEXT, true), Ui.lpWeight(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(Ui.pill(act, "新建文档", 0xFFFFFFFF, Ui.ACCENT, new Runnable() {
            @Override
            public void run() {
                Doc d = node.createDoc("未命名文档");
                open(d.did);
            }
        }));
        list.addView(head);
        list.addView(Ui.spacer(act, 10));

        List<Doc> docs = node.docList();
        if (docs.isEmpty()) {
            LinearLayout card = Ui.card(act);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setPadding(0, Ui.dp(act, 28), 0, Ui.dp(act, 28));
            card.addView(Ui.icon(act, R.drawable.ic_doc, 0xFFC3CDE0, 42));
            TextView t = Ui.text(act, "还没有文档", 14, Ui.TEXT, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(act, 10), 0, 0);
            card.addView(t, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView s2 = Ui.text(act, "新建一份，或等待好友共享给你", 12, Ui.MUTED, false);
            s2.setGravity(Gravity.CENTER);
            card.addView(s2, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            list.addView(card);
        } else {
            for (final Doc d : docs) {
                list.addView(docRow(d));
                list.addView(Ui.spacer(act, 8));
            }
        }

        sv.addView(list, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv);
    }

    private View docRow(final Doc d) {
        LinearLayout card = Ui.card(act);
        LinearLayout row = Ui.row(act);
        row.addView(Ui.icon(act, R.drawable.ic_doc, 0xFF9AA5B8, 22));

        LinearLayout info = Ui.column(act);
        info.setPadding(Ui.dp(act, 10), 0, 0, 0);
        TextView t = Ui.text(act, d.title, 15, Ui.TEXT, true);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(t);
        info.addView(Ui.text(act, d.wordCount() + " 字 · " + Ui.timeText(d.updatedAt), 11, Ui.MUTED, false));
        row.addView(info, Ui.lpWeight(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (!d.editors.isEmpty()) {
            row.addView(Ui.text(act, "协作中", 10, Ui.GREEN, true));
        }
        card.addView(row);
        Ui.click(card, new Runnable() {
            @Override
            public void run() {
                open(d.did);
            }
        });
        return card;
    }

    // ---------------------------------------------------------------- 编辑

    private void open(String did) {
        current = did;
        node.awareDoc(did, false);
        editing = true;
        buildEditor();
    }

    private void buildEditor() {
        root.removeAllViews();
        Doc d = node.doc(current);
        if (d == null) {
            showList();
            return;
        }
        LinearLayout col = Ui.column(act);

        // 顶栏
        LinearLayout head = Ui.row(act);
        head.setBackgroundColor(0xFFFFFFFF);
        head.setPadding(Ui.dp(act, 10), Ui.dp(act, 6), Ui.dp(act, 12), Ui.dp(act, 6));
        TextView back = Ui.text(act, "‹", 30, Ui.TEXT, false);
        back.setPadding(Ui.dp(act, 6), 0, Ui.dp(act, 8), Ui.dp(act, 4));
        Ui.click(back, new Runnable() {
            @Override
            public void run() {
                showList();
            }
        });
        head.addView(back);

        titleField = new EditText(act);
        titleField.setText(d.title);
        titleField.setTextSize(16);
        titleField.setTypeface(Typeface.DEFAULT_BOLD);
        titleField.setTextColor(Ui.TEXT);
        titleField.setBackground(null);
        titleField.setSingleLine(true);
        titleField.setPadding(0, 0, 0, 0);
        head.addView(titleField, Ui.lpWeight(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(Ui.pill(act, "共享", Ui.ACCENT, Ui.ACCENT_SOFT, new Runnable() {
            @Override
            public void run() {
                shareDoc();
            }
        }));
        TextView del = Ui.pill(act, "删除", Ui.RED, 0xFFFDECEE, new Runnable() {
            @Override
            public void run() {
                confirmDelete();
            }
        });
        LinearLayout.LayoutParams dp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dp.leftMargin = Ui.dp(act, 6);
        head.addView(del, dp);
        col.addView(head, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Markdown 快捷工具栏
        col.addView(buildMdBar(), Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // 编辑器 + 预览
        FrameLayout body = new FrameLayout(act);
        editor = new EditText(act) {
            @Override
            protected void onSelectionChanged(int selStart, int selEnd) {
                super.onSelectionChanged(selStart, selEnd);
                onCursorMoved(selStart);
            }
        };
        editor.setText(d.text);
        editor.setTextSize(14);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setTextColor(0xFF1F2937);
        editor.setBackgroundColor(0xFFFFFFFF);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setPadding(Ui.dp(act, 14), Ui.dp(act, 12), Ui.dp(act, 14), Ui.dp(act, 12));
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setVerticalScrollBarEnabled(true);
        // 选中配色与 PC 端一致：淡蓝底 + 深色字，避免选中后看不清文字
        editor.setHighlightColor(0x992B6BF3);
        editor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (applying) {
                    return;
                }
                lastLocal = System.currentTimeMillis();
                handler.removeCallbacks(pushTask);
                handler.postDelayed(pushTask, 500);
                if (status != null) {
                    status.setText(s.length() + " 字符 · 同步中…");
                    status.setTextColor(Ui.ACCENT);
                }
            }
        });
        body.addView(editor, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        preview = new WebView(act);
        preview.setVisibility(View.GONE);
        preview.getSettings().setJavaScriptEnabled(false);
        body.addView(preview, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        col.addView(body, Ui.lpWeight(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 底栏
        LinearLayout bar = Ui.row(act);
        bar.setBackgroundColor(0xFFFFFFFF);
        bar.setPadding(Ui.dp(act, 14), Ui.dp(act, 8), Ui.dp(act, 14), Ui.dp(act, 8));
        status = Ui.text(act, "", 11, Ui.MUTED, false);
        bar.addView(status, Ui.lpWeight(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(Ui.pill(act, "预览", Ui.ACCENT, Ui.ACCENT_SOFT, new Runnable() {
            @Override
            public void run() {
                togglePreview();
            }
        }));
        col.addView(bar, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(col, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        updateStatus();
        // 打开文档后先广播一次当前光标行，对方立刻就能看到我在哪里
        lastCursorLine = -1;
        onCursorMoved(editor.getSelectionStart());
    }

    private void togglePreview() {
        if (preview == null || editor == null) {
            return;
        }
        previewing = !previewing;
        if (previewing) {
            preview.loadDataWithBaseURL(null, Md.toHtml(editor.getText().toString()),
                    "text/html", "UTF-8", null);
            preview.setVisibility(View.VISIBLE);
            editor.setVisibility(View.GONE);
        } else {
            preview.setVisibility(View.GONE);
            editor.setVisibility(View.VISIBLE);
        }
    }

    /** 一行可横向滚动的 Markdown 快捷按钮。 */
    private View buildMdBar() {
        HorizontalScrollView hs = new HorizontalScrollView(act);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setFillViewport(false);
        hs.setBackgroundColor(0xFFF7F9FC);

        LinearLayout row = Ui.row(act);
        int px = Ui.dp(act, 8), py = Ui.dp(act, 6);
        row.setPadding(px, py, px, py);

        addMd(row, "H", "标题", new Runnable() {
            public void run() {
                mdHeading(2);
            }
        });
        addMd(row, "B", "加粗", new Runnable() {
            public void run() {
                mdWrap("**", "**", "粗体文字");
            }
        });
        addMd(row, "I", "斜体", new Runnable() {
            public void run() {
                mdWrap("*", "*", "斜体文字");
            }
        });
        addMd(row, "S", "删除线", new Runnable() {
            public void run() {
                mdWrap("~~", "~~", "删除线");
            }
        });
        addMd(row, "`", "行内代码", new Runnable() {
            public void run() {
                mdWrap("`", "`", "code");
            }
        });
        addMd(row, "\u2022", "无序列表", new Runnable() {
            public void run() {
                mdPrefix("- ");
            }
        });
        addMd(row, "1.", "有序列表", new Runnable() {
            public void run() {
                mdPrefix("1. ");
            }
        });
        addMd(row, "\u201C", "引用", new Runnable() {
            public void run() {
                mdPrefix("> ");
            }
        });
        addMd(row, "链接", "插入链接", new Runnable() {
            public void run() {
                mdLink();
            }
        });
        addMd(row, "表格", "插入表格", new Runnable() {
            public void run() {
                mdInsert("\n| 列 1 | 列 2 |\n| --- | --- |\n| 内容 | 内容 |\n");
            }
        });
        addMd(row, "代码块", "插入代码块", new Runnable() {
            public void run() {
                mdInsert("\n```\n代码\n```\n");
            }
        });
        addMd(row, "\u2014", "分割线", new Runnable() {
            public void run() {
                mdInsert("\n---\n");
            }
        });

        hs.addView(row, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return hs;
    }

    private void addMd(LinearLayout row, String label, String tip, final Runnable action) {
        TextView t = Ui.pill(act, label, Ui.SUB, 0xFFFFFFFF, action);
        t.setTextSize(12);
        t.setContentDescription(tip);
        int px = Ui.dp(act, 9), py = Ui.dp(act, 5);
        t.setPadding(px, py, px, py);
        LinearLayout.LayoutParams lp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(act, 6);
        row.addView(t, lp);
    }

    /** 用前后缀包裹选区；没有选区时插入占位文字并选中它。 */
    private void mdWrap(String prefix, String suffix, String placeholder) {
        if (editor == null) {
            return;
        }
        int s = Math.max(0, editor.getSelectionStart());
        int e = Math.max(0, editor.getSelectionEnd());
        if (s > e) {
            int t = s;
            s = e;
            e = t;
        }
        Editable ed = editor.getText();
        String sel = ed.subSequence(s, e).toString();
        String body = sel.isEmpty() ? placeholder : sel;
        ed.replace(s, e, prefix + body + suffix);
        editor.setSelection(s + prefix.length(), s + prefix.length() + body.length());
        editor.requestFocus();
    }

    /** 给所选行（未选则当前行）加行首标记，已加过则去掉。 */
    private void mdPrefix(String prefix) {
        if (editor == null) {
            return;
        }
        Editable ed = editor.getText();
        int s = Math.max(0, editor.getSelectionStart());
        int e = Math.max(0, editor.getSelectionEnd());
        if (s > e) {
            int t = s;
            s = e;
            e = t;
        }
        int from = mdLineStart(ed, s);
        int to = e == s ? mdLineEnd(ed, s) : mdLineEnd(ed, e);
        String block = ed.subSequence(from, to).toString();
        if (block.isEmpty()) {
            ed.insert(from, prefix);
            editor.setSelection(from + prefix.length());
            editor.requestFocus();
            return;
        }
        String[] lines = block.split("\n", -1);
        boolean allMarked = true;
        for (String line : lines) {
            if (!line.trim().isEmpty() && !line.startsWith(prefix)) {
                allMarked = false;
                break;
            }
        }
        StringBuilder sb = new StringBuilder();
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
        ed.replace(from, to, sb.toString());
        editor.setSelection(from, Math.min(ed.length(), from + sb.length()));
        editor.requestFocus();
    }

    /** 把光标所在行改成 1~3 级标题。 */
    private void mdHeading(int level) {
        if (editor == null) {
            return;
        }
        Editable ed = editor.getText();
        int s = Math.max(0, editor.getSelectionStart());
        int from = mdLineStart(ed, s);
        int to = mdLineEnd(ed, s);
        String line = ed.subSequence(from, to).toString();
        String stripped = line.replaceFirst("^#{1,6}\\s+", "");
        StringBuilder hashes = new StringBuilder();
        for (int i = 0; i < level; i++) {
            hashes.append('#');
        }
        String prefix = hashes + " ";
        if (line.startsWith(prefix)) {
            ed.replace(from, to, stripped);
            editor.setSelection(from + stripped.length());
        } else {
            ed.replace(from, to, prefix + stripped);
            editor.setSelection(from + prefix.length() + stripped.length());
        }
        editor.requestFocus();
    }

    private void mdLink() {
        if (editor == null) {
            return;
        }
        int s = Math.max(0, editor.getSelectionStart());
        int e = Math.max(0, editor.getSelectionEnd());
        if (s > e) {
            int t = s;
            s = e;
            e = t;
        }
        Editable ed = editor.getText();
        String sel = ed.subSequence(s, e).toString();
        String label = sel.isEmpty() ? "链接文字" : sel;
        String ins = "[" + label + "](https://)";
        ed.replace(s, e, ins);
        int urlStart = s + 1 + label.length() + 2;
        editor.setSelection(urlStart, urlStart + 8);
        editor.requestFocus();
    }

    private void mdInsert(String text) {
        if (editor == null) {
            return;
        }
        int s = Math.max(0, editor.getSelectionStart());
        int e = Math.max(0, editor.getSelectionEnd());
        if (s > e) {
            int t = s;
            s = e;
            e = t;
        }
        Editable ed = editor.getText();
        ed.replace(s, e, text);
        editor.setSelection(s + text.length());
        editor.requestFocus();
    }

    private static int mdLineStart(CharSequence text, int pos) {
        int p = Math.min(pos, text.length());
        for (int i = p - 1; i >= 0; i--) {
            if (text.charAt(i) == '\n') {
                return i + 1;
            }
        }
        return 0;
    }

    private static int mdLineEnd(CharSequence text, int pos) {
        int p = Math.min(pos, text.length());
        for (int i = p; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                return i;
            }
        }
        return text.length();
    }

    /** 光标行变化：节流广播（300ms）。 */
    private void onCursorMoved(int off) {
        if (applying || current == null || editor == null) {
            return;
        }
        String t = editor.getText().toString();
        int line = 0;
        int p = Math.min(off, t.length());
        for (int i = 0; i < p; i++) {
            if (t.charAt(i) == '\n') {
                line++;
            }
        }
        if (line == lastCursorLine) {
            return;
        }
        lastCursorLine = line;
        if (cursorTask == null) {
            cursorTask = new Runnable() {
                @Override
                public void run() {
                    if (current != null) {
                        node.sendDocCursor(current, lastCursorLine);
                    }
                }
            };
        }
        handler.removeCallbacks(cursorTask);
        handler.postDelayed(cursorTask, 300);
    }

    /** 给协作者所在的行铺一层底色（每人一色）。 */
    private void applyCursorMarks() {
        if (editor == null || current == null) {
            return;
        }
        Doc d = node.doc(current);
        if (d == null) {
            return;
        }
        applying = true;
        try {
            Editable ed = editor.getText();
            for (Object sp : cursorSpans) {
                ed.removeSpan(sp);
            }
            cursorSpans.clear();
            String text = ed.toString();
            for (java.util.Map.Entry<String, Integer> e : d.cursors.entrySet()) {
                String pid = e.getKey();
                if (pid.equals(node.id)) {
                    continue;
                }
                int start = lineOffset(text, e.getValue());
                int end = lineEnd(text, start);
                if (end <= start) {
                    end = Math.min(text.length(), start + 1);
                }
                if (start >= ed.length()) {
                    continue;
                }
                BackgroundColorSpan span = new BackgroundColorSpan(softColor(pid));
                ed.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                cursorSpans.add(span);
            }
        } catch (Throwable ignored) {
        } finally {
            applying = false;
        }
        updateStatus();
    }

    private static int lineOffset(String text, int line) {
        int p = 0;
        for (int i = 0; i < line; i++) {
            int n = text.indexOf('\n', p);
            if (n < 0) {
                return text.length();
            }
            p = n + 1;
        }
        return p;
    }

    private static int lineEnd(String text, int off) {
        int n = text.indexOf('\n', off);
        return n < 0 ? text.length() : n;
    }

    /** 由设备 id 派生的颜色（与 PC 端调色板一致）。 */
    private static int colorOf(String pid) {
        int[] palette = {0xFF2B6BF3, 0xFF12A27B, 0xFFE08700, 0xFF8B5CF6, 0xFFD6455D, 0xFF0E9AA7};
        return palette[Node.colorIndex(pid)];
    }

    private static int softColor(String pid) {
        int c = colorOf(pid);
        int alpha = 38;
        return (alpha << 24) | (c & 0x00FFFFFF);
    }

    private static String shortName(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() <= 4 ? t : t.substring(0, 4);
    }

    private void updateStatus() {
        Doc d = node.doc(current);
        if (d == null || status == null) {
            return;
        }
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append(d.wordCount() + " 字 · 版本 " + d.ver);
        int shown = 0;
        for (java.util.Map.Entry<String, Integer> e : d.cursors.entrySet()) {
            String pid = e.getKey();
            if (pid.equals(node.id) || shown >= 2) {
                continue;
            }
            shown++;
            int at = sb.length();
            sb.append("  ● ").append(shortName(node.nameOf(pid)))
                    .append(" 第").append(String.valueOf(e.getValue() + 1)).append("行");
            sb.setSpan(new ForegroundColorSpan(colorOf(pid)), at, sb.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (shown == 0) {
            sb.append(d.editors.isEmpty() ? " · 仅自己" : " · " + d.editors.size() + " 人协作中");
        }
        status.setText(sb);
        status.setTextColor(Ui.MUTED);
    }

    /** 拉取远端修改（用户空闲时覆盖编辑器内容）。 */
    private void pullRemote() {
        Doc d = node.doc(current);
        if (d == null || editor == null) {
            return;
        }
        if (System.currentTimeMillis() - lastLocal < 1000) {
            return;
        }
        if (editor.getText().toString().equals(d.text)) {
            return;
        }
        applying = true;
        int start = editor.getSelectionStart();
        editor.setText(d.text);
        try {
            editor.setSelection(Math.min(Math.max(0, start), d.text.length()));
        } catch (Exception ignored) {
        }
        applying = false;
        if (previewing && preview != null) {
            preview.loadDataWithBaseURL(null, Md.toHtml(d.text), "text/html", "UTF-8", null);
        }
    }

    private void shareDoc() {
        final List<Peer> friends = node.friendPeerList();
        if (friends.isEmpty()) {
            act.toast("还没有好友可以共享");
            return;
        }
        String[] names = new String[friends.size()];
        for (int i = 0; i < friends.size(); i++) {
            names[i] = node.nameOf(friends.get(i).id);
        }
        new android.app.AlertDialog.Builder(act)
                .setTitle("共享给")
                .setItems(names, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        node.shareDoc(current, friends.get(which).id);
                        act.toast("已共享给 " + node.nameOf(friends.get(which).id));
                    }
                })
                .show();
    }

    private void confirmDelete() {
        new android.app.AlertDialog.Builder(act)
                .setTitle("删除文档")
                .setMessage("确定删除这份文档？本机文件会被移除。")
                .setPositiveButton("删除", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        node.deleteDoc(current);
                        showList();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
