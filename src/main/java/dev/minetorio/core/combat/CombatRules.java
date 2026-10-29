package dev.minetorio.core.combat;

/** Единицы и базовая физика боя. Источник: официальный factorio-data, тег 1.1.110. */
public final class CombatRules {
    public static final int MAGAZINE_SIZE = 10;
    public static final double HEALTH_SCALE = 20.0 / 250;
    private CombatRules() {}

    public enum Gun {
        PISTOL(5, 15, 0.2), SUBMACHINE_GUN(2, 18, 0.7);
        public final int cooldownTicks;
        public final double range;
        public final double slowDown;
        Gun(int cooldownTicks, double range, double slowDown) { this.cooldownTicks = cooldownTicks; this.range = range; this.slowDown = slowDown; }
    }
    public static float health(double factorio) { return (float) (factorio * HEALTH_SCALE); }
    /** Формула Official Factorio Wiki, Damage#Resistance, oldid=220259; Lua задает только R и P. */
    public static double physicalDamage(double damage, double decrease, double percent) {
        double reduced = damage > decrease + 1 ? damage - decrease
                : damage > 1 ? 1 / (decrease - damage + 2) : 1 / (decrease + 1);
        return reduced * (1 - percent / 100);
    }
    /** Остаток после одного выстрела; ноль означает израсходованный магазин. */
    public static int consumeRound(int remaining) {
        if (remaining < 1 || remaining > MAGAZINE_SIZE) throw new IllegalArgumentException("Некорректный остаток магазина");
        return remaining - 1;
    }
    public static boolean canFire(long now, long nextShot) { return now >= nextShot; }
    /** Убирает только наш вклад в формулу AbstractClientPlayer.getFieldOfViewModifier 1.21.1. */
    public static float fovCorrection(float rawFov, double speed, double walkSpeed, double ownMultiplier, double effectScale) {
        if (walkSpeed <= 0 || ownMultiplier <= 0) return 0;
        double with = (speed / walkSpeed + 1) / 2;
        double without = (speed / ownMultiplier / walkSpeed + 1) / 2;
        return (float)(rawFov * (without / with - 1) * effectScale);
    }
}
