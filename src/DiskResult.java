/*
 * DiskResult.java
 *
 * Contains the result of a disk read.
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2007,2010 Mike Dahlin
 *
 */
public class DiskResult{
  public static final int INPROGRESS = 34234;
  public static final int OK = 9083;
  public static final int FAKE_ERROR = 23955; // We "killed" disk
  public static final int REAL_ERROR = 33245; // Real IO eror
  public static final int RESERVED_TAG = -1;

  private int tag;  // Request identifier provided by caller
  private int status;

  private byte buf[];
  private int secNum;
  private int operation;

  //-------------------------------------------------------
  // DiskResult
  //-------------------------------------------------------
  public DiskResult(int operation, int tag, int sectorNum, byte b[])
  {
    this.operation = operation;
    this.tag = tag;
    this.secNum = sectorNum;
    this.buf = b;
    this.status = INPROGRESS;
  }
  
  //-------------------------------------------------------
  // get/set fields
  //-------------------------------------------------------  
  public int getOperation(){
    return operation;
  }
  public int getSectorNum(){
    return secNum;
  }
  public byte[] getBuf(){
    return buf;
  }
  public void setStatus(int status){
    assert(status == INPROGRESS 
           || status == OK
           || status == FAKE_ERROR
           || status == REAL_ERROR);
    this.status = status;
  }
  public int getStatus(){
    return status;
  }
  public int getTag(){
    return tag;
  }

  public String toString(){
      return "(DiskResult)(operation: " + operation + " tag: " + tag + " secNum: " + secNum + " status: " + status + ")";
  }
}
