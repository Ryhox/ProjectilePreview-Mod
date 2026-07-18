package dev.duels.projectilepreview.client.projectile;

import net.fabricmc.loader.api.FabricLoader;

public final class AimPreview {
    private AimPreview() {}

    private static final boolean PTP_LOADED = FabricLoader.getInstance().isModLoaded("ptp");

    // Skip our trajectory line when Projectiles Trajectory Prediction already draws one.
    public static boolean shouldRenderTrajectory() {
        return !PTP_LOADED;
    }
}
