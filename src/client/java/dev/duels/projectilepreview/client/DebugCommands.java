package dev.duels.projectilepreview.client;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;
import dev.duels.projectilepreview.client.projectile.StartPosTuning;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.*;

public final class DebugCommands {
    private DebugCommands() {}

    // auf true stellen, wenn du ingame tunen willst
    public static final boolean ENABLED = false;

    public static void init() {
        if (!ENABLED) return;

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, reg) -> {
            dispatcher.register(literal("startpos")
                    .then(literal("set")
                            .then(argument("profile", string())
                                    .then(argument("forward", DoubleArgumentType.doubleArg(-2, 2))
                                            .then(argument("side", DoubleArgumentType.doubleArg(-2, 2))
                                                    .then(argument("up", DoubleArgumentType.doubleArg(-2, 2))
                                                            .executes(ctx -> {
                                                                StartPosTuning.set(
                                                                        getString(ctx, "profile"),
                                                                        DoubleArgumentType.getDouble(ctx, "forward"),
                                                                        DoubleArgumentType.getDouble(ctx, "side"),
                                                                        DoubleArgumentType.getDouble(ctx, "up")
                                                                );
                                                                ctx.getSource().sendFeedback(Text.literal("startPos updated ✓"));
                                                                return 1;
                                                            })))))));
        });
    }
}
