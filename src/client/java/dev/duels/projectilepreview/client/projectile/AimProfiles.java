package dev.duels.projectilepreview.client.projectile;

import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;

/**
 * Mean launch parameters from vanilla 26.3. Random shot spread is deliberately
 * excluded: its server-side random seed cannot be predicted by a client.
 */
public final class AimProfiles {
    private AimProfiles() {}

    private static final Set<Item> POTIONS = Set.of(
            Items.SPLASH_POTION,
            Items.LINGERING_POTION
    );

    private static final float DEG_TO_RAD = 0.017453292f;

    // Speeds and roll offsets from the vanilla item use() methods.
    private static final float BOW_MAX_SPEED = 3.0f;
    private static final float CROSSBOW_SPEED = 3.15f;
    private static final float CROSSBOW_FIREWORK_SPEED = 1.6f;
    private static final float TRIDENT_SPEED = 2.5f;
    private static final float THROWN_SPEED = 1.5f;
    private static final float XP_BOTTLE_SPEED = 0.7f;
    private static final float POTION_SPEED = 0.5f;
    private static final float WIND_CHARGE_SPEED = 1.5f;
    private static final float LOB_ROLL_DEG = -20.0f;

    private static final float MIN_BOW_POWER = 0.1f;
    private static final int TRIDENT_MIN_CHARGE_TICKS = 10;

    // The multishot enchantment defines a 10 degree projectile spread.
    private static final float MULTISHOT_SPREAD_DEG = 10.0f;

    // Per-tick physics from the projectile entity classes.
    private static final double DEFAULT_DRAG = 0.99f;
    private static final double ARROW_GRAVITY = 0.05;
    private static final double THROWN_GRAVITY = 0.03;
    private static final double POTION_GRAVITY = 0.05;
    private static final double XP_BOTTLE_GRAVITY = 0.07;

    // Projectiles spawn 0.1 below eye level, at the player's center.
    private static final double SPAWN_EYE_OFFSET = 0.1;

    private static final int DEFAULT_STEPS = 60;
    private static final int ARROW_STEPS = 100;

    public static Profile match(Player player, ItemStack stack) {
        Item item = stack.getItem();

        if (item instanceof BowItem) {
            return player.isUsingItem() && player.getUseItem() == stack ? Profiles.BOW : null;
        }

        if (item instanceof CrossbowItem) {
            if (!CrossbowItem.isCharged(stack)) return null;
            ChargedProjectiles charged = stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
            if (charged.contains(Items.FIREWORK_ROCKET)) {
                Fireworks fireworks = charged.itemCopies().filter(s -> s.is(Items.FIREWORK_ROCKET))
                        .findFirst().orElseThrow().get(DataComponents.FIREWORKS);
                // Vanilla adds 0..11 random ticks. Show only the guaranteed flight.
                int flightTicks = 10 * (1 + (fireworks == null ? 0 : fireworks.flightDuration()));
                return crossbowProfile(true, flightTicks);
            }
            return Profiles.CROSSBOW;
        }

        if (item instanceof TridentItem) {
            return player.isUsingItem() && player.getUseItem() == stack
                    && !stack.nextDamageWillBreak()
                    && EnchantmentHelper.getTridentSpinAttackStrength(stack, player) == 0.0f
                    ? Profiles.TRIDENT : null;
        }

        if (item == Items.WIND_CHARGE) return Profiles.WIND;
        if (item == Items.EXPERIENCE_BOTTLE) return Profiles.XP_BOTTLE;
        if (POTIONS.contains(item)) return Profiles.POTION;
        if (item == Items.ENDER_PEARL) return Profiles.PEARL;
        if (item == Items.SNOWBALL || item instanceof EggItem) return Profiles.SNOW_EGG;

        return null;
    }

    public interface Profile {
        default int steps() { return DEFAULT_STEPS; }
        default double stepTime() { return 1.0; }

        /** Throwables decay velocity before moving; arrows move first. */
        default boolean decayBeforeMove() { return true; }
        default int collisionTickOffset() { return 0; }

        double drag();
        double gravity();
        Vec3 startPos(Player p, float tickDelta);
        List<Vec3> startVels(Player p, ItemStack stack, float tickDelta);

        /** Where the rendered line begins when the hand origin is selected. */
        Vec3 visualStartPos(Player p, float tickDelta);
    }

    // Hand offsets (forward / side / up) matching the classic look of older releases.
    private static final double BOW_HAND_F = 0.45, BOW_HAND_S = -0.35, BOW_HAND_U = -0.08;
    private static final double CROSSBOW_HAND_F = 0.10, CROSSBOW_HAND_S = 0.00, CROSSBOW_HAND_U = -0.08;
    private static final double TRIDENT_HAND_F = 0.10, TRIDENT_HAND_S = -0.10, TRIDENT_HAND_U = 0.025;
    private static final double THROW_HAND_F = 0.15, THROW_HAND_S = -0.20, THROW_HAND_U = -0.10;

