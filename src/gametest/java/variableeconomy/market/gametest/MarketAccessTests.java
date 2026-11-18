package variableeconomy.market.gametest;

import java.util.List;
import variableeconomy.market.data.TradeHistory;
import variableeconomy.market.data.MarketOverrides;
import variableeconomy.market.data.MarketStockData;
import variableeconomy.market.menu.MarketMenu;
import variableeconomy.market.block.MarketBlocks;
import variableeconomy.market.MarketSettings;
import variableeconomy.market.MarketConfig;
import variableeconomy.core.api.CoreApi;
import variableeconomy.core.compat.StagesBridge;
import variableeconomy.core.service.ItemGateService;
import variableeconomy.market.MarketModule;
import variableeconomy.market.catalog.MarketCatalog;
import variableeconomy.market.network.MarketPayloads;
import variableeconomy.market.network.MarketSync;
import variableeconomy.market.service.MarketService.Result;
import variableeconomy.market.service.MarketService;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static variableeconomy.market.gametest.CoreTestSupport.*;
import static variableeconomy.market.gametest.MarketTestSupport.*;

/**
 * The Market Terminal, its access mode, and items locked by stage.
 *
 * Shared setup is in {@link variableeconomy.market.gametest.CoreTestSupport}.
 */
@GameTestHolder(MarketModule.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MarketAccessTests {
    private MarketAccessTests() {
    }


    // ---- Stage locks -------------------------------------------------------------------------------------------

    // These lock items through the item gate's test hook, which every check consults exactly as it does the real
    // locks, so they don't depend on ProgressiveStages; progressiveStagesLocksHideItemsFromTheMarket checks its own.

    @GameTest(template = EMPTY)
    public static void lockedItemIsHiddenAndUntradeableForOnePlayerOnly(GameTestHelper helper) {
        MarketFixture locked = new MarketFixture(helper);
        MarketFixture free = new MarketFixture(helper);
        locked.setStock(Items.IRON_INGOT, 50);
        locked.deposit(10_000);
        free.deposit(10_000);
        locked.giveMany(Items.IRON_INGOT, 10);
        free.giveMany(Items.IRON_INGOT, 10);
        ItemGateService.setTestLocks((player, item) -> player == locked.player
                && (item == Items.IRON_INGOT || item == Items.NETHER_STAR));
        try {
            locked.assertRejectedUnchanged(Result.NOT_LISTED, () -> MarketService.trySell(locked.player, Items.IRON_INGOT, 5, ANY_PRICE));
            locked.assertRejectedUnchanged(Result.NOT_LISTED, () -> MarketService.tryBuy(locked.player, Items.IRON_INGOT, 1, ANY_PRICE));
            // A locked item answers as an unlisted one, never with its own reason (the nether star can't be sold).
            locked.assertRejectedUnchanged(Result.NOT_LISTED, () -> MarketService.trySell(locked.player, Items.NETHER_STAR, 1, ANY_PRICE));
            helper.assertTrue(rowIds(MarketSync.catalogFor(locked.player)).stream().noneMatch(id -> id.equals(id(Items.IRON_INGOT))),
                    "locked player's catalog has no iron");
            helper.assertTrue(rowIds(MarketSync.catalogFor(locked.player)).contains(id(Items.DIAMOND)),
                    "locked player still sees unlocked items");

            helper.assertTrue(rowIds(MarketSync.catalogFor(free.player)).contains(id(Items.IRON_INGOT)), "other player sees iron");
            free.assertResult(MarketService.trySell(free.player, Items.IRON_INGOT, 5, ANY_PRICE), Result.OK);
            free.assertResult(MarketService.tryBuy(free.player, Items.IRON_INGOT, 1, ANY_PRICE), Result.OK);
        } finally {
            ItemGateService.setTestLocks(null);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void unlockingShowsTheItemAgainAtTheSamePrice(GameTestHelper helper) {
        MarketFixture f = new MarketFixture(helper);
        f.giveMany(Items.IRON_INGOT, 5);
        MarketPayloads.Row before = MarketSync.catalogFor(f.player).rows().stream()
                .filter(row -> row.item().equals(id(Items.IRON_INGOT))).findFirst().orElseThrow();
        ItemGateService.setTestLocks((player, item) -> item == Items.IRON_INGOT);
        try {
            helper.assertFalse(rowIds(MarketSync.catalogFor(f.player)).contains(id(Items.IRON_INGOT)), "iron hidden while locked");
        } finally {
            ItemGateService.setTestLocks(null);
        }
        MarketPayloads.Row after = MarketSync.catalogFor(f.player).rows().stream()
                .filter(row -> row.item().equals(id(Items.IRON_INGOT))).findFirst().orElseThrow();
        helper.assertValueEqual(after, before, "iron row after unlocking (prices are the same for everyone)");
        f.assertResult(MarketService.trySell(f.player, Items.IRON_INGOT, 5, ANY_PRICE), Result.OK);
        helper.succeed();
    }

    /**
     * ProgressiveStages' own locks, when it is loaded: the GameTest worlds' test stage ({@code tools/gametest-config})
     * locks the dragon egg, so a listed egg is left out of a stage-less player's catalog and can't be traded. Without
     * it, nothing can be hidden and every listed item is sent.
     */
    @GameTest(template = EMPTY)
    public static void progressiveStagesLocksHideItemsFromTheMarket(GameTestHelper helper) {
        MarketFixture f = new MarketFixture(helper);
        boolean loaded = ModList.get().isLoaded(StagesBridge.MOD_ID);
        helper.assertTrue(StagesBridge.isAvailable() == loaded, "the bridge is on exactly when ProgressiveStages is loaded");
        helper.assertTrue(CoreApi.itemGate().canLockAnything() == loaded, "items can be hidden exactly when it is loaded");
        helper.assertFalse(StagesBridge.isLocked(f.player, Items.IRON_INGOT), "iron is never locked");
        withEntry(seed(Items.DRAGON_EGG, 100, null), () -> {
            Set<ResourceLocation> sent = rowIds(MarketSync.catalogFor(f.player));
            helper.assertValueEqual(sent.size(), MarketCatalog.INSTANCE.entries().size() - (loaded ? 1 : 0),
                    "every listed item but a locked one is sent");
            helper.assertTrue(sent.contains(id(Items.DRAGON_EGG)) != loaded, "the egg is sent only when it isn't locked");
            if (loaded) {
                f.assertResult(MarketService.tryBuy(f.player, Items.DRAGON_EGG, 1, ANY_PRICE), Result.NOT_LISTED);
            }
        });
        helper.succeed();
    }

    private static Set<ResourceLocation> rowIds(MarketPayloads.Snapshot snapshot) {
        Set<ResourceLocation> ids = new java.util.HashSet<>();
        snapshot.rows().forEach(row -> ids.add(row.item()));
        return ids;
    }

    // ---- The Market Terminal: its block, access mode, ids and commands ------------------------------------------------------

    private static EconomyBlock terminal() {
        return new EconomyBlock("market_terminal", MarketBlocks.MARKET_TERMINAL.get(), MarketBlocks.MARKET_TERMINAL_ITEM.get(), MarketMenu::forTerminal, MarketMenu::forCommand,
                net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE);
    }

    @GameTest(template = EMPTY)
    public static void blockOnlyModeKeepsCommandsForOperators(GameTestHelper helper) {
        helper.assertTrue(MarketSettings.commandsAllowed(false, false), "everyone, by default");
        helper.assertTrue(!MarketSettings.commandsAllowed(true, false), "players need the block");
        helper.assertTrue(MarketSettings.commandsAllowed(true, true), "operators keep commands");
        helper.assertTrue(MarketSettings.blockOnly() == (MarketConfig.accessMode() == MarketConfig.AccessMode.BLOCK_ONLY),
                "the market reads its own access_mode");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void terminalScreenClosesWhenYouWalkAwayOrItsBlockBreaks(GameTestHelper helper) {
        assertScreenFollowsTheBlock(helper, terminal());
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void terminalHasARecipeAndFacing(GameTestHelper helper) {
        assertRecipeAndFacing(helper, terminal());
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void terminalIsSolidAndDropsItself(GameTestHelper helper) {
        assertSolidAndDropsItself(helper, terminal());
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void commandsSitUnderTheirOwnRoot(GameTestHelper helper) {
        for (String command : List.of("market", "market stock", "market admin backup", "market admin reload")) {
            helper.assertTrue(commandExists(helper, command), "/" + command + " exists");
        }
        for (String command : List.of("market contracts", "market exchange")) {
            helper.assertTrue(!commandExists(helper, command), "/" + command + " is gone");
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void everythingUsesTheModId(GameTestHelper helper) {
        net.minecraft.server.MinecraftServer server = helper.getLevel().getServer();
        assertOwnNamespace(helper, MarketModule.MOD_ID, List.of("money.variable_economy_market.bits"));
        for (String id : List.of(MarketStockData.ID, MarketOverrides.ID, TradeHistory.ID)) {
            helper.assertTrue(id.startsWith("variable_economy_"), "saved data " + id + " is named after the mod");
        }
        // build.gradle sets the property for the test server; spelled differently there, all the built-in prices load.
        helper.assertTrue(Boolean.getBoolean(MarketModule.SKIP_DEFAULT_PRICES),
                MarketModule.SKIP_DEFAULT_PRICES + " is set for the test server");
        helper.assertTrue(server.getPackRepository().getPack(MarketModule.EXAMPLE_PACK_ID) != null, "the example catalog is a pack");
        helper.assertTrue(server.getPackRepository().getPack(MarketModule.DEFAULT_PRICES_PACK_ID) == null,
                "and the built-in prices aren't, while the property is set");
        helper.succeed();
    }
}
