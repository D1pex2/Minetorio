# Task 8: погрузка поездов, версия 0.10.0

Принят 2026-10-30 в phase1 после независимых ревью ITEM, FLUID и приемочных сценариев. Предыдущие Tasks 1-7 сохраняются. Железнодорожный блок целиком и общая цель до ракеты и войны еще не завершены.

## Реальное поведение

Существующие манипуляторы загружают и разгружают настоящие cargo/fuel slots владельца RailwaySavedData. Сохраняются компоненты предметов, фильтры, bar и фактические лимиты стека, включая заряженный пистолет. Используются точный объем кузова и свежая серверная поза, без зависимости от загруженной entity вагона. Смена owner, split, удаление и движение не позволяют старому endpoint перенести предмет в устаревшее хранилище. Существующий block capability имеет приоритет, даже если он заполнен или отказывается принимать предмет.

Помпы обслуживают настоящие цистерны. Допустим стоящий MANUAL либо AUTO WAIT_STATION, включая temporary target без физической станции. WAIT_SIGNAL, NO_PATH и NO_SCHEDULE не допускаются. Каждый connector проверяет собственную STRAIGHT cardinal occurrence; частичная кривая под другим участком вагона не запрещает допустимый прямой connector. Общая runtime/preview геометрия использует три конечных якоря сопла и Euclidean tolerance 0.0625, без увеличения допуска или изменения station target.

На цистерну одновременно выбирается не более трех подключений с обоих бортов, один side/connector не дублируется и один port не обслуживает два car. Валидные подключения сохраняют приоритет. Потеря питания освобождает место; восстановленный конкурент не вытесняет три удержанных подключения. Пустой источник, полный приемник или несовместимая жидкость сами по себе не сбрасывают выбранное подключение.

Новое powered подключение ждет 64 rail ticks от фактического runtime selection. При штатных трех подшагах первая дискретная возможность передачи наступает через 22 Minecraft ticks, то есть 66 rail ticks. Удержанное допустимое подключение сохраняет время начала при небольшой смене позы внутри того же якоря. Потеря eligibility/selection, питания, смена owner/car/BE/consist/connector/anchor, NBT load и lifecycle сбрасывают ожидание. Наблюденное runtime нарушение нельзя отменить восстановлением условий в том же тике. Preview и диагностический snapshot не меняют таймер.

Общий rail budget помпы - 600 x satisfaction за Minecraft tick, включая оба rail ports и повторные вызовы transfer. Перезагрузка подключения не возвращает уже потраченный бюджет. Обычный stationary endpoint имеет приоритет на своем port; прежний путь network.tick сохранен. Нормальное питание source600 -> pump -> rail output600 допустимо. До извлечения проверяются finite/positive значения, зарегистрированный fluid id, совместимость и свободное место; обе стороны меняются ровно на принятое количество. Inactivity обновляется только при фактическом переносе.

## Доказательства и проверка

- Исходный world RED: настоящий сундук и inserter не находили cargo; настоящий pump/pipe/power оставлял цистерну пустой. Обычный station arrival выявил исходную ошибку nozzle formula на 0.5 при допуске 0.0625. Исправлен конечный 3D-адаптер, station target не подгонялся под помпу.
- Warmup RED: при прежнем immediate transfer помпа перенесла 600 при elapsed=0; остальные 436 GameTest прошли. Новый отдельный batch проверяет zero на каждом раннем тике, timestamp первого реального transfer, delta66 rail и delta22 Minecraft, а также обычную cadence без ручных тиков чужих fixtures.
- Проверены непрерывная сохранность 25000 water туда и обратно через реальные трубы/баки/помпы; два вагона на противоположных портах одной помпы; full600/half300; cap3/history/pending claims; retained таймер, power/eligibility/anchor/lifecycle/NBT/identity resets, старый supplier и отдельная disk SavedData replacement. No-mix/full-target/unknown-id assertions выполняются после готовности. Negative/NaN/Infinity арифметические входы проверены pure transferAmount, это не инъекция поврежденного world box.
- Geometry gates используют actual selection/flow в четырех cardinal направлениях с reversed case. Совпадающий CURVE/DIAGONAL nozzle отвергается по форме, а не из-за старого места помпы; DIAGONAL имеет настоящее отклонение около 0.06066 в допуске. Два canonical cars конкурируют за один port, жидкость получает только выбранный. Same-port stationary priority дополнительно проверен явно synthetic fixture с logical rail под настоящей трубой; он не объявляется обычным player placement.
- ITEM review исправил ложный тест статусов: public commitMotion для AUTO возвращал false. Теперь NO_PATH создается настоящим dispatcher при сохранной геометрии, WAIT_SIGNAL достигается движением к красному сигналу. Добавлены same-tick speed0 pose refresh, границы OBB, reversed/speed epsilon, nearest и UUID tie с проверкой canonical slots. Независимое повторное ревью: все замечания ADDRESSED.

