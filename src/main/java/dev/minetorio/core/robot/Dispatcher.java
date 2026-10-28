package dev.minetorio.core.robot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToIntBiFunction;

/** Hands pending jobs to free robots: build and upgrade jobs one provider each, deconstruction jobs where there is storage. */
public final class Dispatcher {
    private Dispatcher() {}

    /** A job for one robot; {@code provider} is where it fetches the job's item (null for deconstruction). */
    public record Assignment(ConstructionQueue.Job job, Cell provider) {}

    /** {@link #plan(List, int, List, ToIntBiFunction, ProviderLedger, Predicate)} without storage: deconstruction jobs wait. */
    public static List<Assignment> plan(List<ConstructionQueue.Job> pending, int robots, List<Cell> providers,
                                        ToIntBiFunction<Cell, String> stock, ProviderLedger ledger) {
        return plan(pending, robots, providers, stock, ledger, j -> false);
    }

    /**
     * Up to {@code robots} assignments, in queue order. A build or upgrade job whose item (times its
     * count) no provider holds beyond reservations is skipped and stays pending; each such
     * assignment reserves its items in {@code ledger}. A deconstruction job is handed out when
     * {@code storable} says what comes out of it fits into some storage (Factorio robots do not
     * deconstruct without a place to put things).
     */
    public static List<Assignment> plan(List<ConstructionQueue.Job> pending, int robots, List<Cell> providers,
                                        ToIntBiFunction<Cell, String> stock, ProviderLedger ledger,
                                        Predicate<ConstructionQueue.Job> storable) {
        List<Assignment> out = new ArrayList<>();
        for (ConstructionQueue.Job job : pending) {
            if (out.size() >= robots) break;
            if (job.kind() == ConstructionQueue.Kind.DECONSTRUCT) {
                if (storable.test(job)) out.add(new Assignment(job, null));
                continue;
            }
            Optional<Cell> p = ledger.pick(providers, job.cell(), job.item(), job.count(), stock);
            if (p.isEmpty()) continue;
            for (int i = 0; i < job.count(); i++) ledger.reserve(p.get(), job.item());
            out.add(new Assignment(job, p.get()));
        }
        return out;
    }
}
