# Разработка RaskolZones

## Требования

- JDK 25.
- Maven 3.9+.
- Paper 26.2 для runtime smoke test.

## Сборка и unit-тесты

Из корня репозитория:

```bash
mvn --batch-mode --no-transfer-progress clean verify
```

Готовый JAR находится в `target/raskol-zones-0.1.0-SNAPSHOT.jar`.

Paper API подключается с областью `provided`: API предоставляет сервер, поэтому оно не упаковывается внутрь плагина.

## Первый runtime smoke test

1. Создать отдельный тестовый сервер Paper 26.2 на Java 25.
2. Скопировать JAR из `target` в каталог `plugins`.
3. Запустить сервер и проверить, что RaskolZones включился без ошибок.
4. Убедиться, что создан `plugins/RaskolZones/config.yml`.
5. Проверить сообщение о готовности foundation.
6. Корректно остановить сервер и проверить отсутствие ошибок при выключении.

Этот smoke test пока выполняется вручную. Прохождение Maven verify не заменяет его.
