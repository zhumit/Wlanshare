package com.wlanshare.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.wlanshare.core.Node;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/** 主界面：顶部标题 + 内容区 + 底部导航。 */
public class MainActivity extends Activity implements Node.Listener {

    public interface FilePick {
        void picked(File file);
    }

    private static final int REQ_PICK = 1001;

    private App app;
    private Node node;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private FrameLayout content;
    private TextView title;
    private TextView subtitle;
    private LinearLayout navBar;
    /** 底部导航的红点：key -> 数字气泡。 */
    private final Map<String, TextView> navBadges = new LinkedHashMap<String, TextView>();
    private final LinkedHashMap<String, Section> sections = new LinkedHashMap<String, Section>();
    private final LinkedHashMap<String, LinearLayout> navItems = new LinkedHashMap<String, LinearLayout>();
    private String tab = "devices";
    private FilePick pendingPick;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = (App) getApplication();
        node = app.node;
        node.setListener(this);

        buildUi();
        startService(new Intent(this, NetService.class));
        select("devices");

        try {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 2001);
            }
        } catch (Exception ignored) {
        }
    }

    private void buildUi() {
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);

        LinearLayout head = Ui.column(this);
        int ph = Ui.dp(this, 18);
        head.setPadding(ph, Ui.dp(this, 12), ph, Ui.dp(this, 8));
        title = Ui.text(this, "附近设备", 22, Ui.TEXT, true);
        subtitle = Ui.text(this, "", 12, Ui.SUB, false);
        LinearLayout.LayoutParams tp = Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitle.setPadding(0, Ui.dp(this, 3), 0, 0);
        head.addView(title, tp);
        head.addView(subtitle);
        root.addView(head, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content = new FrameLayout(this);
        root.addView(content, Ui.lpWeight(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        navBar = Ui.row(this);
        navBar.setBackgroundColor(0xFFFFFFFF);
        navBar.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        root.addView(navBar, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 58)));

        sections.put("devices", new DevicesView(this, node));
        sections.put("chat", new ChatView(this, node));
        sections.put("files", new FilesView(this, node));
        sections.put("docs", new DocsView(this, node));
        sections.put("settings", new SettingsView(this, node));

        addNav("devices", "附近", R.drawable.ic_devices);
        addNav("chat", "消息", R.drawable.ic_chat);
        addNav("files", "文件", R.drawable.ic_files);
        addNav("docs", "文档", R.drawable.ic_doc);
        addNav("settings", "设置", R.drawable.ic_settings);

        setContentView(root);
        syncBadges();
    }

    private void addNav(final String key, String label, int iconRes) {
        LinearLayout item = Ui.column(this);
        item.setGravity(android.view.Gravity.CENTER);
        ImageView iv = Ui.icon(this, iconRes, Ui.MUTED, 22);
        TextView tv = Ui.text(this, label, 10, Ui.MUTED, false);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setIncludeFontPadding(false);

        // 图标右上角的未读红点
        FrameLayout iconWrap = new FrameLayout(this);
        iconWrap.addView(iv);
        TextView badge = Ui.text(this, "", 10, 0xFFFFFFFF, true);
        badge.setBackground(Ui.round(0xFFF2404D, 8, this));
        badge.setGravity(android.view.Gravity.CENTER);
        badge.setIncludeFontPadding(false);
        badge.setMinWidth(Ui.dp(this, 16));
        badge.setMinimumWidth(Ui.dp(this, 16));
        badge.setMinHeight(Ui.dp(this, 16));
        badge.setMinimumHeight(Ui.dp(this, 16));
        badge.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        badge.setVisibility(View.GONE);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 16),
                android.view.Gravity.END | android.view.Gravity.TOP);
        blp.rightMargin = -Ui.dp(this, 8);
        blp.topMargin = -Ui.dp(this, 2);
        iconWrap.addView(badge, blp);
        navBadges.put(key, badge);
        item.addView(iconWrap);
        LinearLayout.LayoutParams tvp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tvp.topMargin = Ui.dp(this, 4);
        item.addView(tv, tvp);
        Ui.click(item, new Runnable() {
            @Override
            public void run() {
                select(key);
            }
        });
        navBar.addView(item, Ui.lpWeight(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        navItems.put(key, item);
    }

    /** 底部导航「消息」的未读红点。 */
    private void syncBadges() {
        TextView badge = navBadges.get("chat");
        if (badge == null) {
            return;
        }
        int n = node.unreadTotal();
        if (n > 0) {
            badge.setText(n > 99 ? "99+" : String.valueOf(n));
            badge.setVisibility(View.VISIBLE);
        } else {
            badge.setVisibility(View.GONE);
        }
    }

    private void select(String key) {
        Section s = sections.get(key);
        if (s == null) {
            return;
        }
        tab = key;
        content.removeAllViews();
        View v = s.view();
        if (v.getParent() != null) {
            ((ViewGroup) v.getParent()).removeView(v);
        }
        content.addView(v, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        String[] titles = titles(key);
        title.setText(titles[0]);
        subtitle.setText(titles[1]);
        for (String k : navItems.keySet()) {
            ViewGroup item = navItems.get(k);
            boolean active = k.equals(key);
            View first = item.getChildAt(0);
            // 图标外层可能包了一层 FrameLayout（用于右上角未读红点）
            ImageView icon = first instanceof ImageView
                    ? (ImageView) first
                    : (ImageView) ((ViewGroup) first).getChildAt(0);
            TextView label = (TextView) item.getChildAt(1);
            icon.setColorFilter(active ? Ui.ACCENT : Ui.MUTED);
            label.setTextColor(active ? Ui.ACCENT : Ui.MUTED);
            label.setTypeface(null, active ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
        s.onShow();
        s.refresh();
    }

    private String[] titles(String key) {
        switch (key) {
            case "chat":
                return new String[]{"消息", "设备间直连，不经过任何服务器"};
            case "files":
                return new String[]{"文件", "传输中断后可从断点继续"};
            case "docs":
                return new String[]{"文档", "多人同时编辑 Markdown，自动合并"};
            case "settings":
                return new String[]{"设置", "配置只保存在本机"};
            default:
                return new String[]{"附近设备", "同一 WiFi 下的设备会自动出现"};
        }
    }

    // ---------------------------------------------------------------- 交互

    public void toast(String msg) {
        if (msg != null && !msg.isEmpty()) {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        }
    }

    /** 录音权限：未授权时发起申请并返回 false。 */
    public boolean ensureAudioPermission() {
        try {
            if (Build.VERSION.SDK_INT < 23) {
                return true;
            }
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return true;
            }
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 2002);
            toast("请允许录音权限后再点一次语音");
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 设置/修改对方的备注名（只影响本机显示）。 */
    public void promptAlias(final String pid) {
        if (pid == null) {
            return;
        }
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setText(node.aliasOf(pid));
        et.setHint(node.realNameOf(pid));
        et.setSingleLine(true);
        et.setSelection(et.getText().length());
        int pad = Ui.dp(this, 18);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(pad, Ui.dp(this, 6), pad, 0);
        box.addView(et);
        new android.app.AlertDialog.Builder(this)
                .setTitle("备注名 · " + node.realNameOf(pid))
                .setMessage("留空则恢复显示对方昵称")
                .setView(box)
                .setPositiveButton("保存", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        node.setAlias(pid, et.getText().toString());
                        toast("备注已更新");
                        // 立刻刷新当前界面上的名字（会话头部 / 列表）
                        Section chat = section("chat");
                        if (chat != null) {
                            chat.refresh();
                        }
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    public void pickFile(FilePick cb) {
        pendingPick = cb;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, REQ_PICK);
        } catch (Exception e) {
            toast("没有可用的文件选择器");
            pendingPick = null;
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_PICK) {
            FilePick cb = pendingPick;
            pendingPick = null;
            if (res == RESULT_OK && data != null && data.getData() != null && cb != null) {
                File f = copyToCache(data.getData());
                if (f != null) {
                    cb.picked(f);
                }
            }
        }
    }

    private File copyToCache(Uri uri) {
        InputStream in = null;
        OutputStream out = null;
        try {
            String name = null;
            android.database.Cursor c = getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (c.moveToFirst() && idx >= 0) {
                    name = c.getString(idx);
                }
                c.close();
            }
            if (name == null) {
                name = "file_" + System.currentTimeMillis();
            }
            File dir = new File(getCacheDir(), "send");
            dir.mkdirs();
            File outFile = new File(dir, name);
            in = getContentResolver().openInputStream(uri);
            out = new FileOutputStream(outFile);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return outFile;
        } catch (Exception e) {
            toast("读取文件失败：" + e.getMessage());
            return null;
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

    @Override
    public void onBackPressed() {
        Section s = sections.get(tab);
        if (s != null && s.back()) {
            return;
        }
        super.onBackPressed();
    }

    // ---------------------------------------------------------------- Node 回调

    @Override
    public void onState(String what) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                Section s = sections.get(tab);
                if (s != null) {
                    s.refresh();
                }
                syncBadges();
            }
        });
    }

    @Override
    public void onChat(String peerId, boolean mine, String text, long ts) {
        if (!mine && !peerId.equals(node.activeChat)) {
            // 新消息提示音（系统通知音），不打扰当前正在看的会话
            try {
                android.media.Ringtone r = android.media.RingtoneManager.getRingtone(this,
                        android.media.RingtoneManager.getDefaultUri(
                                android.media.RingtoneManager.TYPE_NOTIFICATION));
                if (r != null) {
                    r.play();
                }
            } catch (Throwable ignored) {
            }
        }
        ui.post(new Runnable() {
            @Override
            public void run() {
                Section s = sections.get(tab);
                if (s != null) {
                    s.refresh();
                }
                syncBadges();
            }
        });
    }

    @Override
    public void onToast(String msg) {
        final String m = msg;
        ui.post(new Runnable() {
            @Override
            public void run() {
                toast(m);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (node != null) {
            node.setListener(this);
        }
    }

    @Override
    protected void onDestroy() {
        if (node != null) {
            node.setListener(null);
        }
        super.onDestroy();
    }

    public Section section(String key) {
        return sections.get(key);
    }

    public void selectPublic(String key) {
        select(key);
    }

    public void setStatusBarLight() {
        try {
            Window w = getWindow();
            if (Build.VERSION.SDK_INT >= 23) {
                w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        } catch (Exception ignored) {
        }
    }
}
