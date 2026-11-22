package variableeconomy.market.network;

import variableeconomy.market.MarketSettings;
import variableeconomy.market.MarketModule;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import variableeconomy.market.catalog.CatalogEntry;
import variableeconomy.market.catalog.MarketCatalog;
import variableeconomy.core.api.AccountId;
import variableeconomy.core.api.CoreApi;
import variableeconomy.market.data.MarketStockData;
import variableeconomy.market.data.TradeHistory;
import variableeconomy.market.menu.MarketMenu;
import variableeconomy.market.service.MarketService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Server side: keeps every player's copy of the catalog, and every open market screen, up to date. Each player is
 * sent only the items they have unlocked (core's item gate); when nothing can be locked, everyone gets the same.
 * Server thread only.
 */
public final class MarketSync {
    /** How often to re-check viewers' balance and held items for changes made outside the market (pickups, the bank). */
    private static final int PLAYER_STATE_INTERVAL_TICKS = 20;

    private static final Map<UUID, MarketPayloads.PlayerState> lastSentState = new HashMap<>();
    /** Players whose locks changed, so what they can see may have; their catalog is resent on the next tick. */
    private static final Set<UUID> staleCatalogs = new HashSet<>();
    private static boolean allCatalogsStale;

    private MarketSync() {
    }

    public static List<ServerPlayer> viewers(MinecraftServer server) {
        return server.getPlayerList().getPlayers().stream()
                .filter(p -> p.containerMenu instanceof MarketMenu)
                .toList();
    }

    /** Whether the player has a market screen open that may still trade: a dead player's screen may not. */
    private static boolean viewing(ServerPlayer player) {
        return player.containerMenu instanceof MarketMenu menu && menu.stillValid(player) && MarketService.canTrade(player);
    }

    /** Call right after the market screen opens. */
    public static void sendFullState(ServerPlayer player) {
        // A newly opened screen starts empty, so resend even if nothing changed since the last send.
        lastSentState.remove(player.getUUID());
        sendCatalog(player);
        sendPlayerState(player);
    }

    /** After a catalog reload: prices or listings may have changed for everyone. */
    public static void broadcastSnapshot(MinecraftServer server) {
        // Every player keeps the catalog, so item tooltips can show what the market pays.
        if (CoreApi.itemGate().canLockAnything()) {
            server.getPlayerList().getPlayers().forEach(MarketSync::sendCatalog);
        } else {
            PacketDistributor.sendToAllPlayers(snapshot(server, null));
        }
        viewers(server).forEach(MarketSync::sendPlayerState);
    }

    /** The catalog for one player, e.g. on joining; their tooltips need it even with the market closed. */
    public static void sendCatalog(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, catalogFor(player));
    }

    /**
     * After a player's locks changed, or null for any player's. Their catalog is resent on the next tick, so a burst
     * of changes (logging in, joining a team) sends it once.
     */
    public static void stagesChanged(@Nullable ServerPlayer player) {
        if (player == null) {
            allCatalogsStale = true;
        } else {
            staleCatalogs.add(player.getUUID());
        }
    }

    /** After an item's stock or price changed: every player who can see it gets the new row in the same tick. */
    public static void broadcastRow(MinecraftServer server, Item item) {
        row(server, item).ifPresent(row -> {
            MarketModule.LOGGER.debug("Market row update for {} (stock {}, factor {})", row.item(), row.stock(), row.factor());
            MarketPayloads.RowUpdate update = new MarketPayloads.RowUpdate(List.of(row));
            if (!CoreApi.itemGate().canLockAnything()) {
                PacketDistributor.sendToAllPlayers(update);
                return;
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!CoreApi.itemGate().isLocked(player, item)) {
                    PacketDistributor.sendToPlayer(player, update);
                }
            }
        });
    }

    /** At a recovery step only items whose price is away from base can have moved, so send just those. */
    private static void broadcastRecoveredRows(MinecraftServer server) {
        Map<Item, MarketPayloads.Row> rows = new HashMap<>();
        for (ResourceLocation id : MarketStockData.get(server).factorsView().keySet()) {
            BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> row(server, item).ifPresent(row -> rows.put(item, row)));
        }
        if (rows.isEmpty()) {
            return;
        }
        if (!CoreApi.itemGate().canLockAnything()) {
            PacketDistributor.sendToAllPlayers(new MarketPayloads.RowUpdate(List.copyOf(rows.values())));
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            List<MarketPayloads.Row> visible = rows.entrySet().stream()
                    .filter(e -> !CoreApi.itemGate().isLocked(player, e.getKey()))
                    .map(Map.Entry::getValue)
                    .toList();
            if (!visible.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new MarketPayloads.RowUpdate(visible));
            }
        }
    }

    /** Resends the catalog to players whose locks changed since the last tick. */
    private static void resendStaleCatalogs(MinecraftServer server) {
        if (!allCatalogsStale && staleCatalogs.isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (allCatalogsStale || staleCatalogs.contains(player.getUUID())) {
                sendCatalog(player);
            }
        }
        allCatalogsStale = false;
        staleCatalogs.clear();
    }

    /** Sends a viewer their balance and held counts, but only if they changed since the last send. */
    public static void sendPlayerState(ServerPlayer player) {
        MarketPayloads.PlayerState state = playerState(player);
        if (!state.equals(lastSentState.get(player.getUUID()))) {
            lastSentState.put(player.getUUID(), state);
            PacketDistributor.sendToPlayer(player, state);
        }
    }

    /** After an account's balance changed: viewers who use that account see it at once. */
    public static void balanceChanged(MinecraftServer server, AccountId account) {
        for (ServerPlayer viewer : viewers(server)) {
            if (CoreApi.accounts().active(viewer).equals(account)) {
                sendPlayerState(viewer);
            }
        }
    }

    public static void tick(MinecraftServer server) {
        resendStaleCatalogs(server);
        // Recovery steps happen server-wide at interval boundaries; refresh open screens when any price moved.
        long now = MarketStockData.now(server);
        if (now % MarketSettings.intervalTicks() == 0) {
            broadcastRecoveredRows(server);
        }
        if (server.getTickCount() % PLAYER_STATE_INTERVAL_TICKS != 0) {
            return;
        }
        List<ServerPlayer> viewers = viewers(server);
        Set<UUID> open = new HashSet<>();
        viewers.forEach(v -> open.add(v.getUUID()));
        lastSentState.keySet().retainAll(open);
        viewers.forEach(MarketSync::sendPlayerState);
    }

    // ---- Requests --------------------------------------------------------------------------------------------

    public static void handleTradeRequest(MarketPayloads.TradeRequest request, ServerPlayer player) {
        // Only trade from an open market screen; anything else is a stale or forged packet.
        if (!viewing(player)) {
            return;
        }
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(request.item());
        MarketService.TradeResult result;
        if (item.isEmpty()) {
            result = new MarketService.TradeResult(MarketService.Result.NOT_LISTED, 0, 0, 0);
        } else if (request.buy()) {
            result = MarketService.tryBuy(player, item.get(), request.quantity(), MarketService.Expected.orBetter(request.quotedTotal()));
        } else {
            result = MarketService.trySell(player, item.get(), request.quantity(), MarketService.Expected.orBetter(request.quotedTotal()));
        }

        PacketDistributor.sendToPlayer(player, new MarketPayloads.TradeOutcome(
                request.buy(), request.item(), result.result(), result.quantity(), result.total()));
        if (item.isPresent()) {
            if (result.ok()) {
                broadcastRow(player.server, item.get());
                sendTrades(player);
            } else if (!CoreApi.itemGate().isLocked(player, item.get())) {
                // A rejected trade usually means the client's view is stale (price, stock), so refresh just this row,
                // unless the player can't see it.
                row(player.server, item.get()).ifPresent(row ->
                        PacketDistributor.sendToPlayer(player, new MarketPayloads.RowUpdate(List.of(row))));
            }
        }
        sendPlayerState(player);
    }

    /** Sells the Selling box for a player whose market screen is open (see {@link #sellBox}), then answers them. */
    public static void handleSellBox(MarketPayloads.SellBoxRequest request, ServerPlayer player) {
        if (!viewing(player)) {
            return;
        }
        List<MarketPayloads.SellOutcomeLine> outcomes = sellBox(player, request);
        if (outcomes.isEmpty()) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new MarketPayloads.SellBoxOutcome(outcomes));
        boolean anySold = false;
        for (MarketPayloads.SellOutcomeLine line : outcomes) {
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(line.item());
            if (item.isEmpty()) {
                continue;
            }
            if (line.result() == MarketService.Result.OK) {
                anySold = true;
                broadcastRow(player.server, item.get());
            } else if (!CoreApi.itemGate().isLocked(player, item.get())) {
                // As for a single trade: the client's view of a refused item is probably stale, so refresh its row.
                row(player.server, item.get()).ifPresent(row ->
                        PacketDistributor.sendToPlayer(player, new MarketPayloads.RowUpdate(List.of(row))));
            }
        }
        if (anySold) {
            sendTrades(player);
        }
        sendPlayerState(player);
    }

    /**
     * Sells the lines of a Selling box request, each as its own sale at the current price, as long as that pays at
     * least what the player was shown. A line that fails (price dropped, items gone) changes nothing, and the others
     * still go ahead. Returns one outcome per line, in order; empty if the request is refused whole: no lines, too
     * many, or one item twice, none of which the screen sends.
     */
    public static List<MarketPayloads.SellOutcomeLine> sellBox(ServerPlayer player, MarketPayloads.SellBoxRequest request) {
        if (request.lines().isEmpty() || request.lines().size() > MarketPayloads.MAX_SELL_BOX_LINES) {
            return List.of();
        }
        Set<ResourceLocation> seen = new HashSet<>();
        for (MarketPayloads.SellLine line : request.lines()) {
            if (!seen.add(line.item())) {
                return List.of();
            }
        }
        List<MarketPayloads.SellOutcomeLine> outcomes = new ArrayList<>();
        for (MarketPayloads.SellLine line : request.lines()) {
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(line.item());
            MarketService.TradeResult result = item.isEmpty()
                    ? new MarketService.TradeResult(MarketService.Result.NOT_LISTED, 0, 0, 0)
                    : MarketService.trySell(player, item.get(), line.quantity(), MarketService.Expected.orBetter(line.quotedTotal()));
            outcomes.add(new MarketPayloads.SellOutcomeLine(line.item(), result.result(), result.quantity(), result.total()));
        }
        return outcomes;
    }

    static void handleTradesRequest(ServerPlayer player) {
        if (viewing(player)) {
            sendTrades(player);
        }
    }

    /** The player's own recent trades; nobody else's are ever sent. */
    public static void sendTrades(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new MarketPayloads.Trades(System.currentTimeMillis(),
                TradeHistory.get(player.server).recent(player.getUUID())));
    }

    static void handleHistoryRequest(MarketPayloads.HistoryRequest request, ServerPlayer player) {
        if (!viewing(player)) {
            return;
        }
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(request.item());
        if (item.isEmpty() || CoreApi.itemGate().isLocked(player, item.get())) {
            return;
        }
        MarketStockData.History history = MarketStockData.get(player.server).history(request.item());
        PacketDistributor.sendToPlayer(player, new MarketPayloads.History(request.item(), MarketStockData.now(player.server),
                history.ticks(), history.factors()));
    }

    // ---- What is sent ----------------------------------------------------------------------------------------

    /** The catalog as {@code player} may see it. */
    public static MarketPayloads.Snapshot catalogFor(ServerPlayer player) {
        return snapshot(player.server, CoreApi.itemGate().canLockAnything() ? player : null);
    }

    /** Every listed item, or only those {@code viewer} has unlocked. */
    private static MarketPayloads.Snapshot snapshot(MinecraftServer server, @Nullable ServerPlayer viewer) {
        List<MarketPayloads.Row> rows = new ArrayList<>();
        for (CatalogEntry entry : MarketCatalog.INSTANCE.entries()) {
            if (viewer == null || !CoreApi.itemGate().isLocked(viewer, entry.item())) {
                row(server, entry.item()).ifPresent(rows::add);
            }
        }
        return new MarketPayloads.Snapshot(rows, MarketSettings.maxQuantityPerRequest());
    }

    private static Optional<MarketPayloads.Row> row(MinecraftServer server, Item item) {
        return MarketService.quote(server, item).map(q -> new MarketPayloads.Row(
                q.entry().itemId(), q.entry().canSell(), q.entry().canBuy(), q.stock(), q.inputs(), q.factor()));
    }

    /**
     * The player's balance and main inventory as the market sees it: plain copies of each item, listed or not, empty
     * slots, and the room left in stacks of each of those. One pass over the inventory, so the cost doesn't grow with
     * the size of the catalog.
     */
    public static MarketPayloads.PlayerState playerState(ServerPlayer player) {
        long balance = CoreApi.money().balance(player.server, CoreApi.accounts().active(player)).orElse(0L);
        Map<ResourceLocation, Integer> held = new HashMap<>();
        Map<ResourceLocation, Integer> stackRoom = new HashMap<>();
        int emptySlots = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty()) {
                emptySlots++;
                continue;
            }
            Item item = stack.getItem();
            if (!CoreApi.isCoin(item) && CoreApi.isPlain(stack, item)) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                held.merge(id, stack.getCount(), Integer::sum);
                stackRoom.merge(id, Math.max(0, stack.getMaxStackSize() - stack.getCount()), Integer::sum);
            }
        }
        return new MarketPayloads.PlayerState(balance, held, emptySlots, stackRoom);
    }
}
