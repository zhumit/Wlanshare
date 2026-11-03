package com.wlanshare.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 解析/生成器。PC 与 Android 共用，不依赖任何第三方库。
 */
public final class Json {

    private Json() {
    }

    public static Map<String, Object> obj() {
        return new LinkedHashMap<String, Object>();
    }

    public static Map<String, Object> map(String... kv) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    public static String str(Map<String, Object> m, String k, String d) {
        Object v = m == null ? null : m.get(k);
        if (v == null) {
            return d;
        }
        return v instanceof String ? (String) v : String.valueOf(v);
    }

    public static long num(Map<String, Object> m, String k, long d) {
        Object v = m == null ? null : m.get(k);
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        if (v instanceof String) {
            try {
                return Long.parseLong(((String) v).trim());
            } catch (Exception ignored) {
                return d;
            }
        }
        return d;
    }

    public static int i32(Map<String, Object> m, String k, int d) {
        long v = num(m, k, d);
        if (v > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (v < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) v;
    }

    public static boolean bool(Map<String, Object> m, String k, boolean d) {
        Object v = m == null ? null : m.get(k);
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        if (v instanceof String) {
            String s = (String) v;
            return "1".equals(s) || "true".equalsIgnoreCase(s);
        }
        if (v instanceof Number) {
            return ((Number) v).intValue() != 0;
        }
        return d;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Map<String, Object> m, String k) {
        Object v = m == null ? null : m.get(k);
        if (v instanceof List) {
            return (List<Object>) v;
        }
        return new ArrayList<Object>();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObj(Object o) {
        if (o instanceof Map) {
            return (Map<String, Object>) o;
        }
        return new LinkedHashMap<String, Object>();
    }

    public static String write(Object o) {
        StringBuilder sb = new StringBuilder(256);
        writeValue(sb, o);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String) {
            writeString(sb, (String) v);
        } else if (v instanceof Boolean) {
            sb.append(((Boolean) v) ? "true" : "false");
        } else if (v instanceof Number) {
            Number n = (Number) v;
            if (n instanceof Double || n instanceof Float) {
                double d = n.doubleValue();
                if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                    sb.append((long) d);
                } else {
                    sb.append(d);
                }
            } else {
                sb.append(n.longValue());
            }
        } else if (v instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                writeValue(sb, e.getValue());
            }
            sb.append('}');
        } else if (v instanceof Iterable) {
            sb.append('[');
            boolean first = true;
            for (Object it : (Iterable<?>) v) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeValue(sb, it);
            }
            sb.append(']');
        } else {
            writeString(sb, String.valueOf(v));
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    public static Object parse(String s) {
        Parser p = new Parser(s);
        Object v = p.value();
        p.ws();
        return v;
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s == null ? "" : s;
        }

        void ws() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                    i++;
                } else {
                    break;
                }
            }
        }

        Object value() {
            ws();
            if (i >= s.length()) {
                return null;
            }
            char c = s.charAt(i);
            switch (c) {
                case '{':
                    return object();
                case '[':
                    return array();
                case '"':
                    return string();
                case 't':
                    i += 4;
                    return Boolean.TRUE;
                case 'f':
                    i += 5;
                    return Boolean.FALSE;
                case 'n':
                    i += 4;
                    return null;
                default:
                    return number();
            }
        }

        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            i++; // {
            ws();
            if (i < s.length() && s.charAt(i) == '}') {
                i++;
                return m;
            }
            while (i < s.length()) {
                ws();
                String k = string();
                ws();
                if (i < s.length() && s.charAt(i) == ':') {
                    i++;
                }
                Object v = value();
                m.put(k, v);
                ws();
                if (i < s.length() && s.charAt(i) == ',') {
                    i++;
                    continue;
                }
                if (i < s.length() && s.charAt(i) == '}') {
                    i++;
                }
                break;
            }
            return m;
        }

        List<Object> array() {
            List<Object> l = new ArrayList<Object>();
            i++; // [
            ws();
            if (i < s.length() && s.charAt(i) == ']') {
                i++;
                return l;
            }
            while (i < s.length()) {
                l.add(value());
                ws();
                if (i < s.length() && s.charAt(i) == ',') {
                    i++;
                    continue;
                }
                if (i < s.length() && s.charAt(i) == ']') {
                    i++;
                }
                break;
            }
            return l;
        }

        String string() {
            StringBuilder sb = new StringBuilder();
            if (i < s.length() && s.charAt(i) == '"') {
                i++;
            }
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') {
                    break;
                }
                if (c == '\\' && i < s.length()) {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'u':
                            if (i + 4 <= s.length()) {
                                try {
                                    sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                                } catch (Exception ignored) {
                                }
                                i += 4;
                            }
                            break;
                        default:
                            sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Object number() {
            int st = i;
            while (i < s.length()) {
                char c = s.charAt(i);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                    i++;
                } else {
                    break;
                }
            }
            String t = s.substring(st, i);
            if (t.isEmpty()) {
                return null;
            }
            try {
                if (t.indexOf('.') < 0 && t.indexOf('e') < 0 && t.indexOf('E') < 0) {
                    return Long.parseLong(t);
                }
                return Double.parseDouble(t);
            } catch (Exception e) {
                return null;
            }
        }
    }
}
