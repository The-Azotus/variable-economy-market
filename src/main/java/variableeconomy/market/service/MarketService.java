package variableeconomy.market.service;

import variableeconomy.market.MarketSettings;
import variableeconomy.market.MarketModule;
import variableeconomy.market.catalog.CatalogEntry;
import variableeconomy.market.catalog.MarketCatalog;
import variableeconomy.core.api.AccountId;
import variableeconomy.core.api.AccountRole;
import variableeconomy.core.api.BankResult;
import variableeconomy.core.api.CoreApi;
import variableeconomy.core.api.PaymentResult;
import variableeconomy.core.api.PlayerItems;
import variableeconomy.core.api.Reason;
import variableeconomy.market.api.event.MarketTradeEvent;
import variableeconomy.market.data.MarketStockData;
import variableeconomy.market.data.TradeHistory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Player trades with the market. Outside this class, stock is written only by admin {@code setstock}, {@code clearstock}
 * and {@code prune}, start-stock seeding ({@code MarketStockData.seed}) and backup restore. Money moves only through
 * core's {@link variableeconomy.core.api.Money}: a sale pays the account, a purchase charges it.
 * <p>
 * Every check runs before anything changes, and once the first change is made no remaining step can fail. That holds
 * because everything runs on the server thread, so nothing else can touch the inventory, stock or bank in between.
 * Only the player's main inventory (hotbar plus the 27 storage slots) is read or written; armor, offhand and modded
 * slots are ignored.
 * <p>
 * Every trade that succeeds posts a {@link MarketTradeEvent} once it is complete.
 */
public final class MarketService {
    private MarketService() {
    }

    /** What a sale's money is, in core's money service. */
    public static final ResourceLocation SALE = ResourceLocation.fromNamespaceAndPath(MarketModule.MOD_ID, "market_sale");
    /** What a purchase's money is, in core's money service. */
    public static final ResourceLocation PURCHASE = ResourceLocation.fromNamespaceAndPath(MarketModule.MOD_ID, "market_purchase");

    public enum Result {
        OK, NOT_LISTED, SELL_DISABLED, BUY_DISABLED, NOT_ENOUGH_ITEMS, NO_STOCK, NO_FUNDS, NO_SPACE, PRICE_CHANGED,
        TOO_LARGE,
        /** The player may not use the account named for the trade that way, or can't trade right now (dead). */
        NOT_ALLOWED,
        /** The account named for the trade doesn't exist. */
        NO_ACCOUNT,
        /** Quantity was zero or negative. */
        INVALID_QUANTITY,
        /** The world's bank can't be reached. The mod's own bank is always reachable, so this isn't returned today. */
        BANK_UNAVAILABLE
    }

    /**
     * {@code unitPrice} (what the first unit traded at) and {@code total} are in bits, and are zero unless the trade
     * got as far as pricing.
     */
    public record TradeResult(Result result, int quantity, long unitPrice, long total) {
        static TradeResult fail(Result result) {
            return new TradeResult(result, 0, 0, 0);
        }

        public boolean ok() {
            return result == Result.OK;
        }
    }

    /**
     * The total a player was shown for a trade, and how the server's own total must compare with it.
     *
     * @param total    the total shown, in bits
     * @param orBetter false: the totals must be equal. True: the trade also goes through when it is better for the
     *                 player, paying more for a sale or costing less for a purchase.
     */
    public record Expected(long total, boolean orBetter) {
        /** Exactly {@code total}, or null (any total) when empty. */
        @Nullable
        static Expected of(OptionalLong total) {
            return total.isPresent() ? new Expected(total.getAsLong(), false) : null;
        }

        /** {@code total} or anything better for the player: what the market screen sends. */
        public static Expected orBetter(long total) {
            return new Expected(total, true);
        }

        /** Whether the server's {@code actual} total may go ahead; {@code buying} for a purchase. */
        boolean accepts(long actual, boolean buying) {
            if (!orBetter) {
                return actual == total;
            }
            return buying ? actual <= total : actual >= total;
        }
    }

