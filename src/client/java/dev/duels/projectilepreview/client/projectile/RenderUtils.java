package dev.duels.projectilepreview.client.projectile;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.duels.projectilepreview.client.PreviewConfig;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Draws the preview through the vanilla submit pipeline, like the classic releases did. */
public final class RenderUtils {
    private RenderUtils() {}

    private static final int SMOOTH_SUBDIVISIONS = 4;
    private static final float RAINBOW_CYCLE_SECONDS = 3.0f;
    private static final float RAINBOW_LENGTH_CYCLES = 1.0f;
    private static final float RAINBOW_SATURATION = 0.8f;
    private static final float GRADIENT_HUE_SHIFT = 0.25f;
    private static final double FADE_END_FRACTION = 0.15;

    private static final float FACE_EPS = 0.002f;
    private static final double OUTLINE_PUSH = 0.01;
    // Entity boxes draw slightly enlarged, matching the classic look.
    private static final double ENTITY_BOX_VISUAL_PAD = 0.10;
    private static final int FILL_ALPHA = 80;
    private static final float HIT_BOX_LINE_WIDTH = 5.0f;
    private static final double MARKER_RADIUS = 0.15;
    private static final double MARKER_PUSH = 0.02;

    private static final int BLOCK_HIT_COLOR = 0xFF50A0FF;
    private static final int PLAYER_COLOR = 0xFFB45AFF;
    private static final int ANIMAL_COLOR = 0xFF3CFF78;
    private static final int MONSTER_COLOR = 0xFFFF4646;
    private static final int DEFAULT_COLOR = 0xFFFFA53C;

    public static void drawTrajectory(SubmitNodeCollector collector, Vec3 camPos, List<Vec3> points, PreviewConfig cfg) {
        if (points.size() < 2) return;

        List<Vec3> pts = cfg.smoothCurve ? smooth(points) : points;
        int segments = pts.size() - 1;
        float time = animationTime();

        collector.submitCustomGeometry(new PoseStack(), RenderTypes.lines(), (pose, vc) -> {
            for (int i = 0; i < segments; i++) {
                if (skipForStyle(cfg.lineStyle, i)) continue;

                float t = (float) i / segments;
                int color = ARGB.color(alphaAt(cfg, t), colorAt(cfg, t, time));
                line(vc, pts.get(i).subtract(camPos), pts.get(i + 1).subtract(camPos), color, cfg.lineWidth);
            }
        });
    }

    public static void drawHitOverlay(SubmitNodeCollector collector, Vec3 camPos, TrajectorySim.HitInfo hit, PreviewConfig cfg) {
        if (hit instanceof TrajectorySim.HitInfo.BlockHit bh) {
            BlockHitResult bhr = bh.bhr();
            if (cfg.showHitBox) {
                AABB box = new AABB(bhr.getBlockPos()).move(-camPos.x, -camPos.y, -camPos.z);
                drawHitBox(collector, box, bhr.getDirection(), BLOCK_HIT_COLOR);
            }
            if (cfg.showImpactMarker) {
                drawImpactMarker(collector, bh.pos().subtract(camPos), bhr.getDirection(), cfg);
            }
            return;
        }

        if (cfg.showHitBox && hit instanceof TrajectorySim.HitInfo.EntityHit eh) {
            AABB box = eh.entity().getBoundingBox().inflate(ENTITY_BOX_VISUAL_PAD).move(-camPos.x, -camPos.y, -camPos.z);
            drawHitBox(collector, box, nearestFace(box, hit.pos().subtract(camPos)), colorForEntity(eh.entity()));
        }
    }

    private static void drawHitBox(SubmitNodeCollector collector, AABB box, Direction face, int color) {
        AABB pushed = box.inflate(OUTLINE_PUSH);

        collector.submitCustomGeometry(new PoseStack(), RenderTypes.lines(), (pose, vc) ->
                boxOutline(vc, pushed, color, HIT_BOX_LINE_WIDTH));

        int fill = (color & 0x00FFFFFF) | (FILL_ALPHA << 24);
        collector.submitCustomGeometry(new PoseStack(), RenderTypes.debugFilledBox(), (pose, vc) ->
                faceFill(vc, pushed, face, fill));
    }

