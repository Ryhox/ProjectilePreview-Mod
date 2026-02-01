package dev.duels.projectilepreview.client.projectile;

public final class StartPosTuning {
    private StartPosTuning() {}

    public static volatile double BOW_F = 0.45,     BOW_S = -0.35, BOW_U = -0.08;
    public static volatile double CROSS_F = 0.10,   CROSS_S = 0.00,  CROSS_U = -0.08;
    public static volatile double TRIDENT_F = 0.10, TRIDENT_S = -0.10, TRIDENT_U = 0.025;
    public static volatile double THROW_F = 0.15,   THROW_S = -0.20, THROW_U = -0.10;
    public static volatile double WIND_F = 0.15,    WIND_S = -0.20,  WIND_U = -0.10;

    public static void set(String profile, double f, double s, double u) {
        switch (profile.toLowerCase()) {
            case "bow" -> { BOW_F = f; BOW_S = s; BOW_U = u; }
            case "crossbow" -> { CROSS_F = f; CROSS_S = s; CROSS_U = u; }
            case "trident" -> { TRIDENT_F = f; TRIDENT_S = s; TRIDENT_U = u; }
            case "throwable" -> { THROW_F = f; THROW_S = s; THROW_U = u; }
            case "wind" -> { WIND_F = f; WIND_S = s; WIND_U = u; }
            default -> throw new IllegalArgumentException("Unknown profile: " + profile);
        }
    }
}
