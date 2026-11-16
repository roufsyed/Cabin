#!/bin/sh
# Collects from the head unit what Cabin still has to learn about it:
#   1. which Android it really runs, its screen, and which vendor software it carries;
#   2. what changes on the unit when the headlights are switched on (for Auto brightness);
#   3. what each steering wheel button sends.
#
# It only reads. Nothing on the unit is installed, changed or cleared.
#
# usage: tools/unit-report.sh
#   Connect the unit over adb first. With several devices attached, name one:
#     ANDROID_SERIAL=<serial> tools/unit-report.sh
#   For part 3, have Cabin's debug build installed and on screen.
#
# Everything is written to dist/unit-report/, which git ignores. summary.txt is the file to pass on;
# it leaves out serial numbers, addresses and the apps you installed yourself.

set -u

ADB="${ADB:-adb}"
command -v "$ADB" >/dev/null 2>&1 || ADB="$HOME/Library/Android/sdk/platform-tools/adb"
command -v "$ADB" >/dev/null 2>&1 || { echo "adb was not found. Install the Android platform tools first."; exit 1; }

if [ -z "${ANDROID_SERIAL:-}" ]; then
    count=$("$ADB" devices | awk 'NR > 1 && $2 == "device"' | wc -l | tr -d ' ')
    if [ "$count" != "1" ]; then
        echo "Expected one device over adb but found $count:"
        "$ADB" devices
        echo "Connect the unit, or run again as: ANDROID_SERIAL=<serial> $0"
        exit 1
    fi
fi

root=$(cd "$(dirname "$0")/.." && pwd)
out="$root/dist/unit-report"
mkdir -p "$out"
rm -f "$out"/*.txt
summary="$out/summary.txt"

sh_() { "$ADB" shell "$@" 2>/dev/null | tr -d '\r'; }
say() { printf '%s\n' "$*" | tee -a "$summary"; }
note() { printf '%s\n' "$*" >> "$summary"; }
ask() { printf '\n>>> %s\n    Press Enter when done. ' "$*"; read -r _answer; }

# Lines that could identify the unit or its owner are never written to any file.
private='serial|imei|meid|iccid|android_id|bluetooth_address|bluetooth_name|device_name|ssid|macaddr|mac_addr|\.mac|wifi\.interface|gsm\.|persist\.sys\.timezone|account|ro\.boot\.|adb_keys|ssaid'

: > "$summary"
say "Cabin unit report, $(date '+%Y-%m-%d %H:%M')"

# ---------------------------------------------------------------- 1. The unit
say ""
say "== 1. The unit"
for prop in ro.build.version.release ro.build.version.sdk ro.build.version.security_patch \
    ro.product.manufacturer ro.product.brand \
    ro.product.model ro.product.device ro.product.board ro.board.platform ro.hardware \
    ro.build.display.id ro.build.type ro.build.date; do
    say "$prop = $(sh_ getprop "$prop")"
done
say "screen: $(sh_ wm size | tr '\n' ' ') $(sh_ wm density | tr '\n' ' ')"
say "memory: $(sh_ cat /proc/meminfo | grep MemTotal | tr -s ' ')"
say "kernel: $(sh_ uname -r)"
say "processor:"
sh_ cat /proc/cpuinfo | grep -E "Hardware|CPU part|model name|Processor" | sort | uniq -c | sed 's/^/    /' | tee -a "$summary"
say "light sensor: $(sh_ dumpsys sensorservice | grep -i -m 3 'light' | tr '\n' ';')"
say "car features: $(sh_ pm list features | grep -i -E 'automotive|car' | tr '\n' ' ')"
split=$(sh_ cmd activity supports-split-screen-multi-window | grep -E -m 1 '^(true|false)$')
say "split screen supported: ${split:-not reported}"
# The side bar over other apps is switched on in the unit's Accessibility settings.
access=$(sh_ cmd package resolve-activity --brief -a android.settings.ACCESSIBILITY_SETTINGS | tail -n 1)
say "accessibility settings screen: ${access:-not reported}"
# What the tray's switches hand over to, where Android does not let Cabin switch for itself: the
# Wi-Fi panel, the hotspot page, and the question "turn Bluetooth off?".
for action in android.settings.panel.action.WIFI android.settings.TETHER_SETTINGS \
    android.bluetooth.adapter.action.REQUEST_DISABLE; do
    screen=$(sh_ cmd package resolve-activity --brief -a "$action" | tail -n 1)
    say "screen for $action: ${screen:-not reported}"
done
services=$(sh_ settings get secure enabled_accessibility_services)
case "$services" in
    *com.roufsyed.cabin/*) say "Cabin's side bar: switched on" ;;
    *) say "Cabin's side bar: not switched on" ;;
esac
say "night mode now: $(sh_ dumpsys uimode | grep -o -E 'mNightMode=[0-9]+ \([a-z]+\)|mComputedNightMode=[a-z]+' | tr '\n' ' ')"
say "brightness now: $(sh_ settings get system screen_brightness) (mode $(sh_ settings get system screen_brightness_mode))"

# Only the software the unit shipped with: it names the vendor's platform and its stock apps.
sh_ pm list packages -s | sed 's/^package://' | sort > "$out/system-packages.txt"
say "system packages: $(wc -l < "$out/system-packages.txt" | tr -d ' ') (listed in system-packages.txt)"
say "packages that are not Android's or Google's own:"
grep -v -E '^(android|com\.android\.|com\.google\.|com\.qualcomm\.|org\.chromium\.|com\.mediatek\.)' "$out/system-packages.txt" \
    | sed 's/^/    /' | tee -a "$summary"

say ""
say "media sessions right now (start the radio or some music first to see whether the stock apps publish one):"
sh_ dumpsys media_session | grep -E '^ +(package=|active=|state=PlaybackState|metadata: size)' \
    | sed -E 's/, position=[0-9]+.*//; s/^ +/    /' | tee -a "$summary"

