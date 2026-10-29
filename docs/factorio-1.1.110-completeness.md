# Контракт полноты Factorio 1.1.110

Дата аудита: 2026-10-29. Ветка: `phase1`. Это снимок исходного состояния перед реализацией первого боя, а не отчет о завершении плана. Проверены исходники и первичные данные; Gradle и клиент в рамках этого аудита не запускались. Последующие изменения должны обновлять строки состояния и добавлять фактические проверки.

## Граница и источники

Цель: вся игровая прогрессия базовой Factorio 1.1.110 до ракеты, продолжение с космической наукой и бесконечными исследованиями, полная военная часть, враги и экология. Spidertron относится к 1.1 и входит в перечень. Старое исключение врагов отменено. Сценарии обучения, достижения, редакторские сущности и дословное воспроизведение графики не являются производственной прогрессией.

Основной источник: [официальный тег wube/factorio-data/1.1.110](https://github.com/wube/factorio-data/tree/1.1.110). Ссылки закреплены на тег, не на изменяемый master.

- [Рецепты](https://github.com/wube/factorio-data/blob/1.1.110/base/prototypes/recipe.lua).
- [Технологии и их эффекты](https://github.com/wube/factorio-data/blob/1.1.110/base/prototypes/technology.lua).
- [Предметы, оружие, боеприпасы, ограничения модулей](https://github.com/wube/factorio-data/blob/1.1.110/base/prototypes/item.lua).
- [Кусаки, плеваки и гнезда](https://github.com/wube/factorio-data/blob/1.1.110/base/prototypes/entity/enemies.lua).
- [Турели и черви](https://github.com/wube/factorio-data/blob/1.1.110/base/prototypes/entity/turrets.lua).
- [Основные сущности, включая персонажа](https://github.com/wube/factorio-data/blob/1.1.110/base/prototypes/entity/entities.lua).
- [Оборудование](https://github.com/wube/factorio-data/blob/1.1.110/base/prototypes/equipment.lua), [сетки](https://github.com/wube/factorio-data/blob/1.1.110/base/prototypes/equipment-grid.lua).
- [Настройки загрязнения, эволюции и групп](https://github.com/wube/factorio-data/blob/1.1.110/base/prototypes/map-settings.lua).

Lua-прототипы задают данные, но не раскрывают весь код движка. Для формул сопротивления при уроне ниже плоского снижения, точного ИИ, выбора боеприпаса и других неявных правил нужны отдельные подтверждения и проверки. Наличие числа в таблице не доказывает работу механики.

Согласованные адаптации: сегменты жидкостей по модели 2.0, инвентарь шириной 10 колонок, трехмерный вид от первого лица, собственные изображения/модели/звуки. В старом плане stages2-3 прямо закреплены обычные рецепты (`normal`). Дорогие рецепты (`expensive`) сохраняются в сравнении, но поддержка переключаемого дорогого режима здесь не объявляется утвержденной.

## Формат доказательства полноты

Для каждого игрового ID вести строку: тип/ID оригинала; первичный файл и строки; обычные/дорогие входы и выходы; время/категория; начальная доступность и исследование; точные эффекты/параметры; ID и файлы реализации; серверная интеграция; сохранение; автоматический тест; клиентский сценарий; статус и известное отличие.

Допустимые статусы: отсутствует; только данные/предмет; реализовано частично; проверено автоматически; проверено в клиенте; согласованное отличие; служебное вне прогрессии. Слово "готово" допустимо только после проверки доступности в выживании и всех требуемых эффектов.

Нельзя считать наличие рецепта реализацией сущности, наличие предмета реализацией науки, а тест данных проверкой пользовательского сценария. Все отсутствующие зависимости должны оставаться заблокированными.

## Единицы и точный первый бой

`Units.TICKS_PER_SECOND` в проекте равен 20; в Factorio 60 тиков/с. Время `energy_required` рецепта и `unit.time` исследования задается в секундах. Боевые cooldown, healing_per_tick и movement_speed из прототипов относятся к тикам Factorio. Для боя требуется явное преобразование этих величин.

Текущая шкала персонажа: 250 HP Factorio = 20 HP Minecraft; коэффициент 0,08. Его задает [CharacterRules.java](../src/main/java/dev/minetorio/core/player/CharacterRules.java). Держать расчеты урона и сопротивлений в единицах Factorio, преобразовывать при передаче в здоровье сущности. В HUD показывать согласованную шкалу.

| Параметр | Factorio 1.1.110 | Перевод/оговорка для Minetorio |
|---|---|---|
| Пистолет | bullet, дальность 15, cooldown 15, movement_slow_down_factor 0,2, стек 5 | 0,25 с; 5 MC тиков |
| Автомат | bullet, дальность 18, cooldown 6, movement_slow_down_factor 0,7, стек 5 | 0,1 с; 2 MC тика |
| Обычный магазин | magazine_size 10, physical damage 5, instant delivery, стек 200 | 0,4 MC HP за выстрел до сопротивления |
| Маленький кусака | max_health 15; attack damage 7; cooldown 35; cooldown_deviation 0,15 | 1,2 MC HP; удар 0,56 MC HP; период 7/12 с до отклонения |
| Дистанция укуса | range 0,5, bounding-box-to-bounding-box | Считать расстояние между границами, не центрами |
| Движение кусаки | movement_speed 0,2 за тик; vision_distance 30 | 12 плиток/с; при масштабе 1 плитка = 1 блок это 0,6 блока/MC тик |
| Кусака: восстановление/поведение | healing_per_tick 0,01; pollution_to_join_attack 4; distraction_cooldown 300; min_pursue_time 600; max_pursue_distance 50 | 0,6 Factorio HP/с; остальные числа нельзя переносить как MC тики |
| Гнездо кусак | max_health 350; healing_per_tick 0,02 | 28 MC HP; 1,2 Factorio HP/с |
| Сопротивления гнезда | physical decrease 2, percent 15; explosion decrease 5, percent 15; fire decrease 3, percent 60 | Для обычной пули: (5 - 2) x 0,85 = 2,55 Factorio HP = 0,204 MC HP |
| Размножение гнезда | spawning_cooldown {360,150}; radius 10; spacing 3; max_count_of_owned_units 7; max_friends_around_to_spawn 5 | От 6 с при evolution 0 до 2,5 с при evolution 1; два ограничения количества имеют разный смысл |
| Поддержка гнезда | call_for_help_radius 50 | Не подменять радиусом обычного зрения кусаки |
| Персонаж | 250 HP; healing_per_tick 0,15; ticks_to_stay_in_combat 600 | 10 с после выстрела или получения урона до выхода из боя |

Источники чисел: [пистолет и автомат, item.lua L3690-L3749](https://github.com/wube/factorio-data/blob/1.1.110/base/prototypes/item.lua#L3690-L3749), [магазин L3491-L3536](https://github.com/wube/factorio-data/blob/1.1.110/base/prototypes/item.lua#L3491-L3536), [кусака и гнездо, enemies.lua L15-L162](https://github.com/wube/factorio-data/blob/1.1.110/base/prototypes/entity/enemies.lua#L15-L162), [персонаж L1192-L1220](https://github.com/wube/factorio-data/blob/1.1.110/base/prototypes/entity/entities.lua#L1192-L1220).

Для гнезда веса появления: small-biter {(0;0,3),(0,6;0)}, medium-biter {(0,2;0),(0,6;0,3),(0,7;0,1)}, big-biter {(0,5;0),(1;0,4)}, behemoth-biter {(0,9;0),(1;0,3)}. Это веса с нормализацией, а не независимые вероятности. На первом срезе эволюция и все размеры еще не реализованы; ограничение малыми кусаками надо явно отмечать.

Не округлять период укуса 35/3 до постоянных 12 MC тиков без фиксации отклонения: это меняет частоту. Для точной средней частоты нужен накопитель времени либо другая проверенная модель. Множители исследования скорострельности также дают дробные периоды.

Дальность "плитки в блоки", размеры гнезда и высота прицеливания требуют явного правила 3D-адаптации. Плоскую геометрию нельзя считать полностью перенесенной лишь по совпадению чисел дальности.

Прототипы этих оружий не задают отдельную длительность перезарядки. Не добавлять произвольную FPS-перезарядку/дополнительную задержку после десяти выстрелов под видом исходного баланса. Расходовать один выстрел из десяти; остаток магазина должен переживать перенос предмета и сохранение. Сервер определяет дальность, попадание, cooldown, расход и урон. Клиент передает намерение стрелять и отображает подтвержденные эффекты. Повторный пакет не должен давать второй выстрел. Проверить, что стандартное окно неуязвимости Minecraft не режет 10 выстрелов/с автомата.

### Рецепты и первая военная ветка

| ID | Обычный рецепт | Дорогой рецепт | Время и доступность |
|---|---|---|---|
| pistol | 5 copper-plate + 5 iron-plate = 1 | Тот же, отдельной ветки нет | 5 с; изначально |
| firearm-magazine | 4 iron-plate = 1 магазин | Тот же | 1 с; изначально |
| submachine-gun | 10 iron-gear-wheel + 5 copper-plate + 10 iron-plate = 1 | 15 iron-gear-wheel + 20 copper-plate + 30 iron-plate = 1 | 10 с; military |
| piercing-rounds-magazine | 1 firearm-magazine + 1 steel-plate + 5 copper-plate = 1 | Тот же | 3 с; military-2 |
| grenade | 5 iron-plate + 10 coal = 1 | Тот же | 8 с; military-2 |
| military-science-pack | 1 piercing-rounds-magazine + 1 grenade + 2 stone-wall = 2 | Тот же | 10 с; military-science-pack |

Источники: [первое оружие и магазины](https://github.com/wube/factorio-data/blob/1.1.110/base/prototypes/recipe.lua#L947-L993), [бронебойный магазин и граната](https://github.com/wube/factorio-data/blob/1.1.110/base/prototypes/recipe.lua#L1254-L1280), [военная наука](https://github.com/wube/factorio-data/blob/1.1.110/base/prototypes/recipe.lua#L2117-L2131).

- `military`: без prerequisites, 10 единиц по 1 automation-science-pack, 15 с/ед. Открывает submachine-gun, shotgun и shotgun-shell. Первый срез без дробовика не завершает military.
- `military-2`: prerequisites military, steel-processing, logistic-science-pack; 20 единиц по 1 красной и 1 зеленой колбе, 15 с/ед.; открывает piercing-rounds-magazine и grenade.
- `stone-wall`: без prerequisites, 10 красных единиц по 10 с; открывает stone-wall.
- `military-science-pack`: prerequisites military-2 и stone-wall; 30 красно-зеленых единиц по 15 с; открывает рецепт военной науки.

Источники: [military/military-2](https://github.com/wube/factorio-data/blob/1.1.110/base/prototypes/technology.lua#L2027-L2083), [stone-wall](https://github.com/wube/factorio-data/blob/1.1.110/base/prototypes/technology.lua#L2583-L2603), [military-science-pack](https://github.com/wube/factorio-data/blob/1.1.110/base/prototypes/technology.lua#L2656-L2680).

## Проверенное исходное состояние и полный оставшийся объем

Просмотрены [Phase1Data](../src/main/java/dev/minetorio/core/data/Phase1Data.java), [OilData](../src/main/java/dev/minetorio/core/data/OilData.java), [Technology](../src/main/java/dev/minetorio/core/research/Technology.java), [ResearchState](../src/main/java/dev/minetorio/core/research/ResearchState.java), [LabLogic](../src/main/java/dev/minetorio/core/research/LabLogic.java), [Recipe](../src/main/java/dev/minetorio/core/recipe/Recipe.java), [ModItems](../src/main/java/dev/minetorio/content/registry/ModItems.java), [OilMaterials](../src/main/java/dev/minetorio/content/registry/OilMaterials.java), [ModEntities](../src/main/java/dev/minetorio/content/registry/ModEntities.java), [PersonalRoboport](../src/main/java/dev/minetorio/content/logistics/PersonalRoboport.java), [CharacterHealth](../src/main/java/dev/minetorio/content/player/CharacterHealth.java).

| Область | Исходное состояние | Что еще обязательно |
|---|---|---|
| Ранний завод, нефтехимия, логистика | Таблицы и игровые классы существуют; повторная приемка здесь не выполнялась | Проверить точность всех данных и ранние пробелы: stack-filter-inserter, repair-pack, lamp, покрытие, landfill, cliff-explosives |
| Первый бой | Военных рецептов/технологий и врагов в просмотренных таблицах/реестрах нет | Реальные оружия, магазины, малый кусака, гнездо, генерация, серверная стрельба и сохранение |
| Военная наука | Отсутствует; лаборатория принимает только 5 гражданских видов науки | Вся цепочка military-2/stone-wall, рецепты, колбы, слоты/UI/автоматизация лаборатории |
| Железная дорога | Классы железнодорожной системы не найдены | Все рельсы, составы, станции, расписания/ожидания, сигналы/цепные сигналы, блоки/резервирование, топливо, погрузка, сохранение |
| Поздний завод | assembling-machine-3, electric-furnace, beacon и модули отсутствуют | Все 9 модулей, слоты, ограничения productivity, эффекты, энергия, маяки |
| Фиолетовая/желтая наука | Предметы-иконки есть, рецептов и технологий нет | Production: железная дорога + productivity-module + advanced-material-processing-2; utility: robotics + advanced-electronics-2 + low-density-structure |
| Закрытые поздние возможности | coal_liquefaction/logistics_3 ссылаются на отсутствующую production_science_pack; logistic_system на utility_science_pack | Сохранить реальные prerequisites; завершить науку, не снимать блокировки |
| Личная экипировка | personal_roboport существует как предмет в инвентаре без брони/зарядки; технология требует отсутствующую solar_panel_equipment | Броня, сетки, энергоисточники/батареи, энергетика и все оборудование, roboport mk2 |
| Вооружение и оборона | Отсутствуют | Все семейства боеприпасов/капсул/роботов; стены, ворота, турели, мины, артиллерия, атомное оружие, сопротивления/улучшения/дружественный урон |
| Транспорт | Военный/железнодорожный транспорт отсутствует | Car, tank, spidertron и remote; топливо, инвентари, вооружение, управление, оборудование и сохранение |
| Враги/экология | Военные сущности и отдельные системы не найдены | 4 размера кусак, 4 плевак, 4 червей, оба гнезда; эволюция, загрязнение, поглощение, группы, экспансия, рейды, пути, чанки |
| Логическая сеть | Системы не найдены | Red/green wire, 3 комбинатора, лампы, выключатель, динамик, сигналы, задержки и подключения машин/лент/логистики/поездов |
| Ядерная цепь | Отсутствует | Урановая добыча с кислотой, вероятностная переработка, Kovarex, ячейки/отработанное топливо/переработка, nuclear-fuel; реакторы/соседский бонус/тепло/турбины |
| Ракета и продолжение | Отсутствуют | Low-density-structure, rocket-fuel, processing-unit, rocket-control-unit, silo, parts, satellite, запуск, space science, победа и бесконечные исследования |
| Улучшения | Technology хранит только фиксированную цену и unlocksRecipes; ResearchState фиксирует одно завершение ID | Эффекты урона/скорости/емкости/продуктивности/инвентаря, уровни, формулы цены, бесконечность и сохранение прогресса |
| Приемка | Исторические результаты находятся в CONTINUE; новых прогонов здесь нет | Полный survival-путь, многопользовательский сервер, рестарт/чанки, клиентская стрельба и все экраны, нагрузочные замеры |

Источники зависимостей поздней науки: [technology.lua L2682-L2733](https://github.com/wube/factorio-data/blob/1.1.110/base/prototypes/technology.lua#L2682-L2733). Личная солнечная панель требует modular-armor и solar-energy: [L4518-L4542](https://github.com/wube/factorio-data/blob/1.1.110/base/prototypes/technology.lua#L4518-L4542).

### Перечень отсутствующих обычных рецептов по ID

Сопоставлены 198 явно именованных рецептов из official recipe.lua с объявлениями Phase1Data/OilData. Учтены алиасы personal-roboport-equipment = personal_roboport, logistic-chest-* = *_chest, а также циклы solid-fuel и сундуков. Из 117 отсутствующих ID четыре служебных выделены отдельно; ниже 113 игровых рецептов. Это сверка наличия объявлений, не полная верификация содержимого 81 совпавшего ID. Автоматические рецепты наполнения/опорожнения бочек возникают отдельно и не входят в число 198.

- `speed-module`
- `speed-module-2`
- `speed-module-3`
- `productivity-module`
- `productivity-module-2`
- `productivity-module-3`
- `effectivity-module`
- `effectivity-module-2`
- `effectivity-module-3`
- `stack-filter-inserter`
- `night-vision-equipment`
- `belt-immunity-equipment`
- `energy-shield-equipment`
- `energy-shield-mk2-equipment`
- `battery-equipment`
- `battery-mk2-equipment`
- `solar-panel-equipment`
- `fusion-reactor-equipment`
- `personal-laser-defense-equipment`
- `discharge-defense-equipment`
- `discharge-defense-remote`
- `exoskeleton-equipment`
- `personal-roboport-mk2-equipment`
- `laser-turret`
- `flamethrower-turret`
- `artillery-turret`
- `artillery-targeting-remote`
- `gun-turret`
- `pistol`
- `submachine-gun`
- `firearm-magazine`
- `light-armor`
- `small-lamp`
- `repair-pack`
- `stone-wall`
- `car`
- `shotgun`
- `shotgun-shell`
- `piercing-rounds-magazine`
- `grenade`
- `gate`
- `heavy-armor`
- `concrete`
- `hazard-concrete`
- `refined-concrete`
- `refined-hazard-concrete`
- `landfill`
- `rail`
- `locomotive`
- `cargo-wagon`
- `rail-signal`
- `rail-chain-signal`
- `train-stop`
- `arithmetic-combinator`
- `decider-combinator`
- `constant-combinator`
- `power-switch`
- `programmable-speaker`
- `red-wire`
- `green-wire`
- `poison-capsule`
- `slowdown-capsule`
- `cluster-grenade`
- `defender-capsule`
- `distractor-capsule`
- `destroyer-capsule`
- `cliff-explosives`
- `uranium-rounds-magazine`
- `rocket`
- `explosive-rocket`
- `atomic-bomb`
- `piercing-shotgun-shell`
- `cannon-shell`
- `explosive-cannon-shell`
- `uranium-cannon-shell`
- `explosive-uranium-cannon-shell`
- `artillery-shell`
- `flamethrower-ammo`
- `assembling-machine-3`
- `tank`
- `spidertron`
- `spidertron-remote`
- `fluid-wagon`
- `artillery-wagon`
- `modular-armor`
- `power-armor`
- `power-armor-mk2`
- `flamethrower`
- `land-mine`
- `rocket-launcher`
- `combat-shotgun`
- `military-science-pack`
- `production-science-pack`
- `utility-science-pack`
- `processing-unit`
- `rocket-silo`
- `electric-furnace`
- `beacon`
- `low-density-structure`
- `rocket-fuel`
- `rocket-control-unit`
- `rocket-part`
- `satellite`
- `nuclear-reactor`
- `centrifuge`
- `uranium-processing`
- `kovarex-enrichment-process`
- `nuclear-fuel`
- `nuclear-fuel-reprocessing`
- `uranium-fuel-cell`
- `heat-exchanger`
- `heat-pipe`
- `steam-turbine`

### Перечень отсутствующих технологий по ID

Сопоставлены 35 локальных объявлений (включая personal_roboport как алиас personal-roboport-equipment). В official technology.lua есть 187 буквальных имен; пять дополнительных уровней follower-robot-count-2..6 создаются функцией. Ниже 157 отсутствующих технологий/стартовых прототипов серий. Конечные и бесконечные уровни нельзя трактовать как одно исследование с постоянной ценой.

- `advanced-electronics-2`
- `advanced-material-processing-2`
- `artillery`
- `artillery-shell-range-1`
- `artillery-shell-speed-1`
- `atomic-bomb`
- `automated-rail-transportation`
- `automation-3`
- `automobilism`
- `battery-equipment`
- `battery-mk2-equipment`
- `belt-immunity-equipment`
- `braking-force-1`
- `braking-force-2`
- `braking-force-3`
- `braking-force-4`
- `braking-force-5`
- `braking-force-6`
- `braking-force-7`
- `circuit-network`
- `cliff-explosives`
- `concrete`
- `defender`
- `destroyer`
- `discharge-defense-equipment`
- `distractor`
- `effect-transmission`
- `effectivity-module`
- `effectivity-module-2`
- `effectivity-module-3`
- `energy-shield-equipment`
- `energy-shield-mk2-equipment`
- `energy-weapons-damage-1`
- `energy-weapons-damage-2`
- `energy-weapons-damage-3`
- `energy-weapons-damage-4`
- `energy-weapons-damage-5`
- `energy-weapons-damage-6`
- `energy-weapons-damage-7`
- `exoskeleton-equipment`
- `explosive-rocketry`
- `flamethrower`
- `flammables`
- `fluid-wagon`
- `follower-robot-count-1`
- `follower-robot-count-2`
- `follower-robot-count-3`
- `follower-robot-count-4`
- `follower-robot-count-5`
- `follower-robot-count-6`
- `follower-robot-count-7`
- `fusion-reactor-equipment`
- `gate`
- `gun-turret`
- `heavy-armor`
- `inserter-capacity-bonus-1`
- `inserter-capacity-bonus-2`
- `inserter-capacity-bonus-3`
- `inserter-capacity-bonus-4`
- `inserter-capacity-bonus-5`
- `inserter-capacity-bonus-6`
- `inserter-capacity-bonus-7`
- `kovarex-enrichment-process`
- `land-mine`
- `landfill`
- `laser`
- `laser-shooting-speed-1`
- `laser-shooting-speed-2`
- `laser-shooting-speed-3`
- `laser-shooting-speed-4`
- `laser-shooting-speed-5`
- `laser-shooting-speed-6`
- `laser-shooting-speed-7`
- `laser-turret`
- `low-density-structure`
- `military`
- `military-2`
- `military-3`
- `military-4`
- `military-science-pack`
- `mining-productivity-1`
- `mining-productivity-2`
- `mining-productivity-3`
- `mining-productivity-4`
- `modular-armor`
- `modules`
- `night-vision-equipment`
- `nuclear-fuel-reprocessing`
- `nuclear-power`
- `personal-laser-defense-equipment`
- `personal-roboport-mk2-equipment`
- `physical-projectile-damage-1`
- `physical-projectile-damage-2`
- `physical-projectile-damage-3`
- `physical-projectile-damage-4`
- `physical-projectile-damage-5`
- `physical-projectile-damage-6`
- `physical-projectile-damage-7`
- `power-armor`
- `power-armor-mk2`
- `production-science-pack`
- `productivity-module`
- `productivity-module-2`
- `productivity-module-3`
- `rail-signals`
- `railway`
- `refined-flammables-1`
- `refined-flammables-2`
- `refined-flammables-3`
- `refined-flammables-4`
- `refined-flammables-5`
- `refined-flammables-6`
- `refined-flammables-7`
- `research-speed-1`
- `research-speed-2`
- `research-speed-3`
- `research-speed-4`
- `research-speed-5`
- `research-speed-6`
- `rocket-control-unit`
- `rocket-fuel`
- `rocket-silo`
- `rocketry`
- `solar-panel-equipment`
- `space-science-pack`
- `speed-module`
- `speed-module-2`
- `speed-module-3`
- `spidertron`
- `stone-wall`
- `stronger-explosives-1`
- `stronger-explosives-2`
- `stronger-explosives-3`
- `stronger-explosives-4`
- `stronger-explosives-5`
- `stronger-explosives-6`
- `stronger-explosives-7`
- `tank`
- `toolbelt`
- `uranium-ammo`
- `uranium-processing`
- `utility-science-pack`
- `weapon-shooting-speed-1`
- `weapon-shooting-speed-2`
- `weapon-shooting-speed-3`
- `weapon-shooting-speed-4`
- `weapon-shooting-speed-5`
- `weapon-shooting-speed-6`
- `worker-robots-speed-1`
- `worker-robots-speed-2`
- `worker-robots-speed-3`
- `worker-robots-speed-4`
- `worker-robots-speed-5`
- `worker-robots-speed-6`
- `worker-robots-storage-1`
- `worker-robots-storage-2`
- `worker-robots-storage-3`

Источник программно созданных уровней: [technology.lua L1561-L1626](https://github.com/wube/factorio-data/blob/1.1.110/base/prototypes/technology.lua#L1561-L1626). Перечень не включает отдельными строками все математические уровни бесконечных исследований.

### Служебные прототипы вне обычной прогрессии

- loader, fast-loader, express-loader: рецепты disabled и hidden.
- electric-energy-interface: рецепт disabled; предмет имеет flags={"hidden"}. Не приписывать hidden самому рецепту.
- Heat-interface, infinity-объекты, редакторские/сценарные объекты, эффекты, трупы и обломки не считать обычными технологиями выживания. Необходимые боевые эффекты, трупы и спавн при этом требуют собственной реализации.

Источник: [recipe.lua](https://github.com/wube/factorio-data/blob/1.1.110/base/prototypes/recipe.lua), [служебные предметы item.lua L1799-L1820](https://github.com/wube/factorio-data/blob/1.1.110/base/prototypes/item.lua#L1799-L1820). Их поддержка редактором может стать отдельным объемом, но не нужна для честного закрытия выживательной прогрессии.

## Реальные расхождения и вопросы

1. Старые документы говорят "этапы 1-4 готовы", но часть доступных в креативе возможностей недостижима в выживании: personal_roboport, поздняя логистика и синие ленты. Это не основание разблокировать их раньше оригинала.
2. personal_roboport сейчас работает из обычного инвентаря и не требует зарядки. Полная версия должна завершить оборудование, а не сохранять бесплатную энергетику молча.
3. Текущее восстановление CharacterHealth отмечает только полученный урон; выстрел тоже должен сбрасывать задержку до регенерации. В исходнике Factorio это прямо пояснено у ticks_to_stay_in_combat.
4. military неполон без shotgun и shotgun-shell. Разрешено описать первый бой как частичную реализацию технологии, запрещено считать весь военный блок завершенным.
5. Нормальный режим уже выбран предыдущим планом. Поддержка дорогого режима отдельно не согласована. Например, iron-gear-wheel стоит 2/4 iron-plate, electronic-circuit 1 iron-plate + 3 cable / 2 iron-plate + 8 cable. Не смешивать ветви.
6. Все улучшения нуждаются в эффектах, не только в появлении строки исследования. Текущей записи Technology недостаточно для count_formula и max_level.
7. Для завода требуется модель повреждений/ремонта построек и сохранения HP; аудит не подтверждает существование общей такой системы. Одних врагов с атакой по игроку недостаточно для рейдов.
8. Таблица наличия рецептов не покрывает динамические рецепты, свойства машин, tile prototypes, AI и сценарий победы. Это отдельные обязательные строки будущей детализации.
9. Полный автоматический экспорт конечного набора прототипов из исполняемого Factorio 1.1.110 не выполнен. Текущий список основан на исходных Lua и просмотренных генераторах; before/after проверки игры отсутствуют.
10. Первичный аудит всех чисел остальных этапов еще предстоит: затраты энергии, скорости, тепло, урон позднего оружия, эволюция, сопротивления, условия запуска ракеты. Здесь точными объявлены только проверенные значения первого боя и явно приведенные зависимости/рецепты.

## Проверки, необходимые для закрытия первого боя

- Обычные рецепты и стоимость military совпадают с первичным источником; невозможен ранний автомат и бесплатный магазин.
- Сервер принимает намерение выстрела, проверяет игрока/оружие/магазин/cooldown/дальность/линию видимости; повтор пакета не создает урон.
- 10 выстрелов расходуют один магазин; частичный магазин, оружие, HP и гнездо корректны после выхода/входа и выгрузки чанка.
- Пистолет дает 4 выстрела/с, автомат 10 выстрелов/с без дополнительных FPS-пауз; проверены сопротивления гнезда.
- Кусака преследует/кусает, не проходит сквозь препятствия, не атакует из незагруженного чанка; его нельзя заменить ванильным зомби.
- Гнезда встречаются в новом настоящем Наувисе вне безопасного старта; генерация зависит от seed и не дублируется после загрузки.
- Клиентский плейтест подтверждает прицеливание, звук, отдачу, вспышку, попадания, размеры и читаемость; происхождение ассетов записано.
- Стрельба сбрасывает задержку регенерации. Проверены смерть, возрождение, дружественный урон и несколько игроков.
- Записаны отдельные результаты unit/GameTest/build, серверного сценария и визуального плейтеста. Непроверенные пункты остаются открытыми.

Документ закрывает инвентаризацию исходного состояния и контракт первого боя. Сам по себе он не завершает блок 1 целиком: нужны детализация параметров всех последующих подсистем и проверяемое сопоставление существующей реализации с оригиналом.
