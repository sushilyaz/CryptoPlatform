# CryptoPlatform — Структура проекта (Этап A1)

**Назначение:** зафиксировать структуру модулей бэкенда, их роли и зависимости в многомодульном Gradle-проекте (Java 21, Spring Boot 3).

---

## Содержание

- [Общая идея](#общая-идея)
- [Дерево репозитория](#дерево-репозитория)
- [Роли модулей](#роли-модулей)
    - [core (java-library)](#core-java-library)
    - [adapters/* (java-library)](#adapters-java-library)
    - [Spring Boot сервисы](#spring-boot-сервисы)
- [Диаграмма зависимостей](#диаграмма-зависимостей)
- [Gradle: конфигурация](#gradle-конфигурация)
    - [settings.gradle (корень)](#settingsgradle-корень)
    - [build.gradle (корень)](#buildgradle-корень)
    - [Шаблоны build.gradle для модулей](#шаблоны-buildgradle-для-модулей)
- [Пакеты и именование](#пакеты-и-именование)
- [Сборка и запуск](#сборка-и-запуск)
- [Как добавить новый адаптер](#как-добавить-новый-адаптер)
- [Как добавить новый сервис](#как-добавить-новый-сервис)
- [Политика зависимостей](#политика-зависимостей)
- [Связь с планом работ](#связь-с-планом-работ)

---

## Общая идея

Проект — **многомодульный Gradle**.  
Модули делятся на:

- **Библиотеки** (`java-library`): `core`, все `adapters/*`
- **Приложения** (Spring Boot): все `*-service` + `api-service`

Корневой проект — **агрегатор** (не приложение).  
Папка `modules/` — **контейнер** для подпроектов (сама проектом не является).

---

## Дерево репозитория

```text
CryptoPlatform/
├─ settings.gradle
├─ build.gradle                 # общий конфиг всех подпроектов
└─ modules/
   ├─ core/                     # библиотека: домен + контракты
   ├─ adapters/                 # контейнер (НЕ Gradle-проект)
   │  ├─ binance-adapter/       # библиотека-адаптер
   │  ├─ bybit-adapter/
   │  ├─ bitget-adapter/
   │  ├─ gate-adapter/
   │  ├─ mexc-adapter/
   │  └─ dexscreener-adapter/
   ├─ discovery-service/        # Spring Boot: bootstrap листингов, анти-шум → БД
   ├─ stream-router/            # Spring Boot: WS/poll → Tick → шина
   ├─ fairprice-service/        # Spring Boot: fair (1с) + минутки
   ├─ bias-service/             # Spring Boot: EWMA bias (Redis hot + БД снапшоты)
   ├─ detector-service/         # Spring Boot: детект раскора + алерты
   ├─ notifier-service/         # Spring Boot: Telegram + WebPush
   └─ api-service/              # Spring Boot: REST + WS/SSE для фронта
```

> **Важно:** не включайте `modules` и `modules:adapters` как Gradle-проекты — это просто директории.

---

## Роли модулей

### core (java-library)

- Доменные модели: `InstrumentId`, `MarketId`, `MarketKind`, `TopOfBook`, `Tick`, …
- Контракты:
    - `ExchangeAdapter` — единый интерфейс адаптеров (REST discovery + WS/poll streaming).
    - `EventBus` — абстракция шины (реализация позже).
- Общие утилиты (сериализация, time utils) — по мере появления.

### adapters/* (java-library)

- Реализации `ExchangeAdapter` для конкретных площадок:  
  `binance-adapter`, `bybit-adapter`, `bitget-adapter`, `gate-adapter`, `mexc-adapter`, `dexscreener-adapter`.
- **Не** Spring Boot. Библиотеки с REST/WS клиентами и нормализацией данных.
- При появлении общего кода для адаптеров — вынести в отдельный модуль `adapters/common` (java-library).

### Spring Boot сервисы

- **discovery-service** — объединение листингов CEX/DEX, анти-шум фильтры → `venues/instruments/markets/market_quality`.
- **stream-router** — подключение к WS/poll через адаптеры, нормализация до `Tick`, публикация в шину, heartbeat/quality.
- **fairprice-service** — расчёт справедливой цены раз в 1с, запись `fair_minute`.
- **bias-service** — EWMA-смещение по рынкам (горячее — Redis, снапшоты — БД).
- **detector-service** — детект `|price−fair−bias|/fair ≥ threshold` + hold/hysteresis/dedup → `alerts`.
- **notifier-service** — Telegram + WebPush по событиям алертов.
- **api-service** — REST (`/api/instruments`, `/api/alerts`, `/api/chart/minutes`, `/api/settings`, `/api/health`) + WS/SSE (`/ws/alerts`, `/ws/chart`).

---

## Диаграмма зависимостей

```text
          +---------------------------+
          |            core           |
          | (домен, контракты, утилы) |
          +-------------+-------------+
                        |
          +-------------+-------------------------------+
          |                                             |
+---------v-----------------------+     +----------------v------------------+
|   adapters/* (java-library)     |     |  приложения (Spring Boot)         |
| реализация ExchangeAdapter под  |     | discovery/stream/fair/bias/       |
| конкретные площадки (CEX/DEX)   |     | detector/notifier/api              |
+---------------------------------+     +----------------+-------------------+
                                                         |
                              (каждый сервис зависит от `core`
                              и только от нужных `adapters/*`)
```

> **Правила:** сервисы не зависят друг от друга напрямую; обмен — через шину/БД/Redis.

---

## Gradle: конфигурация

### settings.gradle (корень)

```groovy
rootProject.name = 'CryptoPlatform'

include 'modules:core',
        'modules:adapters:binance-adapter',
        'modules:adapters:bybit-adapter',
        'modules:adapters:bitget-adapter',
        'modules:adapters:gate-adapter',
        'modules:adapters:mexc-adapter',
        'modules:adapters:dexscreener-adapter',
        'modules:discovery-service',
        'modules:stream-router',
        'modules:fairprice-service',
        'modules:bias-service',
        'modules:detector-service',
        'modules:notifier-service',
        'modules:api-service'
```

### build.gradle (корень, агрегатор)

- Подключает Spring Boot **с `apply false`**.
- Задает репозитории и Java toolchain для **всех** подпроектов.
- Группирует подпроекты:
    - **Библиотеки:** `core` и все `adapters/*` → `java-library`.
    - **Приложения:** все сервисы → `org.springframework.boot` + `io.spring.dependency-management` + зависимость `:modules:core`.
- Регистрирует задачу `wrapper` для всех подпроектов (фикс странностей IDE).

Пример (сокращённо):

```groovy
plugins {
  id 'java'
  id 'org.springframework.boot' version '3.5.6' apply false
  id 'io.spring.dependency-management' version '1.1.7' apply false
}

allprojects {
  repositories { mavenCentral() }
}

subprojects {
  apply plugin: 'java'
  java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
  tasks.withType(Test).configureEach { useJUnitPlatform() }
}

configure([
  project(':modules:core'),
  project(':modules:adapters:binance-adapter'),
  project(':modules:adapters:bybit-adapter'),
  project(':modules:adapters:bitget-adapter'),
  project(':modules:adapters:gate-adapter'),
  project(':modules:adapters:mexc-adapter'),
  project(':modules:adapters:dexscreener-adapter')
]) {
  apply plugin: 'java-library'
  dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.3'
  }
}

def appModules = [
  project(':modules:discovery-service'),
  project(':modules:stream-router'),
  project(':modules:fairprice-service'),
  project(':modules:bias-service'),
  project(':modules:detector-service'),
  project(':modules:notifier-service'),
  project(':modules:api-service')
]

configure(appModules) {
  apply plugin: 'org.springframework.boot'
  apply plugin: 'io.spring.dependency-management'
  dependencies {
    implementation project(':modules:core')
    implementation 'org.springframework.boot:spring-boot-starter'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
  }
}

allprojects {
  tasks.register('wrapper', org.gradle.api.tasks.wrapper.Wrapper) {
    gradleVersion = '8.14.3'
    distributionType = org.gradle.api.tasks.wrapper.Wrapper.DistributionType.BIN
  }
}
```

### Шаблоны build.gradle для модулей

**Сервисы** (`modules/*-service`, `modules/api-service`):

```groovy
plugins {
  id 'org.springframework.boot'
  id 'io.spring.dependency-management'
  id 'java'
}

dependencies {
  implementation project(':modules:core')
  implementation 'org.springframework.boot:spring-boot-starter'
  testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

tasks.test { useJUnitPlatform() }
```

**Адаптеры** (`modules/adapters/*-adapter`):

```groovy
plugins { id 'java-library' }

dependencies {
  api project(':modules:core')
  testImplementation 'org.junit.jupiter:junit-jupiter:5.10.3'
}
```

---

## Пакеты и именование

- `core`: `com.suhoi.core.*`  
  (напр. `com.suhoi.core.domain`, `com.suhoi.core.adapters`, `com.suhoi.core.bus`)
- Адаптеры: `com.suhoi.adapters.<venue>.*`  
  (напр. `com.suhoi.adapters.binance.BinanceExchangeAdapter`)
- Сервисы: `com.suhoi.<service>`  
  (напр. `com.suhoi.streamrouter.StreamRouterApplication`)

> Бизнес-логика не живёт в адаптерах; только интеграция и нормализация.

---

## Сборка и запуск

Сборка из корня:

```bash
./gradlew clean build
```

Запуск конкретного сервиса (пример для API):

```bash
./gradlew :modules:api-service:bootRun
```

Каждый сервис — самостоятельное Spring Boot приложение с собственным `*Application.java`.

---

## Как добавить новый адаптер

1. Создай каталог: `modules/adapters/<new-venue>-adapter/`.
2. Добавь строку в `settings.gradle`:
   ```groovy
   include 'modules:adapters:<new-venue>-adapter'
   ```
3. Создай `build.gradle` адаптера:
   ```groovy
   plugins { id 'java-library' }
   dependencies { api project(':modules:core') }
   ```
4. Реализуй `ExchangeAdapter` в пакете  
   `com.suhoi.adapters.<new-venue>.<NewVenue>ExchangeAdapter`.

---

## Как добавить новый сервис

1. Создай каталог: `modules/<new-svc>-service/`.
2. Добавь строку в `settings.gradle`:
   ```groovy
   include 'modules:<new-svc>-service'
   ```
3. `build.gradle` сервиса — как в шаблоне для сервисов.
4. Добавь `*Application.java` с `@SpringBootApplication`.
5. Подключи `:modules:core` и (при необходимости) нужные `adapters/*`.

---

## Политика зависимостей

- Сервис → `core` (обязательно), Сервис → нужные `adapters/*` (по необходимости).
- Сервис ↔ Сервис — **нельзя** напрямую (взаимодействие только через шину/БД/Redis).
- Общий код адаптеров — выделять в `adapters/common`.

---

## Связь с планом работ

- **Этап A1** — структура модулей — **готово** (этот документ).
- **Этап A2** — добавляем Liquibase и миграции (схема БД).
- **Этап A3–A4** — EventBus (NATS), базовые конфиги сервисов.
- Далее — discovery, роутинг, fair/bias/detector и UI.
