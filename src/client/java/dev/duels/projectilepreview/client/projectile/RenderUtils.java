package dev.duels.projectilepreview.client.projectile;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;

import static dev.duels.projectilepreview.client.projectile.TrajectorySim.ENTITY_HITBOX_PAD;

public final class RenderUtils {
    private RenderUtils() {}

    private static final float LINE_WIDTH = 3.0f;
    private static final float THICKNESS = 0.002f;


    public static void drawPolyline(MatrixStack matrices, VertexConsumerProvider consumers, List<Vec3d> points, Vec3d camPos) {
        if (points.size() < 2) return;

        VertexConsumer vc = consumers.getBuffer(PreviewRenderLayers.FILLED_QUADS);
        Matrix4f m = matrices.peek().getPositionMatrix();

        for (int i = 0; i < points.size() - 1; i++) {
            Vec3d a = points.get(i).subtract(camPos);
            Vec3d b = points.get(i + 1).subtract(camPos);
            addThickLine(vc, m, a, b, THICKNESS, 255, 255, 255, 255);

        }
    }


    public static void drawHitOverlay(MatrixStack matrices, VertexConsumerProvider consumers, TrajectorySim.HitInfo hit, Vec3d camPos) {
        if (hit == null) return;

        int aFill = 80;
        int aLine = 220;

        if (hit instanceof TrajectorySim.HitInfo.BlockHit bh) {
            BlockHitResult bhr = bh.bhr();

            Box bbWorld = bh.hitBoxWorld();
            Box bbCam = bbWorld.offset(-camPos.x, -camPos.y, -camPos.z).expand(0.002);
            Vec3d hitPosCam = hit.pos().subtract(camPos);

            drawFaceFillAndOutline(matrices, consumers, bbCam, hitPosCam, 80, 160, 255, aFill, aLine);
            return;
        }

        if (hit instanceof TrajectorySim.HitInfo.EntityHit eh) {
            Entity e = eh.entity();

            Box bbWorld = e.getBoundingBox().expand(ENTITY_HITBOX_PAD);
            Box bbCam = bbWorld.offset(-camPos.x, -camPos.y, -camPos.z).expand(0.002);
            Vec3d hitPosCam = hit.pos().subtract(camPos);

            int[] col = colorForEntity(e);
            drawFaceFillAndOutline(matrices, consumers, bbCam, hitPosCam, col[0], col[1], col[2], aFill, aLine);
        }
    }

    private static void drawFaceFillAndOutline(
            MatrixStack matrices,
            VertexConsumerProvider consumers,
            Box bbCam,
            Vec3d hitPosCam,
            int r, int g, int b,
            int aFill,
            int aLine
    ) {
        Direction face = nearestFace(bbCam, hitPosCam);

        VertexConsumer fill = consumers.getBuffer(PreviewRenderLayers.FILLED_QUADS);
        drawFaceQuad(matrices, fill, bbCam, face, r, g, b, aFill);

        RenderSystem.lineWidth(LINE_WIDTH);
        drawOutline(matrices, consumers, bbCam, THICKNESS, r, g, b, aLine);
        RenderSystem.lineWidth(1.0f);
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

    private static void drawFaceQuad(MatrixStack matrices, VertexConsumer vc, Box b, Direction face,
                                     int r, int g, int bl, int a) {
        Matrix4f m = matrices.peek().getPositionMatrix();

        float x1 = (float) b.minX, x2 = (float) b.maxX;
        float y1 = (float) b.minY, y2 = (float) b.maxY;
        float z1 = (float) b.minZ, z2 = (float) b.maxZ;

        switch (face) {
            case WEST  -> quad(vc, m, x1,y1,z1, x1,y2,z1, x1,y2,z2, x1,y1,z2, r,g,bl,a);
            case EAST  -> quad(vc, m, x2,y1,z2, x2,y2,z2, x2,y2,z1, x2,y1,z1, r,g,bl,a);
            case NORTH -> quad(vc, m, x2,y1,z1, x2,y2,z1, x1,y2,z1, x1,y1,z1, r,g,bl,a);
            case SOUTH -> quad(vc, m, x1,y1,z2, x1,y2,z2, x2,y2,z2, x2,y1,z2, r,g,bl,a);
            case DOWN  -> quad(vc, m, x1,y1,z2, x2,y1,z2, x2,y1,z1, x1,y1,z1, r,g,bl,a);
            case UP    -> quad(vc, m, x1,y2,z1, x2,y2,z1, x2,y2,z2, x1,y2,z2, r,g,bl,a);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m,
                             float ax,float ay,float az,
                             float bx,float by,float bz,
                             float cx,float cy,float cz,
                             float dx,float dy,float dz,
                             int r,int g,int b,int a) {
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

        // camera is at (0,0,0) in cam-space since we already subtracted camPos
        Vec3d mid = a.add(b).multiply(0.5);
        Vec3d view = mid.negate(); // from mid -> camera(0,0,0)

        // if line points straight at camera, choose a stable fallback axis
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


}
