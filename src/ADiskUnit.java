
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Random;

/**
 *
 * @author Rishi Dewan, Lindsey O'Niell
 */
public class ADiskUnit
{

  private ADisk testDisk;
  private byte[] ALL_ZEROES = new byte[Disk.SECTOR_SIZE];
  private byte[] ALL_ONES = new byte[Disk.SECTOR_SIZE];
  private byte[] ALL_TWOS = new byte[Disk.SECTOR_SIZE];
  private byte[] ALL_THREES = new byte[Disk.SECTOR_SIZE];
  private byte[] ALL_FOURS = new byte[Disk.SECTOR_SIZE];
  private byte[] ALL_FIVES = new byte[Disk.SECTOR_SIZE];
  private byte[] ALL_SIXES = new byte[Disk.SECTOR_SIZE];
  private byte[] ALL_SEVENS = new byte[Disk.SECTOR_SIZE];
  SimpleLock lock;
  //-------------------------------------------------------
  // requesDone -- callback -- check writes for status
  // and check reads for
  //-------------------------------------------------------
  public ADiskUnit() throws FileNotFoundException, IllegalArgumentException, IOException
  {
      testDisk = new ADisk(true);
      Arrays.fill(ALL_ONES, (byte)1);
      Arrays.fill(ALL_TWOS, (byte)2);
      Arrays.fill(ALL_THREES, (byte)3);
      Arrays.fill(ALL_FOURS, (byte)4);
      Arrays.fill(ALL_FIVES, (byte)5);
      Arrays.fill(ALL_SIXES, (byte)6);
      Arrays.fill(ALL_SEVENS, (byte)7);
      lock = new SimpleLock();
  }

  public static void main(String args[]) throws FileNotFoundException, IllegalArgumentException, IOException, ClassNotFoundException
   {
        System.out.println("**********************************************");
        System.out.println("**  All tests are completed when you see    **");
        System.out.println("**  'PASSED ALL TESTS' at the end. Error    **");
        System.out.println("**  statements are okay, their names should **");
        System.out.println("**  explain them. They should all be        **");
        System.out.println("**  related to disk failure testing     .   **");
        System.out.println("**********************************************");

        //Test ADisk contstructor
        ADiskUnit adu = new ADiskUnit();
        adu.testSizeOfDiskLists();
        adu.testTransactionFunctionality();

        //testTransactionClass will test the Transaction class and return a Transaction that
        //will be tested in testTransaction(Transaction t)
        adu.testTransactionClass();
        adu.testTransaction(new Transaction(99));
        adu.testCommitTransactions();
        adu.CS372Midterm2Tests();
        adu.testWriteBackList();
		adu.testMDToByteArray();
        adu.testLogManagement();
        adu.testTransCommits();
		adu.smallRecoveryTest();
        adu.testRecovery();
        adu.megaTest();
        System.out.println("***********PASSED ALL TESTS************");
        //We found that calling this stopped the WBThread prematurely at times
        System.exit(0);

  }

