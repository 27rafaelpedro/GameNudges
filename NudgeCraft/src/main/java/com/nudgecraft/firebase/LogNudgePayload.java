package com.nudgecraft.firebase;

import com.nudgecraft.Nudgecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record LogNudgePayload(boolean isPositive, String featureId) implements CustomPacketPayload {
    public static final Identifier ID = Nudgecraft.id("log_nudge");
    public static final CustomPacketPayload.Type<LogNudgePayload> TYPE = new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, LogNudgePayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.isPositive());
                buf.writeUtf(payload.featureId());
            },
            buf -> new LogNudgePayload(buf.readBoolean(), buf.readUtf())
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
