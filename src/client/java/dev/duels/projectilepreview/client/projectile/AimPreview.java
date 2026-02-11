package dev.duels.projectilepreview.client.projectile;

public final class AimPreview {
    private AimPreview() {}

    public static void register() {
        // 1.21.9+ uses the WorldRenderer mixin hook.
    }

    public static boolean shouldUseWorldRenderEvents() {
        return false;
    }
}
