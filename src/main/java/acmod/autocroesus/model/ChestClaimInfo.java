package acmod.autocroesus.model;

public class ChestClaimInfo {
    private final String floor;
    private final int page;
    private final int runSlot;
    private Integer chestSlot;
    private boolean skipKismet;

    public ChestClaimInfo(String floor, int page, int runSlot) {
        this.floor = floor;
        this.page = page;
        this.runSlot = runSlot;
    }

    public String floor() {
        return floor;
    }

    public int page() {
        return page;
    }

    public int runSlot() {
        return runSlot;
    }

    public Integer chestSlot() {
        return chestSlot;
    }

    public void chestSlot(Integer chestSlot) {
        this.chestSlot = chestSlot;
    }

    public boolean skipKismet() {
        return skipKismet;
    }

    public void skipKismet(boolean skipKismet) {
        this.skipKismet = skipKismet;
    }
}
