# CryptoScreener — Детализированный план разработки (MVP «Скринер раскора»)
Версия: 2025-10-09

Документ — единый источник правды по MVP бэкенда. Основан на утверждённой архитектуре и текущей структуре репозитория (`CryptoPlatform/modules/*`).

---

## 0. Нейминг и структура
**Репозиторий**: `CryptoPlatform/`  
**Модули**:
- `modules/core` — доменные DTO, контракты событий, утилиты (Jackson, time, math); интерфейсы `EventBus`, `ExchangeAdapter`, `DiscoveryClient`, `StreamClient`.
- `modules/adapters/*` — по одному jar на биржу: `binance-adapter`, `bybit-adapter`, `bitget-adapter`, `gate-adapter`, `mexc-adapter`, `dexscreener-adapter`.
- Сервисы (отдельные Spring Boot приложения):  
  `discovery-service`, `stream-router`, `fairprice-service`, `bias-service`, `detector-service`, `notifier-service`, `api-service`.

**Общие артефакты**:
- **NATS subjects**: `ticks.{asset}`, `fair.snap.{asset}`, `alerts.{asset}`, `control.{topic}`.
- **Канонические идентификаторы**:  
  `asset` = `BASE` (например, `BTC`), `quote` = `USDT`;  
  `marketKind` ∈ {`SPOT`, `PERP`, `FUTURES`, `DEX`};  
  `venue` ∈ {`BINANCE`, `BYBIT`, `BITGET`, `GATE`, `MEXC`, `DEXSCREENER`} и т. п.

---

## 1. Схемы данных и контракты (core)
### 1.1. Таблицы (Liquibase)
- `venues(venue PK, name, enabled)`
- `instruments(asset PK, base_symbol, quote_symbol='USDT', scale int)`
- `markets(market_id PK, asset FK, venue, kind, native_symbol, status, min_qty, min_notional)`
- `market_quality(market_id FK, vol24h_usd, depth50_usd, quality_score, last_heartbeat_ts)`
- `fair_minute(asset, ts_minute, fair, PRIMARY KEY(asset, ts_minute))`
- `price_minute(asset, venue, kind, ts_minute, mid, PRIMARY KEY(asset, venue, kind, ts_minute))`
- `venue_bias(asset, market_id, ts_minute, bias)`
- `alerts(id PK, ts, asset, market_id, dev_pct, fair, price, bias, threshold_pct, state)`
- `settings(key PK, value_json)`

Индексы по `market_quality.last_heartbeat_ts`, `alerts.ts`, `price_minute.ts_minute`.

### 1.2. События (JSON)
- **Tick**: `{ ts, asset, venue, kind, mid, bid, ask, depthUsd50, heartbeatTs }`
- **FairSnap**: `{ ts, asset, fair, sources: [{marketId, mid, weight}] }`
- **Alert**: `{ ts, asset, marketId, devPct, price, fair, bias, thresholdPct, state: "OPEN|CLOSE" }`

---

## 2. Этап A. Инфра и каркас
**Цель:** сборка многомодульного проекта, Liquibase, NATS, Redis, Postgres.

**Задачи:**
- **A1.** Общий `build.gradle` (BOM, версии), Jacoco, Spotless.
- **A2.** `modules/core`: DTO/схемы событий, `EventBus` (интерфейс), Jackson-конфиг (JavaTimeModule, snake_case), утилиты времени (clock), математика (EWMA, winsorize).
- **A3.** `core:nats` — реализация `EventBus` на `jnats` (pub/sub, reconnect, graceful shutdown).
- **A4.** Liquibase `changelog-master` + includes для всех таблиц из §1.1.
- **A5.** `docker-compose` локально (pg, redis, nats); healthchecks.
- **A6.** Smoke: из `api-service` публикуем/читаем `control.echo`.

**DoD:**
- `docker compose up` → `/actuator/health` зелёный, Liquibase применён, NATS pub/sub эхо работает.

---

## 3. Этап B. Адаптеры и Discovery

