package com.silvaniastudios.roads.client.model;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.silvaniastudios.roads.FurenikusRoads;

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
	/** Cached quads as {@link FlatQuad} (bit-identical, about half the heap). Kill switch: MWRP fix_furenikusflat, -Dfurenikus.noflatquads. */
	public static volatile boolean flat = !Boolean.getBoolean("furenikus.noflatquads");
	private static final int MAX = 4096;

	/** All live caches (weak: models of an old resource reload go away), so a switch of {@link #flat} empties every one, not only those asked again. */
	private static final Set<QuadCache> ALL = Collections.newSetFromMap(new WeakHashMap<>());
	private static volatile boolean builtFlat = flat;

	private final ConcurrentHashMap<Object, List<BakedQuad>> quads = new ConcurrentHashMap<>();

	public QuadCache() {
		synchronized (ALL) { ALL.add(this); }
	}

	public List<BakedQuad> get(IBlockState state, Function<IBlockState, List<BakedQuad>> build) {
		if (!enabled || state == null) return build.apply(state);
		boolean f = flat;
		if (f != builtFlat) switchMode(f);
		Object key = state instanceof IExtendedBlockState
				? new Key(((IExtendedBlockState) state).getClean(), ((IExtendedBlockState) state).getUnlistedProperties())
				: state;
		List<BakedQuad> cached = quads.get(key);
		if (cached != null) return cached;
		List<BakedQuad> built = build.apply(state);
		if (f) built = FlatQuad.convert(built);
		if (quads.size() >= MAX) quads.clear();
		quads.put(key, built);
		return built;
	}

	private static void switchMode(boolean f) {
		long n = 0;
		int c = 0;
		synchronized (ALL) {
			if (f == builtFlat) return;
			builtFlat = f;
			for (QuadCache q : ALL) {
				for (List<BakedQuad> l : q.quads.values()) n += l.size();
				q.quads.clear();
				c++;
			}
		}
		FurenikusRoads.logger.info("[QuadCache] flat={}: {} caches emptied, {} quads dropped", f, c, n);
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
