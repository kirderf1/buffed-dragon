package kirderf.buffeddragon;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.item.EnderCrystalEntity;
import net.minecraft.entity.monster.BlazeEntity;
import net.minecraft.entity.monster.GhastEntity;
import net.minecraft.entity.monster.VexEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.dimension.EndDimension;
import net.minecraft.world.end.DragonFightManager;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.gen.feature.EndPodiumFeature;
import net.minecraft.world.gen.feature.EndSpikeFeature;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.DimensionSavedDataManager;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.registries.ObjectHolder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class EndDimensionTracker extends WorldSavedData
{
	private static final String DATA_NAME = "buffed_dragon_info";
	
	private UUID dragonUUID;
	private EnderDragonEntity dragon;
	private final List<EnderCrystalEntity> crystals = new ArrayList<>();
	
	private boolean hasCrystalsRespawned = false;
	private int underlingSpawnsLeft = 10;
	
	public EndDimensionTracker()
	{
		super(DATA_NAME);
	}
	
	@Override
	public void read(CompoundNBT nbt)
	{
		if(nbt.hasUniqueId("dragon"))
		{
			dragonUUID = nbt.getUniqueId("dragon");
			hasCrystalsRespawned = nbt.getBoolean("respawnedCrystals");
			underlingSpawnsLeft = nbt.getInt("underlingSpawns");
		}
	}
	
	@Override
	public CompoundNBT write(CompoundNBT nbt)
	{
		if(dragonUUID != null)
		{
			nbt.putUniqueId("dragon", dragonUUID);
			nbt.putBoolean("respawnedCrystals", hasCrystalsRespawned);
			nbt.putInt("underlingSpawns", underlingSpawnsLeft);
		}
		return nbt;
	}
	
	private boolean canCrystalsRespawn()
	{
		return !hasCrystalsRespawned && BuffedDragon.CONFIG.doCrystalRespawn.get();
	}
	
	public static void onTick(ServerWorld world)
	{
		EndDimensionTracker tracker = get(world);
		
		tracker.check(world);
		
		tracker.prepare(world);
		
	}
	
	private void prepare(ServerWorld world)
	{
		DragonFightManager fightManager = ((EndDimension) world.dimension).getDragonFightManager();
		
		if(dragonUUID == null && !world.getDragons().isEmpty())
		{
			CompoundNBT nbt = fightManager.write();
			if(nbt.hasUniqueId("DragonUUID"))
			{
				dragonUUID = nbt.getUniqueId("DragonUUID");
				markDirty();
			}
		}
		
		if(dragon == null && dragonUUID != null)
		{
			for(EnderDragonEntity dragon : world.getDragons())
			{
				if(dragon.getUniqueID().equals(dragonUUID))
				{
					this.dragon = dragon;
					break;
				}
			}
		}
		
		if(dragon != null && fightManager.getNumAliveCrystals() != crystals.size())
		{
			crystals.clear();
			for(EndSpikeFeature.EndSpike spike : EndSpikeFeature.generateSpikes(world))
				crystals.addAll(world.getEntitiesWithinAABB(EnderCrystalEntity.class, spike.getTopBoundingBox(), Entity::isAlive));
		}
	}
	
	private void check(ServerWorld world)
	{
		if(dragon != null)
		{
			if(!canCrystalsRespawn() && crystals.isEmpty() && underlingSpawnsLeft > 0
					&& dragon.getHealth() / dragon.getMaxHealth() < underlingSpawnsLeft / 10F)
			{
				summonUnderling(world);
				summonUnderling(world);
				underlingSpawnsLeft--;
				markDirty();
			}
			
			if(!dragon.isAlive())
				clear();
		}
		
		int crystalsLeft = crystals.size();
		Iterator<EnderCrystalEntity> iterator = crystals.iterator();
		while(iterator.hasNext())
		{
			EnderCrystalEntity crystal = iterator.next();
			if(!crystal.isAlive())
			{
				if(onDestroyedCrystal(world, --crystalsLeft, crystal.getPosition()))
					break;
				iterator.remove();
			}
		}
	}
	
	private boolean onDestroyedCrystal(ServerWorld world, int crystalsLeft, BlockPos crystalPos)
	{
		if(!canCrystalsRespawn() && crystalsLeft <= 6)
			spawnCrystalEntities(world, crystalPos, crystalsLeft);
		if(canCrystalsRespawn() && crystalsLeft <= 2)
		{
			respawnCrystals(world);
			return true;
		}
		return false;
	}
	
	private void clear()
	{
		dragonUUID = null;
		dragon = null;
		hasCrystalsRespawned = false;
		underlingSpawnsLeft = 10;
		crystals.clear();
		markDirty();
	}
	
	private void respawnCrystals(ServerWorld world)
	{
		for(EndSpikeFeature.EndSpike spike : EndSpikeFeature.generateSpikes(world))
		{
			BlockPos pos = new BlockPos(spike.getCenterX(), spike.getHeight() + 1, spike.getCenterZ());
			if(crystals.stream().noneMatch(crystal -> crystal.getPosition().equals(pos)))
			{
				EnderCrystalEntity crystal = EntityType.END_CRYSTAL.create(world);
				crystal.setLocationAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.rand.nextFloat() * 360.0F, 0.0F);
				world.addEntity(crystal);
			}
		}
		crystals.clear(); // Force the list to be rebuilt at the next prepare call instead of waiting for the fight manager to catch up
		hasCrystalsRespawned = true;
		markDirty();
	}
	
	private void spawnCrystalEntities(ServerWorld world, BlockPos pos, int crystalsLeft)
	{
		if(!BuffedDragon.CONFIG.doCrystalMobSpawn.get())
			return;
		
		summonVex(world, pos.north(5));
		summonVex(world, pos.south(5));
		summonVex(world, pos.west(5));
		summonVex(world, pos.east(5));
		if(crystalsLeft <= 4)
		{
			summonBlaze(world, pos.north(5).east(5));
			summonBlaze(world, pos.south(5).west(5));
			summonBlaze(world, pos.west(5).north(5));
			summonBlaze(world, pos.east(5).south(5));
		}
		if(crystalsLeft <= 2)
		{
			summonGhast(world, pos.north(5).east(5).up(5));
			summonGhast(world, pos.south(5).west(5).up(5));
			summonGhast(world, pos.west(5).north(5).up(5));
			summonGhast(world, pos.east(5).south(5).up(5));
		}
	}
	
	private void summonBlaze(ServerWorld world, BlockPos pos)
	{
		BlazeEntity blaze = EntityType.BLAZE.create(world);
		blaze.getAttribute(SharedMonsterAttributes.MAX_HEALTH).applyModifier(new AttributeModifier("Buffed-dragon mod boost", 15, AttributeModifier.Operation.MULTIPLY_BASE).setSaved(true));
		blaze.setHealth(blaze.getMaxHealth());
		blaze.getAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).applyModifier(new AttributeModifier("Buffed-dragon mod boost", 3, AttributeModifier.Operation.MULTIPLY_BASE));
		blaze.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).applyModifier(new AttributeModifier("Buffed-dragon mod boost", 2, AttributeModifier.Operation.MULTIPLY_BASE));
		blaze.setLocationAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.rand.nextFloat() * 360F, 0);
		world.addEntity(blaze);
	}
	
	private void summonVex(ServerWorld world, BlockPos pos)
	{
		VexEntity vex = EntityType.VEX.create(world);
		vex.getAttribute(SharedMonsterAttributes.MAX_HEALTH).applyModifier(new AttributeModifier("Buffed-dragon mod boost", 15, AttributeModifier.Operation.MULTIPLY_BASE));
		vex.setHealth(vex.getMaxHealth());
		vex.getAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).applyModifier(new AttributeModifier("Buffed-dragon mod boost", 2, AttributeModifier.Operation.MULTIPLY_BASE));
		vex.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).applyModifier(new AttributeModifier("Buffed-dragon mod boost", 1.5, AttributeModifier.Operation.MULTIPLY_BASE));
		vex.setLocationAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.rand.nextFloat() * 360F, 0);
		world.addEntity(vex);
	}
	
	private void summonGhast(ServerWorld world, BlockPos pos)
	{
		GhastEntity ghast = EntityType.GHAST.create(world);
		ghast.getAttribute(SharedMonsterAttributes.MAX_HEALTH).applyModifier(new AttributeModifier("Buffed-dragon mod boost", 20, AttributeModifier.Operation.MULTIPLY_BASE));
		ghast.setHealth(ghast.getMaxHealth());
		ghast.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).applyModifier(new AttributeModifier("Buffed-dragon mod boost", 3, AttributeModifier.Operation.MULTIPLY_BASE));
		ghast.addPotionEffect(new EffectInstance(Effects.INVISIBILITY, 999999999));
		ghast.setLocationAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.rand.nextFloat() * 360F, 0);
		world.addEntity(ghast);
	}
	
	@ObjectHolder("minestuck:imp")
	public static final EntityType<?> IMP = null;
	@ObjectHolder("minestuck:ogre")
	public static final EntityType<?> OGRE = null;
	@ObjectHolder("minestuck:basilisk")
	public static final EntityType<?> BASILISK = null;
	@ObjectHolder("minestuck:lich")
	public static final EntityType<?> LICH = null;
	
	private void summonUnderling(ServerWorld world)
	{
		if(!BuffedDragon.CONFIG.doPostCrystalSpawns.get())
			return;
		
		int xOffset = world.rand.nextInt(11) - 5, zOffset = world.rand.nextInt(11) - 5;
		BlockPos pos = world.getHeight(Heightmap.Type.MOTION_BLOCKING, EndPodiumFeature.END_PODIUM_LOCATION.add(xOffset, 0, zOffset));
		int i = world.rand.nextInt(4);
		EntityType<?> type = i == 0 ? IMP : i == 1 ? OGRE : i == 2 ? BASILISK : LICH;
		
		//noinspection ConstantConditions
		if(type != null)
		{
			LivingEntity entity = (LivingEntity) type.create(world);
			CompoundNBT nbt = new CompoundNBT();
			nbt.putString("Type", "minestuck:artifact");
			entity.read(nbt);
			entity.getAttribute(SharedMonsterAttributes.MAX_HEALTH).applyModifier(new AttributeModifier("Buffed-dragon mod boost", 10, AttributeModifier.Operation.MULTIPLY_BASE));
			entity.setHealth(entity.getMaxHealth());
			entity.getAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).applyModifier(new AttributeModifier("Buffed-dragon mod boost", 1.5, AttributeModifier.Operation.MULTIPLY_BASE));
			entity.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).applyModifier(new AttributeModifier("Buffed-dragon mod boost", 1.5, AttributeModifier.Operation.MULTIPLY_BASE));
			entity.setLocationAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.rand.nextFloat() * 360F, 0);
			world.addEntity(entity);
		}
	}
	
	private static EndDimensionTracker get(ServerWorld world)
	{
		DimensionSavedDataManager storage = world.getSavedData();
		EndDimensionTracker instance = storage.get(EndDimensionTracker::new, DATA_NAME);
		
		if(instance == null)    //There is no save data
		{
			instance = new EndDimensionTracker();
			storage.set(instance);
		}
		
		return instance;
	}
}