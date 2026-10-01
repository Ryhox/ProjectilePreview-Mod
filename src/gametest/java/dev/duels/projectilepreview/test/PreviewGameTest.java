package dev.duels.projectilepreview.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.duels.projectilepreview.client.ConfigScreen;
import dev.duels.projectilepreview.client.PreviewConfig;
import dev.duels.projectilepreview.client.projectile.AimProfiles;
import dev.duels.projectilepreview.client.projectile.TrajectorySim;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.throwableitemprojectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Runs in a real 26.3 client; test code is excluded from the released jar. */
public final class PreviewGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().adjustSettings(settings -> {
            settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            settings.setAllowCommands(true);
        }).create()) {
            sp.getConnection().waitForChunksRender();
            ctx.getInput().resizeWindow(1280, 720);
            ctx.runOnClient(mc -> {
                mc.options.guiScale().set(2);
                mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
            });
            command(ctx, "fill -12 99 -12 12 99 24 minecraft:grass_block",
                    "fill -12 100 -12 12 115 24 minecraft:air", "tp @s 0 100 0 0 0",
                    "time set day", "weather clear");
            ctx.waitTicks(30);
            sp.getConnection().waitForChunksRender();
            ctx.runOnClient(mc -> {
                PreviewConfig.get().enabled = true;
                PreviewConfig.get().maxTicks = 100;
                PreviewConfig.get().showImpactMarker = true;
            });

            // Compare integration against vanilla entities, with shot uncertainty
            // disabled so only the physics (not server randomness) is measured.
            sp.getServer().runOnServer(server -> {
                var p = sp.getConnection().getServerPlayer();
                p.setXRot(-30.0f);
                p.setYRot(90.0f);
                p.setDeltaMovement(Vec3.ZERO);
                for (Item item : List.of(Items.EGG, Items.BLUE_EGG, Items.BROWN_EGG,
                        Items.SNOWBALL, Items.EXPERIENCE_BOTTLE, Items.SPLASH_POTION, Items.LINGERING_POTION)) {
                    ItemStack stack = new ItemStack(item);
                    var profile = AimProfiles.match(p, stack);
                    require(profile != null, "Missing profile for " + item);
                    Projectile actual;
                    if (item instanceof EggItem) actual = new ThrownEgg(p.level(), p, stack);
                    else if (item == Items.SNOWBALL) actual = new Snowball(p.level(), p, stack);
                    else if (item == Items.EXPERIENCE_BOTTLE) actual = new ThrownExperienceBottle(p.level(), p, stack);
                    else if (item == Items.SPLASH_POTION) actual = new ThrownSplashPotion(p.level(), p, stack);
                    else actual = new ThrownLingeringPotion(p.level(), p, stack);
                    float roll = item == Items.EXPERIENCE_BOTTLE || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION ? -20.0f : 0.0f;
                    float speed = item == Items.EXPERIENCE_BOTTLE ? 0.7f : roll != 0 ? 0.5f : 1.5f;
                    actual.shootFromRotation(p, p.getXRot(), p.getYRot(), roll, speed, 0.0f);
                    Vec3 start = profile.startPos(p, 1.0f);
                    Vec3 velocity = profile.startVels(p, stack, 1.0f).getFirst();
                    require(start.distanceTo(actual.position()) < 1e-6, "Incorrect spawn for " + item);
                    require(velocity.distanceTo(actual.getDeltaMovement()) < 1e-6, "Incorrect launch for " + item);
                    var result = TrajectorySim.simulate(p, start, velocity, profile.gravity(), profile.drag(), 12, 1.0, profile.decayBeforeMove());
                    for (int tick = 1; tick < result.points().size(); tick++) {
                        actual.tick();
                        require(actual.position().distanceTo(result.points().get(tick)) < 1e-5,
                                "Vanilla trajectory mismatch for " + item + " at tick " + tick);
                    }
                    actual.discard();
                }
                ItemStack crossbow = new ItemStack(Items.CROSSBOW);
                crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.FIREWORK_ROCKET))));
                var rocket = AimProfiles.match(p, crossbow);
                require(rocket != null && rocket.gravity() == 0 && rocket.drag() == 1, "Rocket physics");
                require(Math.abs(rocket.startVels(p, crossbow, 1).getFirst().length() - 1.6f) < 1e-6, "Rocket speed");
                var actualRocket = new FireworkRocketEntity(p.level(), new ItemStack(Items.FIREWORK_ROCKET), p,
                        p.getX(), p.getEyeY() - 0.15f, p.getZ(), true);
                p.setXRot(0);
                Vec3 view = p.getViewVector(1);
                actualRocket.shoot(view.x, view.y, view.z, 1.6f, 0);
                var predictedRocket = TrajectorySim.simulate(p, rocket.startPos(p, 1), rocket.startVels(p, crossbow, 1).getFirst(), 0, 1, 10, 1, false);
                for (int tick = 1; tick < predictedRocket.points().size(); tick++) {
                    actualRocket.tick();
                    require(actualRocket.position().distanceTo(predictedRocket.points().get(tick)) < 1e-5,
                            "Rocket trajectory at " + tick + ": actual=" + actualRocket.position() + " predicted=" + predictedRocket.points().get(tick));
                }
                actualRocket.discard();
                crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(
                        new ItemStack(Items.ARROW), new ItemStack(Items.ARROW), new ItemStack(Items.ARROW))));
                require(AimProfiles.match(p, crossbow).startVels(p, crossbow, 1).size() == 3, "Loaded multishot count");
                require(AimProfiles.match(p, new ItemStack(Items.WOODEN_SPEAR)) == null, "Spear is a melee weapon");
                p.setYRot(0);
            });

            for (String item : List.of("blue_egg", "brown_egg", "ender_pearl", "snowball", "splash_potion", "lingering_potion", "experience_bottle", "wind_charge")) {
                command(ctx, "item replace entity @s weapon.mainhand with minecraft:" + item);
                ctx.waitTicks(8);
                ctx.runOnClient(mc -> {
                    mc.player.setYRot(0);
                    mc.player.setXRot(12);
                    require(AimProfiles.match(mc.player, mc.player.getMainHandItem()) != null, "Visual profile missing");
                });
                ctx.takeScreenshot("26.3-" + item);
            }
            command(ctx, "item replace entity @s weapon.mainhand with minecraft:bow");
            ctx.waitTicks(8);
            ctx.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            ctx.waitTicks(25);
            sp.getServer().runOnServer(server -> {
                var p = sp.getConnection().getServerPlayer();
                ItemStack bow = p.getUseItem();
                var profile = AimProfiles.match(p, bow);
                require(profile != null, "Charged bow profile");
                p.setXRot(-30);
                var arrow = new Arrow(p.level(), p, new ItemStack(Items.ARROW), bow);
                arrow.shootFromRotation(p, p.getXRot(), p.getYRot(), 0, 3.0f, 0);
                verifyFlight(p, bow, profile, arrow);
            });
            ctx.takeScreenshot("26.3-bow");
            ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            command(ctx, "item replace entity @s weapon.mainhand with minecraft:trident");
            ctx.waitTicks(8);
            ctx.getInput().holdMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            ctx.waitTicks(15);
            sp.getServer().runOnServer(server -> {
                var p = sp.getConnection().getServerPlayer();
                ItemStack trident = p.getUseItem();
                var profile = AimProfiles.match(p, trident);
                require(profile != null, "Charged trident profile");
                p.setXRot(-30);
                var actual = new ThrownTrident(p.level(), p, trident);
                actual.shootFromRotation(p, p.getXRot(), p.getYRot(), 0, 2.5f, 0);
                verifyFlight(p, trident, profile, actual);
            });
            ctx.takeScreenshot("26.3-trident");
            ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_RIGHT);
            command(ctx, "item replace entity @s weapon.mainhand with minecraft:crossbow[charged_projectiles=[{id:'minecraft:firework_rocket'}]]");
            ctx.waitTicks(8);
            ctx.takeScreenshot("26.3-crossbow-firework");
            command(ctx, "item replace entity @s weapon.mainhand with minecraft:crossbow[charged_projectiles=[{id:'minecraft:arrow'},{id:'minecraft:arrow'},{id:'minecraft:arrow'}],enchantments={'minecraft:multishot':1}]");
            ctx.waitTicks(8);
            ctx.takeScreenshot("26.3-crossbow-multishot");
            command(ctx, "item replace entity @s weapon.mainhand with minecraft:air",
                    "item replace entity @s weapon.offhand with minecraft:blue_egg");
            ctx.waitTicks(8);
            ctx.takeScreenshot("26.3-offhand-blue-egg");
            command(ctx, "item replace entity @s weapon.mainhand with minecraft:snowball",
                    "summon minecraft:iron_golem 0 100 6 {NoAI:1b,NoGravity:1b}");
            ctx.runOnClient(mc -> mc.player.setXRot(0));
            ctx.waitTicks(12);
            ctx.takeScreenshot("26.3-entity-hit");
            command(ctx, "kill @e[type=minecraft:iron_golem]", "setblock 0 101 5 minecraft:sandstone");
            ctx.waitTicks(12);
            ctx.takeScreenshot("26.3-block-hit");
            ctx.runOnClient(mc -> mc.options.improvedTransparency().set(true));
            ctx.waitTicks(5);
            ctx.takeScreenshot("26.3-improved-transparency");
            command(ctx, "item replace entity @s weapon.mainhand with minecraft:trident[enchantments={'minecraft:riptide':1}]");
            ctx.waitTicks(8);
            ctx.runOnClient(mc -> {
                mc.player.startUsingItem(InteractionHand.MAIN_HAND);
                require(AimProfiles.match(mc.player, mc.player.getUseItem()) == null, "Riptide must not show a thrown trident");
                mc.player.stopUsingItem();
            });

            ctx.getInput().pressKey(InputConstants.KEY_P);
            ctx.waitForScreen(ConfigScreen.class);
            ctx.takeScreenshot("26.3-config");
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(5);
        }
    }

    private static void verifyFlight(net.minecraft.world.entity.player.Player p, ItemStack stack, AimProfiles.Profile profile, Projectile actual) {
        Vec3 start = profile.startPos(p, 1);
        Vec3 velocity = profile.startVels(p, stack, 1).getFirst();
        require(start.distanceTo(actual.position()) < 1e-6, "Arrow/trident spawn");
        require(velocity.distanceTo(actual.getDeltaMovement()) < 1e-6, "Arrow/trident launch");
        var prediction = TrajectorySim.simulate(p, start, velocity, profile.gravity(), profile.drag(), 12, 1, profile.decayBeforeMove());
        for (int tick = 1; tick < prediction.points().size(); tick++) {
            actual.tick();
            require(actual.position().distanceTo(prediction.points().get(tick)) < 1e-5, "Arrow/trident flight at " + tick);
        }
        actual.discard();
    }

    private static void command(ClientGameTestContext ctx, String... commands) {
        ctx.runOnClient(mc -> { for (String command : commands) mc.player.connection.sendCommand(command); });
    }

    private static void require(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }
}
