# Test Design — ядро СМП

> Тест-дизайн ядра СМП: Authorization (ТЗ 04) и Card-Management (ТЗ 05).  
> Техники: классы эквивалентности, граничные значения, попарное тестирование (PICT). Каждый тест-кейс содержит требование, предусловие, шаги и ожидаемый результат — для прослеживаемости к ТЗ.

## 0. Требования

Идентификаторы для трассировки тест-кейсов к алгоритму из ТЗ.

**Authorization**

| ID | Требование | Источник |
|---|---|---|
| AUTH-01 | Карта запрашивается в Card Management по PAN; не найдена -> DECLINED, responseCode=14 | ТЗ 04, шаг 1 |
| AUTH-02 | Статус карты: INACTIVE -> CARD_INACTIVE; BLOCKED -> CARD_BLOCKED; EXPIRED -> responseCode=54 | ТЗ 04, шаг 2 |
| AUTH-03 | Срок действия: expiryDate (MMYY) раньше текущей даты -> responseCode=54 | ТЗ 04, шаг 3 |
| AUTH-04 | Дневной лимит: сумма одобренных за сегодня + текущая ≤ dailyLimit, иначе responseCode=61 | ТЗ 04, шаг 4 |
| AUTH-05 | Месячный лимит: аналогично дневному за текущий месяц, иначе responseCode=61 | ТЗ 04, шаг 5 |
| AUTH-06 | Баланс: amount ≤ availableBalance, иначе responseCode=51 | ТЗ 04, шаг 6 |
| AUTH-07 | Успех: резерв через CMS, RRN (12 цифр), authCode (6 симв. A-Z0-9), responseCode=00 | ТЗ 04, шаг 7 |
| AUTH-08 | Обогащение issuerId через Bin Lookup; при недоступности — fallback на BIN-таблицу Switch, решение не меняется | ТЗ 04, раздел 2 (bin-lookup) |
| AUTH-09 | Card Management недоступен -> DECLINED, responseCode=05, declineReason=ISSUER_TIMEOUT | ТЗ 04, раздел 5 (отказ зависимости) |

**Card-Management**

| ID | Требование | Источник |
|---|---|---|
| CMS-01 | POST /api/cards (operation=CREATE): создаёт карту в статусе NEW/ACTIVE, PAN по алгоритму Луна, 201 | ТЗ 05, раздел «Создание карты» |
| CMS-02 | GET /api/cards/{pan} (operation=GET): 200 с данными карты либо 404, если карта не найдена | ТЗ 05, раздел «Получение карты» |
| CMS-03 | PATCH /api/cards/{pan} (operation=UPDATE): частичное обновление статуса, лимитов, баланса; PAN должен быть валиден | ТЗ 05, раздел «Обновление карты» |
| CMS-04 | DELETE /api/cards/{pan} (operation=DELETE): мягкое удаление (status=DELETED), только для известного владельца | ТЗ 05, раздел «Удаление карты» |
| CMS-05 | Валидация PAN: длина 16 цифр и контрольная цифра по алгоритму Луна; иначе 400/404 | ТЗ 05, раздел «Алгоритм Луна» |
| CMS-06 | Валидация expiryDate (MMYY): корректный формат; истёкший срок помечает карту EXPIRED | ТЗ 05, раздел «Срок действия» |
| CMS-07 | Владелец карты должен быть известен системе (owner_check), иначе 404 | ТЗ 05, раздел «Владелец карты» |
| CMS-08 | Аудит: GET-запросы всегда логируются (audit_mode=ENABLED); источник запроса (API/ADMIN_PANEL/BATCH) фиксируется в аудите | ТЗ 05, раздел «Аудит и журналирование» |

**Тестовые данные:** K0 — карта по умолчанию (ACTIVE, срок +3 года, dailyLimit=15000000, monthlyLimit=300000000, availableBalance=100000000); создаётся заново для каждого кейса, если не указано иное. `[M]` — текущий MMYY, `[M-1]` — прошлый месяц. Суммы — в копейках.

