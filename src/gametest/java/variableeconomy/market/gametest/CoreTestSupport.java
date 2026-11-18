package variableeconomy.market.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import variableeconomy.core.CoreModule;
import variableeconomy.core.bank.Bank;
import variableeconomy.core.bank.WorldBank;
import variableeconomy.core.service.BankService;
import variableeconomy.core.service.CoreServices;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * What every module's GameTests share: the fixture (a fake player with an inventory and a bank account), the event
 * recorder, the checks each module runs on its own block and lang file, and small helpers. Tests run synchronously
 * inside one tick, so tests sharing the world's bank cannot interleave. Each module's tests are held under its own mod
 * id and run whenever that mod is loaded: {@code gradlew runGameTestServer} in a mod's repository runs them (an
 * add-on's with core). Each add-on's tests carry their own copy of this class, in their own package, since core's jar
 * holds no test code.
 */
public final class CoreTestSupport {
    private CoreTestSupport() {
    }

    /** The empty structure every test starts from; each module ships a copy under its own namespace. */
    public static final String EMPTY = "empty";

    // ---- Helpers -----------------------------------------------------------------------------------------------

    /**
     * Deletes a test's temp folder and everything in it. Never throws, so called from {@code finally} it can't hide
     * the test's own failure; what can't be deleted is left for the system to clean up.
     */
    public static void deleteTree(Path root) {
        try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException | RuntimeException e) {
                    CoreModule.LOGGER.warn("Could not delete test file {}: {}", path, e.toString());
                }
            }
        } catch (IOException | RuntimeException e) {
            CoreModule.LOGGER.warn("Could not clean up test folder {}: {}", root, e.toString());
        }
    }

    public static ResourceLocation key(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    public static ResourceLocation id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    /** An ID in a namespace no module uses, for test gate rules, reasons and contributors. */
    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("api_test", path);
    }

    /** What a 3x3 crafting grid makes, if anything. */
    public static Optional<ItemStack> craft(GameTestHelper helper, Item... grid) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Item item : grid) {
            stacks.add(new ItemStack(item));
        }
        CraftingInput input = CraftingInput.of(3, 3, stacks);
        return helper.getLevel().getServer().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .map(r -> r.value().assemble(input, helper.getLevel().registryAccess()));
    }

    public static com.google.gson.JsonElement number(double value) {
        return new com.google.gson.JsonPrimitive(value);
    }

    /** Runs a command at permission 4, the way an operator or the console would, and returns what it printed. */
    public static List<String> runCommand(GameTestHelper helper, String command) {
        List<String> output = new ArrayList<>();
        net.minecraft.commands.CommandSource capture = new net.minecraft.commands.CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                output.add(message.getString());
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
            server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack().withSource(capture).withPermission(4));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new GameTestAssertException("/" + command + " was rejected: " + e.getMessage());
        }
        return output;
    }

    /**
     * Runs a command as {@code player}, at their permission level, and returns its messages as translation keys (the
     * first key in each), so tests don't depend on wording.
     */
    public static List<String> commandKeysAs(GameTestHelper helper, ServerPlayer player, String command) {
        List<String> output = new ArrayList<>();
        net.minecraft.commands.CommandSource capture = new net.minecraft.commands.CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                output.add(firstKey(message).orElse(message.getString()));
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
            server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack().withSource(capture)
                    .withEntity(player).withPermission(0));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new GameTestAssertException("/" + command + " was rejected: " + e.getMessage());
        }
        return output;
    }

    /** The first translation key in a message: failures arrive wrapped in a red, empty component. */
    private static Optional<String> firstKey(Component message) {
        if (message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents key) {
            return Optional.of(key.getKey());
        }
        return message.getSiblings().stream().map(CoreTestSupport::firstKey).flatMap(Optional::stream).findFirst();
    }

    /** Whether {@code /command} parses at permission 4: the command exists, with every literal in it. */
    public static boolean commandExists(GameTestHelper helper, String command) {
        net.minecraft.server.MinecraftServer server = helper.getLevel().getServer();
        var parsed = server.getCommands().getDispatcher().parse(command, server.createCommandSourceStack().withPermission(4));
        return parsed.getExceptions().isEmpty() && parsed.getReader().getRemainingLength() == 0
                && !parsed.getContext().getNodes().isEmpty();
    }

    /** Ends a test that needs {@code mod} as passed when it isn't installed, noting it in the log. */
    public static boolean skippedWithout(GameTestHelper helper, String mod) {
        if (ModList.get().isLoaded(mod)) {
            return false;
        }
        CoreModule.LOGGER.info("GameTest skipped: it needs {}, which isn't installed", mod);
        helper.succeed();
        return true;
    }

    // ---- Each module's block -----------------------------------------------------------------------------------

    /** A module's block: its block, its item, the menu it opens there, the menu its command opens, and its tool. */
    public record EconomyBlock(String id, Block block, Item item, BiFunction<ServerPlayer, BlockPos, AbstractContainerMenu> atBlock,
                               Supplier<AbstractContainerMenu> byCommand, TagKey<Block> tool) {
    }

    /** The block's screen is valid next to it, closes 20 blocks away or when the block breaks, and needs its own block. */
    public static void assertScreenFollowsTheBlock(GameTestHelper helper, EconomyBlock block) {
        CoreFixture f = new CoreFixture(helper);
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        helper.setBlock(BlockPos.ZERO, block.block().defaultBlockState());
        AbstractContainerMenu menu = block.atBlock().apply(f.player, pos);
        f.player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 3.5);
        helper.assertTrue(menu.stillValid(f.player), block.id() + ": valid next to the block");
        f.player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 20.5);
        helper.assertTrue(!menu.stillValid(f.player), block.id() + ": closes 20 blocks away");
        f.player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 3.5);
        helper.setBlock(BlockPos.ZERO, Blocks.AIR);
        helper.assertTrue(!menu.stillValid(f.player), block.id() + ": closes when the block is broken");
        helper.assertTrue(block.byCommand().get().stillValid(f.player), block.id() + ": a command-opened screen is valid in the default mode");
        // Another block's menu doesn't count as this one's.
        helper.setBlock(BlockPos.ZERO, Blocks.STONE);
        helper.assertTrue(!block.atBlock().apply(f.player, pos).stillValid(f.player), block.id() + ": needs its own block");
    }

    /** The block has a recipe from Bits, named after it in its own namespace, and can face any way. */
    public static void assertRecipeAndFacing(GameTestHelper helper, EconomyBlock block) {
        var recipes = helper.getLevel().getServer().getRecipeManager();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block.block());
        helper.assertTrue(recipes.byKey(id.withSuffix("_from_bits")).isPresent(), block.id() + ": the Bit recipe loads");
        helper.setBlock(BlockPos.ZERO, block.block().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        helper.assertBlockProperty(BlockPos.ZERO, HorizontalDirectionalBlock.FACING, Direction.EAST);
    }

    /** The block is a full solid block that breaks by hand, faster with its tool, and drops itself. */
    public static void assertSolidAndDropsItself(GameTestHelper helper, EconomyBlock block) {
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        BlockState state = block.block().defaultBlockState();
        helper.setBlock(BlockPos.ZERO, state);
        helper.assertTrue(state.isCollisionShapeFullBlock(helper.getLevel(), pos), block.id() + ": a full block");
        helper.assertTrue(state.canOcclude(), block.id() + ": solid");
        helper.assertTrue(!state.requiresCorrectToolForDrops(), block.id() + ": breaks by hand");
        helper.assertTrue(state.is(block.tool()), block.id() + ": its tool breaks it faster");
        List<ItemStack> drops = Block.getDrops(state, helper.getLevel(), pos, null);
        helper.assertTrue(drops.size() == 1 && drops.get(0).is(block.item()), block.id() + ": broken by hand, it drops itself, got " + drops);
    }

    // ---- Each module's ids and lang file -----------------------------------------------------------------------

    /**
     * Every item and block in {@code modId}'s namespace has an English name in that mod's own lang file, every key in
     * that file is in its namespace, and each of {@code keys} is there too (keys the code builds at run time).
     */
    public static void assertOwnNamespace(GameTestHelper helper, String modId, List<String> keys) {
        com.google.gson.JsonObject lang = lang(modId);
        Set<String> names = new HashSet<>(lang.keySet());
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            if (id.getNamespace().equals(modId)) {
                Item item = BuiltInRegistries.ITEM.get(id);
                helper.assertTrue(names.contains(item.getDescriptionId()), "no English name for " + item.getDescriptionId());
            }
        }
        for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
            if (id.getNamespace().equals(modId)) {
                String descriptionId = BuiltInRegistries.BLOCK.get(id).getDescriptionId();
                helper.assertTrue(names.contains(descriptionId), "no English name for " + descriptionId);
            }
        }
        for (String name : names) {
            String[] parts = name.split("\\.");
            helper.assertTrue(parts.length > 2 && parts[1].equals(modId), modId + "'s lang file has a key outside its namespace: " + name);
        }
        for (String name : keys) {
            helper.assertTrue(names.contains(name), modId + "'s lang file is missing " + name);
        }
    }

    /** {@code modId}'s English lang file, read from its own jar. */
    public static com.google.gson.JsonObject lang(String modId) {
        Path file = ModList.get().getModFileById(modId).getFile().findResource("assets", modId, "lang", "en_us.json");
        try (InputStream in = Files.newInputStream(file)) {
            return com.google.gson.JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new GameTestAssertException("can't read " + modId + "'s lang file: " + e);
        }
    }

    // ---- Event recorder ----------------------------------------------------------------------------------------

    /**
     * Records events while a test asks it to. Each type is put on the event bus once per server run (listeners can't
     * be taken off a lambda again), so it stays and records only while {@link #eventsDuring} runs.
     */
    private static final Set<Class<?>> listened = new HashSet<>();
    private static boolean recording;
    /** While true, the recorder throws after recording, as a broken add-on would. */
    public static boolean throwing;
    private static final List<Event> recorded = new ArrayList<>();

    /** Starts listening for these event types, if not already. */
    @SafeVarargs
    public static void listenFor(Class<? extends Event>... types) {
        for (Class<? extends Event> type : types) {
            if (listened.add(type)) {
                on(type);
            }
        }
    }

    private static <T extends Event> void on(Class<T> type) {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, type, event -> {
            if (recording) {
                recorded.add(event);
                if (throwing) {
                    throw new IllegalStateException("A broken add-on listener (GameTest)");
                }
            }
        });
    }

    /** The events of the types listened for that were posted while {@code body} runs, in order. */
    public static List<Event> recordEvents(Runnable body) {
        recorded.clear();
        recording = true;
        try {
            body.run();
        } finally {
            recording = false;
        }
        List<Event> events = List.copyOf(recorded);
        recorded.clear();
        return events;
    }

    public static <T extends Event> List<T> only(List<Event> events, Class<T> type) {
        return events.stream().filter(type::isInstance).map(type::cast).toList();
    }

    // ---- Settings ----------------------------------------------------------------------------------------------

    /**
     * Runs {@code body} with extra and shared accounts on, as an admin would turn them on, then puts the settings back.
     * The GameTest server loads the config, so the values can be set.
     */
    public static void withAccounts(Runnable body) {
        boolean extra = variableeconomy.core.CoreConfig.EXTRA_ENABLED.get();
        boolean shared = variableeconomy.core.CoreConfig.SHARED_ENABLED.get();
        variableeconomy.core.CoreConfig.EXTRA_ENABLED.set(true);
        variableeconomy.core.CoreConfig.SHARED_ENABLED.set(true);
        try {
            body.run();
        } finally {
            variableeconomy.core.CoreConfig.EXTRA_ENABLED.set(extra);
            variableeconomy.core.CoreConfig.SHARED_ENABLED.set(shared);
        }
    }

    /** A fresh account name, unique among the persistent test world's accounts. */
    public static String accountName(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    // ---- Fixture -----------------------------------------------------------------------------------------------

    /** A fresh player with an empty inventory and a new, empty account in the world's bank. */
    public static class CoreFixture {
        public final GameTestHelper helper;
        public final ServerPlayer player;

        public CoreFixture(GameTestHelper helper) {
            this.helper = helper;
            this.player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "market-test"), ClientInformation.createDefault());
            // Core sees the player, as it does when a real one joins: their personal account exists from now on.
            CoreServices.ACCOUNTS.seen(player);
            int starting = balance();
            if (starting > 0) {
                account().deduct(starting);
            }
        }

        public Bank bank() {
            return WorldBank.of(player.server);
        }

        public Bank.Account account() {
            return bank().account(player.getUUID()).orElseThrow();
        }

        /** Test balances stay well within an int. */
        public int balance() {
            return Math.toIntExact(account().balance());
        }

        public void deposit(int bits) {
            account().deposit(bits);
        }

        public void give(ItemStack stack) {
            NonNullList<ItemStack> items = player.getInventory().items;
            for (int slot = 0; slot < items.size(); slot++) {
                if (items.get(slot).isEmpty()) {
                    items.set(slot, stack);
                    return;
                }
            }
            throw new IllegalStateException("Test inventory is full");
        }

        public void giveMany(Item item, int count) {
            for (int left = count; left > 0; left -= 64) {
                give(new ItemStack(item, Math.min(64, left)));
            }
        }

        public void clearInventory() {
            player.getInventory().items.clear();
        }

        public long count(Item item) {
            return player.getInventory().items.stream()
                    .filter(s -> ItemStack.isSameItemSameComponents(s, new ItemStack(item)))
                    .mapToLong(ItemStack::getCount).sum();
        }

        /** A bank request that must be refused and change nothing: balance, main inventory or offhand. */
        public void assertRejectedUnchanged(BankService.Result expected, Supplier<BankService.Outcome> request) {
            List<ItemStack> before = new ArrayList<>();
            player.getInventory().items.forEach(s -> before.add(s.copy()));
            ItemStack offhandBefore = player.getInventory().offhand.get(0).copy();
            int balanceBefore = balance();
            BankService.Outcome outcome = request.get();
            helper.assertValueEqual(outcome.result(), expected, "bank result");
            helper.assertValueEqual(outcome.amount(), 0L, "nothing moved");
            helper.assertValueEqual(balance(), balanceBefore, "balance after " + expected);
            NonNullList<ItemStack> after = player.getInventory().items;
            for (int slot = 0; slot < after.size(); slot++) {
                helper.assertTrue(ItemStack.matches(after.get(slot), before.get(slot)), "inventory slot " + slot + " changed after " + expected);
            }
            helper.assertTrue(ItemStack.matches(player.getInventory().offhand.get(0), offhandBefore), "offhand changed after " + expected);
        }
    }
}
