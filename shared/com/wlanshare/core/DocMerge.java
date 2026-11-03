package com.wlanshare.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 三方（base / mine / theirs）行级合并，等价于 diff3 的稳定块算法。
 *
 * 关键性质：
 * 1. 两人改动不同位置时自动合并，不产生冲突；
 * 2. 同一插入点两侧各自追加时两段都保留；
 * 3. 同一段被双方替换时才写入冲突标记；
 * 4. 结果与执行顺序无关（按作者 id 排序），保证两端最终文本完全一致。
 */
public final class DocMerge {

    private static final long CELL_LIMIT = 1500000L;

    private DocMerge() {
    }

    public static String merge(String base, String mine, String theirs) {
        return merge(base, mine, theirs, "", "", "你的修改", "对方修改");
    }

    public static String merge(String base, String mine, String theirs,
                               String mineKey, String theirsKey,
                               String mineLabel, String theirsLabel) {
        if (mine == null) {
            mine = "";
        }
        if (theirs == null) {
            theirs = "";
        }
        if (base == null) {
            base = "";
        }
        if (mine.equals(theirs)) {
            return mine;
        }
        if (base.equals(mine)) {
            return theirs;
        }
        if (base.equals(theirs)) {
            return mine;
        }

        Ctx c = new Ctx();
        c.b = base.split("\n", -1);
        c.m = mine.split("\n", -1);
        c.t = theirs.split("\n", -1);
        c.am = align(c.b, c.m);
        c.at = align(c.b, c.t);
        c.mineKey = mineKey == null ? "" : mineKey;
        c.theirsKey = theirsKey == null ? "" : theirsKey;
        c.mineLabel = mineLabel == null ? "你的修改" : mineLabel;
        c.theirsLabel = theirsLabel == null ? "对方修改" : theirsLabel;
        c.out = new ArrayList<String>();
        c.mineFirst = c.mineKey.compareTo(c.theirsKey) <= 0;

        int mi = 0;
        int ti = 0;
        int i = 0;
        while (i < c.b.length) {
            if (c.am[i] >= 0 && c.at[i] >= 0) {
                if (mi < c.am[i] || ti < c.at[i]) {
                    emit(c, i, i, mi, c.am[i], ti, c.at[i]);
                    mi = c.am[i];
                    ti = c.at[i];
                }
                c.out.add(c.b[i]);
                i++;
                mi++;
                ti++;
                continue;
            }
            int j = i;
            while (j < c.b.length && !(c.am[j] >= 0 && c.at[j] >= 0)) {
                j++;
            }
            int mEnd = j < c.b.length ? c.am[j] : c.m.length;
            int tEnd = j < c.b.length ? c.at[j] : c.t.length;
            if (mEnd < mi) {
                mEnd = mi;
            }
            if (tEnd < ti) {
                tEnd = ti;
            }
            emit(c, i, j, mi, mEnd, ti, tEnd);
            mi = mEnd;
            ti = tEnd;
            i = j;
        }
        emit(c, c.b.length, c.b.length, mi, c.m.length, ti, c.t.length);

        StringBuilder sb = new StringBuilder(mine.length() + theirs.length() + 16);
        for (int k = 0; k < c.out.size(); k++) {
            if (k > 0) {
                sb.append('\n');
            }
            sb.append(c.out.get(k));
        }
        return sb.toString();
    }

    private static final class Ctx {
        String[] b;
        String[] m;
        String[] t;
        int[] am;
        int[] at;
        List<String> out;
        String mineKey;
        String theirsKey;
        String mineLabel;
        String theirsLabel;
        boolean mineFirst;
    }

    private static void emit(Ctx c, int b0, int b1, int m0, int m1, int t0, int t1) {
        if (m1 < m0) {
            m1 = m0;
        }
        if (t1 < t0) {
            t1 = t0;
        }
        boolean mineChanged;
        boolean theirsChanged;
        if (b1 > b0) {
            mineChanged = !same(c.m, m0, m1, c.b, b0, b1);
            theirsChanged = !same(c.t, t0, t1, c.b, b0, b1);
        } else {
            mineChanged = m1 > m0;
            theirsChanged = t1 > t0;
        }

        if (!mineChanged && !theirsChanged) {
            for (int k = b0; k < b1; k++) {
                c.out.add(c.b[k]);
            }
        } else if (mineChanged && !theirsChanged) {
            for (int k = m0; k < m1; k++) {
                c.out.add(c.m[k]);
            }
        } else if (!mineChanged) {
            for (int k = t0; k < t1; k++) {
                c.out.add(c.t[k]);
            }
        } else if (same(c.m, m0, m1, c.t, t0, t1)) {
            for (int k = m0; k < m1; k++) {
                c.out.add(c.m[k]);
            }
        } else if (b0 == b1) {
            // 同一插入点两侧各自追加：两段都保留，按作者 id 固定顺序保证两端一致
            if (c.mineFirst) {
                copy(c.out, c.m, m0, m1);
                copy(c.out, c.t, t0, t1);
            } else {
                copy(c.out, c.t, t0, t1);
                copy(c.out, c.m, m0, m1);
            }
        } else {
            String firstLabel = c.mineFirst ? c.mineLabel : c.theirsLabel;
            String secondLabel = c.mineFirst ? c.theirsLabel : c.mineLabel;
            if (c.mineFirst) {
                c.out.add("<<<<<<< " + firstLabel);
                copy(c.out, c.m, m0, m1);
                c.out.add("=======");
                copy(c.out, c.t, t0, t1);
                c.out.add(">>>>>>> " + secondLabel);
            } else {
                c.out.add("<<<<<<< " + firstLabel);
                copy(c.out, c.t, t0, t1);
                c.out.add("=======");
                copy(c.out, c.m, m0, m1);
                c.out.add(">>>>>>> " + secondLabel);
            }
        }
    }

    private static void copy(List<String> out, String[] src, int from, int to) {
        for (int k = from; k < to; k++) {
            out.add(src[k]);
        }
    }

    public static boolean hasConflict(String text) {
        return text != null && text.contains("<<<<<<< ");
    }

    private static boolean same(String[] a, int a0, int a1, String[] b, int b0, int b1) {
        if (a1 - a0 != b1 - b0) {
            return false;
        }
        for (int i = 0; i < a1 - a0; i++) {
            if (!a[a0 + i].equals(b[b0 + i])) {
                return false;
            }
        }
        return true;
    }

    /** 对 base 的每一行，返回其在 other 中匹配的索引（LCS），无匹配为 -1。 */
    static int[] align(String[] base, String[] other) {
        int n = base.length;
        int m = other.length;
        int[] res = new int[n];
        for (int i = 0; i < n; i++) {
            res[i] = -1;
        }
        if (n == 0 || m == 0 || (long) n * (long) m > CELL_LIMIT) {
            return res;
        }
        int[] dp = new int[(n + 1) * (m + 1)];
        int w = m + 1;
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                if (base[i].equals(other[j])) {
                    dp[i * w + j] = dp[(i + 1) * w + j + 1] + 1;
                } else {
                    int x = dp[(i + 1) * w + j];
                    int y = dp[i * w + j + 1];
                    dp[i * w + j] = x > y ? x : y;
                }
            }
        }
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (base[i].equals(other[j])) {
                res[i] = j;
                i++;
                j++;
            } else if (dp[(i + 1) * w + j] >= dp[i * w + j + 1]) {
                i++;
            } else {
                j++;
            }
        }
        return res;
    }
}
