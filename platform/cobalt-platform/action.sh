#!/system/bin/sh
MODDIR=${0%/*}
if [ "$#" -eq 0 ]; then
  STATUS=$("$MODDIR/bin/cobalt-platform" status 2>/dev/null) || STATUS='{}'
  VERSION=$(printf '%s' "$STATUS" | sed -n 's/.*"module_version":"\([^"]*\)".*/\1/p')
  ROOT=$(printf '%s' "$STATUS" | sed -n 's/.*"root":\([^,}]*\).*/\1/p')
  LSPOSED=$(printf '%s' "$STATUS" | sed -n 's/.*"lsposed":\([^,}]*\).*/\1/p')
  ENABLED=$(printf '%s' "$STATUS" | sed -n 's/.*"enabled":\([^,}]*\).*/\1/p')
  [ -n "$VERSION" ] || VERSION='unknown'
  [ "$ROOT" = '1' ] || [ "$ROOT" = 'true' ] && ROOT_TEXT='connected' || ROOT_TEXT='not found'
  [ "$LSPOSED" = '1' ] || [ "$LSPOSED" = 'true' ] && LSPOSED_TEXT='active' || LSPOSED_TEXT='not found'
  [ "$ENABLED" = 'true' ] && ENABLED_TEXT='enabled' || ENABLED_TEXT='disabled'
  echo "Cobalt Platform $VERSION"
  echo "Status: $ENABLED_TEXT"
  echo "Root: $ROOT_TEXT"
  echo "LSPosed: $LSPOSED_TEXT"
  exit 0
fi
exec "$MODDIR/bin/cobalt-platform" "$@"
