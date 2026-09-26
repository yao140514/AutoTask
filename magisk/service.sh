#!/system/bin/sh
# =============================================================
#  AutoTask 增强模块（v0.1.0）
#  Root 守护进程：定时执行任务，支持自动解锁锁屏及多种动作
# =============================================================

MODDIR=${0%/*}
CONF="$MODDIR/tasks.conf"
LOG="$MODDIR/autotask.log"

# ---------- 首次运行：生成配置模板 ----------
if [ ! -f "$CONF" ]; then
  cat > "$CONF" <<'EOF'
# ============================================================
#  AutoTask 增强模块配置（修改后自动生效，无需重启）
#  每行一个任务：时间 类型 参数
#  时间格式：HH:MM:SS（24 小时制）
#
#  任务类型：
#    click X Y                点击坐标
#    swipe X1 Y1 X2 Y2 时长    滑动（毫秒）
#    longpress X Y 时长        长按（毫秒）
#    key 键码                 模拟按键（3=主页 4=返回 26=电源 24=音量+）
#    app 包名                 打开应用
#    url 链接                 打开链接
#    lock                     锁屏
#    notify 标题|内容          发送通知（| 分隔标题和内容）
#    shell 命令               执行 shell 命令
#    volume 通道 0-100        调整音量（通道: media/ring/notification/alarm）
#    delay 秒                 延时（可小数，如 0.5）
#    randdelay 最小 最大       随机延时（秒）
#    http URL                 发送 HTTP GET 请求（仅 http://，https 不支持）
#
#  【增强功能】自动解锁：设置下面这行，执行任务前自动输入 PIN 解锁
#    unlock_pin 你的锁屏密码
#
#  示例（去掉行首 # 启用）：
# 08:30:00 click 540 1200
# 09:00:00 notify 打卡提醒|该打卡了
# 12:00:00 app com.android.settings
# 22:30:00 lock
# ============================================================
EOF
  chmod 644 "$CONF"
fi

# ---------- 读取解锁密码 ----------
PIN=$(grep -E '^unlock_pin[[:space:]]' "$CONF" | head -1 | awk '{print $2}')

# ---------- 工具函数 ----------
screen_on() {
  dumpsys power 2>/dev/null | grep -q "mWakefulness=Awake"
}

ensure_unlocked() {
  screen_on || input keyevent 224
  sleep 1
  wm dismiss-keyguard 2>/dev/null
  if [ -n "$PIN" ]; then
    input keyevent 82
    sleep 1
    input text "$PIN"
    sleep 1
    input keyevent 66
    sleep 1
  fi
}

# 音量通道名转 stream 编号
volcode() {
  case "$1" in
    media) echo 3 ;;
    ring) echo 2 ;;
    notification|notify) echo 5 ;;
    alarm) echo 4 ;;
    *) echo "$1" ;;
  esac
}

# ---------- 后台守护循环 ----------
(
  while true; do
    now="$(date +%H:%M:%S)"

    while IFS= read -r line || [ -n "$line" ]; do
      line="$(printf '%s' "$line" | tr -d '\r')"
      case "$line" in ''|'#'*) continue ;; esac

      set -- $line
      t="$1"; typ="$2"
      [ "$t" = "$now" ] || continue

      # 时间、类型之后的剩余部分（保留内部空格）
      rest="$(printf '%s' "$line" | sed 's/^[^ ]* [^ ]* //')"

      case "$typ" in
        click)
          ensure_unlocked
          input tap "$3" "$4"
          echo "[$now] click $3 $4" >> "$LOG"
          ;;
        swipe)
          ensure_unlocked
          input swipe "$3" "$4" "$5" "$6" "$7"
          echo "[$now] swipe $3,$4 -> $5,$6" >> "$LOG"
          ;;
        longpress)
          ensure_unlocked
          input swipe "$3" "$4" "$3" "$4" "$5"
          echo "[$now] longpress $3 $4" >> "$LOG"
          ;;
        key)
          ensure_unlocked
          input keyevent "$3"
          echo "[$now] key $3" >> "$LOG"
          ;;
        app)
          ensure_unlocked
          monkey -p "$3" -c android.intent.category.LAUNCHER 1
          echo "[$now] app $3" >> "$LOG"
          ;;
        url)
          ensure_unlocked
          am start -a android.intent.action.VIEW -d "$3"
          echo "[$now] url $3" >> "$LOG"
          ;;
        lock)
          screen_on && input keyevent 26
          echo "[$now] lock" >> "$LOG"
          ;;
        notify)
          title=$(printf '%s' "$rest" | cut -d'|' -f1)
          body=$(printf '%s' "$rest" | cut -d'|' -f2)
          cmd notification post -t "$title" "autotask" "$body" 2>/dev/null
          echo "[$now] notify $title" >> "$LOG"
          ;;
        shell)
          sh -c "$rest" 2>>"$LOG"
          echo "[$now] shell $rest" >> "$LOG"
          ;;
        volume)
          stream=$(volcode "$3")
          idx=$(( ${4:-50} * 15 / 100 ))
          cmd media_session volume --set "$idx" --stream "$stream" 2>/dev/null
          echo "[$now] volume $stream ${4:-50}" >> "$LOG"
          ;;
        delay)
          sleep "${3:-1}"
          echo "[$now] delay ${3:-1}" >> "$LOG"
          ;;
        randdelay)
          lo=${3:-1}; hi=${4:-3}
          r=$( (od -An -N2 -tu2 /dev/urandom 2>/dev/null || echo 0) | tr -d ' ' )
          [ -n "$r" ] && [ "$r" -ge 0 ] 2>/dev/null || r=0
          r=$(( r % (hi - lo + 1) + lo ))
          sleep "$r"
          echo "[$now] randdelay $r" >> "$LOG"
          ;;
        http)
          url="$3"
          case "$url" in
            https://*)
              echo "[$now] http 跳过(https 需 curl，本机无): $url" >> "$LOG"
              ;;
            *)
              rest=$(printf '%s' "$url" | sed 's|^http://||')
              host=$(printf '%s' "$rest" | cut -d'/' -f1)
              path=$(printf '%s' "$rest" | cut -d'/' -f2-)
              port=80
              case "$host" in *:*) port=${host##*:}; host=${host%:*};; esac
              printf 'GET /%s HTTP/1.0\r\nHost: %s\r\nUser-Agent: AutoTask\r\n\r\n' "$path" "$host" | nc -w 5 "$host" "$port" >/dev/null 2>&1
              echo "[$now] http $url" >> "$LOG"
              ;;
          esac
          ;;
      esac
    done < "$CONF"

    sleep 1
  done
) &
