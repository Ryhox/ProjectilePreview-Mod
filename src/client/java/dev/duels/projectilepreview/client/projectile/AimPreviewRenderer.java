package dev.duels.projectilepreview.client.projectile;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

public final class AimPreviewRenderer {
    private AimPreviewRenderer() {}

    public static void render(DeltaTracker deltaTracker, Camera camera, Matrix4f positionMatrix, MultiBufferSource consumers) {
        if (deltaTracker == null || camera == null || positionMatrix == null || consumers == null) return;

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
        if (startVels == null || startVels.isEmpty()) return;

        Vec3 camPos = camera.position();
        boolean renderTrajectory = AimPreview.shouldRenderTrajectory();

        for (Vec3 startVel : startVels) {
            if (startVel == null) continue;

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
                RenderUtils.drawPolyline(positionMatrix, consumers, res.points(), camPos);
            }

            if (res.hit() != null) {
                RenderUtils.drawHitOverlay(positionMatrix, consumers, res.hit(), camPos);
            }
        }
    }
}
