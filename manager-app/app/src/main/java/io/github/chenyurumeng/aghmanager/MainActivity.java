package io.github.chenyurumeng.aghmanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String TOOL = "/data/adb/agh/scripts/tool.sh";
    private static final String PREFS = "agh_manager";
    private static final String KEY_REFRESH_MS = "refresh_ms";

    private static final int BG = Color.rgb(11, 15, 20);
    private static final int SURFACE = Color.rgb(20, 26, 34);
    private static final int SURFACE_ALT = Color.rgb(27, 36, 48);
    private static final int BORDER = Color.rgb(47, 59, 74);
    private static final int TEXT = Color.rgb(245, 247, 250);
    private static final int MUTED = Color.rgb(139, 152, 169);
    private static final int BLUE = Color.rgb(91, 140, 255);
    private static final int GREEN = Color.rgb(57, 217, 138);
    private static final int ORANGE = Color.rgb(255, 176, 32);
    private static final int RED = Color.rgb(255, 92, 92);

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private SharedPreferences prefs;
    private FrameLayout content;
    private LinearLayout bottomNav;
    private boolean destroyed;
    private boolean refreshing;
    private boolean actionBusy;
    private int currentPage = 0;
    private long refreshMs = 5000;
    private int logKind = 0;
    private TextView logOutput;

    private StatusSnapshot snapshot = StatusSnapshot.checking();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        refreshMs = prefs.getLong(KEY_REFRESH_MS, 5000);

        setContentView(buildShell());
        renderPage();
        refreshStatus();
        scheduleNextRefresh();
    }

    private View buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setPadding(dp(8), dp(6), dp(8), dp(8));
        bottomNav.setBackgroundColor(SURFACE);
        root.addView(bottomNav, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(68)));

        String[] labels = {"首页", "控制", "日志", "设置"};
        for (int i = 0; i < labels.length; i++) {
            final int page = i;
            TextView item = text(labels[i], 13, i == 0);
            item.setGravity(Gravity.CENTER);
            item.setTextColor(i == 0 ? BLUE : MUTED);
            item.setTag(i);
            item.setOnClickListener(v -> {
                currentPage = page;
                updateNav();
                renderPage();
                if (currentPage == 2) loadLogs();
            });
            bottomNav.addView(item, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
        }
        return root;
    }

    private void updateNav() {
        for (int i = 0; i < bottomNav.getChildCount(); i++) {
            TextView item = (TextView) bottomNav.getChildAt(i);
            boolean active = i == currentPage;
            item.setTextColor(active ? BLUE : MUTED);
            item.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private void renderPage() {
        if (content == null) return;
        content.removeAllViews();
        switch (currentPage) {
            case 1:
                content.addView(buildControlPage());
                break;
            case 2:
                content.addView(buildLogsPage());
                break;
            case 3:
                content.addView(buildSettingsPage());
                break;
            default:
                content.addView(buildHomePage());
        }
    }

    private View buildHomePage() {
        ScrollView scroll = pageScroll();
        LinearLayout root = pageColumn(scroll);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout titleBox = column();
        titleBox.addView(text("AGH Manager", 28, true));
        TextView sub = text("AdGuardHomeForRoot · Box Dual DNS", 13, false);
        sub.setTextColor(MUTED);
        titleBox.addView(sub);
        header.addView(titleBox, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView refresh = smallButton("刷新", BLUE);
        refresh.setOnClickListener(v -> refreshStatus());
        header.addView(refresh);
        root.addView(header);

        LinearLayout healthRow = new LinearLayout(this);
        healthRow.setOrientation(LinearLayout.HORIZONTAL);
        healthRow.setPadding(0, dp(16), 0, dp(4));
        healthRow.addView(chip(snapshot.rootOk ? "ROOT OK" : "ROOT ?", snapshot.rootOk ? GREEN : RED));
        healthRow.addView(chip(snapshot.moduleReady ? "MODULE READY" : "MODULE ?", snapshot.moduleReady ? GREEN : ORANGE));
        root.addView(healthRow);

        root.addView(buildRoutingCard());
        root.addView(buildInstanceCard(true));
        root.addView(buildInstanceCard(false));

        TextView title = sectionTitle("快捷操作");
        root.addView(title);
        root.addView(actionRow(
                actionButton("全部启动", "start", GREEN),
                actionButton("全部重启", "restart", BLUE),
                actionButton("全部停止", "stop", RED)
        ));

        TextView hint = text("状态每 " + refreshLabel(refreshMs) + " 自动刷新。所有 DNS 路由仍由 Box/AGH 模块负责。", 12, false);
        hint.setTextColor(MUTED);
        hint.setPadding(dp(4), dp(18), dp(4), dp(12));
        root.addView(hint);
        return scroll;
    }

    private View buildRoutingCard() {
        LinearLayout card = card();
        LinearLayout top = rowLayout();
        LinearLayout title = column();
        title.addView(text("DNS 路由", 18, true));
        TextView mode = text(snapshot.boxMode.isEmpty() ? "Box mode: checking…" : "Box mode: " + snapshot.boxMode, 12, false);
        mode.setTextColor(MUTED);
        title.addView(mode);
        top.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView badge = chip(snapshot.boxMode.contains("split-apps") ? "SPLIT APPS" : "BOX", BLUE);
        top.addView(badge);
        card.addView(top);

        LinearLayout routes = new LinearLayout(this);
        routes.setOrientation(LinearLayout.HORIZONTAL);
        routes.setPadding(0, dp(14), 0, 0);
        routes.addView(routeCell("Domestic", "5591", snapshot.port5591Up ? GREEN : RED), weight());
        routes.addView(routeCell("Foreign", "5592", snapshot.port5592Up ? GREEN : RED), weight());
        routes.addView(routeCell("Fallback", "1053", snapshot.port1053Up ? GREEN : ORANGE), weight());
        card.addView(routes);
        return card;
    }

    private View routeCell(String label, String port, int color) {
        LinearLayout box = column();
        box.setGravity(Gravity.CENTER);
        TextView dot = text("●", 18, true);
        dot.setTextColor(color);
        box.addView(dot);
        TextView p = text(":" + port, 18, true);
        box.addView(p);
        TextView l = text(label, 11, false);
        l.setTextColor(MUTED);
        box.addView(l);
        return box;
    }

    private View buildInstanceCard(boolean domestic) {
        String name = domestic ? "Domestic" : "Foreign";
        String dns = domestic ? "5591" : "5592";
        String web = domestic ? "3000" : "3001";
        boolean up = domestic ? snapshot.domesticUp : snapshot.foreignUp;
        String pid = domestic ? snapshot.domesticPid : snapshot.foreignPid;

        LinearLayout card = card();
        LinearLayout top = rowLayout();

        LinearLayout nameBox = column();
        TextView nameText = text(name, 20, true);
        nameBox.addView(nameText);
        TextView ports = text("DNS :" + dns + "   ·   Web :" + web + (pid.isEmpty() ? "" : "   ·   PID " + pid), 12, false);
        ports.setTextColor(MUTED);
        nameBox.addView(ports);
        top.addView(nameBox, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView state = chip(up ? "RUNNING" : "STOPPED", up ? GREEN : RED);
        top.addView(state);
        card.addView(top);

        LinearLayout actions = actionRow(
                outlineButton("重启", domestic ? "restart-domestic" : "restart-foreign"),
                webButton("管理界面", name + " AGH", "http://127.0.0.1:" + web)
        );
        actions.setPadding(0, dp(14), 0, 0);
        card.addView(actions);
        return card;
    }

    private View buildControlPage() {
        ScrollView scroll = pageScroll();
        LinearLayout root = pageColumn(scroll);

        root.addView(pageTitle("服务控制", "所有操作都委托给 /data/adb/agh/scripts/tool.sh"));

        root.addView(sectionTitle("全部实例"));
        LinearLayout allCard = card();
        allCard.addView(actionRow(
                actionButton("启动", "start", GREEN),
                actionButton("重启", "restart", BLUE),
                actionButton("停止", "stop", RED)
        ));
        root.addView(allCard);

        root.addView(sectionTitle("Domestic"));
        root.addView(controlInstanceCard(true));

        root.addView(sectionTitle("Foreign"));
        root.addView(controlInstanceCard(false));

        root.addView(sectionTitle("快速恢复"));
        LinearLayout rescue = card();
        TextView note = text("如果 AGH 实例异常，可先全部停止，再全部启动。Box 会根据监听状态自动更新 DNS 回退。", 13, false);
        note.setTextColor(MUTED);
        rescue.addView(note);
        TextView restart = largeButton("全部重启 AGH", BLUE);
        restart.setOnClickListener(v -> runAction("全部重启", "restart"));
        LinearLayout.LayoutParams rp = matchWrap();
        rp.setMargins(0, dp(14), 0, 0);
        rescue.addView(restart, rp);
        root.addView(rescue);
        return scroll;
    }

    private View controlInstanceCard(boolean domestic) {
        boolean up = domestic ? snapshot.domesticUp : snapshot.foreignUp;
        String name = domestic ? "Domestic" : "Foreign";
        LinearLayout card = card();
        LinearLayout status = rowLayout();
        status.addView(text(name, 18, true), new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        status.addView(chip(up ? "RUNNING" : "STOPPED", up ? GREEN : RED));
        card.addView(status);
        LinearLayout buttons = actionRow(
                actionButton("启动", domestic ? "start-domestic" : "start-foreign", GREEN),
                actionButton("重启", domestic ? "restart-domestic" : "restart-foreign", BLUE),
                actionButton("停止", domestic ? "stop-domestic" : "stop-foreign", RED)
        );
        buttons.setPadding(0, dp(12), 0, 0);
        card.addView(buttons);
        return card;
    }

    private View buildLogsPage() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(10));
        root.setBackgroundColor(BG);

        root.addView(pageTitle("日志", "Domestic / Foreign / Module"));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels = {"Domestic", "Foreign", "Module"};
        for (int i = 0; i < labels.length; i++) {
            final int which = i;
            TextView tab = smallButton(labels[i], i == logKind ? BLUE : SURFACE_ALT);
            tab.setTextColor(i == logKind ? Color.WHITE : MUTED);
            tab.setOnClickListener(v -> {
                logKind = which;
                renderPage();
                loadLogs();
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), 1);
            p.setMargins(dp(3), 0, dp(3), dp(8));
            tabs.addView(tab, p);
        }
        root.addView(tabs);

        LinearLayout tools = rowLayout();
        TextView refresh = smallButton("刷新", BLUE);
        refresh.setOnClickListener(v -> loadLogs());
        TextView copy = smallButton("复制", SURFACE_ALT);
        copy.setOnClickListener(v -> copyText("AGH Logs", logOutput == null ? "" : logOutput.getText().toString()));
        tools.addView(refresh);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(88), dp(40));
        cp.setMargins(dp(8), 0, 0, 0);
        tools.addView(copy, cp);
        root.addView(tools);

        ScrollView logScroll = new ScrollView(this);
        logScroll.setFillViewport(true);
        logOutput = text("正在读取日志…", 12, false);
        logOutput.setTypeface(Typeface.MONOSPACE);
        logOutput.setTextColor(Color.rgb(204, 214, 226));
        logOutput.setTextIsSelectable(true);
        logOutput.setPadding(dp(14), dp(14), dp(14), dp(14));
        logOutput.setBackground(rounded(SURFACE, BORDER, 16));
        logScroll.addView(logOutput);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
        lp.setMargins(0, dp(12), 0, 0);
        root.addView(logScroll, lp);
        return root;
    }

    private View buildSettingsPage() {
        ScrollView scroll = pageScroll();
        LinearLayout root = pageColumn(scroll);
        root.addView(pageTitle("设置", "AGH Manager v0.2.0-rc1"));

        root.addView(sectionTitle("自动刷新"));
        LinearLayout refreshCard = card();
        TextView desc = text("选择状态刷新间隔", 13, false);
        desc.setTextColor(MUTED);
        refreshCard.addView(desc);

        LinearLayout opts = new LinearLayout(this);
        opts.setOrientation(LinearLayout.HORIZONTAL);
        long[] values = {3000, 5000, 10000, 0};
        String[] labels = {"3 秒", "5 秒", "10 秒", "关闭"};
        for (int i = 0; i < values.length; i++) {
            final long value = values[i];
            boolean selected = refreshMs == value;
            TextView option = smallButton(labels[i], selected ? BLUE : SURFACE_ALT);
            option.setTextColor(selected ? Color.WHITE : MUTED);
            option.setOnClickListener(v -> {
                refreshMs = value;
                prefs.edit().putLong(KEY_REFRESH_MS, value).apply();
                main.removeCallbacks(autoRefresh);
                scheduleNextRefresh();
                renderPage();
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), 1);
            p.setMargins(dp(3), dp(12), dp(3), 0);
            opts.addView(option, p);
        }
        refreshCard.addView(opts);
        root.addView(refreshCard);

        root.addView(sectionTitle("诊断"));
        LinearLayout diag = card();
        TextView diagText = text("生成状态、端口监听和 Box DNS 路由快照，方便直接复制给调试会话。", 13, false);
        diagText.setTextColor(MUTED);
        diag.addView(diagText);
        TextView copyReport = largeButton("复制诊断报告", BLUE);
        copyReport.setOnClickListener(v -> copyDebugReport());
        LinearLayout.LayoutParams dr = matchWrap();
        dr.setMargins(0, dp(14), 0, 0);
        diag.addView(copyReport, dr);
        root.addView(diag);

        root.addView(sectionTitle("关于"));
        LinearLayout about = card();
        about.addView(infoRow("版本", "0.2.0-rc1"));
        about.addView(infoRow("控制后端", TOOL));
        about.addView(infoRow("Domestic", "DNS 5591 / Web 3000"));
        about.addView(infoRow("Foreign", "DNS 5592 / Web 3001"));
        root.addView(about);
        return scroll;
    }

    private View pageTitle(String title, String subtitle) {
        LinearLayout box = column();
        TextView t = text(title, 27, true);
        box.addView(t);
        TextView s = text(subtitle, 13, false);
        s.setTextColor(MUTED);
        s.setPadding(0, dp(2), 0, dp(8));
        box.addView(s);
        return box;
    }

    private View infoRow(String key, String value) {
        LinearLayout row = rowLayout();
        row.setPadding(0, dp(8), 0, dp(8));
        TextView k = text(key, 13, false);
        k.setTextColor(MUTED);
        row.addView(k, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 0.35f));
        TextView v = text(value, 13, false);
        v.setGravity(Gravity.END);
        v.setTextIsSelectable(true);
        row.addView(v, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 0.65f));
        return row;
    }

    private void refreshStatus() {
        if (refreshing || destroyed) return;
        refreshing = true;
        io.execute(() -> {
            RootShell.Result r = RootShell.exec(
                    "echo '===MODULE==='; " +
                    "[ -x " + TOOL + " ] && echo READY || echo MISSING; " +
                    "echo '===STATUS==='; " +
                    TOOL + " status 2>&1; " +
                    "echo '===BOX==='; " +
                    "if [ -f /data/adb/box/settings.ini ]; then " +
                    "grep -E '^(dns_hijack_mode|domestic_dns_port|foreign_dns_port|foreign_dns_fallback_port|foreign_dns_fail_port)=' /data/adb/box/settings.ini; " +
                    "else echo 'Box settings missing'; fi; " +
                    "echo '===PORTS==='; " +
                    "for p in 5591 5592 1053; do " +
                    "if ss -lntu 2>/dev/null | grep -qE '[:.]'$p'([[:space:]]|$)'; then echo $p'=up'; else echo $p'=down'; fi; " +
                    "done"
            );
            StatusSnapshot parsed = StatusSnapshot.from(r);
            main.post(() -> {
                refreshing = false;
                snapshot = parsed;
                if (currentPage == 0 || currentPage == 1) renderPage();
            });
        });
    }

    private void runAction(String label, String action) {
        if (actionBusy) {
            Toast.makeText(this, "已有操作正在执行", Toast.LENGTH_SHORT).show();
            return;
        }
        actionBusy = true;
        Toast.makeText(this, label + "…", Toast.LENGTH_SHORT).show();

        io.execute(() -> {
            String command = TOOL + " " + action;
            if ("restart-domestic".equals(action)) {
                command = TOOL + " restart-domestic || { " + TOOL + " stop-domestic; " + TOOL + " start-domestic; }";
            } else if ("restart-foreign".equals(action)) {
                command = TOOL + " restart-foreign || { " + TOOL + " stop-foreign; " + TOOL + " start-foreign; }";
            }
            RootShell.Result r = RootShell.exec(command);
            main.post(() -> {
                actionBusy = false;
                Toast.makeText(this,
                        r.ok() ? label + "完成" : label + "失败\n" + r.output,
                        Toast.LENGTH_LONG).show();
                refreshStatus();
            });
        });
    }

    private void loadLogs() {
        if (logOutput != null) logOutput.setText("正在读取日志…");
        final String path;
        if (logKind == 1) path = "/data/adb/agh/instances/foreign/agh.log";
        else if (logKind == 2) path = "/data/adb/agh/history.log";
        else path = "/data/adb/agh/instances/domestic/agh.log";

        io.execute(() -> {
            RootShell.Result r = RootShell.exec("tail -n 220 " + path + " 2>/dev/null");
            main.post(() -> {
                if (logOutput != null) {
                    logOutput.setText(TextUtils.isEmpty(r.output) ? "(暂无日志)" : r.output);
                }
            });
        });
    }

    private void copyDebugReport() {
        Toast.makeText(this, "正在生成诊断报告…", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            RootShell.Result r = RootShell.exec(
                    "echo 'AGH Manager v0.2.0-rc1'; " +
                    "echo '===== AGH STATUS ====='; " + TOOL + " status 2>&1; " +
                    "echo '===== LISTENERS ====='; ss -lntup 2>/dev/null | grep -E ':5591|:5592|:1053' || true; " +
                    "echo '===== BOX SETTINGS ====='; " +
                    "grep -E '^(proxy_mode|network_mode|dns_hijack_mode|domestic_dns_port|foreign_dns_port|foreign_dns_fallback_port|foreign_dns_fail_port|ipv6)=' /data/adb/box/settings.ini 2>/dev/null || true; " +
                    "echo '===== DNS CHAIN ====='; iptables -t nat -nvL NAT_DNS_HIJACK --line-numbers 2>/dev/null || true"
            );
            main.post(() -> copyText("AGH Manager 诊断报告", r.output));
        });
    }

    private void copyText(String label, String value) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value == null ? "" : value));
        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
    }

    private void openWeb(String title, String url) {
        Intent i = new Intent(this, WebViewActivity.class);
        i.putExtra("title", title);
        i.putExtra("url", url);
        startActivity(i);
    }

    private void scheduleNextRefresh() {
        main.removeCallbacks(autoRefresh);
        if (refreshMs > 0 && !destroyed) main.postDelayed(autoRefresh, refreshMs);
    }

    private final Runnable autoRefresh = new Runnable() {
        @Override
        public void run() {
            if (destroyed || refreshMs <= 0) return;
            refreshStatus();
            main.postDelayed(this, refreshMs);
        }
    };

    private ScrollView pageScroll() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        return scroll;
    }

    private LinearLayout pageColumn(ScrollView parent) {
        LinearLayout root = column();
        root.setPadding(dp(18), dp(20), dp(18), dp(28));
        parent.addView(root);
        return root;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout rowLayout() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    private LinearLayout card() {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(rounded(SURFACE, BORDER, 18));
        LinearLayout.LayoutParams p = matchWrap();
        p.setMargins(0, dp(8), 0, dp(8));
        card.setLayoutParams(p);
        return card;
    }

    private TextView sectionTitle(String title) {
        TextView t = text(title, 16, true);
        t.setTextColor(Color.rgb(214, 222, 232));
        t.setPadding(dp(4), dp(16), 0, dp(4));
        return t;
    }

    private TextView chip(String label, int color) {
        TextView chip = text(label, 10, true);
        chip.setGravity(Gravity.CENTER);
        chip.setTextColor(color);
        chip.setPadding(dp(10), dp(6), dp(10), dp(6));
        chip.setBackground(rounded(withAlpha(color, 28), withAlpha(color, 100), 50));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(30));
        p.setMargins(0, 0, dp(8), 0);
        chip.setLayoutParams(p);
        return chip;
    }

    private TextView actionButton(String label, String action, int color) {
        TextView b = smallButton(label, withAlpha(color, 38));
        b.setTextColor(color);
        b.setBackground(rounded(withAlpha(color, 32), withAlpha(color, 110), 14));
        b.setOnClickListener(v -> runAction(label, action));
        return b;
    }

    private TextView outlineButton(String label, String action) {
        TextView b = smallButton(label, SURFACE_ALT);
        b.setOnClickListener(v -> runAction(label, action));
        return b;
    }

    private TextView webButton(String label, String title, String url) {
        TextView b = smallButton(label, BLUE);
        b.setOnClickListener(v -> openWeb(title, url));
        return b;
    }

    private LinearLayout actionRow(TextView... buttons) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (TextView button : buttons) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1);
            p.setMargins(dp(3), dp(3), dp(3), dp(3));
            row.addView(button, p);
        }
        return row;
    }

    private TextView smallButton(String label, int bg) {
        TextView b = text(label, 13, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), dp(8), dp(12), dp(8));
        b.setBackground(rounded(bg, bg, 14));
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    private TextView largeButton(String label, int color) {
        TextView b = text(label, 14, true);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(Color.WHITE);
        b.setPadding(dp(16), dp(12), dp(16), dp(12));
        b.setBackground(rounded(color, color, 15));
        b.setClickable(true);
        return b;
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextColor(TEXT);
        v.setTextSize(sp);
        v.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return v;
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
    }

    private String refreshLabel(long ms) {
        if (ms <= 0) return "关闭";
        if (ms % 1000 == 0) return (ms / 1000) + " 秒";
        return ms + " ms";
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (content != null) refreshStatus();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        main.removeCallbacks(autoRefresh);
        io.shutdownNow();
        super.onDestroy();
    }

    private static final class StatusSnapshot {
        boolean rootOk;
        boolean moduleReady;
        boolean domesticUp;
        boolean foreignUp;
        boolean port5591Up;
        boolean port5592Up;
        boolean port1053Up;
        String domesticPid = "";
        String foreignPid = "";
        String boxMode = "";

        static StatusSnapshot checking() {
            return new StatusSnapshot();
        }

        static StatusSnapshot from(RootShell.Result r) {
            StatusSnapshot s = new StatusSnapshot();
            s.rootOk = r.ok();
            if (!r.ok()) return s;

            String out = r.output == null ? "" : r.output;
            s.moduleReady = out.contains("===MODULE===\nREADY");
            for (String raw : out.split("\n")) {
                String line = raw.trim();
                if (line.startsWith("domestic:")) {
                    s.domesticUp = line.contains(" up ");
                    s.domesticPid = valueAfter(line, "pid=");
                } else if (line.startsWith("foreign:")) {
                    s.foreignUp = line.contains(" up ");
                    s.foreignPid = valueAfter(line, "pid=");
                } else if (line.startsWith("dns_hijack_mode=")) {
                    s.boxMode = line.substring("dns_hijack_mode=".length()).replace("\"", "");
                } else if ("5591=up".equals(line)) {
                    s.port5591Up = true;
                } else if ("5592=up".equals(line)) {
                    s.port5592Up = true;
                } else if ("1053=up".equals(line)) {
                    s.port1053Up = true;
                }
            }
            return s;
        }

        private static String valueAfter(String line, String key) {
            int i = line.indexOf(key);
            if (i < 0) return "";
            String rest = line.substring(i + key.length());
            int end = rest.indexOf(' ');
            return end >= 0 ? rest.substring(0, end) : rest;
        }
    }
}