## 1. Классы эквивалентности

### Authorization

| Поле | Валидные классы | Невалидные классы |
|---|---|---|
| card_status | ACTIVE | INACTIVE, BLOCKED, EXPIRED |
| expiry_state | CURRENT, FUTURE | EXPIRED |
| daily_limit | BELOW, EQUAL | ABOVE |
| monthly_limit | BELOW, EQUAL | ABOVE |
| balance | BELOW, EQUAL | ABOVE |
| bin_lookup | AVAILABLE | UNAVAILABLE (деградация, не блокирует авторизацию) |
| card_management | AVAILABLE | UNAVAILABLE (блокирует авторизацию, 05/ISSUER_TIMEOUT) |

### Card-Management

| Поле | Валидные классы | Невалидные классы |
|---|---|---|
| operation | CREATE, GET, UPDATE, DELETE | — |
| pan_check | VALID (16 цифр, Luhn верен) | INVALID_LENGTH, INVALID_SYMBOLS |
| expiry_check | VALID | INVALID_FORMAT, EXPIRED |
| owner_check | KNOWN | UNKNOWN |
| card_status | NEW, ACTIVE, BLOCKED | EXPIRED (при operation=GET/UPDATE) |
| audit_mode | ENABLED, DISABLED | — (для GET всегда ENABLED, см. CMS-08) |

## 2. Граничные значения

### Authorization

| Граница | Условие валидности | Точки (OFF / ON) |
|---|---|---|
| availableBalance | amount ≤ availableBalance | B (ON, TC-AUTH-BV-01) / B+1 (OFF, TC-AUTH-BV-02) |
| dailyLimit | used + amount ≤ dailyLimit | L (ON, TC-AUTH-BV-03) / L+1 (OFF, TC-AUTH-BV-04) |
| expiryDate | expiry ≥ текущий месяц [M] | [M] (ON, TC-AUTH-BV-05) / [M-1] (OFF, TC-AUTH-BV-06) |

### Card-Management

| Граница | Условие валидности | Точки (OFF / ON) |
|---|---|---|
| Длина PAN | = 16 цифр | 15 (OFF, BV-01) / 16 (ON, BV-02) / 17 (OFF, BV-03) |
| Месяц expiryDate | 01 ≤ MM ≤ 12 | 12 (ON, BV-04) / 01 (ON, BV-05) / 13 (OFF, BV-06) |

## 3. Попарное тестирование

Инструмент: Microsoft PICT (`pict model.txt > cases.txt`). Наборы лежат в `pict/`.

### Authorization — `pict/model.txt` (8 параметров, 4 ограничения) → `pict/cases.txt`

Ограничения модели: bin_lookup=AVAILABLE влечёт issuer_source=BIN (и наоборот — SWITCH при UNAVAILABLE); card_management=UNAVAILABLE влечёт bin_lookup=AVAILABLE (запрос к CMS не доходит до обогащения по независимому пути); card_status=EXPIRED влечёт expiry_state=EXPIRED. Набор — **16 строк**, все допустимые пары значений покрыты. Каждая строка превращена в отдельный тест-кейс `TC-AUTH-PW-*` в разделе 4.1.3.

### Card-Management — `pict/model-card-management.txt` (7 параметров, 6 ограничений) → `pict/cases-card-management.txt`

Ограничения модели: operation=CREATE влечёт card_status=NEW; operation=DELETE влечёт owner_check=KNOWN; operation=UPDATE влечёт pan_check=VALID; operation=GET влечёт audit_mode=ENABLED; card_status=EXPIRED влечёт expiry_check=EXPIRED; request_source=BATCH исключает operation=DELETE. Набор — **19 строк**, все допустимые пары покрыты. Каждая строка превращена в отдельный тест-кейс `TC-CMS-PW-*` в разделе 4.2.3.

