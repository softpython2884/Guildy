package dev.guildy.builds;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Does a block need a support ({@code canSurvive} in a world of air)? Asked once per palette entry with a stand-in
 * level that holds nothing but air (a dynamic proxy: no chunk is ever read or loaded). A block that cannot survive
 * there (torch, door, carpet, flower, lantern, ladder, sign...) belongs to the "attached" pass; if the check fails in
 * an unexpected way, a block without collision is taken as attached and any other one as solid.
 */
final class SupportProbe {
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final LevelReader EMPTY = (LevelReader) Proxy.newProxyInstance(LevelReader.class.getClassLoader(),
			new Class<?>[] {LevelReader.class}, new Handler());

	private SupportProbe() {
	}

	static boolean needsSupport(BlockState state) {
		try {
			return !state.canSurvive(EMPTY, BlockPos.ZERO);
		} catch (RuntimeException | LinkageError e) {
			try {
				return state.getCollisionShape(EMPTY, BlockPos.ZERO).isEmpty();
			} catch (RuntimeException | LinkageError e2) {
				return false;
			}
		}
	}

	private static final class Handler implements InvocationHandler {
		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			String name = method.getName();
			switch (name) {
				case "getBlockState":
					return AIR;
				case "getFluidState":
					return Fluids.EMPTY.defaultFluidState();
				case "getMinY":
					return -64;
				case "getHeight":
					return 384;
				case "isClientSide":
					return false;
				case "hashCode":
					return System.identityHashCode(proxy);
				case "equals":
					return proxy == args[0];
				case "toString":
					return "SupportProbe.EMPTY";
				default:
					break;
			}
			if (method.isDefault()) {
				return InvocationHandler.invokeDefault(proxy, method, args);
			}
			Class<?> type = method.getReturnType();
			if (type == boolean.class) {
				return false;
			}
			if (type == int.class) {
				return 0;
			}
			if (type == long.class) {
				return 0L;
			}
			if (type == float.class) {
				return 0.0F;
			}
			if (type == double.class) {
				return 0.0;
			}
			return null;
		}
	}
}
