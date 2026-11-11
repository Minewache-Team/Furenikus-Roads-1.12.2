package com.silvaniastudios.roads.client.model;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraftforge.common.property.IExtendedBlockState;

/**
 * Baked quads per block state, kept per model instance (a resource reload bakes new models, so the cache goes with them).
 * Paint and diagonal road models built every quad again for each block in each chunk build - the same handful of
 * shapes over and over (Minewache: 150-250 MB/s of garbage while driving). The result depends only on the state:
 * its listed properties plus the unlisted connection values, so both make up the key. Same quads, same order.
 * Kill switch for Minewache: MWRP fix_furenikusquads (field {@link #enabled}).
 */
public final class QuadCache {

	public static volatile boolean enabled = !Boolean.getBoolean("furenikus.noquadcache");
	private static final int MAX = 4096;

	private final ConcurrentHashMap<Object, List<BakedQuad>> quads = new ConcurrentHashMap<>();

	public List<BakedQuad> get(IBlockState state, Function<IBlockState, List<BakedQuad>> build) {
		if (!enabled || state == null) return build.apply(state);
		Object key = state instanceof IExtendedBlockState
				? new Key(((IExtendedBlockState) state).getClean(), ((IExtendedBlockState) state).getUnlistedProperties())
				: state;
		List<BakedQuad> cached = quads.get(key);
		if (cached != null) return cached;
		List<BakedQuad> built = build.apply(state);
		if (quads.size() >= MAX) quads.clear();
		quads.put(key, built);
		return built;
	}

	private static final class Key {
		private final IBlockState clean;
		private final Object unlisted;
		private final int hash;

		Key(IBlockState clean, Object unlisted) {
			this.clean = clean;
			this.unlisted = unlisted;
			this.hash = System.identityHashCode(clean) * 31 + Objects.hashCode(unlisted);
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof Key)) return false;
			Key k = (Key) o;
			return clean == k.clean && hash == k.hash && Objects.equals(unlisted, k.unlisted);
		}

		@Override
		public int hashCode() {
			return hash;
		}
	}
}
