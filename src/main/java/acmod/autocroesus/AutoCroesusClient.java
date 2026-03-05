package acmod.autocroesus;

import acmod.autocroesus.api.PriceService;
import acmod.autocroesus.command.AutoCroesusCommand;
import acmod.autocroesus.config.ConfigManager;
import acmod.autocroesus.config.ListManager;
import acmod.autocroesus.logic.AutoCroesusController;
import acmod.autocroesus.logic.LootLogService;
import acmod.autocroesus.logic.RewardParser;
import acmod.autocroesus.render.OverlayRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public class AutoCroesusClient implements ClientModInitializer {
    private ConfigManager configManager;

    private long lastConfigSave;

    @Override
    public void onInitializeClient() {
        Path dataDir = FabricLoader.getInstance().getConfigDir().resolve("autocroesus");

        configManager = new ConfigManager(dataDir);
        configManager.load();

        ListManager listManager = new ListManager(dataDir);
        listManager.load();

        PriceService priceService = new PriceService(dataDir);
        priceService.loadCache();

        RewardParser rewardParser = new RewardParser(priceService, listManager);
        LootLogService lootLogService = new LootLogService(dataDir, priceService, listManager);

        AutoCroesusController controller = new AutoCroesusController(
            configManager,
            listManager,
            priceService,
            rewardParser,
            lootLogService
        );

        AutoCroesusCommand.register(controller);
        new OverlayRenderer(controller).register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            controller.onTick(client);

            long now = System.currentTimeMillis();
            if (now - lastConfigSave > 5_000L) {
                configManager.save();
                lastConfigSave = now;
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> configManager.save());
    }
}