# ---------------------------------------------------------------- 2. Headlights
snapshot() {
    {
        for table in system secure global; do
            sh_ settings list "$table" | sed "s/^/settings $table: /"
        done
        sh_ getprop | sed 's/^/prop: /'
        sh_ dumpsys uimode | grep -o -E 'mNightMode=[0-9]+ \([a-z]+\)|mComputedNightMode=[a-z]+|mCarModeEnabled=[a-z]+|mCurUiMode=0x[0-9a-f]+' | sed 's/^/uimode: /'
        sh_ dumpsys power | grep -i -E 'mScreenBrightness|mWakefulness=' | tr -s ' ' | sed 's/^/power:/'
        sh_ dumpsys display | grep -i -E 'mScreenBrightness|mBrightness=|mActualBacklight|mAppliedBrightness' | tr -s ' ' | sed 's/^/display:/'
        for file in /sys/class/backlight/*/brightness /sys/class/leds/lcd-backlight/brightness; do
            value=$(sh_ cat "$file")
            [ -n "$value" ] && echo "file $file: $value"
        done
    } | grep -v -i -E "$private" | sort > "$out/$1"
}

# What the unit announced, newest first: the names of its recent broadcasts.
broadcasts() {
    sh_ dumpsys activity broadcasts history | grep -o -E 'act=[A-Za-z0-9_.]+' | sed 's/^act=//' \
        | grep -v -E '^android\.intent\.action\.(TIME_TICK|BATTERY_CHANGED|SIG_STR|CLOSE_SYSTEM_DIALOGS)$' \
        | awk '!seen[$0]++' | head -40 > "$out/$1"
}

watch_log() {
    : > "$out/$1"
    "$ADB" logcat -v time -T 1 >> "$out/$1" 2>/dev/null &
    log_pid=$!
}

stop_log() {
    kill "$log_pid" 2>/dev/null
    wait "$log_pid" 2>/dev/null
}

changes() {
    # Lines only in the second file, with what the first one had for the same name.
    diff "$out/$1" "$out/$2" | grep -E '^[<>]' | sed 's/^</    was:/; s/^>/    now:/'
}

say ""
say "== 2. Headlights"
ask "Start the engine or switch the ignition on, with the headlights OFF."
snapshot lights-off.txt
broadcasts broadcasts-before.txt
watch_log lights-on-log.txt
ask "Switch the headlights ON and wait about five seconds."
stop_log
snapshot lights-on.txt
broadcasts broadcasts-on.txt
watch_log lights-off-log.txt
ask "Switch the headlights OFF again and wait about five seconds."
stop_log
snapshot lights-off-again.txt
broadcasts broadcasts-off.txt

say "what changed when the headlights went ON:"
changes lights-off.txt lights-on.txt | tee -a "$summary"
say "what changed when they went OFF again:"
changes lights-on.txt lights-off-again.txt | tee -a "$summary"
say "broadcasts the unit sent while they were ON that it had not sent before:"
grep -v -x -F -f "$out/broadcasts-before.txt" "$out/broadcasts-on.txt" | sed 's/^/    /' | tee -a "$summary"
say "and after they went OFF:"
grep -v -x -F -f "$out/broadcasts-on.txt" "$out/broadcasts-off.txt" | sed 's/^/    /' | tee -a "$summary"
words='light|lamp|illum|backlight|bright|night|uimode|headl|dimm|mcu'
# Lines that merely print the whole screen configuration mention "night" too; they say nothing new.
lights_log() {
    grep -i -E "$words" "$out/$1" | grep -v -i -E "$private" | grep -v -E 'sw[0-9]+dp' \
        | cut -c1-220 | head -40 | sed 's/^/    /' | tee -a "$summary"
}
say "log lines about lights while switching ON (up to 40):"
lights_log lights-on-log.txt
say "log lines about lights while switching OFF (up to 40):"
lights_log lights-off-log.txt
# The full logs can hold anything other apps wrote, so only the filtered lines above are kept.
rm -f "$out/lights-on-log.txt" "$out/lights-off-log.txt"

# ---------------------------------------------------------------- 3. Steering wheel buttons
say ""
say "== 3. Steering wheel buttons"
: > "$out/keys-raw.txt"
: > "$out/keys-cabin.txt"
"$ADB" shell getevent -lt >> "$out/keys-raw.txt" 2>&1 &
keys_pid=$!
"$ADB" logcat -v time -T 1 -s CabinKeys >> "$out/keys-cabin.txt" 2>/dev/null &
cabin_pid=$!
broadcasts broadcasts-before-keys.txt
ask "With Cabin on screen, press each steering wheel button once, slowly, in this order: volume up, volume down, next, previous, mode, then any others (phone, voice, mute)."
kill "$keys_pid" "$cabin_pid" 2>/dev/null
wait "$keys_pid" "$cabin_pid" 2>/dev/null
broadcasts broadcasts-keys.txt

say "what reached Cabin (needs its debug build on screen), in the order pressed:"
grep 'CabinKeys' "$out/keys-cabin.txt" | sed -E 's/.*CabinKeys[^:]*: */    /' | tee -a "$summary"
say "key presses the unit's own input devices reported, in the order pressed:"
grep -E 'EV_KEY' "$out/keys-raw.txt" | grep -E 'DOWN' | sed -E 's/^\[ *[0-9.]+\] *//; s/^/    /' | cut -c1-120 | tee -a "$summary"
say "input devices:"
grep -E '^add device|name:' "$out/keys-raw.txt" | paste - - | sed -E 's/add device [0-9]+: //; s/ +name: +/  /; s/^/    /' | tee -a "$summary"
say "broadcasts the unit sent while the buttons were pressed that it had not sent before:"
grep -v -x -F -f "$out/broadcasts-before-keys.txt" "$out/broadcasts-keys.txt" | sed 's/^/    /' | tee -a "$summary"

printf '\nDone. The report is in %s\nsummary.txt is the file to pass on.\n' "$out"
