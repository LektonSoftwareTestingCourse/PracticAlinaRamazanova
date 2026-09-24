# Test Design — ядро СМП

## 1. Классы эквивалентности

### Authorization

| Поле | Валидные классы | Невалидные классы |
|---|---|---|
| `card_status` | ACTIVE, INACTIVE, BLOCKED, EXPIRED | неизвестный статус |
| `expiry_state` | CURRENT, FUTURE, EXPIRED | неверное значение |
| `daily_limit` | BELOW, EQUAL, ABOVE | неверное значение |
| `monthly_limit` | BELOW, EQUAL, ABOVE | неверное значение |
| `balance` | BELOW, EQUAL, ABOVE | неверное значение |
| `bin_lookup` | AVAILABLE, UNAVAILABLE | неверное значение |
| `issuer_source` | BIN, SWITCH | неверное значение |
| `card_management` | AVAILABLE, UNAVAILABLE | неверное значение |

### Card-Management

| Поле | Валидные классы | Невалидные классы |
|---|---|---|
| `operation` | CREATE, UPDATE, DELETE, GET | неизвестная операция |
| `card_status` | NEW, ACTIVE, BLOCKED, EXPIRED | неизвестный статус |
| `pan_check` | VALID | INVALID_LENGTH, INVALID_SYMBOLS |
| `expiry_check` | VALID | INVALID_FORMAT, EXPIRED |
| `owner_check` | KNOWN | UNKNOWN |
| `request_source` | API, ADMIN_PANEL, BATCH | неизвестный источник |
| `audit_mode` | ENABLED, DISABLED | — |

## 2. Граничные значения

Проверяются границы основных бизнес-полей.

### Authorization

- `daily_limit`: BELOW / EQUAL / ABOVE;
- `monthly_limit`: BELOW / EQUAL / ABOVE;
- `balance`: BELOW / EQUAL / ABOVE;
- `expiry_state`: CURRENT / FUTURE / EXPIRED.

### Card-Management

- длина PAN: допустимая длина / меньше / больше;
- `expiryDate`: корректный MMYY / неверный формат / истёкший срок.

## 3. Попарное тестирование

Для каждого сервиса используется отдельная PICT-модель с ограничениями.

**Authorization:** `pict/model.txt` → `pict/cases.txt`.

**Card-Management:** `pict/model-card-management.txt` → `pict/cases-card-management.txt`.

Набор используется как источник комбинаций параметров; критичные сочетания дополнительно проверяются отдельными кейсами.

## 4. Тест-кейсы

### Authorization

| ID | Тип | Источник | Проверка |
|---|---|---|---|
| AUTH-01 | Positive | EP | ACTIVE + корректный срок |
| AUTH-02 | Negative | EP | BLOCKED |
| AUTH-03 | Negative | EP | EXPIRED + EXPIRED |
| AUTH-04 | Positive | BVA | сумма BELOW лимита |
| AUTH-05 | Positive | BVA | сумма EQUAL лимиту |
| AUTH-06 | Negative | BVA | сумма ABOVE лимита |
| AUTH-07 | Positive | Pairwise | строка из `cases.txt` |
| AUTH-08 | Negative | Pairwise | строка с недоступным внешним сервисом |

### Card-Management

| ID | Тип | Источник | Проверка |
|---|---|---|---|
| CARD-01 | Positive | EP | CREATE новой карты |
| CARD-02 | Positive | EP | UPDATE с валидным PAN |
| CARD-03 | Negative | EP | DELETE без известного владельца |
| CARD-04 | Positive | EP | GET с включённым аудитом |
| CARD-05 | Negative | EP | истёкшая карта |
| CARD-06 | Negative | EP | BATCH + DELETE |
| CARD-07 | Positive | Pairwise | строка из `cases-card-management.txt` |
| CARD-08 | Negative | Pairwise | строка с невалидными PAN/expiry |
