package dev.duels.projectilepreview.client.projectile;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

public final class AimPreviewWorldRenderEvents {
    private AimPreviewWorldRenderEvents() {}

    public static void register() {
        WorldRenderEvents.LAST.register(AimPreviewRenderer::render);
    }
}
