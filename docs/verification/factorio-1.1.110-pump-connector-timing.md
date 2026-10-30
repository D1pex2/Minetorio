# Factorio 1.1.110: время подключения помпы к цистерне

30.10.2026 проведен один ограниченный причинный эксперимент с Factorio 1.1.110 build 62357 linux64 headless на Debian 12/QEMU TCG, 1 CPU, 2048 MiB, без сети, seed 12345. Цель - проверить источник задержки первого переноса в одной положительной геометрии, не установить все правила подключения.

## Сценарий

Сцепленный locomotive + fluid-wagon весом 3000 автоматически прибыл к настоящему train-stop на прямом пути. Tick 645: `manual=false`, state 7 (`wait_station`), `station` существует, speed 0; локомотив (1,-296), цистерна (1,-289), ориентация 0. Три последовательные независимые фазы по 240 тиков: на одном и том же левом положении создавались новая помпа, новый источник и новая электросеть. Помпа фактически имела центр (-1,-290.5), направление east. Изолированный test-only источник, скопированный из pipe с емкостью 25000, начинал с 5000 water при 15 C; питание от test-only локального pole и vanilla electric-energy-interface. На границе каждой фазы тест удалял предыдущую помпу/источник/питание и очищал fluid wagon. Это контролируемый сброс, непрерывный баланс между фазами не утверждается. Ни одной фазе не понадобилось заполнять wagon до capacity 25000.

Первый прототип - vanilla pump. В официальном prototype API 1.1.110 `fluid_wagon_connector_speed` имеет default `1 / 64.0`; поле отсутствует в исходном base prototype. Два других - полные `table.deepcopy` той же vanilla pump, изменены только техническое имя и соответствующее поле скорости: `1/32` и `1/128`. Сама vanilla pump, графика, электричество, pumping_speed и fluid boxes не менялись. Data-stage лог фактически записал `vanilla_explicit_connector_speed=nil`, fast `0.03125`, slow `0.0078125`, pumping_speed `200`, connector_frames `35`. Runtime API не предоставляет чтение connector_speed у LuaEntityPrototype: nil в data stage означает использование schema default, а не нулевую скорость. Доступное runtime `pumping_speed` для всех трех равно 200.

| Помпа | connector_speed | Создана, tick | Первое питание, tick | Первый flow в wagon, tick | От создания до flow |
|---|---:|---:|---:|---:|---:|
| Vanilla | 1/64 | 645 | 646 | 710 | 65 |
| Clone fast | 1/32 | 885 | 886 | 918 | 33 |
| Clone slow | 1/128 | 1125 | 1126 | 1254 | 129 |

Каждый ряд содержит все 241 снимок от создания до elapsed240: position, состояние поезда, energy, source/pump/wagon fluid и pipe connections. Перед первым flow source содержал около 4600, собственный buffer помпы 400, wagon был пуст. На первом снимке после переноса wagon содержит 400: это накопленный buffer и не доказательство постоянной скорости 400/tick. К концу каждой фазы все 5000 единиц находились в wagon.

Изменение только connector_speed у клонов меняет измеренную задержку 65 -> 33 или 129 тиков; первое питание во всех случаях наблюдалось на tick после создания. Эти данные причинно связывают startup delay со скоростью connector в данной сцене и совместимы с `1 + 1/speed` для трех точек. Это не доказательство общей формулы для всех speed, систем питания, других поз, уже подключенной помпы, насыщенного/пустого источника, curved track, сохранения и перезапуска. Прямое состояние latch API не подтверждено. Первые два значения времени на снимках относятся к on_tick: внутренний расчет после on_tick проявляется в следующем снимке.

## Данные и условия запуска

- `src/test/resources/factorio-1.1.110/rail-pump-connector-timing-native.json`: 738 исходных JSON объектов без изменения числовых литералов, включая все 723 покадровых trace. Из 749 объектов raw исключены только 11 промежуточных arrival_trace до прибытия; native_arrival сохранен. Контроллер отдельно проверил точное совпадение остальных объектов, SHA-256 обоих логов и побайтовое совпадение трех файлов мода с исполненным payload.
- `tools/factorio-oracle/pump/pump11_1.0.0/{control.lua,data.lua,info.json}`: побайтовые копии исполненного собственного мода. Моды pump9 и pump10 не изменены.
- Полные `serial-pump-11.log` SHA-256 `01b35de1dee0f3748702074424e0c6f4ad8cc7f4d762ed37adbfcbef69c38772` и `pump-11-raw.json` SHA-256 `8defd5a5b8794655a55a0dde6e8cabf7a668b313ffdc36f0cc7fda94dc9779f6` находились в `runtime/factorio-oracle-1.1.110/logs`. Metadata фикстуры содержит эти суммы и полный data-stage лог значений.
- Фактически использованы новый отдельный `pump11-mods`, отдельный save `pump11.zip`, `--map-gen-seed 12345`, `--benchmark-ticks 1700 --benchmark-runs 1`; checksum 3207497654. `PUMP11_DONE`, `PUMP11_RC:0`. Повторять следует в новом пустом mod-directory с одним модом и новом save.

Исходный официальный архив Factorio SHA-256 `485fe6db36e5decd7dd0d70e7c97e61f818100fa3e48d87884b287027c7a646a`. До запуска проверены отсутствие других QEMU, RAM, resolved backing chain, base SHA-512, qcow2 check без repair и shell LF/NUL/bash -n. Фактический PID 15172 использовал один CPU, 2048 MiB, `-nic none`, собственный overlay и readonly payload; findmnt подтвердил ro. Штатные poweroff, `Power down`, exit 0; после завершения QEMU отсутствуют, backing chain прежний, SHA-512 Debian base `3b7c3342105085c60097147c7f7491e761c115170213ba7d9c87685e32d234bdafe0ddf8d6fd6600a6a837c761fa229ce8b9fe2086d1235f8eebeb778d05b977`, qcow2 check без repair без ошибок. Это не полная проверка гостевой файловой системы.
