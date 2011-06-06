/*
 * Transaction.java
 *
 * List of active transactions.
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2011 Mike Dahlin
 *
 */
import java.io.IOException;
import java.util.ArrayList;

public class Transaction{

    // 
    // You can modify and add to the interfaces
    //

      //state of transaction
      public enum transState {COMMITED, INPROGRESS, ABORTED};
      private transState ts;
      //list of sectors update by transaction
      // Future modification: MAKE KEY/VALUE PAIRS
      public ArrayList<Integer> sectorsUpdated;
      public ArrayList<byte[]> sectorWrites; 
      //transID
      private int transID;

      // array form of our magic header and commit numbers
      static byte commitNum[] = ADisk.intToByteArray(ADisk.COMMIT);
      static byte headerNum[] = ADisk.intToByteArray(ADisk.HEADER);

      private int startPoint;
      private int numSectorsInLog;

      private SimpleLock lock;

    /*
     * Creates a new instance of a transaction.
     */

    public Transaction(int TransID)
    {
        sectorsUpdated = new ArrayList<Integer>();
        sectorWrites = new ArrayList<byte[]>();
        transID = TransID;
        ts = transState.INPROGRESS;
        lock = new SimpleLock();
    }

    //Added method to access transID outside of Transaction.java
    public int getTransID()
    {
        return transID;
    }

    public int getStartPoint(){
        try{
            lock.lock();
            return startPoint;
        }
        finally{
            lock.unlock();
        }
    }

    public int getNumSectorsInLog(){
        try{
            lock.lock();
            return numSectorsInLog;
        }
        finally{
            lock.unlock();
        }
    }

    public void setTransState(transState t)
    {
        try{
            lock.lock();
            ts = t;
        }
        finally{
            lock.unlock();
        }
    }

    public transState getTransState()
    {
        try{
            lock.lock();
            return ts;
        }
        finally{
            lock.unlock();
        }
    }

    /*
     * Returns a list of integers that show which
     * sector numbers on disk are to be updated.
     */

    public ArrayList<Integer> getSectorsUpdated()
    {
        try{
            lock.lock();
            return sectorsUpdated;
        }
        finally{
            lock.unlock();
        }
    }

    /*
     * Returns a list of byte arrays that show
     * the contents of the writes to disk.
     */

    public ArrayList<byte[]> getSectorWrites()
    {
        try{
            lock.lock();
            return sectorWrites;
        }
        finally{
            lock.unlock();
        }
    }


    public void addWrite(int sectorNum, byte buffer[])
    throws IllegalArgumentException, 
           IndexOutOfBoundsException
    {
        try{
            lock.lock();
            if (sectorNum < 0 || sectorNum >= Disk.NUM_OF_SECTORS - 1025)
            {
                throw new IndexOutOfBoundsException("Invalid Sector Number");
            }
            if (buffer.length != Disk.SECTOR_SIZE)
            {
                throw new IllegalArgumentException("Buffer is not size Disk.SECTOR_SIZE");
            }
            // if we already have a write for the specified
            // sectorNum, override it (so reflects most recent update
            int index = sectorsUpdated.indexOf(sectorNum);
            if (index != -1){
                    sectorWrites.set(index, buffer);
            }
            // otherwise, we don't have a write to that sector yet
            else
            {
            if (sectorsUpdated.size() == Common.MAX_WRITES_PER_TRANSACTION){
                    throw new IllegalArgumentException("Already at Maximum number of Transaction Writes");
            }
            //Add the sector number and write to their appropriate lists 
            sectorsUpdated.add(sectorNum);
            sectorWrites.add(buffer);
            }
        }
        finally{
            lock.unlock();
        }


    }

    //
    // Return true if this transaction has written the specified
    // sector; in that case update buffer[] with the written value.
    // Return false if this transaction has not written this sector.
    //
    public boolean checkRead(int sectorNum, byte buffer[])
    throws IllegalArgumentException, 
           IndexOutOfBoundsException
    {
        try{
            lock.lock();
            if (sectorNum < 0 || sectorNum >= Disk.NUM_OF_SECTORS - 1025)
            {
                throw new IndexOutOfBoundsException("Invalid Sector Number");
            }
            if (buffer.length != Disk.SECTOR_SIZE)
            {
                throw new IllegalArgumentException("Buffer is not size Disk.SECTOR_SIZE");
            }

            // check if the transaction has updated the specified sector,
            // write the value to be updated to buffer if it has
            for (int i=0; i < sectorsUpdated.size(); i++)
            {
                if (sectorsUpdated.get(i) == sectorNum)
                {
                    byte[] buff = sectorWrites.get(i);
                    for (int ii =0; ii < buffer.length; ii++)
                    {
                        buffer[ii] = buff[ii];
                    }
                    return true;
                }
            }

            return false;
        }
        finally{
            lock.unlock();
        }
    }
    
