package com.mowmaster.pedestals.Items.Upgrades.Pedestal;

import com.mowmaster.mowlib.Capabilities.Dust.DustMagic;
import com.mowmaster.mowlib.MowLibUtils.MowLibItemUtils;
import com.mowmaster.pedestals.Blocks.Pedestal.BasePedestalBlockEntity;
import com.mowmaster.pedestals.Configs.PedestalConfig;
import com.mowmaster.pedestals.Items.ISelectableArea;
import com.mowmaster.pedestals.Items.WorkCards.WorkCardBase;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.extensions.IForgeFluid;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.ref.WeakReference;
import java.util.List;

public class ItemUpgradeMilker extends ItemUpgradeBase
{
    public ItemUpgradeMilker(Properties p_41383_) {
        super(new Properties());
    }

    @Override
    public boolean canModifySpeed(ItemStack upgradeItemStack) {
        return true;
    }

    @Override
    public boolean canModifySuperSpeed(ItemStack upgradeItemStack) {
        return true;
    }

    @Override
    public boolean canModifyRange(ItemStack upgradeItemStack) {
        return true;
    }

    @Override
    public boolean canModifyArea(ItemStack upgradeItemStack) {
        return PedestalConfig.COMMON.upgrade_require_sized_selectable_area.get();
    }

    @Override
    public boolean needsWorkCard() { return true; }

    @Override
    public int getWorkCardType() { return 1; }

    //Requires energy
    @Override
    public int baseEnergyCostPerDistance(){ return PedestalConfig.COMMON.upgrade_milker_baseEnergyCost.get(); }
    @Override
    public boolean energyDistanceAsModifier() {return PedestalConfig.COMMON.upgrade_milker_energy_distance_multiplier.get();}
    @Override
    public double energyCostMultiplier(){ return PedestalConfig.COMMON.upgrade_milker_energyMultiplier.get(); }

    @Override
    public int baseXpCostPerDistance(){ return PedestalConfig.COMMON.upgrade_milker_baseXpCost.get(); }
    @Override
    public boolean xpDistanceAsModifier() {return PedestalConfig.COMMON.upgrade_milker_xp_distance_multiplier.get();}
    @Override
    public double xpCostMultiplier(){ return PedestalConfig.COMMON.upgrade_milker_xpMultiplier.get(); }

    @Override
    public DustMagic baseDustCostPerDistance(){ return new DustMagic(PedestalConfig.COMMON.upgrade_milker_dustColor.get(),PedestalConfig.COMMON.upgrade_milker_baseDustAmount.get()); }
    @Override
    public boolean dustDistanceAsModifier() {return PedestalConfig.COMMON.upgrade_milker_dust_distance_multiplier.get();}
    @Override
    public double dustCostMultiplier(){ return PedestalConfig.COMMON.upgrade_milker_dustMultiplier.get(); }

    @Override
    public boolean hasSelectedAreaModifier() { return PedestalConfig.COMMON.upgrade_milker_selectedAllowed.get(); }
    @Override
    public double selectedAreaCostMultiplier(){ return PedestalConfig.COMMON.upgrade_milker_selectedMultiplier.get(); }

    @Override
    public List<String> getUpgradeHUD(BasePedestalBlockEntity pedestal) {

        List<String> messages = super.getUpgradeHUD(pedestal);

        if(messages.size()<=0)
        {
            if(baseEnergyCostPerDistance()>0)
            {
                if(pedestal.getStoredEnergy()<baseEnergyCostPerDistance())
                {
                    messages.add(ChatFormatting.RED + "Needs Energy");
                    messages.add(ChatFormatting.RED + "To Operate");
                }
            }
            if(baseXpCostPerDistance()>0)
            {
                if(pedestal.getStoredExperience()<baseXpCostPerDistance())
                {
                    messages.add(ChatFormatting.GREEN + "Needs Experience");
                    messages.add(ChatFormatting.GREEN + "To Operate");
                }
            }
            if(baseDustCostPerDistance().getDustAmount()>0)
            {
                if(pedestal.getStoredEnergy()<baseEnergyCostPerDistance())
                {
                    messages.add(ChatFormatting.LIGHT_PURPLE + "Needs Dust");
                    messages.add(ChatFormatting.LIGHT_PURPLE + "To Operate");
                }
            }
        }

        return messages;
    }

    @Override
    public ItemStack getUpgradeDefaultTool() {
        return new ItemStack(Items.BUCKET);
    }

