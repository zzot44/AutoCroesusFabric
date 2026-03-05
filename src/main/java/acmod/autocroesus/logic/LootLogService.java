package acmod.autocroesus.logic;

import acmod.autocroesus.api.PriceService;
import acmod.autocroesus.config.ListManager;
import acmod.autocroesus.model.ChestItem;
import acmod.autocroesus.model.ChestProfit;
import acmod.autocroesus.model.LootFilter;
import acmod.autocroesus.model.LootSummary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LootLogService {
    private final Path logPath;
    private final PriceService priceService;
    private final ListManager listManager;

    public LootLogService(Path dataDir, PriceService priceService, ListManager listManager) {
        this.logPath = dataDir.resolve("runLoot.txt");
        this.priceService = priceService;
        this.listManager = listManager;
    }

    public void logLoot(String floor, List<ChestProfit> claimedChests, int chestCount) {
        long totalCost = 0;
        Map<String, Integer> combinedItems = new LinkedHashMap<>();

        for (ChestProfit chest : claimedChests) {
            totalCost += chest.cost();
            for (ChestItem item : chest.items()) {
                combinedItems.merge(item.id(), item.quantity(), Integer::sum);
            }
        }

        int score = getScoreFromChestCount(chestCount);
        StringBuilder builder = new StringBuilder();
        builder.append(floor)
            .append(' ')
            .append(score)
            .append(' ')
            .append(totalCost);

        for (Map.Entry<String, Integer> item : combinedItems.entrySet()) {
            builder.append(' ')
                .append(item.getKey())
                .append(':')
                .append(item.getValue());
        }

        appendLine(builder.toString());
    }

    public LootSummary summarize(LootFilter filter) {
        LootSummary summary = new LootSummary();

        if (!Files.exists(logPath)) {
            return summary;
        }

        try {
            List<String> lines = Files.readAllLines(logPath);
            Collections.reverse(lines);

            for (String line : lines) {
                if (line.isBlank()) {
                    continue;
                }

                String[] parts = line.split(" ");
                if (parts.length < 3) {
                    continue;
                }

                String floor = parts[0];
                int score;
                long cost;

                try {
                    score = Integer.parseInt(parts[1]);
                    cost = Long.parseLong(parts[2]);
                } catch (NumberFormatException ignored) {
                    continue;
                }

                if (filter.floor() != null && !filter.floor().equalsIgnoreCase(floor)) {
                    continue;
                }
                if (score < filter.minimumScore()) {
                    continue;
                }

                summary.runs++;
                summary.totalChestCost += cost;

                for (int i = 3; i < parts.length; i++) {
                    String[] itemParts = parts[i].split(":");
                    if (itemParts.length != 2) {
                        continue;
                    }

                    String itemId = itemParts[0];
                    int quantity;
                    try {
                        quantity = Integer.parseInt(itemParts[1]);
                    } catch (NumberFormatException ignored) {
                        continue;
                    }

                    summary.quantities.merge(itemId, quantity, Integer::sum);
                }

                if (filter.limit() != null && summary.runs >= filter.limit()) {
                    break;
                }
            }
        } catch (IOException ignored) {
            return summary;
        }

        Map<String, Integer> sorted = new LinkedHashMap<>();
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(summary.quantities.entrySet());
        entries.sort((a, b) -> Double.compare(valueOf(b), valueOf(a)));
        for (Map.Entry<String, Integer> entry : entries) {
            sorted.put(entry.getKey(), entry.getValue());
            summary.totalSellPrice += valueOf(entry);
        }

        summary.quantities.clear();
        summary.quantities.putAll(sorted);
        summary.totalProfit = summary.totalSellPrice - summary.totalChestCost;

        return summary;
    }

    public boolean hasLogs() {
        return Files.exists(logPath);
    }

    private double valueOf(Map.Entry<String, Integer> entry) {
        Double sell = priceService.getSellPrice(entry.getKey(), true, listManager.worthless());
        return (sell == null ? 0.0 : sell) * entry.getValue();
    }

    private int getScoreFromChestCount(int chestCount) {
        if (chestCount == 3) {
            return 229;
        }
        if (chestCount == 4) {
            return 230;
        }
        if (chestCount == 5) {
            return 270;
        }
        if (chestCount == 6) {
            return 300;
        }
        return 0;
    }

    private void appendLine(String line) {
        try {
            Files.createDirectories(logPath.getParent());
            if (!Files.exists(logPath)) {
                Files.writeString(logPath, line);
                return;
            }
            Files.writeString(logPath, Files.readString(logPath) + System.lineSeparator() + line);
        } catch (IOException ignored) {
        }
    }
}
