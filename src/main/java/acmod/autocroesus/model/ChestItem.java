package acmod.autocroesus.model;

public record ChestItem(String id, int quantity, double unitValue, String displayName) {
    public double totalValue() {
        return unitValue * quantity;
    }
}
