/*
 * FlatFS -- flat file system
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2007 Mike Dahlin
 *
 */

import java.io.IOException;
import java.io.EOFException;

public class FlatFS {

    public static final int ASK_MAX_FILE = 2423;
    public static final int ASK_FREE_SPACE_BLOCKS = 29542;
    public static final int ASK_FREE_FILES = 29545;
    public static final int ASK_FILE_METADATA_SIZE = 3502;
    public static final int MAX_INODES = PTree.MAX_TREES;
    private PTree ptree;

    /*
     * In a general essence:
     * inumber == tnum
     */
    public FlatFS(boolean doFormat) throws IOException {
        ptree = new PTree(doFormat);
    }

    public int beginTrans() {

            return ptree.beginTrans(); //Use PTree's beginTrans to start read/writes to file.
 
    }

    public void commitTrans(int xid)
            throws IOException, IllegalArgumentException {
            ptree.commitTrans(xid); //Use PTree's commit to commit read/writes to file.
    }

    public void abortTrans(int xid)
            throws IOException, IllegalArgumentException {
            ptree.abortTrans(xid); //Use PTree's abort to abort read/writes to file.
    }

    public int createFile(int xid)
            throws IOException, IllegalArgumentException, ResourceException, IndexOutOfBoundsException, ClassNotFoundException {
        return ptree.createTree(xid); //Since a file is a tree, we can use PTree's createTree to make a file.
    }

    public void deleteFile(int xid, int inumber)
            throws IOException, IllegalArgumentException {
        ptree.deleteTree(xid, inumber); //Since a file is basically a tree, we can use PTree's deleteTree function.
    }

    public int read(int xid, int inumber, int offset, int count, byte buffer[]) 
            throws IOException, IllegalArgumentException, EOFException 
	{
		/*
		  Reads a range of bytes starting from offset to offset + count. EOFException if attempting to read from a block that hasn't been written or has ended.
		*/
        int totalRead = 0; //Absolute count of the bytes that have been read.
        int bytesRemain = count; //Countdown for the number of bytes read.
        int bufferIndex = 0;
        int blockLocation = offset / 1024;
        int blockWritten = ptree.treeHasWrittenBlock(xid, inumber, blockLocation);
        if (blockWritten == -1) //If the block hasn't been written at block located in offset, then we abort reading.
        {
            throw new EOFException();
        } else {
            boolean firstIter = true;
            while (bytesRemain > 0 && blockWritten != -1)
            {
                //Fetch block
                byte[] tempBuffer1 = new byte[Disk.SECTOR_SIZE];
                byte[] tempBuffer2 = new byte[Disk.SECTOR_SIZE];

                //Fill in block with data on disk
                ptree.adisk.readSector(xid, PTree.SIZE_OF_PTREE_METADATA + blockWritten * 2, tempBuffer1);
                ptree.adisk.readSector(xid, PTree.SIZE_OF_PTREE_METADATA + blockWritten * 2 + 1, tempBuffer2);

                int tempoffset;
                if (firstIter) {
                    if (offset % PTree.BLOCK_SIZE_BYTES < Disk.SECTOR_SIZE) //Starting at first half
                    {
                        tempoffset = offset % PTree.BLOCK_SIZE_BYTES;
                        for (; tempoffset < Disk.SECTOR_SIZE && bytesRemain > 0; tempoffset++) //Iterate through first half of block
                        {
                            buffer[bufferIndex++] = tempBuffer1[tempoffset]; //read a byte
                            totalRead++;
                            bytesRemain--;
                        }
                        if (bytesRemain == 0) //Count is complete.
                        {
                            return totalRead;
                        }
                        for (tempoffset = 0; tempoffset < Disk.SECTOR_SIZE && bytesRemain > 0; tempoffset++) //Iterate through second half.
                        {
                            buffer[bufferIndex++] = tempBuffer2[tempoffset];
                            totalRead++;
                            bytesRemain--;
                        }
                        if (bytesRemain == 0)
                        {
                            return totalRead;
                        }

                    } 
                    else //We will start at the beginning of the block.
                    {
                        tempoffset = offset % PTree.BLOCK_SIZE_BYTES;
                        for (; tempoffset < Disk.SECTOR_SIZE && bytesRemain > 0; tempoffset++) {
                            buffer[bufferIndex++] = tempBuffer2[tempoffset]; //read a byte
                            totalRead++;
                            bytesRemain--;
                        }
                    }
                    firstIter = false;
                } else {
                    //firstIter == false => begin at a start of block rather than start at offset.
                    if (bytesRemain == 0) {
                        return totalRead;
                    }
                    for (int i = 0; i < Disk.SECTOR_SIZE && bytesRemain > 0; i++) {
                        buffer[bufferIndex] = tempBuffer1[i];
                        totalRead++;
                        bytesRemain--;
                    }
                    if (bytesRemain == 0) {
                        return totalRead;
                    }
                    for (int i = 0; i < Disk.SECTOR_SIZE && bytesRemain > 0; i++) {
                        buffer[bufferIndex] = tempBuffer2[i];
                        totalRead++;
                        bytesRemain--;
                    }
                    if (bytesRemain == 0) {
                        return totalRead;
                    }
                }
                blockWritten = ptree.treeHasWrittenBlock(xid, inumber, ++blockLocation);
            }
        }
        return totalRead;
    }

