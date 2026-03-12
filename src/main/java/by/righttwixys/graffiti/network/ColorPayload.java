package by.righttwixys.graffiti.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ColorPayload(int color) implements CustomPayload {
    public static final CustomPayload.Id<ColorPayload> ID = new CustomPayload.Id<>(Identifier.of("graffiti", "sync_color"));

    public static final PacketCodec<RegistryByteBuf, ColorPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, ColorPayload::color,
            ColorPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}