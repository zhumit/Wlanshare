package com.wlanshare.app;

import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.wlanshare.core.Node;
import com.wlanshare.core.Peer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 消息：会话列表 + 聊天详情（两级页面）。 */
public class ChatView implements Section {

    private final MainActivity act;
    private final Node node;
    private final FrameLayout root;
    private String current;
    private boolean chatMode;
    private LinearLayout msgBox;
    private ScrollView msgScroll;
    private TextView headStatus;
    private EditText input;
    private LinearLayout quoteBar;
    private TextView voiceBtn;
    private String quoteId = "";
    private String quoteText = "";
    private String quoteName = "";
    private Rec.Recorder rec;
    private Runnable recTick;
    private android.media.MediaPlayer player;
    private String playingMid = "";

    public ChatView(MainActivity act, Node node) {
        this.act = act;
        this.node = node;
        root = new FrameLayout(act);
        root.setBackgroundColor(Ui.BG);
        showList();
    }

    @Override
    public View view() {
        return root;
    }

    @Override
    public void onShow() {
        if (!chatMode) {
            showList();
        }
    }

    @Override
    public void refresh() {
        if (chatMode) {
            updateHead();
            rebuildMessages();
        } else {
            showList();
        }
    }

    @Override
    public boolean back() {
        if (chatMode) {
            showList();
            return true;
        }
        return false;
    }

    public void openWith(String peerId) {
        node.activeChat = peerId;
        node.markRead(peerId);
        if (peerId == null) {
            return;
        }
        current = peerId;
        node.loadChatHistory(peerId);
        chatMode = true;
        buildChat();
    }

    private Map<String, Peer> conversations() {
        Map<String, Peer> map = new LinkedHashMap<String, Peer>();
        for (Peer p : node.friendPeerList()) {
            map.put(p.id, p);
        }
        for (Peer p : node.peerList()) {
            if (!map.containsKey(p.id)) {
                map.put(p.id, p);
            }
        }
        return map;
    }