    /**
     * Whether the player can trade at all right now. A dead player can't: the screen may still count as open until
     * they respawn, and anything given to the dead player's inventory would be lost.
     */
    public static boolean canTrade(ServerPlayer player) {
        return player.isAlive() && !player.isRemoved();
    }

    /**
     * An item's current market terms.
     *
     * @param factor    the supply/demand factor right now, recovery applied
     * @param sellPrice what the market pays for the next unit
     * @param buyPrice  what the market charges for the next unit
     * @param stock     what the market can sell; empty means unlimited
     */
    public record Quote(CatalogEntry entry, PriceModel.Inputs inputs, double factor, long sellPrice, long buyPrice,
                        OptionalLong stock) {
    }

    public static Optional<Quote> quote(MinecraftServer server, Item item) {
        MarketStockData market = MarketStockData.get(server);
        long now = MarketStockData.now(server);
        return MarketCatalog.INSTANCE.get(item).map(entry -> {
            PriceModel.Inputs inputs = PriceModel.inputs(entry);
            double factor = currentFactor(entry, inputs, market, now);
            return new Quote(entry, inputs, factor, PriceModel.unitSellPrice(inputs, factor),
                    PriceModel.unitBuyPrice(inputs, factor), availableStock(entry, market));
        });
    }

    /** The factor an item trades at right now: stored value, recovered to {@code now}, with the model's limits. */
    public static double currentFactor(CatalogEntry entry, PriceModel.Inputs inputs, MarketStockData market, long now) {
        double stored = market.factor(entry.itemId(), now, inputs.recovery(), MarketSettings.intervalTicks());
        return PriceModel.effectiveFactor(inputs, stored);
    }

    /**
     * What the market can sell of an item: empty for unlimited items, otherwise the stored stock. Unlimited is kept
     * out of band rather than as a sentinel number so it can never leak into stock arithmetic.
     */
    public static OptionalLong availableStock(CatalogEntry entry, MarketStockData market) {
        return entry.unlimitedStock() ? OptionalLong.empty() : OptionalLong.of(market.getStock(entry.itemId()));
    }

    /**
     * The player sells {@code qty} of {@code item} to the market. For an unlimited item the player is paid normally,
     * the items leave the game and stored stock is not touched.
     *
     * @param expectedTotal the total the player was shown; empty to accept the current price
     */
    public static TradeResult trySell(ServerPlayer player, Item item, int qty, OptionalLong expectedTotal) {
        requireServerThread(player);
        return trySell(player, item, qty, Expected.of(expectedTotal), CoreApi.accounts().active(player));
    }

    /** The same, paid into {@code account}, which the player must be able to deposit into. */
    public static TradeResult trySell(ServerPlayer player, Item item, int qty, OptionalLong expectedTotal, AccountId account) {
        return trySell(player, item, qty, Expected.of(expectedTotal), account);
    }

    /** The same, with the total the player was shown compared as {@code expected} says; null accepts any total. */
    public static TradeResult trySell(ServerPlayer player, Item item, int qty, @Nullable Expected expected) {
        requireServerThread(player);
        return trySell(player, item, qty, expected, CoreApi.accounts().active(player));
    }

