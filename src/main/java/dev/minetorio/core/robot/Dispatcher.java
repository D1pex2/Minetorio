package dev.minetorio.core.robot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntBiFunction;

/** Hands pending build jobs to free robots, one provider per job. */
public final class Dispatcher {
    private Dispatcher() {}

    public record Assignment(ConstructionQueue.Job job, Cell provider) {}

    /**
     * Up to {@code robots} assignments, in queue order. A job whose item no provider holds (beyond
     * reservations) is skipped and stays pending; each assignment reserves its item in {@code ledger}.
     * Deconstruction jobs are skipped (not handled by robots yet).
     */
    public static List<Assignment> plan(List<ConstructionQueue.Job> pending, int robots, List<Cell> providers,
                                        ToIntBiFunction<Cell, String> stock, ProviderLedger ledger) {
        List<Assignment> out = new ArrayList<>();
        for (ConstructionQueue.Job job : pending) {
            if (out.size() >= robots) break;
            if (job.kind() != ConstructionQueue.Kind.BUILD) continue;
            Optional<Cell> p = ledger.pick(providers, job.cell(), job.item(), stock);
            if (p.isEmpty()) continue;
            ledger.reserve(p.get(), job.item());
            out.add(new Assignment(job, p.get()));
        }
        return out;
    }
}