    public void write(int xid, int inumber, int offset, int count, byte buffer[])
            throws IOException, IllegalArgumentException, IndexOutOfBoundsException, ClassNotFoundException {
        Transaction writeTrans = ptree.adisk.ActTransList.get(xid);
        if(count >= buffer.length)
        {
            count = buffer.length; //To prevent IndexOOB Errors.
        }
        int bytesRemain = count;
        int tempoffset;
        int bufferIndex = 0;
        int blockLocation = offset / PTree.BLOCK_SIZE_BYTES;
        int blockWritten = ptree.treeHasWrittenBlock(xid, inumber, blockLocation);
        if (blockWritten == -1) {
            if (!(ptree.growTree(writeTrans, xid, inumber, blockLocation, buffer)))
            {
                return;
            }
        }
        else
        {
            boolean firstIter = true;
            while(bytesRemain > 0) //Keep writing until we don't need to anymore.
            {
                //We get a block off from Transaction xid.
                byte[] tempBuffer1 = new byte[Disk.SECTOR_SIZE];
                byte[] tempBuffer2 = new byte[Disk.SECTOR_SIZE];

                //Read the block, in which we will overwrite the range of bytes with bytes in buffer (Whaa?)
                ptree.adisk.readSector(xid, PTree.SIZE_OF_PTREE_METADATA + blockWritten * 2, tempBuffer1);
                ptree.adisk.readSector(xid, PTree.SIZE_OF_PTREE_METADATA + blockWritten * 2 + 1, tempBuffer2);

                if(firstIter) //Starting at a offset, most likely
                {
                    tempoffset = offset%PTree.BLOCK_SIZE_BYTES; //Do an offset of the offset
                    if(tempoffset < 512)
                    {
                        for(;tempoffset < Disk.SECTOR_SIZE && bytesRemain > 0;tempoffset++)
                        {
                            tempBuffer1[tempoffset] = buffer[bufferIndex++];
                            bytesRemain--;
                        }
                        if(bytesRemain > 0)
                        {
                            ptree.adisk.writeSector(xid, PTree.SIZE_OF_PTREE_METADATA + blockWritten * 2, tempBuffer1);
                        }
                        else
                        {
                            for(;tempoffset < Disk.SECTOR_SIZE && bytesRemain > 0;tempoffset++)
                            {
                                tempBuffer2[tempoffset] = buffer[bufferIndex++];
                                bytesRemain--;
                            }
                            if(bytesRemain > 0)
                            {
                                ptree.adisk.writeSector(xid, PTree.SIZE_OF_PTREE_METADATA + blockWritten * 2, tempBuffer1);
                                ptree.adisk.writeSector(xid, PTree.SIZE_OF_PTREE_METADATA + blockWritten * 2 + 1, tempBuffer2);
                            }
                        }
                    }
                    else //Offset is at the 2nd half of block
                    {
                            for(;tempoffset < Disk.SECTOR_SIZE && bytesRemain > 0;tempoffset++) //write to second half of block
                            {
                                tempBuffer2[tempoffset] = buffer[bufferIndex++];
                                bytesRemain--;
                            }
                            if(bytesRemain > 0)
                            {
                                ptree.adisk.writeSector(xid, PTree.SIZE_OF_PTREE_METADATA + blockWritten * 2 + 1, tempBuffer2);
                            }
                    }
                }
                else
                {
                    for(int i = 0; i < Disk.SECTOR_SIZE && bytesRemain > 0; i++)
                    {
                        tempBuffer1[i] = buffer[bufferIndex++];
                        bytesRemain--;
                    }
                    if(bytesRemain == 0)
                    {
                        ptree.adisk.writeSector(xid, PTree.SIZE_OF_PTREE_METADATA + blockWritten * 2 + 1, tempBuffer2);
                    }
                    for(int i = 0; i < Disk.SECTOR_SIZE && bytesRemain > 0; i++)
                    {
                        tempBuffer2[i] = buffer[bufferIndex++];
                        bytesRemain--;
                    }
                    if(bytesRemain == 0)
                    {
                        ptree.adisk.writeSector(xid, PTree.SIZE_OF_PTREE_METADATA + blockWritten * 2, tempBuffer1);
                        ptree.adisk.writeSector(xid, PTree.SIZE_OF_PTREE_METADATA + blockWritten * 2 + 1, tempBuffer2);
                    }
                }
                blockWritten = ptree.treeHasWrittenBlock(xid, inumber, ++blockLocation);
                if(blockWritten == -1)
                {
                    if(!ptree.growTree(writeTrans, xid, inumber, blockLocation, buffer))
                    {
                        return;
                    }
                }
            }
        }
    }

    public void readFileMetadata(int xid, int inumber, byte buffer[])
            throws IOException, IllegalArgumentException {
        ptree.readTreeMetadata(xid, inumber, buffer);
    }

    public void writeFileMetadata(int xid, int inumber, byte buffer[])
            throws IOException, IllegalArgumentException {
        ptree.writeTreeMetadata(xid, inumber, buffer);
    }

    public int getParam(int param)
            throws IOException, IllegalArgumentException {
        switch (param) {
            case ASK_FILE_METADATA_SIZE:
                return PTree.METADATA_SIZE;
            case ASK_FREE_FILES:
                return ptree.getParam(PTree.ASK_FREE_TREES);
            case ASK_FREE_SPACE_BLOCKS:
                return ptree.getParam(PTree.ASK_FREE_SPACE);
            case ASK_MAX_FILE:
                return MAX_INODES;
            default:
                break;
        }
        return -1;
    }
}
