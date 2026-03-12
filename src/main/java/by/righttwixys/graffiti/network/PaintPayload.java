package by.righttwixys.graffiti.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public record PaintPayload(BlockPos pos, Direction side, int u, int v, int color, int size) implements CustomPayload {
    public static final Id<PaintPayload> ID = new Id<>(Identifier.of("graffiti", "paint"));

    public static final PacketCodec<RegistryByteBuf, PaintPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, PaintPayload::pos,
            Direction.PACKET_CODEC, PaintPayload::side,
            PacketCodecs.VAR_INT, PaintPayload::u,
            PacketCodecs.VAR_INT, PaintPayload::v,
            PacketCodecs.INTEGER, PaintPayload::color, // ИСПРАВЛЕНО: INTEGER вместо VAR_INT
            PacketCodecs.VAR_INT, PaintPayload::size,
            PaintPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}