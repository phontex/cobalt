#!/system/bin/sh
MODDIR=${0%/*}
exec "$MODDIR/bin/cobalt-platform" boot >> "$MODDIR/platform.log" 2>&1
