until [ "$(getprop init.svc.bootanim)" = "stopped" ]; do
  sleep 12
done

/data/adb/agh/scripts/tool.sh start

if grep -q '^integration_mode=box-dual$' /data/adb/agh/settings.conf 2>/dev/null; then
  if [ -f /data/adb/agh/watchdog.pid ]; then
    old_pid="$(cat /data/adb/agh/watchdog.pid 2>/dev/null)"
    [ -n "$old_pid" ] && kill "$old_pid" >/dev/null 2>&1 || true
  fi
  nohup /data/adb/agh/scripts/watchdog.sh >/dev/null 2>&1 &
fi

inotifyd /data/adb/agh/scripts/inotify.sh /data/adb/modules/AdGuardHome:d,n &