### 3.0. Контракты адаптеров (в `core`)
```java
public interface DiscoveryClient {
  List<VenueListing> listSpotUsdt();
  List<VenueListing> listPerpUsdt();
  // опционально: futures / delivery
}

public interface StreamClient extends AutoCloseable {
  void subscribeBookTicker(Collection<String> nativeSymbols, TickHandler onTick);
  // fallback: startFastPoll(...)
}

public record VenueListing(
  String venue, String kind, String nativeSymbol,
  String base, String quote, int priceScale, int qtyScale,
  String status
) {}
```

- Нормализация: `nativeSymbol` → `(asset=BASE, quote=USDT)`; строгая фильтрация по статусу `TRADING`.
- Анти-шум параметры берём из `settings` (`minVol24hUsd`, `minDepth50Usd` и пр.).

### 3.1. Binance adapter (`modules/adapters/binance-adapter`)
**Discovery (REST):**
- Spot: `GET /api/v3/exchangeInfo` → фильтр `quoteAsset=USDT`, `status=TRADING`.
- Perp (USDT-M): `GET /fapi/v1/exchangeInfo` → `quoteAsset=USDT`, `status=TRADING`.
- Из `filters` тянем `PRICE_FILTER`, `LOT_SIZE`, `MIN_NOTIONAL` → `priceScale`, `qtyScale`, `minNotional`.
- Нормализация BASE (настройки: whitelist/regex для нестандартных баз).

**Streaming (WS):**
- Spot WS: `wss://stream.binance.com:9443/stream?streams=<symbol>@bookTicker`
- Futures WS: `wss://fstream.binance.com/stream?streams=<symbol>@bookTicker`
- Поля: `b`/`B` (bid/qty), `a`/`A` (ask/qty), `u` (lastUpdateId), `E` (eventTime).
- `mid = (bid+ask)/2`.
- `depthUsd50`: из `@depth5@100ms` (аггрегируем до $50 notional) или REST `/depth?limit=5` (fast-poll).

**Fast-poll fallback (REST):**
- Spot: `GET /api/v3/ticker/bookTicker?symbol=...`
- Futures: `GET /fapi/v1/ticker/bookTicker?symbol=...`
- Частота: 250–500 мс с учётом rate limit (конфиг).

**DoD Binance:**
- `discovery-service` создаёт записи для SPOT/USDT-M с корректными `nativeSymbol` и шкалами.
- `stream-router` публикует `ticks.{asset}` (SPOT и PERP) стабильно.
- `market_quality.last_heartbeat` < 5 с; `depthUsd50` присутствует для >95% тикков.

### 3.2. Gate adapter (Spot/Contracts)
- Discovery по их markets; WS `bookTicker`/depth; fallback `ticker/book_ticker`.
- Нормализация символов и статусов, heartbeat/reconnect.

### 3.3. Bybit adapter
- USDT Spot и USDT Perp (inverse вне MVP); WS `tickers`/orderbook; REST fallback.

### 3.4. Bitget adapter
- USDT Spot/Perp, WS `books`/`tickers`; REST fallback.

### 3.5. MEXC adapter
- USDT Spot/Perp, WS `bookTicker`-аналоги; REST fallback.

### 3.6. Dexscreener adapter
- Поиск пулов `BASE/USDT` (кросс-квоты избегаем в MVP).
- Выбор пула по `TVL/vol24h` и/или взвешивание; троттлинг запросов; heartbeat.

### 3.7. discovery-service
- Слияние листингов: для каждого `asset` собираем `markets` (SPOT|PERP|FUTURES|DEX).
- Считаем `market_quality.vol24h_usd`/`depth50_usd` (REST снапшоты), присваиваем `quality_score`.
- Анти-шум фильтры: min `vol24h_usd`, min `depth50_usd`, blacklist баз.
- Пишем в БД: `venues`, `instruments`, `markets`, `market_quality`.
- Рефреш каждые `N` минут, `control.reload`.

