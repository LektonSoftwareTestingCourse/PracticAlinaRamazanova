# API-тесты СМП — отчёт

## 1. Покрытие

| Эндпоинт | Проверяемые статус-коды | Кол-во методов | Статус |
|---|---:|---:|---|
| `GET /health` (Gateway) | 200 | 1 | Реализован |
| `POST /api/cards` | 201, 400 | 3 | Реализован |
| `GET /api/cards/{pan}` | 200, 400, 404 | 3 | Реализован |
| `GET /api/cards` | 200, 400 | 3 | Реализован |
| `POST /api/cards/generate` | 201 | 1 | Реализован |
| `PATCH /api/cards/{pan}` | 200 | 1 | Реализован |
| `DELETE /api/cards/{pan}` | 204 | 1 | Реализован |
| `POST /api/cards/{pan}/reserve` | 200, 402, 409 | 3 | Реализован |
| `POST /api/cards/{pan}/rollback` | 200 | 1 | Реализован |
| `POST /api/cards/bulk-update` | 200 | 1 | Реализован |
| `POST /api/transactions` | 200, 400 | 12 | Реализован |
| `GET /api/transactions/search` | 200, 400 | 5 | Реализован |
| `GET /api/transactions/export` | 200 | 1 | Реализован |
| `GET /api/dashboard/stats` | 200 | 1 | Реализован |
| `GET /api/dashboard/recent` | 200, 400 | 2 | Реализован |
| `GET /api/dashboard/charts` | 200, 400 | 2 | Реализован |
| `POST /api/simulator/terminal/run` | 200, 400 | 2 | Реализован |
| `POST /api/simulator/terminal/start-continuous` | 200 | 1 | Реализован |
| `POST /api/simulator/terminal/stop` | 200 | 1 | Реализован |
| `POST /api/simulator/merchant/run` | 200 | 1 | Реализован |
| `POST /api/simulator/merchant/fee` | 400, 404 | 2 | Реализован |
| `GET /api/simulator/merchants` (service URL) | 200 | 1 | Реализован напрямую; Gateway route отсутствует |
| `GET /health` (Authorization, Switch, Logger) | 200 | 3 | Реализован |
| `POST /api/internal/authorize` | 200, 400, 404 | 6 | Реализован |
| `POST /api/internal/route` | 200 | 1 | Реализован |
| `POST /api/internal/rollback` | 200, 404, 409 | 2 | Реализован |
| `POST /api/internal/log` | 201, 200, 400, 409 | 2 | Реализован |

Всего 86 тестов:

[INFO] Results: Tests run: 86, Failures: 0, Errors: 0, Skipped: 0

## 2. Бизнес-отказы

| Исход | Код | Причина | Путь достижения | Подготовка данных | Статус |
|---|---:|---|---|---|---|
| Недостаточно средств | 51 | `INSUFFICIENT_FUNDS` | Gateway → Switch → Authorization | Карта с балансом 50, сумма 100 | Тест |
| Превышен дневной лимит | 61 | `EXCEEDS_AMOUNT_LIMIT` | Gateway → Switch → Authorization | Карта с дневным лимитом 100, сумма 101 | Тест |
| Заблокированная карта | 05 | `CARD_BLOCKED` | Gateway → Switch → Authorization | Создать карту, PATCH status=`BLOCKED` | Тест |
| Неактивная карта | 05 | `CARD_INACTIVE` | Gateway → Switch → Authorization | Создать карту, PATCH status=`INACTIVE` | Тест |
| Истёкшая карта по статусу | 54 | `CARD_EXPIRED` | Gateway → Switch → Authorization | Создать карту, PATCH status=`EXPIRED` | Тест |
| Истёкшая карта по времени запроса | 54 | `CARD_EXPIRED` | Gateway → Switch → Authorization | Передать `transmissionDateTime` после срока действия карты | Тест |
| Неизвестный BIN | 14 | `CARD_NOT_FOUND` | Gateway → Switch | Запрос с BIN `499999` | Тест |
| Неизвестный PAN при известном BIN | 14 | `CARD_NOT_FOUND` | Прямой вызов Authorization | Случайный PAN с BIN `400000` | Тест |
| Повторный откат | 05 | `ALREADY_ROLLED_BACK` | Прямой вызов Authorization | Успешная авторизация и два одинаковых отката | Тест |
| Откат неизвестной транзакции | 14 | `TRANSACTION_NOT_FOUND` | Прямой вызов Authorization | Случайный RRN | Тест |


## 3. Валидация

