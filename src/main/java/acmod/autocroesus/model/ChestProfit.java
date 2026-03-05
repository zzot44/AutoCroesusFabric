package acmod.autocroesus.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class ChestProfit {
    private final String chestName;
    private final int slot;
    private final long cost;
    private final double value;
    private final double profit;
    private final List<ChestItem> items;

    public ChestProfit(String chestName, int slot, long cost, double value, double profit, List<ChestItem> items) {
        this.chestName = chestName;
        this.slot = slot;
        this.cost = cost;
        this.value = value;
        this.profit = profit;
        this.items = new ArrayList<>(items);
        this.items.sort(Comparator.comparingDouble(ChestItem::totalValue).reversed());
    }

    public String chestName() {
        return chestName;
    }

    public int slot() {
        return slot;
    }

    public long cost() {
        return cost;
    }

    public double value() {
        return value;
    }

    public double profit() {
        return profit;
    }

    public List<ChestItem> items() {
        return items;
    }

    public boolean hasAlwaysBuy(Set<String> alwaysBuy) {
        return items.stream().anyMatch(item -> alwaysBuy.contains(item.id()));
    }
}