  /*
   * Test writing 20 transactions with 30 writes each to disk
   */
  private void testTransCommits() throws IllegalArgumentException, IndexOutOfBoundsException, IOException, ClassNotFoundException
  {
      try{
            lock.lock();
            int tid = testDisk.beginTransaction();
            for(int i= 0; i < 30; i++)
            {
                testDisk.writeSector(tid,1+i,ALL_SEVENS);
            }
            testDisk.commitTransaction(tid);
            int tid1 = testDisk.beginTransaction();
            for(int i= 0; i < 30; i++)
            {
                testDisk.writeSector(tid1,1+i,ALL_SEVENS);
            }
            testDisk.commitTransaction(tid1);
            int tid3 = testDisk.beginTransaction();
            for(int i= 0; i < 30; i++)
            {
                testDisk.writeSector(tid3,1+i,ALL_SEVENS);
            }
            testDisk.commitTransaction(tid3);
            int tid4 = testDisk.beginTransaction();
            for(int i= 0; i < 30; i++)
            {
                testDisk.writeSector(tid4,1+i,ALL_SEVENS);
            }
            testDisk.commitTransaction(tid4);
            int tid5 = testDisk.beginTransaction();
            for(int i= 0; i < 30; i++)
            {
                testDisk.writeSector(tid5,1+i,ALL_SEVENS);
            }
            testDisk.commitTransaction(tid5);
            int tid6 = testDisk.beginTransaction();
            for(int i= 0; i < 30; i++)
            {
                testDisk.writeSector(tid6,1+i,ALL_SEVENS);
            }
            testDisk.commitTransaction(tid6);
            int tid7 = testDisk.beginTransaction();
            for(int i= 0; i < 30; i++)
            {
                testDisk.writeSector(tid7,1+i,ALL_SEVENS);
            }
            testDisk.commitTransaction(tid7);
            int tid8 = testDisk.beginTransaction();
            for(int i= 0; i < 30; i++)
            {
                testDisk.writeSector(tid8,1+i,ALL_SEVENS);
            }
            testDisk.commitTransaction(tid8);
            int tid9 = testDisk.beginTransaction();
            for(int i= 0; i < 30; i++)
            {
                testDisk.writeSector(tid9,1+i,ALL_SEVENS);
            }
            testDisk.commitTransaction(tid9);
            int tid10 = testDisk.beginTransaction();
            for(int i= 0; i < 30; i++)
            {
                testDisk.writeSector(tid10,1+i,ALL_SEVENS);
            }
                    testDisk.commitTransaction(tid10);
            int tid11 = testDisk.beginTransaction();
            for(int i= 0; i < 30; i++)
            {
                testDisk.writeSector(tid11,1+i,ALL_SEVENS);
            }
             testDisk.commitTransaction(tid11);
            int tid12 = testDisk.beginTransaction();
            for(int i= 0; i < 30; i++)
            {
                testDisk.writeSector(tid12,1+i,ALL_SEVENS);
            }
             testDisk.commitTransaction(tid12);
            int tid13 = testDisk.beginTransaction();
            for(int i= 0; i < 30; i++)
            {
                testDisk.writeSector(tid13,1+i,ALL_SEVENS);
            }
             testDisk.commitTransaction(tid13);
            int tid14 = testDisk.beginTransaction();
            for(int i= 0; i < 30; i++)
            {
                testDisk.writeSector(tid14,1+i,ALL_SEVENS);
            }
             testDisk.commitTransaction(tid14);
            int tid15 = testDisk.beginTransaction();
            for(int i= 0; i < 30; i++)
            {
                testDisk.writeSector(tid15,1+i,ALL_SEVENS);
            }
             testDisk.commitTransaction(tid15);
            int tid16 = testDisk.beginTransaction();
            for(int i= 0; i < 30; i++)
            {
                testDisk.writeSector(tid16,1+i,ALL_SEVENS);
            }
             testDisk.commitTransaction(tid16);
            int tid17 = testDisk.beginTransaction();
            for(int i= 0; i < 30; i++)
            {
                testDisk.writeSector(tid17,1+i,ALL_SEVENS);
            }
             testDisk.commitTransaction(tid17);
            int tid18 = testDisk.beginTransaction();
            for(int i= 0; i < 30; i++)
            {
                testDisk.writeSector(tid18,1+i,ALL_SEVENS);
            }
             testDisk.commitTransaction(tid18);
            int tid19 = testDisk.beginTransaction();
            for(int i= 0; i < 30; i++)
            {
                testDisk.writeSector(tid19,1+i,ALL_SEVENS);
            }
             testDisk.commitTransaction(tid19);
            int tid20 = testDisk.beginTransaction();
            for(int i= 0; i < 30; i++)
            {
                testDisk.writeSector(tid20,1+i,ALL_SEVENS);
            }
             testDisk.commitTransaction(tid20);
      }
      finally{
          lock.unlock();
      }
  }

  /*
   * Very preliminary test to make sure the lists are cleared witha new ADisk
   */
  private void testSizeOfDiskLists()
  {
      assert testDisk.ActTransList.size() == 0;
      assert testDisk.WBList.size() == 0;
      System.out.println("testSizeOfDiskLists() passed");
  }

  /*
   * Various tests for all of the methods within Transaction.java
   * Testing is done with assertions (MAKE SURE THEY ARE ENABLED
   * FOR ACCURATE RESULTS)
   */
  private void testTransactionFunctionality() throws IllegalArgumentException, IndexOutOfBoundsException, IOException, ClassNotFoundException
  {
      try{
            lock.lock();

            //Test beginTransaction()
            assert testDisk.ActTransList.size() == 0;
            int transID = testDisk.beginTransaction();
            assert testDisk.ActTransList.size() == 1;

            //test that reated Transaction is the same as the one
            //on the ATL
            Transaction testTrans = testDisk.ActTransList.get(transID);
            //Test that Transaction created correctly
            Transaction.transState ts = testTrans.getTransState();
            assert ts == Transaction.transState.INPROGRESS;
            assert testTrans.getTransID() == transID;
            assert (testTrans.getSectorsUpdated()).isEmpty();
            assert testTrans.getSectorWrites().isEmpty();

            //test writeSector()
            byte[] testBuff = getRandBuff();
            testDisk.writeSector(transID, 4564, testBuff);
            assert (testTrans.getSectorsUpdated()).size() == 1;
            assert testTrans.getSectorWrites().size() == 1;
            assert Arrays.equals(testTrans.getSectorWrites().get(0), testBuff);
            assert testTrans.getSectorsUpdated().get(0) == 4564;
            for (int i = 0; i < 15; i++)
            {
                testBuff = getRandBuff();
                testDisk.writeSector(transID, i + 3000, testBuff);
                assert Arrays.equals(testTrans.getSectorWrites().get(i+1), testBuff);
                assert testTrans.getSectorsUpdated().get(i+1) == i+3000;
            }

            assert (testTrans.getSectorsUpdated()).size() == 16;
            assert testTrans.getSectorWrites().size() == 16;

            //test abortTransaction(tid)
            assert testDisk.ActTransList.size() == 1;
            testDisk.abortTransaction(transID);
            assert testDisk.ActTransList.size() == 0;
            try{
                testDisk.abortTransaction(transID);
            }
            catch (IllegalArgumentException IAE)
            {
                System.out.println("testTransactionFunctionality() IAE correctly Triggered: \n" + IAE);
            }
            System.out.println("testTransactionFunctionality() PASSED");
      }
      finally{
          lock.unlock();
      }
  }

