package io.github.chenyurumeng.aghmanager;

import android.app.Activity;
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
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String AGH_TOOL = "/data/adb/agh/scripts/tool.sh";
    private static final String BOX_SERVICE = "/data/adb/box/scripts/box.service";
    private static final String BOX_SETTINGS = "/data/adb/box/settings.ini";
    private static final String BOX_STOP_GUARD = "/data/adb/box/run/state/user_stopped";

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
    private static final int PURPLE = Color.rgb(182, 130, 255);

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private SharedPreferences prefs;
    private FrameLayout content;
    private LinearLayout bottomNav;
    private TextView logOutput;

    private boolean destroyed;
    private boolean refreshing;
    private boolean actionBusy;
    private int currentPage = 0;
    private int logKind = 0;
    private long refreshMs = 5000;
    private final int[] pageScrollY = new int[5];
    private final Map<String, TextView> statusViews = new HashMap<>();

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
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, 0);
            return insets;
        });
        root.requestApplyInsets();

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setPadding(dp(6), dp(6), dp(6), dp(8));
        bottomNav.setBackgroundColor(SURFACE);
        root.addView(bottomNav, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(68)));

        String[] labels = {"首页", "Box", "AGH", "日志", "设置"};
        for (int i = 0; i < labels.length; i++) {
            final int page = i;
            TextView item = text(labels[i], 12, i == 0);
            item.setGravity(Gravity.CENTER);
            item.setTextColor(i == 0 ? BLUE : MUTED);
            item.setOnClickListener(v -> {
                saveCurrentScrollPosition();
                currentPage = page;
                updateNav();
                renderPage();
                if (currentPage == 3) loadLogs();
            });
            bottomNav.addView(item, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
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

    private void saveCurrentScrollPosition() {
        if (content == null || content.getChildCount() == 0) return;
        View child = content.getChildAt(0);
        if (child instanceof ScrollView && currentPage >= 0 && currentPage < pageScrollY.length) {
            pageScrollY[currentPage] = ((ScrollView) child).getScrollY();
        }
    }

    private void restoreCurrentScrollPosition() {
        if (content == null || content.getChildCount() == 0) return;
        View child = content.getChildAt(0);
        if (child instanceof ScrollView && currentPage >= 0 && currentPage < pageScrollY.length) {
            final ScrollView scroll = (ScrollView) child;
            final int y = pageScrollY[currentPage];
            scroll.post(() -> scroll.scrollTo(0, y));
        }
    }

    private void renderPagePreservingScroll() {
        saveCurrentScrollPosition();
        renderPage();
    }

    private void renderPage() {
        if (content == null) return;
        statusViews.clear();
        content.removeAllViews();
        switch (currentPage) {
            case 1:
                content.addView(buildBoxPage());
                break;
            case 2:
                content.addView(buildAghPage());
                break;
            case 3:
                content.addView(buildLogsPage());
                break;
            case 4:
                content.addView(buildSettingsPage());
                break;
            default:
                content.addView(buildHomePage());
                break;
        }
        restoreCurrentScrollPosition();
    }

    private View buildHomePage() {
        ScrollView scroll = pageScroll();
        LinearLayout root = pageColumn(scroll);

        LinearLayout header = rowLayout();
        LinearLayout titleBox = column();
        titleBox.addView(text("Box & AGH Manager", 27, true));
        TextView sub = text("Mihomo · Dual AdGuard Home · DNS Routing", 12, false);
        sub.setTextColor(MUTED);
        titleBox.addView(sub);
        header.addView(titleBox, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView refresh = smallButton("刷新", BLUE);
        refresh.setOnClickListener(v -> refreshStatus());
        header.addView(refresh, new LinearLayout.LayoutParams(dp(72), dp(42)));
        root.addView(header);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(0, dp(16), 0, dp(4));
        chips.addView(bind("home.root", chip(snapshot.rootOk ? "ROOT OK" : "ROOT ?", snapshot.rootOk ? GREEN : RED)));
        chips.addView(bind("home.boxReady", chip(snapshot.boxModuleReady ? "BOX READY" : "BOX ?", snapshot.boxModuleReady ? GREEN : ORANGE)));
        chips.addView(bind("home.aghReady", chip(snapshot.aghModuleReady ? "AGH READY" : "AGH ?", snapshot.aghModuleReady ? GREEN : ORANGE)));
        root.addView(chips);

        root.addView(buildHealthCard());
        root.addView(buildBoxSummaryCard());
        root.addView(buildAghSummaryCard());
        root.addView(buildRoutingCard());

        root.addView(sectionTitle("整套系统"));
        root.addView(actionRow(
                systemButton("全套启动", "start", GREEN),
                systemButton("全套重启", "restart", BLUE),
                systemButton("全套停止", "stop", RED)
        ));

        TextView hint = text(
                "停止顺序固定为 Box → AGH；启动顺序固定为 AGH → Box。这样会保留 Box 的 user_stopped 防竞态保护。",
                12, false);
        hint.setTextColor(MUTED);
        hint.setPadding(dp(4), dp(16), dp(4), dp(10));
        root.addView(hint);
        return scroll;
    }

    private View buildHealthCard() {
        LinearLayout card = card();
        LinearLayout top = rowLayout();

        LinearLayout left = column();
        left.addView(text("系统健康", 18, true));
        TextView detail = bind("home.healthDetail", text(healthDetail(), 12, false));
        detail.setTextColor(MUTED);
        left.addView(detail);
        top.addView(left, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        top.addView(bind("home.healthChip", chip(healthLabel(), healthColor())));
        card.addView(top);

        LinearLayout stateRow = new LinearLayout(this);
        stateRow.setOrientation(LinearLayout.HORIZONTAL);
        stateRow.setPadding(0, dp(14), 0, 0);
        stateRow.addView(routeCell("Box",
                snapshot.boxUp ? safe(snapshot.boxBin, "core") : "DOWN",
                snapshot.boxUp ? GREEN : RED, "home.box"), weight());
        stateRow.addView(routeCell("Domestic", snapshot.domesticUp ? "5591" : "DOWN",
                snapshot.domesticUp ? GREEN : RED, "home.domestic"), weight());
        stateRow.addView(routeCell("Foreign", snapshot.foreignUp ? "5592" : "DOWN",
                snapshot.foreignUp ? GREEN : ORANGE, "home.foreign"), weight());
        stateRow.addView(routeCell("Fallback", snapshot.port1053Up ? "1053" : "DOWN",
                snapshot.port1053Up ? GREEN : ORANGE, "home.fallback"), weight());
        card.addView(stateRow);
        return card;
    }

    private View buildBoxSummaryCard() {
        LinearLayout card = card();
        LinearLayout top = rowLayout();

        LinearLayout title = column();
        title.addView(text("Box / " + safe(snapshot.boxBin, "core"), 19, true));
        String meta = "PID " + safe(snapshot.boxPid, "-")
                + " · " + safe(snapshot.proxyMode, "?")
                + " · " + safe(snapshot.networkMode, "?")
                + " · DNS " + safe(snapshot.dnsHijackMode, "?");
        TextView m = text(meta, 12, false);
        m.setTextColor(MUTED);
        title.addView(m);
        top.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        top.addView(chip(snapshot.boxUp ? "RUNNING" : "STOPPED",
                snapshot.boxUp ? GREEN : RED));
        card.addView(top);

        if (!TextUtils.isEmpty(snapshot.boxVersion)) {
            TextView version = text(snapshot.boxVersion, 11, false);
            version.setTextColor(MUTED);
            version.setPadding(0, dp(8), 0, 0);
            card.addView(version);
        }

        LinearLayout actions = actionRow(
                boxButton("重启 Box", "restart", BLUE),
                webButton("Mihomo Dashboard", "Mihomo Dashboard", snapshot.dashboardUrl())
        );
        actions.setPadding(0, dp(12), 0, 0);
        card.addView(actions);
        return card;
    }

    private View buildAghSummaryCard() {
        LinearLayout card = card();
        LinearLayout titleRow = rowLayout();
        titleRow.addView(text("AdGuard Home", 19, true), new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        boolean allUp = snapshot.domesticUp && snapshot.foreignUp;
        titleRow.addView(chip(allUp ? "DUAL UP" : "DEGRADED", allUp ? GREEN : ORANGE));
        card.addView(titleRow);

        card.addView(instanceMiniRow("Domestic", "5591 / 3000", snapshot.domesticUp, snapshot.domesticPid));
        card.addView(instanceMiniRow("Foreign", "5592 / 3001", snapshot.foreignUp, snapshot.foreignPid));

        LinearLayout actions = actionRow(
                webButton("Domestic", "Domestic AGH", "http://127.0.0.1:3000"),
                webButton("Foreign", "Foreign AGH", "http://127.0.0.1:3001")
        );
        actions.setPadding(0, dp(10), 0, 0);
        card.addView(actions);
        return card;
    }

    private View instanceMiniRow(String name, String ports, boolean up, String pid) {
        LinearLayout row = rowLayout();
        row.setPadding(0, dp(10), 0, 0);

        TextView dot = text("●", 15, true);
        dot.setTextColor(up ? GREEN : RED);
        row.addView(dot, new LinearLayout.LayoutParams(dp(26),
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout body = column();
        body.addView(text(name, 14, true));
        TextView p = text(ports + (TextUtils.isEmpty(pid) ? "" : " · PID " + pid), 11, false);
        p.setTextColor(MUTED);
        body.addView(p);
        row.addView(body, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private View buildRoutingCard() {
        LinearLayout card = card();
        LinearLayout top = rowLayout();

        LinearLayout title = column();
        title.addView(text("DNS Routing", 18, true));
        TextView mode = text(
                "split: " + safe(snapshot.dnsHijackMode, "?")
                        + " · IPv6 " + safe(snapshot.ipv6, "?"),
                12, false);
        mode.setTextColor(MUTED);
        title.addView(mode);
        top.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        top.addView(chip(snapshot.userStopped ? "USER STOPPED" : "ACTIVE",
                snapshot.userStopped ? ORANGE : BLUE));
        card.addView(top);

        boolean blacklistMode = "blacklist".equalsIgnoreCase(snapshot.proxyMode)
                || "black".equalsIgnoreCase(snapshot.proxyMode);
        if (blacklistMode) {
            card.addView(routeTextRow("黑名单应用", domesticDnsTarget()));
            card.addView(routeTextRow("其它应用", foreignDnsTarget()));
        } else {
            card.addView(routeTextRow("白名单应用", foreignDnsTarget()));
            card.addView(routeTextRow("其它应用", domesticDnsTarget()));
        }

        LinearLayout listeners = new LinearLayout(this);
        listeners.setOrientation(LinearLayout.HORIZONTAL);
        listeners.setPadding(0, dp(12), 0, 0);
        listeners.addView(routeCell("5591", snapshot.port5591Up ? "LISTEN" : "DOWN",
                snapshot.port5591Up ? GREEN : RED), weight());
        listeners.addView(routeCell("5592", snapshot.port5592Up ? "LISTEN" : "DOWN",
                snapshot.port5592Up ? GREEN : RED), weight());
        listeners.addView(routeCell("1053", snapshot.port1053Up ? "LISTEN" : "DOWN",
                snapshot.port1053Up ? GREEN : ORANGE), weight());
        listeners.addView(routeCell("9090", snapshot.port9090Up ? "API" : "DOWN",
                snapshot.port9090Up ? GREEN : ORANGE), weight());
        card.addView(listeners);
        return card;
    }

    private View routeTextRow(String label, String target) {
        LinearLayout row = rowLayout();
        row.setPadding(0, dp(10), 0, 0);
        TextView key = text(label, 13, false);
        key.setTextColor(MUTED);
        row.addView(key, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.38f));
        TextView value = text("→ " + target, 13, true);
        value.setGravity(Gravity.END);
        row.addView(value, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.62f));
        return row;
    }

    private View buildBoxPage() {
        ScrollView scroll = pageScroll();
        LinearLayout root = pageColumn(scroll);
        root.addView(pageTitle("Box / Mihomo", "安全控制通过 " + BOX_SERVICE));

        LinearLayout status = card();
        LinearLayout top = rowLayout();
        LinearLayout name = column();
        name.addView(text("Box Service", 20, true));
        TextView version = text(TextUtils.isEmpty(snapshot.boxVersion)
                ? safe(snapshot.boxBin, "core")
                : snapshot.boxVersion, 11, false);
        version.setTextColor(MUTED);
        name.addView(version);
        top.addView(name, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        top.addView(chip(snapshot.boxUp ? "RUNNING" : "STOPPED",
                snapshot.boxUp ? GREEN : RED));
        status.addView(top);

        status.addView(infoRow("Core", safe(snapshot.boxBin, "-")));
        status.addView(infoRow("PID", safe(snapshot.boxPid, "-")));
        status.addView(infoRow("Proxy Mode", safe(snapshot.proxyMode, "-")));
        status.addView(infoRow("Network Mode", safe(snapshot.networkMode, "-")));
        status.addView(infoRow("DNS Hijack", safe(snapshot.dnsHijackMode, "-")));
        status.addView(infoRow("IPv6", safe(snapshot.ipv6, "-")));
        status.addView(infoRow("Mihomo DNS", snapshot.port1053Up ? ":1053 listening" : ":1053 down"));
        status.addView(infoRow("Controller", safe(snapshot.boxController, "127.0.0.1:9090")));
        status.addView(infoRow("Stop Guard", snapshot.userStopped ? "present" : "clear"));
        root.addView(status);

        root.addView(sectionTitle("应用分流"));
        LinearLayout routing = card();
        LinearLayout routingTop = rowLayout();
        LinearLayout routingTitle = column();
        routingTitle.addView(text("Whitelist / Blacklist", 18, true));
        TextView routingDesc = text(
                "Whitelist：选中应用走代理 / Foreign；Blacklist：选中应用直连 / Domestic",
                12, false);
        routingDesc.setTextColor(MUTED);
        routingTitle.addView(routingDesc);
        routingTop.addView(routingTitle, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        routingTop.addView(chip(safe(snapshot.proxyMode, "?").toUpperCase(), BLUE));
        routing.addView(routingTop);

        TextView manageApps = largeButton("管理应用分流", PURPLE);
        manageApps.setOnClickListener(v ->
                startActivity(new Intent(this, AppRoutingActivity.class)));
        LinearLayout.LayoutParams map = matchWrap();
        map.setMargins(0, dp(12), 0, 0);
        routing.addView(manageApps, map);

        TextView liveHint = text(
                "支持搜索、User/System 筛选、多用户/分身 UID；修改后点击“应用”才写入 Box。",
                11, false);
        liveHint.setTextColor(MUTED);
        liveHint.setPadding(dp(2), dp(10), dp(2), 0);
        routing.addView(liveHint);
        root.addView(routing);

        root.addView(sectionTitle("Box 控制"));
        LinearLayout controls = card();
        controls.addView(actionRow(
                boxButton("启动", "start", GREEN),
                boxButton("重启", "restart", BLUE),
                boxButton("停止", "stop", RED)
        ));
        TextView safety = text(
                "App 不直接执行 box.iptables enable/disable。stop/restart 完全交给 box.service，以保留 user_stopped 防竞态。",
                12, false);
        safety.setTextColor(MUTED);
        safety.setPadding(dp(2), dp(12), dp(2), 0);
        controls.addView(safety);
        root.addView(controls);

        root.addView(sectionTitle("Mihomo"));
        LinearLayout dashboard = card();
        dashboard.addView(infoRow("API", snapshot.dashboardUrl()));
        TextView open = largeButton("打开 Mihomo Dashboard", BLUE);
        open.setOnClickListener(v -> openWeb("Mihomo Dashboard", snapshot.dashboardUrl()));
        LinearLayout.LayoutParams op = matchWrap();
        op.setMargins(0, dp(12), 0, 0);
        dashboard.addView(open, op);
        root.addView(dashboard);
        return scroll;
    }

    private View buildAghPage() {
        ScrollView scroll = pageScroll();
        LinearLayout root = pageColumn(scroll);
        root.addView(pageTitle("AdGuard Home", "Dual DNS · Domestic / Foreign"));

        root.addView(sectionTitle("全部实例"));
        LinearLayout all = card();
        all.addView(actionRow(
                aghButton("启动", "start", GREEN),
                aghButton("重启", "restart", BLUE),
                aghButton("停止", "stop", RED)
        ));
        root.addView(all);

        root.addView(sectionTitle("Domestic"));
        root.addView(aghInstanceCard(true));

        root.addView(sectionTitle("Foreign"));
        root.addView(aghInstanceCard(false));
        return scroll;
    }

    private View aghInstanceCard(boolean domestic) {
        String name = domestic ? "Domestic" : "Foreign";
        String dns = domestic ? "5591" : "5592";
        String web = domestic ? "3000" : "3001";
        boolean up = domestic ? snapshot.domesticUp : snapshot.foreignUp;
        String pid = domestic ? snapshot.domesticPid : snapshot.foreignPid;

        LinearLayout card = card();
        LinearLayout top = rowLayout();
        LinearLayout title = column();
        title.addView(text(name, 19, true));
        TextView info = text("DNS :" + dns + " · Web :" + web
                + (TextUtils.isEmpty(pid) ? "" : " · PID " + pid), 12, false);
        info.setTextColor(MUTED);
        title.addView(info);
        top.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        top.addView(chip(up ? "RUNNING" : "STOPPED", up ? GREEN : RED));
        card.addView(top);

        LinearLayout buttons = actionRow(
                aghButton("启动", domestic ? "start-domestic" : "start-foreign", GREEN),
                aghButton("重启", domestic ? "restart-domestic" : "restart-foreign", BLUE),
                aghButton("停止", domestic ? "stop-domestic" : "stop-foreign", RED)
        );
        buttons.setPadding(0, dp(12), 0, 0);
        card.addView(buttons);

        TextView webButton = largeButton("打开 " + name + " 管理界面", PURPLE);
        webButton.setOnClickListener(v ->
                openWeb(name + " AGH", "http://127.0.0.1:" + web));
        LinearLayout.LayoutParams wp = matchWrap();
        wp.setMargins(0, dp(10), 0, 0);
        card.addView(webButton, wp);
        return card;
    }

    private View buildLogsPage() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(10));
        root.setBackgroundColor(BG);
        root.addView(pageTitle("日志", "Box + AGH unified logs"));

        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);

        String[] labels = {"Box Core", "Box Service", "Box Tool", "Domestic", "Foreign", "AGH Module"};
        for (int i = 0; i < labels.length; i++) {
            final int which = i;
            boolean selected = i == logKind;
            TextView tab = smallButton(labels[i], selected ? BLUE : SURFACE_ALT);
            tab.setTextColor(selected ? Color.WHITE : MUTED);
            tab.setOnClickListener(v -> {
                logKind = which;
                renderPage();
                loadLogs();
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    dp(112), dp(42));
            p.setMargins(dp(3), 0, dp(3), dp(8));
            tabs.addView(tab, p);
        }
        tabScroll.addView(tabs);
        root.addView(tabScroll);

        LinearLayout tools = rowLayout();
        TextView refresh = smallButton("刷新", BLUE);
        refresh.setOnClickListener(v -> loadLogs());
        TextView copy = smallButton("复制", SURFACE_ALT);
        copy.setOnClickListener(v -> copyText(
                "Box & AGH Logs",
                logOutput == null ? "" : logOutput.getText().toString()));
        tools.addView(refresh, new LinearLayout.LayoutParams(dp(88), dp(40)));
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
        root.addView(pageTitle("设置", "Box & AGH Manager v0.4.0-rc2"));

        root.addView(sectionTitle("自动刷新"));
        LinearLayout refreshCard = card();
        TextView desc = text("选择整套网络栈状态刷新间隔", 13, false);
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
                scheduleNextRefresh();
                renderPagePreservingScroll();
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    0, dp(44), 1);
            p.setMargins(dp(3), dp(12), dp(3), 0);
            opts.addView(option, p);
        }
        refreshCard.addView(opts);
        root.addView(refreshCard);

        root.addView(sectionTitle("诊断"));
        LinearLayout diag = card();
        TextView diagText = text(
                "生成 Box、Mihomo、AGH、监听端口、user_stopped 与 NAT_DNS_HIJACK 的统一快照。",
                13, false);
        diagText.setTextColor(MUTED);
        diag.addView(diagText);
        TextView copyReport = largeButton("复制完整诊断报告", BLUE);
        copyReport.setOnClickListener(v -> copyDebugReport());
        LinearLayout.LayoutParams dr = matchWrap();
        dr.setMargins(0, dp(14), 0, 0);
        diag.addView(copyReport, dr);
        root.addView(diag);

        root.addView(sectionTitle("关于"));
        LinearLayout about = card();
        about.addView(infoRow("版本", "0.4.0-rc2"));
        about.addView(infoRow("Box 后端", BOX_SERVICE));
        about.addView(infoRow("AGH 后端", AGH_TOOL));
        about.addView(infoRow("Mihomo Dashboard", snapshot.dashboardUrl()));
        about.addView(infoRow("Domestic", "DNS 5591 / Web 3000"));
        about.addView(infoRow("Foreign", "DNS 5592 / Web 3001"));
        root.addView(about);
        return scroll;
    }

    private void refreshStatus() {
        if (refreshing || destroyed) return;
        refreshing = true;

        io.execute(() -> {
            String command =
                    "echo '===MODULES==='; " +
                    "[ -x " + AGH_TOOL + " ] && echo 'AGH_MODULE=ready' || echo 'AGH_MODULE=missing'; " +
                    "[ -x " + BOX_SERVICE + " ] && echo 'BOX_MODULE=ready' || echo 'BOX_MODULE=missing'; " +

                    "echo '===AGH_STATUS==='; " +
                    "if [ -x " + AGH_TOOL + " ]; then " + AGH_TOOL + " status 2>&1; fi; " +

                    "echo '===BOX_SETTINGS==='; " +
                    "if [ -f " + BOX_SETTINGS + " ]; then " +
                    "grep -E '^(bin_name|proxy_mode|network_mode|dns_hijack_mode|ipv6|domestic_dns_port|foreign_dns_port|foreign_dns_fallback_port|foreign_dns_fail_port)=' "
                    + BOX_SETTINGS + " 2>/dev/null || true; fi; " +

                    "echo '===BOX_PROCESS==='; " +
                    "bin=$(sed -n 's/^bin_name=\"\\([^\"]*\\)\".*/\\1/p' " + BOX_SETTINGS + " 2>/dev/null | head -n1); " +
                    "[ -n \"$bin\" ] || bin=mihomo; echo \"BOX_BIN=$bin\"; " +
                    "pid=$(cat /data/adb/box/run/box.pid 2>/dev/null); " +
                    "if [ -n \"$pid\" ] && kill -0 \"$pid\" 2>/dev/null; then echo 'BOX_STATUS=up'; echo \"BOX_PID=$pid\"; else echo 'BOX_STATUS=down'; fi; " +
                    "[ -f " + BOX_STOP_GUARD + " ] && echo 'BOX_USER_STOPPED=true' || echo 'BOX_USER_STOPPED=false'; " +
                    "if [ \"$bin\" = mihomo ] && [ -x /data/adb/box/bin/mihomo ]; then " +
                    "v=$(/data/adb/box/bin/mihomo -v 2>/dev/null | head -n1); echo \"BOX_VERSION=$v\"; fi; " +
                    "controller=$(awk '!/^[[:space:]]*#/ && /external-controller:[[:space:]]/ {print $2; exit}' /data/adb/box/mihomo/config.yaml 2>/dev/null | tr -d '\"' ); " +
                    "[ -n \"$controller\" ] && echo \"BOX_CONTROLLER=$controller\"; " +

                    "echo '===PORTS==='; " +
                    "for p in 5591 5592 1053 9090; do " +
                    "if ss -lntu 2>/dev/null | grep -qE '[:.]'$p'([[:space:]]|$)'; then echo \"PORT_$p=up\"; else echo \"PORT_$p=down\"; fi; done; " +

                    "echo '===DNS_RULES==='; " +
                    "iptables -t nat -S NAT_DNS_HIJACK 2>/dev/null || true; " +
                    "echo '===END==='; true";

            RootShell.Result result = RootShell.exec(command, 45);
            StatusSnapshot parsed = StatusSnapshot.from(result);

            main.post(() -> {
                refreshing = false;
                snapshot = parsed;
                if (currentPage == 0 || currentPage == 1 || currentPage == 2 || currentPage == 4) {
                    renderPagePreservingScroll();
                }
            });
        });
    }

    private void runBoxAction(String label, String action) {
        runRootAction(label, BOX_SERVICE + " " + action, 150);
    }

    private void runAghAction(String label, String action) {
        String command = AGH_TOOL + " " + action;
        if ("restart-domestic".equals(action)) {
            command = AGH_TOOL + " restart-domestic || { "
                    + AGH_TOOL + " stop-domestic; "
                    + AGH_TOOL + " start-domestic; }";
        } else if ("restart-foreign".equals(action)) {
            command = AGH_TOOL + " restart-foreign || { "
                    + AGH_TOOL + " stop-foreign; "
                    + AGH_TOOL + " start-foreign; }";
        }
        runRootAction(label, command, 300);
    }

    private void runSystemAction(String label, String action) {
        final String command;
        final long timeout;

        switch (action) {
            case "start":
                command =
                        "[ -x " + AGH_TOOL + " ] || exit 31; " +
                        "[ -x " + BOX_SERVICE + " ] || exit 32; " +
                        AGH_TOOL + " start; agh_rc=$?; " +
                        "[ $agh_rc -eq 0 ] || exit $agh_rc; " +
                        BOX_SERVICE + " start";
                timeout = 360;
                break;
            case "stop":
                command =
                        "box_rc=0; agh_rc=0; " +
                        "if [ -x " + BOX_SERVICE + " ]; then " + BOX_SERVICE + " stop || box_rc=$?; fi; " +
                        "if [ -x " + AGH_TOOL + " ]; then " + AGH_TOOL + " stop || agh_rc=$?; fi; " +
                        "[ $box_rc -eq 0 ] && [ $agh_rc -eq 0 ]";
                timeout = 150;
                break;
            case "restart":
                command =
                        "[ -x " + AGH_TOOL + " ] || exit 31; " +
                        "[ -x " + BOX_SERVICE + " ] || exit 32; " +
                        BOX_SERVICE + " stop || exit $?; " +
                        AGH_TOOL + " restart || exit $?; " +
                        BOX_SERVICE + " start";
                timeout = 420;
                break;
            default:
                return;
        }
        runRootAction(label, command, timeout);
    }

    private void runRootAction(String label, String command, long timeoutSeconds) {
        if (actionBusy) {
            Toast.makeText(this, "已有操作正在执行", Toast.LENGTH_SHORT).show();
            return;
        }

        actionBusy = true;
        Toast.makeText(this, label + "…", Toast.LENGTH_SHORT).show();

        io.execute(() -> {
            RootShell.Result result = RootShell.exec(command, timeoutSeconds);
            main.post(() -> {
                actionBusy = false;
                String message = result.ok()
                        ? label + "完成"
                        : label + "失败\n" + (TextUtils.isEmpty(result.output)
                        ? "exit=" + result.code
                        : result.output);
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                refreshStatus();
            });
        });
    }

    private void loadLogs() {
        if (logOutput != null) logOutput.setText("正在读取日志…");

        final String command;
        switch (logKind) {
            case 0:
                command = "bin=$(sed -n 's/^bin_name=\"\\([^\"]*\\)\".*/\\1/p' "
                        + BOX_SETTINGS + " 2>/dev/null | head -n1); "
                        + "[ -n \"$bin\" ] || bin=mihomo; "
                        + "tail -n 260 /data/adb/box/run/$bin.log 2>/dev/null";
                break;
            case 1:
                command = "tail -n 260 /data/adb/box/run/runs.log 2>/dev/null";
                break;
            case 2:
                command = "tail -n 260 /data/adb/box/run/tool.log 2>/dev/null";
                break;
            case 3:
                command = "tail -n 260 /data/adb/agh/instances/domestic/agh.log 2>/dev/null";
                break;
            case 4:
                command = "tail -n 260 /data/adb/agh/instances/foreign/agh.log 2>/dev/null";
                break;
            default:
                command = "tail -n 260 /data/adb/agh/history.log 2>/dev/null";
                break;
        }

        io.execute(() -> {
            RootShell.Result result = RootShell.exec(command, 30);
            main.post(() -> {
                if (logOutput != null) {
                    logOutput.setText(TextUtils.isEmpty(result.output)
                            ? "(暂无日志)"
                            : result.output);
                }
            });
        });
    }

    private void copyDebugReport() {
        Toast.makeText(this, "正在生成诊断报告…", Toast.LENGTH_SHORT).show();

        io.execute(() -> {
            String command =
                    "echo 'Box & AGH Manager v0.4.0-rc2'; " +
                    "echo '===== BOX STATUS ====='; " +
                    BOX_SERVICE + " status 2>&1 || true; " +
                    "echo '===== BOX STOP GUARD ====='; " +
                    "[ -f " + BOX_STOP_GUARD + " ] && echo present || echo clear; " +
                    "echo '===== AGH STATUS ====='; " +
                    AGH_TOOL + " status 2>&1 || true; " +
                    "echo '===== LISTENERS ====='; " +
                    "ss -lntup 2>/dev/null | grep -E ':5591|:5592|:1053|:9090' || true; " +
                    "echo '===== BOX SETTINGS ====='; " +
                    "grep -E '^(bin_name|proxy_mode|network_mode|dns_hijack_mode|domestic_dns_port|foreign_dns_port|foreign_dns_fallback_port|foreign_dns_fail_port|ipv6)=' "
                    + BOX_SETTINGS + " 2>/dev/null || true; " +
                    "echo '===== NAT_DNS_HIJACK ====='; " +
                    "iptables -t nat -nvL NAT_DNS_HIJACK --line-numbers 2>/dev/null || true; " +
                    "echo '===== IPv6 DNS GUARD ====='; " +
                    "ip6tables -t filter -S OUTPUT 2>/dev/null | grep BOX_DNS6_REJECT || true";

            RootShell.Result result = RootShell.exec(command, 60);
            main.post(() -> copyText("Box & AGH Manager 诊断报告", result.output));
        });
    }

    private TextView boxButton(String label, String action, int color) {
        TextView button = actionStyleButton(label, color);
        button.setOnClickListener(v -> runBoxAction(label, action));
        return button;
    }

    private TextView aghButton(String label, String action, int color) {
        TextView button = actionStyleButton(label, color);
        button.setOnClickListener(v -> runAghAction(label, action));
        return button;
    }

    private TextView systemButton(String label, String action, int color) {
        TextView button = actionStyleButton(label, color);
        button.setOnClickListener(v -> runSystemAction(label, action));
        return button;
    }

    private TextView actionStyleButton(String label, int color) {
        TextView button = smallButton(label, withAlpha(color, 34));
        button.setTextColor(color);
        button.setBackground(rounded(
                withAlpha(color, 28),
                withAlpha(color, 110),
                14));
        return button;
    }

    private TextView webButton(String label, String title, String url) {
        TextView button = smallButton(label, BLUE);
        button.setOnClickListener(v -> openWeb(title, url));
        return button;
    }

    private String healthLabel() {
        if (!snapshot.rootOk || !snapshot.boxModuleReady || !snapshot.aghModuleReady) {
            return "ERROR";
        }
        if (snapshot.userStopped || !snapshot.boxUp) {
            return "STOPPED";
        }
        if (snapshot.route65534 || (!snapshot.foreignUp && !snapshot.port1053Up)) {
            return "FAIL-CLOSED";
        }
        if (snapshot.boxUp
                && snapshot.domesticUp
                && snapshot.foreignUp
                && snapshot.port5591Up
                && snapshot.port5592Up
                && snapshot.port1053Up) {
            return "HEALTHY";
        }
        return "DEGRADED";
    }

    private int healthColor() {
        switch (healthLabel()) {
            case "HEALTHY":
                return GREEN;
            case "DEGRADED":
                return ORANGE;
            case "FAIL-CLOSED":
                return RED;
            case "STOPPED":
                return MUTED;
            default:
                return RED;
        }
    }

    private String healthDetail() {
        switch (healthLabel()) {
            case "HEALTHY":
                return "Box、双 AGH 与 Mihomo DNS 均正常";
            case "DEGRADED":
                return "部分组件不可用，当前由回退策略维持服务";
            case "FAIL-CLOSED":
                return "Foreign 与 Mihomo DNS 不可用，白名单 DNS 已保护性阻断";
            case "STOPPED":
                return snapshot.userStopped
                        ? "Box 已被主动停止，DNS hooks 不应被 watchdog 重建"
                        : "Box 当前未运行";
            default:
                return "Root、Box 或 AGH 模块状态异常";
        }
    }

    private String domesticDnsTarget() {
        if (snapshot.userStopped || !snapshot.boxUp) return "系统 DNS（Box stopped）";
        if (snapshot.route5591 && snapshot.port5591Up) return "Domestic :5591";
        return snapshot.domesticUp ? "Domestic :5591" : "Android 系统 DNS";
    }

    private String foreignDnsTarget() {
        if (snapshot.userStopped || !snapshot.boxUp) return "系统 DNS（Box stopped）";
        if (snapshot.route5592 && snapshot.port5592Up) return "Foreign :5592";
        if (snapshot.route1053 && snapshot.port1053Up) return "Mihomo :1053 fallback";
        if (snapshot.route65534) return ":65534 FAIL-CLOSED";
        if (snapshot.foreignUp) return "Foreign :5592";
        if (snapshot.port1053Up) return "Mihomo :1053 fallback";
        return "FAIL-CLOSED";
    }

    private View routeCell(String label, String value, int color) {
        LinearLayout box = column();
        box.setGravity(Gravity.CENTER);

        TextView dot = text("●", 15, true);
        dot.setTextColor(color);
        box.addView(dot);

        TextView v = text(value, 13, true);
        box.addView(v);

        TextView l = text(label, 10, false);
        l.setTextColor(MUTED);
        box.addView(l);
        return box;
    }

    private View pageTitle(String title, String subtitle) {
        LinearLayout box = column();
        box.addView(text(title, 27, true));
        TextView sub = text(subtitle, 12, false);
        sub.setTextColor(MUTED);
        sub.setPadding(0, dp(2), 0, dp(8));
        box.addView(sub);
        return box;
    }

    private View infoRow(String key, String value) {
        LinearLayout row = rowLayout();
        row.setPadding(0, dp(8), 0, dp(8));

        TextView k = text(key, 13, false);
        k.setTextColor(MUTED);
        row.addView(k, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.38f));

        TextView v = text(value, 13, false);
        v.setGravity(Gravity.END);
        v.setTextIsSelectable(true);
        row.addView(v, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.62f));
        return row;
    }

    private void copyText(String label, String value) {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(
                label, value == null ? "" : value));
        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
    }

    private void openWeb(String title, String url) {
        Class<?> target = WebViewActivity.class;
        if (url != null && url.contains(":3000")) {
            target = DomesticWebViewActivity.class;
        } else if (url != null && url.contains(":3001")) {
            target = ForeignWebViewActivity.class;
        } else if (url != null && url.contains(":9090")) {
            target = MihomoWebViewActivity.class;
        }

        Intent intent = new Intent(this, target);
        intent.putExtra("title", title);
        intent.putExtra("url", url);
        startActivity(intent);
    }

    private void scheduleNextRefresh() {
        main.removeCallbacks(autoRefresh);
        if (refreshMs > 0 && !destroyed) {
            main.postDelayed(autoRefresh, refreshMs);
        }
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
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout rowLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private LinearLayout card() {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(rounded(SURFACE, BORDER, 18));

        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(8), 0, dp(8));
        card.setLayoutParams(params);
        return card;
    }

    private TextView sectionTitle(String title) {
        TextView text = text(title, 16, true);
        text.setTextColor(Color.rgb(214, 222, 232));
        text.setPadding(dp(4), dp(16), 0, dp(4));
        return text;
    }

    private TextView chip(String label, int color) {
        TextView chip = text(label, 10, true);
        chip.setGravity(Gravity.CENTER);
        chip.setTextColor(color);
        chip.setPadding(dp(10), dp(6), dp(10), dp(6));
        chip.setBackground(rounded(
                withAlpha(color, 28),
                withAlpha(color, 100),
                50));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(30));
        params.setMargins(0, 0, dp(7), 0);
        chip.setLayoutParams(params);
        return chip;
    }

    private LinearLayout actionRow(TextView... buttons) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (TextView button : buttons) {
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(0, dp(48), 1);
            params.setMargins(dp(3), dp(3), dp(3), dp(3));
            row.addView(button, params);
        }
        return row;
    }

    private TextView smallButton(String label, int background) {
        TextView button = text(label, 12, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(10), dp(8), dp(10), dp(8));
        button.setBackground(rounded(background, background, 14));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private TextView largeButton(String label, int color) {
        TextView button = text(label, 14, true);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(Color.WHITE);
        button.setPadding(dp(16), dp(12), dp(16), dp(12));
        button.setBackground(rounded(color, color, 15));
        button.setClickable(true);
        return button;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(TEXT);
        view.setTextSize(sp);
        view.setTypeface(Typeface.DEFAULT,
                bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(
                alpha,
                Color.red(color),
                Color.green(color),
                Color.blue(color));
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
    }

    private String safe(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
        boolean aghModuleReady;
        boolean boxModuleReady;

        boolean domesticUp;
        boolean foreignUp;
        boolean boxUp;
        boolean userStopped;

        boolean port5591Up;
        boolean port5592Up;
        boolean port1053Up;
        boolean port9090Up;

        boolean route5591;
        boolean route5592;
        boolean route1053;
        boolean route65534;

        String domesticPid = "";
        String foreignPid = "";
        String boxPid = "";
        String boxBin = "";
        String boxVersion = "";
        String boxController = "";
        String proxyMode = "";
        String networkMode = "";
        String dnsHijackMode = "";
        String ipv6 = "";

        static StatusSnapshot checking() {
            return new StatusSnapshot();
        }

        String dashboardUrl() {
            String controller = boxController == null ? "" : boxController.trim();
            if (controller.isEmpty()) controller = "127.0.0.1:9090";
            controller = controller.replace("\"", "");

            if (controller.startsWith("http://")) {
                controller = controller.substring(7);
            } else if (controller.startsWith("https://")) {
                controller = controller.substring(8);
            }

            if (controller.startsWith("0.0.0.0:")) {
                controller = "127.0.0.1:" + controller.substring("0.0.0.0:".length());
            } else if (controller.startsWith("*:")) {
                controller = "127.0.0.1:" + controller.substring(2);
            } else if (controller.startsWith("[::]:")) {
                controller = "127.0.0.1:" + controller.substring("[::]:".length());
            }
            return "http://" + controller + "/ui/";
        }

        static StatusSnapshot from(RootShell.Result result) {
            StatusSnapshot snapshot = new StatusSnapshot();
            snapshot.rootOk = result.ok();
            if (!result.ok()) return snapshot;

            String output = result.output == null ? "" : result.output;
            boolean inDnsRules = false;

            for (String raw : output.split("\n")) {
                String line = raw.trim();

                if ("===DNS_RULES===".equals(line)) {
                    inDnsRules = true;
                    continue;
                }
                if ("===END===".equals(line)) {
                    inDnsRules = false;
                    continue;
                }

                if ("AGH_MODULE=ready".equals(line)) {
                    snapshot.aghModuleReady = true;
                } else if ("BOX_MODULE=ready".equals(line)) {
                    snapshot.boxModuleReady = true;
                } else if (line.startsWith("domestic:")) {
                    snapshot.domesticUp = line.contains(" up ");
                    snapshot.domesticPid = valueAfter(line, "pid=");
                } else if (line.startsWith("foreign:")) {
                    snapshot.foreignUp = line.contains(" up ");
                    snapshot.foreignPid = valueAfter(line, "pid=");
                } else if ("BOX_STATUS=up".equals(line)) {
                    snapshot.boxUp = true;
                } else if ("BOX_USER_STOPPED=true".equals(line)) {
                    snapshot.userStopped = true;
                } else if (line.startsWith("BOX_PID=")) {
                    snapshot.boxPid = afterEquals(line);
                } else if (line.startsWith("BOX_BIN=")) {
                    snapshot.boxBin = afterEquals(line);
                } else if (line.startsWith("BOX_VERSION=")) {
                    snapshot.boxVersion = afterEquals(line);
                } else if (line.startsWith("BOX_CONTROLLER=")) {
                    snapshot.boxController = afterEquals(line);
                } else if (line.startsWith("proxy_mode=")) {
                    snapshot.proxyMode = cleanSetting(afterEquals(line));
                } else if (line.startsWith("network_mode=")) {
                    snapshot.networkMode = cleanSetting(afterEquals(line));
                } else if (line.startsWith("dns_hijack_mode=")) {
                    snapshot.dnsHijackMode = cleanSetting(afterEquals(line));
                } else if (line.startsWith("ipv6=")) {
                    snapshot.ipv6 = cleanSetting(afterEquals(line));
                } else if ("PORT_5591=up".equals(line)) {
                    snapshot.port5591Up = true;
                } else if ("PORT_5592=up".equals(line)) {
                    snapshot.port5592Up = true;
                } else if ("PORT_1053=up".equals(line)) {
                    snapshot.port1053Up = true;
                } else if ("PORT_9090=up".equals(line)) {
                    snapshot.port9090Up = true;
                } else if (inDnsRules) {
                    if (line.contains("--to-ports 5591")) snapshot.route5591 = true;
                    if (line.contains("--to-ports 5592")) snapshot.route5592 = true;
                    if (line.contains("--to-ports 1053")) snapshot.route1053 = true;
                    if (line.contains("--to-ports 65534")) snapshot.route65534 = true;
                }
            }
            return snapshot;
        }

        private static String cleanSetting(String value) {
            return value == null ? "" : value.replace("\"", "").trim();
        }

        private static String afterEquals(String line) {
            int index = line.indexOf('=');
            return index < 0 ? "" : line.substring(index + 1).trim();
        }

        private static String valueAfter(String line, String key) {
            int index = line.indexOf(key);
            if (index < 0) return "";
            String rest = line.substring(index + key.length());
            int end = rest.indexOf(' ');
            return end >= 0 ? rest.substring(0, end) : rest;
        }
    }
}
