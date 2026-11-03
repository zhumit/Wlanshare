package com.wlanshare.app;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.wlanshare.core.Node;
import com.wlanshare.core.Peer;

import java.util.List;

/** 附近设备列表。 */
public class DevicesView implements Section {

    private final MainActivity act;
    private final Node node;
    private final ScrollView scroll;
    private final LinearLayout list;

    public DevicesView(MainActivity act, Node node) {
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

    @Override
    public void onShow() {
        refresh();
    }

    @Override
    public void refresh() {
        list.removeAllViews();
        List<Peer> peers = node.peerList();
        if (peers.isEmpty()) {
            list.addView(emptyCard());
            return;
        }
        for (Peer p : peers) {
            list.addView(peerCard(p));
            list.addView(Ui.spacer(act, 10));
        }
    }

    private View emptyCard() {
        LinearLayout card = Ui.card(act);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(0, Ui.dp(act, 30), 0, Ui.dp(act, 30));
        android.widget.ImageView ic = Ui.icon(act, R.drawable.ic_devices, 0xFFC3CDE0, 46);
        card.addView(ic);
        TextView t = Ui.text(act, "正在搜索附近设备…", 15, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(act, 14), 0, 0);
        card.addView(t, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView s = Ui.text(act, "请确认双方连在同一个 WiFi，并且客户端都在运行", 12, Ui.MUTED, false);
        s.setGravity(Gravity.CENTER);
        s.setPadding(Ui.dp(act, 24), Ui.dp(act, 6), Ui.dp(act, 24), 0);
        card.addView(s, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private View peerCard(final Peer p) {
        LinearLayout card = Ui.card(act);
        LinearLayout top = Ui.row(act);
        top.addView(Ui.avatar(act, p.id, p.nick, 44));

        LinearLayout info = Ui.column(act);
        info.setPadding(Ui.dp(act, 12), 0, 0, 0);
        TextView name = Ui.text(act, p.nick, 15, Ui.TEXT, true);
        info.addView(name);

        String type = p.deviceType == 1 ? "手机" : p.deviceType == 0 ? "电脑" : "设备";
        TextView dev = Ui.text(act, type + " · " + p.device, 11, Ui.SUB, false);
        dev.setPadding(0, Ui.dp(act, 3), 0, 0);
        info.addView(dev);

        TextView st = Ui.text(act, (p.host.isEmpty() ? "未知地址" : p.host) + (p.connected ? " · 已连接" : " · 待连接"),
                11, p.connected ? Ui.GREEN : Ui.MUTED, false);
        st.setPadding(0, Ui.dp(act, 2), 0, 0);
        info.addView(st);

        top.addView(info, Ui.lpWeight(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(top);

        LinearLayout actions = Ui.row(act);
        actions.setPadding(0, Ui.dp(act, 10), 0, 0);
        if (p.friend) {
            TextView tag = Ui.text(act, "已是好友", 11, Ui.GREEN, true);
            tag.setPadding(0, 0, Ui.dp(act, 10), 0);
            actions.addView(tag);
        } else {
            actions.addView(Ui.pill(act, "加好友", Ui.ACCENT, Ui.ACCENT_SOFT, new Runnable() {
                @Override
                public void run() {
                    node.requestFriend(p.id);
                    act.toast("已发送好友申请");
                    refresh();
                }
            }));
            actions.addView(Ui.spacer(act, 1));
        }
        actions.addView(Ui.pill(act, "发消息", Ui.SUB, Ui.SOFT, new Runnable() {
            @Override
            public void run() {
                ((ChatView) findSection("chat")).openWith(p.id);
                act.selectPublic("chat");
            }
        }));
        actions.addView(Ui.spacer(act, 1));
        actions.addView(Ui.pill(act, "传文件", Ui.SUB, Ui.SOFT, new Runnable() {
            @Override
            public void run() {
                ((FilesView) findSection("files")).setTarget(p.id);
                act.selectPublic("files");
            }
        }));
        card.addView(actions);

        Ui.click(top, new Runnable() {
            @Override
            public void run() {
                ((ChatView) findSection("chat")).openWith(p.id);
                act.selectPublic("chat");
            }
        });
        card.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                act.promptAlias(p.id);
                return true;
            }
        });
        return card;
    }

    private Section findSection(String key) {
        return act.section(key);
    }
}
