# Task 8: промежуточная передача 0.10.0

Точка 2026-10-30, ветка phase1. Пользователь запросил push текущей работы и обновление README.
Task 8 еще не принят. Последняя принятая основа - 0.9.1, Tasks 1-7. Полная цель до ракеты и войны остается открытой.

## Что добавлено

- RailCargoAccess связывает настоящие манипуляторы с canonical cargo/fuel slots владельца RailwaySavedData. Сохраняются компоненты, фильтры, bar и лимиты; старые ссылки проверяются после split, замены owner и движения. Приоритет остается у существующего block capability.
- RailDockGeometry дает общую геометрию для поиска и preview: точный объем кузова для предметов и три конечных якоря сопла для помп. Допуск 0.0625 не увеличен, arrival не менялся. Каждый connector проверяет собственный прямой cardinal участок пути; частичная кривая не запрещает остальные допустимые connectors.
- RailPumpDock подключает настоящие помпы к цистернам. Максимум три подключения на вагон с обоих бортов, один connector на port, сохранение действующих подключений и освобождение при отсутствии питания. Один общий бюджет 600 x satisfaction на помпу за Minecraft tick, включая оба порта.
- Допускается стоящий MANUAL либо AUTO WAIT_STATION, включая временную цель без физической станции. Перенос жидкости проверяет тип, объем и свободное место до извлечения.

Это адаптация геометрии Minecraft и существующей модели жидкостей. Закрытый native latch algorithm и точное поведение сочленений Factorio не воспроизведены и не заявляются.

## Проверки checkpoint 49c7510

Полный unit-прогон: 763 теста, 0 failures/errors/skipped, BUILD SUCCESSFUL за 29 с.
GameTest проверяют настоящие сундуки, манипуляторы, помпы, трубы, баки, вагоны и серверные тики.
Проверены заряженный пистолет и компоненты предметов, сохранение количества на каждом тике,
bar/filter, три слота топлива, устаревшие endpoints, AUTO arrival, временная цель, геометрические
границы, частичная кривая, обратная перекачка, общий бюджет обоих портов и неполное питание.

Итог checkpoint-прогона: 430 из 431 обязательного GameTest прошли, серверные тесты заняли 45.08 с,
вся команда завершилась ошибкой за 66 с. Падает actualSignalStoppedAutoRefusesInserterLoading.
Обычный build прошел за 28 с, собран JAR с версией 0.10.0. В сообщении упавшего теста также превышен
лимит сериализации 1024 символа; это не отменяет зарегистрированный required failure.

Воспроизведение из корня репозитория с JDK 21:

```powershell
.\gradlew.bat test --console=plain
$env:MINETORIO_TASK7_GT_DIR = Join-Path (Get-Location) 'build/railway-verification/loading-checkpoint'
.\gradlew.bat --init-script tools/railway-verification.init.gradle runGameTestServer --console=plain
.\gradlew.bat build --console=plain
```

Команды выполнять последовательно. GameTest сейчас ожидаемо завершается ошибкой указанного сценария;
успешная сборка JAR не означает успешный GameTest. На этой машине дополнительно использовался локальный
Maven mirror init-script для загрузки зависимостей. Он не нужен как обязательная часть проекта.
Автоматические fixtures не используют личные сохранения. Визуальный плейтест пропущен по указанию пользователя.

## Исправления после checkpoint

После checkpoint 49c7510 исправлена граница красного сигнала: TrainMotion ограничивает фактический
шаг текущим authority.remaining до построения sweep, а owner commit отклоняет превышение до расхода
топлива. Допуски планирования и shared-initial-interior policy сохранены. Новый TrainSignalBoundaryTest
до исправления дал 3 падения из 6; после исправления все 77 покрывающих core-тестов прошли за 21 с.
Проверены forward/reverse, каждый подшаг sweep/owner, точная граница, освобождение блока и штатный
departure, отказ прямого commit до fuel callback и разрешенный shared start. Независимое scoped
ревью APPROVED, Critical/Important не найдено. Команда:

```powershell
.\gradlew.bat test --tests dev.minetorio.core.rail.TrainSignalBoundaryTest --tests dev.minetorio.core.rail.TrainReservationsTest --tests dev.minetorio.core.rail.TrainDispatcherTest --tests dev.minetorio.core.rail.TrainMotionTest --console=plain
```

После этого full world прогон дал 429 из 431: настоящий WAIT_SIGNAL и отказ погрузки прошли,
но повторились два NO_PATH у physical и temporary arrival. Точный snapshot: leading.distance
1.500000000000007 при target.distance 1.5 на том же traversal. Изолированный replay на старом JAR
0.9.1 подтвердил: без смены revision следующий подшаг дает speed0/WAIT_STATION; replaceRails
с неизмененной геометрией перед ним сбрасывает assignment, и router отвергает цель чуть позади.
Это отдельный подтвержденный дефект, не устраненный одним исправлением сигнала.

Затем исправлен terminal target overrun: TrainDispatcher.move ограничивает terminal limit свежим
геометрическим остатком до той же направленной route occurrence. Кратчайший маршрут не подменяет
назначенную петлю, global revision и behind-goal проверки сохранены. TrainTargetBoundaryTest до
исправления дал 6 падений из 7, после исправления все 7 прошли. Покрыты physical/temporary,
forward/reverse, смена revision между terminal и braking, настоящий curve prefix перед прямой
остановкой, цель позади и удаление target rail. Все 182 railway core tests прошли, scoped review APPROVED.

На объединенном snapshot с новыми ITEM-тестами: 777 unit, 0 failures/errors/skipped; все 436 required
GameTest за 47.84 с (команда 67 с); build за 29 с. Версия внутри JAR - 0.10.0,
SHA-256 `377e77fba7fd146c98661a575a9286ceb6536596de5c5066780b8879df6a2ffd`.
Контроллер отдельно проверил XML, raw GT log, внутреннюю версию и SHA JAR. Это еще не приемка Task 8.

```powershell
.\gradlew.bat test --tests 'dev.minetorio.core.rail.*' --console=plain
```

## Что проверить дальше

1. Оба подтвержденных численных дефекта исправлены в указанном выше объеме. Не отменять regression tests ради последующих изменений и не заменять штатное прибытие принудительным WAIT_STATION.
2. Добавить задержку подключения помпы до переноса. Текущий bridge переносит сразу; native эксперимент причинно связал задержку со скоростью connector: 64 native ticks после первого питания при default 1/64. Mapping на Minecraft 20 Hz, удержание/сброс подключения и чистоту preview нужно явно определить и проверить до приемки. [Native измерение](factorio-1.1.110-pump-connector-timing.md).
3. Закончить gate stationary endpoint на том же port и финальное ревью жидкостей/Task 8, затем повторить полную проверку после warmup. ITEM review нашел тест, который игнорировал отказ commitMotion для AUTO и дважды проверял NO_SCHEDULE вместо WAIT_SIGNAL/NO_PATH. Исправление заменило его настоящим NO_PATH producer с сохранной геометрией, свежими и захваченными endpoints. Добавлены same-tick speed0 pose freshness, геометрические границы всех направлений, reversed и speed epsilon, nearest/train UUID/car UUID tie с проверкой canonical slots. Все новые world tests прошли; scoped ITEM re-review APPROVED, исходный I1 и три пробела ADDRESSED, новых замечаний нет.
4. Затем Task 9: интерфейсы, включая настоящий menu shift-click заряженного пистолета; Task 10: чертежи; Task 11: полная матрица restart с движением, пассажиром, chain, split, reverse и fluid-in-flight.

Подробные согласованные правила: [план железной дороги](../superpowers/plans/2026-10-29-minetorio-railway.md).
Native условия питания, остановки и трех подключений: [pump predicates](factorio-1.1.110-pump-predicates.md).
