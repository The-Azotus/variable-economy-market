package variableeconomy.market.service;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import variableeconomy.market.catalog.CatalogEntry;
import variableeconomy.market.catalog.MarketCatalog;
import variableeconomy.core.api.AccountId;
import variableeconomy.core.api.CoreApi;
import variableeconomy.market.api.MarketApi;
import variableeconomy.market.api.MarketPrices;
import variableeconomy.market.api.MarketQuote;
import variableeconomy.market.api.MarketResult;
import variableeconomy.market.api.MarketTrading;
import variableeconomy.market.api.TradeResult;
import variableeconomy.market.menu.MarketMenu;
import variableeconomy.market.network.MarketSync;
import variableeconomy.market.service.MarketService;
import variableeconomy.market.service.PriceModel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** The market's API: prices and trading, over {@link MarketService} and {@link PriceModel}. */
public final class MarketApiService implements MarketPrices, MarketTrading {
    public static final MarketApiService INSTANCE = new MarketApiService();

    private MarketApiService() {
    }

    /** Installs the services into {@link MarketApi}. Once, while the mod is constructed. */
    public static void install() {
        MarketApi.install(new MarketApi.Services(INSTANCE, INSTANCE));
    }

    // ---- Prices ----------------------------------------------------------------------------------------------

    @Override
    public Optional<MarketQuote> quote(MinecraftServer server, Item item) {
        requireServerThread(server);
        Objects.requireNonNull(item, "item");
        return MarketService.quote(server, item).map(quote -> new MarketQuote(item, quote.entry().basePrice(), quote.sellPrice(),
                quote.buyPrice(), quote.stock(), quote.entry().canSell(), quote.entry().canBuy(), quote.factor(), quote.entry().itemClass()));
    }

    @Override
    public Optional<MarketQuote> quote(ServerPlayer player, Item item) {
        requireServerThread(player.server);
        Objects.requireNonNull(item, "item");
        return CoreApi.itemGate().isLocked(player, item) ? Optional.empty() : quote(player.server, item);
    }

    @Override
    public List<Item> listedItems(MinecraftServer server) {
        requireServerThread(server);
        return MarketCatalog.INSTANCE.entries().stream().map(CatalogEntry::item).toList();
    }

    @Override
    public OptionalLong sellTotal(MinecraftServer server, Item item, int quantity) {
        requireServerThread(server);
        checkQuantity(quantity);
        Optional<MarketService.Quote> quote = MarketService.quote(server, Objects.requireNonNull(item, "item"));
        if (quote.isEmpty() || !quote.get().entry().canSell()) {
            return OptionalLong.empty();
        }
        return total(PriceModel.sell(quote.get().inputs(), quote.get().factor(), quantity));
    }

    @Override
    public OptionalLong buyTotal(MinecraftServer server, Item item, int quantity) {
        requireServerThread(server);
        checkQuantity(quantity);
        Optional<MarketService.Quote> quote = MarketService.quote(server, Objects.requireNonNull(item, "item"));
        if (quote.isEmpty() || !quote.get().entry().canBuy()) {
            return OptionalLong.empty();
        }
        return total(PriceModel.buy(quote.get().inputs(), quote.get().factor(), quantity));
    }

    @Override
    public OptionalLong normalSellTotal(MinecraftServer server, Item item, int quantity) {
        requireServerThread(server);
        checkQuantity(quantity);
        Optional<CatalogEntry> entry = MarketCatalog.INSTANCE.get(Objects.requireNonNull(item, "item"));
        if (entry.isEmpty() || !entry.get().canSell()) {
            return OptionalLong.empty();
        }
        return total(PriceModel.sell(PriceModel.inputs(entry.get()), 1.0, quantity));
    }

    @Override
    public OptionalLong lowestBuyCost(MinecraftServer server, Item item, int quantity) {
        requireServerThread(server);
        checkQuantity(quantity);
        Optional<MarketService.Quote> quote = MarketService.quote(server, Objects.requireNonNull(item, "item"));
        if (quote.isEmpty()) {
            return OptionalLong.empty();
        }
        Optional<BigDecimal> unit = MarketService.lowestUnitBuyPrice(quote.get().entry(), quote.get().inputs(), quote.get().factor());
        if (unit.isEmpty()) {
            return OptionalLong.empty();
        }
        BigDecimal cost = unit.get().multiply(BigDecimal.valueOf(quantity)).setScale(0, RoundingMode.CEILING);
        return cost.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0 ? OptionalLong.empty() : OptionalLong.of(cost.longValueExact());
    }

    private static OptionalLong total(PriceModel.Order order) {
        return order.overflow() ? OptionalLong.empty() : OptionalLong.of(order.total());
    }

    private static void checkQuantity(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity below 1: " + quantity);
        }
    }

    // ---- Trading ---------------------------------------------------------------------------------------------

    @Override
    public TradeResult sell(ServerPlayer player, Item item, int quantity) {
        requireServerThread(player.server);
        return sell(player, item, quantity, CoreApi.accounts().active(player));
    }

    @Override
    public TradeResult sell(ServerPlayer player, Item item, int quantity, AccountId account) {
        requireServerThread(player.server);
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(account, "account");
        return traded(player, item, account, MarketService.trySell(player, item, quantity, OptionalLong.empty(), account));
    }

    @Override
    public TradeResult buy(ServerPlayer player, Item item, int quantity) {
        requireServerThread(player.server);
        return buy(player, item, quantity, CoreApi.accounts().active(player));
    }

    @Override
    public TradeResult buy(ServerPlayer player, Item item, int quantity, AccountId account) {
        requireServerThread(player.server);
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(account, "account");
        return traded(player, item, account, MarketService.tryBuy(player, item, quantity, OptionalLong.empty(), account));
    }

    /** After a trade: every player gets the item's new price, and an open market screen the player's trades. */
    private static TradeResult traded(ServerPlayer player, Item item, AccountId account, MarketService.TradeResult result) {
        if (result.ok()) {
            MarketSync.broadcastRow(player.server, item);
            if (player.containerMenu instanceof MarketMenu) {
                MarketSync.sendTrades(player);
            }
        }
        return new TradeResult(result(result.result()), result.quantity(), result.unitPrice(), result.total(), account);
    }

    /** The market's own result as the API's. {@code PRICE_CHANGED} needs a quote from a screen, which the API never passes. */
    static MarketResult result(MarketService.Result result) {
        return switch (result) {
            case OK -> MarketResult.OK;
            case NOT_LISTED -> MarketResult.NOT_LISTED;
            case SELL_DISABLED -> MarketResult.SELL_DISABLED;
            case BUY_DISABLED -> MarketResult.BUY_DISABLED;
            case NOT_ENOUGH_ITEMS -> MarketResult.NOT_ENOUGH_ITEMS;
            case NO_STOCK -> MarketResult.NO_STOCK;
            case NO_SPACE -> MarketResult.NO_SPACE;
            case NO_FUNDS -> MarketResult.NO_FUNDS;
            case TOO_LARGE -> MarketResult.TOO_LARGE;
            case INVALID_QUANTITY -> MarketResult.INVALID_QUANTITY;
            case NOT_ALLOWED -> MarketResult.NOT_ALLOWED;
            case NO_ACCOUNT -> MarketResult.NO_ACCOUNT;
            case BANK_UNAVAILABLE, PRICE_CHANGED -> MarketResult.BANK_UNAVAILABLE;
        };
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Variable Economy's market must be called on the server thread");
        }
    }
}
