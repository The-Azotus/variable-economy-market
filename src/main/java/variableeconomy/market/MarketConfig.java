package variableeconomy.market;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The market's server config, {@code config/variable_economy_market-server.toml}, loaded when a server starts. The
 * pricing values are server-wide defaults; catalog entries and file defaults can override all of them except the
 * recovery interval. {@link #install} hands its values to {@link MarketSettings}, which the rest of the market reads.
 */
public final class MarketConfig {
    /** The config's file name. */
    public static final String FILE = "variable_economy_market-server.toml";
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<AccessMode> ACCESS_MODE;
    public static final ModConfigSpec.IntValue MAX_QUANTITY_PER_REQUEST;
    public static final ModConfigSpec.DoubleValue SELL_IMPACT;
    public static final ModConfigSpec.DoubleValue BUY_IMPACT;
    public static final ModConfigSpec.DoubleValue RECOVERY;
    public static final ModConfigSpec.IntValue INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue FLOOR;
    public static final ModConfigSpec.DoubleValue CEILING;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        ACCESS_MODE = builder
                .comment("How players reach the market.",
                        "COMMAND_AND_BLOCK: the /market commands or the Market Terminal.",
                        "BLOCK_ONLY: only the Market Terminal. Players without operator permission (level 2) can't open the",
                        "market or trade by command; operators and the console always can.")
                .defineEnum("access_mode", AccessMode.COMMAND_AND_BLOCK);
        MAX_QUANTITY_PER_REQUEST = builder
                .comment("Largest quantity a single buy or sell request may move. Larger orders are rejected with TOO_LARGE.")
                .defineInRange("max_quantity_per_request", 640, 1, 1_000_000);

        builder.comment("Supply and demand. Each item has a price factor s, starting at 1.0. These are defaults;",
                "catalog entries may override them (except interval_ticks).").push("pricing");
        SELL_IMPACT = builder
                .comment("Each unit sold multiplies s by (1 - sell_impact).")
                .defineInRange("sell_impact", 0.0005, 0.0, 0.5);
        BUY_IMPACT = builder
                .comment("Each unit bought multiplies s by (1 + buy_impact). Keep it no higher than the smallest markup,",
                        "and no higher than sell_impact / (1 - sell_impact), or buying and reselling can make a profit.",
                        "Don't set it far below sell_impact either, or selling and buying back can. Equal values are safe;",
                        "the server log warns at startup about any item whose settings allow a profitable round trip.")
                .defineInRange("buy_impact", 0.0005, 0.0, 0.5);
        RECOVERY = builder
                .comment("Fraction of the distance back to 1.0 that s recovers at each interval step.")
                .defineInRange("recovery", 0.05, 0.0, 1.0);
        INTERVAL_TICKS = builder
                .comment("Game ticks per recovery step (20 ticks = 1 second). Steps happen server-wide at multiples",
                        "of this value, so prices only change when someone trades or at a step.")
                .defineInRange("interval_ticks", 1200, 1, 1_728_000);
        FLOOR = builder
                .comment("Lowest s can fall to from selling.")
                .defineInRange("floor", 0.2, 0.001, 1.0);
        CEILING = builder
                .comment("Highest s can rise to from buying.")
                .defineInRange("ceiling", 5.0, 1.0, 1000.0);
        builder.pop();

        SPEC = builder.build();
    }

    private MarketConfig() {
    }

    /** Hands {@link MarketSettings} its values. Once, while the mod is constructed. */
    public static void install() {
        MarketSettings.install(new MarketSettings.Source() {
            @Override
            public boolean blockOnly() {
                return accessMode() == AccessMode.BLOCK_ONLY;
            }

            @Override
            public int maxQuantityPerRequest() {
                return MarketConfig.maxQuantityPerRequest();
            }

            @Override
            public double sellImpact() {
                return MarketConfig.sellImpact();
            }

            @Override
            public double buyImpact() {
                return MarketConfig.buyImpact();
            }

            @Override
            public double recovery() {
                return MarketConfig.recovery();
            }

            @Override
            public int intervalTicks() {
                return MarketConfig.intervalTicks();
            }

            @Override
            public double floor() {
                return MarketConfig.floor();
            }

            @Override
            public double ceiling() {
                return MarketConfig.ceiling();
            }
        });
    }

    /** Whether players reach the market by command and block, or by block only. */
    public enum AccessMode { COMMAND_AND_BLOCK, BLOCK_ONLY }

    // A server config only loads when a server starts, so each getter falls back to the default before then.

    public static AccessMode accessMode() {
        return SPEC.isLoaded() ? ACCESS_MODE.get() : ACCESS_MODE.getDefault();
    }

    public static int maxQuantityPerRequest() {
        return SPEC.isLoaded() ? MAX_QUANTITY_PER_REQUEST.get() : MAX_QUANTITY_PER_REQUEST.getDefault();
    }

    public static double sellImpact() {
        return SPEC.isLoaded() ? SELL_IMPACT.get() : SELL_IMPACT.getDefault();
    }

    public static double buyImpact() {
        return SPEC.isLoaded() ? BUY_IMPACT.get() : BUY_IMPACT.getDefault();
    }

    public static double recovery() {
        return SPEC.isLoaded() ? RECOVERY.get() : RECOVERY.getDefault();
    }

    public static int intervalTicks() {
        return SPEC.isLoaded() ? INTERVAL_TICKS.get() : INTERVAL_TICKS.getDefault();
    }

    public static double floor() {
        return SPEC.isLoaded() ? FLOOR.get() : FLOOR.getDefault();
    }

    public static double ceiling() {
        return SPEC.isLoaded() ? CEILING.get() : CEILING.getDefault();
    }
}
