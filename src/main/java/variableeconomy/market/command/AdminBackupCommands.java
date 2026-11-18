package variableeconomy.market.command;

import variableeconomy.market.MarketModule;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import variableeconomy.core.api.CoreApi;
import variableeconomy.market.data.MarketBackup;
import variableeconomy.market.data.MarketOverrides;
import variableeconomy.market.data.MarketStockData;
import variableeconomy.market.network.MarketSync;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /market admin backup}, {@code backups} and {@code restore}: backups of the market's stock, prices and admin
 * catalog edits, and putting one back.
 */
public final class AdminBackupCommands {
    private static final int BACKUPS_SHOWN = 10;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private AdminBackupCommands() {
    }

    public static void addTo(LiteralArgumentBuilder<CommandSourceStack> admin) {
        admin.then(Commands.literal("backup").executes(AdminBackupCommands::backup));
        admin.then(Commands.literal("backups").executes(AdminBackupCommands::listBackups));
        var name = Commands.argument("name", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    try {
                        return SharedSuggestionProvider.suggest(MarketBackup.list(ctx.getSource().getServer()), builder);
                    } catch (IOException e) {
                        return builder.buildFuture();
                    }
                });
        for (MarketBackup.Part part : MarketBackup.Part.values()) {
            name.then(Commands.literal(part.name().toLowerCase(Locale.ROOT))
                    .executes(ctx -> restore(ctx, part, false))
                    .then(Commands.literal("confirm").executes(ctx -> restore(ctx, part, true))));
        }
        admin.then(Commands.literal("restore").then(name));
    }

    private static int backup(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        try {
            String name = MarketBackup.create(source.getServer(), null);
            CoreApi.audit().record(source, "backup", name, "ok", null, null);
            source.sendSuccess(() -> Component.literal(("Backed up the market's stock, prices and admin edits as \"%s\". "
                    + "Restore with /market admin restore %s <stock|prices|edits|all>").formatted(name, name)), true);
            return 1;
        } catch (IOException e) {
            MarketModule.LOGGER.error("Market backup failed", e);
            source.sendFailure(Component.literal("Backup failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int listBackups(CommandContext<CommandSourceStack> ctx) {
        try {
            List<String> names = MarketBackup.list(ctx.getSource().getServer());
            if (names.isEmpty()) {
                ctx.getSource().sendSuccess(() -> Component.literal("No backups yet. Make one with /market admin backup"), false);
                return 0;
            }
            ctx.getSource().sendSuccess(() -> Component.literal("Backups, newest first (\"auto\" ones were taken before a restore or prune):"), false);
            names.stream().limit(BACKUPS_SHOWN).forEach(name -> ctx.getSource().sendSuccess(() -> Component.literal("  " + name), false));
            if (names.size() > BACKUPS_SHOWN) {
                ctx.getSource().sendSuccess(() -> Component.literal("  ...and %d older; all are in <world>/%s/backups/"
                        .formatted(names.size() - BACKUPS_SHOWN, MarketModule.DATA_NAMESPACE)), false);
            }
            return names.size();
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("Couldn't list backups: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Without {@code confirm}: checks the backup and says what restoring would change. With it: backs up the current
     * state automatically, then replaces the chosen part.
     */
    private static int restore(CommandContext<CommandSourceStack> ctx, MarketBackup.Part part, boolean confirm) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        String name = StringArgumentType.getString(ctx, "name");
        String what = part.name().toLowerCase(Locale.ROOT);
        Path file;
        try {
            file = MarketBackup.file(server, name);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
        MarketBackup.ParseResult read = MarketBackup.read(file);
        if (read.contents().isEmpty()) {
            source.sendFailure(Component.literal("Backup \"%s\" can't be restored; nothing was changed:".formatted(name)));
            read.problems().stream().limit(5).forEach(problem -> source.sendFailure(Component.literal("  " + problem)));
            if (confirm) {
                CoreApi.audit().record(source, "restore " + what, name, "refused: " + read.problems().get(0), null, null);
            }
            return 0;
        }
        MarketBackup.Contents backup = read.contents().get();
        MarketStockData market = MarketStockData.get(server);
        MarketOverrides edits = MarketOverrides.get(server);
        MarketBackup.Difference difference = MarketBackup.difference(backup, part, market, edits.snapshot());

        List<String> lines = new ArrayList<>();
        long ticksAgo = MarketStockData.now(server) - backup.gameTime();
        lines.add("Backup \"%s\", taken %s (about %d minutes of game time ago)".formatted(name, when(backup.created()),
                Math.max(0, ticksAgo) / 1200));
        long newer = since(backup.created()).map(time -> CoreApi.audit().countSince(server, time)).orElse(0L);
        if (newer > 0) {
            lines.add("  %d admin action(s) have been logged since then; the audit log lists them".formatted(newer));
        }
        lines.add("  Restoring %s would change: %d stock level(s), %d price(s), %d item edit(s), %d tag rule(s)"
                .formatted(what, difference.stock(), difference.prices(), difference.itemEdits(), difference.tagRules()));
        if (!difference.unknownItems().isEmpty()) {
            lines.add("  %d item(s) in it no longer exist in the game; their data is restored but unused"
                    .formatted(difference.unknownItems().size()));
        }
        lines.forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
        if (!confirm) {
            source.sendSuccess(() -> Component.literal("Nothing has been changed. Add \"confirm\" to restore; the current "
                    + "state is backed up first"), false);
            return 1;
        }

        String safety;
        try {
            safety = MarketBackup.create(server, "restore");
        } catch (IOException e) {
            source.sendFailure(Component.literal("Couldn't back up the current state first, so nothing was restored: " + e.getMessage()));
            return 0;
        }
        MarketBackup.apply(backup, part, market, edits, MarketStockData.now(server));
        if (part == MarketBackup.Part.EDITS || part == MarketBackup.Part.ALL) {
            MarketModule.catalogReloaded(server);
        } else {
            MarketSync.broadcastSnapshot(server);
        }
        JsonObject counts = new JsonObject();
        counts.addProperty("stock", difference.stock());
        counts.addProperty("prices", difference.prices());
        counts.addProperty("item_edits", difference.itemEdits());
        counts.addProperty("tag_rules", difference.tagRules());
        CoreApi.audit().record(source, "restore " + what, name, "ok", new JsonPrimitive("saved first as " + safety), counts);
        source.sendSuccess(() -> Component.literal("Restored %s from \"%s\". The state before is saved as \"%s\" if you need to go back"
                .formatted(what, name, safety)), true);
        return 1;
    }

    /** The instant a backup was taken, if it can be read. */
    private static java.util.Optional<Instant> since(String instant) {
        try {
            return java.util.Optional.of(Instant.parse(instant));
        } catch (RuntimeException e) {
            return java.util.Optional.empty();
        }
    }

    /** An ISO instant as local date and time, or as it is if it can't be read. */
    private static String when(String instant) {
        try {
            return WHEN.format(Instant.parse(instant));
        } catch (RuntimeException e) {
            return instant;
        }
    }
}