**DoD Discovery:**
- В БД валидный список рынков; каждый PERP имеет SPOT или DEX в связке; «пустышки» отфильтрованы.

---

## 4. Этап C. Stream Router
**Цель:** подписки на WS/fast-poll, нормализация до `Tick`, публикация в NATS.

**Задачи:**
- **C1.** Загрузка валидных `markets` из БД (только `enabled`).
- **C2.** Для каждой биржи — батч-подписки (multi-stream) с авто-чанкованием символов.
- **C3.** Парсинг и нормализация: `nativeSymbol → asset`, расчёт `mid`, `depthUsd50`.
- **C4.** Публикация `ticks.{asset}`; обновление `market_quality.last_heartbeat`.
- **C5.** Watchdog: stale-детект, перезапуск WS, экспоненциальный backoff, jitter.
- **C6.** Метрики: events/s, reconnects, лаг, доля событий без `depthUsd50`.

**DoD:**
- Устойчивый поток `Tick` для пилотного набора (например, 50 инструментов × 2–4 рынка).

---

## 5. Этап D. Аналитика: Fair, Bias, Detector
### 5.1. fairprice-service
- Подписка `ticks.*`.
- Раз в секунду по каждому `asset` агрегируем «живые» рынки (heartbeats OK).
- Вес: `w = sqrt(depthUsd50) * quality_score` (с нормировкой).
- Анти-шум: winsorize по квантилям, отсечение экстремумов.
- Публикуем `fair.snap.{asset}`, пишем `fair_minute` и `price_minute` (per-venue) с округлением к минуте.
- Retention: чистим >24h.

### 5.2. bias-service
- Подписка `ticks.*` и `fair.snap.*`.
- EWMA по `(market)` для `(price − fair)` → горячее в Redis `bias:{asset}:{marketId}`, снапшоты в `venue_bias`.
- Параметры: `alpha` (в `settings`), optional `winsorizePct`.

### 5.3. detector-service
- `dev = (price − fair − bias) / fair`.
- Порог: `absThresholdPct = 3%`, `holdMs = 500`, `hysteresisDown = 0.7`, дедуп 60 c по `(asset, marketId, direction)`.
- Учитываем только рынки с валидным heartbeat и качеством.
- Пишем в `alerts` и публикуем `alerts.{asset}` (OPEN/CLOSE).

**DoD:**
- `fair` обновляется каждую секунду, `bias` стабилен, алерты корректно открываются/закрываются при реальном >3%.

---

## 6. Этап E. Уведомления
- `notifier-service` слушает `alerts.*`.
- Telegram Bot API: форматируем тикер, venue, dev%, fair, price, bias, ссылка на график.
- WebPush (VAPID): подписки через `api-service`, rate-limit на `(asset, marketId)`.

**DoD:** TG и браузерные пуши приходят мгновенно при алерте.

---

## 7. Этап F. API-сервис
**REST:**
- `GET /api/instruments` — список инструментов и рынков.
- `GET /api/alerts?from=&to=&asset=` — алерты (по умолчанию 24h).
- `GET /api/chart/minutes?asset=...&windowMin=1440` — минутки per-venue + fair.
- `GET /api/settings` / `PUT /api/settings` — пороги/анти-шум.
- `GET /api/health`.

**WS/SSE:**
- `/ws/alerts` — live-алерты.
- `/ws/chart?asset=...` — текущая секунда (все площадки + `fair`).

**DoD:** фронт получает минутки и live-данные, CORS/валидация/пагинация настроены.

---

## 8. Этап G. Фронтенд (отдельный проект)
- **Alerts:** лента/таблица, фильтры, клик → Chart.
- **Chart:** uPlot — серии по venue + FAIR; минутки (REST) + «секунда» (WS), подсказки dev%/bias.
- **Settings:** изменение порогов.

---

## 9. Этап H. Наблюдаемость и эксплуатация
- Логи JSON, уровни per-module, traceId в MDC.
- Health WS/Redis/DB/NATS; backoff на reconnect.
- Троттлинг Dexscreener/REST-очередь к CEX; rate-limit уведомлений.
- README: ENV, запуск, порядок сервисов, матрица поддерживаемых рынков.

