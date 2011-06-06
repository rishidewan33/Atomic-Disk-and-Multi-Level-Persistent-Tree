
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;

/**
 *
 * @author Lindsey O'Niell, Rishi Dewan
 */
public class PTreeUnit {

    private PTree ptree;
    private static final int TO_USABLE_SECTORS = ADisk.REDO_LOG_SECTORS + 1 + PTree.SIZE_OF_PTREE_METADATA;
    private static byte[] hugeSevens = new byte[PTree.BLOCK_SIZE_BYTES];
    private static byte[] hugeSixes = new byte[PTree.BLOCK_SIZE_BYTES];
    private static byte[] hugeFives = new byte[PTree.BLOCK_SIZE_BYTES];
    private static byte[] hugeFours = new byte[PTree.BLOCK_SIZE_BYTES];


    public PTreeUnit() throws FileNotFoundException, IllegalArgumentException, IOException {
        ptree = new PTree(true);
        Arrays.fill(hugeSevens, (byte) 7);
        Arrays.fill(hugeSixes, (byte) 6);
        Arrays.fill(hugeFives, (byte) 5);
        Arrays.fill(hugeFours, (byte) 4);
    }

    public static void main(String args[]) throws FileNotFoundException, IllegalArgumentException, IOException, ResourceException, IndexOutOfBoundsException, ClassNotFoundException {
        System.out.println("********************************************");
        System.out.println("**           Testing PTree.java           **");
        System.out.println("********************************************");

        long startTime = System.currentTimeMillis();

        PTreeUnit ptu = new PTreeUnit();
        ptu.testFreeList();
        ptu.testFreeList2();
        ptu.testCreateTree();
        ptu.testCreateTree2();
        ptu.testWriteData();
        ptu.testReadData();
        ptu.testMultipleTreeWrites();
        long totalTime = System.currentTimeMillis() - startTime;
        double seconds = totalTime / 1000.0;
        long minutes = (long) (seconds / 60);
        seconds %= 60;
        System.out.println("ALL TESTS PASSED SUCCESFULLY");
        System.out.println("Testing took: " + minutes + " minutes and "+ seconds + " seconds.");
        System.exit(0);
    }

    private void testFreeList() throws IllegalArgumentException, IOException {
        assert ptree.isBlockFree(1);
        //set a few blocks as in use
        ptree.setBlockInUse(1);
        ptree.setBlockInUse(2);
        ptree.setBlockInUse(7);
        assert !ptree.isBlockFree(1);
        assert !ptree.isBlockFree(2);
        assert !ptree.isBlockFree(7);
        assert ptree.isBlockFree(6000);
        //set the blocks back to free
        ptree.setBlockFree(1);
        ptree.setBlockFree(2);
        ptree.setBlockFree(7);
        //make sure they are now available
        assert ptree.isBlockFree(1);
        assert ptree.isBlockFree(2);
        assert ptree.isBlockFree(7);
        //Random tests:
        assert ptree.isBlockFree(6000);
        assert ptree.isBlockFree(9);
        System.out.println("testFreeList PASSED");
    }

    private void testFreeList2() throws IllegalArgumentException, IOException
    {
        for(int i = 0; i < PTree.SIZE_OF_FREELIST; i+=2) //Set every other bit in the free list to "used".
        {
            ptree.setBlockInUse(i);
            assert !ptree.isBlockFree(i);
        }
        for(int i = 0; i < PTree.SIZE_OF_FREELIST; i+=2) //Reset every other bit in the free list to "free".
        {
            ptree.setBlockFree(i);
            assert ptree.isBlockFree(i);
        }
        for(int i = 1; i < PTree.SIZE_OF_FREELIST; i+=2) //Set every other bit (This time i = 1) in the free list to "used".
        {
            ptree.setBlockInUse(i);
            assert !ptree.isBlockFree(i);
        }
        for(int i = 1; i < PTree.SIZE_OF_FREELIST; i+=2) //Reset every other bit (starting with i = 1) in the free list to "free".
        {
            ptree.setBlockFree(i);
            assert ptree.isBlockFree(i);
        }
        for(int i = 0; i < PTree.SIZE_OF_FREELIST; i++) //Check the entire free list is free.
        {
            assert ptree.isBlockFree(i);
        }
        System.out.println("testFreeList2 PASSED");
        return;
    }

