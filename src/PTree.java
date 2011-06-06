/*
 * PTree -- persistent tree
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2007 Mike Dahlin
 *
 */

import java.io.FileNotFoundException;
import java.io.IOException;
//import java.io.EOFException;
//import java.text.CharacterIterator;
//import java.text.StringCharacterIterator;
import java.util.Arrays;
import java.util.concurrent.locks.Condition;

public class PTree {

    public static final int METADATA_SIZE = 64;
    public static final int MAX_TREES = 512;
    public static final int MAX_BLOCK_ID = Integer.MAX_VALUE;
    //
    // Arguments to getParam
    //
    public static final int ASK_FREE_SPACE = 997;
    public static final int ASK_MAX_TREES = 13425;
    public static final int ASK_FREE_TREES = 23421;
    //
    // TNode structure
    //
    public static final int TNODE_DIRECT = 8;
    public static final int TNODE_INDIRECT = 1;
    public static final int TNODE_DOUBLE_INDIRECT = 1;
    public static final int BLOCK_SIZE_BYTES = 1024;
    public static final int POINTERS_PER_INTERNAL_NODE = 256;
    //added by us:
    //so, 4 tnodes per sector = 128 sectors for TNode Array
    public static final int SIZE_OF_TREE_NODE = 128;
    public static final int SIZE_OF_FREELIST = 2;
    public static final int SIZE_OF_TNODE_ARRAY = 128;
    // + 2 is so we never write to block 1
    public static final int SIZE_OF_PTREE_METADATA = SIZE_OF_FREELIST + SIZE_OF_TNODE_ARRAY;
    // 16384 - 1156 = 15228 available sectors... /2 = 7614
    // put the extra minus one because otherwise, odd number of sectors.
    public static final int TOTAL_AVAILABLE_BLOCKS = (Disk.NUM_OF_SECTORS - ADisk.REDO_LOG_SECTORS - 1 - SIZE_OF_PTREE_METADATA - 1) / 2;
    public static final byte[] zeroBuffer = new byte[BLOCK_SIZE_BYTES];
    public ADisk adisk;
    public byte[] freeList;
    public boolean freeListInMem;
    public boolean[] treeIDs; //Keep track of what treeIDs have been used.
    public byte[] currentTNodeSector;
    public int TNodeSectorInMem;
    private SimpleLock lock;
    private Condition outstandingTrans;

    public PTree(boolean doFormat) throws FileNotFoundException, IllegalArgumentException, IOException {
        adisk = new ADisk(doFormat);
        lock = new SimpleLock();
        outstandingTrans = lock.newCondition();
        freeList = new byte[BLOCK_SIZE_BYTES];
        freeListInMem = false;
        treeIDs = new boolean[MAX_TREES];
        currentTNodeSector = new byte[Disk.SECTOR_SIZE];
        TNodeSectorInMem = -1;
        /*
         * We are using the first 1155 sectors of the disk:
         * 1025 log + 2 freeList bitmap + 128 array Tnodes
         *
         * 1 block == 1 bit in the freeList
         *
         */
    }

    public int beginTrans()
    {
        try {
            lock.lock();
            while(adisk.ActTransList.ActiveTransactionList.size() > 0)
            {
                outstandingTrans.awaitUninterruptibly();
            }
            int tid = adisk.beginTransaction();
            return tid;
        } finally {
            lock.unlock();
        }
    }

    public void commitTrans(int xid)
            throws IOException, IllegalArgumentException {
        try {
            lock.lock();
            TNodeSectorInMem = -1;
            writeFreeListToDisk();
            freeListInMem = false;
            adisk.commitTransaction(xid);
            outstandingTrans.signalAll();
        } finally {
            lock.unlock();
        }

    }

