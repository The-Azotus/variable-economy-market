package variableeconomy.market.menu;

import variableeconomy.market.MarketSettings;
import variableeconomy.market.MarketModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import variableeconomy.market.block.MarketBlocks;

/**
 * The market screen's server-side half. Trades travel in the market's own packets. It marks which players have the
 * market open, so requests from anyone else are ignored and updates go only to viewers. Opened from a Market Terminal
 * it stays valid only while the player is near that block; opened by command, only while the access mode allows that
 * player to use commands.
 * <p>
 * It holds the player's 36 main-inventory slots (hotbar first) only so that vanilla keeps the client's copy of the
 * inventory in sync while the screen is open; the Selling box draws them. The slots can't be clicked: nothing can be
 * picked up from or put into them, and {@link #clicked} ignores every click, so a forged click changes nothing.
 */
public final class MarketMenu extends AbstractContainerMenu {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MarketModule.MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<MarketMenu>> TYPE =
            MENUS.register("market", () -> IMenuTypeExtension.create((id, inventory, data) -> new MarketMenu(id, inventory, ContainerLevelAccess.NULL)));
    /** How many slots the menu has: the player's main inventory. */
    public static final int INVENTORY_SLOTS = 36;

    /** The terminal this was opened from, or {@link ContainerLevelAccess#NULL} if opened by command (or on the client). */
    private final ContainerLevelAccess block;

    private MarketMenu(int containerId, Container inventory, ContainerLevelAccess block) {
        super(TYPE.get(), containerId);
        this.block = block;
        for (int i = 0; i < INVENTORY_SLOTS; i++) {
            addSlot(new ShownSlot(inventory, i));
        }
    }

    /**
     * A main-inventory slot that is only shown: the screen draws it in its own place, and nothing can be moved in or
     * out of it. Inactive, so vanilla's screen code neither draws it nor treats it as hovered.
     */
    private static final class ShownSlot extends Slot {
        ShownSlot(Container container, int index) {
            super(container, index, 0, 0);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean isActive() {
            return false;
        }
    }

    /** Opens the market by command. */
    public static void open(ServerPlayer player) {
        open(player, ContainerLevelAccess.NULL);
    }

    /** Opens the market from the terminal at {@code pos}. */
    public static void openAtTerminal(ServerPlayer player, BlockPos pos) {
        open(player, ContainerLevelAccess.create(player.level(), pos));
    }

    private static void open(ServerPlayer player, ContainerLevelAccess block) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new MarketMenu(id, inventory, block),
                Component.translatable("screen.variable_economy_market.market")));
    }

    /** A server-side menu for the terminal at {@code pos}, without opening a screen. For tests. */
    public static MarketMenu forTerminal(ServerPlayer player, BlockPos pos) {
        return new MarketMenu(0, player.getInventory(), ContainerLevelAccess.create(player.level(), pos));
    }

    /** A server-side menu as {@code /market} opens it, without opening a screen. For tests. */
    public static MarketMenu forCommand() {
        return new MarketMenu(0, new SimpleContainer(INVENTORY_SLOTS), ContainerLevelAccess.NULL);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /** Every click is ignored: the slots are only shown, and the Selling box is the client's marking, not a container. */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        if (block != ContainerLevelAccess.NULL) {
            // Vanilla's container rule: the block must still be there and the player within 8 blocks of it.
            return stillValid(block, player, MarketBlocks.MARKET_TERMINAL.get());
        }
        return MarketSettings.commandsAllowed(player);
    }
}