Воспроизведение: `cd docs/practice-2/pict && pict model.txt > cases.txt && pict model-card-management.txt > cases-card-management.txt`.

## 4. Тест-кейсы

Каждый кейс: ID, требование (раздел 0), вид, предусловие, шаги, ожидаемый результат, источник. Предусловие pairwise-кейсов — значения параметров модели через `/` в порядке, заданном в разделе 3; источник `cases.txt#N` / `cases-cm.txt#N` — номер строки набора.

### 4.1. Authorization

#### 4.1.1. Классы эквивалентности

| ID | Требование | Вид | Предусловие | Шаги | Ожидаемый результат | Источник |
|---|---|---|---|---|---|---|
| TC-AUTH-EC-01 | AUTH-01, AUTH-07 | поз. | K0 создана: ACTIVE, срок +3 года, лимиты и баланс с запасом | 1) POST /transactions amount=100000; 2) GET /cards/{pan} | APPROVED, 00; rrn/authCode валидны; баланс -100000 | card_status=ACTIVE |
| TC-AUTH-EC-02 | AUTH-01 | нег. | PAN корректного формата (16 цифр, Luhn верен), такой карты в CMS нет | 1) POST /transactions, pan=<несуществующий> | DECLINED, responseCode=14 | карта не найдена |
| TC-AUTH-EC-03 | AUTH-02 | нег. | K0: PATCH status=INACTIVE | 1) POST /transactions amount=100000 | DECLINED, CARD_INACTIVE | card_status=INACTIVE |
| TC-AUTH-EC-04 | AUTH-02 | нег. | K0: PATCH status=BLOCKED | 1) POST /transactions amount=100000 | DECLINED, CARD_BLOCKED | card_status=BLOCKED |
| TC-AUTH-EC-05 | AUTH-02, AUTH-03 | нег. | K0: PATCH status=EXPIRED (expiryDate остаётся в будущем) | 1) POST /transactions amount=100000 | DECLINED, 54 | card_status=EXPIRED |
| TC-AUTH-EC-06 | AUTH-03 | нег. | K0 ACTIVE; в БД expiryDate = [M-1] (прошлый месяц) | 1) POST /transactions amount=100000 | DECLINED, 54 | expiry_state=EXPIRED |
| TC-AUTH-EC-07 | AUTH-04 | нег. | K0: PATCH dailyLimit=99999 (< amount=100000) | 1) POST /transactions amount=100000 | DECLINED, 61 | daily_limit=ABOVE |
| TC-AUTH-EC-08 | AUTH-05 | нег. | K0: dailyLimit с запасом; monthlyLimit=99999 (< amount=100000) | 1) POST /transactions amount=100000 | DECLINED, 61 | monthly_limit=ABOVE |
| TC-AUTH-EC-09 | AUTH-06 | нег. | K0: PATCH availableBalance=99999 (< amount=100000); лимиты с запасом | 1) POST /transactions amount=100000 | DECLINED, 51; баланс не изменён | balance=ABOVE |
| TC-AUTH-EC-10 | AUTH-09 | нег. | K0 ACTIVE; Card Management остановлен: docker compose stop card-management | 1) POST /transactions amount=100000 | DECLINED, 05, ISSUER_TIMEOUT | card_management=UNAVAILABLE |
| TC-AUTH-EC-11 | AUTH-08 | поз. | K0 ACTIVE; Bin Lookup остановлен: docker compose stop bin-lookup | 1) POST /transactions amount=100000 | APPROVED, 00; issuerId из fallback-таблицы | bin_lookup=UNAVAILABLE |

#### 4.1.2. Граничные значения

