package com.raca;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

public class RacaMod implements ModInitializer {
    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(AbilityPayload.ID, AbilityPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(AbilityPayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            if (!Habilidades.temRaca(p)) return;
            switch (payload.acao()) {
                case AbilityPayload.GRITO -> Habilidades.grito(p);
                case AbilityPayload.VOO -> Habilidades.alternarVoo(p);
                case AbilityPayload.LOBOS -> Habilidades.invocarLobos(p);
                default -> { }
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) ->
            dispatcher.register(CommandManager.literal("raca")
                .then(CommandManager.literal("ativar").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    Habilidades.ativar(p);
                    ctx.getSource().sendFeedback(() -> Text.literal(
                        "Raca ativada! R = grito, V = voar, G = lobos"), false);
                    return 1;
                }))
                .then(CommandManager.literal("desativar").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    Habilidades.desativar(p);
                    ctx.getSource().sendFeedback(() -> Text.literal("Raca desativada."), false);
                    return 1;
                }))));

        ServerTickEvents.END_SERVER_TICK.register(Habilidades::tick);

        // Dragao: sem dano de queda
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
            !(entity instanceof ServerPlayerEntity p && Habilidades.semDanoDeQueda(p, source)));

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!world.isClient
                && player instanceof ServerPlayerEntity sp
                && entity instanceof LivingEntity alvo) {
                Habilidades.lobosAtacam(sp, alvo);
            }
            return ActionResult.PASS;
        });

        // Lobos que sobraram de uma sessao anterior somem ao carregar
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity.getCommandTags().contains(Habilidades.TAG_LOBO)
                && !Habilidades.LOBOS.containsKey(entity.getUuid())) {
                world.getServer().execute(entity::discard);
            }
        });
    }
}
