package com.mowmaster.pedestals.item.pedestalUpgrades;

import com.mojang.authlib.GameProfile;
import com.mowmaster.pedestals.crafting.CalculateColor;
import com.mowmaster.pedestals.network.PacketHandler;
import com.mowmaster.pedestals.network.PacketParticles;
import com.mowmaster.pedestals.tiles.PedestalTileEntity;
import com.mowmaster.pedestals.util.PedestalFakePlayer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FlowingFluidBlock;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.List;
import java.util.stream.IntStream;

import static com.mowmaster.pedestals.pedestals.PEDESTALS_TAB;
import static com.mowmaster.pedestals.references.Reference.MODID;
import static net.minecraft.state.properties.BlockStateProperties.FACING;

public class ItemUpgradeFluidPumpFilter extends ItemUpgradeBaseFluid
{
    public ItemUpgradeFluidPumpFilter(Properties builder) {super(builder.tab(PEDESTALS_TAB));}

    @Override
    public Boolean canAcceptRange() {
        return true;
    }

    @Override
    public Boolean canAcceptArea() {return true;}

    @Override
    public Boolean canAcceptCapacity() {
        return true;
    }

    @Override
    public Boolean canAcceptAdvanced() {
        return true;
    }

    public int getHeight(ItemStack stack)
    {
        return getRangeLarge(stack);
    }

    //Riped Straight from ItemUpgradePlacer
    public void placeBlock(PedestalTileEntity pedestal, BlockPos targetBlockPos)
    {
        World world = pedestal.getLevel();
        BlockPos pedPos = pedestal.getBlockPos();
        ItemStack itemInPedestal = pedestal.getItemInPedestal();
        ItemStack coinOnPedestal = pedestal.getCoinOnPedestal();
        if(!itemInPedestal.isEmpty())
        {
            Block blockBelow = world.getBlockState(targetBlockPos).getBlock();
            Item singleItemInPedestal = itemInPedestal.getItem();

            if(blockBelow.equals(Blocks.AIR) && !singleItemInPedestal.equals(Items.AIR)) {
                if(singleItemInPedestal instanceof BlockItem)
                {
                    if (((BlockItem) singleItemInPedestal).getBlock() instanceof Block)
                    {
                        if (!itemInPedestal.isEmpty() && itemInPedestal.getItem() instanceof BlockItem && ((BlockItem) itemInPedestal.getItem()).getBlock() instanceof Block) {

                            FakePlayer fakePlayer = new PedestalFakePlayer((ServerWorld) world,getPlayerFromCoin(coinOnPedestal),pedPos,itemInPedestal);
                            if(!fakePlayer.blockPosition().equals(new BlockPos(pedPos.getX(), pedPos.getY(), pedPos.getZ()))) {fakePlayer.setPos(pedPos.getX(), pedPos.getY(), pedPos.getZ());}

                            BlockItemUseContext blockContext = new BlockItemUseContext(fakePlayer, Hand.MAIN_HAND, itemInPedestal.copy(), new BlockRayTraceResult(Vector3d.ZERO, getPedestalFacing(world,pedPos), targetBlockPos, false));

                            ActionResultType result = ForgeHooks.onPlaceItemIntoWorld(blockContext);
                            if (result == ActionResultType.CONSUME) {
                                this.removeFromPedestal(world,pedPos,1);
                                world.playSound((PlayerEntity) null, targetBlockPos.getX(), targetBlockPos.getY(), targetBlockPos.getZ(), SoundEvents.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 0.5F, 1.0F);
                            }
                        }
                    }
                }
            }
        }
    }

