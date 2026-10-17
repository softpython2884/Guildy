package dev.guildy.ruin;

import dev.guildy.core.Safe;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Capture of the ruins' data markers at generation time (called by the ruin mixins, on the world generation threads).
 * <p>
 * Vanilla {@code SinglePoolElement.place} runs its data markers through the placement processors, and the
 * {@code BlockIgnoreProcessor.STRUCTURE_BLOCK} processor it always adds drops every structure block: the
 * {@code handleDataMarker} hook is never reached. The mixin calls {@link #onPiecePlaced} after the piece was placed in
 * the current chunk; the markers inside that chunk are turned into air (the marker cell kept the natural terrain block)
 * and queued with the structure start being placed ({@link #enterStart}: a thread-local set around
 * {@code StructureStart.placeInChunk}). The server thread drains the queue ({@link RuinIndex#processPending}).
 */
public final class MarkerCapture {
	/** Generated data markers waiting for the server thread. */
	record Captured(ResourceKey<Level> dimension, String ruinId, RuinRules.Marker type, BlockPos pos, @Nullable StartInfo start) {
	}

	/** The structure start whose pieces were being placed (identifies the ruin). */
	record StartInfo(Structure structure, int chunkX, int chunkZ, BoundingBox box) {
	}

	/** Registration of a ruin start found in a loaded chunk (CHUNK_LOAD) or by a locate. */
	record StartFound(ResourceKey<Level> dimension, StartInfo start) {
	}

	private static final int MAX_QUEUED = 20_000;
	private static final ThreadLocal<StructureStart> CURRENT = new ThreadLocal<>();
	static final ConcurrentLinkedQueue<Captured> MARKERS = new ConcurrentLinkedQueue<>();
	static final ConcurrentLinkedQueue<StartFound> STARTS = new ConcurrentLinkedQueue<>();
	private static final AtomicInteger QUEUED = new AtomicInteger();

	private MarkerCapture() {
	}

	/** StructureStart.placeInChunk entered (any thread). */
	public static void enterStart(StructureStart start) {
		CURRENT.set(start);
	}

	public static void exitStart() {
		CURRENT.remove();
	}

	/**
	 * A single pool element was placed in the chunk box {@code chunkBB} (any thread). Only templates under
	 * {@code <namespace>:ruin/} are inspected (cheap path check first).
	 */
	public static void onPiecePlaced(SinglePoolElement element, @Nullable Identifier template, WorldGenLevel level,
			StructureTemplateManager templates, BlockPos position, Rotation rotation, BoundingBox chunkBB) {
		if (template == null || !RuinRules.isRuinTemplate(template.getPath())) {
			return;
		}
		Safe.run("ruin.markers", () -> capture(element, template, level, templates, position, rotation, chunkBB));
	}

	private static void capture(SinglePoolElement element, Identifier template, WorldGenLevel level, StructureTemplateManager templates,
			BlockPos position, Rotation rotation, BoundingBox chunkBB) {
		String ruinId = RuinRules.ruinIdOf(template.getPath());
		if (ruinId == null) {
			return;
		}
		List<StructureTemplate.StructureBlockInfo> markers = element.getDataMarkers(templates, position, rotation, true);
		if (markers.isEmpty()) {
			return;
		}
		StartInfo start = startInfo(CURRENT.get());
		ResourceKey<Level> dimension = level.getLevel().dimension();
		for (StructureTemplate.StructureBlockInfo info : markers) {
			if (!chunkBB.isInside(info.pos())) {
				// Handled when the chunk holding it is placed (place() runs once per chunk the piece intersects).
				continue;
			}
			CompoundTag nbt = info.nbt();
			RuinRules.Marker type = RuinRules.marker(nbt == null ? null : nbt.getStringOr("metadata", ""));
			if (type == null) {
				continue;
			}
			BlockPos pos = info.pos().immutable();
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			if (QUEUED.incrementAndGet() <= MAX_QUEUED) {
				MARKERS.add(new Captured(dimension, ruinId, type, pos, start));
			} else {
				QUEUED.decrementAndGet();
			}
		}
	}

	/** Queues a ruin start seen in a loaded chunk or found by a locate (any thread). */
	static void startFound(ResourceKey<Level> dimension, StructureStart start) {
		StartInfo info = startInfo(start);
		if (info != null && QUEUED.incrementAndGet() <= MAX_QUEUED) {
			STARTS.add(new StartFound(dimension, info));
		} else if (info != null) {
			QUEUED.decrementAndGet();
		}
	}

	static void dequeued() {
		QUEUED.decrementAndGet();
	}

	/** Start details, the box being the union of the piece boxes (without the terrain adaptation margin). */
	static @Nullable StartInfo startInfo(@Nullable StructureStart start) {
		if (start == null || !start.isValid()) {
			return null;
		}
		List<StructurePiece> pieces = start.getPieces();
		BoundingBox box = null;
		for (StructurePiece piece : pieces) {
			BoundingBox b = piece.getBoundingBox();
			box = box == null ? b : new BoundingBox(Math.min(box.minX(), b.minX()), Math.min(box.minY(), b.minY()), Math.min(box.minZ(), b.minZ()),
					Math.max(box.maxX(), b.maxX()), Math.max(box.maxY(), b.maxY()), Math.max(box.maxZ(), b.maxZ()));
		}
		if (box == null) {
			box = start.getBoundingBox();
		}
		return new StartInfo(start.getStructure(), start.getChunkPos().x(), start.getChunkPos().z(), box);
	}
}
