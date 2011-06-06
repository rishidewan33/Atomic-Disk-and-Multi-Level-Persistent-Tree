/*
 * ADisk.java
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2007, 2010 Mike Dahlin
 *
 */
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.concurrent.locks.Condition;

public class ADisk implements Serializable
{

  //-------------------------------------------------------
  // The size of the redo log in sectors
  //-------------------------------------------------------
  public static final int REDO_LOG_SECTORS = 1024;
  public static final int HEADER = 666666;
  public static final int COMMIT = 696969;

  public ActiveTransactionList ActTransList;
  public WriteBackList WBList;
  public CallbackTracker callback;
  public Disk AD;
  public SimpleLock ADlock;
  public LogStatus logStat;
  public Condition okToDiskWrite;
  public Condition okToLog;
  public Condition writeCP;
  public int totalAvailableSectors; // need to update this when WB thread returns
  public int tag;
  private int transIDCount;
  public WriteBackThread WBThread;

  //-------------------------------------------------------
  //
  // Allocate an ADisk that stores its data using
  // a Disk.
  //
  // If format is true, wipe the current disk
  // and initialize data structures for an empty 
  // disk.
  //
  // Otherwise, initialize internal state, read the log, 
  // and redo any committed transactions. 
  //
  //-------------------------------------------------------
  public ADisk (boolean format) throws FileNotFoundException, IllegalArgumentException, IOException
  {
      ADlock = new SimpleLock();
      callback = new CallbackTracker();
      ActTransList = new ActiveTransactionList();
      WBList = new WriteBackList();
      AD = new Disk(callback);
      logStat = new LogStatus(this);
      tag = 0;
      transIDCount = 0;
      totalAvailableSectors = Disk.NUM_OF_SECTORS - Disk.ADISK_REDO_LOG_SECTORS;
      okToDiskWrite = ADlock.newCondition();
      okToLog = ADlock.newCondition();
      WBThread = new WriteBackThread(this, callback, logStat);
      WBThread.start();

      //To format disk, fill the entire disk with zeroes.
      if (format == true)
      {
          System.out.println("Formatting...This may take a second.");
          ArrayList formatTags = new ArrayList();
          byte[] clearLog = new byte[Disk.SECTOR_SIZE];
          for (int i = 0; i < Disk.NUM_OF_SECTORS; i++){
              try {
                  AD.startRequest(Disk.WRITE, getTag(), i, clearLog.clone());
              } catch (IOException iOException) {
                  throw new IOException("Disk is dead.");
              }
              formatTags.add(setTag());
          }
          callback.waitForTags(formatTags);
          System.out.println("Done formatting.");
      }

      //if not formatting, perform recovery in case of a crash
      else{

          //Pull the log meta data off the disk
          byte[] logMetaData = new byte[Disk.SECTOR_SIZE];
          try{
          AD.startRequest(Disk.READ, getTag(), LogStatus.RESERVED_LOG_SEC, logMetaData);
          }
          catch (IOException ex){
              throw new IOException("Disk is dead.");
          }
          callback.waitForTag(setTag());
          int tempTag = getTag();
		  //get the checkpoint
		  byte CP[] = new byte[Disk.SECTOR_SIZE];
		  System.arraycopy(logMetaData, 0, CP, 0, 4);
          int checkPoint = byteArrayToInt(CP);
		  //get the head
		  byte head[] = new byte[Disk.SECTOR_SIZE];
		  System.arraycopy(logMetaData, 4, head, 0, 4);
		  int logHead = byteArrayToInt(head);
		  //get the total available log sectors
		  byte availLogSectors[] = new byte[Disk.SECTOR_SIZE];
		  System.arraycopy(logMetaData, 8, availLogSectors, 0, 4);
		  int availLogSec = byteArrayToInt(availLogSectors);
          //The checkpoint tells us where to start reading for recovery
		  //the logHead tells us when to stop reading
		  //the available log sectors are for the special case when CP = head (empty or full
          recovery(checkPoint, logHead, availLogSec, tempTag);
      }  
  }

