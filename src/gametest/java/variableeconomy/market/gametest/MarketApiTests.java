package variableeconomy.market.gametest;

import variableeconomy.core.api.AccountId;
import variableeconomy.core.api.CoreApi;
import variableeconomy.core.api.GateRule;
import variableeconomy.core.api.Reason;
import variableeconomy.core.coin.Coin;
import variableeconomy.core.coin.Coins;
import variableeconomy.market.MarketConfig;
import variableeconomy.market.MarketModule;
import variableeconomy.market.api.MarketApi;
import variableeconomy.market.api.MarketQuote;
import variableeconomy.market.api.MarketResult;
import variableeconomy.market.api.TradeResult;
import variableeconomy.market.api.event.CatalogReloadedEvent;
import variableeconomy.market.api.event.MarketTradeEvent;
import variableeconomy.market.catalog.CatalogEntry;
import variableeconomy.market.catalog.MarketCatalog;
import variableeconomy.market.service.MarketService.Result;
import variableeconomy.market.service.MarketService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static variableeconomy.market.gametest.CoreTestSupport.*;
import static variableeconomy.market.gametest.MarketTestSupport.*;

/**
 * The market's API: quotes, trades, refusals and the market's events.
 *
 * Shared setup is in {@link variableeconomy.market.gametest.CoreTestSupport}.
 */