    private void showList() {
        chatMode = false;
        current = null;
        node.activeChat = "";
        root.removeAllViews();
        ScrollView sv = new ScrollView(act);
        LinearLayout list = Ui.column(act);
        list.setPadding(Ui.dp(act, 14), Ui.dp(act, 2), Ui.dp(act, 14), Ui.dp(act, 14));

        Map<String, Peer> convs = conversations();
        if (convs.isEmpty()) {
            LinearLayout card = Ui.card(act);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setPadding(0, Ui.dp(act, 30), 0, Ui.dp(act, 30));
            card.addView(Ui.icon(act, R.drawable.ic_chat, 0xFFC3CDE0, 44));
            TextView t = Ui.text(act, "还没有联系人", 15, Ui.TEXT, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(act, 12), 0, 0);
            card.addView(t, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView s = Ui.text(act, "附近设备出现后会自动列在这里", 12, Ui.MUTED, false);
            s.setGravity(Gravity.CENTER);
            s.setPadding(0, Ui.dp(act, 4), 0, 0);
            card.addView(s, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            list.addView(card);
        } else {
            for (final Peer p : convs.values()) {
                list.addView(convRow(p));
                list.addView(Ui.spacer(act, 8));
            }
        }
        sv.addView(list, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv);
    }

    private View convRow(final Peer p) {
        LinearLayout card = Ui.card(act);
        LinearLayout row = Ui.row(act);
        row.addView(Ui.avatar(act, p.id, p.nick, 42));

        LinearLayout info = Ui.column(act);
        info.setPadding(Ui.dp(act, 12), 0, 0, 0);
        info.addView(Ui.text(act, node.nameOf(p.id), 15, Ui.TEXT, true));

        List<Node.ChatMsg> msgs = node.chatList(p.id);
        String preview = msgs.isEmpty()
                ? (p.connected ? "在线，可以开始聊天" : "离线")
                : (msgs.get(msgs.size() - 1).mine ? "我：" : "") + msgs.get(msgs.size() - 1).text.replace("\n", " ");
        TextView pv = Ui.text(act, preview, 12, Ui.MUTED, false);
        pv.setSingleLine(true);
        pv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        pv.setPadding(0, Ui.dp(act, 3), 0, 0);
        info.addView(pv);

        row.addView(info, Ui.lpWeight(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout right = Ui.column(act);
        right.setGravity(Gravity.END);
        if (!msgs.isEmpty()) {
            right.addView(Ui.text(act, Ui.timeText(msgs.get(msgs.size() - 1).ts), 10, 0xFFB0B8C8, false));
        }
        int unread = node.unreadOf(p.id);
        if (unread > 0) {
            TextView badge = Ui.text(act, unread > 99 ? "99+" : String.valueOf(unread),
                    10, 0xFFFFFFFF, true);
            badge.setBackground(Ui.round(0xFFF2404D, 10, act));
            badge.setGravity(Gravity.CENTER);
            badge.setPadding(Ui.dp(act, 6), 0, Ui.dp(act, 6), Ui.dp(act, 1));
            LinearLayout.LayoutParams blp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.topMargin = Ui.dp(act, 4);
            right.addView(badge, blp);
        } else if (p.connected) {
            TextView dot = Ui.text(act, "在线", 10, Ui.GREEN, false);
            dot.setPadding(0, Ui.dp(act, 4), 0, 0);
            right.addView(dot);
        }
        row.addView(right);

        card.addView(row);
        Ui.click(card, new Runnable() {
            @Override
            public void run() {
                openWith(p.id);
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

    private void buildChat() {
        root.removeAllViews();
        LinearLayout col = Ui.column(act);

        // 顶栏
        LinearLayout head = Ui.row(act);
        head.setBackgroundColor(0xFFFFFFFF);
        head.setPadding(Ui.dp(act, 12), Ui.dp(act, 8), Ui.dp(act, 12), Ui.dp(act, 8));
        TextView back = Ui.text(act, "‹", 30, Ui.TEXT, false);
        back.setPadding(Ui.dp(act, 6), 0, Ui.dp(act, 10), Ui.dp(act, 4));
        Ui.click(back, new Runnable() {
            @Override
            public void run() {
                showList();
            }
        });
        head.addView(back);
        head.addView(Ui.avatar(act, current, node.nameOf(current), 34));

        LinearLayout who = Ui.column(act);
        who.setPadding(Ui.dp(act, 10), 0, 0, 0);
        who.addView(Ui.text(act, node.nameOf(current), 15, Ui.TEXT, true));
        headStatus = Ui.text(act, "", 11, Ui.MUTED, false);
        headStatus.setPadding(0, Ui.dp(act, 2), 0, 0);
        who.addView(headStatus);
        who.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                act.promptAlias(current);
            }
        });
        head.addView(who, Ui.lpWeight(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(Ui.pill(act, "备注", Ui.SUB, Ui.SOFT, new Runnable() {
            @Override
            public void run() {
                act.promptAlias(current);
            }
        }));
        LinearLayout.LayoutParams rp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.leftMargin = Ui.dp(act, 6);
        head.addView(Ui.pill(act, "传文件", Ui.SUB, Ui.SOFT, new Runnable() {
            @Override
            public void run() {
                ((FilesView) act.section("files")).setTarget(current);
                act.selectPublic("files");
            }
        }));
        col.addView(head, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 消息区
        msgScroll = new ScrollView(act);
        msgBox = Ui.column(act);
        msgBox.setPadding(Ui.dp(act, 12), Ui.dp(act, 10), Ui.dp(act, 12), Ui.dp(act, 10));
        msgScroll.addView(msgBox, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(msgScroll, Ui.lpWeight(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 引用条
        quoteBar = new LinearLayout(act);
        quoteBar.setOrientation(LinearLayout.HORIZONTAL);
        quoteBar.setGravity(Gravity.CENTER_VERTICAL);
        quoteBar.setBackgroundColor(0xFFF0F4FC);
        quoteBar.setPadding(Ui.dp(act, 12), Ui.dp(act, 6), Ui.dp(act, 8), Ui.dp(act, 6));
        quoteBar.setVisibility(View.GONE);
        final TextView quoteLabel = new TextView(act);
        quoteLabel.setTextColor(0xFF6B7488);
        quoteLabel.setTextSize(11);
        quoteLabel.setSingleLine(true);
        quoteLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        quoteLabel.setTag("label");
        quoteBar.addView(quoteLabel, Ui.lpWeight(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView quoteClose = Ui.text(act, "×", 16, Ui.SUB, false);
        quoteClose.setPadding(Ui.dp(act, 10), 0, Ui.dp(act, 6), 0);
        Ui.click(quoteClose, new Runnable() {
            @Override
            public void run() {
                clearQuote();
            }
        });
        quoteBar.addView(quoteClose);
        col.addView(quoteBar, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // 输入区
        LinearLayout bar = Ui.row(act);
        bar.setBackgroundColor(0xFFFFFFFF);
        bar.setPadding(Ui.dp(act, 10), Ui.dp(act, 8), Ui.dp(act, 10), Ui.dp(act, 8));
        voiceBtn = Ui.pill(act, "", Ui.SUB, Ui.SOFT, new Runnable() {
            @Override
            public void run() {
                toggleRecord();
            }
        });
        voiceBtn.setMinWidth(Ui.dp(act, 42));
        voiceBtn.setPadding(Ui.dp(act, 11), Ui.dp(act, 6), Ui.dp(act, 11), Ui.dp(act, 6));
        voiceBtn.setCompoundDrawablePadding(Ui.dp(act, 5));
        voiceState(-1);
        LinearLayout.LayoutParams vp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        vp.rightMargin = Ui.dp(act, 8);
        bar.addView(voiceBtn, vp);
        input = new EditText(act);
        input.setHighlightColor(0x992B6BF3);
        input.setHint("说点什么…");
        input.setTextSize(14);
        input.setTextColor(Ui.TEXT);
        input.setHintTextColor(Ui.MUTED);
        input.setBackground(Ui.roundStroke(Ui.SOFT, Ui.BORDER, 12, act));
        input.setPadding(Ui.dp(act, 12), Ui.dp(act, 8), Ui.dp(act, 12), Ui.dp(act, 8));
        input.setMaxLines(4);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        bar.addView(input, Ui.lpWeight(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView send = Ui.text(act, "发送", 14, 0xFFFFFFFF, true);
        send.setBackground(Ui.round(Ui.ACCENT, 12, act));
        send.setPadding(Ui.dp(act, 16), Ui.dp(act, 9), Ui.dp(act, 16), Ui.dp(act, 9));
        Ui.click(send, new Runnable() {
            @Override
            public void run() {
                doSend();
            }
        });
        LinearLayout.LayoutParams sp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.leftMargin = Ui.dp(act, 8);
        bar.addView(send, sp);
        col.addView(bar, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(col, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        updateHead();
        rebuildMessages();
    }

    private void doSend() {
        if (input == null || current == null) {
            return;
        }
        String t = input.getText().toString().trim();
        if (t.isEmpty()) {
            return;
        }
        node.sendChat(current, t, quoteId, quoteText, quoteName);
        input.setText("");
        clearQuote();
        rebuildMessages();
    }

    private void updateHead() {
        Peer p = node.peers.get(current);
        boolean online = p != null && p.connected;
        if (headStatus != null) {
            String dev = p == null ? "设备离线" : (p.deviceType == 1 ? "手机" : "电脑") + " · " + p.device;
            headStatus.setText((online ? "在线" : "离线") + " · " + dev);
            headStatus.setTextColor(online ? Ui.GREEN : Ui.MUTED);
        }
    }

    private void rebuildMessages() {
        if (msgBox == null) {
            return;
        }
        msgBox.removeAllViews();
        List<Node.ChatMsg> list = node.chatList(current);
        if (list.isEmpty()) {
            TextView t = Ui.text(act, "还没有消息，打个招呼吧", 13, Ui.MUTED, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(act, 40), 0, 0);
            msgBox.addView(t, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            long last = 0;
            for (Node.ChatMsg m : list) {
                if (m.ts - last > 300000) {
                    TextView time = Ui.text(act, Ui.timeText(m.ts), 10, 0xFFB0B8C8, false);
                    time.setGravity(Gravity.CENTER);
                    time.setPadding(0, Ui.dp(act, 8), 0, Ui.dp(act, 4));
                    msgBox.addView(time, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
                    last = m.ts;
                }
                msgBox.addView(bubble(m));
            }
        }
        msgScroll.post(new Runnable() {
            @Override
            public void run() {
                msgScroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private View bubble(final Node.ChatMsg m) {
        LinearLayout row = Ui.row(act);
        row.setPadding(0, Ui.dp(act, 3), 0, Ui.dp(act, 3));
        row.setGravity(m.mine ? Gravity.END : Gravity.START);

        if (m.recalled) {
            TextView tip = Ui.text(act, m.mine ? "你撤回了一条消息" : "对方撤回了一条消息",
                    11, 0xFF8D97AB, false);
            tip.setGravity(Gravity.CENTER);
            tip.setBackground(Ui.round(0xFFE9EDF6, 10, act));
            tip.setPadding(Ui.dp(act, 14), Ui.dp(act, 5), Ui.dp(act, 14), Ui.dp(act, 5));
            LinearLayout wrap = Ui.row(act);
            wrap.setGravity(Gravity.CENTER);
            wrap.setPadding(0, Ui.dp(act, 2), 0, Ui.dp(act, 2));
            wrap.addView(tip);
            return wrap;
        }

        LinearLayout box = Ui.column(act);
        box.setBackground(Ui.round(m.mine ? Ui.ACCENT : 0xFFF1F4FA, 14, act));
        box.setPadding(Ui.dp(act, 12), Ui.dp(act, 9), Ui.dp(act, 12), Ui.dp(act, 9));
        int max = (int) (act.getResources().getDisplayMetrics().widthPixels * 0.66f);

        if (m.voice) {
            final TextView play = Ui.text(act,
                    (m.mid.equals(playingMid) ? "■ " : "▶ ") + (m.voiceDur / 60) + ":" + String.format("%02d", m.voiceDur % 60)
                            + "    " + repeatBar(m.voiceDur),
                    13, m.mine ? 0xFFFFFFFF : Ui.ACCENT, true);
            play.setTag(m.mid);
            Ui.click(play, new Runnable() {
                @Override
                public void run() {
                    playVoice(m);
                }
            });
            box.addView(play);
        } else {
            TextView b = Ui.text(act, m.text, 14, m.mine ? 0xFFFFFFFF : 0xFF27303F, false);
            b.setMaxWidth(max);
            box.addView(b);
        }

        if (!m.quoteText.isEmpty()) {
            LinearLayout q = Ui.row(act);
            q.setBackground(Ui.round(m.mine ? 0x33FFFFFF : 0xFFE4E9F3, 8, act));
            View bar = new View(act);
            bar.setBackgroundColor(m.mine ? 0x99FFFFFF : 0xFFAEBACF);
            q.addView(bar, Ui.lp(Ui.dp(act, 3), ViewGroup.LayoutParams.MATCH_PARENT));

            LinearLayout inner = Ui.column(act);
            inner.setPadding(Ui.dp(act, 8), Ui.dp(act, 5), Ui.dp(act, 9), Ui.dp(act, 5));
            TextView who = Ui.text(act, m.quoteName.isEmpty() ? "引用" : m.quoteName,
                    10, m.mine ? 0xE6FFFFFF : 0xFF6B7488, true);
            TextView quoted = Ui.text(act, m.quoteText, 11,
                    m.mine ? 0xCCFFFFFF : 0xFF7D879C, false);
            quoted.setSingleLine(true);
            quoted.setEllipsize(android.text.TextUtils.TruncateAt.END);
            inner.addView(who);
            inner.addView(quoted);
            q.addView(inner, Ui.lpWeight(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            LinearLayout.LayoutParams qp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            qp.topMargin = Ui.dp(act, 6);
            box.addView(q, qp);
        }

        box.setTag(m.mid);
        attachLongPress(box, m);

        if (!m.mine) {
            row.addView(Ui.avatar(act, current, node.nameOf(current), 28));
            LinearLayout.LayoutParams lp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = Ui.dp(act, 8);
            row.addView(box, lp);
        } else {
            row.addView(box);
        }
        return row;
    }

    /** 长按菜单要挂到气泡内所有子 View 上，否则子 View 会吃掉触摸事件。 */
    private void attachLongPress(View v, final Node.ChatMsg m) {
        v.setLongClickable(true);
        v.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                showMsgMenu(m);
                return true;
            }
        });
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                attachLongPress(g.getChildAt(i), m);
            }
        }
    }

    private String repeatBar(int dur) {
        StringBuilder sb = new StringBuilder();
        int n = Math.max(3, Math.min(9, dur));
        String[] bars = {"▁", "▂", "▃", "▅", "▄", "▆", "▃", "▂", "▁"};
        for (int i = 0; i < n; i++) {
            sb.append(bars[i % bars.length]);
        }
        return sb.toString();
    }

    private void showMsgMenu(final Node.ChatMsg m) {
        final java.util.List<String> items = new java.util.ArrayList<String>();
        items.add("引用这条消息");
        if (m.voice) {
            items.add("播放语音");
        } else {
            items.add("复制文字");
        }
        final boolean canRecall = !m.recalled && m.mine && node.recallLeft(current, m.mid) > 0;
        if (canRecall) {
            items.add("撤回（" + node.recallLeft(current, m.mid) + "s）");
        }
        new android.app.AlertDialog.Builder(act)
                .setItems(items.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        String label = items.get(which);
                        if (label.startsWith("引用")) {
                            setQuote(m);
                        } else if (label.startsWith("播放")) {
                            playVoice(m);
                        } else if (label.startsWith("复制")) {
                            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                                    act.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("msg", m.text));
                            act.toast("已复制");
                        } else if (label.startsWith("撤回")) {
                            node.recallChat(current, m.mid);
                            rebuildMessages();
                        }
                    }
                })
                .show();
    }

    private void setQuote(Node.ChatMsg m) {
        quoteId = m.mid;
        quoteText = m.voice ? "[语音] " + m.voiceDur + "\"" : m.text;
        if (quoteText.length() > 60) {
            quoteText = quoteText.substring(0, 60) + "…";
        }
        quoteName = m.mine ? "我" : node.nameOf(current);
        if (quoteBar != null) {
            TextView label = (TextView) quoteBar.findViewWithTag("label");
            label.setText("引用 " + quoteName + "：" + quoteText);
            quoteBar.setVisibility(View.VISIBLE);
        }
        if (input != null) {
            input.requestFocus();
        }
    }

    private void clearQuote() {
        quoteId = "";
        quoteText = "";
        quoteName = "";
        if (quoteBar != null) {
            quoteBar.setVisibility(View.GONE);
        }
    }

    /**
     * 语音按钮状态：sec &gt;= 0 显示红色停止图标 + 计时，sec &lt; 0 只显示话筒图标。
     */
    private void voiceState(int sec) {
        boolean recording = sec >= 0;
        if (recording) {
            voiceBtn.setText(sec / 60 + ":" + String.format("%02d", sec % 60));
            voiceBtn.setTextColor(Ui.RED);
            voiceBtn.setBackground(Ui.round(0xFFFDECEE, 9, act));
            voiceBtn.setContentDescription("结束录音并发送");
            voiceIcon(R.drawable.ic_stop, Ui.RED, 16);
        } else {
            voiceBtn.setText("");
            voiceBtn.setTextColor(Ui.SUB);
            voiceBtn.setBackground(Ui.round(Ui.SOFT, 9, act));
            voiceBtn.setContentDescription("录音");
            voiceIcon(R.drawable.ic_mic, Ui.SUB, 20);
        }
    }

    /** 用带 tint 的矢量图作按钮左侧图标。 */
    private void voiceIcon(int res, int color, int sizeDp) {
        try {
            android.graphics.drawable.Drawable d =
                    act.getResources().getDrawable(res, act.getTheme());
            if (d == null) {
                return;
            }
            d = d.mutate();
            d.setTint(color);
            int s = Ui.dp(act, sizeDp);
            d.setBounds(0, 0, s, s);
            voiceBtn.setCompoundDrawables(d, null, null, null);
        } catch (Throwable ignored) {
        }
    }

    private void toggleRecord() {
        if (rec == null) {
            if (!act.ensureAudioPermission()) {
                return;
            }
            rec = new Rec.Recorder();
            if (!rec.start()) {
                rec = null;
                act.toast("无法录音，请检查麦克风权限");
                return;
            }
            if (recTick == null) {
                recTick = new Runnable() {
                    @Override
                    public void run() {
                        if (rec == null) {
                            return;
                        }
                        voiceState(rec.seconds());
                        act.getWindow().getDecorView().postDelayed(this, 300);
                    }
                };
            }
            act.getWindow().getDecorView().post(recTick);
            voiceState(0);
        } else {
            int dur = rec.seconds();
            byte[] wav = rec.stop();
            rec = null;
            voiceState(-1);
            if (wav == null) {
                act.toast("说话时间太短");
                return;
            }
            node.sendVoice(current, wav, Math.max(1, dur), quoteId, quoteText, quoteName);
            clearQuote();
            rebuildMessages();
        }
    }

    private void playVoice(Node.ChatMsg m) {
        if (m.voicePath == null || m.voicePath.isEmpty() || !new java.io.File(m.voicePath).exists()) {
            act.toast("语音文件不存在");
            return;
        }
        if (player != null) {
            try {
                player.stop();
                player.release();
            } catch (Throwable ignored) {
            }
            player = null;
        }
        if (m.mid.equals(playingMid)) {
            playingMid = "";
            rebuildMessages();
            return;
        }
        playingMid = m.mid;
        rebuildMessages();
        player = Rec.play(new java.io.File(m.voicePath), new Runnable() {
            @Override
            public void run() {
                act.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        playingMid = "";
                        rebuildMessages();
                    }
                });
            }
        });
        if (player == null) {
            playingMid = "";
            rebuildMessages();
            act.toast("无法播放这段语音");
        }
    }
}