  /*
   * Perform recovery on the disk
   */
  public void recovery(int checkpoint, int head, int availLogSec, int tempTag) throws IllegalArgumentException, IOException
  {
      try{
         ADlock.lock();
         int tempCheckpoint = checkpoint;
         //to tell the new log how many sectors are in use from transactions
         //currently on the writeback list
         int totalSectorsInUse = 0;
         int counter = 0;
		 int totalSecToGo = Disk.ADISK_REDO_LOG_SECTORS;
		 //calculate how many sectors we have to go:
		 if (tempCheckpoint > head)
		 {
			 int temp = Disk.ADISK_REDO_LOG_SECTORS - tempCheckpoint;
			 totalSecToGo = temp + head;
		 }
		 else if (head > tempCheckpoint)
		 {
			 totalSecToGo = head - tempCheckpoint;
		 }
		 //If get here, then CP == head, Check
		 //availLogSec to see if full or empty.
		 // if availLogSec != 0, log is empty, return. No need for recovery
		 else if ( head == tempCheckpoint && availLogSec != 0){
			 return;
		 }
		 //So, log is full. totalSecToGo initialized to total number of log sectors

         while (counter < totalSecToGo){
             //Read the first sector the checkpoint points to
              byte[] iHopeHeader = new byte[Disk.SECTOR_SIZE];
              try{
                    AD.startRequest(Disk.READ, getTag(), checkpoint, iHopeHeader);
              }
              catch (IOException ex){
                    throw new IOException("Disk is dead.");
              }
              callback.waitForTag(setTag());

              //grab (what we hope is) the header
              int numSectors = Transaction.parseHeader(iHopeHeader);

              //If CP not pointing to valid header, stop recovery
              if (numSectors == -1)
              {
                  return;
              }

              //If here, found a header.
              //grab the whole transaction off of disk and store
              //it as a big byte array
              byte[] wholeTrans = new byte[0];
              for (int i =0; i < numSectors; i++)
              {
                  byte[] temp = new byte[Disk.SECTOR_SIZE];
                  try{
                  AD.startRequest(Disk.READ, getTag(), checkpoint + i, temp);
                  }
                  catch (IOException ex){
                      throw new IOException("Disk is dead.");
                  }
                  callback.waitForTag(setTag());
                  wholeTrans = concat(wholeTrans, temp);
              }

              //Parse your byte array into a transaction
              Transaction logTrans;
              logTrans = Transaction.parseLogBytes(wholeTrans);

              // if logTrans == null, there was no commit for the
              // transaction. Break out of recovery
              if (logTrans == null)
              {
                  return;
              }

              //remember log sectors so we can update totalSectorsAvailableInLog
              logTrans.rememberLogSectors(checkpoint, numSectors);

              //add the transaction to the writeback list, so it
              //goes to its proper location on disk
              WBList.addCommitted(logTrans);

              totalSectorsInUse += numSectors;
              //increment your counter to check next sector
              counter += numSectors;
              checkpoint = (checkpoint + numSectors)%Disk.ADISK_REDO_LOG_SECTORS;
        }
        //set the number of available log sectors in LogStatus
        logStat.recoverySectorsInUse(tempCheckpoint, totalSectorsInUse);
    }
    finally{
          ADlock.unlock();
    }
  }

  // return the current tag value
  public int getTag()
  {
      try{
          ADlock.lock();
          return tag;
      }
      finally{
          ADlock.unlock();
      }
  }

  // increment the tag number
  public int setTag()
  {
      try{
          ADlock.lock();
          return tag++;
      }
      finally{
          ADlock.unlock();
      }
  }

  //set the failure probability of the disk 
  public void setFailureProb(int countdown, float prob)
  {
	  AD.setFailProb(countdown, prob);
  }

