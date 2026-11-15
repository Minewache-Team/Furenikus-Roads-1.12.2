package com.silvaniastudios.roads.client.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.silvaniastudios.roads.FurenikusRoads;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.pipeline.IVertexConsumer;
import net.minecraftforge.client.model.pipeline.LightUtil;
import net.minecraftforge.client.model.pipeline.UnpackedBakedQuad;

/**
 * Bit-identical replacement for a cached UnpackedBakedQuad: the same floats in one flat array instead of
 * float[4][elements][4] (1 + 4 + 4*elements objects per quad), and the packed int[] taken over from the original.
 * pipe() hands the consumer exactly the calls and values UnpackedBakedQuad.pipe() does.
 * Check against the original: -Dfurenikus.quadcache.verify=true (logs matches/mismatches).
 */
public final class FlatQuad extends BakedQuad {

	public static volatile boolean verify = Boolean.getBoolean("furenikus.quadcache.verify");
	private static final AtomicLong checked = new AtomicLong();
	private static final AtomicLong mismatched = new AtomicLong();
	private static final ThreadLocal<float[]> SCRATCH = ThreadLocal.withInitial(() -> new float[4]);

	private final float[] data;
	private final int elements;

	private FlatQuad(int[] vertexData, float[] data, int elements, int tint, EnumFacing face, TextureAtlasSprite sprite, boolean diffuse, VertexFormat format) {
		super(vertexData, tint, face, sprite, diffuse, format);
		this.data = data;
		this.elements = elements;
	}

	static List<BakedQuad> convert(List<BakedQuad> quads) {
		if (quads == null || quads.isEmpty()) return quads;
		List<BakedQuad> out = new ArrayList<>(quads.size());
		for (BakedQuad q : quads) out.add(of(q));
		return out;
	}

	static BakedQuad of(BakedQuad q) {
		if (q == null || q.getClass() != UnpackedBakedQuad.class) return q;
		VertexFormat fmt = q.getFormat();
		int n = fmt.getElementCount();
		float[] flat = new float[16 * n];
		q.pipe(new Collector(fmt, flat, n));
		FlatQuad f = new FlatQuad(q.getVertexData(), flat, n, q.getTintIndex(), q.getFace(), q.getSprite(), q.shouldApplyDiffuseLighting(), fmt);
		if (verify) check(q, f);
		return f;
	}

	@Override
	public void pipe(IVertexConsumer consumer) {
		VertexFormat to = consumer.getVertexFormat();
		int[] eMap = LightUtil.mapFormats(to, getFormat());
		if (hasTintIndex()) consumer.setQuadTint(getTintIndex());
		consumer.setTexture(getSprite());
		consumer.setApplyDiffuseLighting(shouldApplyDiffuseLighting());
		consumer.setQuadOrientation(getFace());
		float[] s = SCRATCH.get();
		int count = to.getElementCount();
		for (int v = 0; v < 4; v++) {
			for (int e = 0; e < count; e++) {
				if (eMap[e] != elements) {
					System.arraycopy(data, (v * elements + eMap[e]) * 4, s, 0, 4);
					consumer.put(e, s);
				} else {
					consumer.put(e);
				}
			}
		}
	}

	/** Copies what UnpackedBakedQuad.pipe() puts into its own format (identity mapping, every slot float[4]). */
	private static final class Collector extends Nop {
		private final float[] flat;
		private final int n;
		private int v, e;

		Collector(VertexFormat fmt, float[] flat, int n) {
			super(fmt);
			this.flat = flat;
			this.n = n;
		}

		@Override
		public void put(int element, float... d) {
			System.arraycopy(d, 0, flat, (v * n + element) * 4, Math.min(4, d.length));
			if (++e == n) { e = 0; v++; }
		}
	}

	private static class Nop implements IVertexConsumer {
		private final VertexFormat fmt;
		Nop(VertexFormat fmt) { this.fmt = fmt; }
		@Override public VertexFormat getVertexFormat() { return fmt; }
		@Override public void setQuadTint(int tint) {}
		@Override public void setQuadOrientation(EnumFacing orientation) {}
		@Override public void setApplyDiffuseLighting(boolean diffuse) {}
		@Override public void setTexture(TextureAtlasSprite texture) {}
		@Override public void put(int element, float... data) {}
	}

	/** Records every call with raw float bits, so old and new can be compared exactly. */
	private static final class Recorder extends Nop {
		final StringBuilder log = new StringBuilder();
		Recorder(VertexFormat fmt) { super(fmt); }
		@Override public void setQuadTint(int tint) { log.append('t').append(tint).append(';'); }
		@Override public void setQuadOrientation(EnumFacing o) { log.append('o').append(o).append(';'); }
		@Override public void setApplyDiffuseLighting(boolean d) { log.append('d').append(d).append(';'); }
		@Override public void setTexture(TextureAtlasSprite t) { log.append('s').append(System.identityHashCode(t)).append(';'); }
		@Override public void put(int element, float... data) {
			log.append('p').append(element).append('#').append(data.length);
			for (float x : data) log.append(',').append(Integer.toHexString(Float.floatToRawIntBits(x)));
			log.append(';');
		}
	}

	private static void check(BakedQuad old, FlatQuad neu) {
		boolean ok = old.getTintIndex() == neu.getTintIndex() && old.getFace() == neu.getFace()
				&& old.getSprite() == neu.getSprite() && old.shouldApplyDiffuseLighting() == neu.shouldApplyDiffuseLighting()
				&& old.getFormat() == neu.getFormat() && Arrays.equals(old.getVertexData(), neu.getVertexData());
		VertexFormat[] targets = { old.getFormat(), DefaultVertexFormats.BLOCK, DefaultVertexFormats.ITEM,
				withNormal(DefaultVertexFormats.BLOCK) };
		for (VertexFormat t : targets) {
			if (!ok) break;
			Recorder a = new Recorder(t), b = new Recorder(t);
			old.pipe(a);
			neu.pipe(b);
			ok = a.log.toString().equals(b.log.toString());
		}
		long c = checked.incrementAndGet();
		long m = ok ? mismatched.get() : mismatched.incrementAndGet();
		if (!ok && m <= 10) FurenikusRoads.logger.warn("[FlatQuad verify] MISMATCH #{} format={} face={}", m, old.getFormat(), old.getFace());
		if (c % 5000 == 0 || (!ok && m <= 10)) FurenikusRoads.logger.info("[FlatQuad verify] checked={} mismatched={}", c, m);
	}

	/** Same format VertexLighterFlat gathers in for block rendering: BLOCK plus NORMAL_4F. */
	private static VertexFormat withNormal(VertexFormat base) {
		VertexFormat f = new VertexFormat(base);
		f.addElement(new net.minecraft.client.renderer.vertex.VertexFormatElement(0, net.minecraft.client.renderer.vertex.VertexFormatElement.EnumType.FLOAT, net.minecraft.client.renderer.vertex.VertexFormatElement.EnumUsage.NORMAL, 4));
		return f;
	}

	public static String stats() {
		return "checked=" + checked.get() + " mismatched=" + mismatched.get();
	}
}