    /** The same, paid into {@code account}. */
    public static TradeResult trySell(ServerPlayer player, Item item, int qty, @Nullable Expected expected, AccountId account) {
        requireServerThread(player);
        Objects.requireNonNull(account, "account");
        if (!canTrade(player)) {
            return TradeResult.fail(Result.NOT_ALLOWED);
        }
        Optional<CatalogEntry> listing = MarketCatalog.INSTANCE.get(item);
        // A locked item answers exactly as an unlisted one, so the reply doesn't reveal it.
        if (listing.isEmpty() || CoreApi.itemGate().isLocked(player, item)) {
            return TradeResult.fail(Result.NOT_LISTED);
        }
        CatalogEntry entry = listing.get();
        if (!entry.canSell()) {
            return TradeResult.fail(Result.SELL_DISABLED);
        }
        Result quantityCheck = checkQuantity(qty);
        if (quantityCheck != Result.OK) {
            return TradeResult.fail(quantityCheck);
        }

        MarketStockData market = MarketStockData.get(player.server);
        long now = MarketStockData.now(player.server);
        PriceModel.Inputs inputs = PriceModel.inputs(entry);
        double factor = currentFactor(entry, inputs, market, now);
        long unitPrice = PriceModel.unitSellPrice(inputs, factor);
        PriceModel.Order order = PriceModel.sell(inputs, factor, qty);
        long stock = market.getStock(entry.itemId());
        if (!fitsBank(order) || (!entry.unlimitedStock() && stock > Long.MAX_VALUE - qty)) {
            return new TradeResult(Result.TOO_LARGE, 0, unitPrice, 0);
        }
        if (expected != null && !expected.accepts(order.total(), false)) {
            return new TradeResult(Result.PRICE_CHANGED, 0, unitPrice, order.total());
        }

        if (PlayerItems.count(player, entry::matches) < qty) {
            return new TradeResult(Result.NOT_ENOUGH_ITEMS, 0, unitPrice, order.total());
        }
        Result accountCheck = checkAccount(player, account, false, order.total());
        if (accountCheck != Result.OK) {
            return new TradeResult(accountCheck, 0, unitPrice, order.total());
        }

        // Nothing has changed yet. Everything below always succeeds: the account takes the payment (checked above).
        PlayerItems.remove(player, entry::matches, qty);
        if (!entry.unlimitedStock()) {
            market.setStock(entry.itemId(), stock + qty);
        }
        updateFactor(entry, market, factor, order, now);
        market.recordTrade(entry.itemId(), false, qty, order.total());
        TradeHistory.get(player.server).record(player.getUUID(),
                new TradeHistory.Trade(System.currentTimeMillis(), entry.itemId(), false, qty, order.total()));
        // A sale can be worth nothing (a cheap item with a fee): then nothing is paid.
        PaymentResult paid = order.total() == 0 ? new PaymentResult(BankResult.OK, 0, false)
                : CoreApi.money().pay(player.server, account, order.total(), Reason.by(SALE, player.getUUID()));
        if (!paid.ok()) {
            MarketModule.LOGGER.error("A market sale's payment of {} to {} failed after its checks passed: {}",
                    order.total(), account, paid.result());
        }
        MarketEvents.post(new MarketTradeEvent(player, account, item, false, qty, unitPrice, order.total()));
        return new TradeResult(Result.OK, qty, unitPrice, order.total());
    }

    /**
     * The player buys {@code qty} of {@code item} from the market. An unlimited item never runs out of stock, and
     * its stored stock is not touched.
     *
     * @param expectedTotal the total the player was shown; empty to accept the current price
     */
    public static TradeResult tryBuy(ServerPlayer player, Item item, int qty, OptionalLong expectedTotal) {
        requireServerThread(player);
        return tryBuy(player, item, qty, Expected.of(expectedTotal), CoreApi.accounts().active(player));
    }

    /** The same, paid from {@code account}, which the player must be able to spend from. */
    public static TradeResult tryBuy(ServerPlayer player, Item item, int qty, OptionalLong expectedTotal, AccountId account) {
        return tryBuy(player, item, qty, Expected.of(expectedTotal), account);
    }

    /** The same, with the total the player was shown compared as {@code expected} says; null accepts any total. */
    public static TradeResult tryBuy(ServerPlayer player, Item item, int qty, @Nullable Expected expected) {
        requireServerThread(player);
        return tryBuy(player, item, qty, expected, CoreApi.accounts().active(player));
    }

