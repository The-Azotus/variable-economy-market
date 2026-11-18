package variableeconomy.market.gametest;

import variableeconomy.core.CoreModule;
import variableeconomy.core.bank.OwnBank;
import variableeconomy.core.api.AccountId;
import variableeconomy.core.api.AccountRole;
import variableeconomy.core.api.BankResult;
import variableeconomy.core.api.CoreApi;
import variableeconomy.core.api.Reason;
import variableeconomy.core.data.AccountRegistry;
import variableeconomy.core.service.CoreServices;
import variableeconomy.core.coin.Coin;
import variableeconomy.core.coin.Coins;
import variableeconomy.core.menu.BankMenu;
import variableeconomy.core.service.BankService;
import variableeconomy.market.MarketModule;
import variableeconomy.market.block.MarketBlocks;
import variableeconomy.market.catalog.CatalogOverlay;
import variableeconomy.market.catalog.MarketCatalog;
import variableeconomy.market.data.MarketOverrides;
import variableeconomy.market.network.MarketPayloads;
import variableeconomy.market.network.MarketSync;
import variableeconomy.market.service.MarketService.Result;
import variableeconomy.market.service.MarketService.TradeResult;
import variableeconomy.market.service.MarketService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static variableeconomy.market.gametest.CoreTestSupport.*;
import static variableeconomy.market.gametest.MarketTestSupport.*;

/**
 * The market and the bank together: coins are never listed, team accounts trade, the Bank Card's screen.
 *
 * Shared setup is in {@link variableeconomy.market.gametest.CoreTestSupport}.
 */