    public void abortTrans(int xid)
            throws IOException, IllegalArgumentException {
        try {
            lock.lock();
            adisk.abortTransaction(xid);
            outstandingTrans.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public int createTree(int xid)
            throws IOException, IllegalArgumentException, ResourceException, IndexOutOfBoundsException, ClassNotFoundException {
        try {
            lock.lock();
            Transaction createTrans = adisk.ActTransList.get(xid);
            int treeNum = -1;
            for (int i = 0; i < treeIDs.length; i++) {
                if (!treeIDs[i]) {
                    treeIDs[i] = true;
                    treeNum = i;
                    break;
                }
            }

            if (treeNum == -1) {
                //If no more free ID's, then surpassing MAX_TREES
                // ?? NEED TO throw resource exception  ??
                throw new ResourceException();//("Cannot create new tree. Already at MAX_TREES number of trees.");
            }

            //write that Tree is in use on disk (1st 4 bytes of OUR metadata --> +64 into TNode).
            //(used for recovery)
            int treeInUse = 1; //IN USE
            byte[] inUse = ADisk.intToByteArray(treeInUse);

            // THINKING THIS NEEDS TO BE WITHIN A TRANSACTION TO BE ATOMIC

            //create temp buffer to load the TNode into
            byte[] tnodeBuffer = new byte[SIZE_OF_TREE_NODE];
            //create temp buffer to hold the sector the TNode is in
            byte[] tnodeSector = new byte[Disk.SECTOR_SIZE];
            //Now, read the TNode from disk into temp buffer
            //sector number = 1025(log+CP) + 2 (freeList) puts
            //you at the beginning of the TNode array. Then to get to specific TNode,
            //do... + (tnum/4). /4 because there are 4 tnums in a sector.
            //This gives us the sector that the tnum is in. Then we need to arraycopy
            //the actual tnum from there
            //First, check if the trans has written it yet

            adisk.readSector(xid, SIZE_OF_FREELIST + treeNum / 4, tnodeSector);
            System.arraycopy(tnodeSector, SIZE_OF_TREE_NODE * (treeNum % 4), tnodeBuffer, 0, SIZE_OF_TREE_NODE);

            //start at 64 b/c 64 bytes of other metadata
            System.arraycopy(inUse, 0, tnodeBuffer, 64, 4);
            System.arraycopy(tnodeBuffer, 0, tnodeSector, SIZE_OF_TREE_NODE * (treeNum % 4), SIZE_OF_TREE_NODE);

            //Now, copy buffer back into sector and write sector back to disk
            createTrans.addWrite(SIZE_OF_FREELIST + treeNum / 4, tnodeSector);
            return treeNum;
        } finally {
            lock.unlock();
        }

    }

    public void deleteTree(int xid, int tnum)
            throws IOException, IllegalArgumentException {
        try {
            lock.lock();
            byte[] zeroSector = new byte[Disk.SECTOR_SIZE];
            Transaction trans = adisk.ActTransList.get(xid);
            //walk Tnode buffer , go into DI block, zero all indirect blocks
            //next, go to indirect block, zero those
            //then zero whole sector and write it back
            byte[] tnodeSector = new byte[Disk.SECTOR_SIZE];
            adisk.readSector(xid, SIZE_OF_FREELIST + tnum / 4, tnodeSector);
            byte[] tnodeBuffer = new byte[SIZE_OF_TREE_NODE];
            System.arraycopy(tnodeSector, SIZE_OF_TREE_NODE * (tnum % 4), tnodeBuffer, 0, SIZE_OF_TREE_NODE);
            //first, get DI block
            byte[] doubIndBlockPtr = new byte[4];
            System.arraycopy(tnodeBuffer, 64 + 24 + 9 * 4, doubIndBlockPtr, 0, 4);
            int DIBlock = ADisk.byteArrayToInt(doubIndBlockPtr);
            byte[] doubIndirectDataBlock1 = new byte[Disk.SECTOR_SIZE];
            byte[] doubIndirectDataBlock2 = new byte[Disk.SECTOR_SIZE];
            if (DIBlock != 0) { //Go clear out All Indirect blocks
                //get DI block
                adisk.readSector(xid, SIZE_OF_PTREE_METADATA + DIBlock * 2, doubIndirectDataBlock1);
                adisk.readSector(xid, SIZE_OF_PTREE_METADATA + DIBlock * 2 + 1, doubIndirectDataBlock2);
                doubIndirectDataBlock1 = ADisk.concat(doubIndirectDataBlock1, doubIndirectDataBlock2);
                byte[] IDblockNumArr = new byte[4];
                int IDblockNum = -1;
                for (int i = 0; i < POINTERS_PER_INTERNAL_NODE; i++) {
                    //if ID block has pointer in it, need to load that ID block and clear it
                    //make sure to free the ID block and all data blocks
                    System.arraycopy(doubIndirectDataBlock1, i * 4, IDblockNumArr, 0, 4);
                    IDblockNum = ADisk.byteArrayToInt(IDblockNumArr);
                    if (IDblockNum != 0) {
                        clearIndBlock(xid, IDblockNum, zeroSector);
                    }
                }
                //zero out DI block
                setBlockFree(DIBlock);
                trans.addWrite(SIZE_OF_PTREE_METADATA + DIBlock * 2, zeroSector);
                trans.addWrite(SIZE_OF_PTREE_METADATA + DIBlock * 2 + 1, zeroSector);
            }
            //if DIBlock == 0, no DIblock data, so go to IndBlock
            byte[] IDBlock = new byte[4];
            System.arraycopy(tnodeBuffer, 64 + 24 + 8 * 4, IDBlock, 0, 4);
            int IDblockNum = ADisk.byteArrayToInt(IDBlock);
            if (IDblockNum != 0) { //then there is an Ind Block
                clearIndBlock(xid, IDblockNum, zeroSector);
            }
            //Now, delete data blocks!
            byte[] dataBlockArr = new byte[4];
            int dataBlockNum = -1;
            for (int i = 0; i < 8; i++) {
                System.arraycopy(tnodeBuffer, 64 + 24 + i * 4, dataBlockArr, 0, 4);
                dataBlockNum = ADisk.byteArrayToInt(dataBlockArr);
                if (dataBlockNum != 0) {
                    setBlockFree(dataBlockNum);
                }
            }
            Arrays.fill(tnodeBuffer, (byte) 0);
            //no Ind block, clear entire sector, write back to disk
            System.arraycopy(tnodeBuffer, 0, tnodeSector, SIZE_OF_TREE_NODE * (tnum % 4), SIZE_OF_TREE_NODE);
            trans.addWrite(SIZE_OF_FREELIST + tnum / 4, tnodeSector);
        } finally {
            lock.unlock();
        }
    }

    /*
     * clear ind block and free data blocks
     */
    public void clearIndBlock(int xid, int IDblockNum, byte[] zeroSector) throws IOException {
        try {
            lock.lock();
            Transaction trans = adisk.ActTransList.get(xid);
            // load IDblock into disk
            byte[] IDBlock1 = new byte[Disk.SECTOR_SIZE];
            byte[] IDBlock2 = new byte[Disk.SECTOR_SIZE];
            adisk.readSector(xid, SIZE_OF_PTREE_METADATA + IDblockNum * 2, IDBlock1);
            adisk.readSector(xid, SIZE_OF_PTREE_METADATA + IDblockNum * 2 + 1, IDBlock2);
            IDBlock1 = ADisk.concat(IDBlock1, IDBlock2);
            byte[] dataBlockNum = new byte[4];
            int dataBlock = -1;
            // set all data blocks as cleared
            for (int ii = 0; ii < POINTERS_PER_INTERNAL_NODE; ii++) {
                //if ID block has pointer in it, need to load that ID block and clear it
                //make sure to free the ID block and all data blocks
                System.arraycopy(IDBlock1, ii * 4, dataBlockNum, 0, 4);
                dataBlock = ADisk.byteArrayToInt(dataBlockNum);
                if (dataBlock != 0) {
                    setBlockFree(dataBlock);
                }
            }
            //Now, clear Ind block
            setBlockFree(IDblockNum);
            trans.addWrite(SIZE_OF_PTREE_METADATA + IDblockNum * 2, zeroSector);
            trans.addWrite(SIZE_OF_PTREE_METADATA + IDblockNum * 2 + 1, zeroSector);
        } finally {
            lock.unlock();
        }

    }

    public void readData(int xid, int tnum, int blockId, byte buffer[])
            throws IOException, IllegalArgumentException {
        try {
            lock.lock();
            //want to know if tnum wrote to blockID
            //need to iterate through all blocks owned by tnum and see if blockID is there
            //First, pull tnum off disk:

            int hasWrittenBlock = treeHasWrittenBlock(xid, tnum, blockId);
            //If hasn't written block, fill buffer with zeroes and return
            if (hasWrittenBlock == -1) {
                for (int i = 0; i < BLOCK_SIZE_BYTES; i++) {
                    buffer[i] = zeroBuffer[i];
                }
                return;
            }

            //If not -1, then wrote to it, so get write.
            //If it has, then we need to get value from there first in case
            //transaction hasn't commited yet
            byte[] tempBuffer1 = new byte[Disk.SECTOR_SIZE];
            byte[] tempBuffer2 = new byte[Disk.SECTOR_SIZE];
            adisk.readSector(xid, SIZE_OF_PTREE_METADATA + hasWrittenBlock * 2, tempBuffer1);
            adisk.readSector(xid, SIZE_OF_PTREE_METADATA + hasWrittenBlock * 2 + 1, tempBuffer2);
            tempBuffer1 = ADisk.concat(tempBuffer1, tempBuffer2);

            for (int i = 0; i < BLOCK_SIZE_BYTES; i++) {
                buffer[i] = tempBuffer1[i];
            }

            return;
        } finally {
            lock.unlock();
        }
    }

    public void writeData(int xid, int tnum, int blockId, byte buffer[])
            throws IOException, IllegalArgumentException {
        try {
            lock.lock();
            /*
             * Search again for blockID in tnum (see readSector)
             * If not there, grow tree (first check data blocks, then indir, then
             * double indir. Put it in first available place)
             * growth --> update freeList, internal tree nodes (TNODE arrayList),
             * writing to actual data block..duh)
             * then...
             * all of the above updates (minus some if not growing) done by xid
             * transaction....atomically. So we do a bunch of transaction_xid,addWrites()
             *
             */

            //create transaction from xid
            Transaction writeTrans = adisk.ActTransList.get(xid);

            boolean status = false;
            int treeWroteBlock = treeHasWrittenBlock(xid, tnum, blockId);
            if (treeWroteBlock == -1)
            {
                //tree hasn't written to block yet
                status = growTree(writeTrans, xid, tnum, blockId, buffer);
                if (!status)
                {
                    //PROBLEM WITH GROWING TREE...NOT ENOUGH ROOM!!
                }
            } 
            else
            {
                // If get here, then tree should be grown to support the block
                // Perform the write already!
                byte[] tempBuffer = new byte[BLOCK_SIZE_BYTES];
                for (int i = 0; i < BLOCK_SIZE_BYTES; i++) {
                    tempBuffer[i] = buffer[i];
                }
                byte[] tempBuffer1 = new byte[Disk.SECTOR_SIZE];
                byte[] tempBuffer2 = new byte[Disk.SECTOR_SIZE];
                //fill buffers to write with data to be written
                System.arraycopy(tempBuffer, 0, tempBuffer1, 0, Disk.SECTOR_SIZE);
                System.arraycopy(tempBuffer, Disk.SECTOR_SIZE, tempBuffer2, 0, Disk.SECTOR_SIZE);
                writeTrans.addWrite(SIZE_OF_PTREE_METADATA + treeWroteBlock * 2, tempBuffer1);
                writeTrans.addWrite(SIZE_OF_PTREE_METADATA + treeWroteBlock * 2 + 1, tempBuffer2);
            }
            return;
        } finally {
            lock.unlock();
        }

    }

    public void readTreeMetadata(int xid, int tnum, byte buffer[])
            throws IOException, IllegalArgumentException {

        try {
            /*
             * read sector base + tnum/4
             * arraycopy TNODE minisector (128 bytes) at tnum%4
             * arraycopy first 64 bytes of specified tnode
             * put in buffer.
             */
            //create temp buffer to load the TNode into
            byte[] tnodeBuffer = new byte[SIZE_OF_TREE_NODE];

            //create temp buffer to hold the sector the TNode is in
            byte[] tnodeSector = new byte[Disk.SECTOR_SIZE];

            if (TNodeSectorInMem != tnum / 4) {
                adisk.readSector(xid, SIZE_OF_FREELIST + tnum / 4, tnodeSector);
                //was changed by trans, so got it from trans
                System.arraycopy(tnodeSector, SIZE_OF_TREE_NODE * (tnum % 4), tnodeBuffer, 0, SIZE_OF_TREE_NODE);
                System.arraycopy(tnodeSector, 0, currentTNodeSector, 0, Disk.SECTOR_SIZE);
                TNodeSectorInMem = tnum / 4;
            } else {
                //if it is in memory, use in-mem vals
                System.arraycopy(currentTNodeSector, 0, tnodeSector, 0, Disk.SECTOR_SIZE);
                System.arraycopy(tnodeSector, SIZE_OF_TREE_NODE * (tnum % 4), tnodeBuffer, 0, SIZE_OF_TREE_NODE);
            }
            System.arraycopy(tnodeBuffer, 0, buffer, 0, METADATA_SIZE);
        } finally {
            lock.unlock();
        }
    }

    public void writeTreeMetadata(int xid, int tnum, byte buffer[])
            throws IOException, IllegalArgumentException {
        /*
         * pull whole TNODE off disk. arraycopy the new metadata in.
         * arraycopy the TNODE back into the sector
         * write sector TNODE was in back to disk
         */
        try {
            lock.lock();
            Transaction trans = adisk.ActTransList.get(xid);

            //create temp buffer to load the TNode into
            byte[] tnodeBuffer = new byte[SIZE_OF_TREE_NODE];

            //create temp buffer to hold the sector the TNode is in
            byte[] tnodeSector = new byte[Disk.SECTOR_SIZE];

            if (TNodeSectorInMem != tnum / 4) {
                adisk.readSector(xid, SIZE_OF_FREELIST + tnum / 4, tnodeSector);
                //was changed by trans, so got it from trans
                System.arraycopy(tnodeSector, SIZE_OF_TREE_NODE * (tnum % 4), tnodeBuffer, 0, SIZE_OF_TREE_NODE);
                System.arraycopy(tnodeSector, 0, currentTNodeSector, 0, Disk.SECTOR_SIZE);
                TNodeSectorInMem = tnum / 4;
            } else {
                //if it is in memory, use in-mem vals
                System.arraycopy(currentTNodeSector, 0, tnodeSector, 0, Disk.SECTOR_SIZE);
                System.arraycopy(tnodeSector, SIZE_OF_TREE_NODE * (tnum % 4), tnodeBuffer, 0, SIZE_OF_TREE_NODE);

            }
            //copy buffer with metadata into TNodeBuffer
            System.arraycopy(buffer, 0, tnodeBuffer, 0, METADATA_SIZE);
            //copy tnodebuffer into tnodeSector
            System.arraycopy(tnodeBuffer, 0, tnodeSector, SIZE_OF_TREE_NODE * (tnum % 4), METADATA_SIZE);
            //write tnodeSector back to disk
            trans.addWrite(SIZE_OF_FREELIST + tnum / 4, buffer);
            //update in mem state of tnodeSector
            System.arraycopy(tnodeSector, 0, currentTNodeSector, 0, Disk.SECTOR_SIZE);
        } finally {
            lock.unlock();
        }
    }

    public int getParam(int param)
            throws IOException, IllegalArgumentException {
        try {
            lock.lock();
            if (param == ASK_FREE_SPACE) {
                //free blocks * bytes per block
                return numFreeBlocks() * BLOCK_SIZE_BYTES;
            } else if (param == ASK_FREE_TREES) {
                //check the TNum list
                int count = 0;
                for (int i = 0; i < treeIDs.length; i++) {
                    if (!treeIDs[i]) {
                        count++;
                    }
                }
                return count;

            } else if (param == ASK_MAX_TREES) {
                return MAX_TREES;
            } else {
                throw new IllegalArgumentException("Illegal Argument Exception.");
            }
        } finally {
            lock.unlock();
        }
    }

    public int treeHasWrittenBlock(int xid, int tnum, int blockId) throws IOException {
        /*
         * return -1 if hasn't written blockId
         */
        try {
            lock.lock();
            Transaction trans = adisk.ActTransList.get(xid);

            //create temp buffer to load the TNode into
            byte[] tnodeBuffer = new byte[SIZE_OF_TREE_NODE];

            //create temp buffer to hold the sector the TNode is in
            byte[] tnodeSector = new byte[Disk.SECTOR_SIZE];

            //Now, read the TNode from disk into temp buffer
            //sector number = 1025(log+CP) + 2 (freeList) puts
            //you at the beginning of the TNode array. Then to get to specific TNode,
            //do... + (tnum/4). /4 because there are 4 tnums in a sector.
            //This gives us the sector that the tnum is in. Then we need to arraycopy
            //the acutal tnum from there
            //If not in mem, get from disk. (first read)
            if (TNodeSectorInMem != tnum / 4) {
                adisk.readSector(xid, SIZE_OF_FREELIST + tnum / 4, tnodeSector);
                //was changed by trans, so got it from trans
                System.arraycopy(tnodeSector, SIZE_OF_TREE_NODE * (tnum % 4), tnodeBuffer, 0, SIZE_OF_TREE_NODE);
                System.arraycopy(tnodeSector, 0, currentTNodeSector, 0, Disk.SECTOR_SIZE);
                TNodeSectorInMem = tnum / 4;
            } else {
                //if it is in memory, use in-mem vals
                System.arraycopy(currentTNodeSector, 0, tnodeSector, 0, Disk.SECTOR_SIZE);
                System.arraycopy(tnodeSector, SIZE_OF_TREE_NODE * (tnum % 4), tnodeBuffer, 0, SIZE_OF_TREE_NODE);
            }

            //case where block looking for is a data block
            if (blockId < 8) {
                /*
                 * how do we deal with if we haven't written to blockId? Does that mean
                 * that the block where the number is is null, or 0? But what if
                 * wrote to block 0 in disk?? MAP SO ONLY USE 1 --> MAX FREE BLOCKS
                 * so that zero is always null
                 */

                //read block number the data block is writing to
                //+1 bc we have one sector of TNode metadata (24 bytes for us, 64 for whoever)
                //so 0th block starts at startOfTnodeOnDisk+1
                byte[] blockNum = new byte[4];
                System.arraycopy(tnodeBuffer, 64 + 24 + 4 * blockId, blockNum, 0, 4);

                int dataBlockOnDisk = ADisk.byteArrayToInt(blockNum);

                //we need to check if dataBlockOnDisk has been written to by tree
                //because of how we're mapping, if dataBlockOnDisk == 0, then
                //we haven't written to it. So return zero buffer.
                if (dataBlockOnDisk == 0) {
                    return -1;
                }
                return dataBlockOnDisk;
            } //case where its a block pointed to by the indirect block
            else if (blockId >= 8 && blockId < 256 + 8) {
                /*
                 * pull the block indirect block points to out of tnodeSector buffer
                 */
                byte[] blockNum = new byte[4];
                // +8 because that's where indirect ptr is
                System.arraycopy(tnodeBuffer, 64 + 24 + 4 * 8, blockNum, 0, 4);
                //this is the indirect block on disk.
                int dataBlockOnDisk = ADisk.byteArrayToInt(blockNum);
                if (dataBlockOnDisk == 0) {
                    return -1;
                }

                //read the indirect block from disk (2 sectors)
                byte[] indirectDataBlock1 = new byte[Disk.SECTOR_SIZE];
                byte[] indirectDataBlock2 = new byte[Disk.SECTOR_SIZE];
                adisk.readSector(xid, SIZE_OF_PTREE_METADATA + dataBlockOnDisk * 2, indirectDataBlock1);
                adisk.readSector(xid, SIZE_OF_PTREE_METADATA + dataBlockOnDisk * 2 + 1, indirectDataBlock2);
                indirectDataBlock1 = ADisk.concat(indirectDataBlock1, indirectDataBlock2);

                //Now, read the specific data block from indirect data block
                byte[] dataBlockFromIndirect = new byte[4];
                //blockId - 8 b/c passed data blocks and in indiret block
                // *4 because each blockId is an int = 4 bytes
                System.arraycopy(indirectDataBlock1, 4 * (blockId - 8), dataBlockFromIndirect, 0, 4);
                //Now, we have the data block number to read from.
                int dataBlockNum = ADisk.byteArrayToInt(dataBlockFromIndirect);

                if (dataBlockNum == 0) {
                    return -1;
                }

                return dataBlockNum;

            } //case where pointed to by doubly indirect block
            else if (blockId >= 256 + 8 && blockId < 8 + 256 + 256 * 256) {
                byte[] doubIndBlockPtr = new byte[4];
                // +9 because that's where doubly indirect ptr is
                System.arraycopy(tnodeBuffer, 64 + 24 + 9 * 4, doubIndBlockPtr, 0, 4);

                //this is the block number of the doubly indirect block on disk.
                int doubIndBlockOnDisk = ADisk.byteArrayToInt(doubIndBlockPtr);
                if (doubIndBlockOnDisk == 0) {
                    return -1;
                }

                byte[] doubIndirectDataBlock1 = new byte[Disk.SECTOR_SIZE];
                byte[] doubIndirectDataBlock2 = new byte[Disk.SECTOR_SIZE];
                //load the doubly indirect block from disk (2 sectors)
                //check if transaction has written the block first
                adisk.readSector(xid, SIZE_OF_PTREE_METADATA + doubIndBlockOnDisk * 2, doubIndirectDataBlock1);
                adisk.readSector(xid, SIZE_OF_PTREE_METADATA + doubIndBlockOnDisk * 2 + 1, doubIndirectDataBlock2);
                doubIndirectDataBlock1 = ADisk.concat(doubIndirectDataBlock1, doubIndirectDataBlock2);

                //Read the correct indirect block from it:
                //Within doubly indirect = 256 indirect ptrs.
                //So the indir block you're looking for is blockID-(256+8)/256
                byte[] indirBlockNum = new byte[4];
                System.arraycopy(doubIndirectDataBlock1, 4 * ((blockId - (256 + 8)) / 256), indirBlockNum, 0, 4);
                int indirectBlockNumber = ADisk.byteArrayToInt(indirBlockNum);
                if (indirectBlockNumber == 0) {
                    return -1;
                }

                //Now, get actual indirect block
                //read the indirect block from disk (2 sectors)
                byte[] indirectDataBlock1 = new byte[Disk.SECTOR_SIZE];
                byte[] indirectDataBlock2 = new byte[Disk.SECTOR_SIZE];
                adisk.readSector(xid, SIZE_OF_PTREE_METADATA + indirectBlockNumber * 2, indirectDataBlock1);
                adisk.readSector(xid, SIZE_OF_PTREE_METADATA + indirectBlockNumber * 2 + 1, indirectDataBlock2);
                indirectDataBlock1 = ADisk.concat(indirectDataBlock1, indirectDataBlock2);

                //Now, read the specific data block from indirect data block
                byte[] dataBlockFromIndirect = new byte[4];
                //blockId - 8 b/c passed data blocks and in indiret block
                // *4 because each blockId is an int = 4 bytes
                System.arraycopy(indirectDataBlock1, 4 * ((blockId - (256 + 8)) % 256), dataBlockFromIndirect, 0, 4);
                //Now, we have the data block number to read from.
                int dataBlockNum = ADisk.byteArrayToInt(dataBlockFromIndirect);
                if (dataBlockNum == 0) {
                    return -1;
                }
                return dataBlockNum;

            }

            //shouldn't ever get here, but just in case, return -1
            return -1;
        } finally {
            lock.unlock();
        }
    }

    /*
     * Grows a tree to write a newly written block
     * returns true if growth successful, false if not enough room
     */
    public boolean growTree(Transaction trans, int xid, int tnum, int blockId, byte[] buff) throws IllegalArgumentException, IOException {
        try {
            /*
             * To grow tree, you need to find a free block,
             */
            lock.lock();
            //load freeList from disk:
            if (!freeListInMem) {
                loadFreeListFromDisk();
                freeListInMem = true;
            }

            //load tree from disk
            //create temp buffer to load the TNode into
            byte[] tnodeBuffer = new byte[SIZE_OF_TREE_NODE];
            //create temp buffer to hold the sector the TNode is in
            byte[] tnodeSector = new byte[Disk.SECTOR_SIZE];
            if (TNodeSectorInMem != tnum / 4) {
                adisk.readSector(xid, SIZE_OF_FREELIST + tnum / 4, tnodeSector);
                //was changed by trans, so got it from trans
                System.arraycopy(tnodeSector, SIZE_OF_TREE_NODE * (tnum % 4), tnodeBuffer, 0, SIZE_OF_TREE_NODE);
                System.arraycopy(tnodeSector, 0, currentTNodeSector, 0, Disk.SECTOR_SIZE);
                TNodeSectorInMem = tnum / 4;
            } else {
                //if it is in memory, use in-mem vals
                System.arraycopy(currentTNodeSector, 0, tnodeSector, 0, Disk.SECTOR_SIZE);
                System.arraycopy(tnodeSector, SIZE_OF_TREE_NODE * (tnum % 4), tnodeBuffer, 0, SIZE_OF_TREE_NODE);

            }

            // check to see if room on freeList for growing,
            // if not.... return false
            //If data block...
            if (blockId < 8) {
                int blockToWrite = getFreeBlock();
                if (blockToWrite == -1) {
                    //no more room
                    return false;
                }

                byte[] blockToWriteByteArray = ADisk.intToByteArray(blockToWrite);
                //write the data block the blockId will write to to the tree
                System.arraycopy(blockToWriteByteArray, 0, tnodeBuffer, 64 + 24 + 4 * blockId, 4);
                setMaxBlockID(tnodeBuffer, blockId);
                //re-write transaction back into the sector to re-write the sector to disk
                System.arraycopy(tnodeBuffer, 0, tnodeSector, SIZE_OF_TREE_NODE * (tnum % 4), SIZE_OF_TREE_NODE);
                //copy back into mem
                System.arraycopy(tnodeSector, 0, currentTNodeSector, 0, Disk.SECTOR_SIZE);
                //add write so it makes it to disk
                trans.addWrite(SIZE_OF_FREELIST + tnum / 4, tnodeSector);

                //Now, write val in buffer to blockToWrite on disk.
                byte[] buff1 = new byte[Disk.SECTOR_SIZE];
                byte[] buff2 = new byte[Disk.SECTOR_SIZE];
                System.arraycopy(buff, 0, buff1, 0, Disk.SECTOR_SIZE);
                System.arraycopy(buff, Disk.SECTOR_SIZE, buff2, 0, Disk.SECTOR_SIZE);
                trans.addWrite(SIZE_OF_PTREE_METADATA + blockToWrite * 2, buff1);
                trans.addWrite(SIZE_OF_PTREE_METADATA + blockToWrite * 2 + 1, buff2);

                return true;
            } //if indirect block....
            else if (blockId < 256 + 8) {
                //First, check if the indirect block is there:
                byte[] possibleIndirectBlock = new byte[4];
                System.arraycopy(tnodeBuffer, 24 + 64 + 4 * 8, possibleIndirectBlock, 0, 4);
                int indirectBlockPtr = ADisk.byteArrayToInt(possibleIndirectBlock);

                int dataBlockToWrite = getFreeBlock();
                if (dataBlockToWrite == -1) {
                    //no more room
                    return false;
                }
                byte[] dataBlock = ADisk.intToByteArray(dataBlockToWrite);

                // set this now. Only changes if no indirect block yet
                int indBlockToWrite = indirectBlockPtr;

                //if == 0, then don't have indirect block, if != 0, have indirect
                //block, just not data block
                if (indirectBlockPtr == 0) { //create an indirect block

                    //If get here, have room.
                    indBlockToWrite = getFreeBlock();
                    if (indBlockToWrite == -1) {
                        //no more room
                        return false;
                    }
                    byte[] indBlock = ADisk.intToByteArray(indBlockToWrite);

                    //write indBlockToWrite to indirect data block
                    //fill possibleIndirectBlock with indBlock, write it to disk
                    for (int i = 0; i < 4; i++) {
                        possibleIndirectBlock[i] = indBlock[i];
                    }
                    createIndBlock(possibleIndirectBlock, tnodeBuffer, tnodeSector, tnum, trans);
                }
                writeIndirectAndDataBlocks(indBlockToWrite, dataBlock, dataBlockToWrite, blockId, trans, buff);
                //set the new max block ID
                setMaxBlockID(tnodeBuffer, blockId);
                System.arraycopy(tnodeBuffer, 0, tnodeSector, SIZE_OF_TREE_NODE * (tnum % 4), SIZE_OF_TREE_NODE);
                System.arraycopy(tnodeSector, 0, currentTNodeSector, 0, Disk.SECTOR_SIZE);
                trans.addWrite(SIZE_OF_FREELIST + tnum / 4, tnodeSector);
                return true;
            } else if (blockId < 256 + 8 + 256 * 256) {
                //read space where DI block should be from tnode buffer
                byte[] DIblockValArr = new byte[4];
                System.arraycopy(tnodeBuffer, 24 + 64 + 4 * 9, DIblockValArr, 0, 4);
                int DIblockVal = ADisk.byteArrayToInt(DIblockValArr);

                // if == 0, don't have a DI block! if != 0, have DI block!
                if (DIblockVal == 0) { //don't have a DI block, check if have space for one and create it

                    int doubleIndBlockToWrite = getFreeBlock();
                    if (doubleIndBlockToWrite == -1) {
                        //no more room
                        return false;
                    }
                    byte[] DIblockToWrite = ADisk.intToByteArray(doubleIndBlockToWrite);

                    //already did this above...
                    //fill DIblockval so can use it later
                    for (int i = 0; i < 4; i++) {
                        DIblockValArr[i] = DIblockToWrite[i];
                    }

                    //now, we need to change the tnodeBuffer, tnodeSector, and
                    //write sector to disk
                    //change tnode, then change sector tnode is in
                    System.arraycopy(DIblockValArr, 0, tnodeBuffer, 24 + 64 + 4 * 9, 4);
                    System.arraycopy(tnodeBuffer, 0, tnodeSector, SIZE_OF_TREE_NODE * (tnum % 4), SIZE_OF_TREE_NODE);
                    //write sector back to disk
                    System.arraycopy(tnodeSector, 0, currentTNodeSector, 0, Disk.SECTOR_SIZE);
                    trans.addWrite(SIZE_OF_FREELIST + tnum / 4, tnodeSector);
                }
                //In case it changed in the if statement.
                DIblockVal = ADisk.byteArrayToInt(DIblockValArr);

                //Do you already have the appropriate Indirect block?
                //check DI block
                byte DIBlockBuffer1[] = new byte[Disk.SECTOR_SIZE];
                byte DIBlockBuffer2[] = new byte[Disk.SECTOR_SIZE];
                adisk.readSector(xid, SIZE_OF_PTREE_METADATA + DIblockVal * 2, DIBlockBuffer1);
                adisk.readSector(xid, SIZE_OF_PTREE_METADATA + DIblockVal * 2 + 1, DIBlockBuffer2);
                byte[] DIBlockBuffer = new byte[BLOCK_SIZE_BYTES];
                DIBlockBuffer = ADisk.concat(DIBlockBuffer1, DIBlockBuffer2);

                //Now read where the indirect block should be
                byte shouldBeIDBlockNum[] = new byte[4];
                System.arraycopy(DIBlockBuffer, 4 * ((blockId - (256 + 8)) / 256), shouldBeIDBlockNum, 0, 4);
                int possIBlock = ADisk.byteArrayToInt(shouldBeIDBlockNum);
                int indBlockToWrite = possIBlock;
                //if != 0, have ind block, just need to write data
                //if == 0, need to allocate ind block
                if (possIBlock == 0) {
                    indBlockToWrite = getFreeBlock();
                    byte[] indBlockNum = ADisk.intToByteArray(indBlockToWrite);
                    for (int i = 0; i < 4; i++) {
                        shouldBeIDBlockNum[i] = indBlockNum[i];
                    }
                    //DIBlock now holds the doubly indirect block. Need to write the indirect block to it
                    //in the correct location
                    System.arraycopy(shouldBeIDBlockNum, 0, DIBlockBuffer, 4 * ((blockId - (256 + 8)) / 256), 4);


                    //write DIBlock back to Disk. split it up again first
                    DIBlockBuffer1 = new byte[Disk.SECTOR_SIZE];
                    DIBlockBuffer2 = new byte[Disk.SECTOR_SIZE];
                    System.arraycopy(DIBlockBuffer, 0, DIBlockBuffer1, 0, Disk.SECTOR_SIZE);
                    System.arraycopy(DIBlockBuffer, Disk.SECTOR_SIZE, DIBlockBuffer2, 0, Disk.SECTOR_SIZE);
                    trans.addWrite(SIZE_OF_PTREE_METADATA + DIblockVal * 2, DIBlockBuffer1);
                    trans.addWrite(SIZE_OF_PTREE_METADATA + DIblockVal * 2 + 1, DIBlockBuffer2);
                }

                //Taken care of DI block, now just have to deal with indirect block and down.
                //Don't need to create indirect block!!!
                //data block on disk
                //Do you have room for a data block if necessary

                int dataBlockToWrite = getFreeBlock();
                if (dataBlockToWrite == -1) {
                    //no more room
                    return false;
                }
                byte[] dataBlock = ADisk.intToByteArray(dataBlockToWrite);

                writeIndirectAndDataBlocks(indBlockToWrite, dataBlock, dataBlockToWrite, ((blockId - 256 - 8) % 256) + 8, trans, buff);

                //set the new max block ID
                setMaxBlockID(tnodeBuffer, blockId);
                System.arraycopy(tnodeBuffer, 0, tnodeSector, SIZE_OF_TREE_NODE * (tnum % 4), SIZE_OF_TREE_NODE);
                System.arraycopy(tnodeSector, 0, currentTNodeSector, 0, Disk.SECTOR_SIZE);
                trans.addWrite(SIZE_OF_FREELIST + tnum / 4, tnodeSector);
                return true;
            }
            //If got here, then INVALID BLOCKID NUMBER!!
            return false;
        } finally {
            lock.unlock();
        }
    }
    /*
     * Fills in indirect pointer spot in a tnode array and writes the tnode back to disk
     */

    public void createIndBlock(byte[] indirectBlock, byte[] tnodeBuffer, byte[] tnodeSector, int tnum, Transaction trans) throws IllegalArgumentException, IOException {
        try {
            lock.lock();
            //change tnode, then change sector tnode is in
            System.arraycopy(indirectBlock, 0, tnodeBuffer, 24 + 64 + 4 * 8, 4);
            System.arraycopy(tnodeBuffer, 0, tnodeSector, SIZE_OF_TREE_NODE * (tnum % 4), SIZE_OF_TREE_NODE);
            //write sector back to disk
            System.arraycopy(tnodeSector, 0, currentTNodeSector, 0, Disk.SECTOR_SIZE);
            trans.addWrite(SIZE_OF_FREELIST + tnum / 4, tnodeSector);
        } finally {
            lock.unlock();
        }
    }

    /*
     *
     */
    public void writeIndirectAndDataBlocks(int indBlockToWrite, byte[] dataBlock, int dataBlockToWrite, int blockId, Transaction trans, byte[] buff) throws IllegalArgumentException, IOException {

        try {
            lock.lock();
            //if get here, already had indirect ptr.
            //update indirect block on disk with new data block
            //write data to new data block on disk


            //Now, write indBlockToWrite sector in disk with dataBlockToWrite number
            byte[] buff1 = new byte[Disk.SECTOR_SIZE];
            byte[] buff2 = new byte[Disk.SECTOR_SIZE];
            adisk.readSector(trans.getTransID(), PTree.SIZE_OF_PTREE_METADATA + indBlockToWrite * 2, buff1);
            adisk.readSector(trans.getTransID(), PTree.SIZE_OF_PTREE_METADATA + indBlockToWrite * 2 + 1, buff2);
            byte[] blockBuff = new byte[BLOCK_SIZE_BYTES];
            blockBuff = ADisk.concat(buff1, buff2);

            //write dataBlockToWrite number into the appropriate place:
            System.arraycopy(dataBlock, 0, blockBuff, 4 * (blockId - 8), 4);

            //split the block into 2 sectors again
            buff1 = new byte[Disk.SECTOR_SIZE];
            buff2 = new byte[Disk.SECTOR_SIZE];
            System.arraycopy(blockBuff, 0, buff1, 0, Disk.SECTOR_SIZE);
            System.arraycopy(blockBuff, Disk.SECTOR_SIZE, buff2, 0, Disk.SECTOR_SIZE);

            //add writes to transaction (putting data block pointer into indirect block
            trans.addWrite(SIZE_OF_PTREE_METADATA + indBlockToWrite * 2, buff1);
            trans.addWrite(SIZE_OF_PTREE_METADATA + indBlockToWrite * 2 + 1, buff2);

            //Write data to dataBlockToWrite block
            buff1 = new byte[Disk.SECTOR_SIZE];
            buff2 = new byte[Disk.SECTOR_SIZE];
            System.arraycopy(buff, 0, buff1, 0, Disk.SECTOR_SIZE);
            System.arraycopy(buff, Disk.SECTOR_SIZE, buff2, 0, Disk.SECTOR_SIZE);
            //buff1 and buff1 now filled

            //add writes to transaction
            trans.addWrite(SIZE_OF_PTREE_METADATA + dataBlockToWrite * 2, buff1);
            trans.addWrite(SIZE_OF_PTREE_METADATA + dataBlockToWrite * 2 + 1, buff2);
        } finally {
            lock.unlock();
        }
    }

    public void loadFreeListFromDisk() throws IllegalArgumentException, IOException {
        try {
            //load freeList from disk:
            lock.lock();
            byte[] freeList1 = new byte[Disk.SECTOR_SIZE];
            byte[] freeList2 = new byte[Disk.SECTOR_SIZE];
            adisk.AD.startRequest(Disk.READ, adisk.getTag(), ADisk.REDO_LOG_SECTORS + 1, freeList1);
            adisk.callback.waitForTag(adisk.setTag());
            adisk.AD.startRequest(Disk.READ, adisk.getTag(), ADisk.REDO_LOG_SECTORS + 2, freeList2);
            adisk.callback.waitForTag(adisk.setTag());
            System.arraycopy(freeList1, 0, freeList, 0, Disk.SECTOR_SIZE);
            System.arraycopy(freeList2, 0, freeList, Disk.SECTOR_SIZE, Disk.SECTOR_SIZE);
            freeListInMem = true;
            return;
        } finally {
            lock.unlock();
        }
    }

    public void writeFreeListToDisk() throws IllegalArgumentException, IOException {
        try {
            lock.lock();
            byte[] freeList1 = new byte[Disk.SECTOR_SIZE];
            byte[] freeList2 = new byte[Disk.SECTOR_SIZE];
            System.arraycopy(freeList, 0, freeList1, 0, Disk.SECTOR_SIZE);
            System.arraycopy(freeList, Disk.SECTOR_SIZE, freeList2, 0, Disk.SECTOR_SIZE);
            adisk.AD.startRequest(Disk.WRITE, adisk.getTag(), ADisk.REDO_LOG_SECTORS + 1, freeList1);
            adisk.callback.waitForTag(adisk.setTag());
            adisk.AD.startRequest(Disk.WRITE, adisk.getTag(), ADisk.REDO_LOG_SECTORS + 1 + 1, freeList2);
            adisk.callback.waitForTag(adisk.setTag());
            return;
        } finally {
            lock.unlock();
        }
    }

    public int numFreeBlocks() throws IllegalArgumentException, IOException {
        try {
            lock.lock();
            int count = 0;
            //divided by 8, because that's how many bytes need to express them
            for (int i = 1; i <= TOTAL_AVAILABLE_BLOCKS; i++) {
                if (isBlockFree(i)) {
                    count++;
                }
            }
            return count;
        } finally {
            lock.unlock();
        }
    }

    public int getFreeBlock() throws IllegalArgumentException, IOException {
        try {
            lock.lock();
            int blockToRet = -1;
            //divided by 8, because that's how many bytes need to express them
            for (int i = 1; i < TOTAL_AVAILABLE_BLOCKS / 8; i++) {
                if (isBlockFree(i)) {
                    setBlockInUse(i);
                    return i;
                }
            }
            return blockToRet;
        } finally {
            lock.unlock();
        }
    }

    public boolean isBlockFree(int blockNum) throws IllegalArgumentException, IOException {
        try {
            lock.lock();
            if (!freeListInMem) {
                loadFreeListFromDisk();
            }
            byte FLblock = freeList[blockNum / 8];
            int shiftVal = blockNum % 8;
            FLblock = (byte) ((byte) (FLblock << shiftVal) & 0x80);
            // -128 == 0x80
            if (FLblock == -128) {
                return false;
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    /*
     * Sets a block in freeList to in use
     */
    public void setBlockInUse(int blockNum) {
        /*
         * take block (n) that you're looking for, divide by 8. Look
         * in that index of freeList. n%8 will give you which bit to look at.
         */

        try {
            lock.lock();
            byte FLblock = freeList[blockNum / 8];
            int shiftVal = 7 - (blockNum % 8);
            byte shift = (byte) ((byte) 0x01 << shiftVal);
            FLblock = (byte) (0xFF & (FLblock | shift));
            freeList[blockNum / 8] = FLblock;
        } finally {
            lock.unlock();
        }
    }

    /*
     * Sets a block in freeList to free
     */
    public void setBlockFree(int blockNum) {
        /*
         * take block (n) that you're looking for, divide by 8. Look
         * in that index of freeList. n%8 will give you which bit to look at.
         */

        try {
            lock.lock();
            byte FLblock = freeList[blockNum / 8];
            int shiftVal = 7 - (blockNum % 8);
            byte shift = (byte) ((byte) 0x01 << shiftVal);
            if (shift == -128) {
                freeList[blockNum / 8] = (byte) 0x00;
                return;
            }
            FLblock = (byte) (0xFF & (FLblock ^ shift));
            freeList[blockNum / 8] = FLblock;
        } finally
        {
            lock.unlock();
        }
    }

    /*
     * checks the max blockID a tree has written, then changed it if necessary
     */
    public void setMaxBlockID(byte[] tnodeBuffer, int blockId)
    {
        try {
            lock.lock();
            // check/set maxBlockID
            byte[] maxBlockID = new byte[4];
            System.arraycopy(tnodeBuffer, 64 + 1 * 4, maxBlockID, 0, 4);
            int oldMax = ADisk.byteArrayToInt(maxBlockID);
            int newMax = Math.max(blockId, oldMax);
            byte[] newMaxBlockID = ADisk.intToByteArray(newMax);
            System.arraycopy(newMaxBlockID, 0, tnodeBuffer, 64 + 1 * 4, 4);
        } finally
        {
            lock.unlock();
        }

    }

    public int getMaxDataBlockId(int xid, int tnum)
            throws IOException, IllegalArgumentException {
        try {
            lock.lock();
            byte[] tnodeSector = new byte[Disk.SECTOR_SIZE];
            adisk.readSector(xid, SIZE_OF_FREELIST + tnum / 4, tnodeSector);
            byte[] maxBlockID = new byte[4];
            System.arraycopy(tnodeSector, SIZE_OF_TREE_NODE * (tnum % 4) + 64 + 1 * 4, maxBlockID, 0, 4);
            int maxBlock = ADisk.byteArrayToInt(maxBlockID);
            return maxBlock;
        } finally
        {
            lock.unlock();
        }
    }

    /*
     * Prints out the freeList
     */
    public void printFreeList() {
        int count = 0;
        for (int i = 0; i < freeList.length; i++) {
//          System.out.print(String.format("%8s",Integer.toBinaryString((int)freeList[i])).replace(' ', '0'));
            Integer val = new Integer(freeList[i]);
            String valBinary = val.toBinaryString(val);
            if (val == -128) {
                System.out.print("10000000");
            } else {
                System.out.print(String.format("%8s", valBinary).replace(' ', '0'));
            }
            System.out.printf("|");
            count++;
            if (count == 20) {
                count = 0;
                System.out.printf("\n");
            }

        }
        System.out.printf("\n");
    }
}
