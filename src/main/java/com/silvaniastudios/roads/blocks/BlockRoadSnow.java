package com.silvaniastudios.roads.blocks;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.silvaniastudios.roads.FurenikusRoads;
import com.silvaniastudios.roads.blocks.decorative.CurbBlock;
import com.silvaniastudios.roads.blocks.paint.PaintBlockBase;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.DefaultStateMapper;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class BlockRoadSnow extends BlockSnow {
	
	protected String name;
	
	public BlockRoadSnow(String name) {
		super();
		this.name = name;
		setTranslationKey(FurenikusRoads.MODID + "." + name);
		setRegistryName(name);
		setHardness(0.1F);
		setSoundType(SoundType.SNOW);
	}

	@Override
	public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
		ItemStack stack = player.getHeldItem(hand);
		if (stack.getItem() != Item.getItemFromBlock(Blocks.SNOW_LAYER)) {
			return super.onBlockActivated(world, pos, state, player, hand, facing, hitX, hitY, hitZ);
		}
		int layers = state.getValue(LAYERS);
		if (layers < 8 && !world.isRemote) {
			world.setBlockState(pos, state.withProperty(LAYERS, layers + 1), 3);
			SoundType sound = this.getSoundType(state, world, pos, player);
			world.playSound(null, pos, sound.getPlaceSound(), SoundCategory.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
			if (!player.capabilities.isCreativeMode) {
				stack.shrink(1);
			}
		}
		return true;
	}
	
	public void registerItemModel(Item itemBlock) {
		FurenikusRoads.proxy.registerItemRenderer(itemBlock, 0, name);
	}
	
	public Item createItemBlock() {
		return new ItemBlock(this).setRegistryName(getRegistryName());
	}
	
	@SideOnly(Side.CLIENT)
	public void initModel() {
		StateMapperBase b = new DefaultStateMapper();
		BlockStateContainer bsc = this.getBlockState();
		ImmutableList<IBlockState> values = bsc.getValidStates();
		
		for(IBlockState state : values) {
			ModelResourceLocation mrl = new ModelResourceLocation(state.getBlock().getRegistryName(), b.getPropertyString(state.getProperties()));
			
			ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(state.getBlock()), this.getMetaFromState(state), mrl);
		}
	}
	
	
	@SuppressWarnings("deprecation")
	@Override
    public Vec3d getOffset(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
        if (worldIn.getBlockState(pos.offset(EnumFacing.DOWN)).getBlock() instanceof RoadBlock) {
        	IBlockState roadState = worldIn.getBlockState(pos.offset(EnumFacing.DOWN));
        	RoadBlock block = (RoadBlock) roadState.getBlock();
        	int meta = block.getMetaFromState(roadState);
        	return new Vec3d(0, -1 + 0.0625 + (meta*0.0625), 0);
        }
        return super.getOffset(state, worldIn, pos);
    }
	
	@Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
    	double base = -1 + getBlockBelowHeight(source, pos);
    	return new AxisAlignedBB(0.0D, base, 0.0D, 1.0D, base + state.getValue(LAYERS) * 0.125D, 1.0D);
    }

    @Nullable
    public AxisAlignedBB getCollisionBoundingBox(IBlockState blockState, IBlockAccess worldIn, BlockPos pos) {
    	int layers = blockState.getValue(LAYERS);
    	if (layers <= 1) {
    		return NULL_AABB;
    	}
    	double base = -1 + getBlockBelowHeight(worldIn, pos);
    	return new AxisAlignedBB(0.0D, base, 0.0D, 1.0D, base + (layers - 1) * 0.125D, 1.0D);
    }
    
    @SuppressWarnings("deprecation")
	public double getBlockBelowHeight(IBlockAccess worldIn, BlockPos pos) {
    	IBlockState underState = worldIn.getBlockState(pos.offset(EnumFacing.DOWN));
        Block underBlock = underState.getBlock();
        double extraOffset = 0.0;
        
        if (underBlock instanceof PaintBlockBase || underBlock instanceof NonPaintRoadTopBlock || underBlock instanceof CurbBlock) {
        	extraOffset = 0.062;
        }
        
        return underBlock.getBoundingBox(underState, worldIn, pos.offset(EnumFacing.DOWN)).maxY - extraOffset;
    }

}
