package org.dave.CompactMachines.handler.machinedimension;

import java.util.HashMap;
import java.util.Iterator;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

import org.dave.CompactMachines.handler.ConfigurationHandler;
import org.dave.CompactMachines.handler.machinedimension.tools.ChunkLoadingTools;
import org.dave.CompactMachines.handler.machinedimension.tools.CubeTools;
import org.dave.CompactMachines.handler.machinedimension.tools.TeleportTools;
import org.dave.CompactMachines.reference.Reference;
import org.dave.CompactMachines.tileentity.TileEntityMachine;
import org.dave.CompactMachines.utility.PlayerUtils;

public class MachineHandler extends WorldSavedData {

	int							nextCoord;
	private World				worldObj;

	HashMap<Integer, double[]>	spawnPoints	= new HashMap<Integer, double[]>();
	HashMap<Integer, Integer>	roomSizes	= new HashMap<Integer, Integer>();

	public MachineHandler(String s) {
		super(s);

		nextCoord = 0;
	}

	public MachineHandler(World worldObj) {
		this("MachineHandler");
		this.worldObj = worldObj;
	}

	public void tick() {
		if (worldObj.getTotalWorldTime() % 10 == 0 && ConfigurationHandler.keepPlayersInsideOfRooms) {
			for (int i = 0; i < worldObj.playerEntities.size(); i++) {
				if (worldObj.playerEntities.get(i) instanceof EntityPlayer) {
					EntityPlayer player = (EntityPlayer) worldObj.playerEntities.get(i);
					if (player.capabilities.isCreativeMode && PlayerUtils.isPlayerOpped(player)) {
						// Opped players in creative mode are actually allowed to leave the rooms
						continue;
					}
					int lastCoord = PlayerUtils.getPlayerCoords(player);
					if (lastCoord == -1) {
						// We don't know where the player is atm :(
						continue;
					}
					if (!roomSizes.containsKey(lastCoord)) {
						// We sadly don't know the size of the room the player is in. Skipping.
						// This automatically changes once any player enters the cube again.
						continue;
					}

					int roomSize = Reference.getBoxSize(roomSizes.get(lastCoord));

					AxisAlignedBB bb = CubeTools.getBoundingBoxForCube(lastCoord, roomSize);
					if (!bb.isVecInside(Vec3.createVectorHelper(player.posX, player.posY, player.posZ))) {
						TeleportTools.teleportPlayerToCoords((EntityPlayerMP) player, lastCoord, true);

						// Add potion effects for 200 ticks
						player.addPotionEffect(new PotionEffect(2, 200, 5, false));	// Slowness
						player.addPotionEffect(new PotionEffect(9, 200, 5, false)); // Nausea
					}
				}
			}
		}
	}

	public void setCoordSpawnpoint(EntityPlayerMP player) {
		int lastCoord = PlayerUtils.getPlayerCoords(player);
		if (lastCoord > -1 && roomSizes.containsKey(lastCoord)) {
			int roomSize = Reference.getBoxSize(roomSizes.get(lastCoord));
			AxisAlignedBB bb = CubeTools.getBoundingBoxForCube(lastCoord, roomSize);

			if (bb.isVecInside(Vec3.createVectorHelper(player.posX, player.posY, player.posZ))) {
				spawnPoints.put(lastCoord, new double[] { player.posX, player.posY, player.posZ, player.rotationYaw, player.rotationPitch });
			}
		}

		this.markDirty();
	}

	public double[] getSpawnLocation(int coord) {
		boolean usingPresetSpawnpoint = false;
		double[] destination = new double[] { coord * ConfigurationHandler.cubeDistance + 1.5, 42, 1.5 };
		if (spawnPoints.containsKey(coord)) {
			destination = spawnPoints.get(coord);
			usingPresetSpawnpoint = true;
		} else if (roomSizes.containsKey(coord)) {
			int size = Reference.getBoxSize(roomSizes.get(coord));
	
			destination = new double[] {
					coord * ConfigurationHandler.cubeDistance + 0.5 + size / 2,
					42,
					0.5 + size / 2
			};
		}
	
		return destination;
	}

	public int createOrGetChunk(TileEntityMachine machine) {
		if (machine.coords != -1) {
			return machine.coords;
		}

		//LogHelper.info("Reserving new coords...");
		machine.coords = nextCoord;
		machine.markDirty();

		nextCoord++;

		CubeTools.generateCube(machine);
		ChunkLoadingTools.forceChunkLoad(machine.coords);

		roomSizes.put(machine.coords, machine.meta);
		this.markDirty();

		return machine.coords;
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt) {
		nextCoord = nbt.getInteger("nextMachineCoord");

		if (nbt.hasKey("spawnpoints")) {
			spawnPoints.clear();
			NBTTagList tagList = nbt.getTagList("spawnpoints", 10);
			for (int i = 0; i < tagList.tagCount(); i++) {
				NBTTagCompound tag = tagList.getCompoundTagAt(i);
				int coords = tag.getInteger("coords");
				double[] positions = new double[] { tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"), tag.getDouble("yaw"), tag.getDouble("pitch") };

				spawnPoints.put(coords, positions);
			}
		}

		if (nbt.hasKey("roomsizes")) {
			roomSizes.clear();
			NBTTagList tagList = nbt.getTagList("roomsizes", 10);
			for (int i = 0; i < tagList.tagCount(); i++) {
				NBTTagCompound tag = tagList.getCompoundTagAt(i);
				int coords = tag.getInteger("coords");
				int size = tag.getInteger("size");

				roomSizes.put(coords, size);
			}
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt) {
		nbt.setInteger("nextMachineCoord", nextCoord);

		NBTTagList sizeList = new NBTTagList();
		Iterator sizeIterator = roomSizes.keySet().iterator();
		while (sizeIterator.hasNext()) {
			int coords = (Integer) sizeIterator.next();
			int size = roomSizes.get(coords);

			NBTTagCompound tag = new NBTTagCompound();
			tag.setInteger("coords", coords);
			tag.setInteger("size", size);
			sizeList.appendTag(tag);
		}
		nbt.setTag("roomsizes", sizeList);

		NBTTagList tagList = new NBTTagList();
		Iterator sp = spawnPoints.keySet().iterator();
		while (sp.hasNext()) {
			int coords = (Integer) sp.next();
			double[] positions = spawnPoints.get(coords);

			NBTTagCompound tag = new NBTTagCompound();
			tag.setInteger("coords", coords);
			tag.setDouble("x", positions[0]);
			tag.setDouble("y", positions[1]);
			tag.setDouble("z", positions[2]);
			if(positions.length == 5) {
				tag.setDouble("yaw", positions[3]);
				tag.setDouble("pitch", positions[4]);
			}

			tagList.appendTag(tag);
		}

		nbt.setTag("spawnpoints", tagList);
	}

	public World getWorld() {
		return this.worldObj;
	}

	public void setWorld(World world) {
		this.worldObj = world;
	}

	public int getRoomSize(int coord) {
		return roomSizes.get(coord);
	}

}
