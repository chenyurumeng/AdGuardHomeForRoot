. /data/adb/agh/settings.conf
. /data/adb/agh/scripts/base.sh

# Old installations may preserve an earlier settings.conf without the dual-mode
# path constants.  Keep runtime paths safe even before settings migration runs.
[ -n "${AGH_DIR:-}" ] || AGH_DIR="/data/adb/agh"
[ -n "${BIN_DIR:-}" ] || BIN_DIR="$AGH_DIR/bin"
[ -n "${SCRIPT_DIR:-}" ] || SCRIPT_DIR="$AGH_DIR/scripts"
[ -n "${INSTANCE_DIR:-}" ] || INSTANCE_DIR="$AGH_DIR/instances"
[ -n "${PID_FILE:-}" ] || PID_FILE="$AGH_DIR/bin/agh.pid"
[ -n "${MOD_PATH:-}" ] || MOD_PATH="/data/adb/modules/AdGuardHome"

startup_timeout="${startup_timeout:-120}"
integration_mode="${integration_mode:-standalone}"
domestic_enabled="${domestic_enabled:-true}"
foreign_enabled="${foreign_enabled:-true}"
domestic_dns_port="${domestic_dns_port:-5591}"
foreign_dns_port="${foreign_dns_port:-5592}"

move_to_system_cgroup() {
  echo $$ > /sys/fs/cgroup/cgroup.procs 2>/dev/null
  [ -f /dev/memcg/system/cgroup.procs ] && echo $$ > /dev/memcg/system/cgroup.procs 2>/dev/null
  [ -f /dev/cpuctl/system/cgroup.procs ] && echo $$ > /dev/cpuctl/system/cgroup.procs 2>/dev/null
  [ -f /dev/cpuset/system-background/cgroup.procs ] && echo $$ > /dev/cpuset/system-background/cgroup.procs 2>/dev/null
  [ -f /dev/blkio/cgroup.procs ] && echo $$ > /dev/blkio/cgroup.procs 2>/dev/null
}

move_to_system_cgroup

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

wait_for_port() {
  local port="$1"
  local pid="$2"
  local waited=0
  while [ "$waited" -lt "$startup_timeout" ]; do
    is_port_listening "$port" && return 0
    kill -0 "$pid" 2>/dev/null || return 1
    sleep 1
    waited=$((waited + 1))
  done
  return 1
}

notify_box_dns() {
  local box_iptables="/data/adb/box/scripts/box.iptables"
  [ -x "$box_iptables" ] || return 0
  "$box_iptables" renew-dns >/dev/null 2>&1 || true
}

instance_dir() {
  printf '%s/%s\n' "$INSTANCE_DIR" "$1"
}

instance_config() {
  printf '%s/AdGuardHome.yaml\n' "$(instance_dir "$1")"
}

instance_pidfile() {
  printf '%s/agh.pid\n' "$(instance_dir "$1")"
}

instance_logfile() {
  printf '%s/agh.log\n' "$(instance_dir "$1")"
}

instance_port() {
  case "$1" in
    domestic) printf '%s\n' "$domestic_dns_port" ;;
    foreign) printf '%s\n' "$foreign_dns_port" ;;
    *) return 1 ;;
  esac
}

instance_enabled() {
  case "$1" in
    domestic) [ "$domestic_enabled" = "true" ] ;;
    foreign) [ "$foreign_enabled" = "true" ] ;;
    *) return 1 ;;
  esac
}

instance_pid() {
  local pidfile pid
  pidfile="$(instance_pidfile "$1")"
  [ -f "$pidfile" ] || return 1
  pid="$(cat "$pidfile" 2>/dev/null)"
  case "$pid" in
    ''|*[!0-9]*) return 1 ;;
  esac
  kill -0 "$pid" 2>/dev/null || return 1
  printf '%s\n' "$pid"
}