| ID | Требование | Вид | Предусловие | Шаги | Ожидаемый результат | Источник |
|---|---|---|---|---|---|---|
| TC-AUTH-BV-01 | AUTH-06 | поз. | K0 (свежая карта): PATCH availableBalance=1000000; лимиты с запасом | 1) POST /transactions amount=1000000; 2) GET /cards | APPROVED, 00; баланс=0 | balance ON |
| TC-AUTH-BV-02 | AUTH-06 | нег. | K0 (та же карта, следующая транзакция): availableBalance=0 после BV-01 | 1) POST /transactions amount=1 | DECLINED, 51 | balance OFF |
| TC-AUTH-BV-03 | AUTH-04 | поз. | K0 (свежая карта): dailyLimit=15000000, использовано за день 0 | 1) POST /transactions amount=15000000 | APPROVED, 00 | dailyLimit ON |
| TC-AUTH-BV-04 | AUTH-04 | нег. | K0 (та же карта, следующая транзакция): daily_amount=15000000 после BV-03 | 1) POST /transactions amount=1, новый stan | DECLINED, 61 | dailyLimit OFF |
| TC-AUTH-BV-05 | AUTH-03 | поз. | K0 (свежая карта); [M] — текущий MMYY (на дату прогона); expiryDate задаётся в БД | 1) SQL expiry_date=[M]; 2) POST /transactions amount=100000 | APPROVED, 00 | expiryDate ON |
| TC-AUTH-BV-06 | AUTH-03 | нег. | K0 (свежая карта); expiryDate = [M-1] | 1) SQL expiry_date=[M-1]; 2) POST /transactions amount=100000 | DECLINED, 54 | expiryDate OFF |

#### 4.1.3. Попарное тестирование (по одному кейсу на строку `cases.txt`)

