package io.github.chenyurumeng.aghmanager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String TOOL = "/data/adb/agh/scripts/tool.sh";
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private TextView rootState;
    private TextView domesticState;
    private TextView foreignState;
    private TextView integrationState;
    private boolean destroyed;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(buildUi());
        refreshStatus();
        main.postDelayed(autoRefresh, 5000);
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = column();
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        scroll.addView(root);

        TextView title = text("AGH Manager", 26, true);
        root.addView(title);
        TextView subtitle = text("AdGuardHomeForRoot · Box Dual DNS", 13, false);
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setPadding(0, 0, 0, dp(16));
        root.addView(subtitle);

        rootState = statusLine("Root: checking…");
        integrationState = statusLine("Integration: checking…");
        domesticState = statusLine("Domestic :5591 / Web :3000 · checking…");
        foreignState = statusLine("Foreign :5592 / Web :3001 · checking…");
        root.addView(rootState);
        root.addView(integrationState);
        root.addView(domesticState);
        root.addView(foreignState);

        Button refresh = button("刷新状态");
        refresh.setOnClickListener(v -> refreshStatus());
        root.addView(refresh);

        root.addView(section("全部实例"));
        root.addView(row(
                actionButton("全部启动", "start"),
                actionButton("全部停止", "stop"),
                actionButton("全部重启", "restart")
        ));

        root.addView(section("Domestic · 5591 / 3000"));
        root.addView(row(
                actionButton("启动", "start-domestic"),
                actionButton("停止", "stop-domestic"),
                actionButton("重启", "restart-domestic")
        ));
        Button domesticWeb = button("打开 Domestic AdGuard Home");
        domesticWeb.setOnClickListener(v -> openWeb("Domestic AGH", "http://127.0.0.1:3000"));
        root.addView(domesticWeb);

        root.addView(section("Foreign · 5592 / 3001"));
        root.addView(row(
                actionButton("启动", "start-foreign"),
                actionButton("停止", "stop-foreign"),
                actionButton("重启", "restart-foreign")
        ));
        Button foreignWeb = button("打开 Foreign AdGuard Home");
        foreignWeb.setOnClickListener(v -> openWeb("Foreign AGH", "http://127.0.0.1:3001"));
        root.addView(foreignWeb);

        root.addView(section("诊断"));
        Button logs = button("查看 AGH 日志");
        logs.setOnClickListener(v -> showLogs());
        root.addView(logs);

        TextView note = text(
                "所有启停操作均调用 /data/adb/agh/scripts/tool.sh。\n" +
                "APK 不直接修改 iptables，也不复制 Box/AGH 的 DNS 回退逻辑。",
                12, false);
        note.setTextColor(Color.GRAY);
        note.setPadding(0, dp(18), 0, 0);
        root.addView(note);

        return scroll;
    }

    private final Runnable autoRefresh = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            refreshStatus();
            main.postDelayed(this, 5000);
        }
    };

    private void refreshStatus() {
        io.execute(() -> {
            RootShell.Result r = RootShell.exec(
                    "echo '===MODULE==='; " +
                    "[ -x " + TOOL + " ] && echo READY || echo MISSING; " +
                    "echo '===STATUS==='; " +
                    TOOL + " status 2>&1; " +
                    "echo '===BOX==='; " +
                    "if [ -f /data/adb/box/settings.ini ]; then " +
                    "grep -E '^(dns_hijack_mode|domestic_dns_port|foreign_dns_port|foreign_dns_fallback_port)=' /data/adb/box/settings.ini; " +
                    "else echo 'Box settings missing'; fi"
            );
            main.post(() -> renderStatus(r));
        });
    }

    private void renderStatus(RootShell.Result r) {
        if (!r.ok()) {
            rootState.setText("Root: DENIED / unavailable");
            rootState.setTextColor(Color.rgb(255, 110, 110));
            domesticState.setText("Domestic: unavailable");
            foreignState.setText("Foreign: unavailable");
            integrationState.setText("Module: cannot query");
            return;
        }

        rootState.setText("Root: OK");
        rootState.setTextColor(Color.rgb(120, 220, 140));

        String out = r.output;
        boolean moduleReady = out.contains("===MODULE===\nREADY");
        integrationState.setText(moduleReady ? "Module: READY · tool.sh detected" : "Module: MISSING");
        integrationState.setTextColor(moduleReady ? Color.WHITE : Color.rgb(255, 170, 80));

        String d = findLine(out, "domestic:");
        String f = findLine(out, "foreign:");
        domesticState.setText("Domestic :5591 / Web :3000 · " + (d.isEmpty() ? "UNKNOWN" : d.replace("domestic: ", "").toUpperCase()));
        foreignState.setText("Foreign :5592 / Web :3001 · " + (f.isEmpty() ? "UNKNOWN" : f.replace("foreign: ", "").toUpperCase()));
        domesticState.setTextColor(d.contains("up") ? Color.rgb(120, 220, 140) : Color.rgb(255, 170, 80));
        foreignState.setTextColor(f.contains("up") ? Color.rgb(120, 220, 140) : Color.rgb(255, 170, 80));

        String mode = findLine(out, "dns_hijack_mode=");
        if (!mode.isEmpty()) integrationState.setText(integrationState.getText() + " · Box " + mode);
    }

    private String findLine(String text, String prefix) {
        for (String line : text.split("\n")) {
            line = line.trim();
            if (line.startsWith(prefix)) return line;
        }
        return "";
    }

    private Button actionButton(String label, String action) {
        Button b = button(label);
        b.setOnClickListener(v -> runAction(label, action));
        return b;
    }

    private void runAction(String label, String action) {
        Toast.makeText(this, label + "…", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            RootShell.Result r = RootShell.exec(TOOL + " " + action);
            main.post(() -> {
                String msg = r.ok() ? label + "完成" : label + "失败: " + r.output;
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                refreshStatus();
            });
        });
    }

    private void showLogs() {
        io.execute(() -> {
            RootShell.Result r = RootShell.exec(
                    "echo '===== DOMESTIC ====='; " +
                    "tail -n 80 /data/adb/agh/instances/domestic/agh.log 2>/dev/null; " +
                    "echo; echo '===== FOREIGN ====='; " +
                    "tail -n 80 /data/adb/agh/instances/foreign/agh.log 2>/dev/null; " +
                    "echo; echo '===== MODULE ====='; " +
                    "tail -n 80 /data/adb/agh/history.log 2>/dev/null"
            );
            main.post(() -> {
                TextView body = text(r.output.isEmpty() ? "(no logs)" : r.output, 12, false);
                body.setTypeface(Typeface.MONOSPACE);
                body.setTextIsSelectable(true);
                body.setMovementMethod(new ScrollingMovementMethod());
                body.setPadding(dp(12), dp(8), dp(12), dp(8));
                ScrollView sv = new ScrollView(this);
                sv.addView(body);
                new AlertDialog.Builder(this)
                        .setTitle("AGH Logs")
                        .setView(sv)
                        .setPositiveButton("关闭", null)
                        .show();
            });
        });
    }

    private void openWeb(String title, String url) {
        Intent i = new Intent(this, WebViewActivity.class);
        i.putExtra("title", title);
        i.putExtra("url", url);
        startActivity(i);
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private LinearLayout row(Button... buttons) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        for (Button b : buttons) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(52), 1);
            p.setMargins(dp(3), dp(3), dp(3), dp(3));
            l.addView(b, p);
        }
        return l;
    }

    private TextView section(String s) {
        TextView v = text(s, 18, true);
        v.setPadding(0, dp(22), 0, dp(8));
        return v;
    }

    private TextView statusLine(String s) {
        TextView v = text(s, 15, false);
        v.setPadding(dp(12), dp(10), dp(12), dp(10));
        v.setBackgroundColor(Color.rgb(38, 38, 38));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(3), 0, dp(3));
        v.setLayoutParams(p);
        return v;
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextColor(Color.WHITE);
        v.setTextSize(sp);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        main.removeCallbacks(autoRefresh);
        io.shutdownNow();
        super.onDestroy();
    }
}
