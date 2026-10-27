package dev.minetorio.core.robot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Construction jobs of one world: ghosts to build and blocks marked for deconstruction, at most one
 * job per cell. Jobs are handed out in placement order ({@link Job#seq}, saved with the ghost so it
 * survives reloads). A robot {@link #claim claims} a job while it works on it; only the claimant
 * releases it, and removing the job drops the claim.
 *
 * <p>Deconstruction is only a flag for now (the planner item comes with blueprints): robots do not
 * take deconstruction jobs yet ({@link Dispatcher} skips them).
 */
public final class ConstructionQueue {
    public enum Kind { BUILD, DECONSTRUCT }

    /** A job; {@code item} is the item to place (null for deconstruction); {@code claimant} 0 = free. */
    public static final class Job {
        private final Cell cell;
        private final Kind kind;
        private final String item;
        private final long seq;
        private long claimant;

        private Job(Cell cell, Kind kind, String item, long seq) {
            this.cell = cell;
            this.kind = kind;
            this.item = item;
            this.seq = seq;
        }

        public Cell cell() { return cell; }
        public Kind kind() { return kind; }
        public String item() { return item; }
        public long seq() { return seq; }
        public long claimant() { return claimant; }
        public boolean isClaimed() { return claimant != 0; }
    }

    private final Map<Cell, Job> jobs = new HashMap<>();
    private long nextSeq = 1;

    /** A new ghost at the end of the queue. */
    public Job addBuild(Cell cell, String item) {
        return addBuild(cell, item, nextSeq);
    }

    /**
     * A ghost with a known sequence number (loaded from save). Re-adding the same ghost (chunk
     * reload) keeps the job and its claim.
     */
    public Job addBuild(Cell cell, String item, long seq) {
        Job old = jobs.get(cell);
        if (old != null && old.kind == Kind.BUILD && old.seq == seq && java.util.Objects.equals(old.item, item)) return old;
        Job job = new Job(cell, Kind.BUILD, item, seq);
        jobs.put(cell, job);
        nextSeq = Math.max(nextSeq, seq + 1);
        return job;
    }

    /** The next sequence number {@link #addBuild(Cell, String)} would use. */
    public long nextSeq() { return nextSeq; }

    /** Marks the block at {@code cell} for deconstruction (replaces nothing if a job is there). */
    public Job markForDeconstruction(Cell cell) {
        Job old = jobs.get(cell);
        if (old != null) return old;
        Job job = new Job(cell, Kind.DECONSTRUCT, null, nextSeq++);
        jobs.put(cell, job);
        return job;
    }

    public boolean isMarkedForDeconstruction(Cell cell) {
        Job j = jobs.get(cell);
        return j != null && j.kind == Kind.DECONSTRUCT;
    }

    /** Removes a deconstruction mark; false if there was none. */
    public boolean unmarkDeconstruction(Cell cell) {
        if (!isMarkedForDeconstruction(cell)) return false;
        jobs.remove(cell);
        return true;
    }

    public void remove(Cell cell) { jobs.remove(cell); }

    public Optional<Job> get(Cell cell) { return Optional.ofNullable(jobs.get(cell)); }

    public int size() { return jobs.size(); }

    /** Claims the free job at {@code cell} for robot {@code robot} (non-zero). */
    public boolean claim(Cell cell, long robot) {
        if (robot == 0) throw new IllegalArgumentException("robot id 0");
        Job j = jobs.get(cell);
        if (j == null || (j.claimant != 0 && j.claimant != robot)) return false;
        j.claimant = robot;
        return true;
    }

    /** Frees the job at {@code cell} if {@code robot} holds it. */
    public boolean release(Cell cell, long robot) {
        Job j = jobs.get(cell);
        if (j == null || j.claimant != robot) return false;
        j.claimant = 0;
        return true;
    }

    /** Free jobs whose cell passes {@code where}, in queue order. */
    public List<Job> pending(Predicate<Cell> where) {
        return pending(null, where);
    }

    /** Free jobs of {@code kind} (null: any) whose cell passes {@code where}, in queue order. */
    public List<Job> pending(Kind kind, Predicate<Cell> where) {
        List<Job> out = new ArrayList<>();
        for (Job j : jobs.values()) {
            if (!j.isClaimed() && (kind == null || j.kind == kind) && where.test(j.cell)) out.add(j);
        }
        out.sort(Comparator.comparingLong(Job::seq));
        return out;
    }
}
