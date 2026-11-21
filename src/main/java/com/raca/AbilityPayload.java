package com.raca;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record AbilityPayload(int acao) implements CustomPayload {
    public static final int GRITO = 0;
    public static final int VOO = 1;
    public static final int LOBOS = 2;

    public static final CustomPayload.Id<AbilityPayload> ID =
        new CustomPayload.Id<>(Identifier.of("raca", "habilidade"));

    public static final PacketCodec<ByteBuf, AbilityPayload> CODEC =
        PacketCodecs.VAR_INT.xmap(AbilityPayload::new, AbilityPayload::acao);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
