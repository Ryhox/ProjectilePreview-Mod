package dev.duels.projectilepreview.client;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.network.chat.Component;
import dev.duels.projectilepreview.client.projectile.StartPosTuning;

public final class DebugCommands {
    private DebugCommands() {}

    public static final boolean ENABLED = false;

    public static void init() {
        if (!ENABLED) return;

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, reg) -> {
            dispatcher.register(ClientCommands.literal("startpos")
                    .then(ClientCommands.literal("set")
                            .then(ClientCommands.argument("profile", StringArgumentType.string())
                                    .then(ClientCommands.argument("forward", DoubleArgumentType.doubleArg(-2, 2))
                                            .then(ClientCommands.argument("side", DoubleArgumentType.doubleArg(-2, 2))
                                                    .then(ClientCommands.argument("up", DoubleArgumentType.doubleArg(-2, 2))
                                                            .executes(ctx -> {
                                                                StartPosTuning.set(
                                                                        StringArgumentType.getString(ctx, "profile"),
                                                                        DoubleArgumentType.getDouble(ctx, "forward"),
                                                                        DoubleArgumentType.getDouble(ctx, "side"),
                                                                        DoubleArgumentType.getDouble(ctx, "up")
                                                                );
                                                                ctx.getSource().sendFeedback(Component.literal("startPos updated ✓"));
                                                                return 1;
                                                            })))))));
        });
    }
}