| ID | Требование | Вид | Предусловие | Шаги | Ожидаемый результат | Источник |
|---|---|---|---|---|---|---|
| TC-AUTH-PW-01 | AUTH-02 | нег. | INACTIVE/[M]/above/above/equal/bin-/cms+ | 1) POST /transactions amount=100000 | DECLINED, CARD_INACTIVE | cases.txt#1 |
| TC-AUTH-PW-02 | AUTH-09 | нег. | INACTIVE/[M-1]/equal/equal/below/bin+/cms- | 1) POST /transactions amount=100000 | DECLINED, responseCode=05, declineReason=ISSUER_TIMEOUT (Card Management недоступен, дальнейшие шаги не выполняются) | cases.txt#2 |
| TC-AUTH-PW-03 | AUTH-09 | нег. | ACTIVE/+3г/below/below/equal/bin+/cms- | 1) POST /transactions amount=100000 | DECLINED, responseCode=05, declineReason=ISSUER_TIMEOUT (Card Management недоступен, дальнейшие шаги не выполняются) | cases.txt#3 |
| TC-AUTH-PW-04 | AUTH-02, AUTH-03 | нег. | EXPIRED/[M-1]/equal/below/above/bin-/cms+ | 1) POST /transactions amount=100000 | DECLINED, 54 | cases.txt#4 |
| TC-AUTH-PW-05 | AUTH-02, AUTH-03 | нег. | EXPIRED/[M-1]/above/equal/above/bin+/cms+ | 1) POST /transactions amount=100000 | DECLINED, 54 | cases.txt#5 |
| TC-AUTH-PW-06 | AUTH-02 | нег. | BLOCKED/+3г/below/equal/below/bin-/cms+ | 1) POST /transactions amount=100000 | DECLINED, CARD_BLOCKED | cases.txt#6 |
| TC-AUTH-PW-07 | AUTH-09 | нег. | EXPIRED/[M-1]/below/above/equal/bin+/cms- | 1) POST /transactions amount=100000 | DECLINED, responseCode=05, declineReason=ISSUER_TIMEOUT (Card Management недоступен, дальнейшие шаги не выполняются) | cases.txt#7 |
| TC-AUTH-PW-08 | AUTH-09 | нег. | BLOCKED/[M]/equal/above/above/bin+/cms- | 1) POST /transactions amount=100000 | DECLINED, responseCode=05, declineReason=ISSUER_TIMEOUT (Card Management недоступен, дальнейшие шаги не выполняются) | cases.txt#8 |
| TC-AUTH-PW-09 | AUTH-04 | нег. | ACTIVE/[M]/above/above/below/bin-/cms+ | 1) POST /transactions amount=100000 | DECLINED, responseCode=61 (превышен дневной лимит); баланс не изменён | cases.txt#9 |
| TC-AUTH-PW-10 | AUTH-09 | нег. | BLOCKED/[M]/above/below/equal/bin+/cms- | 1) POST /transactions amount=100000 | DECLINED, responseCode=05, declineReason=ISSUER_TIMEOUT (Card Management недоступен, дальнейшие шаги не выполняются) | cases.txt#10 |
| TC-AUTH-PW-11 | AUTH-02 | нег. | INACTIVE/[M]/below/equal/above/bin-/cms+ | 1) POST /transactions amount=100000 | DECLINED, CARD_INACTIVE | cases.txt#11 |
| TC-AUTH-PW-12 | AUTH-09 | нег. | INACTIVE/+3г/equal/above/above/bin+/cms- | 1) POST /transactions amount=100000 | DECLINED, responseCode=05, declineReason=ISSUER_TIMEOUT (Card Management недоступен, дальнейшие шаги не выполняются) | cases.txt#12 |
| TC-AUTH-PW-13 | AUTH-02, AUTH-03 | нег. | EXPIRED/[M-1]/above/below/below/bin+/cms+ | 1) POST /transactions amount=100000 | DECLINED, 54 | cases.txt#13 |
| TC-AUTH-PW-14 | AUTH-02 | нег. | BLOCKED/[M-1]/equal/equal/equal/bin-/cms+ | 1) POST /transactions amount=100000 | DECLINED, CARD_BLOCKED | cases.txt#14 |
| TC-AUTH-PW-15 | AUTH-02, AUTH-03 | нег. | ACTIVE/[M-1]/equal/equal/above/bin+/cms+ | 1) POST /transactions amount=100000 | DECLINED, 54 | cases.txt#15 |
| TC-AUTH-PW-16 | AUTH-09 | нег. | INACTIVE/+3г/above/below/above/bin+/cms- | 1) POST /transactions amount=100000 | DECLINED, responseCode=05, declineReason=ISSUER_TIMEOUT (Card Management недоступен, дальнейшие шаги не выполняются) | cases.txt#16 |

### 4.2. Card-Management

#### 4.2.1. Классы эквивалентности

