package dev.duels.projectilepreview.mixin.client;

import dev.duels.projectilepreview.client.projectile.AimPreviewRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    // Submit our geometry alongside the vanilla features, same frame, same pipeline.
    @Inject(method = "submitFeatures", at = @At("HEAD"))
    private void projectilepreview$submit(LevelRenderState state, SubmitNodeCollector collector, boolean renderBlockOutline, CallbackInfo ci) {
        AimPreviewRenderer.render(state, collector);
    }
}
