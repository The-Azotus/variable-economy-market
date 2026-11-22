package variableeconomy.market.client;

import variableeconomy.market.MarketModule;
import variableeconomy.market.network.MarketPayloads;
import variableeconomy.market.service.MarketService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What the client knows about the market, filled only from server packets. Display only: every trade is re-checked
 * on the server. The catalog rows are kept for the whole session so item tooltips can show market prices.
 * Render thread only. Deliberately uses no client-only classes, so the network code can reference it on both sides.
 */
public final class ClientMarketState {
    /** The server's default order limit, used until the first snapshot arrives. */
    private static final int DEFAULT_MAX_QUANTITY = 640;

    private static final Map<ResourceLocation, MarketPayloads.Row> rows = new LinkedHashMap<>();
    private static MarketPayloads.PlayerState player = emptyPlayer();
    private static int maxQuantity = DEFAULT_MAX_QUANTITY;
    private static Component status = Component.empty();
    private static boolean statusIsError;
    private static int version;
    @Nullable
    private static MarketPayloads.History history;
    /** The last trade request sent, so a "price changed" reply can show the old total next to the new one. */
    @Nullable
    private static MarketPayloads.TradeRequest lastRequest;
    /** The Selling box's last answer, until the screen takes it. */
    @Nullable
    private static MarketPayloads.SellBoxOutcome sellBoxOutcome;
    /** This player's recent trades, once the screen has asked for them. */
    @Nullable
    private static MarketPayloads.Trades trades;

    private ClientMarketState() {
    }

    private static MarketPayloads.PlayerState emptyPlayer() {
        return new MarketPayloads.PlayerState(0, Map.of(), 0, Map.of());
    }

    public static void applySnapshot(MarketPayloads.Snapshot snapshot) {
        rows.clear();
        snapshot.rows().forEach(row -> rows.put(row.item(), row));
        maxQuantity = snapshot.maxQuantity();
        MarketModule.LOGGER.debug("Market snapshot: {} rows, order limit {}", rows.size(), maxQuantity);
        version++;
    }

    public static void applyRow(MarketPayloads.Row row) {
        MarketModule.LOGGER.debug("Market row update: {} stock={} sell={} buy={}", row.item(), row.stock(), row.sellPrice(), row.buyPrice());
        rows.put(row.item(), row);
        version++;
    }

    public static void applyPlayerState(MarketPayloads.PlayerState state) {
        MarketModule.LOGGER.debug("Market player state: balance={} held={} emptySlots={}", state.balance(), state.held(), state.emptySlots());
        player = new MarketPayloads.PlayerState(state.balance(), Map.copyOf(state.held()), state.emptySlots(),
                Map.copyOf(state.stackRoom()));
        version++;
    }

    public static void applyTrades(MarketPayloads.Trades received) {
        trades = received;
        version++;
    }

    /** The player's recent trades, newest first, or null until they've been received. */
    @Nullable
    public static MarketPayloads.Trades trades() {
        return trades;
    }

    /** Remembers a request as it is sent. */
    public static void sent(MarketPayloads.TradeRequest request) {
        lastRequest = request;
    }

    public static void applyOutcome(MarketPayloads.TradeOutcome outcome) {
        MarketModule.LOGGER.debug("Market trade outcome: {}", outcome);
        Component itemName = BuiltInRegistries.ITEM.get(outcome.item()).getDescription();
        if (outcome.result() == MarketService.Result.OK) {
            status = Component.translatable(outcome.buy() ? "screen.variable_economy_market.bought" : "screen.variable_economy_market.sold",
                    outcome.quantity(), itemName, PriceFormat.money(PriceFormat.amount(outcome.total())));
            statusIsError = false;
        } else if (outcome.result() == MarketService.Result.PRICE_CHANGED) {
            // The server also sent the refreshed row, so the button already shows the new total.
            MarketPayloads.TradeRequest request = lastRequest;
            status = request != null && request.buy() == outcome.buy() && request.item().equals(outcome.item())
                    ? Component.translatable("screen.variable_economy_market.price_changed_from_to",
                            PriceFormat.money(PriceFormat.amount(request.quotedTotal())),
                            PriceFormat.money(PriceFormat.amount(outcome.total())))
                    : Component.translatable("screen.variable_economy_market.price_changed_to",
                            PriceFormat.money(PriceFormat.amount(outcome.total())));
            statusIsError = true;
        } else {
            status = Component.translatable("screen.variable_economy_market.result." + outcome.result().name().toLowerCase(Locale.ROOT));
            statusIsError = true;
        }
        version++;
    }