| ID | Требование | Вид | Предусловие | Шаги | Ожидаемый результат | Источник |
|---|---|---|---|---|---|---|
| TC-CMS-EC-01 | CMS-01 | поз. | Произвольное состояние БД | 1) POST /cards bin=400000, dailyLimit=15000000, monthlyLimit=300000000, balance=100000000; 2) GET /cards/{pan} | 201; PAN валиден по Луну, status=NEW/ACTIVE, expiryDate=[M]+3г | operation=CREATE |
| TC-CMS-EC-02 | CMS-02 | поз. | Карта K0 существует, status=ACTIVE | 1) GET /cards/{pan} | 200; status=ACTIVE | operation=GET |
| TC-CMS-EC-03 | CMS-02, CMS-05 | нег. | В БД нет карты с заданным PAN (корректный формат) | 1) GET /cards/{несуществующий PAN} | 404 | карта не найдена |
| TC-CMS-EC-04 | CMS-05 | нег. | Карта K0 существует | 1) GET /cards/{PAN, 15 цифр} | 4xx | pan_check=INVALID_LENGTH |
| TC-CMS-EC-05 | CMS-05 | нег. | Карта K0 существует | 1) GET /cards/4000001234ABCD01 | 4xx, без 500 | pan_check=INVALID_SYMBOLS |
| TC-CMS-EC-06 | CMS-03 | поз. | Карта K0 ACTIVE | 1) PATCH /cards/{pan} status=BLOCKED; 2) GET /cards/{pan} | 200; статус изменён, остальное прежнее | operation=UPDATE |
| TC-CMS-EC-07 | CMS-04 | поз. | Карта K0 ACTIVE, владелец известен | 1) DELETE /cards/{pan}; 2) GET /cards/{pan} | 200/204, затем 404; status=DELETED | operation=DELETE |
| TC-CMS-EC-08 | CMS-04, CMS-07 | нег. | Карта существует, но владелец в запросе не совпадает с владельцем карты (owner_check=UNKNOWN) | 1) DELETE /cards/{pan} от неизвестного владельца | 404; карта не удалена | owner_check=UNKNOWN |
| TC-CMS-EC-09 | CMS-06 | нег. | Произвольное состояние БД | 1) POST /cards, expiryDate=1399 | 4xx | expiry_check=INVALID_FORMAT |
| TC-CMS-EC-10 | CMS-06 | нег. | Карта K0 ACTIVE | 1) SQL expiry_date=[M-1]; 2) GET /cards/{pan} | 200; status=EXPIRED | expiry_check=EXPIRED |
| TC-CMS-EC-11 | CMS-08 | поз. | Карта K0 существует | 1) GET /cards/{pan}; 2) проверить аудит-лог | 200; запись в аудит-логе есть | audit_mode=ENABLED |

#### 4.2.2. Граничные значения

| ID | Требование | Вид | Предусловие | Шаги | Ожидаемый результат | Источник |
|---|---|---|---|---|---|---|
| TC-CMS-BV-01 | CMS-05 | нег. | Карта K0 существует, PAN — 16 цифр | 1) GET /cards/{PAN без 1 цифры} | 4xx | PAN len 15 |
| TC-CMS-BV-02 | CMS-05 | поз. | Карта K0 существует | 1) GET /cards/{PAN, 16 цифр} | 200 | PAN len 16 |
| TC-CMS-BV-03 | CMS-05 | нег. | Карта K0 существует | 1) GET /cards/{PAN+"0"}, 17 цифр | 4xx | PAN len 17 |
| TC-CMS-BV-04 | CMS-06 | поз. | Произвольное состояние БД; дата создания подменена на 31.12.YYYY | 1) POST /cards {...} | 201; expiryDate MM=12, год+3 | MM=12 |
| TC-CMS-BV-05 | CMS-06 | поз. | Произвольное состояние БД; дата создания подменена на 01.01.YYYY | 1) POST /cards {...} | 201; expiryDate MM=01, год+3 | MM=01 |
| TC-CMS-BV-06 | CMS-06 | нег. | Произвольное состояние БД | 1) POST /cards, expiryDate=1301 | 4xx | MM=13 |

#### 4.2.3. Попарное тестирование (по одному кейсу на строку `cases-card-management.txt`)

