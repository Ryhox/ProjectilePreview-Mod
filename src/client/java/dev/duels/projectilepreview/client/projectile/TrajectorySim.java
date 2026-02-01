package dev.duels.projectilepreview.client.projectile;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public final class TrajectorySim {
    private TrajectorySim() {}

    public static final double ENTITY_HITBOX_PAD = 0.10;

    public record Result(List<Vec3d> points, HitInfo hit) {}

    public interface HitInfo {
        Vec3d pos();

        final class BlockHit implements HitInfo {
            private final Vec3d pos;
            private final BlockHitResult bhr;
            private final Box hitBoxWorld;

            public BlockHit(Vec3d pos, BlockHitResult bhr, Box hitBoxWorld) {
                this.pos = pos;
                this.bhr = bhr;
                this.hitBoxWorld = hitBoxWorld;
            }

            @Override public Vec3d pos() { return pos; }
            public BlockHitResult bhr() { return bhr; }
            public Box hitBoxWorld() { return hitBoxWorld; }
        }

        final class EntityHit implements HitInfo {
            private final Vec3d pos;
            private final Entity entity;

            public EntityHit(Vec3d pos, Entity entity) {
                this.pos = pos;
                this.entity = entity;
            }

            @Override public Vec3d pos() { return pos; }
            public Entity entity() { return entity; }
        }
    }

    public static Result simulate(
            Entity owner,
            Vec3d startPos,
            Vec3d startVel,
            double gravity,
            double drag,
            int steps,
            double stepTime
    ) {
        World world = owner.getWorld();
        if (world == null) return null;

        List<Vec3d> points = new ArrayList<>(steps + 1);

        Vec3d pos = startPos;
        Vec3d vel = startVel;

        points.add(pos);

        HitInfo finalHit = null;

        for (int i = 0; i < steps; i++) {
            Vec3d nextPos = pos.add(vel.multiply(stepTime));

            HitInfo.EntityHit entHit = raycastEntity(owner, pos, nextPos);
            if (entHit != null) {
                points.add(entHit.pos());
                finalHit = entHit;
                break;
            }

            HitResult hit = world.raycast(new RaycastContext(
                    pos,
                    nextPos,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    owner
            ));

            if (hit.getType() != HitResult.Type.MISS) {
                Vec3d hp = hit.getPos();
                points.add(hp);

                if (hit instanceof BlockHitResult bhr) {
                    Box hb = resolveBlockHitBox(world, bhr.getBlockPos(), pos, nextPos);
                    finalHit = new HitInfo.BlockHit(hp, bhr, hb);
                }
                break;
            }

            points.add(nextPos);

            vel = vel.multiply(drag).add(0.0, -gravity, 0.0);
            pos = nextPos;
        }

        return new Result(points, finalHit);
    }

    private static HitInfo.EntityHit raycastEntity(Entity owner, Vec3d from, Vec3d to) {
        World world = owner.getWorld();
        if (world == null) return null;

        Box sweep = new Box(from, to).expand(0.35);

        List<Entity> candidates = world.getOtherEntities(owner, sweep, e ->
                e.isAlive()
                        && e instanceof LivingEntity
                        && !e.isSpectator()
                        && e.canHit()
        );

        if (candidates.isEmpty()) return null;

        Entity best = null;
        Vec3d bestPos = null;
        double bestDist2 = Double.MAX_VALUE;

        for (Entity e : candidates) {
            Box bb = e.getBoundingBox().expand(ENTITY_HITBOX_PAD);

            var opt = bb.raycast(from, to);
            if (opt.isEmpty()) continue;

            Vec3d hp = opt.get();
            double d2 = hp.squaredDistanceTo(from);
            if (d2 < bestDist2) {
                bestDist2 = d2;
                best = e;
                bestPos = hp;
            }
        }

        return best == null ? null : new HitInfo.EntityHit(bestPos, best);
    }

    private static Box resolveBlockHitBox(World world, BlockPos bp, Vec3d from, Vec3d to) {
        BlockState state = world.getBlockState(bp);
        VoxelShape shape = state.getCollisionShape(world, bp);
        List<Box> boxes = shape.getBoundingBoxes();

        if (boxes.isEmpty()) {
            return new Box(bp).expand(ENTITY_HITBOX_PAD);
        }

        Box best = null;
        double bestDist2 = Double.MAX_VALUE;

        for (Box local : boxes) {
            Box wb = local.offset(bp).expand(ENTITY_HITBOX_PAD);

            var opt = wb.raycast(from, to);
            if (opt.isEmpty()) continue;

            Vec3d hp = opt.get();
            double d2 = hp.squaredDistanceTo(from);
            if (d2 < bestDist2) {
                bestDist2 = d2;
                best = wb;
            }
        }

        return best != null ? best : boxes.get(0).offset(bp).expand(ENTITY_HITBOX_PAD);
    }
}
