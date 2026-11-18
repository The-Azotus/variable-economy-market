package variableeconomy.market.api;

import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * The way into the market add-on. Each service is one object for the whole game, so it may be kept in a field; its
 * methods take the server or player they act on.
 * <p>
 * The services are ready once the market has been constructed, which NeoForge does before any mod that depends on it.
 * Called earlier, every method throws {@link IllegalStateException}.
 */
public final class MarketApi {
    private static volatile Services services;

    private MarketApi() {
    }

    /**
     * Prices: what the market pays and charges for items, now and at most. Any thread.
     *
     * @return the prices service
     */
    public static MarketPrices prices() {
        return services().prices();
    }

    /**
     * Selling to and buying from the market for a player. Any thread.
     *
     * @return the trading service
     */
    public static MarketTrading trading() {
        return services().trading();
    }

    /**
     * Hands the API its services. The market calls this once, while it is constructed; a second call throws.
     *
     * @param services the services
     */
    @ApiStatus.Internal
    public static void install(Services services) {
        Objects.requireNonNull(services, "services");
        synchronized (MarketApi.class) {
            if (MarketApi.services != null) {
                throw new IllegalStateException("Variable Economy's market API is already installed");
            }
            MarketApi.services = services;
        }
    }

    private static Services services() {
        Services installed = services;
        if (installed == null) {
            throw new IllegalStateException("Variable Economy's market isn't constructed yet");
        }
        return installed;
    }

    /**
     * What the market installs: the implementations behind {@link MarketApi}'s methods. Not for add-ons.
     *
     * @param prices  the prices service
     * @param trading the trading service
     */
    @ApiStatus.Internal
    public record Services(MarketPrices prices, MarketTrading trading) {
        /**
         * Checks both are there.
         *
         * @param prices  the prices service
         * @param trading the trading service
         */
        public Services {
            Objects.requireNonNull(prices, "prices");
            Objects.requireNonNull(trading, "trading");
        }
    }
}