    @Override
    public void upgradeAction(Level level, BasePedestalBlockEntity pedestal, BlockPos pedestalPos, ItemStack coin)
    {
        if(level.isClientSide())return;

        if(pedestal.hasWorkCard())
        {
            ItemStack card = pedestal.getWorkCardInPedestal();
            if(card.getItem() instanceof WorkCardBase workCardBase)
            {
                if(workCardBase.hasTwoPointsSelected(card) && workCardBase.selectedAreaWithinRange(pedestal))
                {
                    boolean canRun = true;
                    Fluid getFluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation("minecraft:milk"));
                    if(getFluid !=null && (pedestal.spaceForFluid() < FluidType.BUCKET_VOLUME)){return;}
                    else if((getFluid == null || getFluid.defaultFluidState().isEmpty()) && pedestal.getItemInPedestal().isEmpty()){return;}
                    else
                    {
                        if(removeFuelForAction(pedestal, getDistanceBetweenPoints(pedestal.getPos(),pedestalPos), true))
                        {
                            WeakReference<FakePlayer> getPlayer = pedestal.getPedestalPlayer(pedestal);
                            if(getPlayer != null && getPlayer.get() != null)
                            {
                                AABB getArea = workCardBase.getAABBonUpgrade(card);
                                List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, getArea);
                                ItemStack toolStack = (pedestal.hasItem())?(pedestal.getItemInPedestal().copy()):(pedestal.getToolStack());
                                tryEquipItem(toolStack,getPlayer,InteractionHand.MAIN_HAND);

                                if(canRun)
                                {
                                    for (LivingEntity getEntity : entities)
                                    {
                                        if(getEntity == null)continue;

                                        BlockPos getEntityPos = getEntity.getOnPos();
                                        if(getEntity instanceof Animal animal)
                                        {
                                            InteractionResult result = animal.mobInteract((getPlayer.get() == null)?(pedestal.getPedestalPlayer(pedestal).get()):(getPlayer.get()), InteractionHand.MAIN_HAND);
                                            if(result == InteractionResult.CONSUME)
                                            {
                                                NonNullList<ItemStack> getItemsInPlayer = getPlayer.get().getInventory().items;
                                                for(int i=0;i<getItemsInPlayer.size();i++)
                                                {
                                                    ItemStack stackInPlayer = getItemsInPlayer.get(i);
                                                    //System.out.println(stackInPlayer.getItem());
                                                    if(!stackInPlayer.isEmpty() && !toolStack.getItem().equals(stackInPlayer.getItem()))
                                                    {
                                                        if(removeFuelForAction(pedestal, getDistanceBetweenPoints(pedestal.getPos(),getEntityPos), false))
                                                        {
                                                            if(stackInPlayer.getItem().equals(Items.MILK_BUCKET))
                                                            {
                                                                if(getFluid !=null && pedestal.getItemInPedestal().isEmpty())
                                                                {
                                                                    FluidStack getFluidStack = new FluidStack(getFluid,FluidType.BUCKET_VOLUME);
                                                                    if(pedestal.addFluid(getFluidStack, IFluidHandler.FluidAction.SIMULATE)>0)
                                                                    {
                                                                        pedestal.addFluid(getFluidStack, IFluidHandler.FluidAction.EXECUTE);
                                                                        if(!hasAdvancedOne(coin))break;
                                                                    }
                                                                }
                                                                else
                                                                {
                                                                    if(!pedestal.removeItem(1,true).isEmpty())
                                                                    {
                                                                        pedestal.removeItem(1,false);
                                                                        MowLibItemUtils.spawnItemStack(level,getEntityPos.getX(),getEntityPos.getY(),getEntityPos.getZ(),stackInPlayer);
                                                                        if(!hasAdvancedOne(coin))break;
                                                                    }
                                                                }
                                                            }
                                                            else if(stackInPlayer.getItem() instanceof BucketItem bucket)
                                                            {
                                                                if(!bucket.getFluid().equals(Fluids.EMPTY))
                                                                {
                                                                    FluidStack getFluidStack = new FluidStack(bucket.getFluid(),FluidType.BUCKET_VOLUME);
                                                                    if(pedestal.addFluid(getFluidStack, IFluidHandler.FluidAction.SIMULATE)>0)
                                                                    {
                                                                        pedestal.addFluid(getFluidStack, IFluidHandler.FluidAction.EXECUTE);
                                                                        if(!hasAdvancedOne(coin))break;
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
