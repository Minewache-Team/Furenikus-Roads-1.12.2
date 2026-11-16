package com.silvaniastudios.roads.client.model;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
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
	/** Drop entries nobody asked for in {@link #IDLE} seconds (the first far-render pass fills ~1.5M quads it never asks for again). Kill switch: MWRP fix_furenikustrim, -Dfurenikus.notrim. */
	public static volatile boolean trim = !Boolean.getBoolean("furenikus.notrim");
	private static final int IDLE = Integer.getInteger("furenikus.quadcache.idlesec", 60);
	private static final int MAX = 4096;

	/** All live caches (weak: models of an old resource reload go away), so a switch of {@link #flat} empties every one, not only those asked again. */
	private static final Set<QuadCache> ALL = Collections.newSetFromMap(new WeakHashMap<>());
	private static volatile boolean builtFlat = flat;

	private static final AtomicInteger lastSweep = new AtomicInteger(now());
	private static final LongAdder hits = new LongAdder(), misses = new LongAdder(), builtQuads = new LongAdder();
	private static long evictedEntries, evictedQuads;
	private static int lastLog = now();

	private final ConcurrentHashMap<Object, Entry> quads = new ConcurrentHashMap<>();

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
		int t = now();
		if (t - lastSweep.get() >= 10) sweep(t);
		Entry cached = quads.get(key);
		if (cached != null) {
			cached.lastUse = t;
			hits.increment();
			return cached.quads;
		}
		List<BakedQuad> built = build.apply(state);
		if (f) built = FlatQuad.convert(built);
		misses.increment();
		builtQuads.add(built == null ? 0 : built.size());
		if (quads.size() >= MAX) quads.clear();
		quads.put(key, new Entry(built, t));
		return built;
	}

	private static void switchMode(boolean f) {
		long n = 0;
		int c = 0;
		synchronized (ALL) {
			if (f == builtFlat) return;
			builtFlat = f;
			for (QuadCache q : ALL) {
				for (Entry e : q.quads.values()) n += e.size();
				q.quads.clear();
				c++;
			}
		}
		FurenikusRoads.logger.info("[QuadCache] flat={}: {} caches emptied, {} quads dropped", f, c, n);
	}

	/** Coarse clock, about one unit per second. */
	private static int now() {
		return (int) (System.nanoTime() >>> 30);
	}

	private static void sweep(int t) {
		int last = lastSweep.get();
		if (t - last < 10 || !lastSweep.compareAndSet(last, t)) return;
		boolean on = trim;
		int cutoff = t - IDLE;
		long e = 0, n = 0, keptE = 0, keptQ = 0;
		synchronized (ALL) {
			for (QuadCache q : ALL) {
				for (java.util.Iterator<Entry> it = q.quads.values().iterator(); it.hasNext(); ) {
					Entry en = it.next();
					if (on && en.lastUse - cutoff < 0) {
						it.remove();
						e++;
						n += en.size();
					} else {
						keptE++;
						keptQ += en.size();
					}
				}
			}
			evictedEntries += e;
			evictedQuads += n;
			if (t - lastLog >= 30) {
				lastLog = t;
				FurenikusRoads.logger.info("[QuadCache] trim={}: kept {} entries / {} quads, evicted {} entries / {} quads since last, hits {} misses {} built quads {}",
						on, keptE, keptQ, evictedEntries, evictedQuads, hits.sumThenReset(), misses.sumThenReset(), builtQuads.sumThenReset());
				evictedEntries = 0;
				evictedQuads = 0;
			}
		}
	}

	private static final class Entry {
		final List<BakedQuad> quads;
		int lastUse;

		Entry(List<BakedQuad> quads, int lastUse) {
			this.quads = quads;
			this.lastUse = lastUse;
		}

		int size() {
			return quads == null ? 0 : quads.size();
		}
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