  /*
   * More testing of Transaction.java as well as methods from ADisk.java
   */
  private void testTransactionClass() throws IOException, ClassNotFoundException
  {
      try
      {
            lock.lock();

            //test beginTransaction() again
            int transID1 = testDisk.beginTransaction();
            int transID2 = testDisk.beginTransaction();
            int transID3 = testDisk.beginTransaction();
            int transID4 = testDisk.beginTransaction();
            int transID5 = testDisk.beginTransaction();
            assert testDisk.ActTransList.size() == 5;
            Transaction transByteArray = testDisk.ActTransList.get(testDisk.beginTransaction());
            assert testDisk.ActTransList.size() == 6;

            transByteArray.addWrite(1056, ALL_ONES);
            transByteArray.addWrite(1057, ALL_TWOS);
            transByteArray.addWrite(1058, ALL_THREES);

            //testing that our loop in Transaction.addWrite(...)
            //correctly updates the sector as the most recent
            for (int ii = 0; ii < 60; ii++){
                    transByteArray.addWrite(1058, ALL_THREES);
            }

            testDisk.commitTransaction(transByteArray.getTransID());

            boolean resultByteArrayTest = toAndFromByteArrayWorks(transByteArray);
            if(resultByteArrayTest){
                //System.out.println("toAndFromByteArrayWorks() PASSED ");
            }
            else{
                //System.out.println("toAndFromByteArrayWorks() FAILED ");
            }

            boolean resultTestParseLogBytes = testParseLogBytes(transByteArray);
            if (resultTestParseLogBytes){
                //System.out.println("testParseLogBytes() PASSED");
            }
            else{
                //System.out.println("testParseLogBytes() FAILED");
            }
            // Test Transaction functionality
            Transaction transTest = new Transaction(99);
            boolean resultTestTransaction = testTransaction(transTest);
            if (resultTestTransaction){
                //System.out.println("testTransaction() PASSED");
            }
            else{
                //System.out.println("testTransaction() FAILED");
            }

            //Test WriteBackList
            boolean resultTestWriteBackList = testWriteBackList();
            if (resultTestWriteBackList){
                //System.out.println("testWriteBackList() PASSED");
            }
            else{
                //System.out.println("testWriteBackList() FAILED");
            }
            //System.out.println("testTransactionClass() PASSED");
      }
      finally{
          lock.unlock();
      }
  }

  //generates a random buffer
    private byte[] getRandBuff()
    {
            byte[] buffer = new byte[512];
            Random r = new Random();
            r.nextBytes(buffer);
            return buffer;
    }

    /*
     * Test commiting all of the transactions of the ActiveTransaction List 
     */
    private void testCommitTransactions() throws IOException
    {
        try{
            lock.lock();
            assert testDisk.ActTransList.size() == 5;
            Iterator<Integer> it = testDisk.ActTransList.ActiveTransactionKeySet().iterator();
            while(it.hasNext())
            {
                int curTID = it.next();
                testDisk.commitTransaction(curTID);
            }
            assert testDisk.ActTransList.size() == 0;
            //System.out.println("testCommitTransactions() PASSED");
            }
            finally{
                lock.unlock();
        }
    }

    /*
     * Test given by Mike on our previous exam
     * Tests reads and writes
     */
    private void CS372Midterm2Tests() throws FileNotFoundException, IllegalArgumentException, IOException, ClassNotFoundException
    {
        try{
            lock.lock();
            testDisk = new ADisk(true); //Initially, all sectors are filled with ALL_ZEROES

            byte b1[] = new byte[Disk.SECTOR_SIZE];
            byte b2[] = new byte[Disk.SECTOR_SIZE];
            byte b3[] = new byte[Disk.SECTOR_SIZE];
            byte b4[] = new byte[Disk.SECTOR_SIZE];

            int t1 = testDisk.beginTransaction();
            int t2 = testDisk.beginTransaction();
            int t3 = testDisk.beginTransaction();
            int t4 = testDisk.beginTransaction();

            testDisk.writeSector(t1, 1, ALL_ONES);
            testDisk.writeSector(t1, 2, ALL_TWOS);

            testDisk.writeSector(t2, 1, ALL_THREES);
            testDisk.writeSector(t2, 2, ALL_FOURS);

            testDisk.writeSector(t3, 1, ALL_FIVES);
            testDisk.writeSector(t3, 2, ALL_SIXES);

            testDisk.writeSector(t4, 1, ALL_SEVENS);
            testDisk.readSector(t2, 1, b1);
            testDisk.readSector(t4, 2, b2);
            testDisk.commitTransaction(t3);
            testDisk.readSector(t2, 1, b3);
            testDisk.commitTransaction(t1);
            testDisk.readSector(t4, 2, b4);
    //        printByteArray(b1);
    //        printByteArray(b2);
    //        printByteArray(b3);
    //        printByteArray(b4);

            assert Arrays.equals(b1, ALL_THREES);
            assert Arrays.equals(b2, ALL_ZEROES);
            assert Arrays.equals(b3, ALL_THREES);
            assert Arrays.equals(b4, ALL_TWOS);


            //Test recovery by simulating crash by creating new ADisk object 
            //System.out.println("Right before testing recovery:");

            testDisk = new ADisk(false);

            byte[] testRecovery = new byte[Disk.SECTOR_SIZE];
            testDisk.AD.startRequest(Disk.READ, testDisk.getTag(), 1025+1, testRecovery);
            testDisk.callback.waitForTag(testDisk.setTag());
            assert Arrays.equals(testRecovery, ALL_ONES);
            //System.out.println("CS372MidtermTests() PASSED");
        }
        finally
        {
            lock.unlock();
        }
    }
    /*
     * Prints a byte array
     */
    public static void printByteArray(byte[] byteArray)
    {
        int count = 0;
        for (int i = 0; i < byteArray.length; i++)
        {
            System.out.print(byteArray[i] + "|");
            count++;
            if (count == 32)
            {
                System.out.println();
                count = 0;
            }
        }
    
   }

