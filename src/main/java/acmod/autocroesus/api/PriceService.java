package acmod.autocroesus.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PriceService {
    private static final String BAZAAR_URL = "https://api.hypixel.net/skyblock/bazaar";
    private static final String ITEMS_URL = "https://api.hypixel.net/v2/resources/skyblock/items";
    private static final String LOWEST_BIN_URL = "https://moulberry.codes/lowestbin.json";

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "autocroesus-prices");
        thread.setDaemon(true);
        return thread;
    });

    private final Path bazaarCachePath;
    private final Path itemsCachePath;
    private final Path binsCachePath;

    private final Map<String, BazaarValue> bazaarValues = new HashMap<>();
    private final Map<String, SkyblockItem> itemsById = new HashMap<>();
    private final Map<String, SkyblockItem> itemsByName = new HashMap<>();
    private final Map<String, Double> lowestBins = new HashMap<>();

    public PriceService(Path dataDir) {
        this.bazaarCachePath = dataDir.resolve("bzValues.json");
        this.itemsCachePath = dataDir.resolve("items.json");
        this.binsCachePath = dataDir.resolve("binValues.json");
    }

    public void loadCache() {
        loadBazaarCache();
        loadItemsCache();
        loadBinsCache();
    }

    public CompletableFuture<Void> updatePrices() {
        return CompletableFuture.runAsync(() -> {
            String bazaarBody = request(BAZAAR_URL);
            String itemsBody = request(ITEMS_URL);
            String binsBody = request(LOWEST_BIN_URL);

            parseBazaarResponse(bazaarBody);
            parseItemsResponse(itemsBody);
            parseLowestBinResponse(binsBody);

            saveCaches();
        }, executor);
    }

    public Double getSellPrice(String skyblockId, boolean useSellOrder, Set<String> worthless) {
        if (worthless.contains(skyblockId)) {
            return 0.0;
        }

        BazaarValue bazaarValue = bazaarValues.get(skyblockId);
        if (bazaarValue != null) {
            return useSellOrder ? bazaarValue.sellOrderValue : bazaarValue.instaSellValue;
        }

        return lowestBins.get(skyblockId);
    }

    public SkyblockItem getItem(String skyblockId) {
        return itemsById.get(skyblockId);
    }

    public SkyblockItem findItemByName(String name) {
        return itemsByName.get(name);
    }

    public boolean itemExists(String itemId) {
        return itemsById.containsKey(itemId) || bazaarValues.containsKey(itemId);
    }

    public List<SkyblockItem> allItems() {
        return itemsById.values().stream()
            .sorted(Comparator.comparing(SkyblockItem::id))
            .toList();
    }

    private String request(String url) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build();

        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
        } catch (IOException | InterruptedException exception) {
            throw new RuntimeException("Failed to request " + url, exception);
        }
    }

    private void parseBazaarResponse(String responseBody) {
        JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
        if (!root.get("success").getAsBoolean()) {
            throw new IllegalStateException("Hypixel bazaar API returned success=false");
        }

        bazaarValues.clear();
        JsonObject products = root.getAsJsonObject("products");
        for (Map.Entry<String, JsonElement> entry : products.entrySet()) {
            JsonObject product = entry.getValue().getAsJsonObject();
            JsonObject quickStatus = product.getAsJsonObject("quick_status");

            double sellOrderValue = quickStatus.get("buyPrice").getAsDouble();
            double instaSellValue = quickStatus.get("sellPrice").getAsDouble();

            JsonArray buySummary = product.getAsJsonArray("buy_summary");
            if (!buySummary.isEmpty()) {
                sellOrderValue = topAverage(buySummary);
            }

            JsonArray sellSummary = product.getAsJsonArray("sell_summary");
            if (!sellSummary.isEmpty()) {
                instaSellValue = topAverage(sellSummary);
            }

            bazaarValues.put(entry.getKey(), new BazaarValue(sellOrderValue, instaSellValue));
        }
    }

    private void parseItemsResponse(String responseBody) {
        JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
        if (!root.get("success").getAsBoolean()) {
            throw new IllegalStateException("Hypixel items API returned success=false");
        }

        itemsById.clear();
        itemsByName.clear();

        JsonArray items = root.getAsJsonArray("items");
        for (JsonElement element : items) {
            JsonObject item = element.getAsJsonObject();
            String id = item.get("id").getAsString();
            String name = item.get("name").getAsString();
            String tier = item.has("tier") ? item.get("tier").getAsString() : "COMMON";

            SkyblockItem skyblockItem = new SkyblockItem(id, name, tier);
            itemsById.put(id, skyblockItem);

            if (!id.startsWith("STARRED_") && !itemsByName.containsKey(name)) {
                itemsByName.put(name, skyblockItem);
            }
        }
    }

    private void parseLowestBinResponse(String responseBody) {
        JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
        lowestBins.clear();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            lowestBins.put(entry.getKey(), entry.getValue().getAsDouble());
        }
    }

    private double topAverage(JsonArray summary) {
        int sampleSize = Math.min(5, summary.size());
        double total = 0;
        for (int i = 0; i < sampleSize; i++) {
            total += summary.get(i).getAsJsonObject().get("pricePerUnit").getAsDouble();
        }
        return total / sampleSize;
    }

    private void saveCaches() {
        try {
            Files.createDirectories(bazaarCachePath.getParent());
            Files.writeString(bazaarCachePath, gson.toJson(bazaarValues));
            Files.writeString(itemsCachePath, gson.toJson(new ArrayList<>(itemsById.values())));
            Files.writeString(binsCachePath, gson.toJson(lowestBins));
        } catch (IOException exception) {
            throw new RuntimeException("Failed to save cache", exception);
        }
    }

    private void loadBazaarCache() {
        if (!Files.exists(bazaarCachePath)) {
            return;
        }

        try {
            JsonObject root = JsonParser.parseString(Files.readString(bazaarCachePath)).getAsJsonObject();
            bazaarValues.clear();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                JsonObject value = entry.getValue().getAsJsonObject();
                bazaarValues.put(entry.getKey(), new BazaarValue(
                    value.get("sellOrderValue").getAsDouble(),
                    value.get("instaSellValue").getAsDouble()
                ));
            }
        } catch (Exception ignored) {
        }
    }

    private void loadItemsCache() {
        if (!Files.exists(itemsCachePath)) {
            return;
        }

        try {
            JsonArray array = JsonParser.parseString(Files.readString(itemsCachePath)).getAsJsonArray();
            itemsById.clear();
            itemsByName.clear();

            for (JsonElement element : array) {
                JsonObject item = element.getAsJsonObject();
                SkyblockItem skyblockItem = new SkyblockItem(
                    item.get("id").getAsString(),
                    item.get("name").getAsString(),
                    item.has("tier") ? item.get("tier").getAsString() : "COMMON"
                );

                itemsById.put(skyblockItem.id(), skyblockItem);
                if (!skyblockItem.id().startsWith("STARRED_") && !itemsByName.containsKey(skyblockItem.name())) {
                    itemsByName.put(skyblockItem.name(), skyblockItem);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void loadBinsCache() {
        if (!Files.exists(binsCachePath)) {
            return;
        }

        try {
            JsonObject object = JsonParser.parseString(Files.readString(binsCachePath)).getAsJsonObject();
            lowestBins.clear();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                lowestBins.put(entry.getKey(), entry.getValue().getAsDouble());
            }
        } catch (Exception ignored) {
        }
    }

    private record BazaarValue(double sellOrderValue, double instaSellValue) {
    }

    public record SkyblockItem(String id, String name, String tier) {
    }
}