| Поле | Правило | Классы | Границы | Кол-во проверок |
|---|---|---|---|---:|
| `pan` | 16 цифр | Короткое значение; недопустимый символ; пустое значение | `123`; `400000123456789A`; пустая строка | 7 |
| `amount` | Положительная сумма для Gateway; для Authorization DTO проверяется отрицательное значение | Нулевая и отрицательная сумма | `0`; `-1` | 3 |
| `mti` | Обязательное значение `0100` | Неправильная длина; недопустимое значение | `010`; `0120` | 2 |
| `stan` | Обязательное значение длиной 6 символов в Authorization DTO | Неправильная длина | `123` | 1 |
| `processingCode` | Обязательное значение длиной 6 символов в Authorization DTO | Неправильная длина; пустое значение | `123`; пустая строка | 2 |
| `currencyCode` | Обязательное значение из 3 цифр | Неправильная длина | `64` | 3 |
| `terminalId` | Обязательное значение длиной 8 символов в Authorization DTO | Неправильная длина; пустое значение | `TERM01`; пустая строка | 2 |
| `merchantId` | Обязательное значение длиной 15 символов в Authorization DTO | Неправильная длина; пустое значение | `MERCH01`; пустая строка | 2 |
| `mcc` | Обязательное значение из 4 цифр | Неправильная длина | `123` | 2 |
| `acquirerId` | Обязательное значение | Пустое значение | Пустая строка | 2 |
| `issuerId` | Обязательное поле в Authorization DTO | Отсутствующее поле | Поле удалено из запроса | 1 |
| `transmissionDateTime` | Обязательное поле | Отсутствующее значение (`null`) | `null` | 2 |
| `cardholderName` | Заглавные латинские буквы и разрешённые символы | Строчные буквы | `api test` | 1 |
| `bin` | Значение должно соответствовать формату BIN | Неправильная длина | `123` | 1 |
| `dailyLimit` | Неотрицательное значение | Отрицательное значение | `-1` | 1 |
| `monthlyLimit` | Неотрицательное значение | Отрицательное значение | `-1` | 1 |
| `limit` | Положительное число, максимум 500 | Нулевое; отрицательное; превышающее максимум | `0`; `-1`; `501` | 6 |
| `offset` | Неотрицательное число | Отрицательное значение | `-1` | 1 |
| `dateFrom`, `dateTo` | Начало диапазона не позже конца | Обратный диапазон дат | `dateFrom=2026-11-27`, `dateTo=2026-11-25` | 1 |
| `granularity` | Допустимы значения `hour` и `day` | Недопустимое значение | `minute` | 1 |
| `count` | Минимум 1 | Значение ниже минимума | `0` | 1 |


## 4. Расхождения контракта и реализации

| Декларировано | Наблюдается по исходникам | Источник | Классификация |
|---|---|---|---|
| `AuthorizationRequest.issuerId` необязателен и заполняется Switch | DTO Authorization помечает `issuerId` как `@NotBlank`; прямой вызов `/api/internal/authorize` без поля отклоняется с 400. Switch добавляет issuerId до вызова Authorization | `docs/api-spec.md`, `AuthorizationRequest`, Switch routing service | Контракт внутреннего API расходится с DTO; для прямого вызова требуется поле |
| `Card.expiryDate` — строка `MMYY` | `CardModel.expiryDate` имеет тип `YearMonth`, а не `String(4)`; фактическое JSON-представление поля требует runtime-подтверждения | `docs/api-spec.md`, `CardModel` | Несовпадение типа; wire-формат не подтверждён прогоном |
| `GET /api/simulator/merchants` указан как API симулятора мерчантов | Gateway route задан как `/api/simulator/merchant/**` (единственное число), а контроллер использует `/api/simulator/merchants` (множественное число); напрямую сервисный endpoint существует | Gateway `application.yml`, `SimulationController` | Дефект маршрутизации Gateway |
| В `docs/api-spec.md` описан сокращённый набор Card/Transaction/Dashboard/simulator endpoint | В контроллерах есть дополнительные операции: список/изменение/удаление/резервирование/откат/bulk update карт, CSV export, charts, fee, continuous start/stop | `docs/api-spec.md`, контроллеры сервисов | Документация неполная |
| Внутренние `/api/internal/*` операции воспринимаются как часть общей спецификации | Gateway не содержит маршрутов к `/api/internal/authorize`, `/api/internal/rollback`, `/api/internal/log` и `/api/internal/route`; вызовы идут напрямую в соответствующие сервисы | Gateway `application.yml`, сервисные контроллеры | Внутренние контракты доступны только по адресам downstream-сервисов |
| Бизнес-отказ транзакции должен отражаться HTTP-ошибкой согласно контракту | В текущем прогоне бизнес-отказы возвращаются с HTTP 200 и доменным результатом `DECLINED`; тесты проверяют `responseCode` и `declineReason` | `docs/api-spec.md`, `TransactionsApiTest` | Расхождение HTTP-контракта и фактического поведения; 403/422 нельзя указывать как проверенные runtime-ответы |\n| Повторный `POST /api/internal/log` с тем же UUID и тем же телом должен быть идемпотентным | Фактическая реализация возвращает HTTP 409 для повтора с тем же телом и для конфликтующего тела | `InternalApiTest.loggerInternal_duplicateAndConflictingPayload_return409`, runtime-прогон | Расхождение поведения с ожидаемой идемпотентностью; тест фиксирует текущий ответ 409 |\n| Gateway должен проверять формат полей так же, как Authorization DTO | Gateway проверяет непустые значения `stan`, `processingCode`, `terminalId` и `merchantId`, но не проверяет их длину; Authorization DTO применяет `@ExactSize` для этих полей. Ошибка Authorization маскируется Switch как общий отказ | `TransactionRequestValidator`, `AuthorizationRequest`, `AuthorizationClient`, `RouteService` | Дефект валидации и преобразования ошибки между Gateway, Switch и Authorization |
| Ошибки валидации должны указывать поле и нарушенное правило | Card Management `GlobalExceptionHandler` формирует `message` из `FieldError.defaultMessage`, не добавляя имя поля; у Authorization нет собственного обработчика `MethodArgumentNotValidException` | Card Management `GlobalExceptionHandler`, Authorization controller/config | Недостаточная диагностичность тела ошибки |