    public void commit() throws IOException, IllegalArgumentException
    {
        try{
            lock.lock();
            ts = transState.COMMITED;
        }
        finally{
            lock.unlock();
        }
    }

    public void abort() throws IOException, IllegalArgumentException
    {
        try{
            lock.lock();
            ts = transState.ABORTED;
        }
        finally{
            lock.unlock();
        }
    }

    // 
    // These methods help get a transaction from memory to
    // the log (on commit), get a committed transaction's writes
    // (for writeback), and get a transaction from the log
    // to memory (for recovery).
    //

    //
    // For a committed transaction, return a byte
    // array that can be written to some number
    // of sectors in the log in order to place
    // this transaction on disk. Note that the
    // first sector is the header, which lists
    // which sectors the transaction updaets
    // and the last sector is the commit. 
    // The k sectors between should contain
    // the k writes by this transaction.
    //
    public byte[] getSectorsForLog() throws IOException{
        try{
            lock.lock();
            return createByteArray();
        }
        finally{
            lock.unlock();
        }
    }

    byte[] createByteArray() throws IOException
    {
        try{
              lock.lock();

              byte header[] = new byte[0];
              byte commit[] = new byte[0];

              header = ADisk.concat(header, headerNum);

              byte transIDBA[] = ADisk.intToByteArray(transID);
              header = ADisk.concat(header, transIDBA);

              //number of sectors
              int numSectorWritesInt = sectorWrites.size();
              byte numSectorWrites[] = ADisk.intToByteArray(numSectorWritesInt);
              header = ADisk.concat(header, numSectorWrites);

              // create a byte array of all of the sector numbers to be updated
              byte sectorUpdatesBA[] = new byte[0];
              for (int i=0; i < sectorsUpdated.size(); i++)
              {
                      byte[] temp = ADisk.intToByteArray(sectorsUpdated.get(i));
                      sectorUpdatesBA = ADisk.concat(sectorUpdatesBA, temp);
              }
              //concatenate them onto the header
              header = ADisk.concat(header, sectorUpdatesBA);

              //byte array of all sector writes concatenated together
              byte sectorWritesTotal[] = new byte[0];
              for (int i = 0; i < sectorWrites.size(); i++)
              {
                    byte sectorWritesBA[] = sectorWrites.get(i);
                    sectorWritesTotal = ADisk.concat(sectorWritesTotal, sectorWritesBA);
              }

              //This will be concatenated on to the rest of the head to make it
              //the size of a sector
              byte fillInHeader[] = new byte[Disk.SECTOR_SIZE - 12 - sectorsUpdated.size()*4];
              byte fillInCommit[] = new byte[Disk.SECTOR_SIZE - 8];

              header = ADisk.concat(header, fillInHeader);
              commit = ADisk.concat(commit, commitNum);
              commit = ADisk.concat(commit, transIDBA);
              commit = ADisk.concat(commit, fillInCommit);

              //complete concatenation of the entire transaction byte array
              byte[] everything;
              everything = ADisk.concat(header, sectorWritesTotal);
              everything = ADisk.concat(everything, commit);
              return everything;
        }
        finally{
            lock.unlock();
        }
    }

    //
    // You'll want to remember where a Transactions
    // log record ended up in the log so that
    // you can free this part of the log when
    // writeback is done.
    //
    public void rememberLogSectors(int start, int nSectors){
        try{
            lock.lock();
            startPoint = start;
            numSectorsInLog = nSectors;
        }
        finally{
            lock.unlock();
        }
    }

    public int recallLogSectorStart(){
        try{
            lock.lock();
            return startPoint;
        }
        finally{
            lock.unlock();
        }
    }

    public int recallLogSectorNSectors(){
        try{
            lock.lock();
            return numSectorsInLog;
        }
        finally{
            lock.unlock();
        }

    }

    //
    // For a committed transaction, return
    // the number of sectors that this
    // transaction updates. Used for writeback.
    //
    public int getNUpdatedSectors(){
        try{
            lock.lock();
            return sectorsUpdated.size();
        }
        finally{
            lock.unlock();
        }
    }

