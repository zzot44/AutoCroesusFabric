package acmod.autocroesus.config;

import acmod.autocroesus.util.Chat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ListManager {
    private static final List<String> ALWAYS_BUY_DEFAULTS = List.of(
        "NECRON_HANDLE",
        "DARK_CLAYMORE",
        "FIRST_MASTER_STAR",
        "SECOND_MASTER_STAR",
        "THIRD_MASTER_STAR",
        "FOURTH_MASTER_STAR",
        "FIFTH_MASTER_STAR",
        "SHADOW_FURY",
        "SHADOW_WARP_SCROLL",
        "IMPLOSION_SCROLL",
        "WITHER_SHIELD_SCROLL",
        "DYE_LIVID"
    );

    private static final List<String> WORTHLESS_DEFAULTS = List.of(
        "DUNGEON_DISC_5",
        "DUNGEON_DISC_4",
        "DUNGEON_DISC_3",
        "DUNGEON_DISC_2",
        "DUNGEON_DISC_1",
        "MAXOR_THE_FISH",
        "STORM_THE_FISH",
        "GOLDOR_THE_FISH",
        "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_1",
        "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_2",
        "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_3",
        "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_4",
        "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_5",
        "ENCHANTMENT_ULTIMATE_COMBO_1",
        "ENCHANTMENT_ULTIMATE_COMBO_2",
        "ENCHANTMENT_ULTIMATE_COMBO_3",
        "ENCHANTMENT_ULTIMATE_COMBO_4",
        "ENCHANTMENT_ULTIMATE_COMBO_5",
        "ENCHANTMENT_ULTIMATE_BANK_1",
        "ENCHANTMENT_ULTIMATE_BANK_2",
        "ENCHANTMENT_ULTIMATE_BANK_3",
        "ENCHANTMENT_ULTIMATE_BANK_4",
        "ENCHANTMENT_ULTIMATE_BANK_5",
        "ENCHANTMENT_ULTIMATE_JERRY_1",
        "ENCHANTMENT_ULTIMATE_JERRY_2",
        "ENCHANTMENT_ULTIMATE_JERRY_3",
        "ENCHANTMENT_ULTIMATE_JERRY_4",
        "ENCHANTMENT_ULTIMATE_JERRY_5",
        "ENCHANTMENT_FEATHER_FALLING_6",
        "ENCHANTMENT_FEATHER_FALLING_7",
        "ENCHANTMENT_FEATHER_FALLING_8",
        "ENCHANTMENT_FEATHER_FALLING_9",
        "ENCHANTMENT_FEATHER_FALLING_10",
        "ENCHANTMENT_INFINITE_QUIVER_6",
        "ENCHANTMENT_INFINITE_QUIVER_7",
        "ENCHANTMENT_INFINITE_QUIVER_8",
        "ENCHANTMENT_INFINITE_QUIVER_9",
        "ENCHANTMENT_INFINITE_QUIVER_10"
    );

    private final Path alwaysBuyPath;
    private final Path worthlessPath;

    private final Set<String> alwaysBuy = new LinkedHashSet<>();
    private final Set<String> worthless = new LinkedHashSet<>();

    public ListManager(Path dataDir) {
        this.alwaysBuyPath = dataDir.resolve("always_buy.txt");
        this.worthlessPath = dataDir.resolve("worthless.txt");
    }

    public void load() {
        if (!Files.exists(alwaysBuyPath)) {
            resetAlwaysBuy();
        }
        if (!Files.exists(worthlessPath)) {
            resetWorthless();
        }

        alwaysBuy.clear();
        alwaysBuy.addAll(readList(alwaysBuyPath));

        worthless.clear();
        worthless.addAll(readList(worthlessPath));
    }

    public void resetAlwaysBuy() {
        writeList(alwaysBuyPath, ALWAYS_BUY_DEFAULTS);
        alwaysBuy.clear();
        alwaysBuy.addAll(ALWAYS_BUY_DEFAULTS);
        Chat.info("Always Buy list reset.");
    }

    public void resetWorthless() {
        writeList(worthlessPath, WORTHLESS_DEFAULTS);
        worthless.clear();
        worthless.addAll(WORTHLESS_DEFAULTS);
        Chat.info("Worthless list reset.");
    }

    public Set<String> alwaysBuy() {
        return alwaysBuy;
    }

    public Set<String> worthless() {
        return worthless;
    }

    public boolean toggleAlwaysBuy(String id) {
        String upper = id.toUpperCase();
        boolean added;
        if (alwaysBuy.contains(upper)) {
            alwaysBuy.remove(upper);
            added = false;
        } else {
            alwaysBuy.add(upper);
            added = true;
        }
        writeList(alwaysBuyPath, alwaysBuy);
        return added;
    }

    public boolean toggleWorthless(String id) {
        String upper = id.toUpperCase();
        boolean added;
        if (worthless.contains(upper)) {
            worthless.remove(upper);
            added = false;
        } else {
            worthless.add(upper);
            added = true;
        }
        writeList(worthlessPath, worthless);
        return added;
    }

    private List<String> readList(Path path) {
        try {
            List<String> lines = Files.readAllLines(path);
            List<String> cleaned = new ArrayList<>();
            for (String line : lines) {
                String item = line.trim();
                if (!item.isEmpty()) {
                    cleaned.add(item.toUpperCase());
                }
            }
            return cleaned;
        } catch (IOException ignored) {
            return new ArrayList<>();
        }
    }

    private void writeList(Path path, Iterable<String> values) {
        try {
            Files.createDirectories(path.getParent());
            List<String> out = new ArrayList<>();
            for (String value : values) {
                out.add(value);
            }
            Files.write(path, out);
        } catch (IOException ignored) {
        }
    }
}
