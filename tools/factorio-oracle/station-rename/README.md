# Station rename oracle 1.0.0

Один headless эксперимент для Factorio 1.1.110 build 62357. Мод использует стандартные прототипы; data.lua ничего не меняет. rename.sh создает новый save и отдельный mod-directory, затем выполняет 60 benchmark ticks. Повторять только в свежем окружении: существующие rename.zip и rename-mods считаются ошибкой.

control.lua создает три независимых baseline с разными именами остановок. В каждом есть неподвижные manual и AUTO поезда без топлива с current=2 и повторной записью цели в расписании. После baseline harness присваивает только backer_name. Каждый setter окружен снимками до, внутри настоящего on_entity_renamed, сразу после и через один/два тика. on_train_schedule_changed только наблюдается. Поддельные события не поднимаются.

Фазы: одна surface/force и unique/first duplicate/last duplicate; две surfaces одной force; одна surface с двумя forces. Результат и ограничения: [verification](../../../docs/verification/factorio-1.1.110-station-rename.md). Setter с by_script=true не заменяет GUI gate с by_script=false.

Исходные JSON объекты в rail-station-rename-native.json перенесены из serial stdout как байты без повторной сериализации и изменения числовых литералов. Runtime runner, raw, preflight/postflight и analysis находятся вне Git в runtime/factorio-oracle-1.1.110. Порядок безопасного запуска и суммы записаны в verification.
