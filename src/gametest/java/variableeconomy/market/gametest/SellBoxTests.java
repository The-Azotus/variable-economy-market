package variableeconomy.market.gametest;

import variableeconomy.core.api.CoreApi;
import variableeconomy.market.MarketModule;
import variableeconomy.market.menu.MarketMenu;
import variableeconomy.market.network.MarketPayloads;
import variableeconomy.market.network.MarketSync;
import variableeconomy.market.service.MarketService;
import variableeconomy.market.service.MarketService.Result;
import variableeconomy.market.service.PriceModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import static variableeconomy.market.gametest.CoreTestSupport.*;
import static variableeconomy.market.gametest.MarketTestSupport.*;

/**
 * The Selling box (one request selling several items, each as its own sale), the screen's "at least as good" price
 * check, a dead player's trades, and the market menu's shown-only inventory slots.
 */
@GameTestHolder(MarketModule.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SellBoxTests {
    private SellBoxTests() {
    }

    /** What selling {@code qty} pays right now, as the screen would show it. */
    private static long sellTotal(MarketFixture f, Item item, int qty) {
        MarketService.Quote quote = f.quote(item);
        return PriceModel.sell(quote.inputs(), quote.factor(), qty).total();
    }

    private static long buyTotal(MarketFixture f, Item item, int qty) {
        MarketService.Quote quote = f.quote(item);
        return PriceModel.buy(quote.inputs(), quote.factor(), qty).total();
    }

    private static MarketPayloads.SellLine line(Item item, int qty, long quoted) {
        return new MarketPayloads.SellLine(key(item), qty, quoted);
    }

    private static MarketPayloads.SellBoxRequest request(MarketPayloads.SellLine... lines) {
        return new MarketPayloads.SellBoxRequest(List.of(lines));
    }

    @GameTest(template = EMPTY)
    public static void sellBoxSellsEachItemAsItsOwnSale(GameTestHelper helper) {
        MarketFixture f = new MarketFixture(helper);
        f.give(new ItemStack(Items.IRON_INGOT, 10));
        f.give(new ItemStack(Items.DIAMOND, 2));
        long ironStock = f.stock(Items.IRON_INGOT);
        long diamondStock = f.stock(Items.DIAMOND);
        long iron = sellTotal(f, Items.IRON_INGOT, 10);
        long diamonds = sellTotal(f, Items.DIAMOND, 2);

        List<MarketPayloads.SellOutcomeLine> outcomes = MarketSync.sellBox(f.player,
                request(line(Items.IRON_INGOT, 10, iron), line(Items.DIAMOND, 2, diamonds)));

        helper.assertValueEqual(outcomes.stream().map(MarketPayloads.SellOutcomeLine::result).toList(),
                List.of(Result.OK, Result.OK), "both lines sold");
        helper.assertValueEqual(outcomes.get(0).total(), iron, "iron paid what the box showed");
        helper.assertValueEqual((long) f.balance(), iron + diamonds, "paid for both");
        helper.assertValueEqual(f.count(Items.IRON_INGOT) + f.count(Items.DIAMOND), 0L, "both taken");
        helper.assertValueEqual(f.stock(Items.IRON_INGOT), ironStock + 10, "iron stock");
        helper.assertValueEqual(f.stock(Items.DIAMOND), diamondStock + 2, "diamond stock");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void sellBoxSellsAtABetterPriceAndKeepsAWorseOne(GameTestHelper helper) {
        MarketFixture f = new MarketFixture(helper);
        f.give(new ItemStack(Items.IRON_INGOT, 5));
        f.give(new ItemStack(Items.DIAMOND, 1));
        long iron = sellTotal(f, Items.IRON_INGOT, 5);
        long diamond = sellTotal(f, Items.DIAMOND, 1);

        // The box showed less for the iron than it pays now (the price rose), and more for the diamond (it fell).
        List<MarketPayloads.SellOutcomeLine> outcomes = MarketSync.sellBox(f.player,
                request(line(Items.IRON_INGOT, 5, iron - 1), line(Items.DIAMOND, 1, diamond + 1)));

        helper.assertValueEqual(outcomes.get(0).result(), Result.OK, "a better price still sells");
        helper.assertValueEqual(outcomes.get(0).total(), iron, "and pays the current total");
        helper.assertValueEqual(outcomes.get(1).result(), Result.PRICE_CHANGED, "a worse price doesn't");
        helper.assertValueEqual(outcomes.get(1).total(), diamond, "the reply carries the new total");
        helper.assertValueEqual((long) f.balance(), iron, "only the iron was paid");
        helper.assertValueEqual(f.count(Items.DIAMOND), 1L, "the diamond stays in the inventory");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void sellBoxLinesThatFailLeaveTheOthers(GameTestHelper helper) {
        MarketFixture f = new MarketFixture(helper);
        f.give(new ItemStack(Items.IRON_INGOT, 5));
        f.give(new ItemStack(Items.NETHER_STAR, 1));
        Item bit = BuiltInRegistries.ITEM.stream().filter(CoreApi::isCoin).findFirst().orElseThrow();
        f.give(new ItemStack(bit, 8));
        long iron = sellTotal(f, Items.IRON_INGOT, 5);

        List<MarketPayloads.SellOutcomeLine> outcomes = MarketSync.sellBox(f.player, request(
                line(Items.DIAMOND, 1, 1),
                line(Items.NETHER_STAR, 1, 1),
                line(bit, 8, 1),
                line(Items.IRON_INGOT, 5, iron)));

        helper.assertValueEqual(outcomes.stream().map(MarketPayloads.SellOutcomeLine::result).toList(),
                List.of(Result.NOT_ENOUGH_ITEMS, Result.SELL_DISABLED, Result.NOT_LISTED, Result.OK),
                "items not held, not bought or coins fail; the iron sells");
        helper.assertValueEqual((long) f.balance(), iron, "only the iron was paid");
        helper.assertValueEqual(f.count(Items.NETHER_STAR) + f.count(bit), 9L, "the rest stays");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void sellBoxRefusesRequestsTheScreenNeverSends(GameTestHelper helper) {
        MarketFixture f = new MarketFixture(helper);
        f.give(new ItemStack(Items.IRON_INGOT, 10));
        long iron = sellTotal(f, Items.IRON_INGOT, 5);

        helper.assertTrue(MarketSync.sellBox(f.player, request(line(Items.IRON_INGOT, 5, iron), line(Items.IRON_INGOT, 5, iron)))
                .isEmpty(), "one item twice is refused whole");
        helper.assertTrue(MarketSync.sellBox(f.player, request()).isEmpty(), "an empty box is refused");
        List<MarketPayloads.SellLine> tooMany = new ArrayList<>();
        BuiltInRegistries.ITEM.stream().limit(MarketPayloads.MAX_SELL_BOX_LINES + 1L)
                .forEach(item -> tooMany.add(line(item, 1, 0)));
        helper.assertTrue(MarketSync.sellBox(f.player, new MarketPayloads.SellBoxRequest(tooMany)).isEmpty(),
                "more lines than inventory slots are refused whole");
        helper.assertValueEqual(f.count(Items.IRON_INGOT), 10L, "nothing sold");
        helper.assertValueEqual(f.balance(), 0, "nothing paid");

        // Only an open market screen can sell the box.
        f.player.containerMenu = f.player.inventoryMenu;
        MarketSync.handleSellBox(request(line(Items.IRON_INGOT, 5, iron)), f.player);
        helper.assertValueEqual(f.count(Items.IRON_INGOT), 10L, "nothing sold without the screen");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void screenTradesGoThroughWhenThePriceIsBetter(GameTestHelper helper) {
        MarketFixture f = new MarketFixture(helper);
        f.give(new ItemStack(Items.IRON_INGOT, 4));
        f.setStock(Items.DIAMOND, 10);
        f.deposit(100_000);

        long sale = sellTotal(f, Items.IRON_INGOT, 2);
        f.assertRejectedUnchanged(Result.PRICE_CHANGED, () -> MarketService.trySell(f.player, Items.IRON_INGOT, 2,
                MarketService.Expected.orBetter(sale + 1)));
        f.assertResult(MarketService.trySell(f.player, Items.IRON_INGOT, 2, MarketService.Expected.orBetter(sale - 1)), Result.OK);
        helper.assertValueEqual((long) f.balance(), 100_000 + sale, "a sale that pays more than shown goes through");

        long purchase = buyTotal(f, Items.DIAMOND, 1);
        f.assertRejectedUnchanged(Result.PRICE_CHANGED, () -> MarketService.tryBuy(f.player, Items.DIAMOND, 1,
                MarketService.Expected.orBetter(purchase - 1)));
        f.assertResult(MarketService.tryBuy(f.player, Items.DIAMOND, 1, MarketService.Expected.orBetter(purchase + 1)), Result.OK);
        helper.assertValueEqual((long) f.balance(), 100_000 + sale - purchase, "a purchase that costs less than shown goes through");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aDeadPlayerCantTrade(GameTestHelper helper) {
        MarketFixture f = new MarketFixture(helper);
        f.give(new ItemStack(Items.IRON_INGOT, 5));
        f.setStock(Items.IRON_INGOT, 10);
        f.deposit(10_000);
        f.player.setHealth(0);

        f.assertRejectedUnchanged(Result.NOT_ALLOWED, () -> MarketService.tryBuy(f.player, Items.IRON_INGOT, 1, ANY_PRICE));
        f.assertRejectedUnchanged(Result.NOT_ALLOWED, () -> MarketService.trySell(f.player, Items.IRON_INGOT, 1, ANY_PRICE));

        // With the market screen still open after death, its packets are ignored.
        f.player.containerMenu = MarketMenu.forCommand();
        try {
            MarketSync.handleTradeRequest(new MarketPayloads.TradeRequest(true, key(Items.IRON_INGOT), 1,
                    buyTotal(f, Items.IRON_INGOT, 1)), f.player);
            MarketSync.handleSellBox(request(line(Items.IRON_INGOT, 5, 0)), f.player);
        } finally {
            f.player.containerMenu = f.player.inventoryMenu;
        }
        helper.assertValueEqual(f.balance(), 10_000, "nothing paid or charged");
        helper.assertValueEqual(f.count(Items.IRON_INGOT), 5L, "nothing bought or sold");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theMenusInventorySlotsCantBeClicked(GameTestHelper helper) {
        MarketFixture f = new MarketFixture(helper);
        f.player.getInventory().items.set(0, new ItemStack(Items.IRON_INGOT, 7));
        f.player.getInventory().items.set(9, new ItemStack(Items.DIAMOND, 3));
        MarketMenu menu = MarketMenu.forTerminal(f.player, helper.absolutePos(BlockPos.ZERO));

        helper.assertValueEqual(menu.slots.size(), MarketMenu.INVENTORY_SLOTS, "one slot per main-inventory slot");
        helper.assertTrue(menu.getSlot(9).getItem().is(Items.DIAMOND), "slots show the player's inventory");
        for (ClickType type : ClickType.values()) {
            menu.clicked(0, 0, type, f.player);
            menu.clicked(9, 1, type, f.player);
        }
        helper.assertTrue(menu.quickMoveStack(f.player, 0).isEmpty(), "nothing quick-moves");
        helper.assertTrue(menu.getCarried().isEmpty(), "nothing picked up");
        helper.assertValueEqual(f.player.getInventory().items.get(0).getCount(), 7, "hotbar slot untouched");
        helper.assertValueEqual(f.player.getInventory().items.get(9).getCount(), 3, "inventory slot untouched");
        helper.assertValueEqual(f.count(Items.IRON_INGOT), 7L, "nothing moved or thrown");
        helper.succeed();
    }
}
