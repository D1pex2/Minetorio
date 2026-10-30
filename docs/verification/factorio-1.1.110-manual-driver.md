# Factorio 1.1.110: ручное управление с персонажем за рулем

30.10.2026, Factorio 1.1.110 build 62357 linux64 headless; Debian 12 на QEMU TCG, 1 CPU и 2048 MiB, без сети, seed 12345. Один ванильный locomotive на прямом пути, 30 coal, force bonus торможения 0. Автономный character, без connected player, посажен `locomotive.set_driver(character)`: вызов успешен, `get_driver()` возвращал character unit 224 на всех выбранных тиках. `manual_mode=true`, train state 9 (`manual_control`). После первого тика позиция character соответствовала позиции локомотива. Скорость и burner скриптом не задавались.

API 1.1.110: `LuaEntity.set_driver` принимает `LuaEntity`; `LuaEntity` наследует `LuaControl.riding_state`, `LuaTrain.riding_state` доступен на чтение. Описание `LuaControl.riding_state` упоминает car/игрока, поэтому применимость к автономному персонажу и locomotive здесь проверена фактическим состоянием и движением, а не выведена только из сигнатуры. `character.riding_state` устанавливал straight с accelerating на tick 0, nothing на tick 120 и braking на tick 140; каждый вызов успешен, последующее значение driver и train подтверждало команду. Эти значения сохранялись между присваиваниями.

| on_tick | Управление после команды | Скорость, tile/tick | Fuel remaining, J | Позиция y |
|---:|---|---:|---:|---:|
| 119 | accelerating | 0.4520601357661794 | 2809333.3333333335 | -28.87890625 |
| 120 | nothing | 0.45509709775705626 | 2799333.3333333335 | -29.33203125 |
| 121 | nothing | 0.4531414211404673 | 2799333.3333333335 | -29.78515625 |
| 140 | braking | 0.41734612680512856 | 2799333.3333333335 | -38.03125 |
| 141 | braking | 0.4123461268051286 | 2799333.3333333335 | -38.4453125 |
| 160 | braking | 0.31734612680512847 | 2799333.3333333335 | -45.33203125 |

Команда устанавливается внутри on_tick до записи trace, а движение в этом тике происходит после нее. Поэтому speed на tick 120 отражает предыдущий шаг разгона, tick 120 -> 140 - 20 шагов nothing, tick 140 -> 160 - 20 шагов braking. Накат за первый шаг уменьшил speed на 0.00195567661658896; торможение уменьшало speed на 0.005 за шаг, совпадая с braking_force 10 / weight 2000. На обоих отрезках fuel remaining и heat были неизменны; накат и торможение не равны в этих конкретных условиях. Опыт не исследует другие массы, топлива, изменения направления и ручной ввод connected player; без driver отдельно в этом запуске не проверялось.

Фикстура `src/test/resources/factorio-1.1.110/rail-manual-driver-native.json` содержит setup, все команды и каждый on_tick на интервале 120..160 плюс начальные и предшествующие точки, с исходными числовыми литералами. Собственный исполненный мод `tools/factorio-oracle/manual-driver/manual8_1.0.0/{control.lua,info.json}` скопирован побайтово. Каждый сценарий следует запускать в отдельном пустом mod-directory и новом сохранении; benchmark использовал 170 тиков, checksum 2797307334. Полный serial `serial-manual-8.log` SHA-256 `836c1d23d1308250a5b1a012f8e29a909c43a193697d0c745c2f7b49ccc534a4`, полный извлеченный JSON `manual-8-raw.json` SHA-256 `60b05e40c1b5ce36a2aada80d4b22e6b7848e51ebb0d068c19247eaab831e314` в локальном `runtime/factorio-oracle-1.1.110/logs`. Factorio archive SHA-256 `485fe6db36e5decd7dd0d70e7c97e61f818100fa3e48d87884b287027c7a646a`.

До старта другие QEMU отсутствовали, shell был LF без NUL и прошел bash -n, base hash совпал, qcow2 check без repair успешен. Фактический PID 22824 использовал `-smp 1 -m 2048 -nic none`, отдельный overlay и readonly payload, findmnt подтвердил ro. MANUAL_DONE и MANUAL_RC:0; штатный poweroff, code 0 и Power down. После завершения других QEMU нет, Debian base SHA-512 `3b7c3342105085c60097147c7f7491e761c115170213ba7d9c87685e32d234bdafe0ddf8d6fd6600a6a837c761fa229ce8b9fe2086d1235f8eebeb778d05b977`, qemu-img check без repair ошибок не выявил. Это не проверка всего содержимого гостевой ФС.