---

## 10. Детальные чек-листы по адаптерам

### 10.1. Binance — чек-лист реализации
- [ ] REST discovery SPOT/USDT-M, парсеры фильтров, статус `TRADING`.
- [ ] Нормализация базовых символов; конфигируемый blacklist/regex.
- [ ] WS `@bookTicker` (SPOT/USDT-M); батч-стримы; восстановление при лаге.
- [ ] `depthUsd50`: depth-стрим или периодический `/depth?limit=5`.
- [ ] Fast-poll резерв `ticker/bookTicker` (rate-limit aware).
- [ ] Heartbeat метрики, reconnect/backoff, jitter.
- [ ] Unit-тесты парсинга exchangeInfo/filters; интеграционный smoke WS.
- [ ] DoD: стабильные `Tick` с >95% заполнением `depthUsd50`.

### 10.2. Bybit | Bitget | Gate | MEXC — чек-лист
- [ ] Discovery SPOT+PERP USDT, статус-фильтр.
- [ ] WS bookTicker/depth (эквиваленты), fallback REST.
- [ ] Нормализация symbols → `asset`.
- [ ] Heartbeat/reconnect; метрики.
- [ ] Smoke: 5–10 инструментов, 24h стабильность.

### 10.3. Dexscreener — чек-лист
- [ ] Поиск пулов `BASE/USDT`, выбор top по `TVL/vol24h`.
- [ ] Троттлинг запросов, кэш; heartbeat.
- [ ] Нормализация до `Tick` (mid, depthUsd50 если доступно; иначе эвристика/NA).

---

## 11. Тест-план (MVP)
- **E2E пилот**: `BTC`, `ETH`, `SOL` × (BINANCE SPOT+PERP, BYBIT PERP, MEXC SPOT, GATE SPOT) + (опционально 1 DEX).
- **Нагрузка**: 50 инструментов × 2–4 рынков, целевой поток ≥ 200 msg/s.
- **Надёжность**: WS reconnect < 5 c; потеря не более 1% секундных fair-снапшотов за 1 час.
- **Функция**: искусственно поднять dev% > 3% (эмулятор или off-market) → алерт OPEN/CLOSE.

---

## 12. Backlog (Kanban)
**To Do:**
- A1–A6 Инфра/Liquibase/NATS/Redis/PG/Smoke.
- B0 Контракты `DiscoveryClient`/`StreamClient` в core.
- B1 Binance (REST+WS+fallback+depth, тесты).
- B2 Gate (REST+WS), B3 Bybit, B4 Bitget, B5 MEXC.
- B6 Dexscreener (поиск пулов + троттлинг).
- B7 discovery-service (слияние листингов, анти-шум, запись, рефреш).
- C1–C6 stream-router (подписки, `Tick`, watchdog, метрики).
- D1 fairprice-service (секунда, минутки, retention).
- D2 bias-service (EWMA Redis + снапшоты).
- D3 detector-service (3%, hold, hysteresis, dedup, alerts → DB/NATS).
- E1/E2 notifier-service (TG + WebPush в API).
- F1–F3 API (REST/WS/SSE, CORS, health).
- G1–G3 Frontend (Alerts/Chart/Settings).
- H1–H4 Обслуживание/логи/health/reconnect/rate-limits.

**In Progress:** заполняется по ходу.  
**Done:** по мере выполнения.

---

## 13. Немедленные next steps
1. Залочить контракты `Tick`, `FairSnap`, `Alert` в `core` + `EventBus` (NATS).
2. Завести `binance-adapter` (REST discovery + WS bookTicker + depth fallback) и прогнать smoke на 5 символах.
3. Поднять `stream-router` с Binance, писать `market_quality.last_heartbeat`.
4. Запустить `fairprice-service` (секундный fair) и `bias-service`.
5. Включить `detector-service` с порогом 3% и TG-уведомления.
