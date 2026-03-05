package acmod.autocroesus.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class TooltipUtil {
    private TooltipUtil() {
    }

    public static List<String> plainTooltip(MinecraftClient client, ItemStack stack) {
        List<Text> tooltip = stack.getTooltip(Item.TooltipContext.DEFAULT, client.player, TooltipType.BASIC);
        List<String> lines = new ArrayList<>(tooltip.size());
        for (Text text : tooltip) {
            lines.add(Formatting.strip(text.getString()));
        }
        return lines;
    }
}