    /** Small diamond on the hit face marking the exact impact point. */
    private static void drawImpactMarker(SubmitNodeCollector collector, Vec3 center, Direction face, PreviewConfig cfg) {
        Vec3 normal = face.getUnitVec3();
        Vec3 u = Math.abs(normal.y) < 0.5 ? new Vec3(0.0, 1.0, 0.0).cross(normal) : new Vec3(1.0, 0.0, 0.0).cross(normal);
        u = u.normalize().scale(MARKER_RADIUS);
        Vec3 v = normal.cross(u).normalize().scale(MARKER_RADIUS);

        Vec3 pos = center.add(normal.scale(MARKER_PUSH));
        int color = ARGB.color(255, colorAt(cfg, 1.0f, animationTime()));

        Vec3 a = pos.add(u);
        Vec3 b = pos.add(v);
        Vec3 c = pos.subtract(u);
        Vec3 d = pos.subtract(v);

        collector.submitCustomGeometry(new PoseStack(), RenderTypes.lines(), (pose, vc) -> {
            line(vc, a, b, color, cfg.lineWidth);
            line(vc, b, c, color, cfg.lineWidth);
            line(vc, c, d, color, cfg.lineWidth);
            line(vc, d, a, color, cfg.lineWidth);
        });
    }

    private static void line(VertexConsumer vc, Vec3 a, Vec3 b, int color, float width) {
        lineVertex(vc, a, color, width);
        lineVertex(vc, b, color, width);
    }

    private static void lineVertex(VertexConsumer vc, Vec3 p, int color, float width) {
        vc.addVertex((float) p.x, (float) p.y, (float) p.z)
                .setColor(color)
                .setNormal(0.0f, 1.0f, 0.0f)
                .setLineWidth(width);
    }

    private static void boxOutline(VertexConsumer vc, AABB b, int color, float width) {
        Vec3 p000 = new Vec3(b.minX, b.minY, b.minZ);
        Vec3 p001 = new Vec3(b.minX, b.minY, b.maxZ);
        Vec3 p010 = new Vec3(b.minX, b.maxY, b.minZ);
        Vec3 p011 = new Vec3(b.minX, b.maxY, b.maxZ);
        Vec3 p100 = new Vec3(b.maxX, b.minY, b.minZ);
        Vec3 p101 = new Vec3(b.maxX, b.minY, b.maxZ);
        Vec3 p110 = new Vec3(b.maxX, b.maxY, b.minZ);
        Vec3 p111 = new Vec3(b.maxX, b.maxY, b.maxZ);

        line(vc, p000, p001, color, width);
        line(vc, p001, p101, color, width);
        line(vc, p101, p100, color, width);
        line(vc, p100, p000, color, width);

        line(vc, p010, p011, color, width);
        line(vc, p011, p111, color, width);
        line(vc, p111, p110, color, width);
        line(vc, p110, p010, color, width);

        line(vc, p000, p010, color, width);
        line(vc, p001, p011, color, width);
        line(vc, p100, p110, color, width);
        line(vc, p101, p111, color, width);
    }

    private static void faceFill(VertexConsumer vc, AABB b, Direction face, int color) {
        float x1 = (float) b.minX, x2 = (float) b.maxX;
        float y1 = (float) b.minY, y2 = (float) b.maxY;
        float z1 = (float) b.minZ, z2 = (float) b.maxZ;

        switch (face) {
            case WEST  -> { float x = x1 - FACE_EPS; quadDoubleSided(vc, x,y1,z1, x,y2,z1, x,y2,z2, x,y1,z2, color); }
            case EAST  -> { float x = x2 + FACE_EPS; quadDoubleSided(vc, x,y1,z2, x,y2,z2, x,y2,z1, x,y1,z1, color); }
            case NORTH -> { float z = z1 - FACE_EPS; quadDoubleSided(vc, x2,y1,z, x2,y2,z, x1,y2,z, x1,y1,z, color); }
            case SOUTH -> { float z = z2 + FACE_EPS; quadDoubleSided(vc, x1,y1,z, x1,y2,z, x2,y2,z, x2,y1,z, color); }
            case DOWN  -> { float y = y1 - FACE_EPS; quadDoubleSided(vc, x1,y,z2, x2,y,z2, x2,y,z1, x1,y,z1, color); }
            case UP    -> { float y = y2 + FACE_EPS; quadDoubleSided(vc, x1,y,z1, x2,y,z1, x2,y,z2, x1,y,z2, color); }
        }
    }

