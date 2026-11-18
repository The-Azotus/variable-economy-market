package variableeconomy.market.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * An item's market terms at one moment. Prices are in bits, for the next single unit: a larger order moves the price
 * as it goes, so its total isn't simply the unit price times the quantity ({@link MarketPrices#sellTotal} and
 * {@link MarketPrices#buyTotal} give it). Same for every player.
 *
 * @param item      the item
 * @param basePrice the item's base price from the catalog
 * @param sellPrice what the market pays a player for the next unit
 * @param buyPrice  what the market charges a player for the next unit
 * @param stock     how many the market holds to sell; empty when its stock is unlimited
 * @param canSell   whether players may sell it to the market
 * @param canBuy    whether players may buy it from the market
 * @param factor    the supply and demand factor now: 1.0 is the base price, lower after players sell, higher after
 *                  they buy
 * @param itemClass the item class the catalog gives the item, such as {@code variable_economy:common_resource}; empty
 *                  if it has none
 */
public record MarketQuote(Item item, long basePrice, long sellPrice, long buyPrice, OptionalLong stock, boolean canSell,
                          boolean canBuy, double factor, Optional<ResourceLocation> itemClass) {
    /**
     * Checks the item and stock are there.
     *
     * @param item      the item
     * @param basePrice the item's base price
     * @param sellPrice what the market pays for the next unit
     * @param buyPrice  what the market charges for the next unit
     * @param stock     how many the market holds; empty when unlimited
     * @param canSell   whether players may sell it
     * @param canBuy    whether players may buy it
     * @param factor    the supply and demand factor now
     * @param itemClass the item's class; empty if it has none
     */
    public MarketQuote {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(stock, "stock");
        Objects.requireNonNull(itemClass, "itemClass");
    }
}
