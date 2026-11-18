package variableeconomy.market.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import variableeconomy.market.gametest.CoreTestSupport.CoreFixture;
import variableeconomy.market.MarketConfig;
import variableeconomy.market.MarketModule;
import variableeconomy.market.api.event.CatalogReloadedEvent;
import variableeconomy.market.api.event.MarketTradeEvent;
import variableeconomy.market.catalog.CatalogEntry;
import variableeconomy.market.catalog.MarketCatalog;
import variableeconomy.market.data.MarketStockData;
import variableeconomy.market.service.MarketService;
import variableeconomy.market.service.MarketService.Result;
import variableeconomy.market.service.MarketService.TradeResult;
import variableeconomy.market.service.PriceModel;
import variableeconomy.market.service.PriceSuggestion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.Supplier;

import static variableeconomy.market.gametest.CoreTestSupport.key;
import static variableeconomy.market.gametest.CoreTestSupport.recordEvents;
import static variableeconomy.market.gametest.CoreTestSupport.listenFor;

/**
 * What the market's GameTests share: the market fixture, catalog swaps and pricing helpers. They rely on the example
 * catalog pack, which the GameTest server enables like every available pack.
 */
public final class MarketTestSupport {
    private MarketTestSupport() {
    }

    public static final OptionalLong ANY_PRICE = OptionalLong.empty();

    /** The market's events posted while {@code body} runs, in order (with any other module's its tests listen for). */
    public static List<net.neoforged.bus.api.Event> marketEventsDuring(Runnable body) {
        listenFor(MarketTradeEvent.class, CatalogReloadedEvent.class);
        return recordEvents(body);
    }

    /** Runs {@code body} with an item's supply/demand turned off, so it trades at fixed base prices. */
    public static void withFixedPrice(Item item, Runnable body) {
        CatalogEntry entry = MarketCatalog.INSTANCE.get(item).orElseThrow();
        withEntry(entry.withPricing(entry.sellFee(), false, entry.minBuyMarkup(), entry.overrides()), body);
    }

    /** Runs {@code body} with one listing replaced, then puts the real catalog back. */
    public static void withEntry(CatalogEntry entry, Runnable body) {
        Map<Item, CatalogEntry> original = swapEntry(entry);
        try {
            body.run();
        } finally {
            MarketCatalog.INSTANCE.replaceForTesting(original);
        }
    }

    /** Replaces one listing in the live catalog and returns the whole previous catalog to restore. */
    public static Map<Item, CatalogEntry> swapEntry(CatalogEntry entry) {
        Map<Item, CatalogEntry> swapped = new java.util.LinkedHashMap<>();
        MarketCatalog.INSTANCE.entries().forEach(e -> swapped.put(e.item(), e));
        swapped.put(entry.item(), entry);
        return MarketCatalog.INSTANCE.replaceForTesting(swapped);
    }

    public static PriceModel.Inputs inputs(int base, double markup, double sellFee, boolean unlimited) {
        return new PriceModel.Inputs(base, markup, sellFee, markup, unlimited, true, MarketConfig.sellImpact(),
                MarketConfig.buyImpact(), MarketConfig.floor(), MarketConfig.ceiling(), MarketConfig.recovery());
    }

    /** The catalog files as loaded in the test world (which has no admin edits of its own). */
    public static Map<Item, CatalogEntry> files() {
        Map<Item, CatalogEntry> files = new java.util.LinkedHashMap<>();
        MarketCatalog.INSTANCE.entries().forEach(e -> files.put(e.item(), e));
        return files;
    }

    public static MarketCatalog.Loaded loadOne(String json) {
        return MarketCatalog.load(List.of(new MarketCatalog.SourceFile(ResourceLocation.fromNamespaceAndPath("test", "classes"), 0,
                "test", JsonParser.parseString(json))));
    }

    public static CatalogEntry seed(Item item, int price, String itemClass) {
        JsonObject json = new JsonObject();
        json.addProperty("item", key(item).toString());
        json.addProperty("base_price", price);
        if (itemClass != null) {
            json.addProperty("class", itemClass);
        }
        return MarketCatalog.decode(json).result().orElseThrow();
    }

    public static PriceSuggestion.Path path(Item output, int count, boolean fuel, Item... inputs) {
        List<PriceSuggestion.Slot> slots = new ArrayList<>();
        for (Item input : inputs) {
            slots.add(new PriceSuggestion.Slot(List.of(input), 1));
        }
        return new PriceSuggestion.Path(ResourceLocation.fromNamespaceAndPath("test", key(output).getPath()), "minecraft:crafting",
                output, count, slots, fuel);
    }

    /** The built-in price files, read straight from the mod (the GameTest server leaves the pack itself off). */
    public static MarketCatalog.Loaded defaultPrices() {
        return MarketCatalog.load(defaultPriceFiles());
    }

