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
 * Steps the projectile forward tick by tick using the exact vanilla physics:
 * position += velocity, then velocity = velocity * drag - gravity.
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
            double stepTime
    ) {
        Level world = owner.level();
        if (world == null) return null;

        List<Vec3> points = new ArrayList<>(steps + 1);

        Vec3 pos = startPos;
        Vec3 vel = startVel;

        points.add(pos);

        HitInfo finalHit = null;

        for (int i = 0; i < steps; i++) {
            Vec3 nextPos = pos.add(vel.scale(stepTime));

            // Vanilla clips blocks first, then looks for entities up to the block hit.
            HitResult blockHit = world.clip(new ClipContext(
                    pos,
                    nextPos,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    owner
            ));

            Vec3 segmentEnd = blockHit.getType() != HitResult.Type.MISS ? blockHit.getLocation() : nextPos;

            HitInfo.EntityHit entHit = raycastEntity(owner, pos, segmentEnd);
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

            points.add(nextPos);

            vel = vel.scale(drag).add(0.0, -gravity, 0.0);
            pos = nextPos;
        }

        return new Result(points, finalHit);
    }

    private static HitInfo.EntityHit raycastEntity(Entity owner, Vec3 from, Vec3 to) {
        Level world = owner.level();

        AABB sweep = new AABB(from, to).inflate(ENTITY_SEARCH_INFLATE);

        // Same targeting rules as Projectile.canHitEntity.
        List<Entity> candidates = world.getEntities(owner, sweep, e ->
                e.isAlive()
                        && !e.isSpectator()
                        && e.isPickable()
                        && !owner.isPassengerOfSameVehicle(e)
        );

        if (candidates.isEmpty()) return null;

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
