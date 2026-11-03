package com.wlanshare.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 一份可协作的 Markdown 文档。 */
public class Doc {

    private static final int HIST_MAX = 40;

    public String did = "";
    public String title = "未命名文档";
    public String text = "";
    public int ver;
    public String owner = "";
    public long updatedAt = System.currentTimeMillis();
    public boolean dirty;

    /** ver -> 文本快照，用于三方合并。 */
    public final LinkedHashMap<Integer, String> hist = new LinkedHashMap<Integer, String>();
    /** 正在编辑该文档的其他成员：peerId -> 昵称 */
    public final Map<String, String> editors = new LinkedHashMap<String, String>();
    /** 协作者的光标所在行（0 基，不持久化）：peerId -> 行号 */
    public final Map<String, Integer> cursors = new LinkedHashMap<String, Integer>();
    /** 光标最后更新时间，用于超时清除 */
    public final Map<String, Long> cursorAt = new LinkedHashMap<String, Long>();

    public void putSnapshot(int v, String t) {
        hist.put(v, t);
        while (hist.size() > HIST_MAX) {
            Integer first = hist.keySet().iterator().next();
            hist.remove(first);
        }
    }

    public String snapshot(int v) {
        return hist.get(v);
    }

    public String base(String v) {
        return hist.get(v);
    }

    public int lineCount() {
        if (text.isEmpty()) {
            return 1;
        }
        int n = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }

    public int wordCount() {
        int n = 0;
        boolean in = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean w = !Character.isWhitespace(c);
            if (w && !in) {
                n++;
            }
            in = w;
        }
        return n;
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = Json.obj();
        m.put("did", did);
        m.put("title", title);
        m.put("text", text);
        m.put("ver", ver);
        m.put("owner", owner);
        m.put("updatedAt", updatedAt);
        return m;
    }

    public static Doc fromJson(Map<String, Object> m) {
        Doc d = new Doc();
        d.did = Json.str(m, "did", "");
        d.title = Json.str(m, "title", "未命名文档");
        d.text = Json.str(m, "text", "");
        d.ver = Json.i32(m, "ver", 1);
        d.owner = Json.str(m, "owner", "");
        d.updatedAt = Json.num(m, "updatedAt", System.currentTimeMillis());
        d.putSnapshot(d.ver, d.text);
        return d;
    }

    public List<String> editorNames() {
        return new ArrayList<String>(editors.values());
    }
}
