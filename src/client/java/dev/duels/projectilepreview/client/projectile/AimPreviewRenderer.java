package dev.duels.projectilepreview.client.projectile;

import dev.duels.projectilepreview.client.PreviewConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class AimPreviewRenderer {
    private AimPreviewRenderer() {}

    // Ticks over which the displayed line eases from the hand onto the true path.
    private static final int HAND_BLEND_TICKS = 10;

    public static void render(LevelRenderState state, SubmitNodeCollector collector) {
        PreviewConfig cfg = PreviewConfig.get();
        if (!cfg.enabled) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        Player player = client.player;
        if (player == null) return;

        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return;

        AimProfiles.Profile profile = AimProfiles.match(player, stack);
        if (profile == null) return;

        float tickDelta = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        Vec3 startPos = profile.startPos(player, tickDelta);
        List<Vec3> startVels = profile.startVels(player, stack, tickDelta);
        if (startVels.isEmpty()) return;

        int steps = Math.min(profile.steps(), cfg.maxTicks);
        boolean renderTrajectory = AimPreview.shouldRenderTrajectory();
        Vec3 camPos = state.cameraRenderState.pos;

        for (Vec3 startVel : startVels) {
            TrajectorySim.Result res = TrajectorySim.simulate(
                    player,
                    startPos,
                    startVel,
                    profile.gravity(),
                    profile.drag(),
                    steps,
                    profile.stepTime(),
                    profile.decayBeforeMove()
            );

            if (res == null || res.points().size() < 2) continue;

            if (renderTrajectory) {
                List<Vec3> display = res.points();
                if (cfg.lineOrigin == PreviewConfig.LineOrigin.HAND) {
                    display = blendFromHand(display, profile.visualStartPos(player, tickDelta));
                }
                RenderUtils.drawTrajectory(collector, camPos, display, cfg);
            }

            if (res.hit() != null) {
                RenderUtils.drawHitOverlay(collector, camPos, res.hit(), cfg);
            }
        }
    }

    /**
     * Shifts the first few points toward the hand so the line visually leaves the
     * held item, while the simulated path and impact point stay untouched.
     */
    private static List<Vec3> blendFromHand(List<Vec3> points, Vec3 handPos) {
        Vec3 offset = handPos.subtract(points.get(0));
        int blend = Math.min(HAND_BLEND_TICKS, points.size() - 1);

        List<Vec3> out = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
            if (i < blend) {
                double f = 1.0 - (double) i / blend;
                out.add(points.get(i).add(offset.scale(f)));
            } else {
                out.add(points.get(i));
            }
        }
        return out;
    }
}
