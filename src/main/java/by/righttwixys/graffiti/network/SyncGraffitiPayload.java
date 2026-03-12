package by.righttwixys.graffiti.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;

public record SyncGraffitiPayload(List<PaintPayload> allPixels) implements CustomPayload {

    public static final CustomPayload.Id<SyncGraffitiPayload> ID = new CustomPayload.Id<>(Identifier.of("graffiti", "sync_all"));

    public static final PacketCodec<RegistryByteBuf, SyncGraffitiPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeInt(value.allPixels().size());
                for (PaintPayload p : value.allPixels()) {
                    PaintPayload.CODEC.encode(buf, p);
                }
            },
            buf -> {
                int size = buf.readInt();
                List<PaintPayload> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    list.add(PaintPayload.CODEC.decode(buf));
                }
                return new SyncGraffitiPayload(list);
            }
    );

    // Изменено с id() на getId()
    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}