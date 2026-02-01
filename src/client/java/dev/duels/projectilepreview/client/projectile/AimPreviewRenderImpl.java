package dev.duels.projectilepreview.client.projectile;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public final class AimPreviewRenderImpl {
    private AimPreviewRenderImpl() {}

    public static void render(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        PlayerEntity player = client.player;
        if (player == null) return;

        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty()) return;

        AimProfiles.Profile profile = AimProfiles.match(player, stack);
        if (profile == null) return;

        Vec3d startPos = profile.startPos(player, tickDelta);
        List<Vec3d> startVels = profile.startVels(player, stack, tickDelta);
        if (startVels == null || startVels.isEmpty()) return;

        Vec3d camPos = camera.getPos();

        for (Vec3d startVel : startVels) {
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

            RenderUtils.drawPolyline(matrices, consumers, res.points(), camPos);

            if (res.hit() != null) {
                RenderUtils.drawHitOverlay(matrices, consumers, res.hit(), camPos);
            }
        }
    }
}
