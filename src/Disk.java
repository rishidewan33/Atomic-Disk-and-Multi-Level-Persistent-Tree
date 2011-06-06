/*
 * Disk.java
 *
 * A fake asynchronous disk.
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2007,2010 Mike Dahlin
 *
 */
import java.io.RandomAccessFile;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.LinkedList;
import java.util.concurrent.locks.Condition;
import java.util.Random;
import java.util.ListIterator;

public class Disk{
  public static final int NUM_OF_SECTORS = 16384;
  public static final int SECTOR_SIZE = 512;
  public static final int ADISK_REDO_LOG_SECTORS = 1024;

  /* Operation types */
  public static final int READ = 19432;
  public static final int WRITE = 43255;
  public static final int BARRIER = 134219;

  private static final String PATH = "DISK.dat";

  private RandomAccessFile file;
  private LinkedList<DiskResult> pending;
  private SimpleLock lock;
  private Condition workReady;

  //
  // For debugging, you can make the disk "fail"
  // with some specified probability per request
  // starting after some number of requests in the future.
  //
  private float failureProb;
  private int failureCountDown;
  private Random rand;
  private boolean diskIsDead;

  //-------------------------------------------------------
  // Disk
  //-------------------------------------------------------
  public Disk(DiskCallback callback)
  throws FileNotFoundException
  {
    DiskWorker dw;

    this.file = new RandomAccessFile(PATH, "rws");
    this.pending = new LinkedList<DiskResult>();
    this.lock = new SimpleLock();
    this.workReady = lock.newCondition();
    this.rand = new Random();
    this.diskIsDead = false;
    this.failureProb = (float)0.0;
    this.failureCountDown = -1;

    dw = new DiskWorker(this, file, callback);
    dw.start();
  }


  //-------------------------------------------------------
  // Update the failure probability for testing
  //-------------------------------------------------------
    public void setFailProb(int countDown, float failProb)
  {
      try{
          lock.lock();
          this.failureCountDown = countDown;
          this.failureProb = failProb;
      }
      finally{
          lock.unlock();
      }
  }

  //-------------------------------------------------------
  // startRequest -- enqueue a read or write request. 
  // Callback will be called when it completes. 
  //
  // WARNING -- this call passes ownership of b to
  // Disk. Caller must not touch b until we return
  // it via callback (or disk fails.)
  //-------------------------------------------------------
  public void startRequest(int operation, int tag, int sectorNum, byte b[])
    throws IllegalArgumentException, IOException
  {
    DiskResult dr;
    try{
      lock.lock();
      if(diskIsDead){
        throw new IOException("Disk is dead");
      }
      if(sectorNum < 0 || sectorNum >= NUM_OF_SECTORS){
        throw new IllegalArgumentException("Bad sector number");
      }
      if(b == null || b.length < SECTOR_SIZE){
        throw new IllegalArgumentException("Bad buffer");
      }
      if(operation != READ && operation != WRITE){
        throw new IllegalArgumentException("Bad operation");
      }
      if(tag == DiskResult.RESERVED_TAG){
        throw new IllegalArgumentException("Reserved tag");
      }
      dr = new DiskResult(operation, tag, sectorNum, b);
      pending.addLast(dr);
      workReady.signal();
      return;
    }
    finally{
      lock.unlock();
    }
  }

    //
    // Put a BARRIER marker onto queue. Reads can pass barrier but writes cannot
    //
    public void addBarrier()
        throws IOException
    {
        DiskResult dr;
        try{
            lock.lock();
            if(diskIsDead){
                throw new IOException("Disk is dead");
            }
            dr = new DiskResult(BARRIER, DiskResult.RESERVED_TAG, 0, null);
            pending.addLast(dr);
        }
        finally{
            lock.unlock();
        }
    }

  //-------------------------------------------------------
  // getWork() -- used by worker thread. Block until a
  // read or write request needs attention.
  //-------------------------------------------------------
  public DiskResult getWork()
    throws IOException
  {
    int skip;
    DiskResult dr;

    try{
      lock.lock();


      //Mike's version:
//      while(!diskIsDead && pending.size() == 0){
//        workReady.awaitUninterruptibly();
//      }

      // Our change (to account for Barrier error)
      while(!diskIsDead && (pending.size() == 0 || (pending.size() == 1 && pending.getFirst().getOperation() == BARRIER))){
       workReady.awaitUninterruptibly();
     }
      
      randomlyKillDisk();

      if(diskIsDead){
        throw new IOException("Disk is dead");
      }

      //
      // Pull a random-ish item off the list. Use random
      // to test non-fifo list...
      //
      // Note
      //  -- if first item is barrier, remove it
      //  -- writes cannot pass barrier -- if random item selected is a write past
      //     a barrier, then just take first item on list
      //  -- reads can pass barrier
      //

      //
      // Remove any barriers at front of list
      //
      while(pending.getFirst().getOperation() == BARRIER){
          pending.removeFirst();
      }

      skip = 0;
      if(pending.size() > 1){
        skip = rand.nextInt(pending.size() - 1); 
      }
      dr = pending.get(skip);

      // Reads can pass barrier
      if(dr.getOperation() == READ){
          dr = pending.remove(skip);
      }
      // BARRIER is no-op; just work on first request instead
      else if(dr.getOperation() == BARRIER){
          dr = pending.removeFirst();
      }
      // Write cannot pass barrier; If skip takes us to a write past the barrier,
      // work on first request instead.
      else{
          assert(dr.getOperation() == WRITE);
          
          ListIterator li = pending.listIterator(0);
          while(li.nextIndex() <= skip){
              if(!li.hasNext() || ((DiskResult)(li.next())).getOperation() == BARRIER){
                  skip = 0;
                  break;
              }
          }
          dr = pending.remove(skip);
      }
      assert(dr.getOperation() == WRITE || dr.getOperation() == READ);
      return dr;
    }
    finally{
      lock.unlock();
    }
  }


  //-------------------------------------------------------
  // randomlyKillDisk -- flip a coin. If heads, then 
  // set diskIsDead and signal all threads. No new
  // requests will be issued for this disk.
  //-------------------------------------------------------
  private void randomlyKillDisk()
  {
    float coin;

    if(diskIsDead){
      return;
    }
    if(failureCountDown > 0){
        failureCountDown --;
    }
    if(failureCountDown > 0){
        return;
    }
    coin = rand.nextFloat();
    if(coin > 1.0 - failureProb){
      System.out.println("Killing disk: " + coin + " failureProb " + failureProb);
      diskIsDead = true;
      workReady.signalAll();
    }
    return;
  }

}
