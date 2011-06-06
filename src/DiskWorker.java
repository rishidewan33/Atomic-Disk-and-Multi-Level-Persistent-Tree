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

public class DiskWorker extends Thread{
  RandomAccessFile file;
  Disk disk;
  DiskCallback callback;
  
  //-------------------------------------------------------
  // DiskWorker
  //-------------------------------------------------------
  public DiskWorker(Disk disk, RandomAccessFile file, 
                    DiskCallback callback)
  {
    this.disk = disk;
    this.file = file;
    this.callback = callback;
  }

  //-------------------------------------------------------
  // run() -- All work and no play makes run() a dull
  // method.
  //-------------------------------------------------------
  public void run()
  {
    DiskResult req;
    while(true)
    {
      try
      {
        req = disk.getWork();
      }
      catch(IOException e)
      {
        //
        // Create fake disk request to pass error back
        //
        req = new DiskResult(Disk.READ, DiskResult.RESERVED_TAG, 0, null);
        req.setStatus(DiskResult.FAKE_ERROR);
        callback.requestDone(req);
        return; // Stop working on requests. Thread exit.
      }

      assert(req.getOperation() == Disk.READ || req.getOperation() == Disk.WRITE);

      try{
        file.seek(req.getSectorNum() * Disk.SECTOR_SIZE);
        if(req.getOperation() == Disk.READ){
          file.read(req.getBuf(), 0, Disk.SECTOR_SIZE);
        }
        else{
          file.write(req.getBuf(), 0, Disk.SECTOR_SIZE);
        }
        req.setStatus(DiskResult.OK);
      }
      catch(IOException e){
        req.setStatus(DiskResult.REAL_ERROR);
      }
      callback.requestDone(req);
    }
  }
  
}