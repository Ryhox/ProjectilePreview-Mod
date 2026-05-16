package dev.duels.projectilepreview.mixin.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import dev.duels.projectilepreview.client.projectile.AimPreview;
import dev.duels.projectilepreview.client.projectile.AimPreviewRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class WorldRendererMixin {
    @Shadow @Final private RenderBuffers renderBuffers;

    @Inject(
            method = "renderLevel",
            at = @At("TAIL")
    )
    private void projectilepreview$render(
            GraphicsResourceAllocator allocator,
            DeltaTracker deltaTracker,
            boolean renderBlockOutline,
            CameraRenderState cameraState,
            Matrix4fc positionMatrix,
            GpuBufferSlice fogBuffer,
            Vector4f fogColor,
            boolean renderSky,
            ChunkSectionsToRender chunkSectionsToRender,
            CallbackInfo ci
    ) {
        if (AimPreview.shouldUseWorldRenderEvents()) return;
        if (deltaTracker == null || positionMatrix == null || renderBuffers == null) return;

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        if (camera == null) return;

        MultiBufferSource.BufferSource consumers = renderBuffers.bufferSource();
        AimPreviewRenderer.render(deltaTracker, camera, new Matrix4f(positionMatrix), consumers);
        consumers.endBatch();
    }
}
