package com.darkender.plugins.regionapi;

import org.bukkit.Location;
import org.bukkit.World;

import java.io.*;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

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
    
    /**
     * Clones the region to another file, automatically changing all coordinates in the region
     * @param from The region file to read from
     * @param to The file to write to
     * @param fromRegionX The starting region X coordinate (in region coordinates, not block coordinates)
     * @param fromRegionZ The starting region Z coordinate (in region coordinates, not block coordinates)
     * @param toRegionX The cloned region X coordinate (in region coordinates, not block coordinates)
     * @param toRegionZ The cloned region Z coordinate (in region coordinates, not block coordinates)
     * @throws IOException Thrown if an error occurred writing or reading
     */
    public static void cloneRegion(File from, RandomAccessFile to,
                                   int fromRegionX, int fromRegionZ, int toRegionX, int toRegionZ) throws IOException
    {
        int chunkXDisplacement = (toRegionX - fromRegionX) * 32;
        int chunkZDisplacement = (toRegionZ - fromRegionZ) * 32;
        
        Region region = new Region(from);
        try(BufferedOutputStream fileBufferedOutputStream = new BufferedOutputStream(
                new FileOutputStream(to.getFD()), 4096);
            DataOutputStream dataOutputStream = new DataOutputStream(fileBufferedOutputStream))
        {
            to.setLength(4096 * 2);
            to.seek(4096 * 2);
        
            // Begin writing chunks
            long totalChunkTime = 0;
        
            int currentOffset = 2;
            int i = 0;
            byte[] locations = new byte[4096];
        
            Deflater deflater = new Deflater(1);
            Inflater inflater = new Inflater();
        
            NBTPositionReplace positionReplace = new NBTPositionReplace(chunkXDisplacement, chunkZDisplacement,
                    chunkXDisplacement * 16, chunkZDisplacement * 16);
        
            for(int z = 0; z < 32; z++)
            {
                for(int x = 0; x < 32; x++)
                {
                    // Get the chunk data from the source
                    int offset = region.getOffset(x, z);
                    int length = region.getLength(x, z);
                
                    if(offset == 0 && length == 0)
                    {
                        // If the chunk hasn't been generated yet
                        for(int pos = (i * 4); pos < (i * 4) + 4; pos++)
                        {
                            locations[pos] = 0;
                        }
                    }
                    else
                    {
                        // Rewrite the chunk
                    
                        // Set up decompression and recompression streams
                        byte[] compressedBytes;
                        try(
                                DataInputStream is = new DataInputStream(
                                        new BufferedInputStream(
                                                new InflaterInputStream(
                                                        new ByteArrayInputStream(region.getChunkCompressedData(offset)), inflater)));
                            
                                ByteArrayOutputStream compressed = new ByteArrayOutputStream();
                                BufferedOutputStream bufferedCompressed = new BufferedOutputStream(compressed);
                                DeflaterOutputStream deflaterOutputStream = new DeflaterOutputStream(bufferedCompressed, deflater);
                                DataOutputStream compressedDataOutputStream = new DataOutputStream(deflaterOutputStream))
                        {
                            // Replace most absolute positions in chunk
                            positionReplace.replace(is, compressedDataOutputStream);
                        
                            // Cry
                            compressedDataOutputStream.flush();
                            deflaterOutputStream.flush();
                            deflaterOutputStream.finish();
                            bufferedCompressed.flush();
                            compressed.flush();
                        
                            // Get the byte data
                            compressedBytes = compressed.toByteArray();
                        }
                        inflater.reset();
                        deflater.reset();
                    
                        // Round to the nearest 4kb
                        // Add 5-byte header
                        int partLength = compressedBytes.length + 5;
                        int sectors = (partLength + 4095) / 4096;
                        int padLength = (sectors * 4096) - partLength;
                    
                        // Write the chunk header, followed by the chunk
                        dataOutputStream.writeInt(compressedBytes.length);
                        dataOutputStream.writeByte(2);
                        dataOutputStream.write(compressedBytes);
                    
                        // Write any padding to fit the 4096-byte sector size
                        if(padLength > 0)
                        {
                            byte[] pad = new byte[padLength];
                            for(int padI = 0; padI < padLength; padI++)
                            {
                                pad[padI] = 0;
                            }
                            dataOutputStream.write(pad);
                        }
                    
                        // Set up the region header for the sector offset and sector length
                        int locOffset = i * 4;
                        locations[locOffset] = (byte) (currentOffset >> 16);
                        locations[locOffset + 1] = (byte) (currentOffset >> 8);
                        locations[locOffset + 2] = (byte) (currentOffset);
                        locations[locOffset + 3] = (byte) (sectors);
                        currentOffset += sectors;
                    }
                
                    i++;
                }
            }
            
            fileBufferedOutputStream.flush();
            to.seek(0);
            to.write(locations);
            to.write(region.getTimestamps());
            to.close();
            region.close();
        }
        catch(IOException e)
        {
            e.printStackTrace();
        }
    }
}
