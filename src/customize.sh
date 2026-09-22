SKIPUNZIP=1

# most of the users are Chinese, so set default language to Chinese
language="zh"

# try to get the system language
locale=$(getprop persist.sys.locale || getprop ro.product.locale || getprop persist.sys.language)

# if the system language is English, set language to English
if echo "$locale" | grep -qi "en"; then
  language="en"
fi

function info() {
  [ "$language" = "en" ] && ui_print "$1" || ui_print "$2"
}

function error() {
  [ "$language" = "en" ] && abort "$1" || abort "$2"
}

info "- 🚀 Installing AdGuardHome for $ARCH" "- 🚀 开始安装 AdGuardHome for $ARCH"

AGH_DIR="/data/adb/agh"
BIN_DIR="$AGH_DIR/bin"
SCRIPT_DIR="$AGH_DIR/scripts"
PID_FILE="$AGH_DIR/bin/agh.pid"

info "- 📦 Extracting module basic files..." "- 📦 解压模块基本文件..."
unzip -o "$ZIPFILE" "action.sh" -d "$MODPATH" >/dev/null 2>&1 
unzip -o "$ZIPFILE" "module.prop" -d "$MODPATH" >/dev/null 2>&1
unzip -o "$ZIPFILE" "service.sh" -d "$MODPATH" >/dev/null 2>&1
unzip -o "$ZIPFILE" "uninstall.sh" -d "$MODPATH" >/dev/null 2>&1
unzip -o "$ZIPFILE" "webroot/*" -d "$MODPATH" >/dev/null 2>&1

extract_keep_config() {
  info "- 🌈 Keeping old configuration files..." "- 🌈 保留原来的配置文件..."
  info "- 📜 Extracting script files..." "- 📜 正在解压脚本文件..."
  unzip -o "$ZIPFILE" "scripts/*" -d $AGH_DIR >/dev/null 2>&1 || {
    error "- ❌ Failed to extract scripts!" "- ❌ 解压脚本文件失败！"
  }
  # Install any new dual-instance files but never overwrite user instance configs.
  unzip -o "$ZIPFILE" "instances/*" -x "instances/domestic/AdGuardHome.yaml" "instances/foreign/AdGuardHome.yaml" -d $AGH_DIR >/dev/null 2>&1 || true
  for inst in domestic foreign; do
    if [ ! -f "$AGH_DIR/instances/$inst/AdGuardHome.yaml" ]; then
      unzip -o "$ZIPFILE" "instances/$inst/AdGuardHome.yaml" -d $AGH_DIR >/dev/null 2>&1 || true
    fi
  done
  info "- 🛠️ Extracting binary files except configuration..." "- 🛠️ 正在解压二进制文件（不包括配置文件）..."
  unzip -o "$ZIPFILE" "bin/*" -x "bin/AdGuardHome.yaml" -d $AGH_DIR >/dev/null 2>&1 || {
    error "- ❌ Failed to extract binary files!" "- ❌ 解压二进制文件失败！"
  }
  info "- 🚫 Skipping configuration file extraction..." "- 🚫 跳过解压配置文件..."
}

extract_no_config() {
  info "- 💾 Backing up old configuration files with .bak extension..." "- 💾 使用 .bak 扩展名备份旧配置文件..."
  [ -f "$AGH_DIR/settings.conf" ] && mv "$AGH_DIR/settings.conf" "$AGH_DIR/settings.conf.bak"
  [ -f "$AGH_DIR/bin/AdGuardHome.yaml" ] && mv "$AGH_DIR/bin/AdGuardHome.yaml" "$AGH_DIR/bin/AdGuardHome.yaml.bak"
  # Dual-instance configs are user data too. Back them up with a timestamp
  # before replacing templates so a reinstall/test cannot destroy a working set.
  local stamp inst cfg
  stamp=$(date '+%Y%m%d%H%M%S')
  for inst in domestic foreign; do
    cfg="$AGH_DIR/instances/$inst/AdGuardHome.yaml"
    [ -f "$cfg" ] && mv "$cfg" "$cfg.bak.$stamp"
  done
  extract_all
}

extract_all() {
  info "- 🌟 Extracting script files..." "- 🌟 正在解压脚本文件..."
  unzip -o "$ZIPFILE" "scripts/*" -d $AGH_DIR >/dev/null 2>&1 || {
    error "- ❌ Failed to extract scripts" "- ❌ 解压脚本文件失败"
  }
  info "- 🌐 Extracting dual-instance templates..." "- 🌐 正在解压双实例模板..."
  unzip -o "$ZIPFILE" "instances/*" -d $AGH_DIR >/dev/null 2>&1 || true
  info "- 🛠️ Extracting binary files..." "- 🛠️ 正在解压二进制文件..."
  unzip -o "$ZIPFILE" "bin/*" -d $AGH_DIR >/dev/null 2>&1 || {
    error "- ❌ Failed to extract binary files" "- ❌ 解压二进制文件失败"
  }
  info "- 📜 Extracting configuration files..." "- 📜 正在解压配置文件..."
  unzip -o "$ZIPFILE" "settings.conf" -d $AGH_DIR >/dev/null 2>&1 || {
    error "- ❌ Failed to extract configuration files" "- ❌ 解压配置文件失败"
  }
}

