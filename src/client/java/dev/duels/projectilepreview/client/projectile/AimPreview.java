package dev.duels.projectilepreview.client.projectile;

import net.fabricmc.loader.api.FabricLoader;

public final class AimPreview {
    private AimPreview() {}

    public static void register() {
        String mc = FabricLoader.getInstance()
                .getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("");

        // 1.21.2–1.21.8 -> WorldRenderEvents exist
        if (isBetweenInclusivePatch(mc, "1.21", 2, 8)) {
            AimPreviewWorldRenderEvents.register();
            return;
        }

        // 1.21.9–1.21.11 -> we use mixin, so do NOTHING here
    }

    private static boolean isBetweenInclusivePatch(String version, String prefix, int minPatch, int maxPatch) {
        if (version == null) return false;
        if (!version.startsWith(prefix + ".")) return false;

        String tail = version.substring((prefix + ".").length()); // "2", "8", "10-rc1"
        int dash = tail.indexOf('-');
        if (dash >= 0) tail = tail.substring(0, dash);

        int patch;
        try {
            patch = Integer.parseInt(tail);
        } catch (NumberFormatException e) {
            return false;
        }
        return patch >= minPatch && patch <= maxPatch;
    }
}
