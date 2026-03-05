package acmod.autocroesus.model;

public record LootFilter(int minimumScore, String floor, Integer limit) {
    public static LootFilter defaults() {
        return new LootFilter(300, null, null);
    }
}