  /*
   * Basic tests to make sure the WriteBack List operates correctly 
   */
  private boolean testWriteBackList() throws IOException
  {
      try{
          lock.lock();
          WriteBackList WB = new WriteBackList();

          Transaction testWB = new Transaction(1);
          Transaction testWB2 = new Transaction(2);
          Transaction testWB3 = new Transaction(3);
          Transaction testWB4 = new Transaction(4);

          //Fill testWrite with a value so we know
          //we are reading the right sector
          byte testWrite[] = new byte[508];
          int value = 456;
          byte testValue[] = ADisk.intToByteArray(value);
          testWrite = ADisk.concat(testValue, testWrite);

          byte testWrite2[] = new byte[508];
          int value2 = 789;
          byte testValue2[] = ADisk.intToByteArray(value2);
          testWrite2 = ADisk.concat(testValue2, testWrite2);

          byte testWrite3[] = new byte[508];
          int value3 = 555;
          byte testValue3[] = ADisk.intToByteArray(value3);
          testWrite3 = ADisk.concat(testValue3, testWrite3);

          byte testWrite4[] = new byte[508];
          int value4 = 18;
          byte testValue4[] = ADisk.intToByteArray(value4);
          testWrite4 = ADisk.concat(testValue4, testWrite4);

          byte testWrite5[] = new byte[508];
          //Fill testWrite5 with a value so we know
          //we are reading a separate sector update
          int value5 = 123;
          byte testValue5[] = ADisk.intToByteArray(value5);
          testWrite5 = ADisk.concat(testValue5, testWrite5);

          testWB.addWrite(1234, testWrite);
          testWB2.addWrite(4534, testWrite2);
          testWB3.addWrite(5656, testWrite3);
          testWB4.addWrite(2229, testWrite4);
          //duplicate sector write with different buffer
          //by a different transaction
          testWB4.addWrite(1234, testWrite5);

          // test addCommitted
          // NOTE commit orders
          testWB2.commit();
          WB.addCommitted(testWB2);
          testWB.commit();
          WB.addCommitted(testWB);
          testWB4.commit();
          WB.addCommitted(testWB4);
          testWB3.commit();
          WB.addCommitted(testWB3);

          assert WB.size() == 4;

          //test checkRead
          byte testRead[] = new byte[512];
          byte testRead2[] = new byte[512];
          byte testRead3[] = new byte[512];
          byte testRead4[] = new byte[512];
          byte testReadFalse[] = new byte[512];
          assert WB.checkRead(4534, testRead2);
          assert Arrays.equals(testRead2, testWrite2);
          assert ADisk.byteArrayToInt(testWrite2) == 789;
          assert WB.checkRead(5656, testRead3);
          assert ADisk.byteArrayToInt(testWrite3) == 555;
          assert Arrays.equals(testRead3, testWrite3);
          assert WB.checkRead(2229, testRead4);
          assert Arrays.equals(testRead4, testWrite4);
          assert ADisk.byteArrayToInt(testWrite4) == 18;
          assert !(WB.checkRead(3333, testReadFalse));

          //Now, check that returns LATEST commit
          //should be equal to testWrite5, not testWrite
          //This is because testWB4 was committed LATER than testWB
          assert WB.checkRead(1234, testRead);
          assert Arrays.equals(testRead, testWrite5);
          assert !(Arrays.equals(testRead, testWrite));
          assert ADisk.byteArrayToInt(testWrite) == 456;
          assert ADisk.byteArrayToInt(testWrite5) == 123;

          //test getNextWriteBack and removeNextWB
          Transaction testGetWB = WB.getNextWriteback();
          assert testGetWB == testWB2;
          Transaction testRemWB = WB.removeNextWriteback();
          assert testRemWB == testGetWB;
          assert WB.size() == 3;
          WB.removeNextWriteback();
          assert WB.size() == 2;
          WB.removeNextWriteback();
          assert WB.size() == 1;
          WB.removeNextWriteback();
          assert WB.size() == 0;

          System.out.println("testWriteBackList() PASSED.");
          return true;
          }
      finally{
          lock.unlock();
      }
  }