    private static void quadDoubleSided(
            VertexConsumer vc,
            float ax, float ay, float az,
            float bx, float by, float bz,
            float cx, float cy, float cz,
            float dx, float dy, float dz,
            int color
    ) {
        vc.addVertex(ax, ay, az).setColor(color);
        vc.addVertex(bx, by, bz).setColor(color);
        vc.addVertex(cx, cy, cz).setColor(color);
        vc.addVertex(dx, dy, dz).setColor(color);

        vc.addVertex(dx, dy, dz).setColor(color);
        vc.addVertex(cx, cy, cz).setColor(color);
        vc.addVertex(bx, by, bz).setColor(color);
        vc.addVertex(ax, ay, az).setColor(color);
    }

    /** Catmull-Rom spline through the tick positions; the original points stay on the curve. */
    private static List<Vec3> smooth(List<Vec3> points) {
        int n = points.size();
        List<Vec3> out = new ArrayList<>((n - 1) * SMOOTH_SUBDIVISIONS + 1);
        out.add(points.get(0));

        for (int i = 0; i < n - 1; i++) {
            Vec3 p0 = points.get(Math.max(0, i - 1));
            Vec3 p1 = points.get(i);
            Vec3 p2 = points.get(i + 1);
            Vec3 p3 = points.get(Math.min(n - 1, i + 2));

            for (int s = 1; s <= SMOOTH_SUBDIVISIONS; s++) {
                out.add(catmullRom(p0, p1, p2, p3, (double) s / SMOOTH_SUBDIVISIONS));
            }
        }
        return out;
    }

    private static Vec3 catmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return new Vec3(
                catmullRom(p0.x, p1.x, p2.x, p3.x, t, t2, t3),
                catmullRom(p0.y, p1.y, p2.y, p3.y, t, t2, t3),
                catmullRom(p0.z, p1.z, p2.z, p3.z, t, t2, t3)
        );
    }

    private static double catmullRom(double p0, double p1, double p2, double p3, double t, double t2, double t3) {
        return 0.5 * ((2.0 * p1)
                + (p2 - p0) * t
                + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2
                + (3.0 * p1 - p0 - 3.0 * p2 + p3) * t3);
    }

    private static boolean skipForStyle(PreviewConfig.LineStyle style, int segment) {
        return switch (style) {
            case SOLID -> false;
            case DASHED -> (segment % 4) >= 2;
            case DOTTED -> (segment % 2) == 1;
        };
    }

    public static float animationTime() {
        return (Util.getMillis() % 600_000L) / 1000.0f;
    }

    public static int colorAt(PreviewConfig cfg, float t, float time) {
        return switch (cfg.colorMode) {
            case STATIC -> ARGB.color(cfg.red, cfg.green, cfg.blue);
            case RAINBOW -> {
                float hue = Mth.frac(time * cfg.rainbowSpeed / RAINBOW_CYCLE_SECONDS + t * RAINBOW_LENGTH_CYCLES);
                yield Mth.hsvToRgb(hue, RAINBOW_SATURATION, 1.0f);
            }
            case GRADIENT -> {
                float[] hsv = rgbToHsv(cfg.red, cfg.green, cfg.blue);
                float hue = Mth.frac(hsv[0] + GRADIENT_HUE_SHIFT * t);
                yield Mth.hsvToRgb(hue, hsv[1], hsv[2]);
            }
        };
    }

    private static int alphaAt(PreviewConfig cfg, float t) {
        float base = cfg.opacity / 100.0f;
        if (cfg.fadeOut) {
            base *= (float) (1.0 - (1.0 - FADE_END_FRACTION) * t);
        }
        return Mth.clamp((int) (base * 255.0f), 0, 255);
    }

    private static float[] rgbToHsv(int r, int g, int b) {
        float rf = r / 255.0f, gf = g / 255.0f, bf = b / 255.0f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float delta = max - min;

        float hue;
        if (delta == 0.0f) hue = 0.0f;
        else if (max == rf) hue = Mth.frac(((gf - bf) / delta) / 6.0f);
        else if (max == gf) hue = Mth.frac((2.0f + (bf - rf) / delta) / 6.0f);
        else hue = Mth.frac((4.0f + (rf - gf) / delta) / 6.0f);

        float sat = max == 0.0f ? 0.0f : delta / max;
        return new float[]{hue, sat, max};
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

    private static int colorForEntity(Entity e) {
        if (e instanceof Player) return PLAYER_COLOR;
        if (e instanceof Animal) return ANIMAL_COLOR;
        if (e instanceof Monster) return MONSTER_COLOR;
        return DEFAULT_COLOR;
    }
}
