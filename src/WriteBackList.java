
import java.util.LinkedList;
import java.util.ListIterator;

/*
 * WriteBackList.java
 *
 * List of commited transactions with pending writebacks.
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2011 Mike Dahlin
 *
 */
public class WriteBackList{

    // 
    // You can modify and add to the interfaces
    //

    public LinkedList<Transaction> WriteBackList = new LinkedList<Transaction>();

    public int size()
    {
        return WriteBackList.size();
    }

    // Once a transaction is committed in the log,
    // move it from the ActiveTransactionList to 
    // the WriteBackList
    public void addCommitted(Transaction t)
    {
        assert(t.getTransState() == Transaction.transState.COMMITED);
        WriteBackList.addLast(t);
    }

    //
    // A write-back thread should process
    // writebacks in FIFO order.
    //
    // NOTE: Don't remove the Transaction from
    // the list until the writeback is done
    // (reads need to see them)!
    //
    // NOTE: Service transactions in FIFO order
    // so that if there are multiple writes
    // to the same sector, the write that is
    // part of the last-committed transaction "wins".
    //
    // NOTE: you need to use log order for commit
    // order -- the transaction IDs are assigned
    // when transactions are created, so commit
    // order may not match transaction ID order.
    //    
    public Transaction getNextWriteback(){
        return WriteBackList.getFirst();
    }

    //
    // Remove a transaction -- its writebacks
    // are now safely on disk.
    //
    public Transaction removeNextWriteback(){
        return WriteBackList.removeFirst();
    }

    //
    // Check to see if a sector has been written
    // by a committed transaction. If there
    // are multiple writes to the same sector,
    // be sure to return the last-committed write.
    //
    public boolean checkRead(int secNum, byte buffer[])
    throws IllegalArgumentException, 
           IndexOutOfBoundsException
    {
        
      if (buffer.length != Disk.SECTOR_SIZE){
          throw new IllegalArgumentException("The buffer is smaller than SECTOR_SIZE.");
      }
      if (secNum < 0 || secNum >= Disk.NUM_OF_SECTORS - 1025){
          throw new IndexOutOfBoundsException("sectorNum is not a valid sector number.");
      }

      //Get an iterator for the WriteBack List
      ListIterator<Transaction> it = WriteBackList.listIterator();

      Transaction temp = null;
      Transaction tempToRet = null;

      int index = 0;
      int indexToUse = 0;
      while(it.hasNext())
      {
          temp = it.next();
          index = temp.getSectorsUpdated().indexOf(secNum);
          if( index != -1)
          {
              //Iterate the WB List to check if a Transaction is writing to that sectorNum.
              //Also, if a transaction is found to do the writing to sectorNum, we will still
              //check the rest of the WBList to see if they too committed the writing of the
              //sector number (Remember the "last commit wins" rule?).
              tempToRet = temp;
              indexToUse = index;
          }
      }
      // If tempToRet == null, then didn't find the Transaction in the WB list
      // Write the sector to the buffer
      if (tempToRet != null)
      {
        byte[] buff = tempToRet.sectorWrites.get(indexToUse);
        for (int i = 0; i < buffer.length; i ++)
        {
            buffer[i] = buff[i];
        }
        return true;
      }
      //didn't find it on the WriteBack List
      return false;
    }
}
