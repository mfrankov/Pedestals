package com.mowmaster.pedestals.item.pedestalUpgrades;

import com.mowmaster.pedestals.enchants.EnchantmentRegistry;
import com.mowmaster.pedestals.references.Reference;
import com.mowmaster.pedestals.tiles.PedestalTileEntity;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.mowmaster.pedestals.pedestals.PEDESTALS_TAB;
import static com.mowmaster.pedestals.references.Reference.MODID;

public class ItemUpgradeFilteredExportEnchanted extends ItemUpgradeBaseFilter
{
    public ItemUpgradeFilteredExportEnchanted(Properties builder) {super(builder.tab(PEDESTALS_TAB));}

    @Override
    public Boolean canAcceptCapacity() {
        return true;
    }

    @Override
    public Boolean canAcceptOpSpeed() {
        return true;
    }

    @Override
    public Boolean canAcceptAdvanced() {return true;}

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        if(stack.getItem() instanceof ItemUpgradeBase && enchantment.getRegistryName().getNamespace().equals(Reference.MODID))
        {
            return !EnchantmentRegistry.COINUPGRADE.equals(enchantment.type) && super.canApplyAtEnchantingTable(stack, enchantment);
        }
        return false;
    }

    @Override
    public int getItemEnchantability()
    {
        return 10;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return (stack.getCount()==1)?(super.isBookEnchantable(stack, book)):(false);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public boolean canAcceptItem(World world, BlockPos posPedestal, ItemStack itemStackIn)
    {
        boolean returner = false;

        if(itemStackIn.isEnchanted() || itemStackIn.getItem().equals(Items.ENCHANTED_BOOK))
        {
            returner = true;
        }

        return returner;
    }

    private int getNextSlotEmptyOrMatching(LazyOptional<IItemHandler> cap, ItemStack stackInPedestal)
    {
        AtomicInteger slot = new AtomicInteger(-1);
        if(cap.isPresent())
        {
            IItemHandler handler = cap.orElse(null);
            if(handler != null)
            {
                int range = handler.getSlots();
                for(int i=0;i<range;i++)
                {
                    ItemStack stackInSlot = handler.getStackInSlot(i);
                    int maxSizeSlot = handler.getSlotLimit(i);
                    if(maxSizeSlot>0)
                    {
                        if(stackInSlot.getMaxStackSize()>1)
                        {
                            if(doItemsMatch(stackInSlot,stackInPedestal) && stackInSlot.getCount() < handler.getSlotLimit(i))
                            {
                                slot.set(i);
                                break;
                            }
                            else if(stackInSlot.isEmpty())
                            {
                                slot.set(i);
                                break;
                            }
                            //if chest is full
                            else if(i==range)
                            {
                                slot.set(i);
                            }
                        }
                    }
                }
            }
        }



        return slot.get();
    }

    public void updateAction(World world, PedestalTileEntity pedestal)
    {
        if(!world.isClientSide)
        {
            ItemStack coinInPedestal = pedestal.getCoinOnPedestal();
            ItemStack itemInPedestal = pedestal.getItemInPedestal();
            BlockPos pedestalPos = pedestal.getBlockPos();

            int speed = getOperationSpeed(coinInPedestal);

            if(!world.hasNeighborSignal(pedestalPos))
            {
                if (world.getGameTime()%speed == 0) {
                    upgradeAction(world,pedestalPos,coinInPedestal);
                }
            }
        }
    }

    public void upgradeAction(World world, BlockPos posOfPedestal, ItemStack coinInPedestal)
    {
        BlockPos posInventory = getBlockPosOfBlockBelow(world,posOfPedestal,1);
        int upgradeTransferRate = getItemTransferRate(coinInPedestal);
        ItemStack itemFromPedestal = ItemStack.EMPTY;
        //Checks to make sure a TE exists

        LazyOptional<IItemHandler> cap = findItemHandlerAtPos(world,posInventory,getPedestalFacing(world, posOfPedestal),true);
        if(hasAdvancedInventoryTargeting(coinInPedestal))cap = findItemHandlerAtPosAdvanced(world,posInventory,getPedestalFacing(world, posOfPedestal),true);

        //Gets inventory TE then makes sure its not a pedestal
        TileEntity invToPushTo = world.getTileEntity(posInventory);
        if(invToPushTo instanceof PedestalTileEntity) {
            itemFromPedestal = ItemStack.EMPTY;
        }
        else {
            itemFromPedestal = getStackInPedestal(world,posOfPedestal);
            //IF pedestal is empty and has nothing to transfer then dont do anything
            if(!itemFromPedestal.isEmpty() && !itemFromPedestal.equals(ItemStack.EMPTY))
            {
                if(cap.isPresent())
                {
                    IItemHandler handler = cap.orElse(null);

                    //gets next empty or partially filled matching slot
                    int i = getNextSlotEmptyOrMatching(cap, itemFromPedestal);
                    if(handler != null)
                    {
                        if(i>=0)
                        {
                            if(handler.isItemValid(i, itemFromPedestal))
                            {
                                itemFromPedestal = getStackInPedestal(world,posOfPedestal).copy();
                                ItemStack itemFromInventory = handler.getStackInSlot(i);
                                int spaceInInventoryStack = handler.getSlotLimit(i) - itemFromInventory.getCount();

                                //if inv slot is empty it should be able to handle as much as we can give it
                                int allowedTransferRate = upgradeTransferRate;
                                //checks allowed slot size amount and sets it if its lower then transfer rate
                                if(handler.getSlotLimit(i) <= allowedTransferRate) allowedTransferRate = handler.getSlotLimit(i);
                                //never have to check to see if pedestal and stack match because the slot checker does it for us
                                //if our transfer rate is bigger then what can go in the slot if its partially full we set the transfer size to what can fit
                                //Otherwise if space is bigger then rate we know it can accept as much as we're putting in
                                if(allowedTransferRate> spaceInInventoryStack) allowedTransferRate = spaceInInventoryStack;
                                //IF items in pedestal are less then the allowed transfer amount then set it as the amount
                                if(allowedTransferRate > itemFromPedestal.getCount()) allowedTransferRate = itemFromPedestal.getCount();

                                //After all calculations for transfer rate, set stack size to transfer and transfer the items
                                itemFromPedestal.setCount(allowedTransferRate);

                                if(ItemHandlerHelper.insertItem(handler,itemFromPedestal,true).equals(ItemStack.EMPTY)){
                                    removeFromPedestal(world,posOfPedestal ,allowedTransferRate);
                                    ItemHandlerHelper.insertItem(handler,itemFromPedestal,false);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void chatDetails(PlayerEntity player, PedestalTileEntity pedestal)
    {
        ItemStack stack = pedestal.getCoinOnPedestal();

        TranslationTextComponent name = new TranslationTextComponent(getDescriptionId() + ".tooltip_name");
        name.withStyle(TextFormatting.GOLD);
        player.sendMessage(name,Util.NIL_UUID);

        TranslationTextComponent rate = new TranslationTextComponent(getDescriptionId() + ".chat_rate");
        rate.append(""+getItemTransferRate(stack)+"");
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
        super.addInformation(stack, worldIn, tooltip, flagIn);

        TranslationTextComponent rate = new TranslationTextComponent(getDescriptionId() + ".tooltip_rate");
        rate.append("" + getItemTransferRate(stack) + "");
        TranslationTextComponent speed = new TranslationTextComponent(getDescriptionId() + ".tooltip_speed");
        speed.append(getOperationSpeedString(stack));

        rate.withStyle(TextFormatting.GRAY);
        speed.withStyle(TextFormatting.RED);

        tooltip.add(rate);
        tooltip.add(speed);
    }

    public static final Item FEXPORTENCHANTED = new ItemUpgradeFilteredExportEnchanted(new Properties().stacksTo(64).tab(PEDESTALS_TAB)).setRegistryName(new ResourceLocation(MODID, "coin/fexportenchanted"));

    @SubscribeEvent
    public static void onItemRegistryReady(RegistryEvent.Register<Item> event)
    {
        event.getRegistry().register(FEXPORTENCHANTED);
    }


}