    //
    // For a committed transaction, return
    // the sector number and body of the
    // ith sector to be updated. Return
    // a secNum and put the body of the
    // write in byte array. Used for writeback.
    //
    public int getUpdateI(int i, byte buffer[]){
        try{
            lock.lock();
            //write the write into buffer
            byte[] buff = sectorWrites.get(i);
            for (int ii =0; ii < buffer.length; ii++)
            {
                buffer[ii] = buff[ii];
            }
            return sectorsUpdated.get(i);
        }
        finally{
            lock.unlock();
        }
   }

    
    //
    // Parse a sector from the log that *may*
    // be a transaction header. If so, return
    // the total number of sectors in the
    // log that should be read (including the
    // header and commit) to get the full transaction.
    //
    public static int parseHeader(byte buffer[])
    {
          //grab what should be a header
	  byte[] hNum = new byte[4];
	  System.arraycopy(buffer, 0, hNum, 0, 4);
	  int headerNumber = ADisk.byteArrayToInt(hNum);

          // If didn't find a header, return -1
          if (headerNumber != ADisk.HEADER){
              return -1;
	  }

          //grab the number of sectors 
  	  byte[] secNum = new byte[4];
	  System.arraycopy(buffer, 8, secNum, 0, 4);
	  int secNumfromBA = ADisk.byteArrayToInt(secNum);

          //2 = 1 for header + 1 for commit
	  int numSectors = 2 + secNumfromBA;
          return numSectors;
        }

    //
    // Parse k+2 sectors from disk (header + k update sectors + commit).
    // If this is a committed transaction, construct a
    // committed transaction and return it. Otherwise
    // throw an exception or return null.
    public static Transaction parseLogBytes(byte buffer[]){

          //grab the magic header number
          byte[] hNum = new byte[4];
	  System.arraycopy(buffer, 0, hNum, 0, 4);
	  int headerNumber = ADisk.byteArrayToInt(hNum);
//	  System.out.println("headerNum = " + headerNumber);

          //grab the transaction id from the header
	  byte[] tIDhead = new byte[4];
	  System.arraycopy(buffer, 4, tIDhead, 0, 4);
	  int tIDheadfromBA = ADisk.byteArrayToInt(tIDhead);
//	  System.out.println("transID from header= " + tIDheadfromBA);

          //grab the number of sectors
	  byte[] secNum = new byte[4];
	  System.arraycopy(buffer, 8, secNum, 0, 4);
	  int secNumfromBA = ADisk.byteArrayToInt(secNum);
//	  System.out.println("secNum = " + secNumfromBA);

          //grab all of the sector numbers updated, add them to an ArrayList
	  ArrayList<Integer> secUpdatedfromBA = new ArrayList<Integer>();
	  for (int i = 0; i < secNumfromBA; i++)
	  {
              byte[] secToUpdateNum = new byte[4];
              System.arraycopy(buffer, 12 + i*4, secToUpdateNum, 0, 4);
              secUpdatedfromBA.add(ADisk.byteArrayToInt(secToUpdateNum));
	  }

          //grab all of the sector writes, add them to an ArrayList
	  ArrayList<byte[]> sectorsWrittenfromBA = new ArrayList<byte[]>();
	  for (int i = 0; i < secNumfromBA; i++)
	  {
              byte[] sectorsWritten = new byte[Disk.SECTOR_SIZE];
              System.arraycopy(buffer, 512+(i*Disk.SECTOR_SIZE), sectorsWritten, 0, Disk.SECTOR_SIZE);
              sectorsWrittenfromBA.add(sectorsWritten);
          }

//	  for (int i = 0; i < sectorsWrittenfromBA.size(); i++)
//	  {
//              System.out.println("element " + i + " = " + sectorsWrittenfromBA.get(i));
//	  }

          //grab the magic commit number
	  byte[] comNum = new byte[4];
	  System.arraycopy(buffer, (512+secNumfromBA*Disk.SECTOR_SIZE), comNum, 0, 4);
	  int comNumfromBA = ADisk.byteArrayToInt(comNum);
          //if not magic commit number, return -1
          if (comNumfromBA != ADisk.COMMIT){
              return null;
          }
//	  System.out.println("commitNum = " + comNumfromBA);

          //grab the transaction id from the commit
	  byte[] tIDcom = new byte[4];
	  System.arraycopy(buffer, (512+4+secNumfromBA*Disk.SECTOR_SIZE), tIDcom, 0, 4);
	  int tIDcomfromBA = ADisk.byteArrayToInt(tIDcom);
//	  System.out.println("transID from commit = " + tIDcomfromBA);

          //create a new transaction
	  Transaction retTrans = new Transaction(tIDcomfromBA);

          //Initialize the transaction
	  retTrans.ts = transState.COMMITED;
	  for (int i = 0; i < sectorsWrittenfromBA.size(); i++)
	  {
            retTrans.sectorWrites.add(sectorsWrittenfromBA.get(i));
	  }

	  for (int i=0; i < secUpdatedfromBA.size(); i++)
	  {
            retTrans.sectorsUpdated.add(secUpdatedfromBA.get(i));

	  }
          return retTrans;
    }
    
    
}