@GameTestHolder(MarketModule.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MarketBankTests {
    private MarketBankTests() {
    }


    @GameTest(template = EMPTY)
    public static void coinsCanNeverBeListed(GameTestHelper helper) {
        List<String> ids = new ArrayList<>(List.of(CoreModule.MOD_ID + ":bit", CoreModule.MOD_ID + ":omega"));
        // Numismatics' coins too, should a pack have that mod: selling them would turn its money into bits.
        if (net.neoforged.fml.ModList.get().isLoaded("numismatics")) {
            ids.addAll(List.of("numismatics:spur", "numismatics:sun"));
        }
        for (String id : ids) {
            MarketCatalog.Loaded loaded = loadOne("{ \"entries\": [ { \"item\": \"" + id + "\", \"base_price\": 1 } ] }");
            helper.assertTrue(loaded.entries().isEmpty(), id + " isn't listed from a file");
            helper.assertTrue(loaded.problems().size() == 1 && loaded.problems().get(0).message().contains("coin"),
                    "and the file says why: " + loaded.problems());
        }

        // Nor by an admin edit or a tag rule that prices it.
        Item bit = Coins.item(Coin.BIT);
        MarketOverrides edits = new MarketOverrides();
        edits.setItemField(key(bit), "base_price", number(1));
        ResourceLocation coinTag = ResourceLocation.fromNamespaceAndPath("test", "coins");
        edits.setTagField(coinTag, "base_price", number(5));
        CatalogOverlay.Result result = CatalogOverlay.apply(Map.of(), Map.of(), edits.snapshot(),
                tag -> tag.equals(coinTag) ? List.of(Coins.item(Coin.CHIP)) : CatalogOverlay.registryTag(tag));
        helper.assertTrue(result.entries().isEmpty(), "edits list no coin: " + result.entries().keySet());
        helper.assertTrue(result.problems().containsKey(key(bit)) && result.problems().containsKey(key(Coins.item(Coin.CHIP))),
                "and each is reported: " + result.problems());

        MarketFixture f = new MarketFixture(helper);
        f.give(new ItemStack(bit, 8));
        f.assertRejectedUnchanged(Result.NOT_LISTED, () -> MarketService.trySell(f.player, bit, 8, ANY_PRICE));
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void terminalCraftsFromBits(GameTestHelper helper) {
        Item b = Coins.item(Coin.BIT);
        Optional<ItemStack> made = craft(helper, b, b, b, b, Items.CRAFTING_TABLE, b, b, b, b);
        helper.assertTrue(made.isPresent() && made.get().is(MarketBlocks.MARKET_TERMINAL_ITEM.get()) && made.get().getCount() == 1,
                "a crafting table surrounded by 8 Bits makes one terminal, got " + made);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void bankCardOpensTheBankScreen(GameTestHelper helper) {
        MarketFixture f = new MarketFixture(helper);
        BankMenu menu = BankMenu.forCard();
        helper.assertFalse(menu.stillValid(f.player), "not without a card");
        f.give(new ItemStack(Coins.BANK_CARD.get()));
        helper.assertTrue(menu.stillValid(f.player), "valid while the player carries a card");

        // A trade sent from the bank screen is ignored, however it's dressed up: only the market's screen trades.
        f.setStock(Items.IRON_INGOT, 5);
        f.deposit(10_000);
        f.player.containerMenu = menu;
        try {
            MarketSync.handleTradeRequest(new MarketPayloads.TradeRequest(true, key(Items.IRON_INGOT), 1,
                    f.quote(Items.IRON_INGOT).buyPrice()), f.player);
        } finally {
            f.player.containerMenu = f.player.inventoryMenu;
        }
        helper.assertValueEqual(f.balance(), 10_000, "nothing was paid");
        helper.assertValueEqual(f.count(Items.IRON_INGOT), 0L, "nothing was bought");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void accountMembersShareOneAccount(GameTestHelper helper) {
        MarketFixture a = new MarketFixture(helper);
        MarketFixture b = new MarketFixture(helper);
        net.minecraft.server.MinecraftServer server = helper.getLevel().getServer();
        String name = accountName("t");
        String account = "command.variable_economy.account.";
        withAccounts(() -> {
            helper.assertValueEqual(runCommandAs(helper, a.player, "bank account create " + name), List.of(account + "created"), "created");
            helper.assertValueEqual(runCommandAs(helper, a.player, "bank account create " + name.toUpperCase(java.util.Locale.ROOT)),
                    List.of(account + "result.name_taken"), "names are unique, ignoring case");
            helper.assertValueEqual(runCommandAs(helper, b.player, "bank account join " + name), List.of(account + "not_invited"),
                    "joining needs an invitation");
            AccountId id = new AccountId(AccountRegistry.get(server).namedByName(name).orElseThrow());
            // The invite command needs the player online; test players aren't, so invite through the service.
            helper.assertValueEqual(CoreServices.ACCOUNTS.invite(server, id, b.player.getUUID(), AccountRole.MEMBER), BankResult.OK, "invited");
            helper.assertValueEqual(runCommandAs(helper, b.player, "bank account join " + name), List.of(account + "joined"), "joined");

            OwnBank.get(server).accountOf(id.value()).deposit(1000);
            a.setStock(Items.IRON_INGOT, 5);
            try {
                // A uses the shared account: a purchase is paid from it, and A's own money is untouched.
                helper.assertValueEqual(runCommandAs(helper, a.player, "bank account use " + name), List.of(account + "using"), "A uses it");
                TradeResult bought = MarketService.tryBuy(a.player, Items.IRON_INGOT, 1, ANY_PRICE);
                a.assertResult(bought, Result.OK);
                helper.assertValueEqual(CoreApi.money().balance(server, id).orElseThrow(), 1000 - bought.total(), "the account paid");
                helper.assertValueEqual(a.balance(), 0, "not A");
                // B didn't choose it: B's withdrawal comes from B's own account, which is empty.
                b.assertRejectedUnchanged(BankService.Result.NO_FUNDS, () -> BankService.withdraw(b.player, 1));
                runCommandAs(helper, b.player, "bank account use " + name);
                BankService.Outcome withdrawn = BankService.withdraw(b.player, 8);
                helper.assertTrue(withdrawn.ok(), "B can withdraw from it once chosen: " + withdrawn);

                // A deposit-only member can't spend from it, so it stops being their active account.
                helper.assertValueEqual(CoreApi.accounts().setMember(server, id, b.player.getUUID(), AccountRole.DEPOSIT_ONLY), BankResult.OK,
                        "B is deposit-only now");
                helper.assertValueEqual(CoreApi.accounts().active(b.player), CoreApi.accounts().personal(b.player.getUUID()),
                        "B is back on their own account");
                b.assertResult(MarketService.tryBuy(b.player, Items.IRON_INGOT, 1, ANY_PRICE, id), Result.NOT_ALLOWED);
            } finally {
                CoreApi.accounts().setActive(a.player, CoreApi.accounts().personal(a.player.getUUID()));
                CoreApi.accounts().setActive(b.player, CoreApi.accounts().personal(b.player.getUUID()));
            }

            helper.assertValueEqual(runCommandAs(helper, a.player, "bank account leave " + name), List.of(account + "left"), "A leaves");
            helper.assertValueEqual(CoreApi.accounts().role(server, id, b.player.getUUID()), Optional.of(AccountRole.OWNER),
                    "B, the longest-standing member left, owns it");
            helper.assertValueEqual(runCommandAs(helper, b.player, "bank account delete " + name), List.of(account + "result.not_empty"),
                    "it can't be deleted while it holds money");
            long left = CoreApi.money().balance(server, id).orElseThrow();
            CoreApi.money().charge(server, id, left, Reason.of(rl("cleanup")));
            helper.assertValueEqual(runCommandAs(helper, b.player, "bank account delete " + name), List.of(account + "deleted"), "deleted");
            helper.assertTrue(CoreApi.accounts().get(server, id).isEmpty(), "nothing is left behind");
        });
        helper.succeed();
    }

    /** The first translation key in a message: failures arrive wrapped in a red, empty component. */
    private static Optional<String> translationKey(Component message) {
        if (message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents key) {
            return Optional.of(key.getKey());
        }
        return message.getSiblings().stream().map(MarketBankTests::translationKey).flatMap(Optional::stream).findFirst();
    }

    /** Runs a command as {@code player}, returning its messages as translation keys, so tests don't depend on wording. */
    private static List<String> runCommandAs(GameTestHelper helper, ServerPlayer player, String command) {
        List<String> output = new ArrayList<>();
        net.minecraft.commands.CommandSource capture = new net.minecraft.commands.CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                output.add(translationKey(message).orElse(message.getString()));
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        net.minecraft.server.MinecraftServer server = helper.getLevel().getServer();
        try {
            server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack().withSource(capture).withEntity(player));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new GameTestAssertException("/" + command + " was rejected: " + e.getMessage());
        }
        return output;
    }
}
