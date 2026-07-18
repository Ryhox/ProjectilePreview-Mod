package dev.duels.projectilepreview.client.projectile;

import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class RenderUtils {
    private RenderUtils() {}

    private static final float LINE_WIDTH = 5.0f;
    private static final double OUTLINE_PUSH = 0.01;
    private static final int FILL_ALPHA = 80;

    private static final int TRAJECTORY_COLOR = 0xFFFFFFFF;
    private static final int BLOCK_HIT_COLOR = 0xFF50A0FF;
    private static final int PLAYER_COLOR = 0xFFB45AFF;
    private static final int ANIMAL_COLOR = 0xFF3CFF78;
    private static final int MONSTER_COLOR = 0xFFFF4646;
    private static final int DEFAULT_COLOR = 0xFFFFA53C;

    public static void drawPolyline(List<Vec3> points) {
        for (int i = 0; i < points.size() - 1; i++) {
            Gizmos.line(points.get(i), points.get(i + 1), TRAJECTORY_COLOR, LINE_WIDTH);
        }
    }

    public static void drawHitOverlay(TrajectorySim.HitInfo hit) {
        if (hit instanceof TrajectorySim.HitInfo.BlockHit bh) {
            BlockHitResult bhr = bh.bhr();
            drawHitBox(new AABB(bhr.getBlockPos()), bhr.getDirection(), BLOCK_HIT_COLOR);
            return;
        }

        if (hit instanceof TrajectorySim.HitInfo.EntityHit eh) {
            AABB bb = eh.entity().getBoundingBox();
            drawHitBox(bb, nearestFace(bb, hit.pos()), colorForEntity(eh.entity()));
        }
    }

    private static void drawHitBox(AABB box, Direction face, int color) {
        AABB pushed = box.inflate(OUTLINE_PUSH);
        Gizmos.cuboid(pushed, GizmoStyle.stroke(color, LINE_WIDTH)).setAlwaysOnTop();
        Gizmos.rect(
                new Vec3(pushed.minX, pushed.minY, pushed.minZ),
                new Vec3(pushed.maxX, pushed.maxY, pushed.maxZ),
                face,
                GizmoStyle.fill(withFillAlpha(color))
        ).setAlwaysOnTop();
    }

    private static int withFillAlpha(int color) {
        return (color & 0x00FFFFFF) | (FILL_ALPHA << 24);
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
