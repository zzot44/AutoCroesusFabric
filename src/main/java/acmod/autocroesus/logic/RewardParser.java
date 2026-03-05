package acmod.autocroesus.logic;

import acmod.autocroesus.api.PriceService;
import acmod.autocroesus.config.ListManager;
import acmod.autocroesus.model.ChestItem;
import acmod.autocroesus.model.ChestProfit;
import acmod.autocroesus.model.ParseResult;
import acmod.autocroesus.util.RomanNumerals;
import acmod.autocroesus.util.TooltipUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RewardParser {
    private static final Pattern CHEST_PATTERN = Pattern.compile(
        "^(Wood(?:en)?|Gold|Diamond|Emerald|Obsidian|Bedrock)(?: Chest)?$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern COST_PATTERN = Pattern.compile("^([\\d,]+) Coins$");
    private static final Pattern BOOK_PATTERN = Pattern.compile("^Enchanted Book \\((Ultimate )?([\\w ':-]+) (\\d+|[IVXLCDM]+)\\)$");
    private static final Pattern ESSENCE_PATTERN = Pattern.compile("^([A-Za-z ]+) Essence x(\\d+)$");
    private static final Pattern PET_PATTERN = Pattern.compile("^\\[Lvl 1\\] ([A-Za-z' ]+)$");
    private static final Pattern GENERIC_QUANTITY_PATTERN = Pattern.compile("^(.+?) x(\\d+)$");

    private static final Map<String, String> ITEM_REPLACEMENTS = Map.ofEntries(
        Map.entry("Shiny Wither Boots", "WITHER_BOOTS"),
        Map.entry("Shiny Wither Leggings", "WITHER_LEGGINGS"),
        Map.entry("Shiny Wither Chestplate", "WITHER_CHESTPLATE"),
        Map.entry("Shiny Wither Helmet", "WITHER_HELMET"),
        Map.entry("Shiny Necron's Handle", "NECRON_HANDLE"),
        Map.entry("Wither Shard", "SHARD_WITHER"),
        Map.entry("Thorn Shard", "SHARD_THORN"),
        Map.entry("Apex Dragon Shard", "SHARD_APEX_DRAGON"),
        Map.entry("Power Dragon Shard", "SHARD_POWER_DRAGON"),
        Map.entry("Scarf Shard", "SHARD_SCARF"),
        Map.entry("Necron Dye", "DYE_NECRON"),
        Map.entry("Livid Dye", "DYE_LIVID")
    );

    private final PriceService priceService;
    private final ListManager listManager;

    public RewardParser(PriceService priceService, ListManager listManager) {
        this.priceService = priceService;
        this.listManager = listManager;
    }

    public ParseResult parseChest(MinecraftClient client, ItemStack stack, int slotIndex) {
        String chestName = normalizeName(stack.getName().getString());
        Matcher chestMatcher = CHEST_PATTERN.matcher(chestName);
        if (!chestMatcher.matches()) {
            return ParseResult.fail("Not a chest: " + chestName);
        }

        List<String> tooltip = TooltipUtil.plainTooltip(client, stack);
        int costHeaderIndex = findCostHeader(tooltip);
        if (costHeaderIndex == -1 || costHeaderIndex + 1 >= tooltip.size()) {
            return ParseResult.fail("Could not find chest cost in tooltip for " + chestName);
        }

        long cost = parseCost(tooltip.get(costHeaderIndex + 1));
        if (cost < 0) {
            return ParseResult.fail("Could not parse chest cost line: " + tooltip.get(costHeaderIndex + 1));
        }

        List<String> rewardLines = extractRewardLines(tooltip, costHeaderIndex);
        if (rewardLines.isEmpty()) {
            return ParseResult.fail("Could not find reward lines for " + chestName);
        }

        List<ChestItem> items = new ArrayList<>();
        double totalValue = 0;
        for (String rewardLine : rewardLines) {
            RewardLine parsedLine = parseRewardLine(rewardLine);
            if (parsedLine == null) {
                return ParseResult.fail("Could not parse reward line: " + rewardLine);
            }

            String resolvedId = resolvePriceId(parsedLine.skyblockId());
            Double value = priceService.getSellPrice(resolvedId, true, listManager.worthless());
            if (value == null) {
                return ParseResult.fail("Could not find value for reward: " + rewardLine + " (" + parsedLine.skyblockId() + ")");
            }

            items.add(new ChestItem(resolvedId, parsedLine.quantity(), value, parsedLine.displayName()));
            totalValue += value * parsedLine.quantity();
        }

        double profit = totalValue - cost;
        return ParseResult.ok(new ChestProfit(normalizeChestName(chestMatcher.group(1)), slotIndex, cost, totalValue, profit, items));
    }

    public boolean isPotentialChest(ItemStack stack) {
        String name = normalizeName(stack.getName().getString());
        return CHEST_PATTERN.matcher(name).matches();
    }

    public void sortChests(List<ChestProfit> chests) {
        chests.sort((a, b) -> {
            boolean aAlways = a.hasAlwaysBuy(listManager.alwaysBuy());
            boolean bAlways = b.hasAlwaysBuy(listManager.alwaysBuy());
            if (aAlways != bAlways) {
                return bAlways ? 1 : -1;
            }
            return Comparator.comparingDouble(ChestProfit::profit).reversed().compare(a, b);
        });
    }

    public String getFormattedNameFromId(String itemId) {
        if (itemId.startsWith("ENCHANTMENT_ULTIMATE_")) {
            Matcher matcher = Pattern.compile("^ENCHANTMENT_ULTIMATE_([\\w_]+)_(\\d+)$").matcher(itemId);
            if (matcher.matches()) {
                String enchant = matcher.group(1).replace('_', ' ').toLowerCase();
                int tier = Integer.parseInt(matcher.group(2));
                return "Enchanted Book (Ultimate " + titleCase(enchant) + " " + encodeNumeral(tier) + ")";
            }
        }

        if (itemId.startsWith("ENCHANTMENT_")) {
            Matcher matcher = Pattern.compile("^ENCHANTMENT_([\\w_]+)_(\\d+)$").matcher(itemId);
            if (matcher.matches()) {
                String enchant = matcher.group(1).replace('_', ' ').toLowerCase();
                int tier = Integer.parseInt(matcher.group(2));
                return "Enchanted Book (" + titleCase(enchant) + " " + encodeNumeral(tier) + ")";
            }
        }

        if (itemId.startsWith("ESSENCE_")) {
            return titleCase(itemId.substring("ESSENCE_".length()).replace('_', ' ').toLowerCase()) + " Essence";
        }

        PriceService.SkyblockItem item = priceService.getItem(itemId);
        return item == null ? itemId : item.name();
    }

    private int findCostHeader(List<String> tooltip) {
        for (int i = 0; i < tooltip.size(); i++) {
            if ("Cost".equalsIgnoreCase(tooltip.get(i).trim())) {
                return i;
            }
        }
        return -1;
    }

    private long parseCost(String line) {
        String trimmed = line.trim();
        if (trimmed.equalsIgnoreCase("FREE")) {
            return 0L;
        }

        Matcher matcher = COST_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            return -1L;
        }

        return Long.parseLong(matcher.group(1).replace(",", ""));
    }

    private List<String> extractRewardLines(List<String> tooltip, int costHeaderIndex) {
        int start = Math.min(2, tooltip.size());
        if (costHeaderIndex <= start) {
            start = 1;
        }

        List<String> rewardLines = new ArrayList<>();
        for (int i = start; i < costHeaderIndex; i++) {
            String line = tooltip.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.equalsIgnoreCase("Cost") || line.equalsIgnoreCase("Open Reward Chest to claim your loot!")) {
                continue;
            }
            rewardLines.add(line);
        }

        return rewardLines;
    }

    private RewardLine parseRewardLine(String line) {
        Matcher bookMatcher = BOOK_PATTERN.matcher(line);
        if (bookMatcher.matches()) {
            String prefix = bookMatcher.group(1) == null ? "" : "ULTIMATE_";
            String enchant = bookMatcher.group(2).toUpperCase().replace(' ', '_');
            Integer tier = parseTier(bookMatcher.group(3));
            if (tier == null) {
                return null;
            }

            String id = "ENCHANTMENT_" + prefix + enchant + "_" + tier;
            id = id.replace("ULTIMATE_ULTIMATE_", "ULTIMATE_");
            return new RewardLine(id, 1, line);
        }

        Matcher essenceMatcher = ESSENCE_PATTERN.matcher(line);
        if (essenceMatcher.matches()) {
            String id = "ESSENCE_" + essenceMatcher.group(1).toUpperCase().replace(' ', '_');
            int quantity = Integer.parseInt(essenceMatcher.group(2));
            return new RewardLine(id, quantity, line);
        }

        Matcher petMatcher = PET_PATTERN.matcher(line);
        if (petMatcher.matches()) {
            String id = petMatcher.group(1).toUpperCase().replace(' ', '_') + ";3";
            return new RewardLine(id, 1, line);
        }

        int quantity = 1;
        String itemName = line;
        Matcher quantityMatcher = GENERIC_QUANTITY_PATTERN.matcher(line);
        if (quantityMatcher.matches()) {
            itemName = quantityMatcher.group(1);
            quantity = Integer.parseInt(quantityMatcher.group(2));
        }

        String replacement = ITEM_REPLACEMENTS.get(itemName);
        if (replacement != null) {
            return new RewardLine(replacement, quantity, line);
        }

        PriceService.SkyblockItem item = priceService.findItemByName(itemName);
        if (item == null) {
            return null;
        }

        return new RewardLine(item.id(), quantity, line);
    }

    private Integer parseTier(String tierString) {
        try {
            return Integer.parseInt(tierString);
        } catch (NumberFormatException ignored) {
            return RomanNumerals.decode(tierString);
        }
    }

    private String encodeNumeral(int number) {
        if (number <= 0) {
            return String.valueOf(number);
        }

        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] numerals = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder builder = new StringBuilder();
        int remaining = number;

        for (int i = 0; i < values.length; i++) {
            while (remaining >= values[i]) {
                builder.append(numerals[i]);
                remaining -= values[i];
            }
        }

        return builder.toString();
    }

    private String titleCase(String input) {
        String[] words = input.split(" ");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (word.isEmpty()) {
                continue;
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            if (i < words.length - 1) {
                out.append(' ');
            }
        }
        return out.toString();
    }

    private String normalizeName(String input) {
        return input == null ? "" : input.trim().replaceAll("\\s+", " ");
    }

    private String normalizeChestName(String rawChestName) {
        String normalized = normalizeName(rawChestName).toLowerCase();
        return switch (normalized) {
            case "wood", "wooden" -> "Wood";
            case "gold" -> "Gold";
            case "diamond" -> "Diamond";
            case "emerald" -> "Emerald";
            case "obsidian" -> "Obsidian";
            case "bedrock" -> "Bedrock";
            default -> titleCase(normalized);
        };
    }

    private String resolvePriceId(String rawId) {
        // Some ultimate books appear as "Enchanted Book (Combo I)" in plain text tooltips.
        // If the normal enchant ID has no price, fall back to ENCHANTMENT_ULTIMATE_*.
        if (rawId.startsWith("ENCHANTMENT_") && !rawId.startsWith("ENCHANTMENT_ULTIMATE_")) {
            Double normalValue = priceService.getSellPrice(rawId, true, listManager.worthless());
            if (normalValue != null) {
                return rawId;
            }

            String ultimateId = rawId.replaceFirst("^ENCHANTMENT_", "ENCHANTMENT_ULTIMATE_");
            Double ultimateValue = priceService.getSellPrice(ultimateId, true, listManager.worthless());
            if (ultimateValue != null) {
                return ultimateId;
            }
        }

        return rawId;
    }

    private record RewardLine(String skyblockId, int quantity, String displayName) {
    }
}
