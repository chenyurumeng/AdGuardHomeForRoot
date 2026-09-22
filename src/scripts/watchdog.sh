. /data/adb/agh/settings.conf
. /data/adb/agh/scripts/base.sh

integration_mode="${integration_mode:-standalone}"
[ "$integration_mode" = "box-dual" ] || exit 0

watchdog_pid="$AGH_DIR/watchdog.pid"
echo $$ > "$watchdog_pid"
trap 'rm -f "$watchdog_pid"; exit 0' INT TERM EXIT

box_settings="/data/adb/box/settings.ini"
box_user_stopped_file="/data/adb/box/run/state/user_stopped"

is_port_listening() {
  local port="$1"
  local port_hex f
  port_hex=$(printf '%04X' "$port")
  for f in /proc/net/udp /proc/net/udp6 /proc/net/tcp /proc/net/tcp6; do
    [ -r "$f" ] || continue
    awk -v pat=":$port_hex$" 'NR > 1 && $2 ~ pat { found = 1 } END { exit !found }' "$f" && return 0
  done
  return 1
}

box_fallback_port() {
  local port
  port=$(sed -n 's/^foreign_dns_fallback_port="\{0,1\}\([0-9][0-9]*\)"\{0,1\}$/\1/p' "$box_settings" 2>/dev/null | head -n 1)
  case "$port" in
    ''|*[!0-9]*) port=1053 ;;
  esac
  printf '%s\n' "$port"
}

snapshot() {
  local d="down" f="down" m="down" fallback_port
  fallback_port=$(box_fallback_port)
  is_port_listening "${domestic_dns_port:-5591}" && d="up"
  is_port_listening "${foreign_dns_port:-5592}" && f="up"
  is_port_listening "$fallback_port" && m="up"
  printf 'domestic=%s foreign=%s mihomo=%s fallback_port=%s\n' "$d" "$f" "$m" "$fallback_port"
}

notify_box_dns() {
  local box_iptables="/data/adb/box/scripts/box.iptables"
  [ -x "$box_iptables" ] || return 0

  # An intentional Box stop must restore system DNS. Listener state may still
  # change afterwards (:1053 goes down), but that is not a reason to rebuild
  # split-apps DNS hooks.
  if [ -f "$box_user_stopped_file" ]; then
    log "Box is intentionally stopped; skip DNS refresh" "Box 已主动停止；跳过 DNS 规则刷新"
    return 0
  fi

  "$box_iptables" renew-dns >/dev/null 2>&1 || true
}

last=""
while [ -f "$AGH_DIR/dual.enabled" ]; do
  current="$(snapshot)"
  if [ "$current" != "$last" ]; then
    log "AGH/Box DNS listener state changed: $current" "AGH/Box DNS 监听状态变化: $current"
    # Always advance the snapshot even while intentionally stopped. Otherwise
    # a stale state can generate a spurious refresh after the next start.
    last="$current"
    notify_box_dns
  fi
  sleep 5
done
