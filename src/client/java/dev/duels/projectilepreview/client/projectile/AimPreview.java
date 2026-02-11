package dev.duels.projectilepreview.client.projectile;

import net.fabricmc.loader.api.FabricLoader;

public final class AimPreview {
    private AimPreview() {}

    private static final boolean PTP_LOADED = FabricLoader.getInstance().isModLoaded("ptp");

    public static void register() {
        // 1.21.9+ uses the WorldRenderer mixin hook.
    }

    public static boolean shouldUseWorldRenderEvents() {
        return false;
    }

    public static boolean shouldRenderTrajectory() {
        // Avoid duplicate trajectory lines when Projectiles Trajectory Prediction is installed.
        return !PTP_LOADED;
    }
}