  /*
   * Testing of methods with Transaction.java
   */
  private boolean testTransaction (Transaction t) throws IOException
  {
      try{
          lock.lock();
          assert t.getTransID() == 99;

          // Test addWrite
          assert t.sectorsUpdated.isEmpty();
          assert t.sectorWrites.isEmpty();

          //Fill testWrite with a value so we know
          //we are reading the right sector
          byte testWrite[] = new byte[508];
          int value = 456;
          byte testValue[] = ADisk.intToByteArray(value);
          testWrite = ADisk.concat(testValue, testWrite);

          byte testWrite2[] = new byte[508];
          int value2 = 789;
          byte testValue2[] = ADisk.intToByteArray(value2);
          testWrite2 = ADisk.concat(testValue2, testWrite2);

          byte testWrite3[] = new byte[508];
          int value3 = 555;
          byte testValue3[] = ADisk.intToByteArray(value3);
          testWrite3 = ADisk.concat(testValue3, testWrite3);

          byte testWrite4[] = new byte[508];
          int value4 = 18;
          byte testValue4[] = ADisk.intToByteArray(value4);
          testWrite4 = ADisk.concat(testValue4, testWrite4);

          t.addWrite(1234, testWrite);
          assert t.sectorsUpdated.size() == 1;
          assert t.sectorWrites.size() == 1;
          assert t.sectorsUpdated.get(0) == 1234;
          assert t.sectorWrites.get(0).length == 512;
          t.addWrite(4534, testWrite2);
          t.addWrite(5656, testWrite3);
          t.addWrite(2229, testWrite4);
          assert t.sectorsUpdated.size() == 4;
          assert t.sectorWrites.size() == 4;
          assert t.sectorsUpdated.get(1) == 4534;
          assert t.sectorWrites.get(1).length == 512;
          assert t.sectorsUpdated.get(2) == 5656;
          assert t.sectorWrites.get(2).length == 512;
          assert t.sectorsUpdated.get(3) == 2229;
          assert t.sectorWrites.get(3).length == 512;

          //Test checkRead
          byte testRead[] = new byte[512];
          byte testRead2[] = new byte[512];
          byte testRead3[] = new byte[512];
          byte testRead4[] = new byte[512];
          byte testReadFalse[] = new byte[512];
          assert t.checkRead(1234, testRead);
          assert Arrays.equals(testRead, testWrite);
          assert t.checkRead(4534, testRead2);
          assert Arrays.equals(testRead2, testWrite2);
          assert t.checkRead(5656, testRead3);
          assert Arrays.equals(testRead3, testWrite3);
          assert t.checkRead(2229, testRead4);
          assert Arrays.equals(testRead4, testWrite4);
          assert !(t.checkRead(3333, testReadFalse));

          //Test commit() and abort()
          assert t.getTransState() == Transaction.transState.INPROGRESS;
          t.commit();
          assert t.getTransState() == Transaction.transState.COMMITED;
          t.abort();
          assert t.getTransState() == Transaction.transState.ABORTED;

          //Test getNUpdatedSectors
          int numUpdatedSectorsByTrans = t.getNUpdatedSectors();
          assert numUpdatedSectorsByTrans == 4;

          //Test getUpdateI
          byte testGetUpdateI[] = new byte[512];
          int secNumI = t.getUpdateI(2, testGetUpdateI);
          assert secNumI == 5656;
          assert Arrays.equals(testGetUpdateI, testWrite3);
          byte testGetUpdateI2[] = new byte[512];
          int secNumI2 = t.getUpdateI(1, testGetUpdateI2);
          assert secNumI2 == 4534;
          assert Arrays.equals(testGetUpdateI2, testWrite2);

          //Test getSectorsForLog(): This is done in testParseLogBytes

          //Test parseHeader()
          boolean testPH = testParseHeader(t);
          assert testPH;

          //Test parseLogBytes()
          t.setTransState(Transaction.transState.COMMITED);
          boolean testPLB = testParseLogBytes(t);
          assert testPLB;

          //Test rememberLogSectors(), recallLogSectorStart(), and
          //recallLogSectorsNSectors()
          assert t.recallLogSectorStart() == 0;
          assert t.recallLogSectorNSectors() == 0;

          t.rememberLogSectors(1024, t.sectorsUpdated.size() + 2);
          assert t.recallLogSectorStart() == 1024;
          assert t.recallLogSectorNSectors() == t.sectorsUpdated.size() + 2;

          return true;
      }
      finally{
          lock.unlock();
      }
  }

