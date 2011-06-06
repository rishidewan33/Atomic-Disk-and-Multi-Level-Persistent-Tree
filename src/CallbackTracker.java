/*
 * CallbackTracker.java
 *
 * Wait for a particular tag to finish...
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2011 Mike Dahlin
 *
 */

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;

public class CallbackTracker implements DiskCallback{
    

    SimpleLock lock;
    Condition tagsWaiting;
    ConcurrentHashMap<Integer, DiskResult> currentDiskResults;
    ArrayList<Integer> dontWait;
    //used for disk failure and recovery testing 
    boolean diskIsDead;

    //constructor 
    public CallbackTracker()
    {
        lock = new SimpleLock();
        tagsWaiting = lock.newCondition();
        currentDiskResults = new ConcurrentHashMap<Integer, DiskResult>();
        dontWait = new ArrayList<Integer>();
        diskIsDead = false;
    }

    public void requestDone(DiskResult result) {
        try{
            lock.lock();

            //If we receive an error message (from disk kiling and recovery testing, set diskIsDead to true, print an error message,
            //free waiting tags, and return
            if (result.getStatus() == DiskResult.FAKE_ERROR || result.getTag() == DiskResult.RESERVED_TAG && result.getSectorNum() == 0){
                System.err.println("Error received by callback tracker, result: " + result.getStatus());
                diskIsDead = true;
                tagsWaiting.signalAll();
                return;
            }

            //If the incoming tag has gone to disk and we are supposed to wait for it,
            //put it on the current diskResult list and signal waiting tags
            else if(result.getStatus() == DiskResult.OK && !dontWait.contains(result.getTag()))
            {
                 currentDiskResults.put(result.getTag(), result);
                 tagsWaiting.signalAll();
            }

            //If we are told not to wait for a specified tag, drop it
            else{
                if(dontWait.contains(result.getTag()))
                {
                    dontWait.remove(new Integer(result.getTag()));
                }
            }
        }
        finally{
          lock.unlock();
        }
    }

    // Wait for one tag to be done
    //
    public DiskResult waitForTag(int tag)
    {
        try{
            lock.lock();

            // while NOT in currentDiskResults, wait for tag
            while(currentDiskResults.get(tag) == null && !diskIsDead)
            {
                tagsWaiting.awaitUninterruptibly();
            }
            //Disk failure testing
            if (diskIsDead){
                return null;
            }
            //remove the given tag from the current disk results, return
            return currentDiskResults.remove(tag);
        }
        finally{
            lock.unlock();
        }
    }

    //
    // Wait for a set of tags to be done
    //
    public ArrayList waitForTags(ArrayList tags){
        ArrayList<DiskResult> diskResultArrayList = new ArrayList<DiskResult>();
        try{
            lock.lock();
            //If this is null, then we haven't received any disk results yet,
            //this case is handles by waitForTag's caller 
            if(currentDiskResults == null)
            {
                return null ;
            }
            //wait for each tag in tags to complete, store all diskResults
            //in an array
            for (int i=0; i < tags.size(); i++)
            {
                diskResultArrayList.add(waitForTag((Integer)tags.get(i)));
                if (diskResultArrayList.size() == tags.size()){
                        break;
                }
            }
            return diskResultArrayList;
        }
        finally{
            lock.unlock();
        }


    }

    //
    // To avoid memory leaks, need to tell CallbackTracker
    // if there are tags that we don't plan to wait for.
    // When these results arrive, drop them on the
    // floor.
    //
    public void dontWaitForTag(int tag)
    {
        try
        {
            lock.lock();
            //add the tag to the dontWait list
            dontWait.add(tag);
        }
        finally
        {
            lock.unlock();
        }
    }

    public void dontWaitForTags(ArrayList tags)
    {
        try
        {
            lock.lock();
            //add all tags from tags to dontWait List 
            for (int i =0; i < tags.size(); i++){
                dontWaitForTag((Integer)tags.get(i));
            }
        }
        finally
        {
            lock.unlock();
        }
    }



}