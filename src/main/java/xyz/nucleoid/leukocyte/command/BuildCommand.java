package xyz.nucleoid.leukocyte.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import xyz.nucleoid.leukocyte.build.BuildModeManager;
import xyz.nucleoid.leukocyte.build.LeukocyteBuild;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class BuildCommand {
    private static final SuggestionProvider<ServerCommandSource> AREA_SUGGESTIONS = (context, builder) -> {
        var build = LeukocyteBuild.get(context.getSource().getServer().getOverworld());
        for (var area : build.getAreas()) {
            builder.suggest(area.name());
        }
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            literal("build")
                .then(argument("area", StringArgumentType.greedyString())
                    .suggests(AREA_SUGGESTIONS)
                    .executes(BuildCommand::enterBuild))
        );

        dispatcher.register(
            literal("exit")
                .executes(BuildCommand::exitBuild)
        );
    }

    private static int enterBuild(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("This command can only be run by a player."));
            return 0;
        }

        var build = LeukocyteBuild.get(context.getSource().getServer().getOverworld());
        String areaName = StringArgumentType.getString(context, "area");
        var area = build.getArea(areaName);

        if (area == null) {
            context.getSource().sendError(Text.literal("Build area '" + areaName + "' not found."));
            return 0;
        }

        if (!player.getInventory().isEmpty()) {
            context.getSource().sendError(Text.literal("Your inventory must be empty to enter build mode! Drop or store your items first."));
            return 0;
        }

        BuildModeManager.enterBuildMode(player, area);
        return Command.SINGLE_SUCCESS;
    }

    private static int exitBuild(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendError(Text.literal("This command can only be run by a player."));
            return 0;
        }

        BuildModeManager.exitBuildMode(player);
        return Command.SINGLE_SUCCESS;
    }
}
