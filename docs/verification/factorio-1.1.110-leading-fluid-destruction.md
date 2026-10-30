# Factorio 1.1.110: ведущая цистерна и уничтожение груженого вагона

30.10.2026. Один изолированный запуск Factorio 1.1.110 build 62357 linux64 headless, Debian 12, QEMU TCG, один CPU, 2048 MiB, без сети, seed 12345. Это два конкретных опыта, не проверка всех составов, видов смерти и областей выпадения. Сценарий и фикстура:

- `tools/factorio-oracle/rail-impact/leading7_1.0.0/{info.json,control.lua}` - побайтовая копия исполненного собственного мода.
- `src/test/resources/factorio-1.1.110/rail-leading-fluid-destruction-native.json` - исходные JSON-строки setup, before, damage, died и after без округления или перепарсивания числовых литералов.

## Цистерна впереди локомотива

Сцепленный train weight 3000, реальные `carriages` и `front_stock`: спереди fluid-wagon HP 600, `energy_per_hit_point=6`, сзади locomotive HP 1000, `energy_per_hit_point=5`. Оба orientation 0, направление движения на север. Локомотив с coal естественно разогнал automatic train по прямым рельсам до small-biter HP 15, AI отключен; у обоих collision masks есть train-layer. Скорость и burner скриптом не записывались, телепортаций после старта не было.

На tick 444 до обновления on_tick speed 0.6927078814393639. В `on_entity_damaged` причина - именно fluid-wagon, скорость его train 0.693388855167899365, impact original/final 346.694427490234375, после чего biter погиб; записан и `on_entity_died`. На on_tick 445 speed 0.6633888551678994, front HP 600, back HP 1000, path активен. Fuel remaining 3559333.3333333335 -> 3549333.3333333335 J, heat и число coal в инвентаре не изменились. В фикстуре зафиксированы positions обоих car до, во время и после.

В этой точке raw согласуется с `3000 * 0.693388855167899365 / 6 = 346.69442758395` с учетом float; деление на 5 дает 416.0333131 и не согласуется. Разница event speed и скорости следующего on_tick равна 0.03, что согласуется с `15 * 6 / 3000`; деление с E5 дало бы 0.025. Это поддерживает использование E6 ударившей цистерны в данных конкретных условиях и отличает его от жесткого E5; опыт не доказывает общий алгоритм для curves, других целей и всех порядков столкновения.

## Смерть груженого cargo-wagon

В отдельной области неподвижный штатный cargo-wagon содержал 17 iron-plate. До вызова HP установлено в 20 и фактически прочитано 20.0000019073486328125. Выполнен реальный `LuaEntity.damage(1000, player_force, "impact")`, не mine и не destroy. `on_entity_damaged`: original 1000, final 379.999969482421875, final_health 0. На `on_entity_died` inventory еще показывал 17 iron-plate, после возврата damage entity уже invalid; return damage 20.0000019073486328125, то есть в этом случае ограничен оставшимся HP, в отличие от final события.

В области x=48..80, y=-16..16 до удара item-entity/corpse не было. Сразу после гибели найден только `cargo-wagon-remnants` типа corpse; на ticks 2, 5 и 30 та же картина, item-entity и предмет вагона в этой области не обнаружены. Инвентарь самих останков не исследован, предел поиска локальный; эти наблюдения нельзя распространять на все типы разрушения, локализации и окружение. Они не подтверждают возврат содержимого при гибели в проверенном сценарии и отличаются от отдельно проверенного mining.

## Артефакты, порядок и целостность

Полный serial `serial-leading-7.log` SHA-256 `00958530eac46113fa768be9b44488517ac014adbc093ef8c79e2a1117e11a88`; извлеченный полный `leading-7-raw.json` SHA-256 `e35fd7a65a248682a1cdf6034e2630aca4f296f542cc8f926788efb01e75e9ce`. Оба хранились в локальном `runtime/factorio-oracle-1.1.110/logs`. Archive Factorio SHA-256 `485fe6db36e5decd7dd0d70e7c97e61f818100fa3e48d87884b287027c7a646a`; benchmark 700 тиков checksum 2721441235, LEADING_DONE и LEADING_RC:0. Событие damage происходит после on_tick с тем же номером: не отождествлять on_tick speed с event speed.

До запуска других QEMU не было, SHA-512 Debian base совпал с предыдущим значением, qcow2 check без repair успешен, shell с LF и без NUL прошел bash -n. PID 25856 фактически использовал TCG, 1 CPU, 2048 MiB, `-nic none`, отдельный overlay и readonly payload; гостевой findmnt подтвердил ro. Гость штатно выключен, QEMU завершился code 0 и Power down; после завершения процессов QEMU нет, SHA-512 base `3b7c3342105085c60097147c7f7491e761c115170213ba7d9c87685e32d234bdafe0ddf8d6fd6600a6a837c761fa229ce8b9fe2086d1235f8eebeb778d05b977`, qcow2 check без repair ошибок не выявил. Это не полная проверка гостевой ФС.

Фактически исполнялись отдельный чистый mod-directory `/opt/factorio-oracle/leading7-mods` с одним `leading7_1.0.0`, создание нового `leading7.zip` с `--map-gen-seed 12345` и benchmark `--benchmark-ticks 700 --benchmark-runs 1`. При повторе использовать новый пустой mod-directory и новое имя сохранения, не включать одновременно остальные oracle моды. Production, index, Gradle и version не изменялись.
