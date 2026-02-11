package dev.duels.projectilepreview.client.projectile;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.lang.reflect.Method;
import java.util.List;

import static dev.duels.projectilepreview.client.projectile.TrajectorySim.ENTITY_HITBOX_PAD;

public final class RenderUtils {
    private RenderUtils() {}

    // Face fill sits slightly above the surface to avoid z-fighting.
    private static final float FACE_EPS = 0.002f;

    // Push outlines slightly outward so they don't end up inside the block.
    private static final double OUTLINE_PUSH = 0.01;

    private static final double HITBOX_BORDER_GROW = 0.0;
    private static final float LINE_WIDTH = 5.0f;

    public static void drawPolyline(Matrix4f m, VertexConsumerProvider consumers, List<Vec3d> points, Vec3d camPos) {
        if (points == null || points.size() < 2) return;

        VertexConsumer vc = consumers.getBuffer(RenderLayers.lines());

        for (int i = 0; i < points.size() - 1; i++) {
            Vec3d a = points.get(i).subtract(camPos);
            Vec3d b = points.get(i + 1).subtract(camPos);
            line(vc, m, a, b, 255, 255, 255, 255);
        }
    }

    public static void drawHitOverlay(Matrix4f m, VertexConsumerProvider consumers, TrajectorySim.HitInfo hit, Vec3d camPos) {
        if (hit == null) return;

        // These names vary between patches; reflection avoids NoSuchMethodError.
        rsCall("enableBlend");
        rsCall("disableCull");
        rsCall("disableDepthTest");
        rsCall("depthMask", boolean.class, false);

        try {
            if (hit instanceof TrajectorySim.HitInfo.BlockHit bh) {
                BlockHitResult bhr = bh.bhr();

                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.world == null) return;

                BlockPos pos = bhr.getBlockPos();
                Box camBox = new Box(pos).offset(-camPos.x, -camPos.y, -camPos.z);
                Box outlineBox = camBox.expand(OUTLINE_PUSH + HITBOX_BORDER_GROW);
                drawBoxOutlineLines(m, consumers, outlineBox, 80, 160, 255, 255);
                drawFaceFillQuad(m, consumers, camBox, bhr.getSide(), 80, 160, 255, 80);

                return;
            }

            if (hit instanceof TrajectorySim.HitInfo.EntityHit eh) {
                Entity e = eh.entity();

                Box bbWorld = e.getBoundingBox().expand(ENTITY_HITBOX_PAD);
                Box bbCam = bbWorld.offset(-camPos.x, -camPos.y, -camPos.z);

                int[] col = colorForEntity(e);

                // Bigger entity hitbox outline too
                Box outlineBox = bbCam.expand(OUTLINE_PUSH + HITBOX_BORDER_GROW);
                drawBoxOutlineLines(m, consumers, outlineBox, col[0], col[1], col[2], 255);

                Vec3d hitPosCam = hit.pos().subtract(camPos);
                drawFaceFillQuad(m, consumers, bbCam, hitPosCam, col[0], col[1], col[2], 80);
            }
        } finally {
            rsCall("depthMask", boolean.class, true);
            rsCall("enableDepthTest");
            rsCall("enableCull");
        }
    }

    // ---- Filled face (double-sided, so no "half triangle") ----

    private static void drawFaceFillQuad(Matrix4f m, VertexConsumerProvider consumers, Box b, Vec3d hitPosCam, int r, int g, int bl, int aFill) {
        drawFaceFillQuad(m, consumers, b, nearestFace(b, hitPosCam), r, g, bl, aFill);
    }

    private static void drawFaceFillQuad(Matrix4f m, VertexConsumerProvider consumers, Box b, Direction face, int r, int g, int bl, int aFill) {
        VertexConsumer vc = consumers.getBuffer(RenderLayers.debugFilledBox());

        float x1 = (float) b.minX, x2 = (float) b.maxX;
        float y1 = (float) b.minY, y2 = (float) b.maxY;
        float z1 = (float) b.minZ, z2 = (float) b.maxZ;

        boolean useQuads = RenderLayers.debugFilledBox().getDrawMode() == VertexFormat.DrawMode.QUADS;
        switch (face) {
            case WEST  -> { float x = x1 - FACE_EPS; faceFill(vc, m, useQuads, x,y1,z1, x,y2,z1, x,y2,z2, x,y1,z2, r,g,bl,aFill); }
            case EAST  -> { float x = x2 + FACE_EPS; faceFill(vc, m, useQuads, x,y1,z2, x,y2,z2, x,y2,z1, x,y1,z1, r,g,bl,aFill); }
            case NORTH -> { float z = z1 - FACE_EPS; faceFill(vc, m, useQuads, x2,y1,z, x2,y2,z, x1,y2,z, x1,y1,z, r,g,bl,aFill); }
            case SOUTH -> { float z = z2 + FACE_EPS; faceFill(vc, m, useQuads, x1,y1,z, x1,y2,z, x2,y2,z, x2,y1,z, r,g,bl,aFill); }
            case DOWN  -> { float y = y1 - FACE_EPS; faceFill(vc, m, useQuads, x1,y,z2, x2,y,z2, x2,y,z1, x1,y,z1, r,g,bl,aFill); }
            case UP    -> { float y = y2 + FACE_EPS; faceFill(vc, m, useQuads, x1,y,z1, x2,y,z1, x2,y,z2, x1,y,z2, r,g,bl,aFill); }
        }
    }

    private static void faceFill(
            VertexConsumer vc, Matrix4f m, boolean useQuads,
            float ax, float ay, float az,
            float bx, float by, float bz,
            float cx, float cy, float cz,
            float dx, float dy, float dz,
            int r, int g, int b, int a
    ) {
        if (useQuads) {
            faceQuadDoubleSided(vc, m, ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz, r, g, b, a);
        } else {
            faceTwoTrianglesDoubleSided(vc, m, ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz, r, g, b, a);
        }
    }

    private static void faceQuadDoubleSided(
            VertexConsumer vc, Matrix4f m,
            float ax, float ay, float az,
            float bx, float by, float bz,
            float cx, float cy, float cz,
            float dx, float dy, float dz,
            int r, int g, int b, int a
    ) {
        // Front quad: A B C D
        v(vc, m, ax, ay, az, r, g, b, a);
        v(vc, m, bx, by, bz, r, g, b, a);
        v(vc, m, cx, cy, cz, r, g, b, a);
        v(vc, m, dx, dy, dz, r, g, b, a);

        // Back quad: D C B A
        v(vc, m, dx, dy, dz, r, g, b, a);
        v(vc, m, cx, cy, cz, r, g, b, a);
        v(vc, m, bx, by, bz, r, g, b, a);
        v(vc, m, ax, ay, az, r, g, b, a);
    }

    private static void faceTwoTrianglesDoubleSided(
            VertexConsumer vc, Matrix4f m,
            float ax, float ay, float az,
            float bx, float by, float bz,
            float cx, float cy, float cz,
            float dx, float dy, float dz,
            int r, int g, int b, int a
    ) {
        // Triangle 1: A B C
        v(vc, m, ax, ay, az, r, g, b, a);
        v(vc, m, bx, by, bz, r, g, b, a);
        v(vc, m, cx, cy, cz, r, g, b, a);

        // Triangle 2: A C D
        v(vc, m, ax, ay, az, r, g, b, a);
        v(vc, m, cx, cy, cz, r, g, b, a);
        v(vc, m, dx, dy, dz, r, g, b, a);

        // Triangle 1 back: C B A
        v(vc, m, cx, cy, cz, r, g, b, a);
        v(vc, m, bx, by, bz, r, g, b, a);
        v(vc, m, ax, ay, az, r, g, b, a);

        // Triangle 2 back: D C A
        v(vc, m, dx, dy, dz, r, g, b, a);
        v(vc, m, cx, cy, cz, r, g, b, a);
        v(vc, m, ax, ay, az, r, g, b, a);
    }

    // ---- Line outlines ----

    private static void drawBoxOutlineLines(Matrix4f m, VertexConsumerProvider consumers, Box b, int r, int g, int bl, int a) {
        VertexConsumer vc = consumers.getBuffer(RenderLayers.lines());

        Vec3d p000 = new Vec3d(b.minX, b.minY, b.minZ);
        Vec3d p001 = new Vec3d(b.minX, b.minY, b.maxZ);
        Vec3d p010 = new Vec3d(b.minX, b.maxY, b.minZ);
        Vec3d p011 = new Vec3d(b.minX, b.maxY, b.maxZ);

        Vec3d p100 = new Vec3d(b.maxX, b.minY, b.minZ);
        Vec3d p101 = new Vec3d(b.maxX, b.minY, b.maxZ);
        Vec3d p110 = new Vec3d(b.maxX, b.maxY, b.minZ);
        Vec3d p111 = new Vec3d(b.maxX, b.maxY, b.maxZ);

        line(vc, m, p000, p001, r, g, bl, a);
        line(vc, m, p001, p101, r, g, bl, a);
        line(vc, m, p101, p100, r, g, bl, a);
        line(vc, m, p100, p000, r, g, bl, a);

        line(vc, m, p010, p011, r, g, bl, a);
        line(vc, m, p011, p111, r, g, bl, a);
        line(vc, m, p111, p110, r, g, bl, a);
        line(vc, m, p110, p010, r, g, bl, a);

        line(vc, m, p000, p010, r, g, bl, a);
        line(vc, m, p001, p011, r, g, bl, a);
        line(vc, m, p100, p110, r, g, bl, a);
        line(vc, m, p101, p111, r, g, bl, a);
    }

    private static void line(VertexConsumer vc, Matrix4f m, Vec3d a, Vec3d b, int r, int g, int bl, int alpha) {
        lineVertex(vc, m, a, r, g, bl, alpha);
        lineVertex(vc, m, b, r, g, bl, alpha);
    }

    // ---- Helpers ----

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

    private static int[] colorForEntity(Entity e) {
        if (e instanceof PlayerEntity) return new int[]{180, 90, 255};
        if (e instanceof PassiveEntity) return new int[]{60, 255, 120};
        if (e instanceof HostileEntity) return new int[]{255, 70, 70};
        return new int[]{255, 165, 60};
    }

    // ---- RenderSystem compat (NoSuchMethodError-proof) ----

    private static void rsCall(String name, Class<?> argType, Object arg) {
        try {
            Method m = RenderSystem.class.getMethod(name, argType);
            m.invoke(null, arg);
        } catch (Throwable ignored) {}
    }

    private static void rsCall(String name) {
        try {
            Method m = RenderSystem.class.getMethod(name);
            m.invoke(null);
        } catch (Throwable ignored) {}
    }

    private static void lineVertex(VertexConsumer vc, Matrix4f m, Vec3d p, int r, int g, int b, int a) {
        VertexConsumer v = vc.vertex(m, (float) p.x, (float) p.y, (float) p.z)
                .color(r, g, b, a)
                .normal(0f, 1f, 0f);
        // Must set LineWidth element for RenderLayers.lines() to avoid missing-vertex crash.
        v.lineWidth(LINE_WIDTH);
        vcEnd(v);
    }

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, int r, int g, int b, int a) {
        vc.vertex(m, x, y, z).color(r, g, b, a);
        vcEnd(vc);
    }

    private static void vcEnd(VertexConsumer vc) {
        try {
            Method m = vc.getClass().getMethod("next");
            m.invoke(vc);
            return;
        } catch (Throwable ignored) {}

        try {
            Method m = vc.getClass().getMethod("endVertex");
            m.invoke(vc);
        } catch (Throwable ignored) {}
    }
}
