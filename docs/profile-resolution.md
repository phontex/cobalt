# Выбор профиля

Приложение получает у Android:

- package name;
- versionCode;
- versionName;
- сертификат приложения;
- список split APK и их контрольные суммы.

Затем выбирается запись из `manifests/profiles.json` по точному package name и версии. После этого загружается только путь из найденного профиля:

```text
com.heytap.themestore/17.22.1/
com.oplus.themestore/EXP_17.11.20/
```

Если пакет или версия не совпадают, профиль не активируется. Между `com.heytap.themestore` и `com.oplus.themestore` нет общего словаря по умолчанию.
