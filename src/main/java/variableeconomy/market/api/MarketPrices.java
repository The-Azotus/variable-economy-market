package variableeconomy.market.api;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * What the market pays and charges for items. Get it from {@link MarketApi#prices()}. Nothing here changes the
 * market. Prices are the same for every player; only which items a player may see differs.
 * <p>
 * Every unit traded moves the price a little, so the total for several units isn't the unit price times the quantity:
 * the totals here work it out as a trade would. Prices also recover over time and change with every trade, so ask
 * when a price is needed rather than keeping one.
 * <p>
 * <b>Paying for items.</b> An add-on that takes items and pays for them with core's
 * {@link variableeconomy.core.api.Money#pay} (a contract, a reward for a delivery) must pay less than
 * {@link #lowestBuyCost} for them whenever that has a value; otherwise buying them from the market and handing them in
 * would make a profit. The other way is to sell them through {@link MarketTrading}. Core's
 * {@link variableeconomy.core.api.Money} states the rule.
 * <p>
 * Every method that takes a quantity throws {@link IllegalArgumentException} for one less than 1.
 * <p>
 * Server thread only.
 */
public interface MarketPrices {
    /**
     * An item's market terms now, as every player would trade it. Items locked for some players are included: use
     * {@link #quote(ServerPlayer, Item)} for what one player sees.
     *
     * @param server the server
     * @param item   the item
     * @return the terms, or empty if the item isn't listed
     */
    Optional<MarketQuote> quote(MinecraftServer server, Item item);

    /**
     * An item's market terms now, as {@code player} sees them.
     *
     * @param player the player
     * @param item   the item
     * @return the terms, or empty if the item isn't listed or is locked for the player
     */
    Optional<MarketQuote> quote(ServerPlayer player, Item item);

    /**
     * Every item the market lists, locked ones included, in no particular order. The list doesn't follow later catalog
     * reloads: ask again after a {@link variableeconomy.market.api.event.CatalogReloadedEvent}.
     *
     * @param server the server
     * @return the items; the list can't be changed
     */
    List<Item> listedItems(MinecraftServer server);

    /**
     * What selling {@code quantity} units now would pay, the price falling with each unit and the sell fee taken, as
     * a sale would. The market's per-request limit doesn't apply here.
     *
     * @param server   the server
     * @param item     the item
     * @param quantity how many; at least 1
     * @return the total in bits, or empty if the item isn't listed, the market doesn't buy it, or the total is too
     *         large to count
     */
    OptionalLong sellTotal(MinecraftServer server, Item item, int quantity);

    /**
     * What buying {@code quantity} units now would cost, the price rising with each unit, as a purchase would. Whether
     * the market holds that many isn't checked ({@link MarketQuote#stock()} says), and the per-request limit doesn't
     * apply.
     *
     * @param server   the server
     * @param item     the item
     * @param quantity how many; at least 1
     * @return the total in bits, or empty if the item isn't listed, the market doesn't sell it, or the total is too
     *         large to count
     */
    OptionalLong buyTotal(MinecraftServer server, Item item, int quantity);

    /**
     * What selling {@code quantity} units would pay starting from the item's base price, as if nobody had traded it
     * lately: its normal value, steadier than {@link #sellTotal}.
     *
     * @param server   the server
     * @param item     the item
     * @param quantity how many; at least 1
     * @return the total in bits, or empty as for {@link #sellTotal}
     */
    OptionalLong normalSellTotal(MinecraftServer server, Item item, int quantity);

    /**
     * The least buying {@code quantity} units from the market could ever cost with the catalog as it is: the price
     * can't fall below this however the market moves. An item with unlimited stock never sells below its minimum buy
     * markup over base; a finite item whose price moves never below its price floor (or its price now, if a raised
     * floor hasn't caught up yet); a fixed-price item always sells at its buy price.
     * <p>
     * Paying less than this for those units means buying them from the market to hand them in can't profit.
     *
     * @param server   the server
     * @param item     the item
     * @param quantity how many; at least 1
     * @return the cost in bits, rounded up; empty when the market sets no bound: the item isn't listed or the market
     *         doesn't sell it (players can't have bought it from the market), or the cost is too large to count, so it
     *         bounds nothing
     */
    OptionalLong lowestBuyCost(MinecraftServer server, Item item, int quantity);
}
