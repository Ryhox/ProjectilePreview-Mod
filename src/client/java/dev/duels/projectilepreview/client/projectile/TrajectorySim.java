package dev.duels.projectilepreview.client.projectile;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

public final class TrajectorySim {
    private TrajectorySim() {}

    public static final double ENTITY_HITBOX_PAD = 0.10;

    public record Result(List<Vec3> points, HitInfo hit) {}

    public interface HitInfo {
        Vec3 pos();

        final class BlockHit implements HitInfo {
            private final Vec3 pos;
            private final BlockHitResult bhr;
            private final AABB hitBoxWorld;

            public BlockHit(Vec3 pos, BlockHitResult bhr, AABB hitBoxWorld) {
                this.pos = pos;
                this.bhr = bhr;
                this.hitBoxWorld = hitBoxWorld;
            }

            @Override public Vec3 pos() { return pos; }
            public BlockHitResult bhr() { return bhr; }
            public AABB hitBoxWorld() { return hitBoxWorld; }
        }

        final class EntityHit implements HitInfo {
            private final Vec3 pos;
            private final Entity entity;

            public EntityHit(Vec3 pos, Entity entity) {
                this.pos = pos;
                this.entity = entity;
            }

            @Override public Vec3 pos() { return pos; }
            public Entity entity() { return entity; }
        }
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

            HitInfo.EntityHit entHit = raycastEntity(owner, pos, nextPos);
            if (entHit != null) {
                points.add(entHit.pos());
                finalHit = entHit;
                break;
            }

            HitResult hit = world.clip(new ClipContext(
                    pos,
                    nextPos,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    owner
            ));

            if (hit.getType() != HitResult.Type.MISS) {
                Vec3 hp = hit.getLocation();
                points.add(hp);

                if (hit instanceof BlockHitResult bhr) {
                    AABB hb = resolveBlockHitBox(world, bhr.getBlockPos(), pos, nextPos);
                    finalHit = new HitInfo.BlockHit(hp, bhr, hb);
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
        if (world == null) return null;

        AABB sweep = new AABB(from, to).inflate(0.35);

        List<Entity> candidates = world.getEntities(owner, sweep, e ->
                e.isAlive()
                        && e instanceof LivingEntity
                        && !e.isSpectator()
                        && e.isAttackable()
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

    private static AABB resolveBlockHitBox(Level world, BlockPos bp, Vec3 from, Vec3 to) {
        BlockState state = world.getBlockState(bp);
        VoxelShape shape = state.getCollisionShape(world, bp);
        List<AABB> boxes = shape.toAabbs();

        if (boxes.isEmpty()) {
            return new AABB(bp).inflate(ENTITY_HITBOX_PAD);
        }

        AABB best = null;
        double bestDist2 = Double.MAX_VALUE;

        for (AABB local : boxes) {
            AABB wb = local.move(bp.getX(), bp.getY(), bp.getZ()).inflate(ENTITY_HITBOX_PAD);

            var opt = wb.clip(from, to);
            if (opt.isEmpty()) continue;

            Vec3 hp = opt.get();
            double d2 = hp.distanceToSqr(from);
            if (d2 < bestDist2) {
                bestDist2 = d2;
                best = wb;
            }
        }

        return best != null ? best : boxes.get(0).move(bp.getX(), bp.getY(), bp.getZ()).inflate(ENTITY_HITBOX_PAD);
    }
}
