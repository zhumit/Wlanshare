package com.wlanshare.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Markdown → HTML（面向 Swing JEditorPane 的安全子集）。
 *
 * 版式参考现代阅读器：大标题 + 细分割线、卡片式引用 / 代码块、无竖线的表格。
 * 只使用 Swing 的 HTML3.2 + CSS1 子集：margin / padding / background-color / font-* / align。
 * 注意：Swing 的表格 bordercolor 不生效、空 &lt;td&gt; 会被撑高，所以外框用「1px 内边距 + 底色 td」模拟，
 * 行分割线用 1px 高的 line row（font-size:1pt）。
 *
 * 额外支持 ```html 围栏块与裸 HTML 块：内容不做转义，交给渲染器按原样排版（先做安全过滤）。
 */
public final class Markdown {

    private static final String C_TITLE = "#0F172A";
    private static final String C_TEXT = "#2A3446";
    private static final String C_LINE = "#E7ECF6";
    private static final String C_ACCENT = "#2B6BF3";

    /** 表格配色：外框 / 表头底 / 表头分割线 / 行分割线 / 斑马纹。 */
    private static final String T_FRAME = "#E2E9F5";
    private static final String T_HEAD = "#F4F7FD";
    private static final String T_HEAD_LINE = "#DCE4F2";
    private static final String T_ROW_LINE = "#EEF2F8";
    private static final String T_ZEBRA = "#FBFCFE";

    private Markdown() {
    }

    public static String toHtml(String md) {
        String body = new Parser().run(md == null ? "" : md);
        if (body.isEmpty()) {
            body = "<div style=\"margin:40px 0;color:#AEB6C6;font-size:10pt\">"
                    + "还没有内容。在左边开始写 Markdown，这里会实时排版预览。</div>";
        }
        return "<html><head><style>"
                + "body{font-family:'" + Theme.family() + "';font-size:10.5pt;color:" + C_TEXT + ";}"
                + "p{margin:0 0 12px 0;}"
                + "li{margin:0 0 5px 0;}"
                + "td{font-size:10.5pt;color:" + C_TEXT + ";}"
                + "a{color:" + C_ACCENT + ";}"
                + "</style></head><body style=\"background-color:#FFFFFF;\">"
                + "<div style=\"margin:28px 32px 46px 32px\">" + body + "</div>"
                + "</body></html>";
    }

    // ------------------------------------------------------------------ 解析

    /** 一次渲染的解析状态（避免把十几个状态量在各方法间传来传去）。 */
    private static final class Parser {

        private final StringBuilder body = new StringBuilder();
        private final List<String> quote = new ArrayList<String>();
        private final List<String[]> rows = new ArrayList<String[]>();
        private String[] aligns;

        private boolean inCode;
        private String codeLang = "";
        private final StringBuilder codeBuf = new StringBuilder();

        private boolean inList;
        private boolean ordered;
        private boolean inTable;

        private boolean inConflict;
        private final StringBuilder conflict = new StringBuilder();

        String run(String md) {
            String[] lines = md.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                String raw = lines[i];
                String line = raw.trim();

                // ---- 围栏：代码块 / HTML 块
                if (line.startsWith("```")) {
                    if (inCode) {
                        flushCode();
                    } else {
                        flushAll();
                        inCode = true;
                        codeLang = line.length() > 3 ? line.substring(3).trim() : "";
                        codeBuf.setLength(0);
                    }
                    continue;
                }
                if (inCode) {
                    codeBuf.append(raw).append('\n');
                    continue;
                }

                // ---- 裸 HTML 块（<div>…</div> 之类）
                if (!line.isEmpty() && line.charAt(0) == '<') {
                    int next = rawHtmlBlock(lines, i);
                    if (next > i) {
                        i = next - 1;
                        continue;
                    }
                }

                // ---- 合并冲突（DocMerge 写入的 <<<<<<< … >>>>>>>）
                if (line.startsWith("<<<<<<<")) {
                    flushAll();
                    inConflict = true;
                    conflict.setLength(0);
                    conflict.append(line).append('\n');
                    continue;
                }
                if (inConflict) {
                    conflict.append(raw).append('\n');
                    if (line.startsWith(">>>>>>>")) {
                        body.append(conflictBlock(conflict.toString()));
                        inConflict = false;
                    }
                    continue;
                }

                // ---- 空行
                if (line.isEmpty()) {
                    flushAll();
                    continue;
                }

                // ---- 表格
                if (isTableLine(line)) {
                    closeList();
                    flushQuote();
                    if (!inTable) {
                        inTable = true;
                        rows.clear();
                        aligns = null;
                    }
                    String[] cells = splitRow(line);
                    if (isSeparator(cells)) {
                        aligns = alignments(cells);
                    } else {
                        rows.add(cells);
                    }
                    continue;
                }
                flushTable();

                // ---- 引用（连续多行合成一块）
                if (line.startsWith(">")) {
                    closeList();
                    flushQuote();
                    quote.add(line.substring(1).trim());
                    continue;
                }
                flushQuote();

                // ---- 分隔线
                if (line.matches("^(-{3,}|\\*{3,}|_{3,})$")) {
                    closeList();
                    body.append(ruleLine());
                    continue;
                }

                // ---- 标题
                if (line.charAt(0) == '#') {
                    int level = 0;
                    while (level < line.length() && line.charAt(level) == '#') {
                        level++;
                    }
                    if (level <= 6 && level < line.length() && line.charAt(level) == ' ') {
                        closeList();
                        body.append(heading(level, inline(line.substring(level + 1).trim())));
                        continue;
                    }
                }

                // ---- 列表
                boolean ul = line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ");
                boolean ol = line.matches("^\\d+[.)]\\s+.*");
                if (ul || ol) {
                    if (!inList || ordered != ol) {
                        closeList();
                        body.append(ol
                                ? "<ol style=\"margin:2px 0 14px 22px\">"
                                : "<ul style=\"margin:2px 0 14px 22px\">");
                        inList = true;
                        ordered = ol;
                    }
                    String text = ul ? line.substring(2).trim() : line.replaceFirst("^\\d+[.)]\\s+", "");
                    body.append("<li style=\"margin:0 0 5px 0\">").append(taskOrInline(text)).append("</li>");
                    continue;
                }
                closeList();

                body.append("<p style=\"margin:0 0 12px 0\">").append(inline(line)).append("</p>");
            }

            flushCode();
            flushAll();
            if (inConflict && conflict.length() > 0) {
                body.append(conflictBlock(conflict.toString()));
            }
            return body.toString();
        }

        private void flushCode() {
            if (!inCode) {
                return;
            }
            String raw = codeBuf.toString();
            if (isHtmlLang(codeLang)) {
                body.append(htmlBlock(raw));
            } else {
                body.append(codeBlock(raw, codeLang));
            }
            codeBuf.setLength(0);
            codeLang = "";
            inCode = false;
        }

        private void closeList() {
            if (inList) {
                body.append(ordered ? "</ol>" : "</ul>");
                inList = false;
            }
        }

        private void flushQuote() {
            if (!quote.isEmpty()) {
                body.append(quoteBlock(quote));
            }
        }

        private void flushTable() {
            if (inTable) {
                body.append(table(rows, aligns));
                rows.clear();
                aligns = null;
                inTable = false;
            }
        }

        private void flushAll() {
            closeList();
            flushTable();
            flushQuote();
        }

        /** 裸 HTML 块：从第 i 行开始吃掉整块，返回下一行下标；不是 HTML 块则原样返回 i。 */
        private int rawHtmlBlock(String[] lines, int i) {
            String first = lines[i].trim();
            String tag = leadingBlockTag(first);
            if (tag == null) {
                return i;
            }
            if (isVoidTag(tag) || countTag(first, tag) <= 0) {
                // 单行自闭合 / 单行完整
                closeList();
                flushTable();
                flushQuote();
                body.append(htmlBlock(first));
                return i + 1;
            }
            int depth = 0;
            StringBuilder sb = new StringBuilder();
            int j = i;
            while (j < lines.length && j - i < 300) {
                String l = lines[j];
                if (j > i && l.trim().isEmpty()) {
                    break;
                }
                sb.append(l).append('\n');
                depth += countTag(l, tag);
                j++;
                if (depth <= 0) {
                    break;
                }
            }
            closeList();
            flushTable();
            flushQuote();
            body.append(htmlBlock(sb.toString()));
            return j;
        }
    }

    // ------------------------------------------------------------------ 块级片段

    private static String heading(int level, String text) {
        if (level == 1) {
            return "<div style=\"font-size:17pt;font-weight:bold;color:" + C_TITLE
                    + ";margin:2px 0 10px 0\">" + text + "</div>"
                    + "<table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\""
                    + " style=\"margin:0 0 18px 0\"><tr><td bgcolor=\"" + C_LINE
                    + "\" height=\"1\" style=\"padding:0;font-size:1pt\">&#160;</td></tr></table>";
        }
        if (level == 2) {
            return "<div style=\"font-size:13.5pt;font-weight:bold;color:" + C_TITLE
                    + ";margin:20px 0 10px 0\">" + text + "</div>";
        }
        if (level == 3) {
            return "<div style=\"font-size:11.5pt;font-weight:bold;color:#20293D"
                    + ";margin:17px 0 8px 0\">" + text + "</div>";
        }
        return "<div style=\"font-size:10.5pt;font-weight:bold;color:#333C50"
                + ";margin:14px 0 6px 0\">" + text + "</div>";
    }

    private static String ruleLine() {
        return "<table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\""
                + " style=\"margin:16px 0 20px 0\"><tr><td bgcolor=\"" + C_LINE
                + "\" height=\"1\" style=\"padding:0;font-size:1pt\">&#160;</td></tr></table>";
    }

    /** 引用块：左侧竖条 + 淡蓝底卡片。 */
    private static String quoteBlock(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\"")
                .append(" style=\"margin:4px 0 16px 0\"><tr>")
                .append("<td width=\"3\" bgcolor=\"#A9BFEC\"></td>")
                .append("<td bgcolor=\"#F6F8FE\" style=\"padding:11px 14px\">")
                .append("<font color=\"#4A5773\">");
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append("<br>");
            }
            sb.append(inline(lines.get(i)));
        }
        sb.append("</font></td></tr></table>");
        lines.clear();
        return sb.toString();
    }

    /** 代码块：深色卡片 + 语言标签（用 &lt;br&gt; 手动换行，pre 会重置前景色）。 */
    private static String codeBlock(String code, String lang) {
        String c = code.endsWith("\n") ? code.substring(0, code.length() - 1) : code;
        StringBuilder sb = new StringBuilder();
        sb.append("<table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\"")
                .append(" style=\"margin:6px 0 18px 0\"><tr>")
                .append("<td bgcolor=\"#0F172A\" style=\"padding:12px 14px\">");
        if (lang != null && !lang.isEmpty()) {
            sb.append("<font face=\"Consolas\" color=\"#8494B0\" size=\"-1\">")
                    .append(escape(lang)).append("</font><br>");
        }
        sb.append("<font face=\"Consolas\" color=\"#E8EEF7\" size=\"-1\">")
                .append(monoLines(c))
                .append("</font></td></tr></table>");
        return sb.toString();
    }

    /** HTML 块：原样输出（已做安全过滤）。 */
    private static String htmlBlock(String raw) {
        String html = sanitizeHtml(raw == null ? "" : raw);
        if (html.trim().isEmpty()) {
            return "";
        }
        return "<div style=\"margin:6px 0 16px 0\">" + html + "</div>";
    }

    /** 合并冲突块：琥珀色警示卡片。 */
    private static String conflictBlock(String text) {
        return "<table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\""
                + " style=\"margin:6px 0 16px 0\"><tr>"
                + "<td width=\"3\" bgcolor=\"#F2A33C\"></td>"
                + "<td bgcolor=\"#FFF8EA\" style=\"padding:10px 14px\">"
                + "<font color=\"#8A5A0B\" size=\"-1\"><b>冲突片段，需要人工合并</b></font><br>"
                + "<font face=\"Consolas\" color=\"#7A4A00\" size=\"-1\">"
                + monoLines(text.endsWith("\n") ? text.substring(0, text.length() - 1) : text)
                + "</font></td></tr></table>";
    }

    /** 多行代码 → 保留缩进的 HTML 文本（空格转 nbsp，换行转 br）。 */
    private static String monoLines(String code) {
        StringBuilder sb = new StringBuilder();
        String[] ls = code.split("\n", -1);
        for (int i = 0; i < ls.length; i++) {
            if (i > 0) {
                sb.append("<br>");
            }
            String line = ls[i].replace("\t", "    ");
            int sp = 0;
            while (sp < line.length() && line.charAt(sp) == ' ') {
                sp++;
            }
            for (int k = 0; k < sp; k++) {
                sb.append("&#160;");
            }
            sb.append(escape(line.substring(sp)));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ 表格

    static boolean isTableLine(String line) {
        return line.startsWith("|") && line.endsWith("|") && line.length() > 2;
    }

    private static boolean isSeparator(String[] cells) {
        if (cells.length == 0) {
            return false;
        }
        for (String c : cells) {
            if (!c.trim().matches(":?-{2,}:?")) {
                return false;
            }
        }
        return true;
    }

    /** 从分隔行读出每列对齐（:--- 左 / ---: 右 / :---: 居中）。 */
    private static String[] alignments(String[] cells) {
        String[] out = new String[cells.length];
        for (int i = 0; i < cells.length; i++) {
            String c = cells[i].trim();
            boolean left = c.startsWith(":");
            boolean right = c.endsWith(":");
            out[i] = left && right ? "center" : right ? "right" : "left";
        }
        return out;
    }

    /**
     * 表格：1px 浅色外框 + 表头浅底 + 表头下分割线 + 行间细线 + 交替浅纹。
     * Swing 不支持 border-radius，bordercolor 也不生效，所以外框用「底色单元格 + 1px 内边距」拼出来。
     */
    private static String table(List<String[]> rows, String[] aligns) {
        if (rows.isEmpty()) {
            return "";
        }
        int cols = 0;
        for (String[] r : rows) {
            cols = Math.max(cols, r.length);
        }
        if (cols == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\"")
                .append(" style=\"margin:6px 0 18px 0\"><tr>")
                .append("<td bgcolor=\"").append(T_FRAME).append("\" style=\"padding:1px\">")
                .append("<table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\"")
                .append(" bgcolor=\"#FFFFFF\">");

        // 表头
        String[] head = rows.get(0);
        sb.append("<tr bgcolor=\"").append(T_HEAD).append("\">");
        for (int c = 0; c < cols; c++) {
            String v = c < head.length ? head[c].trim() : "";
            sb.append("<td style=\"padding:9px 10px;font-size:10pt\"").append(alignAttr(aligns, c)).append(">")
                    .append("<font color=\"").append(C_TITLE).append("\"><b>")
                    .append(inline(v)).append("</b></font></td>");
        }
        sb.append("</tr>");
        if (rows.size() > 1) {
            lineRow(sb, cols, T_HEAD_LINE);
        }
        // 数据行
        for (int i = 1; i < rows.size(); i++) {
            String[] r = rows.get(i);
            boolean zebra = i % 2 == 0;
            sb.append(zebra ? "<tr bgcolor=\"" + T_ZEBRA + "\">" : "<tr>");
            for (int c = 0; c < cols; c++) {
                String v = c < r.length ? r[c].trim() : "";
                sb.append("<td style=\"padding:8px 10px;font-size:10pt\"").append(alignAttr(aligns, c)).append(">")
                        .append(inline(v)).append("</td>");
            }
            sb.append("</tr>");
            if (i < rows.size() - 1) {
                lineRow(sb, cols, T_ROW_LINE);
            }
        }
        sb.append("</table></td></tr></table>");
        return sb.toString();
    }

    private static void lineRow(StringBuilder sb, int cols, String color) {
        sb.append("<tr><td colspan=\"").append(cols).append("\" bgcolor=\"").append(color)
                .append("\" height=\"1\" style=\"padding:0;font-size:1pt\">&#160;</td></tr>");
    }

    private static String alignAttr(String[] aligns, int col) {
        if (aligns == null || col >= aligns.length) {
            return "";
        }
        String a = aligns[col];
        return "left".equals(a) ? "" : " align=\"" + a + "\"";
    }

    // ------------------------------------------------------------------ 行内

    /** 任务列表：- [ ] / - [x] 渲染成勾选框。 */
    private static String taskOrInline(String text) {
        if (text.startsWith("[ ] ")) {
            return "<font color=\"#8A94A8\">☐</font> " + inline(text.substring(4).trim());
        }
        if (text.startsWith("[x] ") || text.startsWith("[X] ")) {
            return "<font color=\"#1FBF6B\">☑</font> " + inline(text.substring(4).trim());
        }
        return inline(text);
    }

    private static String[] splitRow(String line) {
        String s = line.trim();
        if (s.startsWith("|")) {
            s = s.substring(1);
        }
        if (s.endsWith("|")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.split("\\|", -1);
    }

    /** 行内格式。 */
    public static String inline(String s) {
        List<String> codes = new ArrayList<String>();
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '`') {
                int end = s.indexOf('`', i + 1);
                if (end > i) {
                    codes.add(s.substring(i + 1, end));
                    sb.append('\u0001').append(codes.size() - 1).append('\u0001');
                    i = end + 1;
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        String t = escape(sb.toString());

        // 链接
        t = t.replaceAll("\\[([^\\]]+)\\]\\(([^)\\s]+)\\)", "<a href=\"$2\">$1</a>");
        // 图片降级为链接文本
        t = t.replaceAll("!\\[([^\\]]*)\\]\\(([^)\\s]+)\\)", "<a href=\"$2\">[图片] $1</a>");
        // 粗体
        t = t.replaceAll("\\*\\*([^*]+)\\*\\*", "<b>$1</b>");
        t = t.replaceAll("__([^_]+)__", "<b>$1</b>");
        // 斜体
        t = t.replaceAll("(?<![*\\w])\\*([^*\\n]+)\\*(?![*\\w])", "<i>$1</i>");
        // 删除线
        t = t.replaceAll("~~([^~]+)~~", "<strike>$1</strike>");
        // 行内代码
        for (int k = 0; k < codes.size(); k++) {
            t = t.replace("\u0001" + k + "\u0001",
                    "<font face=\"Consolas\" color=\"#B5305E\">" + escape(codes.get(k)) + "</font>");
        }
        return t;
    }

    public static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ------------------------------------------------------------------ HTML 块工具

    private static boolean isHtmlLang(String lang) {
        if (lang == null) {
            return false;
        }
        String l = lang.trim().toLowerCase();
        return l.equals("html") || l.equals("htm") || l.equals("xml") || l.startsWith("html ");
    }

    /** 行首是不是块级 HTML 标签，是则返回标签名。 */
    private static String leadingBlockTag(String line) {
        if (line.length() < 3 || line.charAt(0) != '<' || line.charAt(1) == '/') {
            return null;
        }
        StringBuilder name = new StringBuilder();
        int i = 1;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                name.append(Character.toLowerCase(c));
                i++;
            } else {
                break;
            }
        }
        String tag = name.toString();
        for (String t : BLOCK_TAGS) {
            if (t.equals(tag)) {
                return tag;
            }
        }
        return null;
    }

    private static final String[] BLOCK_TAGS = {
            "div", "table", "p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol",
            "blockquote", "section", "article", "figure", "pre", "center", "details", "img", "hr", "br"
    };

    private static boolean isVoidTag(String tag) {
        return "img".equals(tag) || "hr".equals(tag) || "br".equals(tag);
    }

    /** 某行里 &lt;tag 与 &lt;/tag&gt; 的数量差（判断 HTML 块是否闭合）。 */
    private static int countTag(String line, String tag) {
        String lower = line.toLowerCase();
        String open = "<" + tag;
        String close = "</" + tag;
        int depth = 0;
        int i = 0;
        while (i < lower.length()) {
            int o = lower.indexOf(close, i);
            int p = lower.indexOf(open, i);
            if (o < 0 && p < 0) {
                break;
            }
            if (o >= 0 && (p < 0 || o < p)) {
                depth--;
                i = o + close.length();
            } else {
                int after = p + open.length();
                boolean boundary = after >= lower.length()
                        || !Character.isLetterOrDigit(lower.charAt(after));
                if (boundary) {
                    depth++;
                }
                i = after;
            }
        }
        return depth;
    }

    /** 去掉脚本 / 内联事件 / javascript: 之类的内容，避免文档夹带可执行片段。 */
    static String sanitizeHtml(String s) {
        String t = s == null ? "" : s;
        t = t.replaceAll("(?is)<script.*?</script\\s*>", "");
        t = t.replaceAll("(?is)<iframe.*?</iframe\\s*>", "");
        t = t.replaceAll("(?is)<object.*?</object\\s*>", "");
        t = t.replaceAll("(?is)<embed[^>]*>", "");
        t = t.replaceAll("(?is)\\son[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)", "");
        t = t.replaceAll("(?i)javascript:", "");
        return t;
    }

    // ------------------------------------------------------------------ 摘要

    /** 取纯文本摘要（文档列表用）。 */
    public static String plain(String md, int max) {
        if (md == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String line : md.replace("\r", "").split("\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("```")) {
                continue;
            }
            t = t.replaceAll("^#+\\s*", "")
                    .replaceAll("^[->*+]\\s+", "")
                    .replaceAll("\\*\\*|__|~~|`", "")
                    .replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1")
                    .replaceAll("<[^>]+>", "");
            if (sb.length() > 0) {
                sb.append("  ");
            }
            sb.append(t);
            if (sb.length() > max * 2) {
                break;
            }
        }
        String out = sb.toString();
        return out.length() > max ? out.substring(0, max) + "…" : out;
    }
}