  /*
   * Testing our parsing of a header
   */
  private boolean testParseHeader(Transaction t) throws IOException
  {
      try{
          lock.lock();
      //Create a byte array from the Transaction
      byte buff[] = t.getSectorsForLog();
      byte result[] = new byte[512];
      //Store the first sector (should be the header) in 'result'
      System.arraycopy(buff, 0, result, 0, Disk.SECTOR_SIZE);
      //confirm number of updates is 2 less than the number of
      //sectors updated (2 = header sector + commit sector)
//      System.out.println("NUMBER OF UPDATES from PH = " + t.parseHeader(result));
//      System.out.println("NUMBER OF UPDATES from getNUS= " + t.getNUpdatedSectors());
      assert Transaction.parseHeader(result) == t.getNUpdatedSectors()+2;
      return true;
      }
      finally{
          lock.unlock();
      }
  }


  /*
   * Testing our parsing of an entire transaction 
   */
  private boolean testParseLogBytes(Transaction t) throws IOException
  {
      try{
          lock.lock();
          byte[] transBA = t.getSectorsForLog();
          Transaction compare = Transaction.parseLogBytes(transBA);
          if (t.getTransID() == compare.getTransID())
          {
                if (t.sectorsUpdated.size() == compare.sectorsUpdated.size())
                {
                    for (int i = 0; i < t.sectorsUpdated.size(); i++)
                    {
                        if (!t.sectorsUpdated.get(i).equals(compare.sectorsUpdated.get(i)))
                        {
                            System.out.println("t: " + t.sectorsUpdated.get(i));
                            System.out.println("compare: " + compare.sectorsUpdated.get(i));
                            return false;
                        }
                    }
                    if (t.sectorWrites.size() == compare.sectorWrites.size())
                    {
                        for (int i= 0; i < t.sectorWrites.size(); i ++){
                                if (!(Arrays.equals(t.sectorWrites.get(i), compare.sectorWrites.get(i))))
                                {
                                        return false;
                                }
                        }
                    }
                }
                return true;
          }

          return false;
      }
      finally{
          lock.unlock();
      }
  }


  /*
   * Tests the funcionality of createByteArray(), parseHeader()
   */
  private boolean toAndFromByteArrayWorks(Transaction t) throws IOException, ClassNotFoundException
  {
      try{
          lock.lock();
	  ArrayList<byte[]> tempSectorsWritten = t.getSectorWrites();
	  ArrayList<Integer> tempSectorsUpdated = t.getSectorsUpdated();

	  byte[] compare = t.createByteArray();

	  //test Transaction.parseHeader(compare)
	  int parseHeadVal = Transaction.parseHeader(compare);

	  if (parseHeadVal == compare.length/Disk.SECTOR_SIZE){
//		  System.out.println("Transaction.parseHeader() WORKS. WOOT WOOT!");
	  }

          byte[] hNum = new byte[4];
	  System.arraycopy(compare, 0, hNum, 0, 4);
	  int headerNumber = ADisk.byteArrayToInt(hNum);
//	  System.out.println("headerNum = " + headerNumber);

	  byte[] tIDhead = new byte[4];
	  System.arraycopy(compare, 4, tIDhead, 0, 4);
	  int tIDheadfromBA = ADisk.byteArrayToInt(tIDhead);
//	  System.out.println("transID from header= " + tIDheadfromBA);

	  byte[] secNum = new byte[4];
	  System.arraycopy(compare, 8, secNum, 0, 4);
	  int secNumfromBA = ADisk.byteArrayToInt(secNum);
//	  System.out.println("secNum = " + secNumfromBA);

	  ArrayList<Integer> secUpdatedfromBA = new ArrayList<Integer>();
	  for (int i = 0; i < secNumfromBA; i++)
	  {
		  byte[] secToUpdateNum = new byte[4];
		  System.arraycopy(compare, 12 + i*4, secToUpdateNum, 0, 4);
		  secUpdatedfromBA.add(ADisk.byteArrayToInt(secToUpdateNum));
	  }

	  for (int i = 0; i < secUpdatedfromBA.size(); i++)
	  {
//		  System.out.println("element " + i + " of secUpdatedfromBA = " + secUpdatedfromBA.get(i));
	  }

	  ArrayList<byte[]> sectorsWrittenfromBA = new ArrayList<byte[]>();
	  for (int i = 0; i < secNumfromBA; i++)
	  {
		  	  byte[] sectorsWritten = new byte[Disk.SECTOR_SIZE];
			  System.arraycopy(compare, 512+(i*Disk.SECTOR_SIZE), sectorsWritten, 0, Disk.SECTOR_SIZE);
			  sectorsWrittenfromBA.add(sectorsWritten);
	  }

	  for (int i = 0; i < sectorsWrittenfromBA.size(); i++)
	  {
//		  System.out.println("element " + i + " of sectorsWrittenfromBA = " + sectorsWrittenfromBA.get(i));
	  }

	  byte[] comNum = new byte[4];
	  System.arraycopy(compare, (512+secNumfromBA*Disk.SECTOR_SIZE), comNum, 0, 4);
	  int comNumfromBA = ADisk.byteArrayToInt(comNum);
//	  System.out.println("commitNum = " + comNumfromBA);

	  byte[] tIDcom = new byte[4];
	  System.arraycopy(compare, (512+4+secNumfromBA*Disk.SECTOR_SIZE), tIDcom, 0, 4);
	  int tIDcomfromBA = ADisk.byteArrayToInt(tIDcom);
//	  System.out.println("transID from commit = " + tIDcomfromBA);

	  /*
	   * Verify that the transaction was converted to a byte array
	   * and reconstructed successfully. This is a more thorough test
           * than just just the Transaction.parseLogBytes() test
	   */
	  if (t.getTransID() == tIDcomfromBA && t.getTransID() == tIDheadfromBA)
	  {
		 if (t.getSectorWrites().size() == secNumfromBA)
		 {
			if (comNumfromBA == ADisk.COMMIT &&  headerNumber == ADisk.HEADER)
			{
				if (sectorsWrittenfromBA.size() == tempSectorsWritten.size())
				{
                                    for (int i= 0; i < sectorsWrittenfromBA.size(); i ++)
                                    {
                                        if (!(Arrays.equals(sectorsWrittenfromBA.get(i), tempSectorsWritten.get(i))))
                                        {
                                            return false;
                                        }
                                    }
                                    if(tempSectorsUpdated.size() == secUpdatedfromBA.size())
                                    {
                                        for (int ii = 0; ii < secUpdatedfromBA.size(); ii++)
                                        {
                                            if (!(tempSectorsUpdated.get(ii).equals(secUpdatedfromBA.get(ii))))
                                            {
                                                return false;
                                            }
                                        }
                                    }
					
				}
                                return true;
                        }
                 }
	  }
	  return false;
  }
      finally{
          lock.unlock();
      }
  }

