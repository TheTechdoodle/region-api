package com.darkender.plugins.regionapi;

import java.io.*;

/**
 * Wrapper around a region file containing convenience methods to read data
 */
public class Region
{
    private final byte[] locations = new byte[4096];
    private final byte[] modified = new byte[4096];
    private final RandomAccessFile raf;
    
    /**
     * Create a Region object reading from a region file and initializes location and modification tables
     * @param regionFile The region file to read from
     * @throws IOException Throws if there is an exception reading from the file
     */
    public Region(File regionFile) throws IOException
    {
    
        raf = new RandomAccessFile(regionFile, "r");
        raf.seek(0);
        raf.readFully(locations);
        raf.readFully(modified);
    }
    
    /**
     * Closes the RandomAccessFile used to read the region data
     * @throws IOException Throws if there is an exception closing the file
     */
    public void close() throws IOException
    {
        raf.close();
    }
    
    /**
     * Gets the offset value from the location table
     * @param chunkX The (relative) chunk X coordinate
     * @param chunkZ The (relative) chunk Z coordinate
     * @return The offset value from the location table
     * @see <a href="https://wiki.vg/Region_Files#Location_Table">https://wiki.vg/Region_Files#Location_Table</a>
     */
    public int getOffset(int chunkX, int chunkZ)
    {
        int i = ((chunkX & 31) << 2) + ((chunkZ & 31) << 7);
        
        return (locations[i + 2] & 0xFF) | ((locations[i + 1] & 0xFF) << 8) | ((locations[i] & 0x0F) << 16);
    }
    
    /**
     * Gets the length value from the location table
     * @param chunkX The (relative) chunk X coordinate
     * @param chunkZ The (relative) chunk Z coordinate
     * @return The length value from the location table
     * @see <a href="https://wiki.vg/Region_Files#Location_Table">https://wiki.vg/Region_Files#Location_Table</a>
     */
    public int getLength(int chunkX, int chunkZ)
    {
        int i = ((chunkX & 31) << 2) + ((chunkZ & 31) << 7);
        return locations[i + 3];
    }
    
    /**
     * Gets the compressed chunk data at the specified offset
     * @param sectorOffset The sector offset (4096 bytes per sector) to begin reading from
     * @return Byte array containing the compressed chunk data
     * @throws IOException Throws if an exception occurred while reading data
     */
    public byte[] getChunkCompressedData(int sectorOffset) throws IOException
    {
        raf.seek(sectorOffset * 4096);
        int size = raf.readInt();
        
        /* Compression byte:
            "There are two possible values for the compression scheme.
             If it is a 1, the following chunk is compressed using gzip.
             If it's a 2, the following chunk is compressed with zlib.
             In practice, you will only ever encounter chunks compressed using zlib."
           https://wiki.vg/Region_Files#Chunk_Header
        */
        raf.read();
        
        byte[] data = new byte[size];
        raf.readFully(data);
        
        return data;
    }
    
    /**
     * Gets exact compressed chunk size in bytes
     * @param chunkX The (relative) X coordinate of the chunk
     * @param chunkZ The (relative) Z coordinate of the chunk
     * @return compressed chunk size in bytes
     * @throws IOException Throws if an exception occurred while reading data
     */
    public int getChunkSize(int chunkX, int chunkZ) throws IOException
    {
        int offset = getOffset(chunkX, chunkZ) * 4096;
        if(offset == 0 && getLength(chunkX, chunkZ) == 0)
        {
            return 0;
        }
        
        raf.seek(offset);
        return raf.readInt();
    }
    
    /**
     * Get the location table
     * @return The raw location table
     * @see <a href="https://wiki.vg/Region_Files#Location_Table">https://wiki.vg/Region_Files#Location_Table</a>
     */
    public byte[] getLocations()
    {
        return locations;
    }
    
    /**
     * Get the timestamp table
     * @return The raw timestamp table
     * @see <a href="https://wiki.vg/Region_Files#Timestamp_Table">https://wiki.vg/Region_Files#Timestamp_Table</a>
     */
    public byte[] getTimestamps()
    {
        return modified;
    }
}
