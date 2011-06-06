
import java.io.IOException;
import java.util.ArrayList;

/**
 *
 * @author Rishi Dewan, Lindsey O'Niell
 */
public class WriteBackThread extends Thread
{
    ADisk adisk;
    CallbackTracker cbt;
    LogStatus logStat;
    //for disk failure testing
    public boolean diskIsDead;

    public WriteBackThread(ADisk adisk, CallbackTracker cbt, LogStatus logStat)
    {
        this.adisk = adisk;
        this.cbt = cbt;
        this.logStat = logStat;
        diskIsDead = false;
    }

    @Override
    public void run()
    {
        while(true){
            try {
                runMe();
            }
            //in case the disk dies
            catch (IOException ex) {
                  System.err.println("WBThread caught " + ex );
                  return;
            }
        }
    }



    public void runMe() throws IOException
    {
        try
        {
            adisk.ADlock.lock();
            //If there are not writes on the WriteBack List
            //and the disk is not dead, the WBThread waits
            while (adisk.WBList.size() == 0 && !diskIsDead)
            {
                adisk.okToDiskWrite.awaitUninterruptibly();
            }
            //If the disk dies, print an error and
            //throw and exception
            if (diskIsDead){
                System.err.println("DISK DIED");
                adisk.okToDiskWrite.signalAll();
                throw new IOException("DISK DIED");
            }
            //Writeback list isn't empty anymore, so pull of the
            //transaction to be written to disk
            Transaction WBTrans = adisk.WBList.getNextWriteback();
            //Try statement in case of disk failure
            try
            {
            //Now, we're actual writing to DISK

            // holds tags to be passed to CallbackTracker.waitForTags()
            ArrayList<Integer> multipleWriteTags = new ArrayList<Integer>();

            //send startRequests for all of the Transaction's writes
            for (int i=0; i < WBTrans.sectorsUpdated.size(); i++)
            {
                    adisk.AD.startRequest(Disk.WRITE, adisk.getTag(), WBTrans.sectorsUpdated.get(i) + 1025, (WBTrans.sectorWrites.get(i)));
                    multipleWriteTags.add(adisk.setTag());
             }
            cbt.waitForTags(multipleWriteTags);
            }
            catch (IllegalArgumentException ex)
            {
                    System.err.println("Illegal Argument Exception in the WriteBackThread");
            }
            catch (IOException ex)
            {
                    //If disk has died, set diskIsDead to true
                    diskIsDead = true;
                    System.err.println("IOException in the WriteBackThread, thrown from startRequest. Cause: DEAD DISK");
                    throw new IOException("IOException in the WriteBackThread, thrown from startRequest. Cause: DEAD DISK");
            }

            //The Writeback is now safely on disk, remove it from the writeback list
            Transaction temp = adisk.WBList.removeNextWriteback();

            try {
                    //this call affects log management
                    logStat.writeBackDone(temp.recallLogSectorStart(), temp.recallLogSectorNSectors());
            }
            catch (IOException ex) {
                    System.out.println("IOException from LogStatus.java; Because disk dead. OK.");
            }

            //Signal that there may be enough room for the log to write again
            // (if it was waiting)
            adisk.okToLog.signalAll();
        }
       finally
            {
                adisk.ADlock.unlock();
            }
    }

}
