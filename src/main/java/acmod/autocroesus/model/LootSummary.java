package acmod.autocroesus.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class LootSummary {
    public int runs;
    public long totalChestCost;
    public double totalSellPrice;
    public double totalProfit;
    public final Map<String, Integer> quantities = new LinkedHashMap<>();
}
