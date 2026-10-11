package dev.guildy.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializer;
import com.mojang.serialization.JsonOps;
import dev.guildy.core.GuildyServer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/**
 * Gson instance used for world data (guilds.json): adds adapters for BlockPos ([x,y,z]) and ItemStack
 * (vanilla codec, needs the running server's registries).
 */
public final class JsonAdapters {
	private JsonAdapters() {
	}

	public static Gson createGson() {
		return new GsonBuilder()
				.setPrettyPrinting()
				.disableHtmlEscaping()
				.serializeSpecialFloatingPointValues()
				.registerTypeHierarchyAdapter(BlockPos.class, (JsonSerializer<BlockPos>) (pos, type, ctx) -> {
					JsonArray array = new JsonArray();
					array.add(pos.getX());
					array.add(pos.getY());
					array.add(pos.getZ());
					return array;
				})
				.registerTypeHierarchyAdapter(BlockPos.class, (JsonDeserializer<BlockPos>) (json, type, ctx) -> {
					if (json == null || json.isJsonNull()) {
						return null;
					}
					if (json.isJsonArray()) {
						JsonArray array = json.getAsJsonArray();
						return new BlockPos(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt());
					}
					if (json.isJsonObject()) {
						JsonObject obj = json.getAsJsonObject();
						return new BlockPos(obj.get("x").getAsInt(), obj.get("y").getAsInt(), obj.get("z").getAsInt());
					}
					throw new JsonParseException("Bad BlockPos: " + json);
				})
				.registerTypeHierarchyAdapter(ItemStack.class, (JsonSerializer<ItemStack>) (stack, type, ctx) -> {
					if (stack == null || stack.isEmpty()) {
						return JsonNull.INSTANCE;
					}
					var ops = GuildyServer.registryAccess().createSerializationContext(JsonOps.INSTANCE);
					return ItemStack.CODEC.encodeStart(ops, stack).result().orElse(JsonNull.INSTANCE);
				})
				.registerTypeHierarchyAdapter(ItemStack.class, (JsonDeserializer<ItemStack>) (json, type, ctx) -> {
					if (json == null || json.isJsonNull()) {
						return ItemStack.EMPTY;
					}
					var ops = GuildyServer.registryAccess().createSerializationContext(JsonOps.INSTANCE);
					return ItemStack.CODEC.parse(ops, json).result().orElse(ItemStack.EMPTY);
				})
				.create();
	}

	public static JsonElement tree(Object value) {
		return createGson().toJsonTree(value);
	}
}
