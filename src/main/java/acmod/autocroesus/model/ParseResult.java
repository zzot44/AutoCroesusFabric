package acmod.autocroesus.model;

public record ParseResult(boolean success, ChestProfit chestProfit, String error) {
    public static ParseResult ok(ChestProfit chestProfit) {
        return new ParseResult(true, chestProfit, null);
    }

    public static ParseResult fail(String error) {
        return new ParseResult(false, null, error);
    }
}