    public boolean doesFluidBucketMatch(ItemStack bucketIn, FluidStack fluidIn)
    {
        if(bucketIn.getItem() instanceof BucketItem)
        {
            BucketItem bI = (BucketItem) bucketIn.getItem();
            FluidStack fluidFromBucket = new FluidStack(bI.getFluid(), FluidAttributes.BUCKET_VOLUME,bI.getShareTag(bucketIn));
            if(fluidFromBucket.isFluidEqual(fluidIn))
            {
                return true;
            }
        }
        else if (FluidUtil.getFluidHandler(bucketIn).isPresent())
        {
            LazyOptional<IFluidHandlerItem> handler = FluidUtil.getFluidHandler(bucketIn);
            if(handler.resolve().get().getFluidInTank(0).isFluidEqual(fluidIn))
            {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean canRecieveFluid(World world, BlockPos posPedestal, FluidStack fluidIncoming)
    {
        boolean returner = false;
        BlockPos posInventory = getBlockPosOfBlockBelow(world, posPedestal, 1);

        LazyOptional<IItemHandler> cap = findItemHandlerAtPos(world,posInventory,getPedestalFacing(world, posPedestal),true);
        if(cap.isPresent())
        {
            IItemHandler handler = cap.orElse(null);
            if(handler != null)
            {
                int range = handler.getSlots();

                ItemStack itemFromInv = ItemStack.EMPTY;
                itemFromInv = IntStream.range(0,range)//Int Range
                        .mapToObj((handler)::getStackInSlot)//Function being applied to each interval
                        .filter(itemStack -> doesFluidBucketMatch(itemStack,fluidIncoming))
                        .findFirst().orElse(ItemStack.EMPTY);

                if(!itemFromInv.isEmpty())
                {
                    returner = true;
                }
            }
        }

        return returner;
    }

    public boolean canPumpFluid(World world, BlockPos posPedestal, FluidStack fluidIncoming)
    {
        boolean returner = false;
        if(world.getTileEntity(posPedestal) instanceof PedestalTileEntity)
        {
            PedestalTileEntity pedestal = (PedestalTileEntity)world.getTileEntity(posPedestal);
            ItemStack coin = pedestal.getCoinOnPedestal();
            List<ItemStack> stackCurrent = readFilterQueueFromNBT(coin);
            if(!(stackCurrent.size()>0))
            {
                stackCurrent = buildFilterQueue(pedestal);
                writeFilterQueueToNBT(coin,stackCurrent);
            }

            int range = stackCurrent.size();

            ItemStack itemFromInv = ItemStack.EMPTY;
            itemFromInv = IntStream.range(0,range)//Int Range
                    .mapToObj((stackCurrent)::get)//Function being applied to each interval
                    .filter(itemStack -> doesFluidBucketMatch(itemStack,fluidIncoming))
                    .findFirst().orElse(ItemStack.EMPTY);

            if(!itemFromInv.isEmpty())
            {
                returner = true;
            }
        }

        return returner;
    }

    @Override
    public void onPedestalNeighborChanged(PedestalTileEntity pedestal) {
        ItemStack coin = pedestal.getCoinOnPedestal();
        List<ItemStack> stackIn = buildFilterQueue(pedestal);
        if(filterQueueSize(coin)>0)
        {
            List<ItemStack> stackCurrent = readFilterQueueFromNBT(coin);
            if(!doesFilterAndQueueMatch(stackIn,stackCurrent))
            {
                writeFilterQueueToNBT(coin,stackIn);
            }
        }
        else
        {
            writeFilterQueueToNBT(coin,stackIn);
        }
    }

    public int getWidth(ItemStack stack)
    {
        return  getAreaModifier(stack);
    }

    @Override
    public int getWorkAreaX(World world, BlockPos pos, ItemStack coin)
    {
        return getWidth(coin);
    }

    @Override
    public int[] getWorkAreaY(World world, BlockPos pos, ItemStack coin)
    {
        return new int[]{getHeight(coin),0};
    }

    @Override
    public int getWorkAreaZ(World world, BlockPos pos, ItemStack coin)
    {
        return getWidth(coin);
    }

    @Override
    public int getComparatorRedstoneLevel(World worldIn, BlockPos pos)
    {
        int intItem=0;
        TileEntity tileEntity = worldIn.getTileEntity(pos);
        if(tileEntity instanceof PedestalTileEntity) {
            PedestalTileEntity pedestal = (PedestalTileEntity) tileEntity;
            ItemStack coin = pedestal.getCoinOnPedestal();
            int width = getWidth(pedestal.getCoinOnPedestal());
            int widdth = (width*2)+1;
            int height = getHeight(pedestal.getCoinOnPedestal());
            int amount = workQueueSize(coin);
            int area = Math.multiplyExact(Math.multiplyExact(widdth,widdth),height);
            if(amount>0)
            {
                float f = (float)amount/(float)area;
                intItem = MathHelper.floor(f*14.0F)+1;
            }
        }

        return intItem;
    }

    public void updateAction(World world, PedestalTileEntity pedestal)
    {
        if(!world.isClientSide)
        {
            ItemStack coinInPedestal = pedestal.getCoinOnPedestal();
            ItemStack itemInPedestal = pedestal.getItemInPedestal();
            BlockPos pedestalPos = pedestal.getBlockPos();

            int getMaxFluidValue = getFluidbuffer(coinInPedestal);
            if(!hasMaxFluidSet(coinInPedestal) || readMaxFluidFromNBT(coinInPedestal) != getMaxFluidValue) {setMaxFluid(coinInPedestal, getMaxFluidValue);}

            int speed = getOperationSpeed(coinInPedestal);

            if(!world.hasNeighborSignal(pedestalPos)) {
                if(hasFluidInCoin(coinInPedestal) && world.getGameTime() % speed == 0)
                {
                    upgradeActionSendFluid(pedestal);
                }


                int rangeWidth = getWidth(coinInPedestal);
                int rangeHeight = getHeight(coinInPedestal);

                BlockState pedestalState = world.getBlockState(pedestalPos);
                Direction enumfacing = (pedestalState.hasProperty(FACING))?(pedestalState.get(FACING)):(Direction.UP);
                BlockPos negNums = getNegRangePosEntity(world,pedestalPos,rangeWidth,(enumfacing == Direction.NORTH || enumfacing == Direction.EAST || enumfacing == Direction.SOUTH || enumfacing == Direction.WEST)?(rangeHeight-1):(rangeHeight));
                BlockPos posNums = getBlockPosRangePosEntity(world,pedestalPos,rangeWidth,(enumfacing == Direction.NORTH || enumfacing == Direction.EAST || enumfacing == Direction.SOUTH || enumfacing == Direction.WEST)?(rangeHeight-1):(rangeHeight));

                if(world.isAreaLoaded(negNums,posNums))
                {
                    int val = readStoredIntTwoFromNBT(coinInPedestal);
                    if(val>0)
                    {
                        writeStoredIntTwoToNBT(coinInPedestal,val-1);
                    }
                    else {

                        //If work queue doesnt exist, try to make one
                        if(workQueueSize(coinInPedestal)<=0)
                        {
                            buildWorkQueue(pedestal,rangeWidth,rangeHeight);
                        }

                        //
                        if(workQueueSize(coinInPedestal) > 0)
                        {
                            //Check if we can even insert a blocks worth of fluid
                            if(availableFluidSpaceInCoin(coinInPedestal) >= FluidAttributes.BUCKET_VOLUME || getFluidStored(coinInPedestal).isEmpty())
                            {
                                List<BlockPos> workQueue = readWorkQueueFromNBT(coinInPedestal);
                                if (world.getGameTime() % speed == 0) {
                                    for(int i = 0;i< workQueue.size(); i++)
                                    {
                                        BlockPos targetBlockPos = workQueue.get(i);
                                        BlockPos blockToPumpPos = new BlockPos(targetBlockPos.getX(), targetBlockPos.getY(), targetBlockPos.getZ());
                                        BlockState targetFluidState = world.getBlockState(blockToPumpPos);
                                        Block targetFluidBlock = targetFluidState.getBlock();
                                        if(canMineBlock(pedestal,blockToPumpPos))
                                        {
                                            workQueue.remove(i);
                                            writeWorkQueueToNBT(coinInPedestal,workQueue);
                                            upgradeAction(pedestal, targetBlockPos, itemInPedestal, coinInPedestal);
                                            break;
                                        }
                                        else
                                        {
                                            workQueue.remove(i);
                                        }
                                    }
                                    writeWorkQueueToNBT(coinInPedestal,workQueue);
                                }
                            }
                        }
                        else {
                            writeStoredIntTwoToNBT(coinInPedestal,(((rangeWidth*2)+1)*20)+20);
                        }
                    }
                }
            }
        }
    }

    public void upgradeAction(PedestalTileEntity pedestal, BlockPos targetBlockPos, ItemStack itemInPedestal, ItemStack coinInPedestal)
    {
        World world = pedestal.getLevel();
        BlockPos pedestalPos = pedestal.getBlockPos();
        BlockState targetFluidState = world.getBlockState(targetBlockPos);
        Block targetFluidBlock = targetFluidState.getBlock();
        FluidStack fluidToStore = FluidStack.EMPTY;
        if (targetFluidBlock instanceof FlowingFluidBlock)
        {
            if (targetFluidState.get(FlowingFluidBlock.LEVEL) == 0) {
                Fluid fluid = ((FlowingFluidBlock) targetFluidBlock).getFluid();
                FluidStack fluidToPickup = new FluidStack(fluid, FluidAttributes.BUCKET_VOLUME);
                if(canAddFluidToCoin(pedestal,coinInPedestal,fluidToPickup))
                {
                    fluidToStore = fluidToPickup.copy();
                    if(!fluidToStore.isEmpty() && addFluid(pedestal,coinInPedestal,fluidToStore,true))
                    {
                        if(canPumpFluid(world,pedestalPos,fluidToStore))
                        {
                            world.setBlockState(targetBlockPos, Blocks.AIR.defaultBlockState(), 11);
                            addFluid(pedestal,coinInPedestal,fluidToStore,false);
                            if(itemInPedestal.isEmpty())
                            {
                                int[] rgb = CalculateColor.getRGBColorFromInt(fluidToStore.getFluid().getAttributes().getColor());
                                PacketHandler.sendToNearby(world,pedestalPos,new PacketParticles(PacketParticles.EffectType.ANY_COLOR_CENTERED,targetBlockPos.getX(),targetBlockPos.getY(),targetBlockPos.getZ(),rgb[0],rgb[1],rgb[2]));

                            }
                            else {placeBlock(pedestal,targetBlockPos);}
                        }
                    }
                }
            }
        }
        else if (targetFluidBlock instanceof IFluidBlock) {
            IFluidBlock fluidBlock = (IFluidBlock) targetFluidBlock;

            if (fluidBlock.canDrain(world, targetBlockPos)) {
                fluidToStore =  fluidBlock.drain(world, targetBlockPos, IFluidHandler.FluidAction.SIMULATE);
                if(!fluidToStore.isEmpty() && addFluid(pedestal,coinInPedestal,fluidToStore,true))
                {
                    if(canPumpFluid(world,pedestalPos,fluidToStore))
                    {
                        fluidToStore =  fluidBlock.drain(world, targetBlockPos, IFluidHandler.FluidAction.EXECUTE);
                        addFluid(pedestal,coinInPedestal,fluidToStore,false);
                        if(itemInPedestal.isEmpty())
                        {
                            int[] rgb = CalculateColor.getRGBColorFromInt(fluidToStore.getFluid().getAttributes().getColor());
                            PacketHandler.sendToNearby(world,pedestalPos,new PacketParticles(PacketParticles.EffectType.ANY_COLOR_CENTERED,targetBlockPos.getX(),targetBlockPos.getY(),targetBlockPos.getZ(),rgb[0],rgb[1],rgb[2]));

                        }
                        else {placeBlock(pedestal,targetBlockPos);}
                    }
                }
            }
        }
    }

    //Can Pump Block, but just reusing the quarry method here
    @Override
    public boolean canMineBlock(PedestalTileEntity pedestal, BlockPos blockToMinePos, PlayerEntity player)
    {
        World world = pedestal.getLevel();
        BlockPos pedestalPos = pedestal.getBlockPos();
        BlockPos blockToPumpPos = new BlockPos(blockToMinePos.getX(), blockToMinePos.getY(), blockToMinePos.getZ());
        BlockState targetFluidState = world.getBlockState(blockToPumpPos);
        Block targetFluidBlock = targetFluidState.getBlock();
        FluidStack fluidToStore = (targetFluidBlock instanceof FlowingFluidBlock && targetFluidState.get(FlowingFluidBlock.LEVEL) == 0)?(new FluidStack(((FlowingFluidBlock) targetFluidBlock).getFluid(), FluidAttributes.BUCKET_VOLUME)):((targetFluidBlock instanceof IFluidBlock)?(((IFluidBlock) targetFluidBlock).drain(world, blockToMinePos, IFluidHandler.FluidAction.SIMULATE)):(FluidStack.EMPTY));

        if((targetFluidBlock instanceof FlowingFluidBlock && targetFluidState.get(FlowingFluidBlock.LEVEL) == 0
                || targetFluidBlock instanceof IFluidBlock) && canPumpFluid(world,pedestalPos,fluidToStore))
        {
            return true;
        }

        return false;
    }
    @Override
    public boolean canMineBlock(PedestalTileEntity pedestal, BlockPos blockToMinePos)
    {
        World world = pedestal.getLevel();
        BlockPos pedestalPos = pedestal.getBlockPos();
        BlockPos blockToPumpPos = new BlockPos(blockToMinePos.getX(), blockToMinePos.getY(), blockToMinePos.getZ());
        BlockState targetFluidState = world.getBlockState(blockToPumpPos);
        Block targetFluidBlock = targetFluidState.getBlock();
        FluidStack fluidToStore = (targetFluidBlock instanceof FlowingFluidBlock && targetFluidState.get(FlowingFluidBlock.LEVEL) == 0)?(new FluidStack(((FlowingFluidBlock) targetFluidBlock).getFluid(), FluidAttributes.BUCKET_VOLUME)):((targetFluidBlock instanceof IFluidBlock)?(((IFluidBlock) targetFluidBlock).drain(world, blockToMinePos, IFluidHandler.FluidAction.SIMULATE)):(FluidStack.EMPTY));

        if((targetFluidBlock instanceof FlowingFluidBlock && targetFluidState.get(FlowingFluidBlock.LEVEL) == 0
                || targetFluidBlock instanceof IFluidBlock) && canPumpFluid(world,pedestalPos,fluidToStore))
        {
            return true;
        }

        return false;
    }

    @Override
    public void chatDetails(PlayerEntity player, PedestalTileEntity pedestal)
    {
        ItemStack stack = pedestal.getCoinOnPedestal();

        TranslationTextComponent name = new TranslationTextComponent(getDescriptionId() + ".tooltip_name");
        name.withStyle(TextFormatting.GOLD);
        player.sendMessage(name,Util.NIL_UUID);

        int s3 = getWidth(stack);
        int s4 = getHeight(stack);
        String tr = "" + (s3+s3+1) + "";
        String trr = "" + s4 + "";
        TranslationTextComponent area = new TranslationTextComponent(getDescriptionId() + ".chat_area");
        TranslationTextComponent areax = new TranslationTextComponent(getDescriptionId() + ".chat_areax");
        area.append(tr);
        area.append(areax.getString());
        area.append(trr);
        area.append(areax.getString());
        area.append(tr);
        area.withStyle(TextFormatting.WHITE);
        player.sendMessage(area,Util.NIL_UUID);

        FluidStack fluidStored = getFluidStored(stack);
        TranslationTextComponent fluidLabel = new TranslationTextComponent(getDescriptionId() + ".chat_fluidlabel");
        if(!fluidStored.isEmpty())
        {
            TranslationTextComponent fluid = new TranslationTextComponent(getDescriptionId() + ".chat_fluid");
            TranslationTextComponent fluidSplit = new TranslationTextComponent(getDescriptionId() + ".chat_fluidseperator");
            fluid.append("" + fluidStored.getDisplayName().getString() + "");
            fluid.append(fluidSplit.getString());
            fluid.append("" + fluidStored.getAmount() + "");
            fluid.append(fluidLabel.getString());
            fluid.withStyle(TextFormatting.BLUE);
            player.sendMessage(fluid,Util.NIL_UUID);
        }

        TranslationTextComponent btm = new TranslationTextComponent(getDescriptionId() + ".chat_btm");
        btm.append("" + ((workQueueSize(stack)>0)?(workQueueSize(stack)):(0)) + "");
        btm.withStyle(TextFormatting.YELLOW);
        player.sendMessage(btm,Util.NIL_UUID);

        TranslationTextComponent rate = new TranslationTextComponent(getDescriptionId() + ".chat_rate");
        rate.append("" +  getFluidTransferRate(stack) + "");
        rate.append(fluidLabel.getString());
        rate.withStyle(TextFormatting.GRAY);
        player.sendMessage(rate,Util.NIL_UUID);

        //Display Speed Last Like on Tooltips
        TranslationTextComponent speed = new TranslationTextComponent(getDescriptionId() + ".chat_speed");
        speed.append(getOperationSpeedString(stack));
        speed.withStyle(TextFormatting.RED);
        player.sendMessage(speed, Util.NIL_UUID);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<ITextComponent> tooltip, ITooltipFlag flagIn) {

        TranslationTextComponent t = new TranslationTextComponent(getDescriptionId() + ".tooltip_name");
        t.withStyle(TextFormatting.GOLD);
        tooltip.add(t);

        FluidStack fluidStored = getFluidStored(stack);
        TranslationTextComponent fluidLabel = new TranslationTextComponent(getDescriptionId() + ".chat_fluidlabel");
        if(!fluidStored.isEmpty())
        {
            TranslationTextComponent fluid = new TranslationTextComponent(getDescriptionId() + ".chat_fluid");
            TranslationTextComponent fluidSplit = new TranslationTextComponent(getDescriptionId() + ".chat_fluidseperator");
            fluid.append("" + fluidStored.getDisplayName().getString() + "");
            fluid.append(fluidSplit.getString());
            fluid.append("" + fluidStored.getAmount() + "");
            fluid.append(fluidLabel.getString());
            fluid.withStyle(TextFormatting.BLUE);
            tooltip.add(fluid);
        }

        int s3 = getWidth(stack);
        int s4 = getHeight(stack);
        String tr = "" + (s3+s3+1) + "";
        String trr = "" + s4 + "";
        TranslationTextComponent area = new TranslationTextComponent(getDescriptionId() + ".tooltip_area");
        TranslationTextComponent areax = new TranslationTextComponent(getDescriptionId() + ".tooltip_areax");
        area.append(tr);
        area.append(areax.getString());
        area.append(trr);
        area.append(areax.getString());
        area.append(tr);
        area.withStyle(TextFormatting.WHITE);
        tooltip.add(area);

        TranslationTextComponent fluidcapacity = new TranslationTextComponent(getDescriptionId() + ".tooltip_fluidcapacity");
        fluidcapacity.append(""+ getFluidbuffer(stack) +"");
        fluidcapacity.append(fluidLabel.getString());
        fluidcapacity.withStyle(TextFormatting.AQUA);
        tooltip.add(fluidcapacity);

        TranslationTextComponent rate = new TranslationTextComponent(getDescriptionId() + ".tooltip_rate");
        rate.append("" + getFluidTransferRate(stack) + "");
        rate.append(fluidLabel.getString());
        rate.withStyle(TextFormatting.GRAY);
        tooltip.add(rate);

        TranslationTextComponent speed = new TranslationTextComponent(getDescriptionId() + ".tooltip_speed");
        speed.append(getOperationSpeedString(stack));
        speed.withStyle(TextFormatting.RED);
        tooltip.add(speed);
    }

    public static final Item FLUIDPUMPFILTER = new ItemUpgradeFluidPumpFilter(new Properties().stacksTo(64).tab(PEDESTALS_TAB)).setRegistryName(new ResourceLocation(MODID, "coin/fluidpumpfilter"));

    @SubscribeEvent
    public static void onItemRegistryReady(RegistryEvent.Register<Item> event)
    {
        event.getRegistry().register(FLUIDPUMPFILTER);
    }


}