  //concatenate two byte arrays
  //return the newly concatenated array 
  static byte[] concat(byte[] A, byte[] B)
  {
    byte[] C = new byte[A.length+B.length];
    System.arraycopy(A, 0, C, 0, A.length);
    System.arraycopy(B, 0, C, A.length, B.length);
    return C;
  }

  //converts and int to a byte array
  public static byte[] intToByteArray(int value)
  {
        return new byte[] {(byte)(value >>> 24),(byte)(value >>> 16),(byte)(value >>> 8),(byte)value};
  }

  //converts a byte array to an int 
  public static int byteArrayToInt(byte [] b)
  {
        return (b[0] << 24)+((b[1] & 0xFF) << 16)+((b[2] & 0xFF) << 8)+(b[3] & 0xFF);
  }


  //-------------------------------------------------------
  //
  // Return the total number of data sectors that
  // can be used *not including space reseved for
  // the log or other data sructures*. This
  // number will be smaller than Disk.NUM_OF_SECTORS.
  //
  //-------------------------------------------------------
  public int getNSectors()
  {
    return totalAvailableSectors;
  }

  //-------------------------------------------------------
  //
  // Begin a new transaction and return a transaction ID
  //
  //-------------------------------------------------------
  public int beginTransaction()
  {
      try
      {
          ADlock.lock();
          int retTransID = transIDCount++;
          // create a new transaction
          Transaction t = new Transaction(retTransID);
          //Put the transaction on the Active Transaction List
          ActTransList.put(t);
          //return the TID of the transaction
          return retTransID;
      }
      finally
      {
          ADlock.unlock();
      }

  }

