package april.util;

public class BlockingSingleQueue<T>
{
    // t=null means no data arrived yet, else t represents most
    // recently 'put' element.
    T t;

    int drops;

    Object empty  = new Object();

    public synchronized void put(T el)
    {
        if (t != null)
            drops++;

        this.t = el;
        this.notifyAll();
    }

    public void clear()
    {
        t = null;
    }

    public int getDrops()
    {
        return drops;
    }

    public synchronized boolean isEmpty()
    {
        if (this.t == null)
            return true;

        return false;
    }

    /** Block **/
    public synchronized T get()
    {
        while (true) {
            if (this.t != null) {
                T tmp = this.t;
                this.t = null;

                this.notifyAll();

                return tmp;
            }

            try {
                this.wait();
            } catch (InterruptedException ex) {
            }
        }
    }

    public synchronized  void waitTillEmpty()
    {
        while (true) {
            if (this.t == null)
                return;

            try {
                this.wait();
            } catch (InterruptedException ex) {
            }
        }
    }

}
