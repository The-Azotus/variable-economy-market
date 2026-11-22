package variableeconomy.market.api;

import variableeconomy.core.api.AccountId;

import java.util.Objects;

/**
 * How a market trade ended.
 *
 * @param result    what happened; only {@link MarketResult#OK} changed anything
 * @param quantity  the units traded: the quantity asked for on success, otherwise 0
 * @param unitPrice what the first unit traded at (or would have), in bits; 0 if the trade didn't get as far as pricing
 * @param total     what the whole trade paid or cost, in bits; for a refusal found once the trade was priced, what
 *                  it would have: {@link MarketResult#NO_FUNDS}, {@link MarketResult#NO_STOCK},
 *                  {@link MarketResult#NO_SPACE}, {@link MarketResult#NOT_ENOUGH_ITEMS}, {@link MarketResult#NO_ACCOUNT},
 *                  {@link MarketResult#BANK_UNAVAILABLE}, {@link MarketResult#NOT_ALLOWED} for an account the player
 *                  can't use, and {@link MarketResult#TOO_LARGE} for a payment the account can't hold; otherwise 0
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
