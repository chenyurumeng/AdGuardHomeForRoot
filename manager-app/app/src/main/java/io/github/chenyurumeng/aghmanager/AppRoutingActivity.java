package io.github.chenyurumeng.aghmanager;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppRoutingActivity extends Activity {
    private static final String BOX_SERVICE = "/data/adb/box/scripts/box.service";
    private static final String BOX_SETTINGS = "/data/adb/box/settings.ini";
    private static final String BOX_PACKAGE_LIST = "/data/adb/box/package.list.cfg";

    private static final String CACHE_PREFS = "app_routing_cache_v2";
    private static final String KEY_CACHE_APPS = "apps";
    private static final String KEY_CACHE_MODE = "mode";
    private static final String KEY_CACHE_SELECTED = "selected";

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

    private final List<AppEntry> allApps = new ArrayList<>();
    private final List<AppEntry> filteredApps = new ArrayList<>();
    private final Set<String> selected = new HashSet<>();
    private final Set<String> appliedSelected = new HashSet<>();
    private final Map<Integer, String> userNames = new HashMap<>();

    private PackageManager pm;
    private SharedPreferences cachePrefs;
    private AppAdapter adapter;
    private TextView blacklistButton;
    private TextView whitelistButton;
    private TextView allFilterButton;
    private TextView userFilterButton;
    private TextView systemFilterButton;
    private TextView summary;
    private TextView applyState;
    private TextView applyButton;
    private EditText search;

    private String mode = "whitelist";
    private String appliedMode = "whitelist";
    private String filter = "all";
    private String query = "";
    private boolean destroyed;
    private boolean applying;
    private boolean dirty;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        pm = getPackageManager();
        cachePrefs = getSharedPreferences(CACHE_PREFS, MODE_PRIVATE);

        setContentView(buildUi());
        loadCacheImmediately();
        syncIncrementally();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(14), 0, dp(14), dp(12));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            v.setPadding(dp(14), top + dp(8), dp(14), dp(12));
            return insets;
        });

        LinearLayout header = row();
        TextView back = button("‹", SURFACE_ALT);
        back.setTextSize(26);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout titles = column();
        titles.setPadding(dp(10), 0, 0, 0);
        titles.addView(text("应用分流", 24, true));
        applyState = text("正在载入本地缓存…", 11, false);
        applyState.setTextColor(MUTED);
        titles.addView(applyState);
        header.addView(titles, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        root.addView(header);

        LinearLayout modeRow = row();
        modeRow.setPadding(0, dp(14), 0, dp(10));
        blacklistButton = segment("Blacklist");
        whitelistButton = segment("Whitelist");
        blacklistButton.setOnClickListener(v -> setMode("blacklist"));
        whitelistButton.setOnClickListener(v -> setMode("whitelist"));
        modeRow.addView(blacklistButton, weightHeight(50));
        modeRow.addView(whitelistButton, weightHeight(50));
        root.addView(modeRow);
        updateModeButtons();

        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("搜索应用 / 包名");
        search.setHintTextColor(MUTED);
        search.setTextColor(TEXT);
        search.setTextSize(17);
        search.setPadding(dp(18), 0, dp(18), 0);
        search.setBackground(rounded(SURFACE_ALT, BORDER, 18));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                rebuildFilter();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        root.addView(search, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56)));

        LinearLayout filterRow = row();
        filterRow.setPadding(0, dp(12), 0, dp(8));
        allFilterButton = filterButton("✓  All", "all");
        userFilterButton = filterButton("User", "user");
        systemFilterButton = filterButton("System", "system");
        filterRow.addView(allFilterButton, weightHeight(44));
        filterRow.addView(userFilterButton, weightHeight(44));
        filterRow.addView(systemFilterButton, weightHeight(44));
        root.addView(filterRow);
        updateFilterButtons();

        summary = text("正在读取缓存…", 12, false);
        summary.setTextColor(MUTED);
        summary.setPadding(dp(4), dp(2), dp(4), dp(8));
        root.addView(summary);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setBackgroundColor(BG);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, dp(8));
        adapter = new AppAdapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        applyButton = button("应用", BLUE);
        applyButton.setTextSize(15);
        applyButton.setOnClickListener(v -> applyRouting());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        sp.setMargins(0, dp(8), 0, 0);
        root.addView(applyButton, sp);

        TextView hint = text(
                "选择、取消或切换模式只在本页暂存；点击“应用”后才写入 Box 并立即生效。",
                11, false);
        hint.setTextColor(MUTED);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(7), 0, 0);
        root.addView(hint);

        root.requestApplyInsets();
        return root;
    }

    private void loadCacheImmediately() {
        List<AppEntry> cached = readCachedApps();
        allApps.clear();
        allApps.addAll(cached);

        String cachedMode = cachePrefs.getString(KEY_CACHE_MODE, "whitelist");
        mode = normalizeMode(cachedMode);
        appliedMode = mode;

        selected.clear();
        selected.addAll(decodeSelected(cachePrefs.getString(KEY_CACHE_SELECTED, "")));
        appliedSelected.clear();
        appliedSelected.addAll(selected);

        updateModeButtons();
        rebuildFilter();
        dirty = false;
        updateApplyUi();

        if (allApps.isEmpty()) {
            applyState.setText("首次建立应用缓存 · 正在读取设备应用…");
        } else {
            applyState.setText("已从本地缓存加载 " + allApps.size() + " 个应用 · 后台增量检查中…");
        }
        applyState.setTextColor(MUTED);
    }

    private void syncIncrementally() {
        io.execute(() -> {
            RootShell.Result result = RootShell.exec(buildStateCommand(), 45);
            ParsedState parsed = parseState(result.output);

            if (!result.ok()) {
                main.post(() -> {
                    if (destroyed) return;
                    applyState.setText("后台同步失败 · 继续使用本地缓存");
                    applyState.setTextColor(ORANGE);
                });
                return;
            }

            List<AppEntry> cached = readCachedApps();
            MergeResult merge = mergeApps(cached, parsed);
            persistCache(merge.apps, parsed.mode, parsed.selected);

            main.post(() -> {
                if (destroyed) return;

                allApps.clear();
                allApps.addAll(merge.apps);
                userNames.clear();
                userNames.putAll(parsed.userNames);

                appliedMode = normalizeMode(parsed.mode);
                appliedSelected.clear();
                appliedSelected.addAll(parsed.selected);

                if (!dirty) {
                    mode = appliedMode;
                    selected.clear();
                    selected.addAll(appliedSelected);
                }

                updateModeButtons();
                rebuildFilter();
                updateDirtyState();

                String delta;
                if (merge.added == 0 && merge.removed == 0) {
                    delta = "无应用变化";
                } else {
                    delta = "新增 " + merge.added + " · 删除 " + merge.removed;
                }
                applyState.setText("缓存已同步 · " + allApps.size() + " 个应用 · " + delta);
                applyState.setTextColor(GREEN);
            });
        });
    }

    private String buildStateCommand() {
        return "echo '__MODE__'; " +
                "sed -n 's/^proxy_mode=\"\\([^\"]*\\)\".*/\\1/p' " + BOX_SETTINGS + " | head -n1; " +
                "echo '__SELECTED__'; cat " + BOX_PACKAGE_LIST + " 2>/dev/null || true; " +
                "echo '__USERS__'; pm list users 2>/dev/null || true; " +
                "echo '__PACKAGES__'; " +
                "for u in $(pm list users 2>/dev/null | sed -n 's/.*UserInfo{\\([0-9][0-9]*\\):.*/\\1/p'); do " +
                "pm list packages --user \"$u\" 2>/dev/null | sed \"s/^package:/$u|/\"; done; " +
                "echo '__END__'";
    }

    private ParsedState parseState(String output) {
        ParsedState state = new ParsedState();
        String section = "";
        Pattern userPattern = Pattern.compile("UserInfo\\{(\\d+):([^:}]+)");

        for (String raw : (output == null ? "" : output).split("\n")) {
            String line = raw.trim();
            if (line.startsWith("__") && line.endsWith("__")) {
                section = line;
                continue;
            }
            if (line.isEmpty()) continue;

            switch (section) {
                case "__MODE__":
                    if (state.mode.isEmpty()) state.mode = normalizeMode(line);
                    break;
                case "__SELECTED__":
                    if (line.startsWith("#")) break;
                    if (line.matches("[0-9]+:[A-Za-z0-9._]+")) {
                        state.selected.add(line);
                    } else if (line.matches("[A-Za-z0-9._]+")) {
                        state.selected.add("0:" + line);
                    }
                    break;
                case "__USERS__": {
                    Matcher m = userPattern.matcher(line);
                    if (m.find()) {
                        try {
                            state.userNames.put(Integer.parseInt(m.group(1)), m.group(2));
                        } catch (NumberFormatException ignored) {}
                    }
                    break;
                }
                case "__PACKAGES__": {
                    int sep = line.indexOf('|');
                    if (sep <= 0 || sep >= line.length() - 1) break;
                    try {
                        int userId = Integer.parseInt(line.substring(0, sep));
                        String pkg = line.substring(sep + 1);
                        if (pkg.matches("[A-Za-z0-9._]+")) {
                            state.packages.add(new PackageKey(userId, pkg));
                        }
                    } catch (NumberFormatException ignored) {}
                    break;
                }
            }
        }

        if (state.mode.isEmpty()) state.mode = "whitelist";
        return state;
    }

    private MergeResult mergeApps(List<AppEntry> cached, ParsedState parsed) {
        Map<String, AppEntry> old = new HashMap<>();
        for (AppEntry app : cached) old.put(app.key(), app);

        List<AppEntry> merged = new ArrayList<>();
        Set<String> live = new HashSet<>();
        int added = 0;

        for (PackageKey key : parsed.packages) {
            String unique = key.userId + ":" + key.pkg;
            if (!live.add(unique)) continue;

            String userName = parsed.userNames.get(key.userId);
            if (userName == null || userName.trim().isEmpty()) {
                userName = key.userId == 0 ? "Owner" : "User";
            }

            AppEntry previous = old.get(unique);
            if (previous != null) {
                merged.add(new AppEntry(
                        key.userId,
                        key.pkg,
                        previous.label,
                        userName,
                        previous.system,
                        null
                ));
            } else {
                merged.add(resolveNewEntry(key.userId, key.pkg, userName));
                added++;
            }
        }

        int removed = 0;
        for (String key : old.keySet()) {
            if (!live.contains(key)) removed++;
        }

        sortApps(merged);
        return new MergeResult(merged, added, removed);
    }

    private AppEntry resolveNewEntry(int userId, String pkg, String userName) {
        String label = pkg;
        boolean system = false;
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES);
            CharSequence value = pm.getApplicationLabel(ai);
            if (value != null && value.length() > 0) label = value.toString();
            system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (Exception ignored) {}
        return new AppEntry(userId, pkg, label, userName, system, null);
    }

    private List<AppEntry> readCachedApps() {
        List<AppEntry> result = new ArrayList<>();
        String raw = cachePrefs.getString(KEY_CACHE_APPS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) continue;

                int userId = o.optInt("userId", 0);
                String pkg = o.optString("pkg", "");
                if (!pkg.matches("[A-Za-z0-9._]+")) continue;

                String label = o.optString("label", pkg);
                String userName = o.optString("userName", userId == 0 ? "Owner" : "User");
                boolean system = o.optBoolean("system", false);
                result.add(new AppEntry(userId, pkg, label, userName, system, null));
            }
        } catch (Exception ignored) {}
        sortApps(result);
        return result;
    }

    private void persistCache(List<AppEntry> apps, String cacheMode, Set<String> cacheSelected) {
        JSONArray array = new JSONArray();
        try {
            for (AppEntry app : apps) {
                JSONObject o = new JSONObject();
                o.put("userId", app.userId);
                o.put("pkg", app.pkg);
                o.put("label", app.label);
                o.put("userName", app.userName);
                o.put("system", app.system);
                array.put(o);
            }
        } catch (Exception ignored) {}

        cachePrefs.edit()
                .putString(KEY_CACHE_APPS, array.toString())
                .putString(KEY_CACHE_MODE, normalizeMode(cacheMode))
                .putString(KEY_CACHE_SELECTED, encodeSelected(cacheSelected))
                .apply();
    }

    private String encodeSelected(Set<String> values) {
        List<String> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        StringBuilder out = new StringBuilder();
        for (String value : sorted) {
            if (value.matches("[0-9]+:[A-Za-z0-9._]+")) {
                out.append(value).append('\n');
            }
        }
        return out.toString();
    }

    private Set<String> decodeSelected(String raw) {
        Set<String> result = new HashSet<>();
        for (String line : (raw == null ? "" : raw).split("\n")) {
            String value = line.trim();
            if (value.matches("[0-9]+:[A-Za-z0-9._]+")) result.add(value);
        }
        return result;
    }

    private void sortApps(List<AppEntry> apps) {
        Collections.sort(apps, Comparator
                .comparing((AppEntry a) -> a.label.toLowerCase(Locale.ROOT))
                .thenComparingInt(a -> a.userId)
                .thenComparing(a -> a.pkg));
    }

    private void rebuildFilter() {
        filteredApps.clear();
        for (AppEntry app : allApps) {
            if ("user".equals(filter) && app.system) continue;
            if ("system".equals(filter) && !app.system) continue;

            if (!query.isEmpty()) {
                String hay = (app.label + " " + app.pkg + " " + app.userName + " " + app.userId)
                        .toLowerCase(Locale.ROOT);
                if (!hay.contains(query)) continue;
            }
            filteredApps.add(app);
        }
        if (adapter != null) adapter.notifyDataSetChanged();
        updateSummary();
    }

    private void updateSummary() {
        if (summary == null) return;
        String semantics = "whitelist".equals(mode)
                ? "选中应用走代理 / Foreign 5592"
                : "选中应用直连 / Domestic 5591";
        summary.setText("已选 " + selected.size() + " · 显示 " + filteredApps.size()
                + " / " + allApps.size() + " · " + semantics
                + (dirty ? " · 有未应用更改" : ""));
    }

    private void setMode(String newMode) {
        String normalized = normalizeMode(newMode);
        if (normalized.equals(mode)) return;
        mode = normalized;
        updateModeButtons();
        updateDirtyState();
    }

    private String normalizeMode(String value) {
        if ("blacklist".equalsIgnoreCase(value) || "black".equalsIgnoreCase(value)) {
            return "blacklist";
        }
        return "whitelist";
    }

    private void setFilter(String newFilter) {
        filter = newFilter;
        updateFilterButtons();
        rebuildFilter();
    }

    private void updateModeButtons() {
        if (blacklistButton == null || whitelistButton == null) return;
        styleSegment(blacklistButton, "blacklist".equals(mode));
        styleSegment(whitelistButton, "whitelist".equals(mode));
    }

    private void updateFilterButtons() {
        if (allFilterButton == null) return;
        styleFilter(allFilterButton, "all".equals(filter));
        styleFilter(userFilterButton, "user".equals(filter));
        styleFilter(systemFilterButton, "system".equals(filter));
    }

    private void updateDirtyState() {
        dirty = !mode.equals(appliedMode) || !selected.equals(appliedSelected);
        updateApplyUi();
        updateSummary();
    }

    private void updateApplyUi() {
        if (applyButton == null) return;
        applyButton.setText("应用");
        applyButton.setAlpha(applying ? 0.55f : (dirty ? 1.0f : 0.82f));
    }

    private void applyRouting() {
        if (destroyed || applying) return;

        final String applyMode = mode;
        final List<String> entries = new ArrayList<>(selected);
        Collections.sort(entries);

        applying = true;
        updateApplyUi();
        applyState.setText("正在应用 " + applyMode + "…");
        applyState.setTextColor(BLUE);

        io.execute(() -> {
            StringBuilder list = new StringBuilder();
            for (String entry : entries) {
                if (!entry.matches("[0-9]+:[A-Za-z0-9._]+")) continue;
                list.append(entry).append('\n');
            }

            String command =
                    "set -e; tmp=" + BOX_PACKAGE_LIST + ".manager.tmp; " +
                    "cat > \"$tmp\" <<'__BOX_APPS_EOF__'\n" +
                    list +
                    "__BOX_APPS_EOF__\n" +
                    "chmod 0644 \"$tmp\"; mv \"$tmp\" " + BOX_PACKAGE_LIST + "; " +
                    "sed -i 's/^proxy_mode=.*/proxy_mode=\"" + applyMode + "\"/' " + BOX_SETTINGS + "; " +
                    BOX_SERVICE + " apply-apps";

            RootShell.Result result = RootShell.exec(command, 120);
            main.post(() -> {
                applying = false;
                if (destroyed) return;

                if (result.ok()) {
                    appliedMode = applyMode;
                    appliedSelected.clear();
                    appliedSelected.addAll(entries);
                    persistCache(allApps, appliedMode, appliedSelected);
                    dirty = false;
                    applyState.setText("已应用 · " + appliedMode + " · " + entries.size() + " 个应用");
                    applyState.setTextColor(GREEN);
                    Toast.makeText(this, "应用分流已生效", Toast.LENGTH_SHORT).show();
                } else {
                    applyState.setText("应用失败 · exit=" + result.code);
                    applyState.setTextColor(RED);
                    Toast.makeText(this,
                            result.output.isEmpty() ? "应用分流失败" : result.output,
                            Toast.LENGTH_LONG).show();
                }
                updateApplyUi();
                updateSummary();
            });
        });
    }

    private TextView filterButton(String label, String value) {
        TextView b = segment(label);
        b.setOnClickListener(v -> setFilter(value));
        return b;
    }

    private TextView segment(String label) {
        TextView b = text(label, 14, true);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true);
        b.setBackground(rounded(SURFACE_ALT, BORDER, 14));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1);
        p.setMargins(dp(3), 0, dp(3), 0);
        b.setLayoutParams(p);
        return b;
    }

    private void styleSegment(TextView v, boolean active) {
        v.setTextColor(active ? Color.WHITE : MUTED);
        v.setBackground(rounded(active ? BLUE : SURFACE_ALT, active ? BLUE : BORDER, 14));
    }

    private void styleFilter(TextView v, boolean active) {
        v.setTextColor(active ? Color.WHITE : MUTED);
        v.setBackground(rounded(active ? BLUE : SURFACE_ALT, active ? BLUE : BORDER, 14));
    }

    private TextView button(String label, int color) {
        TextView v = text(label, 14, true);
        v.setGravity(Gravity.CENTER);
        v.setClickable(true);
        v.setTextColor(Color.WHITE);
        v.setBackground(rounded(color, color, 14));
        return v;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(TEXT);
        v.setTextSize(sp);
        v.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return v;
    }

    private LinearLayout row() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    private LinearLayout column() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    private LinearLayout.LayoutParams weightHeight(int heightDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(heightDp), 1);
        p.setMargins(dp(3), 0, dp(3), 0);
        return p;
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        io.shutdownNow();
        super.onDestroy();
    }

    private final class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return filteredApps.size(); }
        @Override public AppEntry getItem(int position) { return filteredApps.get(position); }
        @Override public long getItemId(int position) { return getItem(position).key().hashCode(); }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            RowHolder holder;
            if (convertView == null) {
                LinearLayout row = new LinearLayout(AppRoutingActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(10), dp(10), dp(10));
                row.setBackground(rounded(SURFACE, BORDER, 12));

                ImageView icon = new ImageView(AppRoutingActivity.this);
                icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                row.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));

                LinearLayout labels = column();
                labels.setPadding(dp(12), 0, dp(8), 0);
                TextView name = text("", 16, false);
                TextView pkg = text("", 11, false);
                pkg.setTextColor(MUTED);
                labels.addView(name);
                labels.addView(pkg);
                row.addView(labels, new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

                CheckBox check = new CheckBox(AppRoutingActivity.this);
                check.setButtonTintList(new android.content.res.ColorStateList(
                        new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                        new int[]{BLUE, MUTED}));
                row.addView(check, new LinearLayout.LayoutParams(dp(52), dp(52)));

                holder = new RowHolder(icon, name, pkg, check);
                row.setTag(holder);
                convertView = row;
            } else {
                holder = (RowHolder) convertView.getTag();
            }

            AppEntry app = getItem(position);
            if (app.icon == null) {
                try {
                    app.icon = pm.getApplicationIcon(app.pkg);
                } catch (Exception ignored) {}
            }
            holder.icon.setImageDrawable(app.icon != null
                    ? app.icon
                    : getDrawable(android.R.drawable.sym_def_app_icon));
            holder.name.setText(app.label + (app.userId == 0 ? "" :
                    "  [" + app.userName + " " + app.userId + "]"));
            holder.pkg.setText(app.pkg + (app.system ? " · System" : " · User"));

            holder.check.setOnCheckedChangeListener(null);
            holder.check.setChecked(selected.contains(app.key()));
            holder.check.setOnCheckedChangeListener((buttonView, checked) -> {
                if (checked) selected.add(app.key());
                else selected.remove(app.key());
                updateDirtyState();
            });

            final CheckBox target = holder.check;
            convertView.setOnClickListener(v -> target.setChecked(!target.isChecked()));
            return convertView;
        }
    }

    private static final class RowHolder {
        final ImageView icon;
        final TextView name;
        final TextView pkg;
        final CheckBox check;

        RowHolder(ImageView icon, TextView name, TextView pkg, CheckBox check) {
            this.icon = icon;
            this.name = name;
            this.pkg = pkg;
            this.check = check;
        }
    }

    private static final class AppEntry {
        final int userId;
        final String pkg;
        final String label;
        final String userName;
        final boolean system;
        Drawable icon;

        AppEntry(int userId, String pkg, String label, String userName, boolean system, Drawable icon) {
            this.userId = userId;
            this.pkg = pkg;
            this.label = label;
            this.userName = userName;
            this.system = system;
            this.icon = icon;
        }

        String key() { return userId + ":" + pkg; }
    }

    private static final class PackageKey {
        final int userId;
        final String pkg;

        PackageKey(int userId, String pkg) {
            this.userId = userId;
            this.pkg = pkg;
        }
    }

    private static final class ParsedState {
        String mode = "";
        final Set<String> selected = new HashSet<>();
        final Map<Integer, String> userNames = new HashMap<>();
        final List<PackageKey> packages = new ArrayList<>();
    }

    private static final class MergeResult {
        final List<AppEntry> apps;
        final int added;
        final int removed;

        MergeResult(List<AppEntry> apps, int added, int removed) {
            this.apps = apps;
            this.added = added;
            this.removed = removed;
        }
    }
}
