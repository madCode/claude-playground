#!/bin/sh
# Does Carry's hourly session check survive a reboot? Installs the debug APK on the running
# emulator, opens Carry once (which schedules the check), reboots, and then, without opening Carry
# again, waits for the check to be scheduled again and makes it run.
#
# What brings the check back is WorkManager's own reschedule on BOOT_COMPLETED. The permissions it
# brings, and whether any can be removed, are in docs/DESIGN.md; this is the proof one asks for.
#
# Usage, from carry/ with one emulator attached: tools/reboot-check.sh app/build/outputs/apk/debug/app-debug.apk
set -eu

apk=$1
pkg=com.app.carry.debug
worker=com.app.carry.SessionWatchWorker

fail() { echo "reboot check: $*" >&2; exit 1; }

# JobScheduler's jobs for Carry's WorkManager, one per line, as the key dumpsys prints after
# "JOB #": the uid, the namespace when there is one, and the id ("u0a190/ns/3" or "u0a190/3").
jobs() {
  adb shell dumpsys jobscheduler | grep 'JOB #' | grep "$pkg/androidx.work" \
    | sed 's/.*JOB #\([^:]*\):.*/\1/' | sort -u
}

# What dumpsys says about Carry, for a failure's log.
show_jobs() { adb shell dumpsys jobscheduler | grep -n "$pkg" | head -20 >&2; }

# Waits up to $1 seconds for a scheduled job; prints its ids.
await_jobs() {
  i=0
  while [ "$i" -lt "$1" ]; do
    ids=$(jobs)
    [ -n "$ids" ] && { echo "$ids"; return 0; }
    sleep 2; i=$((i + 2))
  done
  return 1
}

# Runs each job now and waits up to 60 seconds for WorkManager to log the worker succeeding.
run_and_see() {
  adb logcat -c
  for key in $1; do
    id=${key##*/}; rest=${key%/*}; ns=${rest#*/}
    if [ "$ns" != "$rest" ]; then
      adb shell cmd jobscheduler run -f -n "$ns" "$pkg" "$id"
    else
      adb shell cmd jobscheduler run -f "$pkg" "$id"
    fi
  done
  i=0
  while [ "$i" -lt 60 ]; do
    adb logcat -d -s WM-WorkerWrapper:I | grep "Worker result SUCCESS" | grep -q "$worker" && return 0
    sleep 2; i=$((i + 2))
  done
  return 1
}

adb install -r -g "$apk" > /dev/null
adb shell am start -W -n "$pkg/com.app.carry.MainActivity" > /dev/null
before=$(await_jobs 60) || { show_jobs; fail "opening Carry scheduled no session check"; }
echo "Scheduled before the reboot: job $before"
run_and_see "$before" || { show_jobs; fail "the session check didn't run before the reboot"; }
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
echo "Scheduled after the reboot: job $after"
run_and_see "$after" || { show_jobs; fail "the session check didn't run after the reboot"; }
echo "The session check is scheduled after a reboot and runs."