| ID | Требование | Вид | Предусловие | Шаги | Ожидаемый результат | Источник |
|---|---|---|---|---|---|---|
| TC-CMS-PW-01 | CMS-02, CMS-05 | нег. | GET/ACTIVE/15циф/expired/unknown/API | 1) GET /cards/{pan} | 4xx; карта не изменена | cases-cm.txt#1 |
| TC-CMS-PW-02 | CMS-03 | поз. | UPDATE/EXPIRED/valid/expired/known/BATCH | 1) PATCH /cards/{pan} status=BLOCKED | 200; поле обновлено, status->EXPIRED; без аудита | cases-cm.txt#2 |
| TC-CMS-PW-03 | CMS-02, CMS-05 | нег. | GET/NEW/буквы/valid/known/ADMIN_PANEL | 1) GET /cards/{pan} | 4xx; карта не изменена | cases-cm.txt#3 |
| TC-CMS-PW-04 | CMS-02, CMS-07 | нег. | GET/BLOCKED/valid/bad/unknown/BATCH | 1) GET /cards/{pan} | 404 (владелец не известен) | cases-cm.txt#4 |
| TC-CMS-PW-05 | CMS-04, CMS-05 | нег. | DELETE/ACTIVE/15циф/bad/known/ADMIN_PANEL | 1) DELETE /cards/{pan} | 4xx; карта не изменена | cases-cm.txt#5 |
| TC-CMS-PW-06 | CMS-02, CMS-05 | нег. | GET/EXPIRED/буквы/expired/unknown/API | 1) GET /cards/{pan} | 4xx; карта не изменена | cases-cm.txt#6 |
| TC-CMS-PW-07 | CMS-03, CMS-07 | нег. | UPDATE/NEW/valid/valid/unknown/API | 1) PATCH /cards/{pan} status=BLOCKED | 404 (владелец не известен) | cases-cm.txt#7 |
| TC-CMS-PW-08 | CMS-04, CMS-05 | нег. | DELETE/ACTIVE/буквы/valid/known/ADMIN_PANEL | 1) DELETE /cards/{pan} | 4xx; карта не изменена | cases-cm.txt#8 |
| TC-CMS-PW-09 | CMS-04 | поз. | DELETE/EXPIRED/valid/expired/known/ADMIN_PANEL | 1) DELETE /cards/{pan} | 200/204; status=DELETED (мягкое удаление); запись в аудит | cases-cm.txt#9 |
| TC-CMS-PW-10 | CMS-01, CMS-05 | нег. | CREATE/NEW/15циф/valid/known/BATCH | 1) POST /cards {...} | 4xx; карта не изменена | cases-cm.txt#10 |
| TC-CMS-PW-11 | CMS-04, CMS-05 | нег. | DELETE/BLOCKED/буквы/valid/known/API | 1) DELETE /cards/{pan} | 4xx; карта не изменена | cases-cm.txt#11 |
| TC-CMS-PW-12 | CMS-01, CMS-07 | нег. | CREATE/NEW/valid/expired/unknown/ADMIN_PANEL | 1) POST /cards {...} | 404 (владелец не известен) | cases-cm.txt#12 |
| TC-CMS-PW-13 | CMS-04, CMS-05 | нег. | DELETE/EXPIRED/15циф/expired/known/API | 1) DELETE /cards/{pan} | 4xx; карта не изменена | cases-cm.txt#13 |
| TC-CMS-PW-14 | CMS-03 | поз. | UPDATE/ACTIVE/valid/bad/known/BATCH | 1) PATCH /cards/{pan} status=BLOCKED | 200; поле обновлено; запись в аудит | cases-cm.txt#14 |
| TC-CMS-PW-15 | CMS-03, CMS-07 | нег. | UPDATE/BLOCKED/valid/expired/unknown/ADMIN_PANEL | 1) PATCH /cards/{pan} status=BLOCKED | 404 (владелец не известен) | cases-cm.txt#15 |
| TC-CMS-PW-16 | CMS-01, CMS-05 | нег. | CREATE/NEW/буквы/bad/unknown/API | 1) POST /cards {...} | 4xx; карта не изменена | cases-cm.txt#16 |
| TC-CMS-PW-17 | CMS-01, CMS-05 | нег. | CREATE/NEW/буквы/expired/known/BATCH | 1) POST /cards {...} | 4xx; карта не изменена | cases-cm.txt#17 |
| TC-CMS-PW-18 | CMS-04, CMS-05 | нег. | DELETE/NEW/15циф/expired/known/API | 1) DELETE /cards/{pan} | 4xx; карта не изменена | cases-cm.txt#18 |
| TC-CMS-PW-19 | CMS-04, CMS-05 | нег. | DELETE/BLOCKED/15циф/valid/known/API | 1) DELETE /cards/{pan} | 4xx; карта не изменена | cases-cm.txt#19 |
