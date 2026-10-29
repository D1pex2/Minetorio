# Native oracle: тяга локомотива Factorio1.1.110

Один автоматический угольный локомотив, полный газ, естественный разгон по прямой к удаленной станции. Нативный движок1.1.110 build62357, linux64 headless. Силы/скорость/топливо из Lua не устанавливаются. В этом опыте измерены только исходные параметры локомотива, full-throttle coal, достижение cap1.2 и последующее ручное торможение с bonus0.

Исполняемый официальный архив получен отдельно: [Factorio headless1.1.110](https://www.factorio.com/get-download/1.1.110/headless/linux64), SHA-256 `485fe6db36e5decd7dd0d70e7c97e61f818100fa3e48d87884b287027c7a646a`. Бинарники и игровые ассеты в репозиторий не входят.

## Сохраненные исходники и данные

- [Собственный control.lua](../../tools/factorio-oracle/physics_1.0.0/control.lua) и info.json: точная копия harness, который выдал исходную трассу physics-3. Создает851 рельс, один локомотив и4 кандидата станции; выбирает фактически достижимый путь. Записывает train validity/path/state, скорость, позиции, remaining_burning_fuel, burner.heat, entity.energy и inventory coal. Переход в manual на tick911 служит командой торможения.
- [Минимальная native fixture](../../src/test/resources/factorio-1.1.110/train-physics-native.json): setup, валидный путь, начало разгона, все19 записанных точек устойчивого cap между826 и891 и4 точки торможения. Исходные числовые JSON-литералы сохранены без промежуточного округления; SHA-256 исходного serial log записан в metadata.
- [run-physics.sh](../../tools/factorio-oracle/run-physics.sh): распаковывает проверенный архив в новую папку и запускает create/benchmark. Не использует существующие миры. config-path.cfg официального архива задает portable data paths в новой распаковке.
- [extract-train-physics.py](../../tools/factorio-oracle/extract-train-physics.py): извлекает тот же ограниченный набор строк PHYSICS из одного serial log или пары create/benchmark logs, проверяет полноту и сохраняет литералы. Никаких внешних Python-зависимостей.

Исходный эксперимент контроллера: отдельная Debian12 VM, QEMU TCG, seed12345, --benchmark-ticks1300, --benchmark-runs1. Полный локальный serial-physics-3.log содержал330 JSON-записей. Этот документ и fixture не зависят от наличия временного runtime каталога.

## Воспроизведение на Linux

Нужны sh, tar с xz, sha256sum, python3 и среда запуска linux64 headless. Имя папки результата должно быть новым:

```sh
sh tools/factorio-oracle/run-physics.sh \
  /path/factorio_headless_x64_1.1.110.tar.xz \
  /path/new-physics-run
```

Команды движка внутри скрипта:

```sh
factorio --mod-directory <new-run>/mods --create <new-run>/physics.zip --map-gen-seed 12345
factorio --mod-directory <new-run>/mods --benchmark <new-run>/physics.zip --benchmark-ticks 1300 --benchmark-runs 1
```

Скрипт сохраняет version.log, create.log, benchmark.log, новый save и извлеченный train-physics-native.json в новой папке. Для повторного извлечения старого serial log:

```sh
python3 tools/factorio-oracle/extract-train-physics.py serial-physics-3.log --output native-summary.json
```

Harness сохранен из действительно выполненного опыта. Новый переносимый wrapper прошел синтаксическую проверку, extractor проверен на исходном serial с сопоставлением всех сохраненных записей; повторный native запуск этим wrapper в рамках исправления не выполнялся.

## Измерение и изменение модели

На ticks826..891 speed=1.2, state=0/on_the_path, path и оба rail endpoints валидны; coal27. Heat и entity.energy оба равны10666.666666666666 и не меняются. Remaining_burning_fuel уменьшается с3819333.3333333335 до3169333.3333333335:650000J за65 тиков, ровно10000J/тик. Неизменность обоих видимых буферов и inventory исключает объяснение этой дельты их накоплением. Их равенство не доказывает, что это независимые буферы, поэтому абсолютные суммы энергии не складываются и не сравниваются с core remainingJ.

Для core действует исправление: полный положенный бюджет10000*abs(throttle)J на каждый направленно доступный локомотив, даже на cap. Ограничение скорости применяется после air. Preview, lookahead и фактический шаг используют один расчет, реальные fuel portions ограничивают доступную тягу. Торможение и idle не расходуют бюджет. Прежняя оптимизация needed/common-lambda давала около9533.877J/шаг и5720326.223J за600 шагов; она противоречит этому native измерению и удалена. Regression требует6000000J за600 core шагов при устойчивом cap, для manual и automatic с настоящим plan/commit.

Первые три powered скорости остаются0.00498125,0.0096947578125,0.014390589970703125. Первый brake step:1.2 до1.195, топливо/оба буфера не меняются. В native скорость стала0 через240 обновлений. Сумма арифметических brake-only скоростей143.4 и native смещение143.3984375 различаются на дискретизацию позиции; точная причина этим опытом не установлена и не менялась в данном исправлении.

Fractional Control, intratick mixed fuel, manual reverse0.6, другие составы/бонусы и механика reservation estimate не измерены этим опытом. Соответствующие core тесты проверяют принятую модель, а не объявляются native oracle. Один этот опыт не закрывает всю совместимость с Factorio1.1.110.
