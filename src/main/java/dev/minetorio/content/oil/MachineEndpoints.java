package dev.minetorio.content.oil;

import dev.minetorio.core.inserter.PickupSource;
import dev.minetorio.core.machine.CraftingMachine;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/** Inserter and drop helpers shared by the crafting machines' block entities. */
final class MachineEndpoints {
    private MachineEndpoints() {}

    /** Offers the machine's outputs, one item at a time; {@code changed} runs after each take. */
    static PickupSource outputs(CraftingMachine machine, Runnable changed) {
        return new PickupSource() {
            @Override
            public Optional<String> peek(Predicate<String> wanted) {
                return machine.outputs().keySet().stream().filter(wanted).findFirst();
            }

            @Override
            public boolean take(String item) {
                if (machine.extractOutput(item, 1) != 1) return false;
                changed.run();
                return true;
            }
        };
    }

    /**
     * Empties the machine (recipe cleared): returns its stored inputs, the inputs of a craft in
     * progress and its outputs.
     */
    static Map<String, Integer> empty(CraftingMachine machine) {
        machine.setRecipe(null);
        Map<String, Integer> all = new LinkedHashMap<>(machine.drainInputs());
        machine.outputs().forEach((id, n) -> all.merge(id, machine.extractOutput(id, n), Integer::sum));
        return all;
    }
}

