(function () {
    let It = null, rr = 0;
    const ii = "/data/adb/agh";
    const settingsPath = ii + "/settings.conf";
    const binLogPath = ii + "/bin.log";
    const historyLogPath = ii + "/history.log";
    const scriptTool = ii + "/scripts/tool.sh";
    const scriptDebug = ii + "/scripts/debug.sh";
    const debugLogPath = ii + "/debug.log";
    const pidFile = ii + "/bin/agh.pid";
    const yamlPath = ii + "/bin/AdGuardHome.yaml";

    if (typeof ksu < "u" && typeof ksu.exec == "function") { It = ksu; }
    else if (typeof ap < "u" && typeof ap.exec == "function") { It = ap; }

    function exec(cmd) {
        return new Promise((resolve) => {
            if (!It) { resolve({ e: -1, s: "" }); return; }
            const cb = "_mc" + rr++;
            window[cb] = (code, out, err) => {
                delete window[cb];
                resolve({ e: code || 0, s: (out || "").replace(/\r/g, "") });
            };
            It.exec(cmd, "{}", cb);
        });
    }

    const $ = id => document.getElementById(id);
    const t = (key) => window.i18n?.t(key) || key;
    let currentStatus = "checking";
    const elements = {
        statusText: $('status-text'),
        statusContainer: $('status-container'),
        pidBadge: $('pid-badge'),
        version: $('agh-version'),
        logContent: $('log-content'),
        linkDomestic: $('btn-link-domestic'),
        linkForeign: $('btn-link-foreign'),
        fields: {
            integration_mode: $('conf-integration_mode'),
            domestic_enabled: $('conf-domestic_enabled'),
            domestic_dns_port: $('conf-domestic_dns_port'),
            domestic_web_port: $('conf-domestic_web_port'),
            foreign_enabled: $('conf-foreign_enabled'),
            foreign_dns_port: $('conf-foreign_dns_port'),
            foreign_web_port: $('conf-foreign_web_port'),
            enable_iptables: $('conf-enable_iptables'),
            block_ipv6_dns: $('conf-block_ipv6_dns'),
            redir_port: $('conf-redir_port'),
            adg_user: $('conf-adg_user'),
            adg_group: $('conf-adg_group')
        }
    };

    function showToast(msg) {
        const t = document.createElement('div');
        t.className = 'toast';
        t.textContent = msg;
        $('toast-container').appendChild(t);
        setTimeout(() => t.remove(), 3000);
    }

    async function refresh() {
        const marker = "::SPLIT::";
        const cmd = `
            cat ${settingsPath} 2>/dev/null; echo "${marker}";
            [ -f ${ii}/instances/domestic/agh.pid ] && cat ${ii}/instances/domestic/agh.pid || echo ""; echo "${marker}";
            [ -f ${ii}/instances/foreign/agh.pid ] && cat ${ii}/instances/foreign/agh.pid || echo ""; echo "${marker}";
            [ -f ${pidFile} ] && cat ${pidFile} || echo ""; echo "${marker}";
            /data/adb/agh/bin/AdGuardHome --version | head -1 | sed 's/.*version //'
        `;
        const res = await exec(cmd);
        const parts = res.s.split(marker).map(p => p.trim());

        const confText = parts[0] || "";
        const domesticPid = parts[1] || "";
        const foreignPid = parts[2] || "";
        const standalonePid = parts[3] || "";
        const conf = {};
        confText.split('\n').forEach(l => {
            const p = l.indexOf('=');
            if (p > 0) conf[l.slice(0, p).trim()] = l.slice(p + 1).trim().replace(/^"|"$/g, "");
        });
        const dual = conf.integration_mode === "box-dual";
        const dRunning = /^\d+$/.test(domesticPid);
        const fRunning = /^\d+$/.test(foreignPid);
        const sRunning = /^\d+$/.test(standalonePid);
        const isRunning = dual ? (dRunning || fRunning) : sRunning;
        document.body.className = isRunning ? 'running' : 'stopped';
        currentStatus = isRunning ? "running" : "stopped";
        if (dual) {
            elements.statusText.textContent = `Domestic: ${dRunning ? "UP" : "DOWN"} / Foreign: ${fRunning ? "UP" : "DOWN"}`;
            elements.pidBadge.textContent = `D:${domesticPid || "-"} F:${foreignPid || "-"}`;
            elements.pidBadge.classList.remove('hidden');
        } else {
            elements.statusText.textContent = t(`status.${currentStatus}`);
            if (sRunning) {
                elements.pidBadge.textContent = "PID: " + standalonePid;
                elements.pidBadge.classList.remove('hidden');
            } else {
                elements.pidBadge.classList.add('hidden');
            }
        }

        if (!window._loaded) {
            for (let k in elements.fields) {
                const el = elements.fields[k];
                if (!el) continue;
                if (el.type === 'checkbox') el.checked = conf[k] === 'true';
                else el.value = conf[k] || "";
            }
            window._loaded = true;
        }

        window._webDomestic = "127.0.0.1:" + (conf.domestic_web_port || "3000");
        window._webForeign = "127.0.0.1:" + (conf.foreign_web_port || "3001");
        elements.version.textContent = (parts[4] || "...");
    }

    $('btn-start').onclick = async () => {
        showToast(t("toast.starting"));
        await exec(`${scriptTool} start`);
        refresh();
    };

    $('btn-stop').onclick = async () => {
        showToast(t("toast.stopping"));
        await exec(`${scriptTool} stop`);
        refresh();
    };

    $('btn-save').onclick = async () => {
        let cmd = "";
        for (let k in elements.fields) {
            const el = elements.fields[k];
            const v = el.type === 'checkbox' ? el.checked : el.value;
            cmd += `sed -i "s/^${k}=.*/${k}=${v}/" ${settingsPath}; `;
        }
        await exec(cmd);
        showToast(t("toast.saved"));
    };

    elements.linkDomestic.onclick = () => {
        window.open(`http://${window._webDomestic || "127.0.0.1:3000"}`, '_blank');
    };
    elements.linkForeign.onclick = () => {
        window.open(`http://${window._webForeign || "127.0.0.1:3001"}`, '_blank');
    };

    document.querySelectorAll('[data-tab]').forEach(b => {
        b.onclick = () => {
            document.querySelectorAll('.tab-btn, .tab-content').forEach(el => el.classList.remove('active'));
            b.classList.add('active');
            $('tab-' + b.dataset.tab).classList.add('active');
            if (b.dataset.tab === 'logs') loadLog();
        };
    });

    let logType = 'bin';
    async function loadLog() {
        elements.logContent.textContent = t("toast.loading");
        const cmd = logType === 'bin'
            ? `echo "===== DOMESTIC ====="; tail -n 60 ${ii}/instances/domestic/agh.log 2>/dev/null; echo "===== FOREIGN ====="; tail -n 60 ${ii}/instances/foreign/agh.log 2>/dev/null; echo "===== STANDALONE ====="; tail -n 40 ${binLogPath} 2>/dev/null`
            : `tail -n 100 ${historyLogPath}`;
        const res = await exec(cmd);
        if (!res.s) {
            elements.logContent.textContent = t("toast.no_logs");
            return;
        }

        // Apply simple highlighting
        const lines = res.s.split('\n').map(line => {
            if (!line.trim()) return "";
            // Highlight Timestamps
            line = line.replace(/^(\d{4}[-/]\d{2}[-/]\d{2}\s\d{2}:\d{2}:\d{2}(\.\d+)?)/, '<span style="color:#888">$1</span>');
            // Highlight Levels
            line = line.replace(/\[(info|INFO|debug|DEBUG)\]/, '[<span style="color:#4CAF50">$1</span>]');
            line = line.replace(/\[(warn|WARN|warning|WARNING)\]/, '[<span style="color:#FFC107">$1</span>]');
            line = line.replace(/\[(error|ERROR|fatal|FATAL)\]/, '[<span style="color:#F44336">$1</span>]');
            // Status icons in history log
            line = line.replace(/(🟢|🔴|🧹|LOG)/g, '<span style="filter:drop-shadow(0 0 2px rgba(255,255,255,0.3))">$1</span>');
            return `<div>${line}</div>`;
        });

        elements.logContent.innerHTML = lines.join('');
        elements.logContent.scrollTop = elements.logContent.scrollHeight;
    }

    document.querySelectorAll('[data-log]').forEach(b => {
        b.onclick = () => {
            document.querySelectorAll('[data-log]').forEach(el => el.classList.remove('active'));
            b.classList.add('active');
            logType = b.dataset.log;
            loadLog();
        };
    });

    $('btn-refresh-log').onclick = loadLog;

    $('btn-debug').onclick = async () => {
        showToast(t("toast.generating"));
        await exec(`${scriptDebug}`);
        showToast(`${t("toast.generated")} ${debugLogPath}`);
    };

    $('btn-open-debug').onclick = async () => {
        // Use system action to open the file instead of internal viewer
        await exec(`am start -a android.intent.action.VIEW -d "file://${debugLogPath}" -t "text/plain" || am start -a android.intent.action.VIEW -d "content://com.android.externalstorage.documents/document/primary%3A${debugLogPath.replace(/^\/data\/adb\//, 'adb/')}" -t "text/plain"`);
        showToast(t("toast.opening_file"));
    };

    document.addEventListener("languageChanged", () => {
        if (currentStatus === "running" || currentStatus === "stopped") {
            elements.statusText.textContent = t(`status.${currentStatus}`);
        }

        if (elements.logContent.textContent === t("toast.no_logs")) {
            elements.logContent.textContent = t("logs.waiting");
        }
    });

    if (It) {
        refresh();
        setInterval(refresh, 5000);
    } else {
        elements.statusText.textContent = t("toast.open_manager");
    }
})();