  /*
   * tests our metaDataToByteArray() function in LogStatus
   */
   private void testMDToByteArray() throws FileNotFoundException, IllegalArgumentException, IOException
   {
	   boolean result = true;
	   //create a new disk, so should all be = 0
	   testDisk = new ADisk(false);
	   byte test1[] = testDisk.logStat.metaDataToByteArray();
	   byte test1CP[] = new byte[Disk.SECTOR_SIZE];
	   System.arraycopy(test1, 0, test1CP, 0, 4);
	   int CP = testDisk.byteArrayToInt(test1CP);
	   if (CP != 0){
		   result = false;
	   }
	   byte test1head[] = new byte[Disk.SECTOR_SIZE];
	   System.arraycopy(test1, 4, test1head, 0, 4);
	   int head = testDisk.byteArrayToInt(test1head);
	   if (head != 0){
		   result = false;
	   }
	   byte test1TotAvailSec[] = new byte[Disk.SECTOR_SIZE];
	   System.arraycopy(test1, 8, test1TotAvailSec, 0, 4);
	   int totAvailSec = testDisk.byteArrayToInt(test1TotAvailSec);
	   if (totAvailSec != 1024){
		   result = false;
	   }
	   //now, change the CP, head, and TotAvailSec to make sure it works
	   testDisk.logStat.checkpoint = 965;
	   testDisk.logStat.headptr = 389;
	   testDisk.logStat.totalSectorsAvailableInLog = 12;
	   	   byte test2[] = testDisk.logStat.metaDataToByteArray();
	   byte test2CP[] = new byte[Disk.SECTOR_SIZE];
	   System.arraycopy(test2, 0, test2CP, 0, 4);
	   int CP2 = testDisk.byteArrayToInt(test2CP);
	   if (CP2 != 965){
		   result = false;
	   }
	   byte test2head[] = new byte[Disk.SECTOR_SIZE];
	   System.arraycopy(test2, 4, test2head, 0, 4);
	   int head2 = testDisk.byteArrayToInt(test2head);
	   if (head2 != 389){
		   result = false;
	   }
	   byte test2TotAvailSec[] = new byte[Disk.SECTOR_SIZE];
	   System.arraycopy(test2, 8, test2TotAvailSec, 0, 4);
	   int totAvailSec2 = testDisk.byteArrayToInt(test2TotAvailSec);
	   if (totAvailSec2 != 12){
		   result = false;
	   }
	   if (result == true){
		   System.out.println("testMDToByteArray() PASSED");
	   }
	   else{
   		   System.out.println("testMDToByteArray() FAILED");
	   }

   }

  /*
   * Tests our log management (overfilling the log) and committing transactions 
   */
    private void testLogManagement() throws FileNotFoundException, IllegalArgumentException, IOException, IndexOutOfBoundsException, ClassNotFoundException
    {
        try{
            lock.lock();
			//added this to clear manual changes made to CP and head in testMDToByteArray()
			testDisk = new ADisk(false);
//        System.out.println("Beginning testLogManagement");
            int test[] = new int[32];
            int count = 0;
            for(int h = 0; h<32;h++)
            {
                test[h] = testDisk.beginTransaction();
                for(int i= 0; i < 30; i++)
                {
                    testDisk.writeSector(test[h],1+(count++),ALL_SEVENS);
                }

                testDisk.commitTransaction(test[h]);
            }
            //For testing recovery:
    //        testDisk = new ADisk(false);

            System.out.println("testLogManagement() PASSED.");
        }
        finally{
            lock.unlock();
        }
    }

