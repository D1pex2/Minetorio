# Factorio 1.1.110: переименование остановки через script setter

30.10.2026 выполнен один ограниченный запуск Factorio 1.1.110 build 62357 linux64 headless на Debian 12/QEMU TCG. Новый save, отдельный mod-directory, seed 12345, 60 benchmark ticks, один CPU, 2048 MiB, сеть отсутствует. Собственный мод rename 1.0.0 не изменяет стандартные прототипы. Это проверка LuaEntity.backer_name setter, а не player GUI rename.

## Результат

1. Одна surface 2, force 1: train 2 manual и train 3 AUTO без топлива. Исходный schedule [Old, Keep, Old, Shared], current=2. Tick 4: единственный Old становится New, обе Old-записи обоих поездов становятся New. Tick 8: первый Shared становится First, расписания сохраняют Shared. Tick 12: последний Shared становится Last, четвертая запись обоих поездов становится Last.
2. Две surfaces 3 и 4 одной force 4: train 4/5 и 6/7 соответственно, на каждой manual и AUTO. На обеих surfaces существует Across. Tick 21: первый Across становится FirstAcross, расписания всех четырех поездов сохраняют Across. Tick 25: последний Across становится LastAcross, обе Across-записи всех четырех поездов на обеих surfaces становятся LastAcross. Переименование последней остановки затронуло также расписания surface, где остановка была переименована первой.
3. Одна surface 5, разные forces 5 и 6: train 8/9 и 10/11 соответственно, на каждой force manual и AUTO. По одной ForceOld у каждой force. Tick 38: первая ForceOld становится ForceFirst, расписания обеих forces сохраняют ForceOld. Tick 42: последняя ForceOld становится ForceLast, обе повторные записи всех четырех поездов обеих forces становятся ForceLast. Переименование последней остановки затронуло также расписания первой force.

Таким образом, в измеренных сценах setter учитывает одинаковые имена между surfaces и между forces, а последняя остановка переписывает расписания через эти границы. Это прямое native evidence для global behavior 1.1.110. Автоматически переносить его в dimension-local owner Minetorio без отдельного решения о модели нельзя.

Во всех семи действиях наблюдался настоящий on_entity_renamed с by_script=true, без player_index. Событие приходило внутри setter: снимок события уже содержал конечное расписание, затем следовал immediate_after_setter. Во время этих действий не зарегистрирован ни один on_train_schedule_changed. Отсутствие этого события не означало отсутствия изменений: результат установлен по train.schedule.

Идентичность поездов, current=2, порядок записей и все wait_conditions сохранились. У manual waits 111, 122/123, 133, 144 ticks; у AUTO 211, 222/223, 233, 244. Вторая запись содержит time с compare_type=and и inactivity с compare_type=or. На следующем и еще одном тике расписания совпадали с immediate snapshot. Все поезда оставались неподвижными, speed=0, fuel_count=0; manual=true/false сохранено. Фикстура содержит полные schedule/current/waits, state, train id, force, surface, stop unit_number/names/counts и события.

## Доказательства и ограничения

- tools/factorio-oracle/station-rename/rename_1.0.0/{control.lua,data.lua,info.json} и rename.sh побайтово совпадают с readonly payload. Гость вывел SHA-256 как readonly исходников, так и фактически скопированных в mod-directory трех файлов мода; суммы сверены с host.
- src/test/resources/factorio-1.1.110/rail-station-rename-native.json содержит все 46 исходных JSON объектов. В observations скопирован весь raw JSON массив без повторной сериализации и изменения числовых литералов. Metadata добавлена отдельно. SHA-256 фикстуры: 0947e4cb67fa63804ad81b29c58773fe5661a0b0c9a33f4ea52a1297a08ad7d9.
- runtime/factorio-oracle-1.1.110/logs/serial-station-rename.log SHA-256: a8c00a4dbb87c1d0f23fffec81ce3935552252182ad5841f794f16ece08e49e5.
- runtime/factorio-oracle-1.1.110/logs/station-rename-raw.json SHA-256: bf913b6a36882503410d820af97d8fc0eee369cbf88f98e30969e06989bc4589.
- control.lua SHA-256: b45fd51a5a5b1dd7cc6c756197e61ec2312f2a92a466ef5fa67b9b1f9aee0dcb. data.lua: 1524d43f6b8aa689bc68d0cdf5f28096788f5f47dc58dd765c362ce02138e282. info.json: 918e11ed8c0f5a81b6d565c6c8104904c16125197842c3835a76102405888980. rename.sh: aefacbe838e394eabde13fb4151a06d288d2b359dcc1595866f04d5dcf4c254c.

Три asserted baseline и три phase_complete, семь before/event/immediate/next/second snapshots, init и complete. Baselines независимы по сущностям и именам; это один свежий save, не три перезапуска. После baseline нет script edits расписаний существующих поездов. Новые поезда следующей фазы получают собственные исходные расписания. Нет GUI, movement, временных rail targets, сохранения/перезагрузки между rename или измерения wait clocks активного ожидания на станции. Тест не доказывает player rename с by_script=false и не закрывает отдельный native GUI gate.

## Безопасность VM

До запуска: других QEMU нет, свободно 16998684 KiB RAM; проверены LF/no NUL/bash -n для shell. Проверен официальный Factorio archive SHA-256 485fe6db36e5decd7dd0d70e7c97e61f818100fa3e48d87884b287027c7a646a. Resolved backing chain: отдельный images/rename-overlay.qcow2, существующий images/oracle-overlay.qcow2, Debian base downloads/debian-12-nocloud-amd64-20261026-2643.qcow2. Для всех трех слоев qemu-img check выполнен без repair и без ошибок. Payload отдельный rename-payload, -drive readonly=on; guest findmnt подтвердил ro. Каждая Factorio команда ограничена timeout 240, benchmark 60 ticks. Runner хранит PID и Popen handle, повторных запусков после timeout не было.

QEMU PID 28684, runner PID 33688. RENAME_DONE и RENAME_RC:0. Штатный poweroff, Power down, QEMU exit 0, PID исчез; других QEMU после запуска нет. До/после совпал Debian base SHA-512 3b7c3342105085c60097147c7f7491e761c115170213ba7d9c87685e32d234bdafe0ddf8d6fd6600a6a837c761fa229ce8b9fe2086d1235f8eebeb778d05b977. SHA-512 промежуточного readonly backing overlay также не изменился: 73026e824855f8b41a3ae0bcb84fd7a63db29d98d3c8c97f9c71fc03acbb9f1e7f3fa2c062ef9d18dbca0a12ac973b248c57af3d31d3252398a6f99a78aadac6. После выключения повторно проверены resolved chain и qemu-img check всех трех слоев без repair, ошибок нет. Это не полная проверка гостевой файловой системы.

Runtime файлы: station-rename-runner.py, station-rename-extract.py; logs/rename-preflight.json, rename-postflight.json, owned-station-rename.json, station-rename-analysis.json. В них сохранены фактические команды, SHA, chain, результаты проверок, PID и время выхода.