    private void testCreateTree() throws IOException, IllegalArgumentException, ResourceException, IndexOutOfBoundsException, ClassNotFoundException
    {
        int transID = ptree.beginTrans();
        for (int i = 0; i < ptree.treeIDs.length; i++) {
            assert !ptree.treeIDs[i];
        }
        int tNum = 0;
        for (int i = 0; i < 10; i++) {
            tNum = ptree.createTree(transID);
            assert ptree.treeIDs[tNum] == true;
        }
        //commit transaction so all writes go to disk.
        ptree.commitTrans(transID);
        //Stall for WB Thread to finish
        while (ptree.adisk.WBList.size() != 0)
        {
            //wait for WB to be done.
        }
        for (int i = 0; i < 10; i++) {
            byte[] tnodeSector = new byte[Disk.SECTOR_SIZE];
            ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), ADisk.REDO_LOG_SECTORS + 1 + PTree.SIZE_OF_FREELIST + i / 4, tnodeSector);
            ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
            byte[] inUse = new byte[4];
            System.arraycopy(tnodeSector, PTree.SIZE_OF_TREE_NODE * (i % 4) + 64, inUse, 0, 4);
            int inUseval = ADisk.byteArrayToInt(inUse);
            assert inUseval == 1;
        }
        System.out.println("testCreateTree PASSED");
    }

    private void testCreateTree2() throws IOException, IllegalArgumentException, ResourceException, IndexOutOfBoundsException, ClassNotFoundException
    {
        int transID = ptree.beginTrans();
        for (int i = 10; i < ptree.treeIDs.length; i++) {
            assert !ptree.treeIDs[i];
        }
        int tNum = 0;
        for (int i = 0; i < 32; i++) //We're going to fatten a transaction.
        {
            tNum = ptree.createTree(transID);
            assert ptree.treeIDs[tNum] == true;
        }
        ptree.commitTrans(transID);
        //Stall for WB Thread to finish
        while (ptree.adisk.WBList.size() != 0)
        {
            //wait for WB to be done.
        }
        for (int i = 10; i < 42; i++) {
            byte[] tnodeSector = new byte[Disk.SECTOR_SIZE];
            ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), ADisk.REDO_LOG_SECTORS + 1 + PTree.SIZE_OF_FREELIST + i / 4, tnodeSector);
            ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
            byte[] inUse = new byte[4];
            System.arraycopy(tnodeSector, PTree.SIZE_OF_TREE_NODE * (i % 4) + 64, inUse, 0, 4);
            int inUseval = ADisk.byteArrayToInt(inUse);
            assert inUseval == 1;
        }
        System.out.println("testCreateTree2 PASSED");
    }


    /*
     * Tests the writeData() method in PTree
     */
    private void testWriteData() throws FileNotFoundException, IllegalArgumentException, IOException, ResourceException, IndexOutOfBoundsException, ClassNotFoundException {
        ptree = new PTree(true);
        //begin a transaction
        int transID = ptree.beginTrans();
        //create a tree
        int tNum = ptree.createTree(transID);

        //write to tree
        ptree.writeData(transID, tNum, 0, hugeFives); //1
        ptree.writeData(transID, tNum, 1, hugeFives); //2
        ptree.writeData(transID, tNum, 1, hugeSevens); //2
        ptree.writeData(transID, tNum, 5, hugeSevens); //3
        ptree.writeData(transID, tNum, 200, hugeSixes); // 4(data),5(ID)
        ptree.writeData(transID, tNum, 201, hugeSixes); // 6
        ptree.writeData(transID, tNum, 202, hugeSixes); // 7
        ptree.writeData(transID, tNum, 202, hugeSevens);// 7
        ptree.writeData(transID, tNum, 265, hugeFours); //DI = 8, I = 9, data = a
        ptree.writeData(transID, tNum, 265, hugeFives); //DI = 8, I = 9, data = a
        ptree.writeData(transID, tNum, 2301, hugeFours); // I = b, // data = c
        ptree.writeData(transID, tNum, 65799, hugeFours); // I = d, data = e
        //check maxBlockId works
        int maxBlock = ptree.getMaxDataBlockId(transID, tNum);
        assert maxBlock == 65799;

//            ptree.deleteTree(transID, tNum);

        //commit transaction
        ptree.commitTrans(transID);
        //Stall for WB Thread to finish
        while (ptree.adisk.WBList.size() != 0) {
            //wait for WB to be done.
            System.out.print("");
        }

        //Let's check it all worked correctly!!
        //First, we'll check the TNode Sector is handled correctly
        //get tree's sector from disk
        byte[] tnodeSector = new byte[Disk.SECTOR_SIZE];
        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), ADisk.REDO_LOG_SECTORS + 1 + PTree.SIZE_OF_FREELIST + tNum / 4, tnodeSector);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());

