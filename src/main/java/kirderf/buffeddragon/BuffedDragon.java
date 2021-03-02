package kirderf.buffeddragon;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.network.FMLNetworkConstants;
import org.apache.commons.lang3.tuple.Pair;

import java.util.UUID;

// The value here should match an entry in the META-INF/mods.toml file
@Mod("buffed-dragon")
public class BuffedDragon
{
	
	public static final Config CONFIG;
	private static final ForgeConfigSpec configSpec;
	
	public BuffedDragon()
	{
		//Make sure the mod being absent on the other network side does not cause the client to display the server as incompatible
		ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST, () -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (a, b) -> true));
		
		ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, configSpec);
		
		MinecraftForge.EVENT_BUS.register(this);
	}
	
	static
	{
		Pair<Config, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(Config::new);
		CONFIG = specPair.getLeft();
		configSpec = specPair.getRight();
	}
	
	private static final UUID BUFF_ID = UUID.fromString("77662d3b-d540-4841-bb59-621005adb318");
	
	@SubscribeEvent
	public void onEntityJoinEvent(EntityJoinWorldEvent event)
	{
		if(!event.getWorld().isRemote && event.getEntity() instanceof EnderDragonEntity)
		{
			EnderDragonEntity dragon = (EnderDragonEntity) event.getEntity();
			AttributeModifier modifier = new AttributeModifier(BUFF_ID,
					"Ender dragon modded buff", CONFIG.maxHealthMultiplier.get(), AttributeModifier.Operation.MULTIPLY_BASE).setSaved(true);
			if(!dragon.getAttribute(SharedMonsterAttributes.MAX_HEALTH).hasModifier(modifier))
			{
				dragon.getAttribute(SharedMonsterAttributes.MAX_HEALTH).applyModifier(modifier);
				dragon.setHealth(dragon.getMaxHealth());    //Assume that the dragon is new if it didn't have the modifier
			}
		}
	}
	
	@SubscribeEvent    // The attack damage attribute is not used by the dragon, so multiply the damage directly
	public void onLivingDamage(LivingHurtEvent event)
	{
		if(!event.getEntity().world.isRemote && event.getSource().getTrueSource() instanceof EnderDragonEntity)
		{
			event.setAmount((float) (event.getAmount() * CONFIG.damageMultiplier.get()));
		}
	}
	
	@SubscribeEvent
	public void onWorldTick(TickEvent.WorldTickEvent event)
	{
		if(!event.world.isRemote && event.phase == TickEvent.Phase.START && event.world.dimension.getType() == DimensionType.THE_END)
		{
			EndDimensionTracker.onTick((ServerWorld) event.world);
		}
	}
	
	public static class Config
	{
		public final ForgeConfigSpec.DoubleValue maxHealthMultiplier;
		public final ForgeConfigSpec.DoubleValue damageMultiplier;
		public final ForgeConfigSpec.BooleanValue doCrystalRespawn;
		public final ForgeConfigSpec.BooleanValue doCrystalMobSpawn;
		public final ForgeConfigSpec.BooleanValue doPostCrystalSpawns;
		
		private Config(ForgeConfigSpec.Builder builder)
		{
			builder.push("options");
			maxHealthMultiplier = builder.comment("A multiplier that modifies the max health of the dragon. (for example, 1 for unchanged, 2 for double etc)")
					.defineInRange("maxHealthMultiplier", 15, 1, Double.MAX_VALUE);
			damageMultiplier = builder.comment("A multiplier that modifies any damage that is attributed to the dragon.")
					.defineInRange("damageMultiplier", 2.5, 1, Double.MAX_VALUE);
			doCrystalRespawn = builder.comment("Respawn crystals once after most have been destroyed.")
					.define("doCrystalRespawn", true);
			doCrystalMobSpawn = builder.comment("Spawn certain mobs with crystals being destroyed.")
					.define("doCrystalMobSpawn", true);
			doPostCrystalSpawns = builder.comment("Spawn certain mobs after all crystals have been destroyed.")
					.define("doPostCrystalSpawns", true);
			builder.pop();
		}
	}
}