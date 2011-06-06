/*
 * DiskCallback.java
 *
 * Interface to call when an IO is done.
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2007,2010 Mike Dahlin
 *
 */
public interface DiskCallback{
  public void requestDone(DiskResult result);
}
