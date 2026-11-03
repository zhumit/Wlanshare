package com.wlanshare.app;

/**
 * Markdown → HTML（WebView 预览用）。
 * 与 PC 端保持同一套版式：表格无竖线（浅色外框 + 表头底色 + 行分隔线），支持 ```html 围栏块原样渲染。
 */
public final class Md {

    private Md() {
    }

    public static String toHtml(String md) {
        StringBuilder body = new StringBuilder();
        String[] lines = (md == null ? "" : md).replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);
        boolean inCode = false;
        boolean inList = false;
        boolean inTable = false;
        boolean tableHead = false;
        String lang = "";
        String[] aligns = null;
        StringBuilder code = new StringBuilder();

        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("```")) {
                if (inCode) {
                    String text = code.toString();
                    if ("html".equals(lang) || "htm".equals(lang)) {
                        body.append("<div class=\"htmlblock\">").append(sanitize(text)).append("</div>");
                    } else {
                        body.append("<pre><code>").append(esc(text)).append("</code></pre>");
                    }
                    code.setLength(0);
                    lang = "";
                    inCode = false;
                } else {
                    if (inList) {
                        body.append("</ul>");
                        inList = false;
                    }
                    if (inTable) {
                        body.append("</table>");
                        inTable = false;
                    }
                    lang = line.length() > 3 ? line.substring(3).trim().toLowerCase() : "";
                    inCode = true;
                }
                continue;
            }
            if (inCode) {
                code.append(raw).append('\n');
                continue;
            }
            if (line.isEmpty()) {
                if (inList) {
                    body.append("</ul>");
                    inList = false;
                }
                if (inTable) {
                    body.append("</table>");
                    inTable = false;
                }
                continue;
            }
            if (line.startsWith("|") && line.endsWith("|") && line.length() > 2) {
                String[] cells = line.substring(1, line.length() - 1).split("\\|", -1);
                boolean sep = true;
                for (String c : cells) {
                    if (!c.trim().matches(":?-{2,}:?")) {
                        sep = false;
                        break;
                    }
                }
                if (!inTable) {
                    body.append("<table>");
                    inTable = true;
                    tableHead = true;
                    aligns = null;
                }
                if (sep) {
                    aligns = alignments(cells);
                    continue;
                }
                body.append("<tr>");
                for (int i = 0; i < cells.length; i++) {
                    String tag = tableHead ? "th" : "td";
                    body.append("<").append(tag).append(alignStyle(aligns, i)).append(">")
                            .append(inline(cells[i].trim()))
                            .append("</").append(tag).append(">");
                }
                body.append("</tr>");
                tableHead = false;
                continue;
            } else if (inTable) {
                body.append("</table>");
                inTable = false;
            }
            if (line.startsWith(">")) {
                body.append("<blockquote>").append(inline(line.substring(1).trim())).append("</blockquote>");
                continue;
            }
            if (line.matches("^(-{3,}|\\*{3,}|_{3,})$")) {
                body.append("<hr>");
                continue;
            }
            if (line.startsWith("#")) {
                int level = 0;
                while (level < line.length() && line.charAt(level) == '#') {
                    level++;
                }
                if (level <= 6 && level < line.length() && line.charAt(level) == ' ') {
                    body.append("<h").append(level).append(">")
                            .append(inline(line.substring(level + 1).trim()))
                            .append("</h").append(level).append(">");
                    continue;
                }
            }
            boolean ul = line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ");
            boolean ol = line.matches("^\\d+[.)]\\s+.*");
            if (ul || ol) {
                if (!inList) {
                    body.append("<ul>");
                    inList = true;
                }
                String t = ul ? line.substring(2) : line.replaceFirst("^\\d+[.)]\\s+", "");
                body.append("<li>").append(inline(t)).append("</li>");
                continue;
            } else if (inList) {
                body.append("</ul>");
                inList = false;
            }
            body.append("<p>").append(inline(line)).append("</p>");
        }
        if (inCode) {
            String text = code.toString();
            if ("html".equals(lang) || "htm".equals(lang)) {
                body.append("<div class=\"htmlblock\">").append(sanitize(text)).append("</div>");
            } else {
                body.append("<pre><code>").append(esc(text)).append("</code></pre>");
            }
        }
        if (inList) {
            body.append("</ul>");
        }
        if (inTable) {
            body.append("</table>");
        }

        return "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<style>body{font-family:-apple-system,'Noto Sans CJK SC',sans-serif;font-size:15px;line-height:1.7;"
                + "color:#2A3446;padding:14px 16px;margin:0}h1{font-size:21px}h2{font-size:18px}h3{font-size:16px}"
                + "h1,h2,h3{margin:18px 0 8px;color:#0F172A}p{margin:8px 0}ul{margin:6px 0;padding-left:22px}"
                + "li{margin:3px 0}code{background:#F4F6FA;padding:2px 5px;border-radius:4px;font-size:13px}"
                + "pre{background:#0F172A;color:#E8EEF7;padding:12px;border-radius:8px;overflow-x:auto}"
                + "pre code{background:none;padding:0;color:inherit}blockquote{margin:10px 0;padding:8px 12px;"
                + "border-left:3px solid #A9BFEC;background:#F7F9FD;color:#5B6478;border-radius:0 8px 8px 0}"
                + "table{border-collapse:collapse;width:100%;margin:10px 0;font-size:14px;"
                + "border:1px solid #E2E9F5;border-radius:10px;overflow:hidden}"
                + "th{background:#F4F7FD;color:#0F172A;font-weight:600;text-align:left;"
                + "border-bottom:1px solid #DCE4F2}"
                + "td{border-bottom:1px solid #EEF2F8}tr:last-child td{border-bottom:none}"
                + "th,td{padding:9px 12px}"
                + ".htmlblock table{border:none}"
                + "hr{border:none;border-top:1px solid #E7ECF6;margin:16px 0}a{color:#2B6BF3}"
                + "</style></head><body>" + body + "</body></html>";
    }

    /** 从分隔行读出每列对齐。 */
    static String[] alignments(String[] cells) {
        String[] out = new String[cells.length];
        for (int i = 0; i < cells.length; i++) {
            String c = cells[i].trim();
            boolean left = c.startsWith(":");
            boolean right = c.endsWith(":");
            out[i] = left && right ? "center" : right ? "right" : "left";
        }
        return out;
    }

    static String alignStyle(String[] aligns, int col) {
        if (aligns == null || col >= aligns.length || "left".equals(aligns[col])) {
            return "";
        }
        return " style=\"text-align:" + aligns[col] + "\"";
    }

    /** 去掉脚本 / 内联事件 / javascript:，与 PC 端一致。 */
    static String sanitize(String s) {
        String t = s == null ? "" : s;
        t = t.replaceAll("(?is)<script.*?</script\\s*>", "");
        t = t.replaceAll("(?is)<iframe.*?</iframe\\s*>", "");
        t = t.replaceAll("(?is)<object.*?</object\\s*>", "");
        t = t.replaceAll("(?is)<embed[^>]*>", "");
        t = t.replaceAll("(?is)\\son[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)", "");
        t = t.replaceAll("(?i)javascript:", "");
        return t;
    }

    static String inline(String s) {
        String t = esc(s);
        t = t.replaceAll("\\[([^\\]]+)\\]\\(([^)\\s]+)\\)", "<a href=\"$2\">$1</a>");
        t = t.replaceAll("\\*\\*([^*]+)\\*\\*", "<b>$1</b>");
        t = t.replaceAll("__([^_]+)__", "<b>$1</b>");
        t = t.replaceAll("(?<![*\\w])\\*([^*\\n]+)\\*(?![*\\w])", "<i>$1</i>");
        t = t.replaceAll("~~([^~]+)~~", "<strike>$1</strike>");
        t = t.replaceAll("`([^`]+)`", "<code>$1</code>");
        return t;
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