if [ -d "$AGH_DIR" ]; then
  info "- ⏹️ Found old version, stopping DNS redirection before core processes..." "- ⏹️ 发现旧版模块，先撤销 DNS 重定向再停止核心..."
  if [ -x "$AGH_DIR/scripts/tool.sh" ]; then
    "$AGH_DIR/scripts/tool.sh" stop >/dev/null 2>&1 || true
  fi
  pkill -f "AdGuardHome" || pkill -9 -f "AdGuardHome" 
  info "- 🔄 Do you want to keep the old configuration? (If not, it will be automatically backed up)" "- 🔄 是否保留原来的配置文件？（若不保留则自动备份）"
  info "- 🔊 (Volume Up = Yes, Volume Down = No, 30s no input = Yes)" "- 🔊 （音量上键 = 是, 音量下键 = 否，30秒无操作 = 是）"
  START_TIME=$(date +%s)
  while true; do
    NOW_TIME=$(date +%s)
    timeout 1 getevent -lc 1 2>&1 | grep KEY_VOLUME >"$TMPDIR/events"
    if [ $((NOW_TIME - START_TIME)) -gt 29 ]; then
      info "- ⏰ No input detected after 30 seconds, defaulting to keep old configuration." "- ⏰ 30秒无输入，默认保留原配置。"
      extract_keep_config
      break
    elif $(cat $TMPDIR/events | grep -q KEY_VOLUMEUP); then
      extract_keep_config
      break
    elif $(cat $TMPDIR/events | grep -q KEY_VOLUMEDOWN); then
      extract_no_config
      break
    fi
  done
else
  info "- 📦 First time installation, extracting files..." "- 📦 第一次安装，正在解压文件..."
  mkdir -p "$AGH_DIR" "$BIN_DIR" "$SCRIPT_DIR"
  extract_all
fi

# Normalize dual-stack DNS settings for preserved dual-instance configs.
# AGH bootstrap_dns is plain DNS only; keep both IPv4 and IPv6 bootstrap paths.
migrate_dualstack_dns_config() {
  local cfg="$1"
  local profile="$2"
  local tmp stamp

  [ -f "$cfg" ] || return 0

  stamp=$(date '+%Y%m%d%H%M%S')
  cp -p "$cfg" "$cfg.bak.dualstack.$stamp" >/dev/null 2>&1 || true
  tmp="$cfg.dualstack.tmp"

  busybox awk -v profile="$profile" '
    function emit_bind_hosts() {
      print "  bind_hosts:"
      print "    - 127.0.0.1"
      print "    - ::1"
    }
    function emit_bootstrap() {
      print "  bootstrap_dns:"
      if (profile == "domestic") {
        print "    - 223.5.5.5"
        print "    - 223.6.6.6"
        print "    - \"2400:3200::1\""
        print "    - \"2400:3200:baba::1\""
      } else {
        print "    - 1.1.1.1"
        print "    - 1.0.0.1"
        print "    - \"2606:4700:4700::1111\""
        print "    - \"2606:4700:4700::1001\""
        print "    - 8.8.8.8"
        print "    - 8.8.4.4"
        print "    - \"2001:4860:4860::8888\""
        print "    - \"2001:4860:4860::8844\""
      }
    }
    BEGIN { skip = "" }
    {
      if ($0 ~ /^  bind_hosts:[[:space:]]*$/) {
        emit_bind_hosts()
        skip = "bind"
        next
      }
      if ($0 ~ /^  bootstrap_dns:[[:space:]]*$/) {
        emit_bootstrap()
        skip = "bootstrap"
        next
      }
      if (skip != "") {
        if ($0 ~ /^  [A-Za-z0-9_]+:/) {
          skip = ""
        } else {
          next
        }
      }
      if ($0 ~ /^  aaaa_disabled:/) {
        print "  aaaa_disabled: false"
        next
      }
      if ($0 ~ /^  use_dns64:/) {
        print "  use_dns64: false"
        next
      }
      print
    }
  ' "$cfg" > "$tmp" && mv "$tmp" "$cfg"
}

for inst in domestic foreign; do
  migrate_dualstack_dns_config "$AGH_DIR/instances/$inst/AdGuardHome.yaml" "$inst"
done

# Migrate keys added by the Box dual-DNS fork when an older settings.conf was kept.
if [ -f "$AGH_DIR/settings.conf" ]; then
  grep -q '^integration_mode=' "$AGH_DIR/settings.conf" || echo 'integration_mode=box-dual' >> "$AGH_DIR/settings.conf"
  grep -q '^domestic_enabled=' "$AGH_DIR/settings.conf" || echo 'domestic_enabled=true' >> "$AGH_DIR/settings.conf"
  grep -q '^domestic_dns_port=' "$AGH_DIR/settings.conf" || echo 'domestic_dns_port=5591' >> "$AGH_DIR/settings.conf"
  grep -q '^domestic_web_port=' "$AGH_DIR/settings.conf" || echo 'domestic_web_port=3000' >> "$AGH_DIR/settings.conf"
  grep -q '^foreign_enabled=' "$AGH_DIR/settings.conf" || echo 'foreign_enabled=true' >> "$AGH_DIR/settings.conf"
  grep -q '^foreign_dns_port=' "$AGH_DIR/settings.conf" || echo 'foreign_dns_port=5592' >> "$AGH_DIR/settings.conf"
  grep -q '^foreign_web_port=' "$AGH_DIR/settings.conf" || echo 'foreign_web_port=3001' >> "$AGH_DIR/settings.conf"
  grep -q '^readonly INSTANCE_DIR=' "$AGH_DIR/settings.conf" || echo 'readonly INSTANCE_DIR="$AGH_DIR/instances"' >> "$AGH_DIR/settings.conf"
fi

info "- 🔐 Setting permissions..." "- 🔐 设置权限..."

chmod +x "$BIN_DIR/AdGuardHome"
chown root:net_raw "$BIN_DIR/AdGuardHome"

chmod +x "$SCRIPT_DIR"/*.sh "$MODPATH"/*.sh

info "- 🎉 Installation completed, please reboot." "- 🎉 安装完成，请重启设备。"
