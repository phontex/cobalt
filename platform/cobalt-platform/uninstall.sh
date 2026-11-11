#!/system/bin/sh
ROOT=/data/adb/cobalt-platform
rm -f "$ROOT/status.json" "$ROOT/config.conf"
# Keep logs and backups for diagnosis; remove them only from the app's log screen.