@GameTestHolder(MarketModule.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MarketApiTests {
    private MarketApiTests() {
    }

    /** The API's trade result as the market's, so {@link MarketFixture#assertRejectedUnchanged} can check it. */
    private static MarketService.TradeResult internal(TradeResult result) {
        return new MarketService.TradeResult(Result.valueOf(result.result().name()), result.quantity(), result.unitPrice(),
                result.total());
    }

    /** An item the market doesn't list in the test world. */
    private static Item unlisted() {
        for (Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR && MarketCatalog.INSTANCE.get(item).isEmpty() && !Coins.isCoin(item)) {
                return item;
            }
        }
        throw new IllegalStateException("every item is listed");
    }

    private static AccountId own(MarketFixture f) {
        return CoreApi.accounts().personal(f.player.getUUID());
    }

    /** Runs {@code body} with a gate rule in place, always removing it again. */
    static void withRule(ResourceLocation id, GateRule rule, Runnable body) {
        CoreApi.itemGate().addRule(id, rule);
        try {
            body.run();
        } finally {
            CoreApi.itemGate().removeRule(id);
        }
    }

    // ---- Quotes ------------------------------------------------------------------------------------------------

    @GameTest(template = EMPTY)
    public static void apiQuotesMatchTheMarket(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        MarketFixture f = new MarketFixture(helper);
        MarketFixture other = new MarketFixture(helper);
        MarketService.Quote market = f.quote(Items.IRON_INGOT);
        MarketQuote quote = MarketApi.prices().quote(server, Items.IRON_INGOT).orElseThrow();
        helper.assertValueEqual(quote.item(), Items.IRON_INGOT, "item");
        helper.assertValueEqual(quote.basePrice(), (long) market.entry().basePrice(), "base price");
        helper.assertValueEqual(quote.sellPrice(), market.sellPrice(), "sell price");
        helper.assertValueEqual(quote.buyPrice(), market.buyPrice(), "buy price");
        helper.assertValueEqual(quote.stock(), market.stock(), "stock");
        helper.assertValueEqual(quote.canSell(), market.entry().canSell(), "can sell");
        helper.assertValueEqual(quote.canBuy(), market.entry().canBuy(), "can buy");
        helper.assertValueEqual(quote.factor(), market.factor(), "factor");
        helper.assertValueEqual(MarketApi.prices().quote(f.player, Items.IRON_INGOT), Optional.of(quote), "the player sees the same terms");

        CatalogEntry logs = MarketCatalog.INSTANCE.get(Items.OAK_LOG).orElseThrow();
        helper.assertTrue(logs.unlimitedStock(), "oak logs are unlimited in the example catalog");
        helper.assertValueEqual(MarketApi.prices().quote(server, Items.OAK_LOG).orElseThrow().stock(), OptionalLong.empty(),
                "unlimited stock is empty");

        Item unlisted = unlisted();
        helper.assertValueEqual(MarketApi.prices().quote(server, unlisted), Optional.empty(), "an unlisted item has no quote: " + key(unlisted));
        helper.assertValueEqual(MarketApi.prices().quote(f.player, unlisted), Optional.empty(), "for anyone");

        withRule(rl("lock_iron"), (player, item) -> player == f.player && item == Items.IRON_INGOT, () -> {
            helper.assertTrue(CoreApi.itemGate().canLockAnything(), "a rule makes locks per player");
            helper.assertValueEqual(MarketApi.prices().quote(f.player, Items.IRON_INGOT), Optional.empty(), "locked for this player");
            helper.assertTrue(CoreApi.itemGate().isLocked(f.player, Items.IRON_INGOT), "isLocked says so");
            helper.assertTrue(MarketApi.prices().quote(other.player, Items.IRON_INGOT).isPresent(), "others still see it");
            helper.assertFalse(CoreApi.itemGate().isLocked(other.player, Items.IRON_INGOT), "not locked for others");
            helper.assertTrue(MarketApi.prices().quote(server, Items.IRON_INGOT).isPresent(), "the server-wide quote ignores locks");
            helper.assertTrue(MarketApi.prices().listedItems(server).contains(Items.IRON_INGOT), "and so does the item list");
        });
        helper.assertTrue(MarketApi.prices().quote(f.player, Items.IRON_INGOT).isPresent(), "shown again once the rule is gone");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void apiHelpersAnswerLikeTheMod(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        List<Item> listed = MarketApi.prices().listedItems(server);
        helper.assertValueEqual(Set.copyOf(listed), MarketCatalog.INSTANCE.entries().stream().map(CatalogEntry::item).collect(Collectors.toSet()),
                "every listed item");
        helper.assertValueEqual(listed.size(), MarketCatalog.INSTANCE.entries().size(), "once each");
        helper.assertFalse(listed.stream().anyMatch(CoreApi::isCoin), "never a coin");
        helper.assertTrue(CoreApi.isCoin(Coins.item(Coin.BIT)), "a bit is a coin");
        helper.assertFalse(CoreApi.isCoin(Items.IRON_INGOT), "iron isn't");
        helper.assertTrue(CoreApi.money().available(server), "the mod's own bank is always reachable");
        helper.assertValueEqual(CoreApi.formatMoney(1234).getString(), "1,234 bits", "money as chat shows it");
        ItemStack renamed = new ItemStack(Items.IRON_INGOT);
        renamed.set(DataComponents.CUSTOM_NAME, Component.literal("Shiny"));
        helper.assertTrue(CoreApi.isPlain(new ItemStack(Items.IRON_INGOT), Items.IRON_INGOT), "a fresh copy is plain");
        helper.assertFalse(CoreApi.isPlain(renamed, Items.IRON_INGOT), "a renamed one isn't");
        helper.assertFalse(CoreApi.isPlain(new ItemStack(Items.GOLD_INGOT), Items.IRON_INGOT), "nor another item");
        helper.succeed();
    }

    // ---- Trades ------------------------------------------------------------------------------------------------

    @GameTest(template = EMPTY)
    public static void apiSellAndBuyTradeAndPostOneEventEach(GameTestHelper helper) {
        withFixedPrice(Items.IRON_INGOT, () -> {
            MinecraftServer server = helper.getLevel().getServer();
            MarketFixture f = new MarketFixture(helper);
            long stock = f.stock(Items.IRON_INGOT);
            try {
                f.give(new ItemStack(Items.IRON_INGOT, 10));
                MarketQuote quote = MarketApi.prices().quote(server, Items.IRON_INGOT).orElseThrow();
                TradeResult[] sold = new TradeResult[1];
                List<Event> events = marketEventsDuring(() -> sold[0] = MarketApi.trading().sell(f.player, Items.IRON_INGOT, 10));
                helper.assertTrue(sold[0].ok(), "sold: " + sold[0]);
                helper.assertValueEqual(sold[0], new TradeResult(MarketResult.OK, 10, quote.sellPrice(), 10 * quote.sellPrice(), own(f)),
                        "ten at the quoted (fixed) price, into the player's own account");
                helper.assertValueEqual(f.balance(), (int) sold[0].total(), "paid into the account");
                helper.assertValueEqual(f.count(Items.IRON_INGOT), 0L, "taken from the inventory");
                helper.assertValueEqual(f.stock(Items.IRON_INGOT), stock + 10, "added to stock");
                helper.assertValueEqual(events.size(), 1, "one event: " + events);
                MarketTradeEvent sale = (MarketTradeEvent) events.get(0);
                helper.assertTrue(sale.getPlayer() == f.player && sale.getServer() == server, "the player and server");
                helper.assertValueEqual(sale.getAccount(), own(f), "the account paid");
                helper.assertValueEqual(sale.getItem(), Items.IRON_INGOT, "event item");
                helper.assertFalse(sale.isBuy(), "a sale");
                helper.assertValueEqual(sale.getQuantity(), 10, "event quantity");
                helper.assertValueEqual(sale.getUnitPrice(), sold[0].unitPrice(), "event unit price");
                helper.assertValueEqual(sale.getTotal(), sold[0].total(), "event total");

                int balance = f.balance();
                TradeResult[] bought = new TradeResult[1];
                events = marketEventsDuring(() -> bought[0] = MarketApi.trading().buy(f.player, Items.IRON_INGOT, 5, own(f)));
                helper.assertValueEqual(bought[0], new TradeResult(MarketResult.OK, 5, quote.buyPrice(), 5 * quote.buyPrice(), own(f)),
                        "five at the quoted (fixed) price, from the named account");
                helper.assertValueEqual(f.balance(), balance - (int) bought[0].total(), "charged");
                helper.assertValueEqual(f.count(Items.IRON_INGOT), 5L, "in the inventory");
                helper.assertValueEqual(f.stock(Items.IRON_INGOT), stock + 5, "taken from stock");
                helper.assertValueEqual(events.size(), 1, "one event: " + events);
                MarketTradeEvent purchase = (MarketTradeEvent) events.get(0);
                helper.assertTrue(purchase.isBuy() && purchase.getQuantity() == 5 && purchase.getTotal() == bought[0].total()
                        && purchase.getUnitPrice() == bought[0].unitPrice() && purchase.getPlayer() == f.player, "the purchase's event");

                // The screen and commands trade through MarketService, which posts the same event.
                events = marketEventsDuring(() -> f.assertResult(MarketService.trySell(f.player, Items.IRON_INGOT, 5, ANY_PRICE), Result.OK));
                helper.assertValueEqual(only(events, MarketTradeEvent.class).size(), 1, "a market-screen trade posts one too");
            } finally {
                f.setStock(Items.IRON_INGOT, stock);
            }
        });
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void apiTradeRefusalsChangeNothingAndPostNoEvent(GameTestHelper helper) {
        MarketFixture f = new MarketFixture(helper);
        MarketFixture other = new MarketFixture(helper);
        long stock = f.stock(Items.IRON_INGOT);
        try {
            f.setStock(Items.IRON_INGOT, 3);
            f.give(new ItemStack(Items.IRON_INGOT, 2));
            int max = MarketConfig.maxQuantityPerRequest();
            List<Event> events = marketEventsDuring(() -> {
                f.assertRejectedUnchanged(Result.NOT_ENOUGH_ITEMS, () -> internal(MarketApi.trading().sell(f.player, Items.IRON_INGOT, 5)));
                f.assertRejectedUnchanged(Result.NO_FUNDS, () -> internal(MarketApi.trading().buy(f.player, Items.IRON_INGOT, 1)));
                f.assertRejectedUnchanged(Result.NO_STOCK, () -> internal(MarketApi.trading().buy(f.player, Items.IRON_INGOT, 4)));
                f.assertRejectedUnchanged(Result.INVALID_QUANTITY, () -> internal(MarketApi.trading().sell(f.player, Items.IRON_INGOT, 0)));
                f.assertRejectedUnchanged(Result.INVALID_QUANTITY, () -> internal(MarketApi.trading().buy(f.player, Items.IRON_INGOT, -1)));
                f.assertRejectedUnchanged(Result.TOO_LARGE, () -> internal(MarketApi.trading().buy(f.player, Items.IRON_INGOT, max + 1)));
                f.assertRejectedUnchanged(Result.SELL_DISABLED, () -> internal(MarketApi.trading().sell(f.player, Items.NETHER_STAR, 1)));
                f.assertRejectedUnchanged(Result.NOT_LISTED, () -> internal(MarketApi.trading().sell(f.player, unlisted(), 1)));
                f.assertRejectedUnchanged(Result.NOT_LISTED, () -> internal(MarketApi.trading().buy(f.player, unlisted(), 1)));
                // Another player's account, and one that doesn't exist.
                f.assertRejectedUnchanged(Result.NOT_ALLOWED, () -> internal(MarketApi.trading().sell(f.player, Items.IRON_INGOT, 1, own(other))));
                f.assertRejectedUnchanged(Result.NO_ACCOUNT, () -> internal(MarketApi.trading().sell(f.player, Items.IRON_INGOT, 1,
                        new AccountId(UUID.randomUUID()))));
                // An item a rule locks answers exactly as an unlisted one.
                f.deposit(10_000);
                withRule(rl("lock_iron_trades"), (player, item) -> player == f.player && item == Items.IRON_INGOT, () -> {
                    f.assertRejectedUnchanged(Result.NOT_LISTED, () -> internal(MarketApi.trading().sell(f.player, Items.IRON_INGOT, 1)));
                    f.assertRejectedUnchanged(Result.NOT_LISTED, () -> internal(MarketApi.trading().buy(f.player, Items.IRON_INGOT, 1)));
                });
            });
            helper.assertValueEqual(events, List.of(), "a refused trade posts no event");
            TradeResult refused = MarketApi.trading().sell(f.player, Items.IRON_INGOT, 0);
            helper.assertTrue(!refused.ok() && refused.quantity() == 0, "nothing traded: " + refused);
            f.assertResult(internal(MarketApi.trading().sell(f.player, Items.IRON_INGOT, 2)), Result.OK);
        } finally {
            f.setStock(Items.IRON_INGOT, stock);
        }
        helper.succeed();
    }

    /** A listener that throws is logged and ignored: the trade it heard about stands. */
    @GameTest(template = EMPTY)
    public static void aThrowingListenerCantUndoATrade(GameTestHelper helper) {
        withFixedPrice(Items.IRON_INGOT, () -> {
            MarketFixture f = new MarketFixture(helper);
            long stock = f.stock(Items.IRON_INGOT);
            f.give(new ItemStack(Items.IRON_INGOT, 4));
            TradeResult[] sold = new TradeResult[1];
            throwing = true;
            List<Event> events;
            try {
                events = marketEventsDuring(() -> sold[0] = MarketApi.trading().sell(f.player, Items.IRON_INGOT, 4));
            } finally {
                throwing = false;
                f.setStock(Items.IRON_INGOT, stock);
            }
            helper.assertTrue(sold[0].ok(), "the sale went through: " + sold[0]);
            helper.assertValueEqual(events.size(), 1, "the listener heard it once");
            helper.assertValueEqual(f.balance(), (int) sold[0].total(), "paid");
            helper.assertValueEqual(f.count(Items.IRON_INGOT), 0L, "the items went");
        });
        helper.succeed();
    }

    /** The API's results are the market's by name, for every result a trade can give. */
    @GameTest(template = EMPTY)
    public static void everyTradeResultHasAnApiResult(GameTestHelper helper) {
        List<Result> trades = List.of(Result.OK, Result.NOT_LISTED, Result.SELL_DISABLED, Result.BUY_DISABLED, Result.NOT_ENOUGH_ITEMS,
                Result.NO_STOCK, Result.NO_FUNDS, Result.NO_SPACE, Result.TOO_LARGE, Result.INVALID_QUANTITY, Result.NOT_ALLOWED,
                Result.NO_ACCOUNT, Result.BANK_UNAVAILABLE);
        for (Result result : trades) {
            try {
                MarketResult.valueOf(result.name());
            } catch (IllegalArgumentException e) {
                helper.fail("no MarketResult for " + result);
            }
        }
        Set<String> api = Arrays.stream(MarketResult.values()).map(Enum::name).collect(Collectors.toSet());
        Set<String> expected = trades.stream().map(Enum::name).collect(Collectors.toSet());
        helper.assertValueEqual(api, expected, "the API's results are exactly the trade results an API trade can give");
        helper.succeed();
    }

    // ---- Threads -----------------------------------------------------------------------------------------------

    @GameTest(template = EMPTY)
    public static void apiRefusesCallsOffTheServerThread(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        MarketFixture f = new MarketFixture(helper);
        f.deposit(100);
        f.give(new ItemStack(Items.IRON_INGOT, 1));
        AccountId account = own(f);
        Reason reason = Reason.of(rl("thread"));
        List<String> threw = new ArrayList<>();
        List<String> answered = new ArrayList<>();
        ResourceLocation ruleId = rl("from_another_thread");
        List<Runnable> calls = List.of(
                () -> MarketApi.prices().quote(server, Items.IRON_INGOT),
                () -> MarketApi.prices().quote(f.player, Items.IRON_INGOT),
                () -> MarketApi.prices().listedItems(server),
                () -> MarketApi.prices().lowestBuyCost(server, Items.IRON_INGOT, 1),
                () -> MarketApi.trading().sell(f.player, Items.IRON_INGOT, 1),
                () -> MarketApi.trading().buy(f.player, Items.IRON_INGOT, 1),
                () -> CoreApi.money().balance(server, account),
                () -> CoreApi.money().charge(server, account, 1, reason),
                () -> CoreApi.money().pay(server, account, 1, reason),
                () -> CoreApi.money().transfer(server, account, account, 1, reason),
                () -> CoreApi.money().available(server),
                () -> CoreApi.accounts().active(f.player),
                () -> CoreApi.accounts().get(server, account),
                () -> CoreApi.itemGate().isLocked(f.player, Items.IRON_INGOT),
                () -> CoreApi.itemGate().refresh(f.player));
        Thread thread = new Thread(() -> {
            for (int i = 0; i < calls.size(); i++) {
                try {
                    calls.get(i).run();
                    answered.add("call " + i);
                } catch (IllegalStateException e) {
                    threw.add("call " + i);
                }
            }
            // These may be called from any thread.
            CoreApi.isCoin(Items.IRON_INGOT);
            CoreApi.formatMoney(1);
            CoreApi.accounts().personal(f.player.getUUID());
            CoreApi.itemGate().addRule(ruleId, (player, item) -> false);
            CoreApi.itemGate().removeRule(ruleId);
        }, "variable-economy-api-test");
        try {
            thread.start();
            thread.join(10_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            CoreApi.itemGate().removeRule(ruleId);
        }
        helper.assertFalse(thread.isAlive(), "the other thread finished");
        helper.assertValueEqual(answered, List.of(), "every server-thread-only call refused off the thread");
        helper.assertValueEqual(threw.size(), calls.size(), "each with IllegalStateException");
        helper.assertValueEqual(f.balance(), 100, "nothing paid or charged");
        helper.assertValueEqual(f.count(Items.IRON_INGOT), 1L, "nothing traded");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aGateRuleThatThrowsIsRemoved(GameTestHelper helper) {
        MarketFixture f = new MarketFixture(helper);
        ResourceLocation broken = rl("broken");
        ResourceLocation working = rl("working");
        try {
            CoreApi.itemGate().addRule(working, (player, item) -> item == Items.GOLD_INGOT);
            CoreApi.itemGate().addRule(broken, (player, item) -> {
                if (item == Items.IRON_INGOT) {
                    throw new IllegalStateException("A broken gate rule (GameTest)");
                }
                return item == Items.DIAMOND;
            });
            helper.assertTrue(CoreApi.itemGate().isLocked(f.player, Items.DIAMOND), "the rule works until it throws");
            helper.assertFalse(CoreApi.itemGate().isLocked(f.player, Items.IRON_INGOT), "a rule that throws locks nothing");
            helper.assertFalse(CoreApi.itemGate().removeRule(broken), "it was removed");
            helper.assertFalse(CoreApi.itemGate().isLocked(f.player, Items.DIAMOND), "so what it locked is unlocked again");
            helper.assertTrue(MarketApi.prices().quote(f.player, Items.IRON_INGOT).isPresent(), "and quoted");
            helper.assertTrue(CoreApi.itemGate().isLocked(f.player, Items.GOLD_INGOT), "other rules stay");
        } finally {
            CoreApi.itemGate().removeRule(broken);
            CoreApi.itemGate().removeRule(working);
        }
        helper.succeed();
    }

    // ---- Catalog event -----------------------------------------------------------------------------------------

    @GameTest(template = EMPTY)
    public static void catalogRebuildPostsCatalogReloaded(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        List<Event> events = marketEventsDuring(() -> MarketModule.catalogReloaded(server));
        List<CatalogReloadedEvent> reloaded = only(events, CatalogReloadedEvent.class);
        helper.assertValueEqual(reloaded.size(), 1, "one event per rebuild: " + events);
        helper.assertTrue(reloaded.get(0).getServer() == server, "for this server");
        helper.assertTrue(MarketCatalog.INSTANCE.get(Items.IRON_INGOT).isPresent(), "posted with the catalog live");
        helper.succeed();
    }
}
