/*
 * SimpleLock
 *
 * Implements a simple lock by wrapping itself around
 * ReentrantLock. We do not implement all Lock
 * methods (to simplify the interface).
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2007 Mike Dahlin
 *
 */

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.TimeUnit;

public class SimpleLock implements Lock{
  ReentrantLock daLock;

  //-------------------------------------------------
  // Constructor
  //-------------------------------------------------
  public SimpleLock()
  {
    daLock = new ReentrantLock();
  }

  //-------------------------------------------------
  // lock() -- acquires the lock
  //-------------------------------------------------
  public void lock()
  {
    daLock.lock();
  }

  //-------------------------------------------------
  // lock() -- releases the lock
  //-------------------------------------------------
  public void unlock()
  {
    assert(daLock.isHeldByCurrentThread());
    daLock.unlock();
  }

  //-------------------------------------------------
  // newCondition() -- Return a new Condition instance
  // that is bound to this Lock instance.
  //-------------------------------------------------
  public Condition newCondition()
  {
    return daLock.newCondition();
  }
  






  //-------------------------------------------------
  // lockInterruptibly() -- NOT SUPPORTED
  //-------------------------------------------------
  public void lockInterruptibly()
  {
    System.out.println("SimpleLock::lockInterruptibly() not supported");
    assert(false);
    System.exit(-1);
  }

  //-------------------------------------------------
  // tryLock() -- NOT SUPPORTED
  //-------------------------------------------------
  public boolean tryLock()
  {
    System.out.println("SimpleLock::tryLock() not supported");
    assert(false);
    System.exit(-1);
    return false;
  }

  //-------------------------------------------------
  // tryLock() -- NOT SUPPORTED
  //-------------------------------------------------
  public boolean tryLock(long time, TimeUnit unit)
  {
    System.out.println("SimpleLock::tryLock() not supported");
    assert(false);
    System.exit(-1);
    return false;
  }
}

