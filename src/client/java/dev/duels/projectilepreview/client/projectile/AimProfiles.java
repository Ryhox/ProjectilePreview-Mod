package dev.duels.projectilepreview.client.projectile;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

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

    public static Profile match(Player player, ItemStack stack) {
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
        Vec3 startPos(Player p, float tickDelta);
        List<Vec3> startVels(Player p, ItemStack stack, float tickDelta);
        default Vec3 visualStartPos(Player p, ItemStack stack, float tickDelta) { return null; }
    }

    private static Vec3 handTipPos(Player p, float td, double f, double s, double u) {
        Vec3 eye = p.getEyePosition(td);

        Vec3 forward = p.getViewVector(td).normalize();

        double yaw = Math.toRadians(p.getViewYRot(td));
        Vec3 fwdYaw = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw)).normalize();

        Vec3 right = new Vec3(0.0, 1.0, 0.0).cross(fwdYaw).normalize();
        Vec3 up = forward.cross(right).normalize();

        InteractionHand active = p.isUsingItem() ? p.getUsedItemHand() : InteractionHand.MAIN_HAND;
        boolean mainArmRight = (p.getMainArm() == HumanoidArm.RIGHT);
        boolean rightSide = (active == InteractionHand.MAIN_HAND) ? mainArmRight : !mainArmRight;
        double sideSign = rightSide ? 1.0 : -1.0;

        return eye
                .add(forward.scale(f))
                .add(right.scale(s * sideSign))
                .add(up.scale(u));
    }

    private static Vec3 rotateYaw(Vec3 v, double deg) {
        double r = Math.toRadians(deg);
        return new Vec3(
                v.x * Math.cos(r) - v.z * Math.sin(r),
                v.y,
                v.x * Math.sin(r) + v.z * Math.cos(r)
        );
    }

    private static int multishotLevel(Player p, ItemStack stack) {
        var registryAccess = p.registryAccess();
        var enchRegistry = registryAccess.lookupOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> holder = enchRegistry.getOrThrow(Enchantments.MULTISHOT);
        return EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
    }

    private static Vec3 aimPoint(Player p, float td, double maxDist) {
        var world = p.level();
        Vec3 eye = p.getEyePosition(td);
        Vec3 dir = p.getViewVector(td).normalize();
        Vec3 end = eye.add(dir.scale(maxDist));

        HitResult hit = world.clip(new ClipContext(
                eye, end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                p
        ));

        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    private static Vec3 dirToCrosshair(Player p, float td, Vec3 startPos, double maxDist) {
        if (!AIM_TO_CROSSHAIR) return p.getViewVector(td).normalize();

        Vec3 target = aimPoint(p, td, maxDist);
        Vec3 d = target.subtract(startPos);
        double len = d.length();
        if (len < 1e-6) return p.getViewVector(td).normalize();
        return d.scale(1.0 / len);
    }

    private static Vec3 dirWithPitchOffset(Player p, float td, float pitchOffsetDeg) {
        float yaw = p.getViewYRot(td);
        float pitch = p.getViewXRot(td) + pitchOffsetDeg;

        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);

        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z =  Math.cos(yawRad) * Math.cos(pitchRad);

        Vec3 v = new Vec3(x, y, z);
        double len = v.length();
        return len < 1e-6 ? p.getViewVector(td).normalize() : v.scale(1.0 / len);
    }

    private static double tridentChargeTicks(Player p, ItemStack trident, float td) {
        // TODO: verify getUseDuration / getUseItemRemainingTicks if compile errors appear
        int used = trident.getUseDuration(p) - p.getUseItemRemainingTicks();
        return used + td;
    }

    private static Vec3 windChargeStartPos(Player p, float td) {
        Vec3 base = p.position();
        Vec3 eye = p.getEyePosition(td);
        return new Vec3(base.x, eye.y, base.z);
    }

    private static final class Profiles {

        static final Profile BOW = new Profile() {
            public double drag() { return 0.99; }
            public double gravity() { return 0.05; }

            public Vec3 startPos(Player p, float td) {
                return handTipPos(p, td, StartPosTuning.BOW_F, StartPosTuning.BOW_S, StartPosTuning.BOW_U);
            }

            public List<Vec3> startVels(Player p, ItemStack s, float td) {
                // TODO: verify BowItem.getPowerForTime — may be getPullProgress if compile error
                int used = s.getUseDuration(p) - p.getUseItemRemainingTicks();
                float pull = BowItem.getPowerForTime(used);
                if (pull < 0.05f) return List.of();

                Vec3 start = startPos(p, td);
                Vec3 dir = dirToCrosshair(p, td, start, AIM_MAX_DIST);
                return List.of(dir.scale(3.0 * pull).add(p.getDeltaMovement()));
            }
        };

        static final Profile CROSSBOW = new Profile() {
            public double drag() { return 0.99; }
            public double gravity() { return 0.05; }

            public Vec3 startPos(Player p, float td) {
                return handTipPos(p, td, StartPosTuning.CROSS_F, StartPosTuning.CROSS_S, StartPosTuning.CROSS_U);
            }

            public List<Vec3> startVels(Player p, ItemStack s, float td) {
                Vec3 start = startPos(p, td);
                Vec3 dir = dirToCrosshair(p, td, start, AIM_MAX_DIST);
                Vec3 base = dir.scale(3.15).add(p.getDeltaMovement());

                int ms = multishotLevel(p, s);
                if (ms > 0) return List.of(rotateYaw(base, -10), base, rotateYaw(base, 10));
                return List.of(base);
            }
        };

        static final Profile TRIDENT = new Profile() {
            public double drag() { return 0.99; }
            public double gravity() { return 0.05; }

            public Vec3 startPos(Player p, float td) {
                Vec3 base = handTipPos(p, td, StartPosTuning.TRIDENT_F, StartPosTuning.TRIDENT_S, StartPosTuning.TRIDENT_U);

                double t = tridentChargeTicks(p, p.getUseItem(), td);

                Vec3 forward = p.getViewVector(td).normalize();
                float yawDeg = p.getViewYRot(td);
                double yaw = Math.toRadians(yawDeg);
                Vec3 right = new Vec3(Math.cos(yaw), 0.0, Math.sin(yaw)).normalize();
                Vec3 up = forward.cross(right).normalize();

                double raise = Math.min(TRIDENT_MAX_RAISE, t * TRIDENT_RAISE_PER_TICK);
                double fwd = Math.min(TRIDENT_MAX_FORWARD, t * TRIDENT_FORWARD_PER_TICK);

                return base.add(up.scale(raise)).add(forward.scale(fwd));
            }

            public List<Vec3> startVels(Player p, ItemStack s, float td) {
                Vec3 start = startPos(p, td);
                Vec3 dir = dirToCrosshair(p, td, start, AIM_MAX_DIST);
                return List.of(dir.scale(3.0).add(p.getDeltaMovement()));
            }
        };

        static final Profile SNOW_EGG = new Profile() {
            public double drag() { return 0.99; }
            public double gravity() { return 0.03; }

            public Vec3 startPos(Player p, float td) {
                return handTipPos(p, td, StartPosTuning.THROW_F, StartPosTuning.THROW_S, StartPosTuning.THROW_U);
            }

            public List<Vec3> startVels(Player p, ItemStack s, float td) {
                Vec3 start = startPos(p, td);
                Vec3 dir = dirToCrosshair(p, td, start, AIM_MAX_DIST);
                return List.of(dir.scale(1.5).add(p.getDeltaMovement()));
            }
        };

        static final Profile PEARL = new Profile() {
            public double drag() { return 0.99; }
            public double gravity() { return 0.03; }

            public Vec3 startPos(Player p, float td) {
                return handTipPos(p, td, StartPosTuning.THROW_F, StartPosTuning.THROW_S, StartPosTuning.THROW_U);
            }

            public List<Vec3> startVels(Player p, ItemStack s, float td) {
                Vec3 start = startPos(p, td);
                Vec3 dir = dirToCrosshair(p, td, start, AIM_MAX_DIST);
                return List.of(dir.scale(1.5).add(p.getDeltaMovement()));
            }
        };

        static final Profile XP_BOTTLE = new Profile() {
            public double drag() { return 0.99; }
            public double gravity() { return 0.07; }

            public Vec3 startPos(Player p, float td) {
                return handTipPos(p, td, StartPosTuning.THROW_F, StartPosTuning.THROW_S, StartPosTuning.THROW_U);
            }

            public List<Vec3> startVels(Player p, ItemStack s, float td) {
                Vec3 start = startPos(p, td);

                Vec3 dir = dirToCrosshair(p, td, start, AIM_MAX_DIST);
                Vec3 off = dirWithPitchOffset(p, td, -20.0f);
                dir = dir.lerp(off, 0.65).normalize();

                return List.of(dir.scale(0.7).add(p.getDeltaMovement()));
            }
        };

        static final Profile POTION = new Profile() {
            public double drag() { return 0.99; }
            public double gravity() { return 0.05; }

            public Vec3 startPos(Player p, float td) {
                return handTipPos(p, td, StartPosTuning.THROW_F, StartPosTuning.THROW_S, StartPosTuning.THROW_U);
            }

            public List<Vec3> startVels(Player p, ItemStack s, float td) {
                Vec3 start = startPos(p, td);

                Vec3 dir = dirToCrosshair(p, td, start, AIM_MAX_DIST);
                Vec3 off = dirWithPitchOffset(p, td, -20.0f);
                dir = dir.lerp(off, 0.65).normalize();

                return List.of(dir.scale(0.5).add(p.getDeltaMovement()));
            }
        };

        static final Profile WIND = new Profile() {
            public double drag() { return 0.995; }
            public double gravity() { return 0.0; }

            public Vec3 startPos(Player p, float td) {
                return handTipPos(p, td, StartPosTuning.WIND_F, StartPosTuning.WIND_S, StartPosTuning.WIND_U);
            }

            public List<Vec3> startVels(Player p, ItemStack s, float td) {
                Vec3 start = startPos(p, td);

                Vec3 dir = dirToCrosshair(p, td, start, AIM_MAX_DIST);
                Vec3 vel = dir.scale(1.5);

                Vec3 move = p.getDeltaMovement();
                double my = p.onGround() ? 0.0 : move.y;
                vel = vel.add(move.x, my, move.z);

                return List.of(vel);
            }

            public Vec3 visualStartPos(Player p, ItemStack s, float td) {
                return null;
            }
        };

    }
}


//SABBYYYYYYYYYYYY GOONEERRRSSSS