start_instance() {
  local name="$1"
  local dir config pidfile logfile port pid

  instance_enabled "$name" || {
    log "$name instance disabled" "$name 实例已禁用"
    return 0
  }

  if pid="$(instance_pid "$name" 2>/dev/null)"; then
    log "$name instance already running [PID: $pid]" "$name 实例已运行 [PID: $pid]"
    return 0
  fi

  dir="$(instance_dir "$name")"
  config="$(instance_config "$name")"
  pidfile="$(instance_pidfile "$name")"
  logfile="$(instance_logfile "$name")"
  port="$(instance_port "$name")"

  mkdir -p "$dir"
  [ -f "$config" ] || {
    log "$name config missing: $config" "$name 配置不存在: $config"
    return 1
  }

  rm -f "$pidfile"
  [ -f "$logfile" ] && mv "$logfile" "$logfile.bak"

  export SSL_CERT_DIR="/system/etc/security/cacerts/"
  export TZ="$timezone"

  busybox setuidgid "$adg_user:$adg_group" "$BIN_DIR/AdGuardHome" \
    --no-check-update \
    --config "$config" \
    --work-dir "$dir" \
    --pidfile "$pidfile" \
    >"$logfile" 2>&1 &

  pid=$!

  if ! wait_for_port "$port" "$pid"; then
    log "$name DNS listener failed on :$port" "$name DNS 监听端口 :$port 启动失败"
    kill "$pid" >/dev/null 2>&1 || true
    rm -f "$pidfile"
    return 1
  fi

  # Some AGH versions create the pidfile themselves slightly later.  Keep a
  # deterministic pidfile for the module even if that write has not happened.
  [ -s "$pidfile" ] || echo "$pid" > "$pidfile"
  log "$name instance ready [PID: $pid, DNS: $port]" "$name 实例已就绪 [PID: $pid, DNS: $port]"
  return 0
}

stop_instance() {
  local name="$1"
  local pidfile pid
  pidfile="$(instance_pidfile "$name")"

  if pid="$(instance_pid "$name" 2>/dev/null)"; then
    kill "$pid" >/dev/null 2>&1 || kill -9 "$pid" >/dev/null 2>&1 || true
    local waited=0
    while kill -0 "$pid" 2>/dev/null && [ "$waited" -lt 5 ]; do
      sleep 1
      waited=$((waited + 1))
    done
    kill -9 "$pid" >/dev/null 2>&1 || true
    log "$name instance stopped [PID: $pid]" "$name 实例已停止 [PID: $pid]"
  fi
  rm -f "$pidfile"
}

start_dual() {
  touch "$AGH_DIR/dual.enabled"
  # In box-dual mode AdGuardHomeForRoot never owns DNS iptables.  Box is the
  # sole dispatcher and can safely fall back if either listener is unavailable.
  "$SCRIPT_DIR/iptables.sh" disable >/dev/null 2>&1 || true

  local ok=true
  start_instance domestic || ok=false
  start_instance foreign || ok=false
  notify_box_dns

  if [ "$ok" = "true" ]; then
    update_description "🟢 Box dual DNS running [5591 domestic / 5592 foreign]" "🟢 Box 双 DNS 运行中 [5591 国内 / 5592 国外]"
    return 0
  fi

  update_description "🟡 Box dual DNS degraded; Box fallback active" "🟡 Box 双 DNS 部分异常；已交由 Box 回退"
  return 0
}

stop_dual() {
  rm -f "$AGH_DIR/dual.enabled"
  # Notify Box after each shutdown sequence so it removes redirects to dead
  # listeners.  Domestic falls back to system DNS, foreign to Mihomo :1053.
  stop_instance foreign
  stop_instance domestic
  notify_box_dns
  update_description "🔴 Box dual DNS stopped" "🔴 Box 双 DNS 已停止"
}

status_dual() {
  local name pid port
  for name in domestic foreign; do
    port="$(instance_port "$name")"
    if pid="$(instance_pid "$name" 2>/dev/null)" && is_port_listening "$port"; then
      echo "$name: up pid=$pid dns=$port"
    else
      echo "$name: down dns=$port"
    fi
  done
}

