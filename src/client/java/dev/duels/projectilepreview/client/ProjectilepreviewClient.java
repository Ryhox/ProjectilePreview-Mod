package dev.duels.projectilepreview.client;

import net.fabricmc.api.ClientModInitializer;
import dev.duels.projectilepreview.client.projectile.AimPreview;

public final class ProjectilepreviewClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AimPreview.register();
        DebugCommands.init(); // ist standardmäßig aus
    }
}
