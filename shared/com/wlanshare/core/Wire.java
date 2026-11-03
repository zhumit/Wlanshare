package com.wlanshare.core;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;

/**
 * 帧格式：[int32 total][int32 headerLen][header JSON (UTF-8)][payload bytes]
 * total 表示 headerLen 之后剩余字节数（即 4 + headerLen + payloadLen）。
 */
public final class Wire {

    public static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int MAX_FRAME = 12 * 1024 * 1024;
    private static final int MAX_HEADER = 512 * 1024;

    private Wire() {
    }

    public static final class Frame {
        public final Map<String, Object> h;
        public final byte[] p;

        Frame(Map<String, Object> h, byte[] p) {
            this.h = h;
            this.p = p;
        }

        public String type() {
            return Json.str(h, "t", "");
        }
    }

    public static void write(DataOutputStream out, Map<String, Object> h, byte[] payload) throws IOException {
        byte[] hb = Json.write(h).getBytes(UTF8);
        byte[] p = payload == null ? new byte[0] : payload;
        out.writeInt(4 + hb.length + p.length);
        out.writeInt(hb.length);
        out.write(hb);
        if (p.length > 0) {
            out.write(p);
        }
        out.flush();
    }

    public static Frame read(DataInputStream in) throws IOException {
        int total = in.readInt();
        if (total < 4 || total > MAX_FRAME) {
            throw new IOException("bad frame size: " + total);
        }
        int hl = in.readInt();
        if (hl < 0 || hl > MAX_HEADER || hl > total) {
            throw new IOException("bad header size: " + hl);
        }
        byte[] hb = new byte[hl];
        in.readFully(hb);
        int pl = total - 4 - hl;
        byte[] pb = new byte[pl];
        if (pl > 0) {
            in.readFully(pb);
        }
        Map<String, Object> h;
        try {
            h = Json.asObj(Json.parse(new String(hb, UTF8)));
        } catch (Exception e) {
            throw new IOException("bad header json: " + e);
        }
        return new Frame(h, pb);
    }
}
