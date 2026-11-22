package variableeconomy.market.client;

import variableeconomy.core.api.CoreApi;
import variableeconomy.market.menu.MarketMenu;
import variableeconomy.market.network.MarketPayloads;
import variableeconomy.market.service.MarketService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static variableeconomy.market.client.MarketScreen.*;

/**
 * The Selling box: the player marks items in their inventory, sees what they're worth, and sells them all at once.
 * <p>
 * The box is only a marking on the client: the items stay in the player's inventory until the sale, so closing the
 * screen, dying or a full inventory can't lose anything. The inventory grid shows marked items as moved into the box.
 * When the inventory changes, marks shrink to what is still there. On Confirm, each item in the box is sold as its own
 * sale at the current price, as long as that pays at least what the box shows; an item that fails stays in the box
 * with the reason in its tooltip.
 * <p>
 * The box sits below the list when the window has room for it ({@link MarketScreen#boxInline}); otherwise it is the
 * third view of the list ({@link MarketScreen.Mode#SELL}).
 */
final class SellBox extends ScreenTab {
    static final int SLOT = 18;
    static final int BOX_COLUMNS = 6;
    static final int BOX_ROWS = 3;
    /** One box slot per item. */
    static final int BOX_SLOTS = BOX_COLUMNS * BOX_ROWS;
    static final int INVENTORY_COLUMNS = 9;
    static final int GAP = 8;
    static final int LABEL_HEIGHT = 11;
    /** The gap between the inventory's three rows and the hotbar, as in vanilla screens. */
    static final int HOTBAR_GAP = 4;
    static final int BOX_WIDTH = BOX_COLUMNS * SLOT;
    static final int INVENTORY_X = BOX_WIDTH + GAP;
    /** The whole area: the box with its buttons on the left, the inventory on the right. */
    static final int WIDTH = INVENTORY_X + INVENTORY_COLUMNS * SLOT;
    static final int HEIGHT = LABEL_HEIGHT + 4 * SLOT + HOTBAR_GAP;
    private static final int BUTTON_HEIGHT = 16;
    private static final int CLEAR_WIDTH = 36;

    private static final int SLOT_BACKGROUND = 0x50000000;
    private static final int SLOT_BORDER = 0x30FFFFFF;
    private static final int DIMMED = 0xA0202024;
    private static final int PROBLEM = 0x60FF4040;

    /** How many of each main-inventory slot are in the box, and which item they were when marked. */
    private final int[] marked = new int[MarketMenu.INVENTORY_SLOTS];
    private final Item[] markedItem = new Item[MarketMenu.INVENTORY_SLOTS];
    /** Why each item that stayed in the box after the last sale wasn't sold. */
    private final Map<ResourceLocation, MarketPayloads.SellOutcomeLine> problems = new HashMap<>();
    /** Set while a sale is on its way, so Confirm can't send it twice. */
    private boolean waiting;

    private Button clearButton;
    private Button confirmButton;
    /** Where the area is drawn, relative to the screen; set by the screen's layout. */
    private int originX;
    private int originY;
    private boolean shown;

    SellBox(MarketScreen screen) {
        super(screen);
    }

    /** One item in the box: how many, priced the way the server will price the sale. */
    record Entry(ResourceLocation item, Item type, int quantity) {
    }

    // ---- Layout and widgets --------------------------------------------------------------------------------