start_standalone() {
  local adg_pid
  if [ -f "$PID_FILE" ]; then
    adg_pid="$(cat "$PID_FILE" 2>/dev/null)"
    if [ -n "$adg_pid" ] && kill -0 "$adg_pid" 2>/dev/null; then
      log "AdGuardHome is already running" "AdGuardHome 已经在运行"
      return 0
    fi
  fi

  export SSL_CERT_DIR="/system/etc/security/cacerts/"
  export TZ="$timezone"
  [ -f "$AGH_DIR/bin.log" ] && mv "$AGH_DIR/bin.log" "$AGH_DIR/bin.log.bak"

  busybox setuidgid "$adg_user:$adg_group" "$BIN_DIR/AdGuardHome" \
    --no-check-update --config "$BIN_DIR/AdGuardHome.yaml" --work-dir "$BIN_DIR" \
    >"$AGH_DIR/bin.log" 2>&1 &
  adg_pid=$!
  echo "$adg_pid" > "$PID_FILE"

  if ! wait_for_port "$redir_port" "$adg_pid"; then
    log "DNS listener did not come up on :$redir_port" "DNS 监听端口 :$redir_port 未就绪"
    return 1
  fi

  if [ "$enable_iptables" = "true" ]; then
    "$SCRIPT_DIR/iptables.sh" enable || return 1
  fi
  update_description "🟢 Running [PID: $adg_pid]" "🟢 运行中 [PID: $adg_pid]"
}

stop_standalone() {
  "$SCRIPT_DIR/iptables.sh" disable >/dev/null 2>&1 || true
  if [ -f "$PID_FILE" ]; then
    local pid
    pid="$(cat "$PID_FILE" 2>/dev/null)"
    [ -n "$pid" ] && kill "$pid" >/dev/null 2>&1 || true
    [ -n "$pid" ] && kill -9 "$pid" >/dev/null 2>&1 || true
    rm -f "$PID_FILE"
  else
    pkill -f "$BIN_DIR/AdGuardHome" >/dev/null 2>&1 || true
  fi
  update_description "🔴 Stopped" "🔴 已停止"
}

case "$integration_mode" in
  box-dual)
    case "$1" in
      start) start_dual ;;
      stop) stop_dual ;;
      restart) stop_dual; start_dual ;;
      toggle)
        if instance_pid domestic >/dev/null 2>&1 || instance_pid foreign >/dev/null 2>&1; then
          stop_dual
        else
          start_dual
        fi
        ;;
      status) status_dual ;;
      start-domestic) start_instance domestic; notify_box_dns ;;
      stop-domestic) stop_instance domestic; notify_box_dns ;;
      restart-domestic) stop_instance domestic; start_instance domestic; notify_box_dns ;;
      start-foreign) start_instance foreign; notify_box_dns ;;
      stop-foreign) stop_instance foreign; notify_box_dns ;;
      restart-foreign) stop_instance foreign; start_instance foreign; notify_box_dns ;;
      *) echo "Usage: $0 {start|stop|restart|toggle|status|start-domestic|stop-domestic|restart-domestic|start-foreign|stop-foreign|restart-foreign}"; exit 1 ;;
    esac
    ;;
  *)
    case "$1" in
      start) start_standalone ;;
      stop) stop_standalone ;;
      restart) stop_standalone; start_standalone ;;
      toggle)
        if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE" 2>/dev/null)" 2>/dev/null; then
          stop_standalone
        else
          start_standalone
        fi
        ;;
      status)
        if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE" 2>/dev/null)" 2>/dev/null; then
          echo "standalone: up pid=$(cat "$PID_FILE") dns=$redir_port"
        else
          echo "standalone: down dns=$redir_port"
        fi
        ;;
      *) echo "Usage: $0 {start|stop|restart|toggle|status}"; exit 1 ;;
    esac
    ;;
esac