Итоговый frozen snapshot: 777 unit, 0 failures/errors/skipped; 454 required GameTest за 50.18 с, вся команда 72 с; обычный build за 29 с. Контроллер отдельно проверил XML, raw GameTest log, внутреннюю версию и SHA-256 JAR.

Артефакт: `build/libs/minetorio-0.10.0.jar`, 3229073 bytes, внутренняя версия 0.10.0. SHA-256: `62c60fbff5601f1736822d4043d753389ca4434080f90040a61bba8d09dc05c5`.

Из корня репозитория, с JDK 21, последовательно:

```powershell
.\gradlew.bat test --console=plain
$env:MINETORIO_TASK7_GT_DIR = Join-Path (Get-Location) 'build/railway-verification/loading'
.\gradlew.bat --init-script tools/railway-verification.init.gradle runGameTestServer --console=plain
.\gradlew.bat build --console=plain
Remove-Item Env:MINETORIO_TASK7_GT_DIR
```

На этой машине дополнительно использовался локальный Maven mirror init-script для зависимостей. GameTest выполнялись в отдельном профиле, личные сохранения не использовались. Команды выше не требуют локальных отчетов .superpowers; Linux/macOS используют ./gradlew и обычное присваивание переменной окружения.

## Численные исправления ядра

Новые реальные loading fixtures выявили два прежних дефекта на неизмененном JAR 0.9.1. Перед красным сигналом допустимая расчетная погрешность давала движение за boundary на 2^-48, после чего ошибочно включалось разрешение initial-interior. Теперь TrainMotion ограничивает фактический шаг authority.remaining до sweep, owner commit отклоняет превышение до расхода топлива. Общие допуски планирования и native shared-start policy сохранены.

Накопленный Visit.remaining также расходился с actual leading на несколько ULP у station target. Без graph edit следующий тормозной шаг давал штатное ожидание; unrelated revision change между ними вызывал NO_PATH для цели чуть позади. TrainDispatcher ограничивает terminal prefix свежей направленной геометрией той же route occurrence. Behind-goal/loops/revision guards не ослаблены. Regression проверяет каждый подшаг, physical/temporary, forward/reverse, curve prefix, смену revision, освобождение блока и расход ресурсов. Оба исправления прошли отдельные независимые ревью.

## Границы приемки и продолжение

Native условия и timing подтверждены собственными headless экспериментами [pump predicates](factorio-1.1.110-pump-predicates.md) и [connector timing](factorio-1.1.110-pump-connector-timing.md). Fixed64 при любом finite positive питании, history выбора и lifecycle reset являются явными адаптациями. Native half-power animation, закрытый latch algorithm и rigid angular behavior не заявляются измеренными. Warmup данные производные, в NBT не добавлены.

Fractional Y boundary 0.0625 покрыта общей pure geometry; integer rail-plane и block pumpY в мире дают совпавший Y либо Y+1. Lifecycle/NBT tests не заменяют process restart. Полная матрица moving/passenger wait/chain/split/reverse/fluid-in-flight остается Task 11. Температуру FluidBox требуется провести до атомного блока. Визуальный плейтест пропущен по указанию пользователя.

Далее Task 9: меню вагонов, станций и расписаний, server authority/revisions и настоящий menu shift-click заряженного пистолета; затем Task 10 чертежи и Task 11 полная приемка. Подробные правила: [план железной дороги](../superpowers/plans/2026-10-29-minetorio-railway.md). Полная поздняя фабрика, ракета и вся военная часть остаются активной целью.
