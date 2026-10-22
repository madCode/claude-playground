#!/bin/sh
# Does Carry's hourly session check survive a reboot? Installs the debug APK on the running
# emulator and opens Carry once, which schedules the check and runs its first period. Then it
# reboots and, without opening Carry again, waits for the check to be scheduled again, moves the
# clock past the hour and runs it.
#
# What brings the check back is WorkManager's own reschedule on BOOT_COMPLETED. The permissions it
# brings, and why FOREGROUND_SERVICE isn't one of them any more, are in docs/DESIGN.md.
#
# "Runs" is read from WorkManager's own record, the periods the work has completed, since a job
# forced before its period is due is only rescheduled, and WorkManager logs nothing at the
# default level. The debug build is debuggable, so run-as can read the database.
#
# Usage, from carry/ with one emulator attached: tools/reboot-check.sh app/build/outputs/apk/debug/app-debug.apk
set -eu

apk=$1
pkg=com.app.carry.debug
worker=com.app.carry.SessionWatchWorker
work=$(mktemp -d)

fail() { echo "reboot check: $*" >&2; exit 1; }

# JobScheduler's jobs for Carry's WorkManager, one per line, as the key dumpsys prints after
# "JOB": the namespace when there is one, the uid and the id. Android 16 prints
# "JOB androidx.work.systemjobscheduler:u0a220/1: e006984 @androidx.work...@<pkg>/androidx.work...";
# older versions "JOB #u0a220/1: e006984 <pkg>/androidx.work...".
jobs() {
  adb shell dumpsys jobscheduler | grep -E "^ *JOB [^ ]+: [0-9a-f]+ .*$pkg/androidx\.work" \
    | awk '{print $2}' | sed 's/^#//; s/:$//' | sort -u
}

# What dumpsys says about Carry, for a failure's log.
show_jobs() { adb shell dumpsys jobscheduler | grep -n "$pkg" | head -30 >&2; }

# Waits up to $1 seconds for a scheduled job; prints the keys.
await_jobs() {
  i=0
  while [ "$i" -lt "$1" ]; do
    keys=$(jobs)
    [ -n "$keys" ] && { echo "$keys"; return 0; }
    sleep 2; i=$((i + 2))
  done
  return 1
}

# How many periods of the session check WorkManager has recorded as done.
periods() {
  for f in androidx.work.workdb androidx.work.workdb-wal androidx.work.workdb-shm; do
    adb exec-out run-as "$pkg" cat "databases/$f" > "$work/$f" 2>/dev/null || rm -f "$work/$f"
  done
  python3 - "$work/androidx.work.workdb" "$worker" <<'PY'
import sqlite3, sys
db = sqlite3.connect(sys.argv[1])
row = db.execute("select period_count from WorkSpec where worker_class_name = ?", (sys.argv[2],)).fetchone()
print(row[0] if row else -1)
PY
}

# Waits up to $2 seconds for more than $1 periods done.
await_period() {
  i=0
  while [ "$i" -lt "$2" ]; do
    [ "$(periods)" -gt "$1" ] && return 0
    sleep 2; i=$((i + 2))
  done
  return 1
}

run() {
  for key in $1; do
    id=${key##*/}
    case ${key%/*} in
      *:*) adb shell cmd jobscheduler run -f -n "${key%%:*}" "$pkg" "$id" ;;
      *) adb shell cmd jobscheduler run -f "$pkg" "$id" ;;
    esac
  done
}

adb install -r -g "$apk" > /dev/null
adb shell am start -W -n "$pkg/com.app.carry.MainActivity" > /dev/null
before=$(await_jobs 60) || { show_jobs; fail "opening Carry scheduled no session check"; }
echo "Scheduled before the reboot: $before"
# The first period runs as soon as it is scheduled.
await_period 0 60 || { show_jobs; fail "the session check didn't run before the reboot"; }
done_before=$(periods)
echo "Periods done before the reboot: $done_before"
adb shell input keyevent KEYCODE_HOME

boot=$(adb shell cat /proc/sys/kernel/random/boot_id)
adb reboot
adb wait-for-device
i=0
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do
  sleep 2; i=$((i + 2)); [ "$i" -lt 300 ] || fail "the emulator didn't finish booting"
done
[ "$(adb shell cat /proc/sys/kernel/random/boot_id)" != "$boot" ] || fail "the emulator didn't reboot"
adb shell svc power stayon true

# Carry isn't opened again: only the boot can bring the check back.
after=$(await_jobs 180) || { show_jobs; fail "no session check scheduled after the reboot"; }
echo "Scheduled after the reboot: $after"

# Two hours on, so the next period is due, then the job as JobScheduler would start it.
adb root > /dev/null
adb wait-for-device
adb shell settings put global auto_time 0
adb shell date "@$(( $(adb shell date +%s | tr -d '\r') + 7200 ))" > /dev/null
sleep 5
due=$(await_jobs 60) || { show_jobs; fail "the session check wasn't scheduled after the clock moved"; }
run "$due"
await_period "$done_before" 90 || { show_jobs; fail "the session check didn't run after the reboot"; }
echo "Periods done after the reboot: $(periods)"
echo "The session check is scheduled after a reboot and runs."
