package acmod.autocroesus.render;

import acmod.autocroesus.logic.AutoCroesusController;
import acmod.autocroesus.mixin.HandledScreenAccessor;
import acmod.autocroesus.model.ChestItem;
import acmod.autocroesus.model.ChestProfit;
import acmod.autocroesus.util.FormatUtil;
import acmod.autocroesus.util.TooltipUtil;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;

import java.util.List;
import java.util.regex.Pattern;

public class OverlayRenderer {
    private static final Pattern RUN_GUI_PATTERN = Pattern.compile("^(?:Master )?Catacombs - ([FloorVI\\d ]*)$");

    private final AutoCroesusController controller;

    public OverlayRenderer(AutoCroesusController controller) {
        this.controller = controller;
    }

    public void register() {
        HudRenderCallback.EVENT.register(this::render);
    }

    private void render(DrawContext context, net.minecraft.client.render.RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || !(client.currentScreen instanceof HandledScreen<?> handledScreen)) {
            if (client != null) {
                controller.onRenderFrame(client);
            }
            return;
        }

        controller.onRenderFrame(client);

        if (controller.config().showChestInfo && RUN_GUI_PATTERN.matcher(handledScreen.getTitle().getString()).matches()) {
            renderRunOverlay(context, controller.currentChestData());
        }

        renderCroesusHighlights(client, context, handledScreen);
    }

    private void renderRunOverlay(DrawContext context, List<ChestProfit> chests) {
        if (chests == null || chests.isEmpty()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        int x = 6;
        int y = 6;

        for (ChestProfit chest : chests) {
            int color = chest.profit() >= 0 ? 0x7CFC00 : 0xFF5555;
            String line = chest.chestName() + " Chest (" + FormatUtil.commas(chest.cost()) + ") " + (chest.profit() >= 0 ? "+" : "") + FormatUtil.commas(chest.profit());
            context.drawText(client.textRenderer, line, x, y, color, true);
            y += 10;

            for (ChestItem item : chest.items()) {
                String itemLine = "  " + item.displayName() + " +" + FormatUtil.commas(item.totalValue());
                context.drawText(client.textRenderer, itemLine, x, y, 0xFFFFFF, false);
                y += 10;
            }

            y += 4;
        }
    }

    private void renderCroesusHighlights(MinecraftClient client, DrawContext context, HandledScreen<?> screen) {
        if (!"Croesus".equals(screen.getTitle().getString())) {
            return;
        }

        ScreenHandler handler = client.player.currentScreenHandler;
        if (handler == null || handler.slots.size() < 54) {
            return;
        }

        int guiX = ((HandledScreenAccessor) screen).autocroesus$getX();
        int guiY = ((HandledScreenAccessor) screen).autocroesus$getY();

        for (int i = 0; i < 54; i++) {
            ItemStack stack = handler.getSlot(i).getStack();
            if (stack.isEmpty()) {
                continue;
            }

            boolean unopened = TooltipUtil.plainTooltip(client, stack).stream().anyMatch(line -> line.contains("No chests opened yet!"));
            if (!unopened) {
                continue;
            }

            int x = guiX + handler.getSlot(i).x;
            int y = guiY + handler.getSlot(i).y;
            context.fill(x, y, x + 16, y + 16, 0x7000FF00);

            if (controller.config().noClick && controller.isAutoClaiming()) {
                context.drawText(client.textRenderer, String.valueOf(i), x + 2, y + 2, 0xFFFFFF, false);
            }
        }
    }
}
