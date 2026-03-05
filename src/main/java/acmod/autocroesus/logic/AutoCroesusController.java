package acmod.autocroesus.logic;

import acmod.autocroesus.api.PriceService;
import acmod.autocroesus.config.ConfigManager;
import acmod.autocroesus.config.ListManager;
import acmod.autocroesus.model.ChestClaimInfo;
import acmod.autocroesus.model.ChestProfit;
import acmod.autocroesus.model.ConfigData;
import acmod.autocroesus.model.ParseResult;
import acmod.autocroesus.util.Chat;
import acmod.autocroesus.util.RomanNumerals;
import acmod.autocroesus.util.TooltipUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoCroesusController {
    private static final int CLICK_JITTER_MS = 20;
    private static final List<Integer> RUN_SLOTS = List.of(
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    );

    private static final Pattern RUN_GUI_PATTERN = Pattern.compile("^(?:Master )?Catacombs - ([FloorVI\\d ]*)$");
    private static final Pattern FLOOR_PATTERN = Pattern.compile("Floor (\\w+)");
    private static final Pattern PAGE_PATTERN = Pattern.compile("Page (\\d+)");
    private static final Pattern CHEST_SCREEN_PATTERN = Pattern.compile("^(\\w+) Chest$");

    private final ConfigManager configManager;
    private final ListManager listManager;
    private final PriceService priceService;
    private final RewardParser rewardParser;
    private final LootLogService lootLogService;

    private boolean autoClaiming;
    private final Set<Integer> failedIndexes = new HashSet<>();
    private final Set<Integer> loggedIndexes = new HashSet<>();

    private List<ChestProfit> currentChestData;
    private ChestClaimInfo chestClaimInfo;

    private boolean waitingForCroesus;
    private boolean waitingForRunToOpen;
    private boolean waitingForChestToOpen;

    private Integer lastPageOn;
    private Integer waitingOnPage;

    private boolean tryingToKismet;
    private boolean canKismet = true;

    private long lastClick;
    private Integer indexToClick;
    private UUID pendingCroesusTarget;
    private long lastFrameNanos;
    private long alignedSinceMs = -1L;
    private long lastCroesusInteractAttempt;
    private long nextCroesusInteractAtMs;
    private int currentGuiSyncId = -1;
    private long currentGuiOpenedAtMs;
    private long currentGuiFirstClickUnlockAtMs;
    private boolean firstClickDoneInCurrentGui;
    private long nextAllowedGuiClickAtMs;
    private boolean bypassNextClickTiming;
    private long requiredAlignmentHoldMs = 140L;
    private float currentYawSpeed = 120f;
    private float currentPitchSpeed = 90f;
    private long nextRotationProfileUpdateAtMs;
    private float aimYawOffset;
    private float aimPitchOffset;
    private long nextAimOffsetUpdateAtMs;

    public AutoCroesusController(
        ConfigManager configManager,
        ListManager listManager,
        PriceService priceService,
        RewardParser rewardParser,
        LootLogService lootLogService
    ) {
        this.configManager = configManager;
        this.listManager = listManager;
        this.priceService = priceService;
        this.rewardParser = rewardParser;
        this.lootLogService = lootLogService;
    }

    public void onTick(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) {
            return;
        }

        updateCurrentGuiState(client);
        performQueuedClick(client);

        if (isKillSwitchPressed(client)) {
            reset();
            return;
        }

        if (autoClaiming && !waitingForCroesus && !(client.currentScreen instanceof HandledScreen<?>)) {
            if (waitingForRunToOpen || waitingForChestToOpen) {
                Chat.warn("Sequence out of sync, stopping.");
                reset();
                return;
            }
            startClaiming(client);
        }

        handleCroesusMenu(client);
        handleRunMenu(client);
        handleChestScreen(client);

        if (!autoClaiming && !config().showChestInfo && !isRunGui(client.currentScreen)) {
            currentChestData = null;
        }
    }

    public void onRenderFrame(MinecraftClient client) {
        if (client == null || client.player == null || client.world == null) {
            return;
        }

        if (!waitingForCroesus || pendingCroesusTarget == null) {
            lastFrameNanos = 0L;
            return;
        }

        long nowNanos = System.nanoTime();
        double deltaSeconds = lastFrameNanos == 0L
            ? (1.0 / 60.0)
            : (nowNanos - lastFrameNanos) / 1_000_000_000.0;
        lastFrameNanos = nowNanos;
        deltaSeconds = MathHelper.clamp(deltaSeconds, 1.0 / 300.0, 0.05);

        updateCroesusRotationAndInteract(client, deltaSeconds);
    }

    public ConfigData config() {
        return configManager.config();
    }

    public ListManager listManager() {
        return listManager;
    }

    public PriceService priceService() {
        return priceService;
    }

    public RewardParser rewardParser() {
        return rewardParser;
    }

    public LootLogService lootLogService() {
        return lootLogService;
    }

    public boolean isAutoClaiming() {
        return autoClaiming;
    }

    public List<ChestProfit> currentChestData() {
        return currentChestData;
    }

    public void enableAutoClaiming() {
        autoClaiming = true;
    }

    public void reset() {
        autoClaiming = false;
        chestClaimInfo = null;

        waitingForCroesus = false;
        waitingForRunToOpen = false;
        waitingForChestToOpen = false;
        lastPageOn = null;
        waitingOnPage = null;

        indexToClick = null;
        tryingToKismet = false;
        canKismet = true;
        pendingCroesusTarget = null;
        lastFrameNanos = 0L;
        alignedSinceMs = -1L;
        lastCroesusInteractAttempt = 0L;
        nextCroesusInteractAtMs = 0L;
        currentGuiSyncId = -1;
        currentGuiOpenedAtMs = 0L;
        currentGuiFirstClickUnlockAtMs = 0L;
        firstClickDoneInCurrentGui = false;
        nextAllowedGuiClickAtMs = 0L;
        bypassNextClickTiming = false;
        requiredAlignmentHoldMs = 140L;
        currentYawSpeed = 120f;
        currentPitchSpeed = 90f;
        nextRotationProfileUpdateAtMs = 0L;
        aimYawOffset = 0f;
        aimPitchOffset = 0f;
        nextAimOffsetUpdateAtMs = 0L;

        failedIndexes.clear();
    }

    private void performQueuedClick(MinecraftClient client) {
        long now = System.currentTimeMillis();
        if (indexToClick == null) {
            return;
        }
        if (!bypassNextClickTiming && now < nextAllowedGuiClickAtMs) {
            return;
        }

        if (!(client.currentScreen instanceof HandledScreen<?>)) {
            return;
        }

        ScreenHandler handler = client.player.currentScreenHandler;
        if (handler == null || indexToClick >= handler.slots.size()) {
            indexToClick = null;
            return;
        }

        if (!bypassNextClickTiming && !firstClickDoneInCurrentGui) {
            if (now < currentGuiFirstClickUnlockAtMs) {
                return;
            }
        }

        int slotToClick = indexToClick;
        indexToClick = null;
        boolean bypassTiming = bypassNextClickTiming;
        bypassNextClickTiming = false;
        lastClick = now;
        firstClickDoneInCurrentGui = true;
        if (!bypassTiming) {
            nextAllowedGuiClickAtMs = now + jitteredDelayMs(config().minClickDelay);
        }

        if (config().noClick) {
            Chat.info("[NoClick] Would click slot " + slotToClick);
            return;
        }

        client.interactionManager.clickSlot(handler.syncId, slotToClick, 0, SlotActionType.PICKUP, client.player);
    }

    private void updateCurrentGuiState(MinecraftClient client) {
        if (!(client.currentScreen instanceof HandledScreen<?>)) {
            currentGuiSyncId = -1;
            currentGuiOpenedAtMs = 0L;
            currentGuiFirstClickUnlockAtMs = 0L;
            firstClickDoneInCurrentGui = false;
            nextAllowedGuiClickAtMs = 0L;
            bypassNextClickTiming = false;
            return;
        }

        ScreenHandler handler = client.player.currentScreenHandler;
        if (handler == null) {
            return;
        }

        if (handler.syncId != currentGuiSyncId) {
            long now = System.currentTimeMillis();
            currentGuiSyncId = handler.syncId;
            currentGuiOpenedAtMs = now;
            currentGuiFirstClickUnlockAtMs = now + jitteredDelayMs(config().firstClickDelay);
            firstClickDoneInCurrentGui = false;
            nextAllowedGuiClickAtMs = now;
            bypassNextClickTiming = false;
        }
    }

    private void startClaiming(MinecraftClient client) {
        autoClaiming = true;

        if (!beginCroesusInteraction(client)) {
            autoClaiming = false;
            Chat.warn("Could not find Croesus (too far away or not found).");
            reset();
            return;
        }

        waitingForCroesus = true;
    }

    private void handleCroesusMenu(MinecraftClient client) {
        if (!isCroesusMenu(client.currentScreen, client)) {
            return;
        }

        if (!autoClaiming || waitingForRunToOpen) {
            return;
        }

        waitingForCroesus = false;
        pendingCroesusTarget = null;
        alignedSinceMs = -1L;
        lastFrameNanos = 0L;

        if (chestClaimInfo == null) {
            currentChestData = new ArrayList<>();
        }

        ScreenHandler handler = client.player.currentScreenHandler;
        Integer page = getCurrentPage(client, handler);
        if (page == null) {
            return;
        }
        if (waitingOnPage != null && !waitingOnPage.equals(page)) {
            return;
        }

        if (chestClaimInfo != null) {
            if (page != chestClaimInfo.page()) {
                if (lastPageOn != null && lastPageOn.equals(page)) {
                    return;
                }

                lastPageOn = page;
                queueClick(53);
                return;
            }

            lastPageOn = null;
            queueClick(chestClaimInfo.runSlot());
            waitingForRunToOpen = true;
            return;
        }

        UnopenedChest unopened = findUnopenedChest(client, handler, page);
        if (unopened.slot() != null) {
            chestClaimInfo = new ChestClaimInfo(unopened.floor(), page, unopened.slot());
            waitingForRunToOpen = true;
            queueClick(unopened.slot());
            return;
        }

        ItemStack nextPage = handler.getSlot(53).getStack();
        if (!nextPage.isEmpty() && nextPage.isOf(Items.ARROW)) {
            if (lastPageOn != null && lastPageOn.equals(page)) {
                return;
            }

            lastPageOn = page;
            waitingOnPage = page + 1;
            queueClick(53);
            return;
        }

        Chat.success("All chests looted.");
        reset();
        client.player.closeHandledScreen();
    }

    private void handleRunMenu(MinecraftClient client) {
        Screen screen = client.currentScreen;
        if (!isRunGui(screen)) {
            if (chestClaimInfo == null) {
                currentChestData = null;
            }
            return;
        }

        if (!autoClaiming && !config().showChestInfo) {
            return;
        }

        ScreenHandler handler = client.player.currentScreenHandler;
        if (!isInventoryLoaded(handler) || waitingForChestToOpen) {
            return;
        }

        waitingForRunToOpen = false;
        lastPageOn = null;
        waitingOnPage = null;

        if (chestClaimInfo != null && chestClaimInfo.chestSlot() != null) {
            waitingForChestToOpen = true;
            queueClick(chestClaimInfo.chestSlot());
            chestClaimInfo.chestSlot(null);
            return;
        }

        List<ChestProfit> chestData = new ArrayList<>();
        for (int i = 0; i < 27; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isEmpty()) {
                continue;
            }
            if (!rewardParser.isPotentialChest(stack)) {
                continue;
            }

            ParseResult parsed = rewardParser.parseChest(client, stack, i);
            if (!parsed.success()) {
                if (parsed.error() != null && parsed.error().startsWith("Not a chest:")) {
                    continue;
                }
                if (autoClaiming && chestClaimInfo != null) {
                    Chat.warn("Failed to parse chest info: " + parsed.error());
                    failedIndexes.add(toRunIndex(chestClaimInfo.runSlot(), chestClaimInfo.page()));
                    chestClaimInfo = null;
                    queueClick(30);
                }
                continue;
            }

            chestData.add(parsed.chestProfit());
        }

        rewardParser.sortChests(chestData);
        currentChestData = chestData;

        if (!autoClaiming || chestData.isEmpty() || chestClaimInfo == null) {
            return;
        }

        ChestProfit bedrockChest = chestData.stream().filter(chest -> "Bedrock".equals(chest.chestName())).findFirst().orElse(null);
        boolean hasAlwaysBuy = bedrockChest != null && bedrockChest.hasAlwaysBuy(listManager.alwaysBuy());

        if (!hasAlwaysBuy
            && !chestClaimInfo.skipKismet()
            && config().useKismets
            && bedrockChest != null
            && config().kismetFloors.contains(chestClaimInfo.floor())
            && bedrockChest.profit() < config().kismetMinProfit) {
            tryingToKismet = true;
            waitingForChestToOpen = true;
            queueClick(bedrockChest.slot());
            return;
        }

        List<ChestProfit> claimedChests = new ArrayList<>();
        claimedChests.add(chestData.getFirst());
        Chat.info("Claiming " + chestData.getFirst().chestName() + " chest.");

        if (config().useChestKeys && chestData.size() > 1 && chestData.get(1).profit() >= config().chestKeyMinProfit) {
            chestClaimInfo.chestSlot(chestData.get(1).slot());
            claimedChests.add(chestData.get(1));
            Chat.info("Using chest key on " + chestData.get(1).chestName() + " chest.");
        }

        int runIndex = toRunIndex(chestClaimInfo.runSlot(), chestClaimInfo.page());
        if (!loggedIndexes.contains(runIndex)) {
            loggedIndexes.add(runIndex);
            lootLogService.logLoot(chestClaimInfo.floor(), claimedChests, chestData.size());
        }

        waitingForChestToOpen = true;
        queueClick(chestData.getFirst().slot());
        failedIndexes.add(runIndex);
    }

    private void handleChestScreen(MinecraftClient client) {
        if (!waitingForChestToOpen || !(client.currentScreen instanceof HandledScreen<?> handledScreen)) {
            return;
        }

        ScreenHandler handler = client.player.currentScreenHandler;
        if (handler == null || handler.slots.size() < 32) {
            return;
        }
        if (!isChestScreenLoaded(handler)) {
            return;
        }

        // Critical guard: never run confirm logic while still in run listing / Croesus menus.
        if (isRunGui(handledScreen) || isCroesusMenu(handledScreen, client)) {
            return;
        }

        String chestName = parseChestNameFromTitle(handledScreen.getTitle().getString());
        boolean titleLooksLikeChest = chestName != null;
        if (!titleLooksLikeChest) {
            Integer confirmSlot = findOpenRewardChestSlot(client, handler);
            if (confirmSlot == null) {
                return;
            }

            waitingForChestToOpen = false;
            queueClickImmediate(confirmSlot);
            if (chestClaimInfo != null && chestClaimInfo.chestSlot() == null) {
                chestClaimInfo = null;
            }
            return;
        }

        if (tryingToKismet && "Bedrock".equals(chestName) && chestClaimInfo != null && !chestClaimInfo.skipKismet()) {
            tryingToKismet = false;
            ItemStack kismetSlot = handler.getSlot(50).getStack();

            boolean canReroll = !kismetSlot.isEmpty()
                && kismetSlot.getName().getString().contains("Reroll Chest")
                && TooltipUtil.plainTooltip(client, kismetSlot).stream().noneMatch(line -> line.contains("Bring a Kismet Feather"));

            if (!canReroll) {
                waitingForChestToOpen = false;
                canKismet = false;
                Chat.warn("No kismets available. Skipping configured reroll floors.");
                failedIndexes.add(toRunIndex(chestClaimInfo.runSlot(), chestClaimInfo.page()));
                chestClaimInfo = null;
                client.player.closeHandledScreen();
                return;
            }

            boolean alreadyRerolled = TooltipUtil.plainTooltip(client, kismetSlot).stream().anyMatch(line -> line.contains("already rerolled"));
            if (alreadyRerolled) {
                waitingForChestToOpen = false;
                chestClaimInfo.skipKismet(true);
                waitingForRunToOpen = true;
                queueClick(49);
                return;
            }

            waitingForChestToOpen = false;
            chestClaimInfo.skipKismet(true);
            queueClick(50);
            return;
        }

        Integer confirmSlot = findOpenRewardChestSlot(client, handler);
        if (confirmSlot == null) {
            return;
        }

        waitingForChestToOpen = false;
        queueClickImmediate(confirmSlot);

        if (chestClaimInfo != null && chestClaimInfo.chestSlot() == null) {
            chestClaimInfo = null;
        }
    }

    private Integer findOpenRewardChestSlot(MinecraftClient client, ScreenHandler handler) {
        // 1) Preferred: find the explicit button by name/lore.
        for (int i = 0; i < handler.slots.size(); i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isEmpty()) {
                continue;
            }

            String name = stack.getName().getString();
            if (name == null) {
                continue;
            }

            String normalized = name.trim().toLowerCase();
            if (normalized.contains("open reward chest")
                || normalized.contains("claim reward")
                || normalized.contains("claim chest")) {
                return i;
            }

            List<String> lore = TooltipUtil.plainTooltip(client, stack);
            boolean claimLore = lore.stream().map(String::toLowerCase).anyMatch(line ->
                line.contains("open reward chest to claim your loot")
                    || line.contains("click to open")
                    || line.contains("claim your loot")
            );
            if (claimLore) {
                return i;
            }
        }

        // 2) Fallback: classic confirm/reroll area (only if there is no explicit marker).
        if (handler.slots.size() > 31) {
            ItemStack fallback = handler.getSlot(31).getStack();
            if (!fallback.isEmpty()) {
                return 31;
            }
        }
        if (handler.slots.size() > 22) {
            ItemStack fallback = handler.getSlot(22).getStack();
            if (!fallback.isEmpty()) {
                return 22;
            }
        }
        if (handler.slots.size() > 13) {
            ItemStack fallback = handler.getSlot(13).getStack();
            if (!fallback.isEmpty()) {
                return 13;
            }
        }

        return null;
    }

    private boolean isChestScreenLoaded(ScreenHandler handler) {
        int[] probeSlots = {31, 22, 13, 49, 50};
        for (int slot : probeSlots) {
            if (slot >= handler.slots.size()) {
                continue;
            }
            if (!handler.getSlot(slot).getStack().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private String parseChestNameFromTitle(String titleRaw) {
        if (titleRaw == null) {
            return null;
        }

        String normalized = titleRaw.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return null;
        }

        if (normalized.contains("bedrock")) {
            return "Bedrock";
        }
        if (normalized.contains("obsidian")) {
            return "Obsidian";
        }
        if (normalized.contains("emerald")) {
            return "Emerald";
        }
        if (normalized.contains("diamond")) {
            return "Diamond";
        }
        if (normalized.contains("gold")) {
            return "Gold";
        }
        if (normalized.contains("wood")) {
            return "Wood";
        }

        return null;
    }

    private boolean beginCroesusInteraction(MinecraftClient client) {
        Entity target = findCroesusTarget(client);
        if (target == null) {
            return false;
        }

        if (target.squaredDistanceTo(client.player) > 16.0) {
            return false;
        }

        pendingCroesusTarget = target.getUuid();
        alignedSinceMs = -1L;
        lastCroesusInteractAttempt = 0L;
        nextCroesusInteractAtMs = 0L;
        lastFrameNanos = 0L;
        nextRotationProfileUpdateAtMs = 0L;
        nextAimOffsetUpdateAtMs = 0L;
        requiredAlignmentHoldMs = randomBetween(120, 220);
        return true;
    }

    private void updateCroesusRotationAndInteract(MinecraftClient client, double deltaSeconds) {
        long nowMs = System.currentTimeMillis();
        Entity target = client.world.getEntity(pendingCroesusTarget);
        if (target == null) {
            target = findCroesusTarget(client);
            if (target == null) {
                Chat.warn("Lost Croesus target. Stopping auto claim.");
                reset();
                return;
            }
            pendingCroesusTarget = target.getUuid();
        }

        if (target.squaredDistanceTo(client.player) > 16.0) {
            Chat.warn("Croesus moved out of reach. Stopping auto claim.");
            reset();
            return;
        }

        double dx = target.getX() - client.player.getX();
        double dy = target.getY() + target.getStandingEyeHeight() - client.player.getEyeY();
        double dz = target.getZ() - client.player.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        updateRotationHumanizer(nowMs);

        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0) + aimYawOffset;
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontalDistance))) + aimPitchOffset;

        float currentYaw = client.player.getYaw();
        float currentPitch = client.player.getPitch();
        float yawDiff = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;

        float maxYawStep = Math.max(0.12f, (float) (currentYawSpeed * deltaSeconds));
        float maxPitchStep = Math.max(0.12f, (float) (currentPitchSpeed * deltaSeconds));
        float yawStep = MathHelper.clamp(yawDiff, -maxYawStep, maxYawStep);
        float pitchStep = MathHelper.clamp(pitchDiff, -maxPitchStep, maxPitchStep);

        yawStep += randomFloat(-0.055f, 0.055f);
        pitchStep += randomFloat(-0.045f, 0.045f);

        float newYaw = currentYaw + yawStep;
        float newPitch = MathHelper.clamp(currentPitch + pitchStep, -90.0f, 90.0f);

        client.player.setYaw(newYaw);
        client.player.setPitch(newPitch);
        client.player.setHeadYaw(newYaw + aimYawOffset * 0.12f);
        client.player.setBodyYaw(MathHelper.lerp(0.42f, client.player.bodyYaw, newYaw));

        boolean aligned = Math.abs(yawDiff) <= 1.7f && Math.abs(pitchDiff) <= 1.6f;
        if (aligned) {
            if (alignedSinceMs < 0) {
                alignedSinceMs = nowMs;
                requiredAlignmentHoldMs = randomBetween(120, 260);
            }
        } else {
            alignedSinceMs = -1L;
            return;
        }

        if (nowMs - alignedSinceMs < requiredAlignmentHoldMs) {
            return;
        }

        if (nowMs < nextCroesusInteractAtMs) {
            return;
        }

        lastCroesusInteractAttempt = nowMs;
        nextCroesusInteractAtMs = nowMs + jitteredDelayMs((int) Math.max(config().minClickDelay, 900));
        lastClick = nowMs;
        nextAllowedGuiClickAtMs = nowMs + jitteredDelayMs(config().minClickDelay);

        if (config().noClick) {
            Chat.info("[NoClick] Would interact with Croesus.");
            return;
        }

        ActionResult result = client.interactionManager.interactEntity(client.player, target, Hand.MAIN_HAND);
        if (!result.isAccepted()) {
            return;
        }
    }

    private void updateRotationHumanizer(long nowMs) {
        if (nowMs >= nextRotationProfileUpdateAtMs) {
            currentYawSpeed = randomFloat(88f, 158f);
            currentPitchSpeed = randomFloat(68f, 118f);
            nextRotationProfileUpdateAtMs = nowMs + randomBetween(130, 340);
        }

        if (nowMs >= nextAimOffsetUpdateAtMs) {
            aimYawOffset = randomFloat(-0.42f, 0.42f);
            aimPitchOffset = randomFloat(-0.30f, 0.30f);
            nextAimOffsetUpdateAtMs = nowMs + randomBetween(55, 160);
        }
    }

    private int jitteredDelayMs(int baseMs) {
        int clamped = Math.max(0, baseMs);
        int jitter = randomBetween(-CLICK_JITTER_MS, CLICK_JITTER_MS);
        return Math.max(0, clamped + jitter);
    }

    private int randomBetween(int minInclusive, int maxInclusive) {
        return ThreadLocalRandom.current().nextInt(minInclusive, maxInclusive + 1);
    }

    private float randomFloat(float minInclusive, float maxInclusive) {
        return ThreadLocalRandom.current().nextFloat() * (maxInclusive - minInclusive) + minInclusive;
    }

    private void queueClick(int slot) {
        indexToClick = slot;
        bypassNextClickTiming = false;
    }

    private void queueClickImmediate(int slot) {
        indexToClick = slot;
        bypassNextClickTiming = true;
    }

    private Entity findCroesusTarget(MinecraftClient client) {
        List<ArmorStandEntity> stands = client.world.getEntitiesByClass(
            ArmorStandEntity.class,
            client.player.getBoundingBox().expand(12),
            stand -> "Croesus".equals(stand.getName().getString())
        );

        if (stands.isEmpty()) {
            return null;
        }

        ArmorStandEntity displayStand = stands.getFirst();
        List<OtherClientPlayerEntity> npcs = client.world.getEntitiesByClass(
            OtherClientPlayerEntity.class,
            displayStand.getBoundingBox().expand(0.4),
            npc -> npc.getUuid().version() == 2 && npc.squaredDistanceTo(displayStand) <= 0.05
        );

        return npcs.isEmpty() ? displayStand : npcs.getFirst();
    }

    private boolean isCroesusMenu(Screen screen, MinecraftClient client) {
        if (!(screen instanceof HandledScreen<?> handledScreen)) {
            return false;
        }
        return "Croesus".equals(handledScreen.getTitle().getString()) && isInventoryLoaded(client.player.currentScreenHandler);
    }

    private boolean isRunGui(Screen screen) {
        if (!(screen instanceof HandledScreen<?> handledScreen)) {
            return false;
        }
        return RUN_GUI_PATTERN.matcher(handledScreen.getTitle().getString()).matches();
    }

    private boolean isInventoryLoaded(ScreenHandler handler) {
        if (handler == null || handler.slots.size() <= 45) {
            return false;
        }

        ItemStack marker = handler.getSlot(handler.slots.size() - 45).getStack();
        return !marker.isEmpty();
    }

    private Integer getCurrentPage(MinecraftClient client, ScreenHandler handler) {
        if (handler.slots.size() <= 53) {
            return null;
        }

        ItemStack next = handler.getSlot(53).getStack();
        ItemStack previous = handler.getSlot(45).getStack();

        if (!next.isEmpty() && next.getName().getString().equalsIgnoreCase("Next Page")) {
            Integer page = parsePageFromTooltip(client, next);
            return page == null ? null : page - 1;
        }

        if (!previous.isEmpty() && previous.getName().getString().equalsIgnoreCase("Previous Page")) {
            Integer page = parsePageFromTooltip(client, previous);
            return page == null ? null : page + 1;
        }

        return 1;
    }

    private Integer parsePageFromTooltip(MinecraftClient client, ItemStack stack) {
        for (String line : TooltipUtil.plainTooltip(client, stack)) {
            Matcher matcher = PAGE_PATTERN.matcher(line);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        return null;
    }

    private UnopenedChest findUnopenedChest(MinecraftClient client, ScreenHandler handler, int page) {
        for (int slot : RUN_SLOTS) {
            int runIndex = toRunIndex(slot, page);
            if (failedIndexes.contains(runIndex)) {
                continue;
            }

            ItemStack stack = handler.getSlot(slot).getStack();
            if (stack.isEmpty()) {
                return new UnopenedChest(null, null);
            }

            if (!stack.isOf(Items.PLAYER_HEAD)) {
                continue;
            }

            List<String> lore = TooltipUtil.plainTooltip(client, stack);
            boolean unopened = lore.stream().anyMatch(line -> line.contains("No chests opened yet!"));
            if (!unopened) {
                continue;
            }

            String floorToken = null;
            for (String line : lore) {
                Matcher matcher = FLOOR_PATTERN.matcher(line);
                if (matcher.find()) {
                    floorToken = matcher.group(1);
                    break;
                }
            }

            if (floorToken == null) {
                failedIndexes.add(runIndex);
                return new UnopenedChest(null, null);
            }

            Integer floorNumber;
            try {
                floorNumber = Integer.parseInt(floorToken);
            } catch (NumberFormatException ignored) {
                floorNumber = RomanNumerals.decode(floorToken);
            }

            if (floorNumber == null) {
                failedIndexes.add(runIndex);
                return new UnopenedChest(null, null);
            }

            String floorPrefix = stack.getName().getString().contains("Master") ? "M" : "F";
            String floor = floorPrefix + floorNumber;

            if (!canKismet && config().kismetFloors.contains(floor)) {
                failedIndexes.add(runIndex);
                continue;
            }

            return new UnopenedChest(slot, floor);
        }

        return new UnopenedChest(null, null);
    }

    private boolean isKillSwitchPressed(MinecraftClient client) {
        long window = client.getWindow().getHandle();
        return org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS
            || org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    private int toRunIndex(int slot, int page) {
        return slot + (page - 1) * 54;
    }

    private record UnopenedChest(Integer slot, String floor) {
    }
}
