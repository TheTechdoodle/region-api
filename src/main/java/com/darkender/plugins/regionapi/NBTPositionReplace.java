package com.darkender.plugins.regionapi;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Class designed for quickly replacing position-related data in an nbt stream
 */
public class NBTPositionReplace
{
    private final int chunkXDisplacement;
    private final int chunkZDisplacement;
    private final int xDisplacement;
    private final int zDisplacement;
    
    /**
     * Creates and initializes the displacements used for calculating the resulting positions
     * @param chunkXDisplacement The amount (in chunk coordinates) the X coordinate should be displaced
     * @param chunkZDisplacement The amount (in chunk coordinates) the Z coordinate should be displaced
     * @param xDisplacement The amount (in block coordinates) the X coordinate should be displaced
     * @param zDisplacement The amount (in block coordinates) the Z coordinate should be displaced
     */
    public NBTPositionReplace(int chunkXDisplacement, int chunkZDisplacement, int xDisplacement, int zDisplacement)
    {
        this.chunkXDisplacement = chunkXDisplacement;
        this.chunkZDisplacement = chunkZDisplacement;
        this.xDisplacement = xDisplacement;
        this.zDisplacement = zDisplacement;
    }
    
    /**
     * Performs the displacement on the stream
     * @param is input stream containing the chunk nbt data
     * @param os output stream to write the modified data
     * @throws IOException throws if the input stream encounters an exception while reading
     */
    public void replace(DataInputStream is, DataOutputStream os) throws IOException
    {
        // Starting data is always a compound tag with name length (2 bytes) of 0
        byte[] startingData = new byte[3];
        is.readFully(startingData);
        os.write(startingData);
        
        writeTagRecursive(10, null, is, os);
    }
    
    /**
     * Recursively write the tags between streams, replacing displacements as it goes
     * @param tagType The NBT tag type
     * @param name The name of the NBT tag
     * @param is Input stream to read from
     * @param os Output stream to output modified data to
     * @throws IOException Throws exception if there was an error reading data
     */
    private void writeTagRecursive(int tagType, String name, DataInputStream is, DataOutputStream os) throws IOException
    {
        // Check for coordinate overwriting
        if(name != null)
        {
            if(tagType == 3 && (name.equals("xPos") || name.equals("ChunkX")))
            {
                os.writeInt(is.readInt() + chunkXDisplacement);
                return;
            }
            if(tagType == 3 && (name.equals("zPos") || name.equals("ChunkZ")))
            {
                os.writeInt(is.readInt() + chunkZDisplacement);
                return;
            }
            
            // Beehives have capital XZ but entities have xz
            if(tagType == 3 && (name.equalsIgnoreCase("x") ||
                    name.equals("posX") ||
                    name.equals("TileX") ||
                    name.equals("xTile") ||
                    name.equals("SleepingX") ||
                    name.equals("BoundX") ||
                    name.equals("HomePosX") ||
                    name.equals("TravelPosX") ||
                    name.equals("APX") ||
                    name.equals("AX") ||
                    name.equals("TreasurePosX")))
            {
                os.writeInt(is.readInt() + xDisplacement);
                return;
            }
            if(tagType == 3 && (name.equalsIgnoreCase("z") ||
                    name.equals("posZ") ||
                    name.equals("TileZ") ||
                    name.equals("zTile") ||
                    name.equals("SleepingZ") ||
                    name.equals("BoundZ") ||
                    name.equals("HomePosZ") ||
                    name.equals("TravelPosZ") ||
                    name.equals("APZ") ||
                    name.equals("AZ") ||
                    name.equals("TreasurePosZ")))
            {
                os.writeInt(is.readInt() + zDisplacement);
                return;
            }
            
            // Position lists
            if(tagType == 9 && name.equals("Pos"))
            {
                int listTagType = is.read();
                int listLength = is.readInt();
                os.writeByte(listTagType);
                os.writeInt(listLength);
                
                if(listTagType == 6 && listLength == 3)
                {
                    os.writeDouble(is.readDouble() + xDisplacement);
                    os.writeDouble(is.readDouble());
                    os.writeDouble(is.readDouble() + zDisplacement);
                }
                else if(listTagType == 3 && listLength == 3)
                {
                    os.writeInt(is.readInt() + xDisplacement);
                    os.writeInt(is.readInt());
                    os.writeInt(is.readInt() + zDisplacement);
                }
                else
                {
                    for(int i = 0; i < listLength; i++)
                    {
                        writeTagRecursive(listTagType, null, is, os);
                    }
                }
                return;
            }
        }
        
        switch(tagType)
        {
            // Compound end
            case 0:
            {
                //os.write(0);
                return;
            }
            // Byte
            case 1:
            {
                os.writeByte(is.readByte());
                break;
            }
            // Short
            case 2:
            {
                os.writeShort(is.readShort());
                break;
            }
            // Int
            case 3:
            {
                os.writeInt(is.readInt());
                break;
            }
            // Long
            case 4:
            {
                os.writeLong(is.readLong());
                break;
            }
            // Float
            case 5:
            {
                os.writeFloat(is.readFloat());
                break;
            }
            // Double
            case 6:
            {
                os.writeDouble(is.readDouble());
                break;
            }
            // Byte array
            case 7:
            {
                int length = is.readInt();
                byte[] data = new byte[length];
                is.readFully(data);
                os.writeInt(length);
                os.write(data);
                break;
            }
            // String
            case 8:
            {
                short length = is.readShort();
                byte[] stringBytes = new byte[length];
                is.readFully(stringBytes);
                os.writeShort(length);
                os.write(stringBytes);
                break;
            }
            // List
            case 9:
            {
                int listTagType = is.read();
                int listLength = is.readInt();
                os.writeByte(listTagType);
                os.writeInt(listLength);
                for(int i = 0; i < listLength; i++)
                {
                    writeTagRecursive(listTagType, null, is, os);
                }
                break;
            }
            // Compound
            case 10:
            {
                // Read tag types until one of them is an end tag
                int subTagType;
                while((subTagType = is.read()) > 0)
                {
                    os.write(subTagType);
                    short nameLength = is.readShort();
                    os.writeShort(nameLength);
                    
                    String subName = null;
                    if(nameLength > 0)
                    {
                        byte[] nameBytes = new byte[nameLength];
                        is.readFully(nameBytes);
                        os.write(nameBytes);
                        subName = new String(nameBytes, StandardCharsets.UTF_8);
                    }
                    
                    writeTagRecursive(subTagType, subName, is, os);
                }
                os.write(0);
                break;
            }
            // Int array
            case 11:
            {
                int length = is.readInt();
                os.writeInt(length);
                for(int i = 0; i < length; i++)
                {
                    os.writeInt(is.readInt());
                }
                break;
            }
            // Long array
            case 12:
            {
                int length = is.readInt();
                os.writeInt(length);
                for(int i = 0; i < length; i++)
                {
                    os.writeLong(is.readLong());
                }
            }
        }
    }
}
