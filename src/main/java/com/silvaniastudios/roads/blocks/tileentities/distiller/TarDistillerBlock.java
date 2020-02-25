package com.silvaniastudios.roads.blocks.tileentities.distiller;

import java.util.List;

import javax.annotation.Nullable;

import com.silvaniastudios.roads.blocks.tileentities.RoadTEBlock;
import com.silvaniastudios.roads.blocks.tileentities.roadfactory.RoadFactoryBlock;

import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class TarDistillerBlock extends RoadTEBlock {
	
	public static final PropertyBool CONNECTED = PropertyBool.create("connected");
	public static final PropertyBool FURNACE_ACTIVE = PropertyBool.create("furnace_active");

	public TarDistillerBlock(String name, boolean electric) {
		super(name, electric, 2);
		this.setDefaultState(this.blockState.getBaseState()
				.withProperty(ROTATION, RoadTEBlock.EnumRotation.north)
				.withProperty(CONNECTED, false)
				.withProperty(FURNACE_ACTIVE, false));
	}
	
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
		tooltip.add(I18n.format("roads.gui.tar_distiller.tooltip_1"));
		tooltip.add(I18n.format("roads.gui.tar_distiller.tooltip_2"));
	}
	
	@Override
	public TileEntity createTileEntity(World worldIn, IBlockState state) {
		if (electric) {
			return new TarDistillerElectricEntity();
		}
		return new TarDistillerEntity();
	}
	
	public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
		EnumFacing getRight = EnumFacing.EAST;
		if (getMetaFromState(state) == 0) { getRight = EnumFacing.EAST;  }
		if (getMetaFromState(state) == 1) { getRight = EnumFacing.SOUTH; }
		if (getMetaFromState(state) == 2) { getRight = EnumFacing.WEST;  }
		if (getMetaFromState(state) == 3) { getRight = EnumFacing.NORTH; }
		
		IBlockState blockRight = world.getBlockState(pos.offset(getRight));
		boolean connect = blockRight.getBlock() instanceof RoadFactoryBlock;
		return state.withProperty(CONNECTED, connect).withProperty(FURNACE_ACTIVE, isFurnaceEnabled(state, world, pos));
	}
	

	
	protected BlockStateContainer createBlockState() {
		return new BlockStateContainer(this, new IProperty[] {CONNECTED, ROTATION, FURNACE_ACTIVE});
	}
}
