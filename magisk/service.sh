#!/system/bin/sh
# =============================================================
#  AutoTask 增强模块（v0.0.3）
#  Root 守护进程：定时执行任务，支持自动解锁锁屏
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
#    click X Y                点击坐标 (X, Y)
#    swipe X1 Y1 X2 Y2 时长    滑动（时长单位毫秒）
#    longpress X Y 时长        长按
#    key 键码                 模拟按键（3=主页 4=返回 26=电源 24=音量+）
#    app 包名                 打开应用
#    url 链接                 打开链接
#    lock                     锁屏
#
#  【增强功能】自动解锁：设置下面这行，执行任务前自动输入 PIN 解锁
#    unlock_pin 你的锁屏密码
#
#  示例（去掉行首 # 启用）：
# 08:30:00 click 540 1200
# 12:00:00 app com.android.settings
# 22:30:00 lock
# ============================================================
EOF
  chmod 644 "$CONF"
fi

# ---------- 读取解锁密码 ----------
PIN=$(grep -E '^unlock_pin[[:space:]]' "$CONF" | head -1 | awk '{print $2}')

# ---------- 判断屏幕是否亮着 ----------
screen_on() {
  dumpsys power 2>/dev/null | grep -q "mWakefulness=Awake"
}

# ---------- 唤醒并尝试解锁 ----------
ensure_unlocked() {
  screen_on || input keyevent 224
  sleep 1
  # 无锁/滑动锁直接解除
  wm dismiss-keyguard 2>/dev/null
  # 若设置了 PIN，尝试输入密码解锁
  if [ -n "$PIN" ]; then
    input keyevent 82          # 调出密码输入框
    sleep 1
    input text "$PIN"
    sleep 1
    input keyevent 66          # 回车确认
    sleep 1
  fi
}

# ---------- 后台守护循环 ----------
(
  while true; do
    now="$(date +%H:%M:%S)"

    while IFS= read -r line || [ -n "$line" ]; do
      line="$(printf '%s' "$line" | tr -d '\r')"
      case "$line" in
        ''|'#'*) continue ;;
      esac

      set -- $line
      t="$1"; typ="$2"
      [ "$t" = "$now" ] || continue

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
      esac
    done < "$CONF"

    sleep 1
  done
) &