	private void smallRecoveryTest() throws FileNotFoundException, IllegalArgumentException, IOException, IndexOutOfBoundsException, ClassNotFoundException
	{
		System.out.println("Starting smallRecoveryTest");
		testDisk = new ADisk(true);
		try{
				int recoverTrans = testDisk.beginTransaction();
				testDisk.writeSector(recoverTrans, 1, ALL_ONES);
				testDisk.writeSector(recoverTrans, 2, ALL_TWOS);
				testDisk.writeSector(recoverTrans, 3, ALL_THREES);
				testDisk.writeSector(recoverTrans, 4, ALL_FOURS);
				testDisk.commitTransaction(recoverTrans);


				int recoverTrans2 = testDisk.beginTransaction();
				testDisk.writeSector(recoverTrans2, 5, ALL_ONES);
				testDisk.writeSector(recoverTrans2, 6, ALL_TWOS);
				testDisk.writeSector(recoverTrans2, 7, ALL_THREES);
				testDisk.writeSector(recoverTrans2, 8, ALL_FOURS);
				testDisk.commitTransaction(recoverTrans2);
				//set a high fail prob so it fails before second
				//transaction gets written back!
				testDisk.setFailureProb(0, (float)0.3);
		}
		catch (IOException ex)
		{
				System.out.println("Disk is dead");
		}

		//manually checking disk for accurate behavior (since don't actually
		//know when it dies
		System.out.println("size of WBList in smallRT() = " + testDisk.WBList.size());
		System.out.println("smallRecoveryTest() completed");

	}

    /*
     * Smaller test of recovery by setting the fail probability of the disk
     */
    private void testRecovery() throws FileNotFoundException, IllegalArgumentException, IOException, IndexOutOfBoundsException, ClassNotFoundException
    {	
        try{
            lock.lock();
            boolean diskIsDead = false;
//            System.out.println("Setting Fail Probability: ");
            testDisk.setFailureProb(0, (float)0.0075);
//            System.out.println("Starting testRecovery");
            testDisk = new ADisk(true);
            try{
                    int recoverTrans = testDisk.beginTransaction();
                    testDisk.writeSector(recoverTrans, 1, ALL_ONES);
                    testDisk.writeSector(recoverTrans, 2, ALL_TWOS);
                    testDisk.writeSector(recoverTrans, 3, ALL_THREES);
                    testDisk.writeSector(recoverTrans, 4, ALL_FOURS);
                    testDisk.commitTransaction(recoverTrans);

                    int recoverTrans2 = testDisk.beginTransaction();
                    testDisk.writeSector(recoverTrans2, 5, ALL_ONES);
                    testDisk.writeSector(recoverTrans2, 6, ALL_TWOS);
                    testDisk.writeSector(recoverTrans2, 7, ALL_THREES);
                    testDisk.writeSector(recoverTrans2, 8, ALL_FOURS);
                    testDisk.commitTransaction(recoverTrans2);
            }
            catch (IOException ex)
            {
                    System.out.println("Disk is dead");
                    diskIsDead = true;
            }
            if (diskIsDead)
            {
                    do{
                    try{
                            testDisk = new ADisk(false);
                    }
                    catch (IOException ex2){
                            System.err.println("caught IOExc");
                    }
                    }
                    while  (testDisk == null);
                    diskIsDead = false;
            }
            System.out.println("testRecovery PASSED");
        }
        finally{
            lock.unlock();
        }
    }


    /*
     * Large test of recovery with 32 transactions with 30 writes each 
     */
    public void megaTest() throws IOException, IllegalArgumentException, IndexOutOfBoundsException, ClassNotFoundException
    {
        try
        {
            lock.lock();
            testDisk = new ADisk(true);
            boolean diskIsDead = false;

            testDisk.setFailureProb(0, (float)0.0075);

            try
            {
                int count = 0;
                int test[] = new int[32];
                for(int h = 0; h<32;h++)
                {
                    test[h] = testDisk.beginTransaction();
                    for(int i= 0; i < 30; i++)
                    {
    //                            System.out.println("count = " + count);
                        testDisk.writeSector(test[h],(count++),ALL_SEVENS);
                    }
                    testDisk.commitTransaction(test[h]);
                }
            }

            catch (IOException ex)
            {
                    System.err.println("Disk is dead");
                    diskIsDead = true;
            }

            if(diskIsDead)
            {
                do
                {
                        try
                        {
                                testDisk = new ADisk(false);
                        }
                        catch (IOException ex2)
                        {
                                System.err.println("caught IOExc for creating new disk after painful death");
                        }

                }
                while  (testDisk == null);
                diskIsDead = false;
            }
            System.out.println("megaTest() PASSED. Recovery SUCCESSFUL");
        }
        finally
        {
             lock.unlock();
        }
    }
}
