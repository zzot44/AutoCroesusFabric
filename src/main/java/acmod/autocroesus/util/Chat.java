package acmod.autocroesus.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class Chat {
    private Chat() {
    }

    public static void info(String message) {
        send(Text.literal(message));
    }

    public static void success(String message) {
        send(Text.literal(message).formatted(Formatting.GREEN));
    }

    public static void warn(String message) {
        send(Text.literal(message).formatted(Formatting.RED));
    }

    private static void send(MutableText message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return;
        }

        MutableText prefix = Text.literal("[AutoCroesus] ").formatted(Formatting.AQUA);
        client.player.sendMessage(prefix.append(message), false);
    }
}
