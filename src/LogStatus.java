
import java.io.IOException;
import java.util.concurrent.locks.Condition;

/*
 * LogStatus.java
 *
 * Keep track of where head of log is (where should next
 * committed transaction go); where tail is (where
 * has write-back gotten); and where recovery
 * should start.
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2011 Mike Dahlin
 *
 */
public class LogStatus{

    public int headptr;
    public int tailptr;
    public int checkpoint;
    public static final int RESERVED_LOG_SEC = 1024;
    private ADisk adisk;
    private SimpleLock lock;
    public Condition waitHead;
    public int totalSectorsAvailableInLog;
    
    public LogStatus(ADisk Adisk)
    {
        headptr = 0;
        tailptr = 0;
        checkpoint = 0;
        adisk = Adisk;
        lock = new SimpleLock();
        waitHead = lock.newCondition();
        totalSectorsAvailableInLog = Disk.ADISK_REDO_LOG_SECTORS;
    }


    //
    // Return the index of the log sector where
    // the next transaction should go.
    //
    public int reserveLogSectors(int nSectors)
    {
        try{
                lock.lock();
                //need to check whether adding a transaction will fit,
                //so we store a temporary header (to be returned)
                //(Modulus is for log circularity)
                int tempHeadptr = (headptr + nSectors) % Disk.ADISK_REDO_LOG_SECTORS;
                //If not enough room for a transaction, the head must wait
                while (totalSectorsAvailableInLog  < nSectors){
                    waitHead.awaitUninterruptibly();
                }
                headptr = tempHeadptr;
                //Decrease the total number of available sectors in the log
                totalSectorsAvailableInLog -= nSectors;
                return tempHeadptr;
        }
        finally{
                lock.unlock();
        }
    }
  
    //
    // The write back for the specified range of
    // sectors is done. These sectors may be safely 
    // reused for future transactions. (Circular log)
    //
    public int writeBackDone(int startSector, int nSectors) throws IOException
    {
        try{
                lock.lock();
                //Set the tail ptr to reflect the newly written to disk transaction
                tailptr = (tailptr + nSectors) % Disk.ADISK_REDO_LOG_SECTORS;
                checkpoint = tailptr;
                //increase the total available log sectors 
                totalSectorsAvailableInLog += nSectors;
				//create a byte array of meta data to write to disk 
				byte writeLogMD[] = metaDataToByteArray();
				//write CP, head, and totalSectorsAvailableInLog to disk
				adisk.AD.startRequest(Disk.WRITE, adisk.getTag(), RESERVED_LOG_SEC, writeLogMD);
                adisk.callback.waitForTag(adisk.setTag());

                //Signal head to go!
                waitHead.signalAll();
                return tailptr;
        }
        finally{
                lock.unlock();
        }
    }

	public byte[] metaDataToByteArray()
	{
		//Create a byte array of the CP
		byte tempCP[] = new byte[4];
		byte CP[] = ADisk.intToByteArray(checkpoint);
		System.arraycopy(CP, 0, tempCP, 0, 4);
		//Create a byte array of the number of avaiable log sectors
		byte tempAvailSecInLog[] = new byte[4];
		byte totAvailSec[] = ADisk.intToByteArray(totalSectorsAvailableInLog);
		System.arraycopy(totAvailSec, 0, tempAvailSecInLog, 0, 4);
		//Create a byte array of the head
		byte tempHead[] = new byte[4];
		byte head[] = ADisk.intToByteArray(headptr);
		System.arraycopy(head, 0, tempHead, 0, 4);
		//Create a byte array with CP, head, and totalSectorsAvailableInLog
		tempCP = ADisk.concat(tempCP, head);
		tempCP = ADisk.concat(tempCP, tempAvailSecInLog);
		byte fillRest[] = new byte[Disk.SECTOR_SIZE - 12];
		tempCP = ADisk.concat(tempCP, fillRest);
		return tempCP;

	}

    //
    // During recovery, we need to initialize the
    // LogStatus information with the sectors 
    // in the log that are in-use by committed
    // transactions with pending write-backs
    //

    public void recoverySectorsInUse(int startSector, int nSectors)
    {
        try{
                lock.lock();
                //Upon recovery, set head and tail the same
                headptr = (startSector + nSectors)%Disk.ADISK_REDO_LOG_SECTORS;
                tailptr = startSector;
                checkpoint = tailptr;
                //update on-disk CP here, just in case
                byte tempCP[] = new byte[Disk.SECTOR_SIZE];
                byte CP[] = ADisk.intToByteArray(checkpoint);
                System.arraycopy(CP, 0, tempCP, 0, 4);
                totalSectorsAvailableInLog -= nSectors;
        }
        finally{
                lock.unlock();
        }
    }

    //
    // On recovery, find out where to start reading
    // log from. LogStatus should reserve a sector
    // in a well-known location. (Like the log, this sector
    // should be "invisible" to everything above the
    // ADisk interface.) You should update this
    // on-disk information at appropriate times.
    // Then, on recovery, you can read this information 
    // to find out where to start processing the log from.
    //
    // NOTE: You can update this on-disk info
    // when you finish write-back for a transaction. 
    // But, you don't need to keep this on-disk
    // sector exactly in sync with the tail
    // of the log. It can point to a transaction
    // whose write-back is complete (there will
    // be a bit of repeated work on recovery, but
    // not a big deal.) On the other hand, you must
    // make sure of three things: (1) it should always 
    // point to a valid header record; (2) if a 
    // transaction T's write back is not complete,
    // it should point to a point no later than T's
    // header; (3) reserveLogSectors must block
    // until the on-disk log-start-point points past
    // the sectors about to be reserved/reused.
    //
    // *****We're not using this.*****
    public int logStartPoint() throws IllegalArgumentException, IOException{
        return RESERVED_LOG_SEC;
    }
}