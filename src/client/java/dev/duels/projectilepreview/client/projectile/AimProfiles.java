package dev.duels.projectilepreview.client.projectile;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.List;
import java.util.Set;

public final class AimProfiles {
    private AimProfiles() {}

    private static final Set<Item> POTIONS = Set.of(
            Items.SPLASH_POTION,
            Items.LINGERING_POTION
    );

    private static final boolean AIM_TO_CROSSHAIR = true;
    private static final double AIM_MAX_DIST = 128.0;

    private static final double TRIDENT_RAISE_PER_TICK = 0.006;
    private static final double TRIDENT_MAX_RAISE = 0.04;
    private static final double TRIDENT_FORWARD_PER_TICK = 0.003;
    private static final double TRIDENT_MAX_FORWARD = 0.02;

    public static Profile match(PlayerEntity player, ItemStack stack) {
        Item item = stack.getItem();

        if (item instanceof BowItem) {
            if (!player.isUsingItem()) return null;
            return Profiles.BOW;
        }

        if (item instanceof CrossbowItem) {
            if (!CrossbowItem.isCharged(stack)) return null;
            return Profiles.CROSSBOW;
        }

        if (item instanceof TridentItem) {
            if (!player.isUsingItem()) return null;
            return Profiles.TRIDENT;
        }

        if (item == Items.WIND_CHARGE) return Profiles.WIND;

        if (item == Items.EXPERIENCE_BOTTLE) return Profiles.XP_BOTTLE;
        if (POTIONS.contains(item)) return Profiles.POTION;
        if (item == Items.ENDER_PEARL) return Profiles.PEARL;
        if (item == Items.SNOWBALL || item == Items.EGG) return Profiles.SNOW_EGG;

        return null;
    }

    public interface Profile {
        default int steps() { return 60; }
        default double stepTime() { return 1.0; }
        double drag();
        double gravity();
        Vec3d startPos(PlayerEntity p, float tickDelta);
        List<Vec3d> startVels(PlayerEntity p, ItemStack stack, float tickDelta);
    }

    private static Vec3d handTipPos(PlayerEntity p, float td, double f, double s, double u) {
        Vec3d eye = p.getCameraPosVec(td);

        Vec3d forward = p.getRotationVec(td).normalize();

        double yaw = Math.toRadians(p.getYaw(td));
        Vec3d fwdYaw = new Vec3d(-Math.sin(yaw), 0.0, Math.cos(yaw)).normalize();

        Vec3d right = new Vec3d(0.0, 1.0, 0.0).crossProduct(fwdYaw).normalize();
        Vec3d up = forward.crossProduct(right).normalize();

        Hand active = p.isUsingItem() ? p.getActiveHand() : Hand.MAIN_HAND;
        boolean mainArmRight = (p.getMainArm() == Arm.RIGHT);
        boolean rightSide = (active == Hand.MAIN_HAND) ? mainArmRight : !mainArmRight;
        double sideSign = rightSide ? 1.0 : -1.0;

        return eye
                .add(forward.multiply(f))
                .add(right.multiply(s * sideSign))
                .add(up.multiply(u));
    }

    private static Vec3d rotateYaw(Vec3d v, double deg) {
        double r = Math.toRadians(deg);
        return new Vec3d(
                v.x * Math.cos(r) - v.z * Math.sin(r),
                v.y,
                v.x * Math.sin(r) + v.z * Math.cos(r)
        );
    }

    private static int multishotLevel(PlayerEntity p, ItemStack stack) {
        var lookup = p.getRegistryManager();

        var enchWrapper = lookup.getOrThrow(RegistryKeys.ENCHANTMENT);

        RegistryEntry.Reference<Enchantment> entry = enchWrapper.getOrThrow(Enchantments.MULTISHOT);

        return EnchantmentHelper.getLevel(entry, stack);
    }

