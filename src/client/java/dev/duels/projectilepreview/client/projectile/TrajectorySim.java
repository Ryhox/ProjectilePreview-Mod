package dev.duels.projectilepreview.client.projectile;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Steps the projectile forward tick by tick using the exact vanilla physics.
 * Arrows move first and then decay their velocity; throwables apply gravity
 * and drag before moving (26.2 ThrowableProjectile.tick order).
 */
public final class TrajectorySim {
    private TrajectorySim() {}

    // ProjectileUtil inflates entity hitboxes by 0.3 when testing projectile hits.
    public static final double ENTITY_HITBOX_PAD = 0.3;
    private static final double ENTITY_SEARCH_INFLATE = 1.0;

    public record Result(List<Vec3> points, HitInfo hit) {}

    public sealed interface HitInfo {
        Vec3 pos();

        record BlockHit(Vec3 pos, BlockHitResult bhr) implements HitInfo {}

        record EntityHit(Vec3 pos, Entity entity) implements HitInfo {}
    }

    public static Result simulate(
            Entity owner,
            Vec3 startPos,
            Vec3 startVel,
            double gravity,
            double drag,
            int steps,
            double stepTime,
            boolean decayBeforeMove
    ) {
        Level world = owner.level();
        if (world == null) return null;

        // First integrate the pure physics path, so entities can be gathered in one query.
        Vec3[] path = new Vec3[steps + 1];
        path[0] = startPos;

        Vec3 pos = startPos;
        Vec3 vel = startVel;

        double minX = pos.x, minY = pos.y, minZ = pos.z;
        double maxX = pos.x, maxY = pos.y, maxZ = pos.z;

        for (int i = 1; i <= steps; i++) {
            if (decayBeforeMove) {
                vel = vel.add(0.0, -gravity, 0.0).scale(drag);
            }

            pos = pos.add(vel.scale(stepTime));
            path[i] = pos;

            minX = Math.min(minX, pos.x); maxX = Math.max(maxX, pos.x);
            minY = Math.min(minY, pos.y); maxY = Math.max(maxY, pos.y);
            minZ = Math.min(minZ, pos.z); maxZ = Math.max(maxZ, pos.z);

            if (!decayBeforeMove) {
                vel = vel.scale(drag).add(0.0, -gravity, 0.0);
            }
        }

        AABB searchBox = new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(ENTITY_SEARCH_INFLATE);

        // Same targeting rules as Projectile.canHitEntity.
        List<Entity> candidates = world.getEntities(owner, searchBox, e ->
                e.isAlive()
                        && !e.isSpectator()
                        && e.isPickable()
                        && !owner.isPassengerOfSameVehicle(e)
        );

        List<Vec3> points = new ArrayList<>(steps + 1);
        points.add(path[0]);

        HitInfo finalHit = null;

        for (int i = 0; i < steps; i++) {
            Vec3 from = path[i];
            Vec3 to = path[i + 1];

            // Vanilla clips blocks first, then looks for entities up to the block hit.
            HitResult blockHit = world.clip(new ClipContext(
                    from,
                    to,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    owner
            ));

            Vec3 segmentEnd = blockHit.getType() != HitResult.Type.MISS ? blockHit.getLocation() : to;

            HitInfo.EntityHit entHit = nearestEntityHit(candidates, from, segmentEnd);
            if (entHit != null) {
                points.add(entHit.pos());
                finalHit = entHit;
                break;
            }

            if (blockHit.getType() != HitResult.Type.MISS) {
                points.add(segmentEnd);
                if (blockHit instanceof BlockHitResult bhr) {
                    finalHit = new HitInfo.BlockHit(segmentEnd, bhr);
                }
                break;
            }

            points.add(to);
        }

        return new Result(points, finalHit);
    }

    private static HitInfo.EntityHit nearestEntityHit(List<Entity> candidates, Vec3 from, Vec3 to) {
        Entity best = null;
        Vec3 bestPos = null;
        double bestDist2 = Double.MAX_VALUE;

        for (Entity e : candidates) {
            AABB bb = e.getBoundingBox().inflate(ENTITY_HITBOX_PAD);

            var opt = bb.clip(from, to);
            if (opt.isEmpty()) continue;

            Vec3 hp = opt.get();
            double d2 = hp.distanceToSqr(from);
            if (d2 < bestDist2) {
                bestDist2 = d2;
                best = e;
                bestPos = hp;
            }
        }

        return best == null ? null : new HitInfo.EntityHit(bestPos, best);
    }
}
