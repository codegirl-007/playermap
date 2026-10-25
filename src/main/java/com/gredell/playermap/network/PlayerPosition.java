package com.gredell.playermap.network;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.UUID;

public record PlayerPosition(
	UUID uuid,
	String name,
	ResourceKey<Level> dimension,
	double x,
	double y,
	double z
) {
	public static final StreamCodec<RegistryFriendlyByteBuf, PlayerPosition> STREAM_CODEC = StreamCodec.composite(
		UUIDUtil.STREAM_CODEC,
		PlayerPosition::uuid,
		ByteBufCodecs.STRING_UTF8,
		PlayerPosition::name,
		ResourceKey.streamCodec(Registries.DIMENSION),
		PlayerPosition::dimension,
		ByteBufCodecs.DOUBLE,
		PlayerPosition::x,
		ByteBufCodecs.DOUBLE,
		PlayerPosition::y,
		ByteBufCodecs.DOUBLE,
		PlayerPosition::z,
		PlayerPosition::new
	);
}