    public static List<MarketCatalog.SourceFile> defaultPriceFiles() {
        try {
            Path folder = ModList.get().getModFileById(MarketModule.MOD_ID).getFile().findResource("default_prices", "data", MarketModule.DATA_NAMESPACE, "market");
            List<MarketCatalog.SourceFile> files = new ArrayList<>();
            try (var paths = Files.list(folder)) {
                for (Path file : paths.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                    String name = file.getFileName().toString().replace(".json", "");
                    files.add(new MarketCatalog.SourceFile(ResourceLocation.fromNamespaceAndPath(MarketModule.DATA_NAMESPACE, name), 0,
                            "default_prices", JsonParser.parseString(Files.readString(file))));
                }
            }
            return files;
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** The most a round trip in either order makes (negative: it loses), over a few starting factors and sizes. */
    public static long bestRoundTripProfit(PriceModel.Inputs in) {
        long best = Long.MIN_VALUE;
        for (double start : List.of(in.floor(), 1.0, in.ceiling())) {
            for (int qty : List.of(1, 64, 640, 3000)) {
                PriceModel.Order bought = PriceModel.buy(in, start, qty);
                PriceModel.Order resold = PriceModel.sell(in, bought.factorAfter(), qty);
                PriceModel.Order sold = PriceModel.sell(in, start, qty);
                PriceModel.Order boughtBack = PriceModel.buy(in, sold.factorAfter(), qty);
                best = Math.max(best, Math.max(resold.total() - bought.total(), sold.total() - boughtBack.total()));
            }
        }
        return best;
    }

    /** The least buying {@code units} of {@code entry} could ever cost at {@code factor}, as the market's API reports it. */
    public static OptionalLong lowestCost(CatalogEntry entry, PriceModel.Inputs in, double factor, int units) {
        return MarketService.lowestUnitBuyPrice(entry, in, factor)
                .map(unit -> OptionalLong.of(unit.multiply(java.math.BigDecimal.valueOf(units))
                        .setScale(0, java.math.RoundingMode.CEILING).longValueExact()))
                .orElse(OptionalLong.empty());
    }

    /** A fresh player with an empty inventory and account, and the market at base prices. */
    public static final class MarketFixture extends CoreFixture {
        public final MarketStockData market;

        public MarketFixture(GameTestHelper helper) {
            super(helper);
            this.market = MarketStockData.get(helper.getLevel().getServer());
            // Every test starts from base prices; tests share the market but each runs within a single tick.
            market.resetAllFactors();
        }

        public double factor(Item item) {
            CatalogEntry entry = MarketCatalog.INSTANCE.get(item).orElseThrow();
            return MarketService.currentFactor(entry, PriceModel.inputs(entry), market, MarketStockData.now(player.server));
        }

        public MarketService.Quote quote(Item item) {
            return MarketService.quote(player.server, item).orElseThrow();
        }

        public long stock(Item item) {
            return market.getStock(BuiltInRegistries.ITEM.getKey(item));
        }

        public void setStock(Item item, long amount) {
            market.setStock(BuiltInRegistries.ITEM.getKey(item), amount);
        }

        public void assertResult(TradeResult result, Result expected) {
            helper.assertValueEqual(result.result(), expected, "trade result");
        }

        /** A rejected trade must leave balance, inventory, stock, price factors, history and money counts as they were. */
        public void assertRejectedUnchanged(Result expected, Supplier<TradeResult> trade) {
            List<ItemStack> inventoryBefore = new ArrayList<>();
            player.getInventory().items.forEach(s -> inventoryBefore.add(s.copy()));
            Map<ResourceLocation, Long> stockBefore = Map.copyOf(market.view());
            Map<ResourceLocation, MarketStockData.Factor> factorsBefore = Map.copyOf(market.factorsView());
            Map<ResourceLocation, MarketStockData.History> historyBefore = Map.copyOf(market.historyView());
            Map<ResourceLocation, MarketStockData.Totals> itemTotalsBefore = Map.copyOf(market.itemTotalsView());
            MarketStockData.Totals totalsBefore = market.totals();
            int balanceBefore = balance();

            assertResult(trade.get(), expected);

            helper.assertValueEqual(balance(), balanceBefore, "balance after " + expected);
            helper.assertValueEqual(Map.copyOf(market.view()), stockBefore, "stock after " + expected);
            helper.assertValueEqual(Map.copyOf(market.factorsView()), factorsBefore, "price factors after " + expected);
            helper.assertValueEqual(Map.copyOf(market.historyView()), historyBefore, "price history after " + expected);
            helper.assertValueEqual(Map.copyOf(market.itemTotalsView()), itemTotalsBefore, "item money counts after " + expected);
            helper.assertValueEqual(market.totals(), totalsBefore, "money counts after " + expected);
            NonNullList<ItemStack> after = player.getInventory().items;
            for (int slot = 0; slot < after.size(); slot++) {
                helper.assertTrue(ItemStack.matches(after.get(slot), inventoryBefore.get(slot)),
                        "inventory slot " + slot + " changed after " + expected);
            }
        }
    }
}