  //-------------------------------------------------------
  //
  // First issue writes to put all of the transaction's
  // writes in the log.
  //
  // Then issue a barrier to the Disk's write queue.
  //
  // Then, mark the log to indicate that the specified
  // transaction has been committed. 
  //
  // Then wait until the "commit" is safely on disk
  // (in the log).
  //
  // Then take some action to make sure that eventually
  // the updates in the log make it to their final
  // location on disk. Do not wait for these writes
  // to occur. These writes should be asynchronous.
  //
  // Note: You must ensure that (a) all writes in
  // the transaction are in the log *before* the
  // commit record is in the log and (b) the commit
  // record is in the log before this method returns.
  //
  // Throws 
  // IOException if the disk fails to complete
  // the commit or the log is full.
  //
  // IllegalArgumentException if tid does not refer
  // to an active transaction.
  // 
  //-------------------------------------------------------
  public void commitTransaction(int tid) throws IOException, IllegalArgumentException
  {
      try
      {
          ADlock.lock();

          //Get the transaction off of the Active Transaction List,
          //searching by TID
          Transaction t = ActTransList.remove(tid);

          // if can't find the Transaction, t == null
          if(t==null)
          {
              throw new IllegalArgumentException("Transaction not found in ActiveTransactionList");
          }

          // The number of sectors writes per transaction
          // (+ 2) is because of header and commit
          int numSecTranWrites = t.getNUpdatedSectors() + 2;

          // We must wait to write to the log if it there isn't
          // enough room for the full transaction
          while (logStat.totalSectorsAvailableInLog < numSecTranWrites)
          {
                okToLog.awaitUninterruptibly();
		  }

          //Set transaction state to COMMITED
          t.commit();

          //Store how many sectors the transaction takes up on the log
          // (we are not using the first variable (start sector)
          t.rememberLogSectors(0, numSecTranWrites);

          // Grab the head pointer of the log
          int currTempHead = logStat.headptr;
          int endtempHead = logStat.reserveLogSectors(numSecTranWrites); //After writing Transaction to log, this is the sector the head should point to.

		  //after moved in memory log head, update on disk log head in case
		  //of crash during writing this transaction
		  byte writeLogMD[] = logStat.metaDataToByteArray();
		  //write CP, head, and totalSectorsAvailableInLog to disk
		  AD.startRequest(Disk.WRITE, getTag(), LogStatus.RESERVED_LOG_SEC, writeLogMD);
		  callback.waitForTag(setTag());


          // turn the transaction into a byte array 
          byte[] transactionToByteArray = t.createByteArray();

          //Write the header to log
          byte header[] = new byte[Disk.SECTOR_SIZE];
          System.arraycopy(transactionToByteArray, 0, header, 0, Disk.SECTOR_SIZE);

          //We don't need to wait for the header and transaction sector
          //writes to get to disk, we have a barrier for that.
          ArrayList<Integer> dontWaitForTags = new ArrayList<Integer>();
          try
          {
                AD.startRequest(Disk.WRITE, getTag(), currTempHead, header);
          }
          //If the disk has died, startRequest may throw an exception, which
          // we re-throw for our testing to catch 
          catch (IOException ex){
                throw new IOException("Disk is dead.");
          }
          //add the current tag to the dontWait array to be sent to
          //dontWaitForTags
          dontWaitForTags.add(setTag());

          //Increase the temporary headptr used for writing to log
          currTempHead=(currTempHead+1)%Disk.ADISK_REDO_LOG_SECTORS;

          //send startRequests (write to log) for all of the Transaction's writes
          for (int i=0; i < t.sectorsUpdated.size(); i++)
          {
              try{
              AD.startRequest(Disk.WRITE, getTag(), currTempHead, (t.sectorWrites.get(i)));
              }
              catch (IOException ex){
                throw new IOException("Disk is dead.");
          }
              currTempHead=(currTempHead+1)%Disk.ADISK_REDO_LOG_SECTORS;
              dontWaitForTags.add(setTag());
          }
          //Call dontWaitForTags on the tags in the dontWaitForTags ArrayList
          callback.dontWaitForTags(dontWaitForTags);

          //Add a barrier so that everything hits disk BEFORE commit does
          AD.addBarrier();

          //write the commit to the log
          byte commit[] = new byte[Disk.SECTOR_SIZE];
          System.arraycopy(transactionToByteArray, (Disk.SECTOR_SIZE + t.sectorsUpdated.size()*Disk.SECTOR_SIZE), commit, 0, Disk.SECTOR_SIZE);

          try{
                AD.startRequest(Disk.WRITE, getTag(), currTempHead, commit);
          }
          catch (IOException ex){
                throw new IOException("Disk is dead.");
          }

          //If the disk fails, the callback tracker will return null from waitForTag
          // throw the exception for testing to catch
          if (callback.waitForTag(setTag()) == null){
            throw new IOException("Disk is dead.");
          }

          //make sure you completed all of the writes correctly
          currTempHead=(currTempHead+1)%Disk.ADISK_REDO_LOG_SECTORS;
          assert currTempHead == endtempHead;

          //Add it to the writeback list
          WBList.addCommitted(t);
          
          //Signal WBThread to start working!
          okToDiskWrite.signal();
     }
     finally
     {
           ADlock.unlock();
     }

  }

  //-------------------------------------------------------
  //
  // Free up the resources for this transaction without
  // committing any of the writes.
  //
  // Throws 
  // IllegalArgumentException if tid does not refer
  // to an active transaction.
  // 
  //-------------------------------------------------------
  public void abortTransaction(int tid) throws IllegalArgumentException, IOException
  {
      //Assumption: this only applies to Transactions on the ATL, not ones
      //that have been commited to the log
      try
      {
          ADlock.lock();
	  if (tid > transIDCount || tid < 0){
		  throw new IllegalArgumentException("Invalid TID");
          }
          Transaction t = ActTransList.get(tid);
	  if (t == null){
		  throw new IllegalArgumentException("Does not refer to a transaction on the Active Transaction List");
	  }
      //remove the transaction from the Active Transaction List 
      ActTransList.remove(tid);
      }
      finally
      {
          ADlock.unlock();
      }
  }


