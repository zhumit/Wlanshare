package com.wlanshare.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.wlanshare.core.Node;

/** 设置：昵称、自动接收策略、本机网络信息。 */
public class SettingsView implements Section {

    private final MainActivity act;
    private final Node node;
    private final ScrollView scroll;
    private final LinearLayout list;
    private EditText nick;
    private TextView ip;
    private TextView port;
    private TextView devId;
    private TextView peers;
    private TextView broadcast;
    private boolean binding;

    public SettingsView(MainActivity act, Node node) {
        this.act = act;
        this.node = node;
        list = Ui.column(act);
        list.setPadding(Ui.dp(act, 14), Ui.dp(act, 2), Ui.dp(act, 14), Ui.dp(act, 20));
        scroll = new ScrollView(act);
        scroll.addView(list, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.setBackgroundColor(Ui.BG);
        build();
    }

    @Override
    public View view() {
        return scroll;
    }

    @Override
    public void refresh() {
        if (ip != null) {
            ip.setText(node.localIp());
            port.setText(node.tcpPort + " / UDP " + node.udpPort);
            devId.setText(node.id);
            peers.setText(node.peerList().size() + " 台");
            broadcast.setText(node.hellos.get() + " 条");
        }
    }

    private void build() {
        // 昵称
        LinearLayout c1 = Ui.card(act);
        c1.addView(Ui.text(act, "昵称", 13, Ui.TEXT, true));
        c1.addView(Ui.text(act, "局域网内其他设备看到的名字", 11, Ui.MUTED, false));
        nick = new EditText(act);
        nick.setText(node.nick);
        nick.setTextSize(14);
        nick.setSingleLine(true);
        nick.setInputType(InputType.TYPE_CLASS_TEXT);
        nick.setBackground(Ui.roundStroke(Ui.SOFT, Ui.BORDER, 10, act));
        nick.setPadding(Ui.dp(act, 12), Ui.dp(act, 9), Ui.dp(act, 12), Ui.dp(act, 9));
        LinearLayout.LayoutParams nlp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = Ui.dp(act, 10);
        c1.addView(nick, nlp);
        LinearLayout saveRow = Ui.row(act);
        saveRow.setGravity(Gravity.END);
        saveRow.setPadding(0, Ui.dp(act, 10), 0, 0);
        saveRow.addView(Ui.pill(act, "保存昵称", 0xFFFFFFFF, Ui.ACCENT, new Runnable() {
            @Override
            public void run() {
                String v = nick.getText().toString().trim();
                if (v.isEmpty()) {
                    act.toast("昵称不能为空");
                    return;
                }
                node.setNick(v);
                nick.clearFocus();
                act.toast("昵称已更新");
            }
        }));
        c1.addView(saveRow);
        list.addView(c1);
        list.addView(Ui.spacer(act, 10));

        // 开关
        LinearLayout c2 = Ui.card(act);
        c2.addView(switchRow("自动接收文件", "关闭后每个文件都需要确认", node.autoAcceptFiles,
                new SwitchListener() {
                    @Override
                    public void onChanged(boolean v) {
                        node.setAutoAcceptFiles(v);
                    }
                }));
        c2.addView(Ui.divider(act));
        c2.addView(switchRow("自动同意好友", "安装客户端即可被发现并添加", node.autoAcceptFriends,
                new SwitchListener() {
                    @Override
                    public void onChanged(boolean v) {
                        node.setAutoAcceptFriends(v);
                    }
                }));
        list.addView(c2);
        list.addView(Ui.spacer(act, 10));

        // 存储
        LinearLayout c3 = Ui.card(act);
        c3.addView(Ui.text(act, "接收目录", 13, Ui.TEXT, true));
        TextView path = Ui.text(act, node.recvDir.getAbsolutePath(), 11, Ui.SUB, false);
        path.setPadding(0, Ui.dp(act, 6), 0, 0);
        c3.addView(path);
        c3.addView(Ui.text(act, "收到的文件保存在应用私有目录，可在文件管理器中查看，"
                + "或在「文件」页点击记录导出到系统下载目录", 10, Ui.MUTED, false));
        list.addView(c3);
        list.addView(Ui.spacer(act, 10));

        // 网络信息
        LinearLayout c4 = Ui.card(act);
        c4.addView(Ui.text(act, "本机信息", 13, Ui.TEXT, true));
        ip = infoRow(c4, "本机地址", true);
        port = infoRow(c4, "端口", true);
        devId = infoRow(c4, "设备标识", true);
        peers = infoRow(c4, "已发现设备", false);
        broadcast = infoRow(c4, "广播收包", false);
        list.addView(c4);
        list.addView(Ui.spacer(act, 10));

        // 提示
        LinearLayout c5 = Ui.card(act);
        c5.addView(Ui.text(act, "使用提示", 13, Ui.TEXT, true));
        String[] tips = {
                "1. 手机和电脑需连在同一个 WiFi。",
                "2. 部分路由器开启了「AP 隔离」，会导致设备互相搜不到。",
                "3. 传输为点对点直连，不经过任何服务器。",
                "4. 文档支持离线编辑，重新连上后自动合并双方修改。",
                "5. 切到后台后仍在运行，通知栏可看到常驻状态。"
        };
        for (String t : tips) {
            TextView tv = Ui.text(act, t, 11, Ui.SUB, false);
            tv.setPadding(0, Ui.dp(act, 4), 0, 0);
            c5.addView(tv);
        }
        list.addView(c5);
        list.addView(Ui.spacer(act, 10));

        LinearLayout c6 = Ui.card(act);
        c6.setGravity(Gravity.CENTER);
        c6.addView(Ui.text(act, "WlanShare 1.0", 12, Ui.TEXT, true));
        c6.addView(Ui.text(act, "局域网文件与协作工具 · 无需服务器", 10, Ui.MUTED, false));
        list.addView(c6);
    }

    private interface SwitchListener {
        void onChanged(boolean v);
    }

    private View switchRow(String title, String desc, boolean checked, final SwitchListener l) {
        LinearLayout row = Ui.row(act);
        row.setPadding(0, Ui.dp(act, 10), 0, Ui.dp(act, 10));
        LinearLayout info = Ui.column(act);
        info.addView(Ui.text(act, title, 14, Ui.TEXT, false));
        info.addView(Ui.text(act, desc, 11, Ui.MUTED, false));
        row.addView(info, Ui.lpWeight(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final Switch sw = new Switch(act);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean v) {
                if (!binding) {
                    l.onChanged(v);
                }
            }
        });
        row.addView(sw);
        return row;
    }

    private TextView infoRow(LinearLayout parent, String key, boolean copyable) {
        LinearLayout row = Ui.row(act);
        row.setPadding(0, Ui.dp(act, 6), 0, 0);
        TextView k = Ui.text(act, key, 11, Ui.MUTED, false);
        k.setWidth(Ui.dp(act, 84));
        row.addView(k);
        final TextView v = Ui.text(act, "-", 12, Ui.TEXT, false);
        v.setSingleLine(true);
        row.addView(v, Ui.lpWeight(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (copyable) {
            TextView copy = Ui.pill(act, "复制", Ui.ACCENT, Ui.ACCENT_SOFT, new Runnable() {
                @Override
                public void run() {
                    ClipboardManager cm = (ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("wlanshare", v.getText()));
                        act.toast("已复制");
                    }
                }
            });
            row.addView(copy);
        }
        parent.addView(row);
        return v;
    }
}
