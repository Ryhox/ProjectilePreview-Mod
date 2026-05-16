package dev.duels.projectilepreview.client.projectile;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.lang.reflect.Method;
import java.util.List;

import static dev.duels.projectilepreview.client.projectile.TrajectorySim.ENTITY_HITBOX_PAD;

public final class RenderUtils {
    private RenderUtils() {}

    private static final float FACE_EPS = 0.002f;
    private static final double OUTLINE_PUSH = 0.01;
    private static final double HITBOX_BORDER_GROW = 0.0;
    private static final float LINE_WIDTH = 5.0f;

    public static void drawPolyline(Matrix4f m, MultiBufferSource consumers, List<Vec3> points, Vec3 camPos) {
        if (points == null || points.size() < 2) return;

        VertexConsumer vc = consumers.getBuffer(RenderTypes.lines());

        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 a = points.get(i).subtract(camPos);
            Vec3 b = points.get(i + 1).subtract(camPos);
            line(vc, m, a, b, 255, 255, 255, 255);
        }
    }

    public static void drawHitOverlay(Matrix4f m, MultiBufferSource consumers, TrajectorySim.HitInfo hit, Vec3 camPos) {
        if (hit == null) return;

        rsCall("enableBlend");
        rsCall("disableCull");
        rsCall("disableDepthTest");
        rsCall("depthMask", boolean.class, false);

        try {
            if (hit instanceof TrajectorySim.HitInfo.BlockHit bh) {
                BlockHitResult bhr = bh.bhr();

                Minecraft mc = Minecraft.getInstance();
                if (mc.level == null) return;

                BlockPos pos = bhr.getBlockPos();
                // Build a unit AABB for the block, shifted into camera-relative space
                AABB camBox = new AABB(
                        pos.getX() - camPos.x, pos.getY() - camPos.y, pos.getZ() - camPos.z,
                        pos.getX() + 1.0 - camPos.x, pos.getY() + 1.0 - camPos.y, pos.getZ() + 1.0 - camPos.z
                );
                AABB outlineBox = camBox.inflate(OUTLINE_PUSH + HITBOX_BORDER_GROW);
                drawBoxOutlineLines(m, consumers, outlineBox, 80, 160, 255, 255);
                drawFaceFillQuad(m, consumers, camBox, bhr.getDirection(), 80, 160, 255, 80);

                return;
            }

            if (hit instanceof TrajectorySim.HitInfo.EntityHit eh) {
                Entity e = eh.entity();

                AABB bbWorld = e.getBoundingBox().inflate(ENTITY_HITBOX_PAD);
                AABB bbCam = bbWorld.move(-camPos.x, -camPos.y, -camPos.z);

                int[] col = colorForEntity(e);

                AABB outlineBox = bbCam.inflate(OUTLINE_PUSH + HITBOX_BORDER_GROW);
                drawBoxOutlineLines(m, consumers, outlineBox, col[0], col[1], col[2], 255);

                Vec3 hitPosCam = hit.pos().subtract(camPos);
                drawFaceFillQuad(m, consumers, bbCam, hitPosCam, col[0], col[1], col[2], 80);
            }
        } finally {
            rsCall("depthMask", boolean.class, true);
            rsCall("enableDepthTest");
            rsCall("enableCull");
        }
    }

    private static void drawFaceFillQuad(Matrix4f m, MultiBufferSource consumers, AABB b, Vec3 hitPosCam, int r, int g, int bl, int aFill) {
        drawFaceFillQuad(m, consumers, b, nearestFace(b, hitPosCam), r, g, bl, aFill);
    }

    private static void drawFaceFillQuad(Matrix4f m, MultiBufferSource consumers, AABB b, Direction face, int r, int g, int bl, int aFill) {
        VertexConsumer vc = consumers.getBuffer(RenderTypes.debugFilledBox());

        float x1 = (float) b.minX, x2 = (float) b.maxX;
        float y1 = (float) b.minY, y2 = (float) b.maxY;
        float z1 = (float) b.minZ, z2 = (float) b.maxZ;

        switch (face) {
            case WEST  -> { float x = x1 - FACE_EPS; faceFillDoubleSided(vc, m, x,y1,z1, x,y2,z1, x,y2,z2, x,y1,z2, r,g,bl,aFill); }
            case EAST  -> { float x = x2 + FACE_EPS; faceFillDoubleSided(vc, m, x,y1,z2, x,y2,z2, x,y2,z1, x,y1,z1, r,g,bl,aFill); }
            case NORTH -> { float z = z1 - FACE_EPS; faceFillDoubleSided(vc, m, x2,y1,z, x2,y2,z, x1,y2,z, x1,y1,z, r,g,bl,aFill); }
            case SOUTH -> { float z = z2 + FACE_EPS; faceFillDoubleSided(vc, m, x1,y1,z, x1,y2,z, x2,y2,z, x2,y1,z, r,g,bl,aFill); }
            case DOWN  -> { float y = y1 - FACE_EPS; faceFillDoubleSided(vc, m, x1,y,z2, x2,y,z2, x2,y,z1, x1,y,z1, r,g,bl,aFill); }
            case UP    -> { float y = y2 + FACE_EPS; faceFillDoubleSided(vc, m, x1,y,z1, x2,y,z1, x2,y,z2, x1,y,z2, r,g,bl,aFill); }
        }
    }

    private static void faceFillDoubleSided(
            VertexConsumer vc, Matrix4f m,
            float ax, float ay, float az,
            float bx, float by, float bz,
            float cx, float cy, float cz,
            float dx, float dy, float dz,
            int r, int g, int b, int a
    ) {
        v(vc, m, ax, ay, az, r, g, b, a);
        v(vc, m, bx, by, bz, r, g, b, a);
        v(vc, m, cx, cy, cz, r, g, b, a);
        v(vc, m, dx, dy, dz, r, g, b, a);

        v(vc, m, dx, dy, dz, r, g, b, a);
        v(vc, m, cx, cy, cz, r, g, b, a);
        v(vc, m, bx, by, bz, r, g, b, a);
        v(vc, m, ax, ay, az, r, g, b, a);
    }

    private static void drawBoxOutlineLines(Matrix4f m, MultiBufferSource consumers, AABB b, int r, int g, int bl, int a) {
        VertexConsumer vc = consumers.getBuffer(RenderTypes.lines());

        Vec3 p000 = new Vec3(b.minX, b.minY, b.minZ);
        Vec3 p001 = new Vec3(b.minX, b.minY, b.maxZ);
        Vec3 p010 = new Vec3(b.minX, b.maxY, b.minZ);
        Vec3 p011 = new Vec3(b.minX, b.maxY, b.maxZ);

        Vec3 p100 = new Vec3(b.maxX, b.minY, b.minZ);
        Vec3 p101 = new Vec3(b.maxX, b.minY, b.maxZ);
        Vec3 p110 = new Vec3(b.maxX, b.maxY, b.minZ);
        Vec3 p111 = new Vec3(b.maxX, b.maxY, b.maxZ);

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

    private static void line(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, int r, int g, int bl, int alpha) {
        lineVertex(vc, m, a, r, g, bl, alpha);
        lineVertex(vc, m, b, r, g, bl, alpha);
    }

    private static Direction nearestFace(AABB b, Vec3 hit) {
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
        if (e instanceof Player) return new int[]{180, 90, 255};
        if (e instanceof Animal) return new int[]{60, 255, 120};
        if (e instanceof Monster) return new int[]{255, 70, 70};
        return new int[]{255, 165, 60};
    }

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

    private static void lineVertex(VertexConsumer vc, Matrix4f m, Vec3 p, int r, int g, int b, int a) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z)
                .setColor(r, g, b, a)
                .setNormal(0f, 1f, 0f)
                .setLineWidth(LINE_WIDTH);
    }

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, int r, int g, int b, int a) {
        vc.addVertex(m, x, y, z).setColor(r, g, b, a);
    }
}