    /** The answer to a Selling box sale: a summary on the status line; the screen takes the lines for the box. */
    public static void applySellBoxOutcome(MarketPayloads.SellBoxOutcome outcome) {
        MarketModule.LOGGER.debug("Market sell box outcome: {}", outcome);
        sellBoxOutcome = outcome;
        long items = 0;
        long total = 0;
        int stayed = 0;
        for (MarketPayloads.SellOutcomeLine line : outcome.lines()) {
            if (line.result() == MarketService.Result.OK) {
                items += line.quantity();
                total += line.total();
            } else {
                stayed++;
            }
        }
        String paid = PriceFormat.amount(total);
        if (stayed == 0) {
            status = Component.translatable("screen.variable_economy_market.sell_box.sold", PriceFormat.amount(items), PriceFormat.money(paid));
        } else if (items > 0) {
            status = Component.translatable("screen.variable_economy_market.sell_box.sold_some", PriceFormat.amount(items),
                    PriceFormat.money(paid), stayed);
        } else {
            status = Component.translatable("screen.variable_economy_market.sell_box.sold_none");
        }
        statusIsError = stayed > 0;
        version++;
    }

    /** The Selling box's last answer, once; null if there is none waiting. */
    @Nullable
    public static MarketPayloads.SellBoxOutcome takeSellBoxOutcome() {
        MarketPayloads.SellBoxOutcome outcome = sellBoxOutcome;
        sellBoxOutcome = null;
        return outcome;
    }

    /** A message from the screen itself on the status line. */
    public static void note(Component message, boolean error) {
        status = message;
        statusIsError = error;
        version++;
    }

    /** Only the most recently requested item's history is kept: the screen shows one at a time. */
    public static void applyHistory(MarketPayloads.History received) {
        history = received;
        version++;
    }

    /** The history for {@code item}, if that's the one last received. */
    @Nullable
    public static MarketPayloads.History history(ResourceLocation item) {
        return history != null && history.item().equals(item) ? history : null;
    }

    /** When the market screen closes. The catalog is kept: item tooltips use it with the screen closed. */
    public static void clear() {
        history = null;
        player = emptyPlayer();
        lastRequest = null;
        sellBoxOutcome = null;
        trades = null;
        status = Component.empty();
        statusIsError = false;
        version++;
    }

    /** When leaving a server: forget its catalog too. */
    public static void reset() {
        clear();
        rows.clear();
        maxQuantity = DEFAULT_MAX_QUANTITY;
    }

    public static Map<ResourceLocation, MarketPayloads.Row> rows() {
        return rows;
    }

    public static int held(ResourceLocation item) {
        return player.held().getOrDefault(item, 0);
    }

    /** Room in the main inventory for {@code item}, counted as the server counts it for a purchase. */
    public static long room(ResourceLocation item, int maxStackSize) {
        return player.room(item, maxStackSize);
    }

    /** The balance of the account the player uses. */
    public static long balance() {
        return player.balance();
    }

    /** Whether the balance looks enough for {@code total}. The server checks again. */
    public static boolean looksAffordable(long total) {
        return player.balance() >= total;
    }

    /** The server's limit on one order's quantity. */
    public static int maxQuantity() {
        return maxQuantity;
    }

    public static Component status() {
        return status;
    }

    public static boolean statusIsError() {
        return statusIsError;
    }

    /** Bumped on every change so the screen knows when to rebuild its filtered list. */
    public static int version() {
        return version;
    }
}
