package acmod.autocroesus.model;

import java.util.ArrayList;
import java.util.List;

public class ConfigData {
    public long lastApiUpdate = 0L;

    public int minClickDelay = 250;
    public int firstClickDelay = 250;
    public boolean noClick = false;
    public boolean showChestInfo = false;

    public boolean useKismets = false;
    public int kismetMinProfit = 2_000_000;
    public List<String> kismetFloors = new ArrayList<>();

    public boolean useChestKeys = true;
    public int chestKeyMinProfit = 200_000;
}
