/*
 * RFS -- reliable file system
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2007 Mike Dahlin
 *
 */
import java.io.IOException;
import java.io.EOFException;
public class RFS{

  public RFS(boolean doFormat)
    throws IOException
  {
  }

  public void createFile(String filename, boolean openIt)
    throws IOException, IllegalArgumentException
  {
  }

  public void createDir(String dirname)
    throws IOException, IllegalArgumentException
  {
  }


  public void unlink(String filename)
    throws IOException, IllegalArgumentException
  {
  }

  public void rename(String oldName, String newName)
    throws IOException, IllegalArgumentException
  {
  }


  public int open(String filename)
    throws IOException, IllegalArgumentException
  {
    return -1;
  }


  public void close(int fd)
    throws IOException, IllegalArgumentException
  {
  }


  public int read(int fd, int offset, int count, byte buffer[])
    throws IOException, IllegalArgumentException
  {
    return -1;
  }


  public void write(int fd, int offset, int count, byte buffer[])
    throws IOException, IllegalArgumentException
  {
  }

  public String[] readDir(String dirname)
    throws IOException, IllegalArgumentException
  {
    return null;
  }

  public int size(int fd)
    throws IOException, IllegalArgumentException
  {
    return -1;
  }

  public int space(int fd)
    throws IOException, IllegalArgumentException
  {
    return -1;
  }




}