    /** The same, paid from {@code account}. */
    public static TradeResult tryBuy(ServerPlayer player, Item item, int qty, @Nullable Expected expected, AccountId account) {
        requireServerThread(player);
        Objects.requireNonNull(account, "account");
        if (!canTrade(player)) {
            return TradeResult.fail(Result.NOT_ALLOWED);
        }
        Optional<CatalogEntry> listing = MarketCatalog.INSTANCE.get(item);
        // A locked item answers exactly as an unlisted one, so the reply doesn't reveal it.
        if (listing.isEmpty() || CoreApi.itemGate().isLocked(player, item)) {
            return TradeResult.fail(Result.NOT_LISTED);
        }
        CatalogEntry entry = listing.get();
        if (!entry.canBuy()) {
            return TradeResult.fail(Result.BUY_DISABLED);
        }
        Result quantityCheck = checkQuantity(qty);
        if (quantityCheck != Result.OK) {
            return TradeResult.fail(quantityCheck);
        }

        MarketStockData market = MarketStockData.get(player.server);
        long now = MarketStockData.now(player.server);
        PriceModel.Inputs inputs = PriceModel.inputs(entry);
        double factor = currentFactor(entry, inputs, market, now);
        long unitPrice = PriceModel.unitBuyPrice(inputs, factor);
        PriceModel.Order order = PriceModel.buy(inputs, factor, qty);
        if (!fitsBank(order)) {
            return new TradeResult(Result.TOO_LARGE, 0, unitPrice, 0);
        }
        if (expected != null && !expected.accepts(order.total(), true)) {
            return new TradeResult(Result.PRICE_CHANGED, 0, unitPrice, order.total());
        }

        OptionalLong stock = availableStock(entry, market);
        if (stock.isPresent() && stock.getAsLong() < qty) {
            return new TradeResult(Result.NO_STOCK, 0, unitPrice, order.total());
        }
        ItemStack fresh = new ItemStack(entry.item());
        if (PlayerItems.room(player, fresh) < qty) {
            return new TradeResult(Result.NO_SPACE, 0, unitPrice, order.total());
        }
        Result accountCheck = checkAccount(player, account, true, order.total());
        if (accountCheck != Result.OK) {
            return new TradeResult(accountCheck, 0, unitPrice, order.total());
        }
        // The charge is itself the funds check: it changes nothing unless it succeeds.
        PaymentResult charged = order.total() == 0 ? new PaymentResult(BankResult.OK, 0, false)
                : CoreApi.money().charge(player.server, account, order.total(), Reason.by(PURCHASE, player.getUUID()));
        if (!charged.ok()) {
            return new TradeResult(fromBank(charged.result()), 0, unitPrice, order.total());
        }

        // Money has been taken. Everything below always succeeds: stock and space were checked above.
        if (stock.isPresent()) {
            market.setStock(entry.itemId(), stock.getAsLong() - qty);
        }
        updateFactor(entry, market, factor, order, now);
        market.recordTrade(entry.itemId(), true, qty, order.total());
        TradeHistory.get(player.server).record(player.getUUID(),
                new TradeHistory.Trade(System.currentTimeMillis(), entry.itemId(), true, qty, order.total()));
        PlayerItems.insert(player, fresh, qty);
        MarketEvents.post(new MarketTradeEvent(player, account, item, true, qty, unitPrice, order.total()));
        return new TradeResult(Result.OK, qty, unitPrice, order.total());
    }

    /**
     * Whether {@code account} can take this trade's money: it exists, the player may deposit into it (a sale) or spend
     * from it ({@code spending}, a purchase), and a sale's payment wouldn't overflow it. A purchase's funds are checked
     * by the charge itself.
     */
    private static Result checkAccount(ServerPlayer player, AccountId account, boolean spending, long total) {
        Optional<AccountRole> role = CoreApi.accounts().role(player.server, account, player.getUUID());
        if (role.isEmpty()) {
            return CoreApi.accounts().get(player.server, account).isPresent() ? Result.NOT_ALLOWED : Result.NO_ACCOUNT;
        }
        if (spending ? !role.get().canSpend() : !role.get().canDeposit()) {
            return Result.NOT_ALLOWED;
        }
        OptionalLong balance = CoreApi.money().balance(player.server, account);
        if (balance.isEmpty()) {
            return Result.BANK_UNAVAILABLE;
        }
        return !spending && balance.getAsLong() > Long.MAX_VALUE - total ? Result.TOO_LARGE : Result.OK;
    }

    /** A money result that refused a purchase, as the market's own. */
    private static Result fromBank(BankResult result) {
        return switch (result) {
            case NO_FUNDS -> Result.NO_FUNDS;
            case NO_ACCOUNT -> Result.NO_ACCOUNT;
            case NOT_ALLOWED -> Result.NOT_ALLOWED;
            case TOO_LARGE -> Result.TOO_LARGE;
            default -> Result.BANK_UNAVAILABLE;
        };
    }

