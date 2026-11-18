package variableeconomy.market;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.world.entity.player.Player;

import java.util.Objects;

/**
 * The market's server settings. They come from {@code config/variable_economy_market-server.toml},
 * whose keys are named below; {@link MarketConfig} installs them when the mod is constructed. A server config only
 * loads when a server starts, so until then each value is the config's default.
 */
public final class MarketSettings {
    /** Where the values come from. */
    public interface Source {
        /**
         * Whether players without operator permission must use a block, not commands (access_mode BLOCK_ONLY).
         *
         * @return the value
         */
        boolean blockOnly();

        /**
         * The largest quantity one trade may move (max_quantity_per_request).
         *
         * @return the value
         */
        int maxQuantityPerRequest();

        /**
         * The default sell impact (pricing.sell_impact).
         *
         * @return the value
         */
        double sellImpact();

        /**
         * The default buy impact (pricing.buy_impact).
         *
         * @return the value
         */
        double buyImpact();

        /**
         * The default recovery per step (pricing.recovery).
         *
         * @return the value
         */
        double recovery();

        /**
         * Game ticks per recovery step (pricing.interval_ticks).
         *
         * @return the value
         */
        int intervalTicks();

        /**
         * The default price floor (pricing.floor).
         *
         * @return the value
         */
        double floor();

        /**
         * The default price ceiling (pricing.ceiling).
         *
         * @return the value
         */
        double ceiling();
    }

    private static volatile Source source;

    private MarketSettings() {
    }

    /** Installs where the values come from. Once, while the mod is constructed. */
    public static void install(Source values) {
        source = Objects.requireNonNull(values, "values");
    }

    private static Source source() {
        Source installed = source;
        if (installed == null) {
            throw new IllegalStateException("MarketSettings isn't installed yet");
        }
        return installed;
    }

    /** Whether players without operator permission must use a block, not commands (access_mode BLOCK_ONLY). */
    public static boolean blockOnly() {
        return source().blockOnly();
    }

    /** The largest quantity one trade may move (max_quantity_per_request). */
    public static int maxQuantityPerRequest() {
        return source().maxQuantityPerRequest();
    }

    /** The default sell impact (pricing.sell_impact). */
    public static double sellImpact() {
        return source().sellImpact();
    }

    /** The default buy impact (pricing.buy_impact). */
    public static double buyImpact() {
        return source().buyImpact();
    }

    /** The default recovery per step (pricing.recovery). */
    public static double recovery() {
        return source().recovery();
    }

    /** Game ticks per recovery step (pricing.interval_ticks). */
    public static int intervalTicks() {
        return source().intervalTicks();
    }

    /** The default price floor (pricing.floor). */
    public static double floor() {
        return source().floor();
    }

    /** The default price ceiling (pricing.ceiling). */
    public static double ceiling() {
        return source().ceiling();
    }

    /** Whether {@code source} may use the market's commands: always for operators and the console, otherwise unless block-only. */
    public static boolean commandsAllowed(CommandSourceStack source) {
        return commandsAllowed(blockOnly(), source.hasPermission(Commands.LEVEL_GAMEMASTERS));
    }

    /** Whether {@code player} may use the market's commands. */
    public static boolean commandsAllowed(Player player) {
        return commandsAllowed(blockOnly(), player.hasPermissions(Commands.LEVEL_GAMEMASTERS));
    }

    /** Operators always may; everyone else unless the server is block-only. */
    public static boolean commandsAllowed(boolean blockOnly, boolean operator) {
        return operator || !blockOnly;
    }
}