## 5. Обоснование достаточности

Тесты разделены по доменам: карты, транзакции, Dashboard, симуляторы, Gateway и внутренние HTTP-контракты Switch/Authorization/Logger. Уникальные карты создаются через API; PAN и STAN генерируются для теста, RRN и UUID не переиспользуются. Начальное состояние карты (баланс, дневной и месячный лимиты, статус) задаётся перед бизнес-сценарием.

Успешная транзакция проверяется по ответу Gateway и затем находится через поиск Logger. Для отклонённых транзакций проверяются доменный код и причина; запись ожидается через Awaitility. Для запроса, отклонённого Gateway на этапе валидации, проверяется отсутствие записи. Доступ к PostgreSQL и RabbitMQ напрямую из тестов не используется.

Применены `given/when/then`, общие `RequestSpecification`, классы по доменам, support-слой с фабриками данных, `@ParameterizedTest`, `matchesPattern`, `nullValue`, `equalTo`, `oneOf`, `hasSize`, `greaterThanOrEqualTo`, `notNullValue`, GPath `findAll`, JSON Schema Validator, проверки `Content-Type`, `Content-Disposition` и `X-Cache`, а также Awaitility с явными `atMost` и `pollInterval`.

Вне набора оставлены прямые проверки PostgreSQL/RabbitMQ, топологии очередей и outbox: они относятся к модулю 6. Не моделировались недоступность зависимостей и аварии инфраструктуры; для их проверки потребуются управляемые fault-injection сценарии. Полный попарный перебор всех фильтров и все комбинации параметров также не реализованы.


## 6. Проверка чувствительности тестов (mutation testing)

| Тест | Внесенное изменение                                                           | Результат                                        |
|---|-------------------------------------------------------------------------------|--------------------------------------------------|
| `postTransaction_approvedResponseAndLoggerEffect` | Изменение ожидаемого `responseCode` с `00` на `05`                            | Тест завершится ошибкой на проверке тела ответа  |
| `postTransaction_insufficientFunds_returnsDeclinedOutcome` | Изменение ожидаемого `declineReason` с `INSUFFICIENT_FUNDS` на `CARD_BLOCKED` | Тест завершится ошибкой на проверке причины отказа |
| `loggerInternal_duplicateAndConflictingPayload_return409` | Изменение ожидаемого статуса повтора с тем же UUID и телом с 409 на 200       | Тест завершится ошибкой на проверке HTTP-статуса |


## 7. Порядок запуска

Linux/macOS:

```bash
bash scripts/run-api-tests.sh
```

Windows PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run-api-tests.ps1
```

Если стек уже поднят:

```bash
mvn -f services/pom.xml -Papi-tests test
```

Профиль `api-tests` подключает только модуль API-тестов. Скрипты создают `.env` из `.env.example`, поднимают необходимые сервисы через Docker Compose, запускают Maven и останавливают Compose-стек после прогона. Переменные `GATEWAY_URL`, `SWITCH_URL`, `AUTH_URL`, `LOGGER_URL` и `MERCHANT_URL` позволяют переопределить адреса тестируемых сервисов.

GitHub Actions: job `api-contract-tests` в `.github/workflows/ci.yml` поднимает стек и выполняет тот же скрипт
