# Box 双 AdGuard Home DNS 集成

本分支为 `chenyurumeng/box` 的 `dns_hijack_mode="split-apps"` 提供双 AdGuard Home 后端。

## 架构

同一个 `/data/adb/agh/bin/AdGuardHome` 二进制启动两个独立进程：

- Domestic：DNS `127.0.0.1/::1:5591`，Web `127.0.0.1:3000`
- Foreign：DNS `127.0.0.1/::1:5592`，Web `127.0.0.1:3001`

两个实例使用独立的配置、work-dir、PID、日志、缓存、过滤数据。

```text
Android DnsResolver
        |
        v
Box eBPF UID
   |         |
white      non-white
   |         |
 :5592     :5591
Foreign    Domestic
  AGH        AGH
   |          |
Cloudflare   AliDNS / DNSPod
DoH DIRECT   DoH DIRECT
```

## 关键安全约束

在 `integration_mode=box-dual` 下：

1. AdGuardHomeForRoot 不安装 DNS iptables；Box 是唯一 DNS dispatcher。
2. 两个 AGH 都以 `root:net_raw` 运行，Box 的 split-apps 模式对该进程身份整体 DIRECT。
3. Foreign AGH 不配置国内 fallback。
4. Domestic AGH 不配置国外 fallback。
5. watchdog 每 5 秒只检查 5591/5592 listener；状态变化时调用 Box 的 `renew-dns`。
6. watchdog 不循环重启 AGH，避免 crash-loop；Box 负责故障回退。

## 故障回退

| 状态 | 白名单 DNS | 非白名单 DNS |
|---|---|---|
| 5592 UP / 5591 UP | Foreign AGH | Domestic AGH |
| 5592 DOWN / Mihomo 1053 UP | Mihomo 1053 | Domestic AGH |
| 5592 DOWN / 1053 DOWN | fail-closed | Domestic AGH |
| 5591 DOWN | Foreign AGH | Android 系统 DNS |
| Box 停止 | Android 系统 DNS | Android 系统 DNS |

## 控制

```sh
/data/adb/agh/scripts/tool.sh status
/data/adb/agh/scripts/tool.sh start
/data/adb/agh/scripts/tool.sh stop
/data/adb/agh/scripts/tool.sh restart

/data/adb/agh/scripts/tool.sh start-domestic
/data/adb/agh/scripts/tool.sh stop-domestic
/data/adb/agh/scripts/tool.sh start-foreign
/data/adb/agh/scripts/tool.sh stop-foreign
```

## 健康检查

```sh
ss -lnup | grep -E ':5591|:5592'
ss -lntp | grep -E ':5591|:5592'

dig @127.0.0.1 -p 5591 www.baidu.com A
dig @127.0.0.1 -p 5592 www.google.com A
```

## 私人 DNS / 应用 DoH

为了让应用 DNS 进入 AGH：

- Android Private DNS 建议关闭。
- 浏览器 Secure DNS/DoH 建议关闭。

应用自带 DoH 使用 HTTPS 443，不属于 TCP/UDP 53 劫持范围，会绕过 AGH 过滤。

## 升级

升级会保留：

- `/data/adb/agh/instances/domestic/AdGuardHome.yaml`
- `/data/adb/agh/instances/foreign/AdGuardHome.yaml`

旧版本保留配置时会自动补齐 dual-mode 设置。升级旧模块前会先调用旧 `tool.sh stop` 撤销旧 DNS iptables，再停止进程，避免 DNS 黑洞。
