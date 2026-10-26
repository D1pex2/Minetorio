package dev.minetorio.core.deposit;

import java.util.HashMap;
import java.util.Map;

/** Tracks how much has been mined from each deposit, by deposit id. */
public final class DepositLedger {
    private final Map<Long, Long> mined = new HashMap<>();

    public long remaining(Deposit d) {
        return Math.max(0, d.initialAmount() - mined.getOrDefault(d.id(), 0L));
    }

    /** Removes up to {@code amount} from the deposit; returns the amount actually mined. */
    public long mine(Deposit d, long amount) {
        if (amount <= 0) return 0;
        long taken = Math.min(amount, remaining(d));
        if (taken > 0) mined.merge(d.id(), taken, Long::sum);
        return taken;
    }

    public Map<Long, Long> minedById() {
        return new HashMap<>(mined);
    }

    public void load(Map<Long, Long> data) {
        mined.clear();
        mined.putAll(data);
    }
}
