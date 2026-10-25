package com.gredell.playermap.network;

import com.gredell.playermap.PlayersMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

public record PlayerPositionsPayload(List<PlayerPosition> players) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PlayerPositionsPayload> TYPE =
		new CustomPacketPayload.Type<>(PlayersMap.id("player_positions"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PlayerPositionsPayload> CODEC = StreamCodec.composite(
		PlayerPosition.STREAM_CODEC.apply(ByteBufCodecs.list()),
		PlayerPositionsPayload::players,
		PlayerPositionsPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