    /** Places the area at {@code x, y} (relative to the screen) and makes its buttons. */
    void init(int left, int top, int x, int y) {
        originX = x;
        originY = y;
        int buttonsY = top + y + LABEL_HEIGHT + BOX_ROWS * SLOT + 2;
        clearButton = screen.add(Button.builder(Component.translatable("screen.variable_economy_market.sell_box.clear"), b -> clear())
                .bounds(left + x, buttonsY, CLEAR_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("screen.variable_economy_market.sell_box.clear.tooltip")))
                .build());
        confirmButton = screen.add(Button.builder(Component.empty(), b -> confirm())
                .bounds(left + x + CLEAR_WIDTH + 2, buttonsY, BOX_WIDTH - CLEAR_WIDTH - 2, BUTTON_HEIGHT)
                .build());
        refreshButtons();
    }

    /** Whether the area is on screen; its buttons show only then. */
    void setShown(boolean shown) {
        this.shown = shown;
        if (clearButton != null) {
            clearButton.visible = shown;
            confirmButton.visible = shown;
        }
    }

    boolean shown() {
        return shown;
    }

    // ---- The box -------------------------------------------------------------------------------------------

    /** The items in the box, in the order they were first put in (by slot), one entry per item. */
    List<Entry> entries() {
        Map<ResourceLocation, Entry> byItem = new LinkedHashMap<>();
        for (int i = 0; i < marked.length; i++) {
            if (marked[i] > 0) {
                Item item = markedItem[i];
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                int count = marked[i];
                byItem.merge(id, new Entry(id, item, count), (a, b) -> new Entry(a.item(), a.type(), a.quantity() + b.quantity()));
            }
        }
        return new ArrayList<>(byItem.values());
    }

    private int markedOf(Item item) {
        int total = 0;
        for (int i = 0; i < marked.length; i++) {
            if (marked[i] > 0 && markedItem[i] == item) {
                total += marked[i];
            }
        }
        return total;
    }

    /** The total the market would pay for everything in the box right now; empty if an item can't be priced. */
    private long total() {
        long total = 0;
        for (Entry entry : entries()) {
            MarketPayloads.Row row = ClientMarketState.rows().get(entry.item());
            if (row != null && row.canSell()) {
                total = saturatedAdd(total, row.sellTotal(entry.quantity()));
            }
        }
        return total;
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return ((a ^ sum) & (b ^ sum)) < 0 ? Long.MAX_VALUE : sum;
    }

    /**
     * Brings the marks in line with the inventory: a slot that now holds something else, or fewer, is unmarked or
     * marks fewer; an item that can no longer be sold leaves the box. Called every tick.
     */
    void sync() {
        List<ItemStack> items = inventory();
        for (int i = 0; i < marked.length; i++) {
            if (marked[i] == 0) {
                continue;
            }
            ItemStack stack = i < items.size() ? items.get(i) : ItemStack.EMPTY;
            if (stack.isEmpty() || stack.getItem() != markedItem[i] || reason(stack) != null) {
                marked[i] = 0;
                markedItem[i] = null;
            } else {
                marked[i] = Math.min(marked[i], stack.getCount());
            }
        }
        problems.keySet().removeIf(id -> markedOf(BuiltInRegistries.ITEM.get(id)) == 0);
        refreshButtons();
    }

    private List<ItemStack> inventory() {
        return screen.minecraft().player == null ? List.of() : screen.minecraft().player.getInventory().items;
    }

    /** Why a stack can't go in the box, or null if it can. */
    @Nullable
    private static Component reason(ItemStack stack) {
        Item item = stack.getItem();
        if (CoreApi.isCoin(item)) {
            return Component.translatable("screen.variable_economy_market.sell_box.coin");
        }
        MarketPayloads.Row row = ClientMarketState.rows().get(BuiltInRegistries.ITEM.getKey(item));
        if (row == null || !row.canSell()) {
            return Component.translatable("screen.variable_economy_market.sell_box.not_bought");
        }
        if (!CoreApi.isPlain(stack, item)) {
            return Component.translatable("screen.variable_economy_market.sell_box.not_plain");
        }
        return null;
    }

    /**
     * Puts {@code amount} more of slot {@code slot} in the box (up to what it holds), without going over the order
     * limit for that item. Returns how many went in.
     */
    private int mark(int slot, int amount) {
        ItemStack stack = inventory().get(slot);
        if (stack.isEmpty() || reason(stack) != null) {
            return 0;
        }
        Item item = stack.getItem();
        int room = ClientMarketState.maxQuantity() - markedOf(item);
        int add = Math.min(Math.min(amount, stack.getCount() - marked[slot]), room);
        if (add <= 0) {
            if (room <= 0) {
                ClientMarketState.note(Component.translatable("screen.variable_economy_market.sell_box.capped",
                        PriceFormat.amount(ClientMarketState.maxQuantity())), true);
            }
            return 0;
        }
        marked[slot] += add;
        markedItem[slot] = item;
        problems.remove(BuiltInRegistries.ITEM.getKey(item));
        return add;
    }

    /** Takes every marked copy of {@code item} out of the box. */
    private void unmark(Item item) {
        for (int i = 0; i < marked.length; i++) {
            if (markedItem[i] == item) {
                marked[i] = 0;
                markedItem[i] = null;
            }
        }
        problems.remove(BuiltInRegistries.ITEM.getKey(item));
    }

    /** Takes one marked copy of {@code item} out of the box, from the last slot it was marked in. */
    private void unmarkOne(Item item) {
        for (int i = marked.length - 1; i >= 0; i--) {
            if (marked[i] > 0 && markedItem[i] == item) {
                if (--marked[i] == 0) {
                    markedItem[i] = null;
                }
                return;
            }
        }
    }

    void clear() {
        for (int i = 0; i < marked.length; i++) {
            marked[i] = 0;
            markedItem[i] = null;
        }
        problems.clear();
        refreshButtons();
    }

    /** Sells everything in the box: one line per item, each with the total the box shows for it. */
    private void confirm() {
        List<MarketPayloads.SellLine> lines = new ArrayList<>();
        for (Entry entry : entries()) {
            MarketPayloads.Row row = ClientMarketState.rows().get(entry.item());
            if (row != null && row.canSell()) {
                lines.add(new MarketPayloads.SellLine(entry.item(), entry.quantity(), row.sellTotal(entry.quantity())));
            }
        }
        if (lines.isEmpty() || waiting) {
            return;
        }
        problems.clear();
        waiting = true;
        PacketDistributor.sendToServer(new MarketPayloads.SellBoxRequest(lines));
        refreshButtons();
    }

    /** The server's answer: sold items leave the box, the others stay with their reason. */
    void applyOutcome(MarketPayloads.SellBoxOutcome outcome) {
        waiting = false;
        for (MarketPayloads.SellOutcomeLine line : outcome.lines()) {
            Item item = BuiltInRegistries.ITEM.get(line.item());
            if (line.result() == MarketService.Result.OK) {
                unmark(item);
            } else {
                problems.put(line.item(), line);
            }
        }
        refreshButtons();
    }

    private void refreshButtons() {
        if (confirmButton == null) {
            return;
        }
        boolean empty = entries().isEmpty();
        clearButton.active = !empty;
        long total = total();
        Component label = Component.translatable("screen.variable_economy_market.sell_for", PriceFormat.amount(total));
        if (screen.font().width(label) > confirmButton.getWidth() - 6) {
            label = Component.translatable("screen.variable_economy_market.sell_for", PriceFormat.compact(total));
        }
        confirmButton.setMessage(empty ? Component.translatable("screen.variable_economy_market.sell") : label);
        confirmButton.setTooltip(Tooltip.create(empty
                ? Component.translatable("screen.variable_economy_market.sell_box.confirm.empty")
                : Component.translatable("screen.variable_economy_market.sell_box.confirm.tooltip",
                        PriceFormat.money(PriceFormat.amount(total)))));
        confirmButton.active = !empty && !waiting;
    }

    // ---- Input ---------------------------------------------------------------------------------------------

    /**
     * Click: the whole stack goes in. Right-click: one more. Shift-click: every copy of that item. In the box,
     * click takes the item out, right-click takes one out. Returns whether the click was on the area's grids.
     */
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!shown || button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return false;
        }
        boolean right = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
        int slot = inventorySlotAt(mouseX, mouseY);
        if (slot >= 0 && slot < inventory().size()) {
            ItemStack stack = inventory().get(slot);
            if (Screen.hasShiftDown()) {
                List<ItemStack> items = inventory();
                for (int i = 0; i < items.size() && i < marked.length; i++) {
                    if (!items.get(i).isEmpty() && items.get(i).getItem() == stack.getItem()) {
                        mark(i, Integer.MAX_VALUE);
                    }
                }
            } else {
                mark(slot, right ? 1 : Integer.MAX_VALUE);
            }
            refreshButtons();
            return true;
        }
        int index = boxSlotAt(mouseX, mouseY);
        if (index >= 0) {
            List<Entry> entries = entries();
            if (index < entries.size()) {
                if (right) {
                    unmarkOne(entries.get(index).type());
                } else {
                    unmark(entries.get(index).type());
                }
                refreshButtons();
            }
            return true;
        }
        return false;
    }

    /** The inventory slot under the mouse (0–8 the hotbar), or -1. */
    private int inventorySlotAt(double mouseX, double mouseY) {
        double x = mouseX - screen.leftPos() - originX - INVENTORY_X;
        double y = mouseY - screen.topPos() - originY - LABEL_HEIGHT;
        if (x < 0 || x >= INVENTORY_COLUMNS * SLOT || y < 0) {
            return -1;
        }
        int column = (int) (x / SLOT);
        if (y < 3 * SLOT) {
            return 9 + (int) (y / SLOT) * INVENTORY_COLUMNS + column;
        }
        double hotbarY = y - 3 * SLOT - HOTBAR_GAP;
        return hotbarY >= 0 && hotbarY < SLOT ? column : -1;
    }

    /** The box slot under the mouse, or -1. */
    private int boxSlotAt(double mouseX, double mouseY) {
        double x = mouseX - screen.leftPos() - originX;
        double y = mouseY - screen.topPos() - originY - LABEL_HEIGHT;
        if (x < 0 || x >= BOX_WIDTH || y < 0 || y >= BOX_ROWS * SLOT) {
            return -1;
        }
        return (int) (y / SLOT) * BOX_COLUMNS + (int) (x / SLOT);
    }

    /** Where inventory slot {@code slot} is drawn, relative to the area. */
    private static int slotX(int slot) {
        return INVENTORY_X + slot % INVENTORY_COLUMNS * SLOT;
    }

    private static int slotY(int slot) {
        return slot < INVENTORY_COLUMNS ? LABEL_HEIGHT + 3 * SLOT + HOTBAR_GAP : LABEL_HEIGHT + (slot / INVENTORY_COLUMNS - 1) * SLOT;
    }

    // ---- Rendering -----------------------------------------------------------------------------------------

    /** The box, its labels and the inventory, with the area's top-left at {@code x, y} on screen. */
    void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!shown) {
            return;
        }
        int x = screen.leftPos() + originX;
        int y = screen.topPos() + originY;
        var font = screen.font();
        graphics.drawString(font, Component.translatable("screen.variable_economy_market.sell_box.title"), x, y + 1, TEXT_DIM, false);
        graphics.drawString(font, Component.translatable("screen.variable_economy_market.sell_box.inventory"), x + INVENTORY_X, y + 1, TEXT_DIM, false);

        List<Entry> entries = entries();
        int hoveredBox = boxSlotAt(mouseX, mouseY);
        for (int i = 0; i < BOX_SLOTS; i++) {
            int sx = x + i % BOX_COLUMNS * SLOT;
            int sy = y + LABEL_HEIGHT + i / BOX_COLUMNS * SLOT;
            slotBackground(graphics, sx, sy);
            if (i < entries.size()) {
                Entry entry = entries.get(i);
                ItemStack stack = new ItemStack(entry.type());
                graphics.renderItem(stack, sx + 1, sy + 1);
                graphics.renderItemDecorations(font, stack, sx + 1, sy + 1, countText(entry.quantity()));
                if (problems.containsKey(entry.item())) {
                    overlay(graphics, sx, sy, PROBLEM);
                }
                if (i == hoveredBox) {
                    AbstractContainerScreen.renderSlotHighlight(graphics, sx + 1, sy + 1, 0);
                }
            }
        }
        if (entries.isEmpty()) {
            List<FormattedCharSequence> lines = font.split(Component.translatable("screen.variable_economy_market.sell_box.hint"), BOX_WIDTH - 8);
            int top = y + LABEL_HEIGHT + (BOX_ROWS * SLOT - lines.size() * 10) / 2;
            for (int i = 0; i < lines.size(); i++) {
                graphics.drawCenteredString(font, lines.get(i), x + BOX_WIDTH / 2, top + i * 10, TEXT_DIM);
            }
        }

        List<ItemStack> items = inventory();
        int hoveredSlot = inventorySlotAt(mouseX, mouseY);
        for (int slot = 0; slot < MarketMenu.INVENTORY_SLOTS && slot < items.size(); slot++) {
            int sx = x + slotX(slot);
            int sy = y + slotY(slot);
            slotBackground(graphics, sx, sy);
            ItemStack stack = items.get(slot);
            int left = stack.getCount() - marked[slot];
            if (!stack.isEmpty() && left > 0) {
                graphics.renderItem(stack, sx + 1, sy + 1);
                graphics.renderItemDecorations(font, stack, sx + 1, sy + 1, left == stack.getCount() ? null : String.valueOf(left));
                if (reason(stack) != null) {
                    overlay(graphics, sx, sy, DIMMED);
                }
            }
            if (slot == hoveredSlot) {
                AbstractContainerScreen.renderSlotHighlight(graphics, sx + 1, sy + 1, 0);
            }
        }
    }

    /** A count that fits a slot: "640", "1.2k". */
    private static String countText(int count) {
        return count < 1000 ? String.valueOf(count) : PriceFormat.compact(count);
    }

    private static void slotBackground(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + SLOT, y + SLOT, SLOT_BORDER);
        graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, SLOT_BACKGROUND);
    }

    /** A tint over a slot's item; drawn above the item, which renders at a raised depth. */
    private static void overlay(GuiGraphics graphics, int x, int y, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        graphics.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, color);
        graphics.pose().popPose();
    }

    /** The tooltip for an inventory or box slot under the mouse; returns whether it drew one. */
    boolean renderSlotTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!shown) {
            return false;
        }
        int slot = inventorySlotAt(mouseX, mouseY);
        if (slot >= 0 && slot < inventory().size()) {
            ItemStack stack = inventory().get(slot);
            if (stack.isEmpty()) {
                return false;
            }
            List<Component> lines = new ArrayList<>(AbstractContainerScreen.getTooltipFromItem(screen.minecraft(), stack));
            Component reason = reason(stack);
            if (reason != null) {
                lines.add(reason.copy().withColor(TEXT_DIM));
            } else {
                MarketPayloads.Row row = ClientMarketState.rows().get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
                lines.add(Component.translatable("screen.variable_economy_market.tooltip.sell",
                        PriceFormat.money(PriceFormat.unitPrice(row.exactSellPrice(), RoundingMode.FLOOR))).withColor(TEXT_GOOD));
                lines.add(Component.translatable("screen.variable_economy_market.sell_box.add_hint").withColor(TEXT_DIM));
            }
            graphics.renderTooltip(screen.font(), lines, Optional.empty(), mouseX, mouseY);
            return true;
        }
        int index = boxSlotAt(mouseX, mouseY);
        List<Entry> entries = entries();
        if (index >= 0 && index < entries.size()) {
            Entry entry = entries.get(index);
            List<Component> lines = new ArrayList<>();
            lines.add(new ItemStack(entry.type()).getHoverName());
            MarketPayloads.Row row = ClientMarketState.rows().get(entry.item());
            if (row != null && row.canSell()) {
                lines.add(Component.translatable("screen.variable_economy_market.sell_box.entry", PriceFormat.amount(entry.quantity()),
                        PriceFormat.money(PriceFormat.amount(row.sellTotal(entry.quantity())))).withColor(TEXT_GOOD));
            }
            MarketPayloads.SellOutcomeLine problem = problems.get(entry.item());
            if (problem != null) {
                lines.add(problemText(problem).copy().withColor(TEXT_BAD));
            }
            lines.add(Component.translatable("screen.variable_economy_market.sell_box.remove_hint").withColor(TEXT_DIM));
            graphics.renderTooltip(screen.font(), lines, Optional.empty(), mouseX, mouseY);
            return true;
        }
        return false;
    }

    private static Component problemText(MarketPayloads.SellOutcomeLine problem) {
        if (problem.result() == MarketService.Result.PRICE_CHANGED) {
            return Component.translatable("screen.variable_economy_market.sell_box.price_dropped",
                    PriceFormat.money(PriceFormat.amount(problem.total())));
        }
        return Component.translatable("screen.variable_economy_market.result." + problem.result().name().toLowerCase(Locale.ROOT));
    }

    // ---- As the list's third view (when the window is too small to show the box below the list) ------------

    @Override
    int listSize() {
        return 0;
    }

    @Override
    void renderList(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        // The area itself is drawn by the screen wherever it is placed.
    }

    @Override
    void renderTooltip(GuiGraphics graphics, int index, int mouseX, int mouseY) {
        if (!renderSlotTooltip(graphics, mouseX, mouseY)) {
            super.renderTooltip(graphics, index, mouseX, mouseY);
        }
    }
}