//            System.out.println("tnodeSector array: ");
//            ADiskUnit.printByteArray(tnodeSector);

        //check MaxBlockID written works!
        byte[] maxBlockID = new byte[4];
        System.arraycopy(tnodeSector, 64 + 1 * 4, maxBlockID, 0, 4);
        int maxBlockWritten = ADisk.byteArrayToInt(maxBlockID);
        assert maxBlockWritten == 65799;

        //Check that the bit is on that tree in use:
        byte[] inUseByte = new byte[4];
        System.arraycopy(tnodeSector, 64, inUseByte, 0, 4);
        assert ADisk.byteArrayToInt(inUseByte) == 1;
        //check first data write:
        byte[] firstDataWrite = new byte[4];
        System.arraycopy(tnodeSector, 64 + 24, firstDataWrite, 0, 4);
        assert ADisk.byteArrayToInt(firstDataWrite) == 1;
        //check second data write:
        byte[] secondDataWrite = new byte[4];
        System.arraycopy(tnodeSector, 64 + 24 + 1 * 4, secondDataWrite, 0, 4);
        assert ADisk.byteArrayToInt(secondDataWrite) == 2;
        //check third and fourth data write (should display more recent write):
        byte[] thirdDataWrite = new byte[4];
        System.arraycopy(tnodeSector, 64 + 24 + 5 * 4, thirdDataWrite, 0, 4);
        assert ADisk.byteArrayToInt(thirdDataWrite) == 3;
        //check Indirect block writes
        byte[] IndWrite = new byte[4];
        System.arraycopy(tnodeSector, 64 + 24 + 8 * 4, IndWrite, 0, 4);
        assert ADisk.byteArrayToInt(IndWrite) == 5;
        //check Doubly Indirect writes
        byte[] DIndWrite = new byte[4];
        System.arraycopy(tnodeSector, 64 + 24 + 9 * 4, DIndWrite, 0, 4);
        assert ADisk.byteArrayToInt(DIndWrite) == 8;

        //Now, check the data got on disk to the right place!

        //buffers we'll use to check:
        byte[] buff1 = new byte[Disk.SECTOR_SIZE];
        byte[] buff2 = new byte[Disk.SECTOR_SIZE];
        byte[] buff = new byte[PTree.BLOCK_SIZE_BYTES];

        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 1 * 2, buff1);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 1 * 2 + 1, buff2);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        buff = ADisk.concat(buff1, buff2);
        assert Arrays.equals(hugeFives, buff);

        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 2 * 2, buff1);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 2 * 2 + 1, buff2);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        buff = ADisk.concat(buff1, buff2);
        assert Arrays.equals(hugeSevens, buff);

        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 3 * 2, buff1);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 3 * 2 + 1, buff2);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        buff = ADisk.concat(buff1, buff2);
        assert Arrays.equals(hugeSevens, buff);

        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 4 * 2, buff1);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 4 * 2 + 1, buff2);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        buff = ADisk.concat(buff1, buff2);
        assert Arrays.equals(hugeSixes, buff);

        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 6 * 2, buff1);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 6 * 2 + 1, buff2);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        buff = ADisk.concat(buff1, buff2);
        assert Arrays.equals(hugeSixes, buff);

        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 7 * 2, buff1);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 7 * 2 + 1, buff2);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        buff = ADisk.concat(buff1, buff2);
        assert Arrays.equals(hugeSevens, buff);

        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 10 * 2, buff1);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 10 * 2 + 1, buff2);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        buff = ADisk.concat(buff1, buff2);
        assert Arrays.equals(hugeFives, buff);

        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 12 * 2, buff1);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 12 * 2 + 1, buff2);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        buff = ADisk.concat(buff1, buff2);
        assert Arrays.equals(hugeFours, buff);

        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 14 * 2, buff1);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 14 * 2 + 1, buff2);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        buff = ADisk.concat(buff1, buff2);
        assert Arrays.equals(hugeFours, buff);

        System.out.println("testWriteData() PASSED");
    }

    /*
     * Tests the readData() method in PTree
     */
    private void testReadData() throws IOException {

        int transID = ptree.beginTrans();
        //Use the writes from testWriteData()
        byte[] checkRBuff = new byte[PTree.BLOCK_SIZE_BYTES];
        ptree.readData(transID, 0, 0, checkRBuff);
        assert Arrays.equals(checkRBuff, hugeFives);
        ptree.readData(transID, 0, 1, checkRBuff);
        assert Arrays.equals(checkRBuff, hugeSevens);
        ptree.readData(transID, 0, 5, checkRBuff);
        assert Arrays.equals(checkRBuff, hugeSevens);
        ptree.readData(transID, 0, 200, checkRBuff);
        assert Arrays.equals(checkRBuff, hugeSixes);
        ptree.readData(transID, 0, 201, checkRBuff);
        assert Arrays.equals(checkRBuff, hugeSixes);
        ptree.readData(transID, 0, 202, checkRBuff);
        assert Arrays.equals(checkRBuff, hugeSevens);
        ptree.readData(transID, 0, 265, checkRBuff);
        assert Arrays.equals(checkRBuff, hugeFives);
        ptree.readData(transID, 0, 2301, checkRBuff);
        assert Arrays.equals(checkRBuff, hugeFours);
        ptree.readData(transID, 0, 65799, checkRBuff);
        assert Arrays.equals(checkRBuff, hugeFours);

        //Now, make sure get back zero buffer if didn't write it:
        byte[] allZeroes = new byte[PTree.BLOCK_SIZE_BYTES];
        Arrays.fill(allZeroes, (byte) 0);
        ptree.readData(transID, 0, 34, checkRBuff);
        assert Arrays.equals(checkRBuff, allZeroes);

    }

    private void testMultipleTreeWrites() throws IOException, IllegalArgumentException, ResourceException, IndexOutOfBoundsException, ClassNotFoundException {
        ptree = new PTree(true);
        //begin a transaction
        int transID = ptree.beginTrans();
        //create a tree
        int tNum1 = ptree.createTree(transID);
        int tNum2 = ptree.createTree(transID);
        int tNum3 = ptree.createTree(transID);
        int tNum4 = ptree.createTree(transID);
        int tNum5 = ptree.createTree(transID);
        int tNum6 = ptree.createTree(transID);
        assert tNum1 == 0;
        assert tNum2 == 1;
        assert tNum3 == 2;
        assert tNum4 == 3;
        assert tNum5 == 4;
        assert tNum6 == 5;

        assert ptree.numFreeBlocks() == PTree.TOTAL_AVAILABLE_BLOCKS;

        ptree.writeData(transID, tNum1, 0, hugeFours); //1
        ptree.writeData(transID, tNum2, 0, hugeFives); //2
        ptree.writeData(transID, tNum1, 200, hugeFours); //data: 3, ind: 4
        ptree.writeData(transID, tNum2, 200, hugeFives); //data: 5, ind: 6
        ptree.writeData(transID, tNum1, 3000, hugeFours); //DI: 7, Ind: 8, data: 9
        ptree.writeData(transID, tNum2, 3000, hugeFives); //DI: 10(a), Ind: 11(b), data: 12(c)


        ptree.deleteTree(transID, tNum2);

        //commit transaction
        ptree.commitTrans(transID);
        //Stall for WB Thread to finish
        while (ptree.adisk.WBList.size() != 0) {
            //wait for WB to be done.
        }

        //Verify sectors visually very easily (uncomment printArray).
        byte[] tnodeSector1 = new byte[Disk.SECTOR_SIZE];
        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), ADisk.REDO_LOG_SECTORS + 1 + PTree.SIZE_OF_FREELIST + tNum1 / 4, tnodeSector1);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
