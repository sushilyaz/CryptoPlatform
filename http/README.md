# HTTP сценарии для IntelliJ

## Как запускать
1. Открой любой `.http` файл в IntelliJ.
2. В правом верхнем углу выбери окружение (например, `local`).
3. Жми ▶️ возле запроса или "Run all requests in file".
4. Внизу появится консоль с ответом и автотестами (если есть).

## Окружения
- `http-client.env.json` — общие переменные для локалки/докера.
- Можно создать `http-client.private.env.json` (не коммитить) — для секретов.

## Что проверяем
- `smoke.http` — базовая «дымовуха»: жив ли NATS, доступны ли сервисы, эхо через шину.
- `api-service.http` — ручные проверки API сервиса (echo, health).
- `discovery-service.http` — health discovery + версия Liquibase (по логам/таблицам).

## Требования
- Docker infra запущена (`nats`, `postgres`, `redis` здоровы).
- `api-service` запущен на `:8080` (для echo).
- `discovery-service` запущен на `:8081` (Liquibase уже прогнал схему).

