package com.darkender.plugins.regionapi;

import org.bukkit.Location;
import org.bukkit.World;

import java.io.File;

/**
 * Class containing static API methods
 */
public class RegionAPI
{
    /**
     * Gets the region file associated with the specified region coordinates and world
     * @param world The world to get the region file for
     * @param regionX The X region coordinate
     * @param regionZ The Z region coordinate
     * @return The file for the region at the specified coordinates
     */
    public static File getRegionFile(World world, int regionX, int regionZ)
    {
        String regionFileName = "r." + regionX + "." + regionZ + ".mca";
        return new File(world.getWorldFolder().getPath() + "/region/" + regionFileName);
    }
    
    /**
     * Gets the region file associated with the specified location
     * @param location The location to get the region file for
     * @return The region file that contains the location
     */
    public static File getRegionFile(Location location)
    {
        return getRegionFile(location.getWorld(),
                (int) Math.floor(location.getX() / 512), (int) Math.floor(location.getZ() / 512));
    }
}