    private static Vec3 handTipPos(Player p, float td, double f, double s, double u) {
        Vec3 eye = p.getEyePosition(td);

        Vec3 forward = shootFromRotationDir(p, 0.0f);

        double yaw = Math.toRadians(p.getYRot());
        Vec3 fwdYaw = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw)).normalize();

        Vec3 right = new Vec3(0.0, 1.0, 0.0).cross(fwdYaw).normalize();
        Vec3 up = forward.cross(right).normalize();

        InteractionHand active = p.isUsingItem() ? p.getUsedItemHand()
                : match(p, p.getMainHandItem()) != null ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        boolean mainArmRight = (p.getMainArm() == HumanoidArm.RIGHT);
        boolean rightSide = (active == InteractionHand.MAIN_HAND) ? mainArmRight : !mainArmRight;
        double sideSign = rightSide ? 1.0 : -1.0;

        return eye
                .add(forward.scale(f))
                .add(right.scale(s * sideSign))
                .add(up.scale(u));
    }

    // Matches Projectile.shootFromRotation: the roll offset only affects the y component.
    private static Vec3 shootFromRotationDir(Player p, float roll) {
        float xRot = p.getXRot();
        float yRot = p.getYRot();
        float x = -Mth.sin(yRot * DEG_TO_RAD) * Mth.cos(xRot * DEG_TO_RAD);
        float y = -Mth.sin((xRot + roll) * DEG_TO_RAD);
        float z = Mth.cos(yRot * DEG_TO_RAD) * Mth.cos(xRot * DEG_TO_RAD);
        return new Vec3(x, y, z).normalize();
    }

    // Shooter momentum is inherited, but vertical momentum only while airborne.
    private static Vec3 launchVelocity(Player p, float roll, float power) {
        Vec3 vel = shootFromRotationDir(p, roll).scale(power);
        Vec3 move = p.getKnownMovement();
        return vel.add(move.x, p.onGround() ? 0.0 : move.y, move.z);
    }

    private static Vec3 spawnPos(Player p, float td) {
        Vec3 pos = p.getPosition(td);
        return new Vec3(pos.x, p.getEyePosition(td).y - SPAWN_EYE_OFFSET, pos.z);
    }

    // Crossbow arrows rotate the view vector around the up axis and ignore shooter momentum.
    private static Vec3 crossbowVelocity(Player p, float angleDeg, float speed) {
        Vec3 up = p.getUpVector(1.0f);
        Vector3f dir = p.getViewVector(1.0f).toVector3f()
                .rotate(new Quaternionf().setAngleAxis(angleDeg * DEG_TO_RAD, up.x, up.y, up.z));
        return new Vec3(dir.x(), dir.y(), dir.z()).normalize().scale(speed);
    }

    private static Profile crossbowProfile(boolean firework, int flightTicks) {
        return new Profile() {
            public Vec3 visualStartPos(Player p, float td) {
                return handTipPos(p, td, CROSSBOW_HAND_F, CROSSBOW_HAND_S, CROSSBOW_HAND_U);
            }
            public boolean decayBeforeMove() { return false; }
            public int collisionTickOffset() { return firework ? 1 : 0; }
            public int steps() { return flightTicks; }
            public double drag() { return firework ? 1.0 : DEFAULT_DRAG; }
            public double gravity() { return firework ? 0.0 : ARROW_GRAVITY; }
            public Vec3 startPos(Player p, float td) {
                return firework ? spawnPos(p, td).add(0.0, SPAWN_EYE_OFFSET - (double) 0.15f, 0.0) : spawnPos(p, td);
            }
            public List<Vec3> startVels(Player p, ItemStack s, float td) {
                int count = s.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).size();
                if (count == 0) return List.of();
                float step = count == 1 ? 0.0f : 2.0f * MULTISHOT_SPREAD_DEG / (count - 1);
                float offset = ((count - 1) % 2) * step / 2.0f;
                List<Vec3> velocities = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    float angle = offset + ((i + 1) / 2) * (i % 2 == 0 ? 1 : -1) * step;
                    velocities.add(crossbowVelocity(p, angle, firework ? CROSSBOW_FIREWORK_SPEED : CROSSBOW_SPEED));
                }
                return velocities;
            }
        };
    }

    private static int useTicks(Player p, ItemStack stack) {
        return stack.getUseDuration(p) - p.getUseItemRemainingTicks();
    }

    private static final class Profiles {

        static final Profile BOW = new Profile() {
            public Vec3 visualStartPos(Player p, float td) {
                return handTipPos(p, td, BOW_HAND_F, BOW_HAND_S, BOW_HAND_U);
            }

            public boolean decayBeforeMove() { return false; }
            public int steps() { return ARROW_STEPS; }
            public double drag() { return DEFAULT_DRAG; }
            public double gravity() { return ARROW_GRAVITY; }

            public Vec3 startPos(Player p, float td) {
                return spawnPos(p, td);
            }

            public List<Vec3> startVels(Player p, ItemStack s, float td) {
                float power = BowItem.getPowerForTime(useTicks(p, s));
                if (power < MIN_BOW_POWER) return List.of();
                return List.of(launchVelocity(p, 0.0f, power * BOW_MAX_SPEED));
            }
        };

        static final Profile CROSSBOW = crossbowProfile(false, ARROW_STEPS);

        static final Profile TRIDENT = new Profile() {
            public Vec3 visualStartPos(Player p, float td) {
                return handTipPos(p, td, TRIDENT_HAND_F, TRIDENT_HAND_S, TRIDENT_HAND_U);
            }

            public boolean decayBeforeMove() { return false; }
            public int steps() { return ARROW_STEPS; }
            public double drag() { return DEFAULT_DRAG; }
            public double gravity() { return ARROW_GRAVITY; }

            public Vec3 startPos(Player p, float td) {
                return spawnPos(p, td);
            }

            public List<Vec3> startVels(Player p, ItemStack s, float td) {
                if (useTicks(p, s) < TRIDENT_MIN_CHARGE_TICKS) return List.of();
                return List.of(launchVelocity(p, 0.0f, TRIDENT_SPEED));
            }
        };

        static final Profile SNOW_EGG = new Profile() {
            public Vec3 visualStartPos(Player p, float td) {
                return handTipPos(p, td, THROW_HAND_F, THROW_HAND_S, THROW_HAND_U);
            }

            public double drag() { return DEFAULT_DRAG; }
            public double gravity() { return THROWN_GRAVITY; }

            public Vec3 startPos(Player p, float td) {
                return spawnPos(p, td);
            }

            public List<Vec3> startVels(Player p, ItemStack s, float td) {
                return List.of(launchVelocity(p, 0.0f, THROWN_SPEED));
            }
        };

        static final Profile PEARL = new Profile() {
            public Vec3 visualStartPos(Player p, float td) {
                return handTipPos(p, td, THROW_HAND_F, THROW_HAND_S, THROW_HAND_U);
            }

            public double drag() { return DEFAULT_DRAG; }
            public double gravity() { return THROWN_GRAVITY; }

            public Vec3 startPos(Player p, float td) {
                return spawnPos(p, td);
            }

            public List<Vec3> startVels(Player p, ItemStack s, float td) {
                return List.of(launchVelocity(p, 0.0f, THROWN_SPEED));
            }
        };

        static final Profile XP_BOTTLE = new Profile() {
            public Vec3 visualStartPos(Player p, float td) {
                return handTipPos(p, td, THROW_HAND_F, THROW_HAND_S, THROW_HAND_U);
            }

            public double drag() { return DEFAULT_DRAG; }
            public double gravity() { return XP_BOTTLE_GRAVITY; }

            public Vec3 startPos(Player p, float td) {
                return spawnPos(p, td);
            }

            public List<Vec3> startVels(Player p, ItemStack s, float td) {
                return List.of(launchVelocity(p, LOB_ROLL_DEG, XP_BOTTLE_SPEED));
            }
        };

        static final Profile POTION = new Profile() {
            public Vec3 visualStartPos(Player p, float td) {
                return handTipPos(p, td, THROW_HAND_F, THROW_HAND_S, THROW_HAND_U);
            }

            public double drag() { return DEFAULT_DRAG; }
            public double gravity() { return POTION_GRAVITY; }

            public Vec3 startPos(Player p, float td) {
                return spawnPos(p, td);
            }

            public List<Vec3> startVels(Player p, ItemStack s, float td) {
                return List.of(launchVelocity(p, LOB_ROLL_DEG, POTION_SPEED));
            }
        };

        // Wind charges fly dead straight: no gravity, no drag, spawned at full eye height.
        static final Profile WIND = new Profile() {
            public Vec3 visualStartPos(Player p, float td) {
                return handTipPos(p, td, THROW_HAND_F, THROW_HAND_S, THROW_HAND_U);
            }

            public double drag() { return 1.0; }
            public double gravity() { return 0.0; }

            public Vec3 startPos(Player p, float td) {
                Vec3 pos = p.getPosition(td);
                return new Vec3(pos.x, p.getEyePosition(td).y, pos.z);
            }

            public List<Vec3> startVels(Player p, ItemStack s, float td) {
                return List.of(launchVelocity(p, 0.0f, WIND_CHARGE_SPEED));
            }
        };
    }
}
