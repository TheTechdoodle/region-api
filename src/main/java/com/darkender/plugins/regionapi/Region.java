package com.darkender.plugins.regionapi;

import java.io.*;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

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
    
    /**
     * Clones the region to another file, automatically changing all coordinates in the region
     * @param to The file to write to
     * @param fromRegionX The starting region X coordinate (in region coordinates, not block coordinates)
     * @param fromRegionZ The starting region Z coordinate (in region coordinates, not block coordinates)
     * @param toRegionX The cloned region X coordinate (in region coordinates, not block coordinates)
     * @param toRegionZ The cloned region Z coordinate (in region coordinates, not block coordinates)
     * @throws IOException Thrown if an error occurred writing or reading
     */
    public void clone(RandomAccessFile to, int fromRegionX, int fromRegionZ, int toRegionX, int toRegionZ) throws IOException
    {
        int chunkXDisplacement = (toRegionX - fromRegionX) * 32;
        int chunkZDisplacement = (toRegionZ - fromRegionZ) * 32;
        
        try(BufferedOutputStream fileBufferedOutputStream = new BufferedOutputStream(
                new FileOutputStream(to.getFD()), 4096);
            DataOutputStream dataOutputStream = new DataOutputStream(fileBufferedOutputStream))
        {
            to.setLength(4096 * 2);
            to.seek(4096 * 2);
            
            // Begin writing chunks
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
                    int offset = this.getOffset(x, z);
                    int length = this.getLength(x, z);
                    
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
                                                        new ByteArrayInputStream(this.getChunkCompressedData(offset)), inflater)));
                                
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
            to.write(this.getTimestamps());
            to.close();
        }
        catch(IOException e)
        {
            e.printStackTrace();
        }
    }
}