    /** Writes the factor an order left behind. Unchanged factors aren't rewritten, so their recovery isn't reset. */
    private static void updateFactor(CatalogEntry entry, MarketStockData market, double before, PriceModel.Order order,
                                     long now) {
        if (entry.dynamic() && order.factorAfter() != before) {
            market.setFactor(entry.itemId(), order.factorAfter(), now);
            market.recordHistory(entry.itemId(), order.factorAfter(), now, MarketSettings.intervalTicks());
        }
    }

    /**
     * Adds the current factor of every dynamic listed item to its price history. Called at each recovery step, so the
     * history shows recovery as well as trades.
     */
    public static void sampleHistory(MinecraftServer server) {
        MarketStockData market = MarketStockData.get(server);
        long now = MarketStockData.now(server);
        int interval = MarketSettings.intervalTicks();
        for (CatalogEntry entry : MarketCatalog.INSTANCE.entries()) {
            if (entry.dynamic()) {
                market.recordHistory(entry.itemId(), currentFactor(entry, PriceModel.inputs(entry), market, now), now, interval);
            }
        }
    }

    /**
     * Forgets stored factors of listed items that have fully recovered to 1.0, which is how an untouched item is
     * stored anyway. Prices don't change; recovery steps just stop resending those items. Called after each step.
     */
    public static void pruneRecovered(MinecraftServer server) {
        MarketStockData market = MarketStockData.get(server);
        long now = MarketStockData.now(server);
        int interval = MarketSettings.intervalTicks();
        for (ResourceLocation id : List.copyOf(market.factorsView().keySet())) {
            BuiltInRegistries.ITEM.getOptional(id).flatMap(MarketCatalog.INSTANCE::get).ifPresent(entry -> {
                if (market.factor(id, now, PriceModel.inputs(entry).recovery(), interval) == 1.0) {
                    market.setFactor(id, 1.0, now);
                }
            });
        }
    }

    /**
     * The least one unit could ever cost to buy from the market, unrounded; empty if the market doesn't sell it.
     * Unlimited items never charge less than {@code min_buy_markup} over base. A finite dynamic item's price never
     * falls below its floor, or below its factor now if a raised floor hasn't caught up with it yet.
     *
     * @param factor the item's factor now
     */
    public static Optional<java.math.BigDecimal> lowestUnitBuyPrice(CatalogEntry entry, PriceModel.Inputs in, double factor) {
        if (!entry.canBuy()) {
            return Optional.empty();
        }
        java.math.BigDecimal base = java.math.BigDecimal.valueOf(in.basePrice());
        if (in.unlimited()) {
            return Optional.of(base.multiply(java.math.BigDecimal.ONE.add(java.math.BigDecimal.valueOf(in.minBuyMarkup()))));
        }
        double lowest = in.dynamic() ? Math.min(in.floor(), factor) : 1.0;
        return Optional.of(base.multiply(java.math.BigDecimal.ONE.add(java.math.BigDecimal.valueOf(in.markup())))
                .multiply(java.math.BigDecimal.valueOf(lowest)));
    }

    /** Whether an order total could be counted: it didn't overflow a {@code long}. */
    private static boolean fitsBank(PriceModel.Order order) {
        return !order.overflow();
    }

    /** {@code unitPrice × qty}, or empty if it would overflow a {@code long}. */
    public static OptionalLong checkedTotal(long unitPrice, int qty) {
        try {
            return OptionalLong.of(Math.multiplyExact(unitPrice, (long) qty));
        } catch (ArithmeticException e) {
            return OptionalLong.empty();
        }
    }

    static Result checkQuantity(int qty) {
        if (qty <= 0) {
            return Result.INVALID_QUANTITY;
        }
        return qty > MarketSettings.maxQuantityPerRequest() ? Result.TOO_LARGE : Result.OK;
    }

    static void requireServerThread(ServerPlayer player) {
        if (!player.server.isSameThread()) {
            throw new IllegalStateException("Market transactions must run on the server thread");
        }
    }

    /** How many copies of a listed item the player could sell right now. */
    public static long heldCount(ServerPlayer player, CatalogEntry entry) {
        return PlayerItems.count(player, entry::matches);
    }
}
