package dev.duels.projectilepreview.client.projectile;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class AimPreviewRenderer {
    private AimPreviewRenderer() {}

    public static void render(DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        Player player = client.player;
        if (player == null) return;

        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return;

        AimProfiles.Profile profile = AimProfiles.match(player, stack);
        if (profile == null) return;

        float tickDelta = deltaTracker.getGameTimeDeltaPartialTick(false);

        Vec3 startPos = profile.startPos(player, tickDelta);
        List<Vec3> startVels = profile.startVels(player, stack, tickDelta);
        if (startVels.isEmpty()) return;

        boolean renderTrajectory = AimPreview.shouldRenderTrajectory();

        for (Vec3 startVel : startVels) {
            TrajectorySim.Result res = TrajectorySim.simulate(
                    player,
                    startPos,
                    startVel,
                    profile.gravity(),
                    profile.drag(),
                    profile.steps(),
                    profile.stepTime()
            );

            if (res == null || res.points().size() < 2) continue;

            if (renderTrajectory) {
                RenderUtils.drawPolyline(res.points());
            }

            if (res.hit() != null) {
                RenderUtils.drawHitOverlay(res.hit());
            }
        }
    }
}
