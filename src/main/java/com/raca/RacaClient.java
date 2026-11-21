package com.raca;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class RacaClient implements ClientModInitializer {
    private static KeyBinding teclaGrito;
    private static KeyBinding teclaVoo;
    private static KeyBinding teclaLobos;

    @Override
    public void onInitializeClient() {
        teclaGrito = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.raca.grito", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "category.raca"));
        teclaVoo = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.raca.voo", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.raca"));
        teclaLobos = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.raca.lobos", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.raca"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            while (teclaGrito.wasPressed()) {
                ClientPlayNetworking.send(new AbilityPayload(AbilityPayload.GRITO));
            }
            while (teclaVoo.wasPressed()) {
                ClientPlayNetworking.send(new AbilityPayload(AbilityPayload.VOO));
            }
            while (teclaLobos.wasPressed()) {
                ClientPlayNetworking.send(new AbilityPayload(AbilityPayload.LOBOS));
            }
        });
    }
}
