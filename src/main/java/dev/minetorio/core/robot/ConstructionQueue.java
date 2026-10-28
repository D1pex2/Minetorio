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
 * <p>Three kinds of jobs: {@link Kind#BUILD} (a ghost, needs its item from a provider),
 * {@link Kind#DECONSTRUCT} (a block marked with the deconstruction planner, needs room in a
 * storage chest for what comes out) and {@link Kind#UPGRADE} (a block marked with the upgrade
 * planner, needs {@link Job#count} of its new item). The marks themselves live in
 * {@link DeconstructionMarks} / {@link UpgradeMarks}; the content side mirrors them into the queue.
 */
public final class ConstructionQueue {
    public enum Kind { BUILD, DECONSTRUCT, UPGRADE }

    /**
     * A job; {@code item} is the item to place (null for deconstruction), {@code count} how many of
     * it the job takes (1 but for a paired underground belt upgrade); {@code claimant} 0 = free.
     */
    public static final class Job {
        private final Cell cell;
        private final Kind kind;
        private final String item;
        private final int count;
        private final long seq;
        private long claimant;

        private Job(Cell cell, Kind kind, String item, long seq) {
            this(cell, kind, item, 1, seq);
        }

        private Job(Cell cell, Kind kind, String item, int count, long seq) {
            this.cell = cell;
            this.kind = kind;
            this.item = item;
            this.count = Math.max(1, count);
            this.seq = seq;
        }

        public int count() { return count; }

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

    /** Marks the block at {@code cell} for deconstruction (replaces an upgrade job, nothing else). */
    public Job markForDeconstruction(Cell cell) {
        Job old = jobs.get(cell);
        if (old != null && old.kind != Kind.UPGRADE) return old;
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

    /**
     * Marks the block at {@code cell} for an upgrade to {@code count} × {@code item}. A deconstruction
     * job there wins (nothing changes); an older upgrade job is replaced (its claim dropped).
     */
    public Job markForUpgrade(Cell cell, String item, int count) {
        Job old = jobs.get(cell);
        if (old != null && old.kind == Kind.UPGRADE && old.item.equals(item) && old.count == count) return old;
        if (old != null && old.kind != Kind.UPGRADE) return old;
        Job job = new Job(cell, Kind.UPGRADE, item, count, nextSeq++);
        jobs.put(cell, job);
        return job;
    }

    public boolean isMarkedForUpgrade(Cell cell) {
        Job j = jobs.get(cell);
        return j != null && j.kind == Kind.UPGRADE;
    }

    /** Removes an upgrade job; false if there was none. */
    public boolean unmarkUpgrade(Cell cell) {
        if (!isMarkedForUpgrade(cell)) return false;
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