  //-------------------------------------------------------
  //
  // Read the disk sector numbered sectorNum and place
  // the result in buffer. Note: the result of a read of a
  // sector must reflect the results of all previously
  // committed writes as well as any uncommitted writes
  // from the transaction tid. The read must not
  // reflect any writes from other active transactions
  // or writes from aborted transactions.
  //
  // Throws 
  // IOException if the disk fails to complete
  // the read.
  //
  // IllegalArgumentException if tid does not refer
  // to an active transaction or buffer is too small
  // to hold a sector.
  // 
  // IndexOutOfBoundsException if sectorNum is not
  // a valid sector number
  //
  //-------------------------------------------------------
  public void readSector(int tid, int sectorNum, byte buffer[])
    throws IOException, IllegalArgumentException, 
    IndexOutOfBoundsException
  {
      try
      {
          ADlock.lock();
          if (buffer.length < Disk.SECTOR_SIZE){
              throw new IllegalArgumentException("The buffer is smaller than SECTOR_SIZE.");
          }
          if (sectorNum < 0 || sectorNum >= Disk.NUM_OF_SECTORS - 1025){
              throw new IndexOutOfBoundsException("sectorNum is not a valid sector number.");
          }
          if (tid > transIDCount || tid < 0){
                  throw new IllegalArgumentException("Invalid TID");
          }

          //keep track of whether a certain case has successfully filled the buffer,
          //i.e. completed the specified read
          boolean bufferFilled = false;

          // FIRST, check the Active Transaction List
          //pull up transaction from the ATL.
          Transaction t = ActTransList.get(tid);
          if (t != null)
          {
                //If t isn't null, then we'll check to see if it's writing to sectorNum.
                //If it is, fill the buffer with what it intends to write to the sector and return.
                for (int i=0; i < t.sectorsUpdated.size(); i++)
                {
                    if (sectorNum == t.sectorsUpdated.get(i))
                    {
                        byte[] buff = t.sectorWrites.get(i);
                        for (int ii =0; ii < buffer.length; ii++)
                        {
                                buffer[ii] = buff[ii];
                        }
                    bufferFilled = true;
                    }
                }
                if (bufferFilled){
                    return;
                }
          }

          //If not in Active Transaction List, check the WB List.
          //checkRead takes care of filling the buffer, and returns
          //false if it didn't find the specified sectorNum
          if (WBList.checkRead(sectorNum, buffer))
          {
                  return;
          }

          //If haven't found the sectorNum yet, read it from disk
          //The sectorNum+1025 is our mapping so that the user doesn't
          //know what sectors our log is stored in
          try{
                AD.startRequest(Disk.READ, getTag(), sectorNum + 1025, buffer);
          }
          catch (IOException ex){
                throw new IOException("Disk is dead.");
          }
          callback.waitForTag(setTag());
          }

      finally
      {
          ADlock.unlock();
      }
  }

  //-------------------------------------------------------
  //
  // Buffer the specified update as part of the in-memory
  // state of the specified transaction. Don't write
  // anything to disk yet.
  //  
  // Concurrency: The final value of a sector
  // must be the value written by the transaction that
  // commits the latest.
  //
  // Throws 
  // IllegalArgumentException if tid does not refer
  // to an active transaction or buffer is too small
  // to hold a sector.
  // 
  // IndexOutOfBoundsException if sectorNum is not
  // a valid sector number
  //
  //-------------------------------------------------------
  public void writeSector(int tid, int sectorNum, byte buffer[]) throws IllegalArgumentException,IndexOutOfBoundsException,IOException,ClassNotFoundException
  {
      try
      {
          ADlock.lock();
          //pull the specified transaction off of the Active Transaction List
          Transaction t = ActTransList.get(tid);
          if (t==null)
          {
              throw new IllegalArgumentException("Transaction not found in ActiveTransactionList");
          }
          //Add the give write to the list of writes for that transaction 
          t.addWrite(sectorNum, buffer);
      }
      finally
      {
          ADlock.unlock();
      }
  }
}