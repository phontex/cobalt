# Cobalt Platform

Минимальный SukiSU/Magisk-совместимый системный компонент Cobalt.

## Возможности

- создаёт `/data/adb/cobalt-platform/{logs,inbox,cache,backup,roles,records}`;
- `status` — состояние модуля (версия, API, root, `apk_patching`, `network`);
- `supported` — спрашивает у Cobalt App список поддерживаемых приложений;
- `plan PACKAGE` — спрашивает рецепт и **проверяет его** по белым спискам;
- `discover PACKAGE` — путь к base.apk, версия, код версии, сплиты;
- `export PACKAGE DEST` — копирует установленный APK туда, где прочитает приложение;
- `configure BASE64` — сохраняет роль приложения в `roles/active-profile.json`;
- `mount PACKAGE APK VERSION` — подмена APK через bind-mount, с проверкой пути и файла;
- `umount PACKAGE` — откат, `verify PACKAGE` — проверка состояния;
- `logs`, `clear-logs`.

## Границы

Не скачивает файлы, не содержит APK, не собирает приложения, не применяет
непроверенные данные. `mount` принимает файл только из хранилища приложения, кэша
платформы или `/data/local/tmp`, и только если это настоящий zip-контейнер.

## Токен

Платформа не может читать каталог и настройки приложения. Приложение передаёт ей
токен через `configure`; с этим токеном платформа запрашивает данные у
`content://app.cobalt.translate.platform/…`. Без токена провайдер отказывает.

```sh
cobalt-platform status
cobalt-platform supported
cobalt-platform plan com.heytap.themestore
cobalt-platform discover com.heytap.themestore
cobalt-platform mount com.heytap.themestore /data/local/tmp/base.apk 17.23.0beta1
cobalt-platform verify com.heytap.themestore
cobalt-platform umount com.heytap.themestore
```
