package acmod.autocroesus.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import acmod.autocroesus.logic.AutoCroesusController;
import acmod.autocroesus.model.ConfigData;
import acmod.autocroesus.model.LootFilter;
import acmod.autocroesus.model.LootSummary;
import acmod.autocroesus.util.Chat;
import acmod.autocroesus.util.FormatUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AutoCroesusCommand {
    private static final Pattern FLOOR_ARG = Pattern.compile("^(?:f|floor):([fFmM][1-7])$");
    private static final Pattern LIMIT_ARG = Pattern.compile("^(?:l|limit):(\\d+)$");
    private static final Pattern SCORE_ARG = Pattern.compile("^(?:s|score):(\\d+)$");

    private static final List<String> KISMET_SUGGESTIONS = List.of(
        "F1", "F2", "F3", "F4", "F5", "F6", "F7",
        "M1", "M2", "M3", "M4", "M5", "M6", "M7",
        "2000000"
    );

    private static final List<String> KEY_SUGGESTIONS = List.of("200000", "500000", "1000000");
    private static final List<String> LOOT_SUGGESTIONS = List.of("floor:F7", "floor:M7", "limit:100", "score:300", "score:270");

    private AutoCroesusCommand() {
    }

    public static void register(AutoCroesusController controller) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            registerRoot(dispatcher, "autocroesus", controller);
            registerRoot(dispatcher, "ac", controller);
        });
    }

    private static void registerRoot(CommandDispatcher<FabricClientCommandSource> dispatcher, String name, AutoCroesusController controller) {
        LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommandManager.literal(name)
            .executes(ctx -> {
                printHelp();
                return Command.SINGLE_SUCCESS;
            });

        root.then(ClientCommandManager.literal("reset").executes(ctx -> {
            controller.reset();
            Chat.success("State reset.");
            return Command.SINGLE_SUCCESS;
        }));

        root.then(ClientCommandManager.literal("settings").executes(ctx -> {
            printSettings(controller.config());
            return Command.SINGLE_SUCCESS;
        }));
        root.then(ClientCommandManager.literal("config").executes(ctx -> {
            printSettings(controller.config());
            return Command.SINGLE_SUCCESS;
        }));
        root.then(ClientCommandManager.literal("s").executes(ctx -> {
            printSettings(controller.config());
            return Command.SINGLE_SUCCESS;
        }));
        root.then(ClientCommandManager.literal("c").executes(ctx -> {
            printSettings(controller.config());
            return Command.SINGLE_SUCCESS;
        }));

        root.then(ClientCommandManager.literal("overlay").executes(ctx -> {
            controller.config().showChestInfo = !controller.config().showChestInfo;
            Chat.info("Chest overlay is now " + controller.config().showChestInfo + ".");
            return Command.SINGLE_SUCCESS;
        }));

        root.then(ClientCommandManager.literal("delay")
            .executes(ctx -> {
                Chat.info("Min click delay is " + controller.config().minClickDelay + "ms.");
                return Command.SINGLE_SUCCESS;
            })
            .then(ClientCommandManager.argument("ms", IntegerArgumentType.integer(0))
                .executes(ctx -> {
                    int delay = IntegerArgumentType.getInteger(ctx, "ms");
                    controller.config().minClickDelay = delay;
                    Chat.info("Min click delay set to " + delay + "ms.");
                    if (delay < 150) {
                        Chat.warn("Very low delay can look suspicious on low ping.");
                    }
                    return Command.SINGLE_SUCCESS;
                }))
        );

        root.then(firstClickDelayLiteral("firstclickdelay", controller));
        root.then(firstClickDelayLiteral("firstdelay", controller));
        root.then(firstClickDelayLiteral("fdelay", controller));

        root.then(kismetLiteral("kismet", controller));
        root.then(kismetLiteral("reroll", controller));

        root.then(keyLiteral("key", controller));
        root.then(keyLiteral("chestkey", controller));

        root.then(ClientCommandManager.literal("go").executes(ctx -> {
            handleGoCommand(controller, false);
            return Command.SINGLE_SUCCESS;
        }));

        root.then(ClientCommandManager.literal("forcego").executes(ctx -> {
            controller.enableAutoClaiming();
            Chat.success("Started without API refresh check.");
            return Command.SINGLE_SUCCESS;
        }));

        root.then(ClientCommandManager.literal("api").executes(ctx -> {
            refreshApi(controller, false);
            return Command.SINGLE_SUCCESS;
        }));

        root.then(ClientCommandManager.literal("loot")
            .executes(ctx -> {
                handleLootCommand(controller, List.of());
                return Command.SINGLE_SUCCESS;
            })
            .then(ClientCommandManager.literal("help").executes(ctx -> {
                printLootHelp();
                return Command.SINGLE_SUCCESS;
            }))
            .then(ClientCommandManager.argument("filters", StringArgumentType.greedyString())
                .suggests(AutoCroesusCommand::suggestLootFilters)
                .executes(ctx -> {
                    handleLootCommand(controller, split(StringArgumentType.getString(ctx, "filters")));
                    return Command.SINGLE_SUCCESS;
                }))
        );

        root.then(listLiteral("alwaysbuy", true, controller));
        root.then(listLiteral("worthless", false, controller));

        root.then(ClientCommandManager.literal("noclick").executes(ctx -> {
            controller.config().noClick = !controller.config().noClick;
            Chat.info("NoClick is now " + controller.config().noClick + ".");
            return Command.SINGLE_SUCCESS;
        }));

        root.then(ClientCommandManager.literal("copy").executes(ctx -> {
            Chat.warn("Debug clipboard copy is not implemented in this Fabric port yet.");
            return Command.SINGLE_SUCCESS;
        }));

        dispatcher.register(root);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> firstClickDelayLiteral(String name, AutoCroesusController controller) {
        return ClientCommandManager.literal(name)
            .executes(ctx -> {
                Chat.info("First GUI click delay is " + controller.config().firstClickDelay + "ms.");
                return Command.SINGLE_SUCCESS;
            })
            .then(ClientCommandManager.argument("ms", IntegerArgumentType.integer(0))
                .executes(ctx -> {
                    int delay = IntegerArgumentType.getInteger(ctx, "ms");
                    controller.config().firstClickDelay = delay;
                    Chat.info("First GUI click delay set to " + delay + "ms.");
                    return Command.SINGLE_SUCCESS;
                }));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> kismetLiteral(String name, AutoCroesusController controller) {
        return ClientCommandManager.literal(name)
            .executes(ctx -> {
                controller.config().useKismets = !controller.config().useKismets;
                Chat.info("Use kismets is now " + controller.config().useKismets + ".");
                return Command.SINGLE_SUCCESS;
            })
            .then(ClientCommandManager.argument("value", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestFrom(builder, KISMET_SUGGESTIONS))
                .executes(ctx -> {
                    applyKismetValue(controller, StringArgumentType.getString(ctx, "value"));
                    return Command.SINGLE_SUCCESS;
                }));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> keyLiteral(String name, AutoCroesusController controller) {
        return ClientCommandManager.literal(name)
            .executes(ctx -> {
                controller.config().useChestKeys = !controller.config().useChestKeys;
                Chat.info("Use chest keys is now " + controller.config().useChestKeys + ".");
                return Command.SINGLE_SUCCESS;
            })
            .then(ClientCommandManager.argument("value", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestFrom(builder, KEY_SUGGESTIONS))
                .executes(ctx -> {
                    Integer minimum = FormatUtil.parseIntLoose(StringArgumentType.getString(ctx, "value"));
                    if (minimum == null || minimum < 0) {
                        Chat.warn("Usage: /ac " + name + " <min_profit>");
                        return Command.SINGLE_SUCCESS;
                    }

                    controller.config().chestKeyMinProfit = minimum;
                    Chat.info("Chest key minimum profit set to " + FormatUtil.commas(minimum) + ".");
                    return Command.SINGLE_SUCCESS;
                }));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> listLiteral(String name, boolean alwaysBuy, AutoCroesusController controller) {
        return ClientCommandManager.literal(name)
            .executes(ctx -> {
                String title = alwaysBuy ? "Always Buy" : "Worthless";
                Chat.info(title + " items:");
                for (String itemId : alwaysBuy ? controller.listManager().alwaysBuy() : controller.listManager().worthless()) {
                    Chat.info("- " + itemId);
                }
                return Command.SINGLE_SUCCESS;
            })
            .then(ClientCommandManager.literal("reset").executes(ctx -> {
                if (alwaysBuy) {
                    controller.listManager().resetAlwaysBuy();
                } else {
                    controller.listManager().resetWorthless();
                }
                return Command.SINGLE_SUCCESS;
            }))
            .then(ClientCommandManager.argument("itemId", StringArgumentType.word())
                .suggests((ctx, builder) -> suggestItemIds(controller, builder))
                .executes(ctx -> {
                    String itemId = StringArgumentType.getString(ctx, "itemId").toUpperCase();
                    if (!controller.priceService().itemExists(itemId)) {
                        Chat.warn("Warning: item ID not found in cache, adding anyway: " + itemId);
                    }

                    boolean added = alwaysBuy
                        ? controller.listManager().toggleAlwaysBuy(itemId)
                        : controller.listManager().toggleWorthless(itemId);

                    if (added) {
                        Chat.success("Added " + itemId + ".");
                    } else {
                        Chat.warn("Removed " + itemId + ".");
                    }
                    return Command.SINGLE_SUCCESS;
                }));
    }

    private static void handleGoCommand(AutoCroesusController controller, boolean force) {
        long sinceUpdate = System.currentTimeMillis() - controller.config().lastApiUpdate;
        if (force || sinceUpdate <= 1_800_000L) {
            controller.enableAutoClaiming();
            return;
        }

        Chat.info("Price cache older than 30 minutes. Refreshing API...");
        refreshApi(controller, true);
    }

    private static void refreshApi(AutoCroesusController controller, boolean startAfterUpdate) {
        CompletableFuture<Void> future = controller.priceService().updatePrices();
        future.whenComplete((unused, throwable) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                if (throwable != null) {
                    Chat.warn("Failed to refresh API: " + throwable.getMessage());
                    return;
                }

                controller.config().lastApiUpdate = System.currentTimeMillis();
                Chat.success("Price data refreshed.");

                if (startAfterUpdate) {
                    controller.enableAutoClaiming();
                }
            });
        });
    }

    private static void applyKismetValue(AutoCroesusController controller, String value) {
        ConfigData config = controller.config();

        if (value.matches("^[fFmM][1-7]$")) {
            String floor = value.toUpperCase();
            if (config.kismetFloors.contains(floor)) {
                config.kismetFloors.remove(floor);
                Chat.info("Removed " + floor + " from kismet floors.");
            } else {
                config.kismetFloors.add(floor);
                Chat.info("Added " + floor + " to kismet floors.");
            }
            return;
        }

        Integer minimum = FormatUtil.parseIntLoose(value);
        if (minimum != null && minimum >= 0) {
            config.kismetMinProfit = minimum;
            Chat.info("Kismet minimum profit set to " + FormatUtil.commas(minimum) + ".");
            return;
        }

        Chat.warn("Usage: /ac kismet [F1..F7|M1..M7|min_profit]");
    }

    private static void handleLootCommand(AutoCroesusController controller, List<String> args) {
        LootFilter filter = parseFilter(args);
        if (filter == null) {
            Chat.warn("Invalid loot filters. Use /ac loot help");
            return;
        }

        LootSummary summary = controller.lootLogService().summarize(filter);
        if (summary.runs == 0) {
            Chat.warn("No runs found for this filter.");
            return;
        }

        String floorLabel = filter.floor() == null ? "All Floors" : filter.floor().toUpperCase();
        Chat.info("Runs: " + summary.runs + " | Floor: " + floorLabel);
        Chat.info("Total chest cost: " + FormatUtil.commas(summary.totalChestCost));
        Chat.info("Total sell price: " + FormatUtil.commas(summary.totalSellPrice));
        Chat.info("Total profit: " + FormatUtil.commas(summary.totalProfit));
        Chat.info("Profit/run: " + FormatUtil.commas(summary.totalProfit / summary.runs));

        int shown = 0;
        for (Map.Entry<String, Integer> entry : summary.quantities.entrySet()) {
            if (shown >= 25) {
                break;
            }

            Double value = controller.priceService().getSellPrice(entry.getKey(), true, controller.listManager().worthless());
            double total = (value == null ? 0 : value) * entry.getValue();
            String itemName = controller.rewardParser().getFormattedNameFromId(entry.getKey());
            Chat.info(entry.getValue() + "x " + itemName + " = " + FormatUtil.commas(total));
            shown++;
        }
    }

    private static LootFilter parseFilter(List<String> args) {
        int score = 300;
        String floor = null;
        Integer limit = null;

        for (String arg : args) {
            Matcher scoreMatcher = SCORE_ARG.matcher(arg);
            if (scoreMatcher.matches()) {
                score = Integer.parseInt(scoreMatcher.group(1));
                if (score < 0 || score > 317) {
                    return null;
                }
                continue;
            }

            Matcher floorMatcher = FLOOR_ARG.matcher(arg);
            if (floorMatcher.matches()) {
                floor = floorMatcher.group(1).toUpperCase();
                continue;
            }

            Matcher limitMatcher = LIMIT_ARG.matcher(arg);
            if (limitMatcher.matches()) {
                limit = Integer.parseInt(limitMatcher.group(1));
            }
        }

        return new LootFilter(score, floor, limit);
    }

    private static void printLootHelp() {
        Chat.info("Usage: /ac loot [floor:F7] [limit:100] [score:300]");
    }

    private static void printSettings(ConfigData config) {
        Chat.info("Settings:");
        Chat.info("overlay=" + config.showChestInfo);
        Chat.info("minClickDelay=" + config.minClickDelay + "ms");
        Chat.info("firstClickDelay=" + config.firstClickDelay + "ms");
        Chat.info("useChestKeys=" + config.useChestKeys + " | chestKeyMinProfit=" + FormatUtil.commas(config.chestKeyMinProfit));
        Chat.info("useKismets=" + config.useKismets + " | kismetMinProfit=" + FormatUtil.commas(config.kismetMinProfit));
        Chat.info("kismetFloors=" + String.join(", ", config.kismetFloors));
    }

    private static void printHelp() {
        Chat.info("/ac go, /ac forcego, /ac api");
        Chat.info("/ac settings, /ac overlay, /ac delay <ms>, /ac firstclickdelay <ms>");
        Chat.info("/ac kismet [floor|profit], /ac key [profit]");
        Chat.info("/ac alwaysbuy [id|reset], /ac worthless [id|reset]");
        Chat.info("/ac loot help|floor:F7 limit:100 score:300");
        Chat.info("/ac reset, /ac noclick");
    }

    private static List<String> split(String raw) {
        String normalized = raw == null ? "" : raw.trim();
        if (normalized.isEmpty()) {
            return List.of();
        }

        String[] parts = normalized.split("\\s+");
        List<String> output = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (!part.isBlank()) {
                output.add(part);
            }
        }
        return output;
    }

    private static CompletableFuture<Suggestions> suggestFrom(SuggestionsBuilder builder, List<String> options) {
        String remaining = builder.getRemainingLowerCase();
        for (String option : options) {
            if (option.toLowerCase().startsWith(remaining)) {
                builder.suggest(option);
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestLootFilters(CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
        return suggestFrom(builder, LOOT_SUGGESTIONS);
    }

    private static CompletableFuture<Suggestions> suggestItemIds(AutoCroesusController controller, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        int max = 100;

        for (var item : controller.priceService().allItems()) {
            String id = item.id();
            if (id.toLowerCase().startsWith(remaining)) {
                builder.suggest(id);
                max--;
                if (max <= 0) {
                    break;
                }
            }
        }

        return builder.buildFuture();
    }
}