//            System.out.println("tnodeSector array1: ");
//            ADiskUnit.printByteArray(tnodeSector1);

        byte[] tnodeSector2 = new byte[Disk.SECTOR_SIZE];
        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), ADisk.REDO_LOG_SECTORS + 1 + PTree.SIZE_OF_FREELIST + tNum6 / 4, tnodeSector2);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
//            System.out.println("tnodeSector array2: ");
//            ADiskUnit.printByteArray(tnodeSector2);

        //Since deleted tree 2, check that tnodeBuffer is empty and ind blocks and dir blocks
        byte[] tnode2Buffer = new byte[PTree.SIZE_OF_TREE_NODE];
        System.arraycopy(tnodeSector1, PTree.SIZE_OF_TREE_NODE * tNum2, tnode2Buffer, 0, PTree.SIZE_OF_TREE_NODE);
        byte[] treeNodeZeroes = new byte[PTree.SIZE_OF_TREE_NODE];
        assert Arrays.equals(tnode2Buffer, treeNodeZeroes);

        //also, check freeList to make sure all blocks are free from that tree:
        //2,5,6,10,11,12
        //and, that the ones from transaction 1 aren't free:
        //1,3,4,7,8,9
        assert !ptree.isBlockFree(1);
        assert !ptree.isBlockFree(3);
        assert !ptree.isBlockFree(4);
        assert !ptree.isBlockFree(7);
        assert !ptree.isBlockFree(8);
        assert !ptree.isBlockFree(9);
        assert ptree.isBlockFree(2);
        assert ptree.isBlockFree(5);
        assert ptree.isBlockFree(6);
        assert ptree.isBlockFree(10);
        assert ptree.isBlockFree(11);
        assert ptree.isBlockFree(12);

        //test numFreeBlocks
        assert ptree.numFreeBlocks() == (PTree.TOTAL_AVAILABLE_BLOCKS - 6);

        //check disk writes:

        //buffers we'll use to check:
        byte[] buff1 = new byte[Disk.SECTOR_SIZE];
        byte[] buff2 = new byte[Disk.SECTOR_SIZE];
        byte[] buff = new byte[PTree.BLOCK_SIZE_BYTES];

        //check deleted tNum2 Indirect and direct blocks are gone (blocks 6, 10, and 11 -> see above)
        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 6 * 2, buff1);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 6 * 2 + 1, buff2);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        buff = ADisk.concat(buff1, buff2);
        assert Arrays.equals(buff, PTree.zeroBuffer);

        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 10 * 2, buff1);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 10 * 2 + 1, buff2);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        buff = ADisk.concat(buff1, buff2);
        assert Arrays.equals(buff, PTree.zeroBuffer);

        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 11 * 2, buff1);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 11 * 2 + 1, buff2);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        buff = ADisk.concat(buff1, buff2);
        assert Arrays.equals(buff, PTree.zeroBuffer);



        //check all writes went correctly to disk:

        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 1 * 2, buff1);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 1 * 2 + 1, buff2);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        buff = ADisk.concat(buff1, buff2);
        assert Arrays.equals(hugeFours, buff);

        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 2 * 2, buff1);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 2 * 2 + 1, buff2);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        buff = ADisk.concat(buff1, buff2);
        assert Arrays.equals(hugeFives, buff);

        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 3 * 2, buff1);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 3 * 2 + 1, buff2);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        buff = ADisk.concat(buff1, buff2);
        assert Arrays.equals(hugeFours, buff);

        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 5 * 2, buff1);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 5 * 2 + 1, buff2);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        buff = ADisk.concat(buff1, buff2);
        assert Arrays.equals(hugeFives, buff);

        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 9 * 2, buff1);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 9 * 2 + 1, buff2);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        buff = ADisk.concat(buff1, buff2);
        assert Arrays.equals(hugeFours, buff);

        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 12 * 2, buff1);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        ptree.adisk.AD.startRequest(Disk.READ, ptree.adisk.getTag(), TO_USABLE_SECTORS + 12 * 2 + 1, buff2);
        ptree.adisk.callback.waitForTag(ptree.adisk.setTag());
        buff = ADisk.concat(buff1, buff2);
        assert Arrays.equals(hugeFives, buff);

        System.out.println("testMultipleTreeWrites() PASSED");
    }
}
