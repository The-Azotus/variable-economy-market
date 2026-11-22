package variableeconomy.market;

import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import variableeconomy.core.api.event.BalanceChangedEvent;
import variableeconomy.core.api.event.ItemGateChangedEvent;
import variableeconomy.market.api.event.CatalogReloadedEvent;
import variableeconomy.market.block.MarketBlocks;
import variableeconomy.market.catalog.CatalogEntry;
import variableeconomy.market.catalog.MarketCatalog;
import variableeconomy.market.command.MarketCommand;
import variableeconomy.market.data.MarketOverrides;
import variableeconomy.market.data.MarketStockData;
import variableeconomy.market.menu.MarketMenu;
import variableeconomy.market.network.MarketNetwork;
import variableeconomy.market.network.MarketSync;
import variableeconomy.market.service.MarketApiService;
import variableeconomy.market.service.MarketEvents;
import variableeconomy.market.service.MarketService;
import variableeconomy.market.service.PriceModel;

import java.util.Map;

/**
 * The market: the server-owned market whose prices move with supply and demand. Registers its block, menu, packets,
 * built-in data packs, catalog, commands and server events, and installs the market API.
 */
@Mod(MarketModule.MOD_ID)
public final class MarketModule {
    /** The market's namespace for its block, menu, packets, money reasons and lang keys. */
    public static final String MOD_ID = "variable_economy_market";
    /**
     * The namespace of the market's data, kept from before the mod was split: the built-in item classes (a class's
     * short name means {@code variable_economy:<name>}), the files an import writes, and the {@code config/variable_economy/}
     * and world {@code variable_economy/backups} folders.
     */
    public static final String DATA_NAMESPACE = "variable_economy";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** The ID admins enable the example catalog with: {@code /datapack enable "mod/variable_economy_market:example_catalog"}. */
    public static final String EXAMPLE_PACK_ID = "mod/" + MOD_ID + ":example_catalog";
    /** The ID admins turn the built-in prices off with: {@code /datapack disable "mod/variable_economy_market:default_prices"}. */
    public static final String DEFAULT_PRICES_PACK_ID = "mod/" + MOD_ID + ":default_prices";
    /** Set to {@code true} to leave the built-in prices out entirely. The GameTest server does, so tests see a small catalog. */
    public static final String SKIP_DEFAULT_PRICES = "variableeconomy.skipDefaultPrices";

    /** The mod's entry point: registers its server config, then its blocks, menus, packets, commands and server events. */
    public MarketModule(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, MarketConfig.SPEC, MarketConfig.FILE);
        MarketConfig.install();
        MarketApiService.install();
        MarketMenu.MENUS.register(modBus);
        MarketBlocks.register(modBus);
        modBus.addListener(MarketNetwork::register);
        modBus.addListener(MarketModule::addExampleCatalog);
        modBus.addListener(MarketModule::addDefaultPrices);
        modBus.addListener(MarketModule::configReloaded);

        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(MarketCatalog.INSTANCE));
        // What a player has unlocked decides which items they're sent.
        NeoForge.EVENT_BUS.addListener((ItemGateChangedEvent event) -> MarketSync.stagesChanged(event.getPlayer().orElse(null)));
        // An open market screen shows a new balance straight away, whatever changed it.
        NeoForge.EVENT_BUS.addListener((BalanceChangedEvent event) -> MarketSync.balanceChanged(event.getServer(), event.getAccount()));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> MarketCommand.register(event.getDispatcher(), event.getBuildContext()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            boolean recoveryStep = MarketStockData.now(event.getServer()) % MarketSettings.intervalTicks() == 0;
            if (recoveryStep) {
                MarketService.sampleHistory(event.getServer());
            }
            MarketSync.tick(event.getServer());
            if (recoveryStep) {
                // After the sync, so players are sent the step that brought each item back to 1.0.
                MarketService.pruneRecovered(event.getServer());
            }
        });
        // Once the world is up its admin edits can be applied; again after every /reload, since tags may change.
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> catalogReloaded(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> MarketCatalog.INSTANCE.setOverrides(MarketOverrides.Snapshot.EMPTY));
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> {
            if (event.getPlayer() == null) {
                catalogReloaded(event.getPlayerList().getServer());
            } else {
                // A joining player gets the catalog so item tooltips can show what the market pays.
                MarketSync.sendCatalog(event.getPlayer());
            }
        });
    }

    /**
     * After the catalog files or the admin edits change: rebuild the live catalog, give new items their start stock,
     * and send every player the new prices. Returns the admin edits that couldn't be applied.
     */
    public static Map<ResourceLocation, String> catalogReloaded(MinecraftServer server) {
        Map<ResourceLocation, String> problems = MarketCatalog.INSTANCE.setOverrides(MarketOverrides.get(server).snapshot());
        MarketStockData.get(server).seed(MarketCatalog.INSTANCE.entries());
        warnAboutArbitrage();
        MarketSync.broadcastSnapshot(server);
        MarketEvents.post(new CatalogReloadedEvent(server));
        return problems;
    }

    /**
     * The server config file was changed while the server runs. Entries read their defaults from it live, so prices
     * may have moved: rebuild as after a catalog reload, which re-checks round trips and sends every player the new
     * prices and order limit. The event comes from the config watcher's thread, so the work goes to the server thread.
     */
    private static void configReloaded(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != MarketConfig.SPEC) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(() -> configChanged(server));
        }
    }

    /** After the server config changed while the server runs. Server thread only. */
    public static void configChanged(MinecraftServer server) {
        LOGGER.info("The market's server config changed; prices updated");
        catalogReloaded(server);
    }

    /** Checked once config and catalog are both loaded, since entries inherit config defaults. */
    private static void warnAboutArbitrage() {
        for (CatalogEntry entry : MarketCatalog.INSTANCE.entries()) {
            PriceModel.arbitrageRisk(PriceModel.inputs(entry)).ifPresent(reason -> LOGGER.warn(
                    "Market item {}: a round trip may make a profit ({})", entry.itemId(), reason));
        }
    }

    /**
     * The five-item example catalog ships as an optional data pack, off unless an admin enables it, so placeholder
     * prices never appear on a server by surprise. Minecraft never switches on packs from the "feature" source by
     * itself. (The GameTest server enables every available pack, so the tests still see the example items.)
     */
    private static void addExampleCatalog(AddPackFindersEvent event) {
        event.addPackFinders(ResourceLocation.fromNamespaceAndPath(MOD_ID, "example_catalog"), PackType.SERVER_DATA,
                Component.literal("Variable Economy example catalog"), PackSource.FEATURE, false, Pack.Position.TOP);
    }

    /**
     * Built-in prices for vanilla and popular mods, one file per mod, each loaded only if its mod is installed. The
     * "built-in" source switches it on by itself, in new and existing worlds, until an admin disables it. It goes to
     * the bottom of the pack order, so any other catalog file overrides its prices.
     */
    private static void addDefaultPrices(AddPackFindersEvent event) {
        if (Boolean.getBoolean(SKIP_DEFAULT_PRICES)) {
            return;
        }
        event.addPackFinders(ResourceLocation.fromNamespaceAndPath(MOD_ID, "default_prices"), PackType.SERVER_DATA,
                Component.literal("Variable Economy default prices"), PackSource.BUILT_IN, false, Pack.Position.BOTTOM);
    }
}
