#!/usr/bin/env bash
# Walks the app on an emulator (CI, ui-shots.yml) and prints small screenshots into the log as
# base64 between markers, so the app's screens can be looked at without a phone.
set -u
PKG=io.github.emmanuelquintana.capri
APK=${1:-app/build/outputs/apk/debug/app-debug.apk}
mkdir -p shots
adb install -r "$APK" >/dev/null
adb shell settings put global window_animation_scale 1
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null
adb shell cmd locale set-app-locales $PKG --locales es-ES # as Emma sees it

# the view tree on screen (uiautomator is flaky on a busy emulator: a few tries)
dump() {
  rm -f /tmp/ui.xml
  for i in 1 2 3 4 5; do
    adb shell rm -f /sdcard/ui.xml >/dev/null 2>&1
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
    grep -q '<hierarchy' /tmp/ui.xml 2>/dev/null && return 0
    sleep 2
  done
  echo "dump failed"
}
# tap the first view whose text contains $1 (case-insensitive)
tap() {
  dump
  local xy
  xy=$(python3 - "$1" <<'PY'
import re, sys
want = sys.argv[1].lower()
xml = open('/tmp/ui.xml', encoding='utf-8').read()
for m in re.finditer(r'<node [^>]*?text="([^"]*)"[^>]*?content-desc="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
    if want in m.group(1).lower() or want in m.group(2).lower():
        x1, y1, x2, y2 = map(int, m.groups()[2:])
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
)
  if [ -n "$xy" ]; then adb shell input tap $xy; echo "tapped '$1' at $xy"; else echo "no '$1' on screen"; fi
}
shot() {
  sleep "${2:-2}"
  adb exec-out screencap -p > "shots/$1.png"
  convert "shots/$1.png" -resize 400x "shots/$1.small.png" 2>/dev/null || cp "shots/$1.png" "shots/$1.small.png"
  echo "===SHOT $1==="; base64 -w0 "shots/$1.small.png"; echo; echo "===END $1==="
}

adb shell am start -n $PKG/.MainActivity >/dev/null
shot 01-boot-early 2
shot 02-boot 7
tap "empezar"
shot 03-name 3
adb shell input text "Emma"
shot 04-name-typed 1
tap "siguiente"
shot 05-ai 3
tap "con una clave"
shot 06-ai-key 2
tap "ollama"
shot 07-ai-pc 2
tap "gratis"
tap "siguiente"
shot 08-perms 5
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
tap "siguiente"
shot 10-done 3
# before she starts (her page animates forever, and the hierarchy dump waits for stillness):
# where the pill bar's tabs are, from the main screen
adb shell appops set $PKG SYSTEM_ALERT_WINDOW deny
tap "iniciar"
shot 11-main 3
adb shell input swipe 500 1800 500 600 300
shot 12-main-scrolled 2
tap "permisos"
shot 13-perms-tab 2
tap "ajustes"
shot 14-settings-tab 2
tap "novedades"
adb shell appops set $PKG SYSTEM_ALERT_WINDOW allow
adb shell input swipe 500 600 500 1800 300
tap "iniciar capri"
shot 15-capri-on 14
adb shell input keyevent KEYCODE_HOME
shot 16-capri-home 6
# her chat (as "Talk to her" in her menu: a debug build lets adb ask her service), then typing
adb shell am start-foreground-service -n $PKG/.CapriService -a capri.CHAT
shot 17-chat 5
adb shell input text "hola"
shot 18-typing 3
adb shell dumpsys window windows | grep -E "Window #|mFrame=|mAttrs=.*ty=" | grep -B1 -A1 -iE "capri|InputMethod" | head -40
adb shell wm size; adb shell wm density
adb logcat -d -s capri capri-js AndroidRuntime | tail -60
