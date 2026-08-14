package com.nudgecraft.Karma;

import com.nudgecraft.Nudgecraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record KarmaPayload(String karmaName) implements CustomPacketPayload {

    public static final Type<KarmaPayload> TYPE = new Type<>(Nudgecraft.id("karma_sync"));
    public static final StreamCodec<ByteBuf, KarmaPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            KarmaPayload::karmaName,
            KarmaPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
