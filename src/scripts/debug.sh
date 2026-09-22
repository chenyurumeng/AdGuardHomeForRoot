#!/system/bin/sh

AGH_DIR="/data/adb/agh"
LOG="$AGH_DIR/debug.log"

{
  echo "==== AdGuardHome Debug Log ===="
  date
  echo

  echo "== System Info =="
  uname -a
  echo "Android Version: $(getprop ro.build.version.release)"
  echo "Device: $(getprop ro.product.model)"
  echo "Architecture: $(uname -m)"
  echo

  echo "== AdGuardHome Version =="
  if [ -f "$AGH_DIR/bin/AdGuardHome" ]; then
    "$AGH_DIR/bin/AdGuardHome" --version
  else
    echo "AdGuardHome binary not found"
  fi
  echo

  echo "== Root Method =="
  if [ -d "/data/adb/magisk" ]; then
    echo "Magisk"
  elif [ -d "/data/adb/ksu" ]; then
    echo "KernelSU"
  elif [ -d "/data/adb/ap" ]; then
    echo "APatch"
  else
    echo "Unknown"
  fi
  echo

  echo "== BusyBox Version =="
  [ -d "/data/adb/magisk" ] && export PATH="/data/adb/magisk:$PATH"
  [ -d "/data/adb/ksu/bin" ] && export PATH="/data/adb/ksu/bin:$PATH"
  [ -d "/data/adb/ap/bin" ] && export PATH="/data/adb/ap/bin:$PATH"
  if command -v busybox >/dev/null 2>&1; then
    busybox --version
  else
    echo "BusyBox not found"
  fi

  echo "== AGH Directory Listing =="
  ls -lR "$AGH_DIR"
  echo

  echo "== AGH Logs (last 30 lines each) =="
  echo "-- standalone --"
  tail -n 30 "$AGH_DIR/bin.log" 2>/dev/null
  echo "-- domestic --"
  tail -n 30 "$AGH_DIR/instances/domestic/agh.log" 2>/dev/null
  echo "-- foreign --"
  tail -n 30 "$AGH_DIR/instances/foreign/agh.log" 2>/dev/null
  echo

  echo "== AGH Settings =="
  cat "$AGH_DIR/settings.conf" 2>/dev/null
  echo

  echo "== AGH PID Files =="
  echo -n "standalone: "; cat "$AGH_DIR/bin/agh.pid" 2>/dev/null
  echo -n "domestic: "; cat "$AGH_DIR/instances/domestic/agh.pid" 2>/dev/null
  echo -n "foreign: "; cat "$AGH_DIR/instances/foreign/agh.pid" 2>/dev/null
  echo

  echo "== DNS Listener Ports =="
  grep -iE ':(15D7|15D8|041D) ' /proc/net/udp /proc/net/udp6 /proc/net/tcp /proc/net/tcp6 2>/dev/null || true
  echo

  echo "== Running Processes (AdGuardHome) =="
  ps -A | grep AdGuardHome
  echo

  echo "== iptables -t nat -L -n -v =="
  iptables -t nat -L -n -v
  echo

  echo "== ip6tables -t nat -L -n -v =="
  ip6tables -t nat -L -n -v
  echo

  echo "== Box DNS Chains =="
  iptables -t nat -nvL NAT_DNS_HIJACK --line-numbers 2>/dev/null || true
  iptables -t nat -nvL NAT_DNS_HIJACK_NEXT --line-numbers 2>/dev/null || true
  ip6tables -t filter -nvL BOX_DNS6_REJECT --line-numbers 2>/dev/null || true
  echo

  echo "== Network Interfaces =="
  ip addr
  echo

} >"$LOG" 2>&1

echo "Debug info collected in $LOG"