    private static Vec3d aimPoint(PlayerEntity p, float td, double maxDist) {
        var world = p.getEntityWorld();
        Vec3d eye = p.getCameraPosVec(td);
        Vec3d dir = p.getRotationVec(td).normalize();
        Vec3d end = eye.add(dir.multiply(maxDist));

        HitResult hit = world.raycast(new RaycastContext(
                eye, end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                p
        ));

        return hit.getType() == HitResult.Type.MISS ? end : hit.getPos();
    }

    private static Vec3d dirToCrosshair(PlayerEntity p, float td, Vec3d startPos, double maxDist) {
        if (!AIM_TO_CROSSHAIR) return p.getRotationVec(td).normalize();

        Vec3d target = aimPoint(p, td, maxDist);
        Vec3d d = target.subtract(startPos);
        double len = d.length();
        if (len < 1e-6) return p.getRotationVec(td).normalize();
        return d.multiply(1.0 / len);
    }

    private static Vec3d dirWithPitchOffset(PlayerEntity p, float td, float pitchOffsetDeg) {
        float yaw = p.getYaw(td);
        float pitch = p.getPitch(td) + pitchOffsetDeg;

        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);

        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z =  Math.cos(yawRad) * Math.cos(pitchRad);

        Vec3d v = new Vec3d(x, y, z);
        double len = v.length();
        return len < 1e-6 ? p.getRotationVec(td).normalize() : v.multiply(1.0 / len);
    }

    private static double tridentChargeTicks(PlayerEntity p, ItemStack trident, float td) {
        int used = trident.getMaxUseTime(p) - p.getItemUseTimeLeft();
        return used + td;
    }

    private static final class Profiles {

        static final Profile BOW = new Profile() {
            public double drag() { return 0.99; }
            public double gravity() { return 0.05; }

            public Vec3d startPos(PlayerEntity p, float td) {
                return handTipPos(p, td, StartPosTuning.BOW_F, StartPosTuning.BOW_S, StartPosTuning.BOW_U);
            }

            public List<Vec3d> startVels(PlayerEntity p, ItemStack s, float td) {
                int used = s.getMaxUseTime(p) - p.getItemUseTimeLeft();
                float pull = BowItem.getPullProgress(used);
                if (pull < 0.05f) return List.of();

                Vec3d start = startPos(p, td);
                Vec3d dir = dirToCrosshair(p, td, start, AIM_MAX_DIST);
                return List.of(dir.multiply(3.0 * pull).add(p.getVelocity()));
            }
        };

        static final Profile CROSSBOW = new Profile() {
            public double drag() { return 0.99; }
            public double gravity() { return 0.05; }

            public Vec3d startPos(PlayerEntity p, float td) {
                return handTipPos(p, td, StartPosTuning.CROSS_F, StartPosTuning.CROSS_S, StartPosTuning.CROSS_U);
            }

            public List<Vec3d> startVels(PlayerEntity p, ItemStack s, float td) {
                Vec3d start = startPos(p, td);
                Vec3d dir = dirToCrosshair(p, td, start, AIM_MAX_DIST);
                Vec3d base = dir.multiply(3.15).add(p.getVelocity());

                int ms = multishotLevel(p, s);
                if (ms > 0) return List.of(rotateYaw(base, -10), base, rotateYaw(base, 10));
                return List.of(base);
            }
        };

        static final Profile TRIDENT = new Profile() {
            public double drag() { return 0.99; }
            public double gravity() { return 0.05; }

            public Vec3d startPos(PlayerEntity p, float td) {
                Vec3d base = handTipPos(p, td, StartPosTuning.TRIDENT_F, StartPosTuning.TRIDENT_S, StartPosTuning.TRIDENT_U);

                double t = tridentChargeTicks(p, p.getActiveItem(), td);

                Vec3d forward = p.getRotationVec(td).normalize();
                float yawDeg = p.getYaw(td);
                double yaw = Math.toRadians(yawDeg);
                Vec3d right = new Vec3d(Math.cos(yaw), 0.0, Math.sin(yaw)).normalize();
                Vec3d up = forward.crossProduct(right).normalize();

                double raise = Math.min(TRIDENT_MAX_RAISE, t * TRIDENT_RAISE_PER_TICK);
                double fwd = Math.min(TRIDENT_MAX_FORWARD, t * TRIDENT_FORWARD_PER_TICK);

                return base.add(up.multiply(raise)).add(forward.multiply(fwd));
            }

            public List<Vec3d> startVels(PlayerEntity p, ItemStack s, float td) {
                Vec3d start = startPos(p, td);
                Vec3d dir = dirToCrosshair(p, td, start, AIM_MAX_DIST);
                return List.of(dir.multiply(3.0).add(p.getVelocity()));
            }
        };

        static final Profile SNOW_EGG = new Profile() {
            public double drag() { return 0.99; }
            public double gravity() { return 0.03; }

            public Vec3d startPos(PlayerEntity p, float td) {
                return handTipPos(p, td, StartPosTuning.THROW_F, StartPosTuning.THROW_S, StartPosTuning.THROW_U);
            }

            public List<Vec3d> startVels(PlayerEntity p, ItemStack s, float td) {
                Vec3d start = startPos(p, td);
                Vec3d dir = dirToCrosshair(p, td, start, AIM_MAX_DIST);
                return List.of(dir.multiply(1.5).add(p.getVelocity()));
            }
        };

        static final Profile PEARL = new Profile() {
            public double drag() { return 0.99; }
            public double gravity() { return 0.03; }

            public Vec3d startPos(PlayerEntity p, float td) {
                return handTipPos(p, td, StartPosTuning.THROW_F, StartPosTuning.THROW_S, StartPosTuning.THROW_U);
            }

            public List<Vec3d> startVels(PlayerEntity p, ItemStack s, float td) {
                Vec3d start = startPos(p, td);
                Vec3d dir = dirToCrosshair(p, td, start, AIM_MAX_DIST);
                return List.of(dir.multiply(1.5).add(p.getVelocity()));
            }
        };

        static final Profile XP_BOTTLE = new Profile() {
            public double drag() { return 0.99; }
            public double gravity() { return 0.07; }

            public Vec3d startPos(PlayerEntity p, float td) {
                return handTipPos(p, td, StartPosTuning.THROW_F, StartPosTuning.THROW_S, StartPosTuning.THROW_U);
            }

            public List<Vec3d> startVels(PlayerEntity p, ItemStack s, float td) {
                Vec3d start = startPos(p, td);

                Vec3d dir = dirToCrosshair(p, td, start, AIM_MAX_DIST);
                Vec3d off = dirWithPitchOffset(p, td, -20.0f);
                dir = dir.lerp(off, 0.65).normalize();

                return List.of(dir.multiply(0.7).add(p.getVelocity()));
            }
        };

        static final Profile POTION = new Profile() {
            public double drag() { return 0.99; }
            public double gravity() { return 0.05; }

            public Vec3d startPos(PlayerEntity p, float td) {
                return handTipPos(p, td, StartPosTuning.THROW_F, StartPosTuning.THROW_S, StartPosTuning.THROW_U);
            }

            public List<Vec3d> startVels(PlayerEntity p, ItemStack s, float td) {
                Vec3d start = startPos(p, td);

                Vec3d dir = dirToCrosshair(p, td, start, AIM_MAX_DIST);
                Vec3d off = dirWithPitchOffset(p, td, -20.0f);
                dir = dir.lerp(off, 0.65).normalize();

                return List.of(dir.multiply(0.5).add(p.getVelocity()));
            }
        };

        static final Profile WIND = new Profile() {
            public double drag() { return 0.995; }
            public double gravity() { return 0.0; }

            public Vec3d startPos(PlayerEntity p, float td) {
                return handTipPos(p, td, StartPosTuning.WIND_F, StartPosTuning.WIND_S, StartPosTuning.WIND_U);
            }

            public List<Vec3d> startVels(PlayerEntity p, ItemStack s, float td) {
                Vec3d start = startPos(p, td);
                Vec3d dir = dirToCrosshair(p, td, start, AIM_MAX_DIST);
                return List.of(dir.multiply(1.6).add(p.getVelocity()));
            }
        };
    }
}
