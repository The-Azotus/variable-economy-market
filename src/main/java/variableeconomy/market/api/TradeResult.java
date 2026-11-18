package variableeconomy.market.api;

import variableeconomy.core.api.AccountId;

import java.util.Objects;

/**
 * How a market trade ended.
 *
 * @param result    what happened; only {@link MarketResult#OK} changed anything
 * @param quantity  the units traded: the quantity asked for on success, otherwise 0
 * @param unitPrice what the first unit traded at (or would have), in bits; 0 if the trade didn't get as far as pricing
 * @param total     what the whole trade paid or cost (or would have, on {@link MarketResult#NO_FUNDS},
 *                  {@link MarketResult#NO_STOCK}, {@link MarketResult#NO_SPACE} and
 *                  {@link MarketResult#NOT_ENOUGH_ITEMS}), in bits; otherwise 0
 * @param account   the account paid or charged (or that would have been)
 */
public record TradeResult(MarketResult result, int quantity, long unitPrice, long total, AccountId account) {
    /**
     * Checks the result and account are there.
     *
     * @param result    what happened
     * @param quantity  the units traded
     * @param unitPrice what the first unit traded at
     * @param total     what the whole trade paid or cost
     * @param account   the account paid or charged
     */
    public TradeResult {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(account, "account");
    }

    /**
     * Whether the trade happened.
     *
     * @return true for {@link MarketResult#OK}
     */
    public boolean ok() {
        return result == MarketResult.OK;
    }
}
