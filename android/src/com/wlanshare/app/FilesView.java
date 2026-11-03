package com.wlanshare.app;

import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.wlanshare.core.Node;
import com.wlanshare.core.Peer;
import com.wlanshare.core.Transfer;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.List;

/** 文件：选人发送 + 传输列表（含断点续传）。 */
public class FilesView implements Section {

    private final MainActivity act;
    private final Node node;
    private final ScrollView scroll;
    private final LinearLayout list;
    private String targetId;

    public FilesView(MainActivity act, Node node) {
        this.act = act;
        this.node = node;
        list = Ui.column(act);
        list.setPadding(Ui.dp(act, 14), Ui.dp(act, 2), Ui.dp(act, 14), Ui.dp(act, 14));
        scroll = new ScrollView(act);
        scroll.addView(list, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.setBackgroundColor(Ui.BG);
    }

    @Override
    public View view() {
        return scroll;
    }

    public void setTarget(String peerId) {
        this.targetId = peerId;
    }

    @Override
    public void onShow() {
        refresh();
    }

    @Override
    public void refresh() {
        list.removeAllViews();
        list.addView(sendCard());
        list.addView(Ui.spacer(act, 12));
        list.addView(Ui.text(act, "传输记录", 13, Ui.TEXT, true));
        list.addView(Ui.spacer(act, 8));

        List<Transfer> ts = node.transferList();
        if (ts.isEmpty()) {
            LinearLayout card = Ui.card(act);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setPadding(0, Ui.dp(act, 26), 0, Ui.dp(act, 26));
            card.addView(Ui.icon(act, R.drawable.ic_files, 0xFFC3CDE0, 40));
            TextView t = Ui.text(act, "还没有传输记录", 14, Ui.TEXT, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(act, 10), 0, 0);
            card.addView(t, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView s2 = Ui.text(act, "点上面的按钮选择文件发送", 12, Ui.MUTED, false);
            s2.setGravity(Gravity.CENTER);
            card.addView(s2, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            list.addView(card);
        } else {
            for (Transfer t : ts) {
                list.addView(transferCard(t));
                list.addView(Ui.spacer(act, 8));
            }
        }
    }

    private View sendCard() {
        LinearLayout card = Ui.card(act);
        LinearLayout head = Ui.row(act);
        card.addView(head);

        TextView to = Ui.text(act, targetName(), 13, Ui.TEXT, true);
        to.setSingleLine(true);
        to.setEllipsize(android.text.TextUtils.TruncateAt.END);
        head.addView(Ui.text(act, "发送给：", 12, Ui.SUB, false));
        head.addView(to, Ui.lpWeight(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(Ui.pill(act, "切换", Ui.ACCENT, Ui.ACCENT_SOFT, new Runnable() {
            @Override
            public void run() {
                chooseTarget();
            }
        }));

        TextView pick = Ui.button(act, "选择文件发送", Ui.ACCENT, 0xFFFFFFFF, new Runnable() {
            @Override
            public void run() {
                if (targetId == null) {
                    chooseTarget();
                    return;
                }
                act.pickFile(new MainActivity.FilePick() {
                    @Override
                    public void picked(File file) {
                        node.sendFile(targetId, file);
                        act.toast("正在发送 " + file.getName());
                        refresh();
                    }
                });
            }
        });
        LinearLayout.LayoutParams lp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(act, 44));
        lp.topMargin = Ui.dp(act, 10);
        card.addView(pick, lp);
        card.addView(Ui.text(act, "传输中断后，重新发送同一文件会自动从断点继续",
                10, Ui.MUTED, false));
        return card;
    }

    private String targetName() {
        if (targetId == null) {
            return "未选择";
        }
        return node.nameOf(targetId);
    }

    private void chooseTarget() {
        final List<Peer> peers = node.peerList();
        if (peers.isEmpty()) {
            act.toast("还没发现其他设备");
            return;
        }
        String[] names = new String[peers.size()];
        for (int i = 0; i < peers.size(); i++) {
            Peer p = peers.get(i);
            names[i] = node.nameOf(p.id) + (p.connected ? "" : "（离线）");
        }
        new android.app.AlertDialog.Builder(act)
                .setTitle("发送给")
                .setItems(names, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        targetId = peers.get(which).id;
                        refresh();
                    }
                })
                .show();
    }

    private View transferCard(final Transfer t) {
        LinearLayout card = Ui.card(act);
        LinearLayout top = Ui.row(act);
        boolean sending = t.direction == Transfer.SENDING;
        top.addView(Ui.icon(act, sending ? R.drawable.ic_files : R.drawable.ic_folder,
                sending ? Ui.ACCENT : Ui.GREEN, 20));

        LinearLayout info = Ui.column(act);
        info.setPadding(Ui.dp(act, 10), 0, 0, 0);
        TextView name = Ui.text(act, t.name, 14, Ui.TEXT, true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        info.addView(name);
        String peerName = node.nameOf(t.peerId);
        if ("未知设备".equals(peerName) && t.peerNick != null && !t.peerNick.isEmpty()) {
            peerName = t.peerNick;
        }
        String meta = (sending ? "发送给 " : "来自 ") + peerName + " · " + Node.human(t.size);
        info.addView(Ui.text(act, meta, 11, Ui.MUTED, false));
        top.addView(info, Ui.lpWeight(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        String status;
        int color;
        switch (t.state) {
            case Transfer.DONE:
                status = "已完成";
                color = Ui.GREEN;
                break;
            case Transfer.FAILED:
                status = "失败";
                color = Ui.RED;
                break;
            case Transfer.REJECTED:
                status = "已拒绝";
                color = Ui.AMBER;
                break;
            default:
                status = (t.offset > 0 ? "续传 " : "") + Math.round(t.progress() * 100) + "%";
                color = Ui.ACCENT;
        }
        TextView st = Ui.text(act, status, 12, color, true);
        top.addView(st);
        card.addView(top);

        if (t.state == Transfer.ACTIVE || t.state == Transfer.PENDING) {
            ProgressBar pb = new ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal);
            pb.setMax(1000);
            pb.setProgress((int) (t.progress() * 1000));
            pb.setProgressTintList(android.content.res.ColorStateList.valueOf(
                    sending ? Ui.ACCENT : Ui.GREEN));
            pb.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE6EBF5));
            pb.setMinimumHeight(Ui.dp(act, 4));
            LinearLayout.LayoutParams lp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(act, 6));
            lp.topMargin = Ui.dp(act, 8);
            card.addView(pb, lp);
        } else if (t.state == Transfer.FAILED) {
            TextView hint = Ui.text(act, "点此重试，将从已传部分继续", 11, Ui.ACCENT, true);
            hint.setPadding(0, Ui.dp(act, 6), 0, 0);
            card.addView(hint);
            Ui.click(card, new Runnable() {
                @Override
                public void run() {
                    node.retryTransfer(t.fid);
                    act.toast("重新发送中");
                    refresh();
                }
            });
        } else if (t.state == Transfer.DONE) {
            TextView hint = Ui.text(act, sending ? "已完成" : "点击保存到「下载」目录", 11, Ui.MUTED, false);
            hint.setPadding(0, Ui.dp(act, 6), 0, 0);
            card.addView(hint);
            if (!sending) {
                Ui.click(card, new Runnable() {
                    @Override
                    public void run() {
                        exportToDownloads(t);
                    }
                });
            }
        }
        return card;
    }

    /** 把接收到的文件复制到系统「下载」目录（Android 10+ 无需权限）。 */
    private void exportToDownloads(Transfer t) {
        File f = new File(t.path);
        if (!f.exists()) {
            act.toast("文件已不存在");
            return;
        }
        if (Build.VERSION.SDK_INT < 29) {
            act.toast("文件位于：" + t.path);
            return;
        }
        FileInputStream in = null;
        OutputStream out = null;
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Downloads.DISPLAY_NAME, t.name);
            v.put(MediaStore.Downloads.MIME_TYPE, mime(t.name));
            Uri uri = act.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) {
                act.toast("保存失败");
                return;
            }
            in = new FileInputStream(f);
            out = act.getContentResolver().openOutputStream(uri);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            act.toast("已保存到「下载」目录");
        } catch (Exception e) {
            act.toast("保存失败：" + e.getMessage());
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static String mime(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")) {
            return "image/*";
        }
        if (n.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (n.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (n.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (n.endsWith(".txt") || n.endsWith(".md")) {
            return "text/plain";
        }
        return "application/octet-stream";
    }
}
