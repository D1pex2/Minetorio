# Task 8: промежуточная передача 0.10.0

Точка 2026-10-30, ветка phase1. Пользователь запросил push текущей работы и обновление README.
Task 8 еще не принят. Последняя принятая основа - 0.9.1, Tasks 1-7. Полная цель до ракеты и войны остается открытой.

## Что добавлено

- RailCargoAccess связывает настоящие манипуляторы с canonical cargo/fuel slots владельца RailwaySavedData. Сохраняются компоненты, фильтры, bar и лимиты; старые ссылки проверяются после split, замены owner и движения. Приоритет остается у существующего block capability.
- RailDockGeometry дает общую геометрию для поиска и preview: точный объем кузова для предметов и три конечных якоря сопла для помп. Допуск 0.0625 не увеличен, arrival не менялся. Каждый connector проверяет собственный прямой cardinal участок пути; частичная кривая не запрещает остальные допустимые connectors.
- RailPumpDock подключает настоящие помпы к цистернам. Максимум три подключения на вагон с обоих бортов, один connector на port, сохранение действующих подключений и освобождение при отсутствии питания. Один общий бюджет 600 x satisfaction на помпу за Minecraft tick, включая оба порта.
- Допускается стоящий MANUAL либо AUTO WAIT_STATION, включая временную цель без физической станции. Перенос жидкости проверяет тип, объем и свободное место до извлечения.

Это адаптация геометрии Minecraft и существующей модели жидкостей. Закрытый native latch algorithm и точное поведение сочленений Factorio не воспроизведены и не заявляются.

## Проверки

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

## Что исправить и проверить дальше

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
Это отдельный подтвержденный дефект, не устраненный исправлением сигнала. Итоговый успешный build
выше относится к checkpoint, новый JAR с этим исправлением пока не заявляется.

1. Исправление границы красного сигнала выполнено в указанном выше объеме. Далее устранить подтвержденный terminal target overrun: ограничивать остаток свежей геометрией той же route occurrence, сохранив строгие правила настоящей цели позади, loops и revision guards. Нужен deterministic regression с global revision change между terminal movement и braking step, physical/temporary, forward/reverse. Не заменять исправление принудительным WAIT_STATION.
2. Добавить задержку подключения помпы до переноса. Текущий bridge переносит сразу; native эксперимент причинно связал задержку со скоростью connector: 64 native ticks после первого питания при default 1/64. Mapping на Minecraft 20 Hz, удержание/сброс подключения и чистоту preview нужно явно определить и проверить до приемки. [Native измерение](factorio-1.1.110-pump-connector-timing.md).
3. Закончить gate stationary endpoint на том же port, независимое ревью Task 8 и полный успешный unit/GameTest/build. ITEM review нашел тест, который игнорировал отказ commitMotion для AUTO и дважды проверял NO_SCHEDULE вместо WAIT_SIGNAL/NO_PATH; требуется настоящий NO_PATH producer и проверка фактического статуса. Также дополняются доказательства same-tick pose freshness, геометрических границ и nearest/UUID tie.
4. Затем Task 9: интерфейсы, включая настоящий menu shift-click заряженного пистолета; Task 10: чертежи; Task 11: полная матрица restart с движением, пассажиром, chain, split, reverse и fluid-in-flight.

Подробные согласованные правила: [план железной дороги](../superpowers/plans/2026-10-29-minetorio-railway.md).
Native условия питания, остановки и трех подключений: [pump predicates](factorio-1.1.110-pump-predicates.md).
