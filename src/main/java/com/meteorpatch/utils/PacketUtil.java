package com.meteorpatch.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;

import java.util.concurrent.ThreadLocalRandom;

public final class PacketUtil {
    private PacketUtil() {}
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static void send(Packet<?> packet) {
        if (mc.getNetworkHandler() == null) return;
        mc.getNetworkHandler().sendPacket(packet);
    }

    public static void sendWithJitter(Packet<?> packet, double probability) {
        if (ThreadLocalRandom.current().nextDouble() > probability) return;
        send(packet);
    }

    public static void sendCommand(String command) {
        if (mc.player == null || mc.player.networkHandler == null) return;
        String c = (command.startsWith("#") || command.startsWith("/")) ? command.substring(1) : command;
        mc.player.networkHandler.sendChatCommand(c);
    }

    public static boolean isSingleplayer() {
        return mc.isInSingleplayer();
    }

    public static String currentServerAddress() {
        if (mc.getCurrentServerEntry() == null) return "singleplayer";
        return mc.getCurrentServerEntry().address;
    }
}
