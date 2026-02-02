package dev.duels.projectilepreview.client.projectile;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.joml.Matrix4f;

import java.util.List;

import static dev.duels.projectilepreview.client.projectile.TrajectorySim.ENTITY_HITBOX_PAD;

public final class RenderUtils {
    private RenderUtils() {}

    private static final float TRAJ_THICKNESS = 0.002f;

    private static final float OUTLINE_THICKNESS = 0.01f;

    private static final float FACE_EPS = 0.0008f;


    private static final double OUTLINE_PUSH = 0.0006;

    public static void drawPolyline(MatrixStack matrices, VertexConsumerProvider consumers, List<Vec3d> points, Vec3d camPos) {
        if (points.size() < 2) return;

        VertexConsumer vc = consumers.getBuffer(PreviewRenderLayers.FILLED_QUADS);
        Matrix4f m = matrices.peek().getPositionMatrix();

        for (int i = 0; i < points.size() - 1; i++) {
            Vec3d a = points.get(i).subtract(camPos);
            Vec3d b = points.get(i + 1).subtract(camPos);
            addThickLine(vc, m, a, b, TRAJ_THICKNESS, 255, 255, 255, 255);
        }
    }

    public static void drawHitOverlay(MatrixStack matrices, VertexConsumerProvider consumers, TrajectorySim.HitInfo hit, Vec3d camPos) {
        if (hit == null) return;

        int aFill = 80;
        int aLine = 255;

        // IMPORTANT:
        // Your outline quads sit exactly on block surfaces -> depth test makes them flicker/disappear.
        // We render overlays with depth test OFF + depthMask OFF so they are always visible,
        // but still “tight” because we do NOT inflate the voxelshape (only a micro OUTLINE_PUSH).
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        try {
            if (hit instanceof TrajectorySim.HitInfo.BlockHit bh) {
                BlockHitResult bhr = bh.bhr();

                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.world == null) return;

                BlockPos pos = bhr.getBlockPos();
                BlockState state = mc.world.getBlockState(pos);

                VoxelShape shape = state.getOutlineShape(mc.world, pos, net.minecraft.block.ShapeContext.absent());
                if (shape == null || shape.isEmpty()) shape = VoxelShapes.fullCube();

                Vec3d hitPosCam = hit.pos().subtract(camPos);

                // Fill: only the closest sub-box face (avoids filling random internal stair pieces)
                Box closest = null;
                double bestD2 = Double.POSITIVE_INFINITY;

                for (Box local : shape.getBoundingBoxes()) {
                    Box worldBox = local.offset(pos.getX(), pos.getY(), pos.getZ());
                    Box camBox = worldBox.offset(-camPos.x, -camPos.y, -camPos.z);

                    double d2 = dist2PointToBox(hitPosCam, camBox);
                    if (d2 < bestD2) {
                        bestD2 = d2;
                        closest = camBox;
                    }
                }

                if (closest != null) {
                    drawFaceFill(matrices, consumers, closest, hitPosCam, 80, 160, 255, aFill);
                }

                // Outline: draw ALL sub-boxes (whole stair / slab / shape)
                // We push outward slightly so it’s not “inside” the block surface.
                for (Box local : shape.getBoundingBoxes()) {
                    Box worldBox = local.offset(pos.getX(), pos.getY(), pos.getZ());
                    Box camBox = worldBox.offset(-camPos.x, -camPos.y, -camPos.z);

                    Box outlineBox = camBox.expand(OUTLINE_PUSH);
                    drawOutline(matrices, consumers, outlineBox, OUTLINE_THICKNESS, 80, 160, 255, aLine);
                }

                return;
            }

            if (hit instanceof TrajectorySim.HitInfo.EntityHit eh) {
                Entity e = eh.entity();

                Box bbWorld = e.getBoundingBox().expand(ENTITY_HITBOX_PAD);
                Box bbCam = bbWorld.offset(-camPos.x, -camPos.y, -camPos.z);
                Vec3d hitPosCam = hit.pos().subtract(camPos);

                int[] col = colorForEntity(e);

                drawFaceFill(matrices, consumers, bbCam, hitPosCam, col[0], col[1], col[2], aFill);

                Box outlineBox = bbCam.expand(OUTLINE_PUSH);
                drawOutline(matrices, consumers, outlineBox, OUTLINE_THICKNESS, col[0], col[1], col[2], aLine);
            }
        } finally {
            // Restore render state so you don’t break other rendering
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
        }
    }

    private static void drawFaceFill(
            MatrixStack matrices,
            VertexConsumerProvider consumers,
            Box bbCam,
            Vec3d hitPosCam,
            int r, int g, int b,
            int aFill
    ) {
        Direction face = nearestFace(bbCam, hitPosCam);
        VertexConsumer fill = consumers.getBuffer(PreviewRenderLayers.FILLED_QUADS);
        drawFaceQuad(matrices, fill, bbCam, face, r, g, b, aFill, FACE_EPS);
    }

    private static void drawOutline(
            MatrixStack matrices,
            VertexConsumerProvider consumers,
            Box b,
            float thickness,
            int r, int g, int bl, int aLine
    ) {
        VertexConsumer vc = consumers.getBuffer(PreviewRenderLayers.FILLED_QUADS);
        Matrix4f m = matrices.peek().getPositionMatrix();

        Vec3d p000 = new Vec3d(b.minX, b.minY, b.minZ);
        Vec3d p001 = new Vec3d(b.minX, b.minY, b.maxZ);
        Vec3d p010 = new Vec3d(b.minX, b.maxY, b.minZ);
        Vec3d p011 = new Vec3d(b.minX, b.maxY, b.maxZ);

        Vec3d p100 = new Vec3d(b.maxX, b.minY, b.minZ);
        Vec3d p101 = new Vec3d(b.maxX, b.minY, b.maxZ);
        Vec3d p110 = new Vec3d(b.maxX, b.maxY, b.minZ);
        Vec3d p111 = new Vec3d(b.maxX, b.maxY, b.maxZ);

        addThickLine(vc, m, p000, p001, thickness, r, g, bl, aLine);
        addThickLine(vc, m, p001, p101, thickness, r, g, bl, aLine);
        addThickLine(vc, m, p101, p100, thickness, r, g, bl, aLine);
        addThickLine(vc, m, p100, p000, thickness, r, g, bl, aLine);

        addThickLine(vc, m, p010, p011, thickness, r, g, bl, aLine);
        addThickLine(vc, m, p011, p111, thickness, r, g, bl, aLine);
        addThickLine(vc, m, p111, p110, thickness, r, g, bl, aLine);
        addThickLine(vc, m, p110, p010, thickness, r, g, bl, aLine);

        addThickLine(vc, m, p000, p010, thickness, r, g, bl, aLine);
        addThickLine(vc, m, p001, p011, thickness, r, g, bl, aLine);
        addThickLine(vc, m, p100, p110, thickness, r, g, bl, aLine);
        addThickLine(vc, m, p101, p111, thickness, r, g, bl, aLine);
    }

    private static Direction nearestFace(Box b, Vec3d hit) {
        double dxMin = Math.abs(hit.x - b.minX);
        double dxMax = Math.abs(hit.x - b.maxX);
        double dyMin = Math.abs(hit.y - b.minY);
        double dyMax = Math.abs(hit.y - b.maxY);
        double dzMin = Math.abs(hit.z - b.minZ);
        double dzMax = Math.abs(hit.z - b.maxZ);

        Direction best = Direction.WEST;
        double bestD = dxMin;

        if (dxMax < bestD) { bestD = dxMax; best = Direction.EAST; }
        if (dyMin < bestD) { bestD = dyMin; best = Direction.DOWN; }
        if (dyMax < bestD) { bestD = dyMax; best = Direction.UP; }
        if (dzMin < bestD) { bestD = dzMin; best = Direction.NORTH; }
        if (dzMax < bestD) { bestD = dzMax; best = Direction.SOUTH; }

        return best;
    }

    private static void drawFaceQuad(
            MatrixStack matrices,
            VertexConsumer vc,
            Box b,
            Direction face,
            int r, int g, int bl, int a,
            float eps
    ) {
        Matrix4f m = matrices.peek().getPositionMatrix();

        float x1 = (float) b.minX, x2 = (float) b.maxX;
        float y1 = (float) b.minY, y2 = (float) b.maxY;
        float z1 = (float) b.minZ, z2 = (float) b.maxZ;

        switch (face) {
            case WEST  -> { float x = x1 - eps; quad(vc, m, x,y1,z1, x,y2,z1, x,y2,z2, x,y1,z2, r,g,bl,a); }
            case EAST  -> { float x = x2 + eps; quad(vc, m, x,y1,z2, x,y2,z2, x,y2,z1, x,y1,z1, r,g,bl,a); }
            case NORTH -> { float z = z1 - eps; quad(vc, m, x2,y1,z, x2,y2,z, x1,y2,z, x1,y1,z, r,g,bl,a); }
            case SOUTH -> { float z = z2 + eps; quad(vc, m, x1,y1,z, x1,y2,z, x2,y2,z, x2,y1,z, r,g,bl,a); }
            case DOWN  -> { float y = y1 - eps; quad(vc, m, x1,y,z2, x2,y,z2, x2,y,z1, x1,y,z1, r,g,bl,a); }
            case UP    -> { float y = y2 + eps; quad(vc, m, x1,y,z1, x2,y,z1, x2,y,z2, x1,y,z2, r,g,bl,a); }
        }
    }

    private static void quad(
            VertexConsumer vc,
            Matrix4f m,
            float ax, float ay, float az,
            float bx, float by, float bz,
            float cx, float cy, float cz,
            float dx, float dy, float dz,
            int r, int g, int b, int a
    ) {
        vc.vertex(m, ax, ay, az).color(r, g, b, a);
        vc.vertex(m, bx, by, bz).color(r, g, b, a);
        vc.vertex(m, cx, cy, cz).color(r, g, b, a);
        vc.vertex(m, dx, dy, dz).color(r, g, b, a);
    }

    private static int[] colorForEntity(Entity e) {
        if (e instanceof PlayerEntity) return new int[]{180, 90, 255};
        if (e instanceof PassiveEntity) return new int[]{60, 255, 120};
        if (e instanceof HostileEntity) return new int[]{255, 70, 70};
        return new int[]{255, 165, 60};
    }

    private static void addThickLine(
            VertexConsumer vc,
            Matrix4f m,
            Vec3d a,
            Vec3d b,
            float thickness,
            int r, int g, int bl, int aCol
    ) {
        Vec3d ab = b.subtract(a);
        double len = ab.length();
        if (len < 1e-6) return;

        Vec3d dir = ab.multiply(1.0 / len);

        Vec3d mid = a.add(b).multiply(0.5);
        Vec3d view = mid.negate();

        Vec3d side = dir.crossProduct(view);
        double sideLen = side.length();
        if (sideLen < 1e-6) {
            Vec3d up = new Vec3d(0, 1, 0);
            side = dir.crossProduct(up);
            sideLen = side.length();
            if (sideLen < 1e-6) {
                Vec3d right = new Vec3d(1, 0, 0);
                side = dir.crossProduct(right);
                sideLen = side.length();
                if (sideLen < 1e-6) return;
            }
        }

        side = side.multiply(thickness / sideLen);

        Vec3d a1 = a.add(side);
        Vec3d a2 = a.subtract(side);
        Vec3d b1 = b.add(side);
        Vec3d b2 = b.subtract(side);

        vc.vertex(m, (float) a1.x, (float) a1.y, (float) a1.z).color(r, g, bl, aCol);
        vc.vertex(m, (float) b1.x, (float) b1.y, (float) b1.z).color(r, g, bl, aCol);
        vc.vertex(m, (float) b2.x, (float) b2.y, (float) b2.z).color(r, g, bl, aCol);
        vc.vertex(m, (float) a2.x, (float) a2.y, (float) a2.z).color(r, g, bl, aCol);
    }

    // squared distance from a point to an AABB (0 if inside)
    private static double dist2PointToBox(Vec3d p, Box b) {
        double dx = 0.0;
        if (p.x < b.minX) dx = b.minX - p.x;
        else if (p.x > b.maxX) dx = p.x - b.maxX;

        double dy = 0.0;
        if (p.y < b.minY) dy = b.minY - p.y;
        else if (p.y > b.maxY) dy = p.y - b.maxY;

        double dz = 0.0;
        if (p.z < b.minZ) dz = b.minZ - p.z;
        else if (p.z > b.maxZ) dz = p.z - b.maxZ;

        return dx * dx + dy * dy + dz * dz;
    }
}
