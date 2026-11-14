#!/system/bin/sh
# Removes every bind mount this module may have created, for all known Cobalt targets.
for PKG in com.heytap.themestore com.oplus.themestore; do
  BASE=$(pm path "$PKG" 2>/dev/null | head -n 1 | cut -d: -f2)
  if [ -n "$BASE" ]; then
    umount -l "$BASE" 2>/dev/null
    am force-stop "$PKG" 2>/dev/null
  fi
  rm -f "/data/adb/cobalt-platform/records/$PKG.json" 2>/dev/null
done
