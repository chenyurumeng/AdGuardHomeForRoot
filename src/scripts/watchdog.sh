. /data/adb/agh/settings.conf
. /data/adb/agh/scripts/base.sh

integration_mode="${integration_mode:-standalone}"
[ "$integration_mode" = "box-dual" ] || exit 0

watchdog_pid="$AGH_DIR/watchdog.pid"
echo $$ > "$watchdog_pid"
trap 'rm -f "$watchdog_pid"; exit 0' INT TERM EXIT

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

snapshot() {
  local d="down" f="down"
  is_port_listening "${domestic_dns_port:-5591}" && d="up"
  is_port_listening "${foreign_dns_port:-5592}" && f="up"
  printf 'domestic=%s foreign=%s\n' "$d" "$f"
}

notify_box_dns() {
  local box_iptables="/data/adb/box/scripts/box.iptables"
  [ -x "$box_iptables" ] || return 0
  "$box_iptables" renew-dns >/dev/null 2>&1 || true
}

last=""
while [ -f "$AGH_DIR/dual.enabled" ]; do
  current="$(snapshot)"
  if [ "$current" != "$last" ]; then
    log "AGH listener state changed: $current" "AGH 监听状态变化: $current"
    notify_box_dns
    last="$current"
  fi
  sleep 5
done
