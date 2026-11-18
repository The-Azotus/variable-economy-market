package variableeconomy.market.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import variableeconomy.core.api.AccountId;

/**
 * Selling to and buying from the market for a player. Get it from {@link MarketApi#trading()}. Each trade works
 * exactly as the market screen's: the same checks, price movement, stock and trade history, and every player is sent
 * the item's new price. Trades go at the current price.
 * <p>
 * The server's access mode (market blocks only, or the command too) isn't checked: the add-on decides who may trade,
 * and where.
 * <p>
 * Selling items this way is one of the two ways an add-on may give money for items; the other is paying less than
 * {@link MarketPrices#lowestBuyCost} for them with core's {@link variableeconomy.core.api.Money#pay}. Core's
 * {@link variableeconomy.core.api.Money} states the rule.
 * <p>
 * Server thread only.
 */
public interface MarketTrading {
    /**
     * The player sells {@code quantity} plain copies of {@code item} (copies carrying only the data the server's
     * {@code ignored_components} setting allows count as plain) from their main inventory (the hotbar and the 27
     * storage slots) to the market, paid into their active account.
     *
     * @param player   the player
     * @param item     the item
     * @param quantity how many, at least 1 and at most the server's per-request limit
     * @return {@link MarketResult#OK} with the units sold and the total paid; otherwise nothing changed
     */
    TradeResult sell(ServerPlayer player, Item item, int quantity);

    /**
     * As {@link #sell(ServerPlayer, Item, int)}, paid into {@code account}, which the player must be able to deposit
     * into.
     *
     * @param player   the player
     * @param item     the item
     * @param quantity how many, at least 1 and at most the server's per-request limit
     * @param account  the account to pay
     * @return as {@link #sell(ServerPlayer, Item, int)}; also {@link MarketResult#NO_ACCOUNT} if there is no such
     *         account, and {@link MarketResult#NOT_ALLOWED} if the player isn't a member of it
     */
    TradeResult sell(ServerPlayer player, Item item, int quantity, AccountId account);

    /**
     * The player buys {@code quantity} of {@code item} from the market, paid from their active account, into their
     * main inventory. The items must fit there: nothing is ever dropped.
     *
     * @param player   the player
     * @param item     the item
     * @param quantity how many, at least 1 and at most the server's per-request limit
     * @return {@link MarketResult#OK} with the units bought and the total charged; otherwise nothing changed
     */
    TradeResult buy(ServerPlayer player, Item item, int quantity);

    /**
     * As {@link #buy(ServerPlayer, Item, int)}, paid from {@code account}, which the player must be able to spend
     * from.
     *
     * @param player   the player
     * @param item     the item
     * @param quantity how many, at least 1 and at most the server's per-request limit
     * @param account  the account to pay from
     * @return as {@link #buy(ServerPlayer, Item, int)}; also {@link MarketResult#NO_ACCOUNT} if there is no such
     *         account, and {@link MarketResult#NOT_ALLOWED} if the player can't spend from it
     */
    TradeResult buy(ServerPlayer player, Item item, int quantity, AccountId account);
}
