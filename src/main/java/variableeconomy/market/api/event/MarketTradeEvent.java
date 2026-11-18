package variableeconomy.market.api.event;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.Event;
import variableeconomy.core.api.AccountId;

/**
 * A player sold items to the market or bought items from it. Posted after every market trade, whether it came from
 * the market screen, a command or the API, once the items, stock and money have all moved. The account's
 * {@link variableeconomy.core.api.event.BalanceChangedEvent} follows at the end of the current or next tick.
 * <p>
 * Posted by Variable Economy; add-ons only listen for it.
 */
public final class MarketTradeEvent extends Event {
    private final MinecraftServer server;
    private final ServerPlayer player;
    private final AccountId account;
    private final Item item;
    private final boolean buy;
    private final int quantity;
    private final long unitPrice;
    private final long total;

    /**
     * Posted by Variable Economy. The server is the player's.
     *
     * @param player    the player who traded
     * @param account   the account paid or charged
     * @param item      the item traded
     * @param buy       true for a purchase, false for a sale
     * @param quantity  how many units
     * @param unitPrice what the first unit traded at, in bits
     * @param total     the whole trade's price, in bits
     */
    public MarketTradeEvent(ServerPlayer player, AccountId account, Item item, boolean buy, int quantity, long unitPrice,
                            long total) {
        this.server = player.server;
        this.player = player;
        this.account = account;
        this.item = item;
        this.buy = buy;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.total = total;
    }

    /**
     * The server.
     *
     * @return the server the trade happened on
     */
    public MinecraftServer getServer() {
        return server;
    }

    /**
     * The player.
     *
     * @return the player who traded
     */
    public ServerPlayer getPlayer() {
        return player;
    }

    /**
     * The account the money went into (a sale) or came from (a purchase): the player's active account, or the one the
     * trade named.
     *
     * @return the account
     */
    public AccountId getAccount() {
        return account;
    }

    /**
     * The item. Only plain copies trade with the market (apart from data the server's {@code ignored_components}
     * setting allows on a sold copy), so the item says all there is to say.
     *
     * @return the item traded
     */
    public Item getItem() {
        return item;
    }

    /**
     * Which way the trade went.
     *
     * @return true if the player bought from the market, false if they sold to it
     */
    public boolean isBuy() {
        return buy;
    }

    /**
     * How many units were traded.
     *
     * @return the quantity, at least 1
     */
    public int getQuantity() {
        return quantity;
    }

    /**
     * What the first unit traded at. Later units in the same trade can differ, since each unit moves the price;
     * {@link #getTotal()} is what actually changed hands.
     *
     * @return the price, in bits
     */
    public long getUnitPrice() {
        return unitPrice;
    }

    /**
     * The whole trade's price: what the player was paid when selling, or charged when buying.
     *
     * @return the total, in bits
     */
    public long getTotal() {
        return total;
    }
}
