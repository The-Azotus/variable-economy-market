package variableeconomy.market.api;

/**
 * How a market trade ended. Only {@link #OK} changed anything; every other result left the market, the accounts and
 * the player's inventory exactly as they were.
 */
public enum MarketResult {
    /** Done. */
    OK,
    /** The item isn't listed, or it is locked for this player (the two answer alike, so neither reveals the item). */
    NOT_LISTED,
    /** The market doesn't buy this item from players. */
    SELL_DISABLED,
    /** The market doesn't sell this item to players. */
    BUY_DISABLED,
    /** The player's main inventory holds fewer plain copies of the item than asked to sell. */
    NOT_ENOUGH_ITEMS,
    /** The market holds fewer of the item than asked to buy. */
    NO_STOCK,
    /** The items bought wouldn't fit in the player's main inventory. */
    NO_SPACE,
    /** The account can't cover the total. */
    NO_FUNDS,
    /**
     * Too much at once: a quantity above the server's per-request limit, a total above what the bank moves at once,
     * or a sale that would take the market's stock or the account's balance past what they can hold.
     */
    TOO_LARGE,
    /** A quantity of zero or less. */
    INVALID_QUANTITY,
    /** The player can't use the named account this way: deposit into it for a sale, or spend from it for a purchase. */
    NOT_ALLOWED,
    /** The named account doesn't exist. */
    NO_ACCOUNT,
    /** The bank can't be reached. The mod's own bank always can, so this isn't returned today. */
    BANK_UNAVAILABLE
}
