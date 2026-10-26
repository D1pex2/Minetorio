package dev.minetorio.core.deposit;

/** A circular ore patch in the XZ plane (height is irrelevant). */
public record Deposit(long id, OreType type, int centerX, int centerZ, int radius, long initialAmount) {
    public boolean contains(int x, int z) {
        long dx = (long) x - centerX;
        long dz = (long) z - centerZ;
        return dx * dx + dz * dz <= (long) radius * radius;
    }
}
