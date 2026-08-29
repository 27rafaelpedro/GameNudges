package com.nudgecraft.Karma;

import com.nudgecraft.Nudgecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record NudgeBlinkPayload() implements CustomPacketPayload {
    public static final Identifier ID = Nudgecraft.id("nudge_blink");
    public static final CustomPacketPayload.Type<NudgeBlinkPayload> TYPE = new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, NudgeBlinkPayload> CODEC = StreamCodec.unit(new NudgeBlinkPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
