
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/*
 * ActiveTransaction.java
 *
 * List of active transactions.
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2011 Mike Dahlin
 *
 */
public class ActiveTransactionList{

    /*
     * You can alter or add to these suggested methods.
     */

    ConcurrentHashMap<Integer, Transaction> ActiveTransactionList = new ConcurrentHashMap<Integer, Transaction>();

    public void put(Transaction trans){
        ActiveTransactionList.put(trans.getTransID(), trans); 
    }

    public Transaction get(int tid)
    {
        return ActiveTransactionList.get(tid);
    }

    public Transaction remove(int tid)
    {
        return ActiveTransactionList.remove(tid);
    }

    public int size()
    {
        return ActiveTransactionList.size();
    }

    public Set ActiveTransactionKeySet()
    {
        return ActiveTransactionList.keySet();
    }


}