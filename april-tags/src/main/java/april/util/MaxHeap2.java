package april.util;

import java.util.*;

//                 0
//         1               2
//      3     4        5       6
//     7 8   9 10    11 12   13 14
//
// Children of node i:  2*i+1, 2*i+2
// Parent of node i: (i-1) / 2
public class MaxHeap2<T extends MaxHeap2.HeapObject>
{
    HeapObject objs[];
    int heapsize;

    public static class HeapObject
    {
        public double heapValue;
    }

    public MaxHeap2()
    {
        this(16);
    }

    public MaxHeap2(int initialsize)
    {
        objs = new HeapObject[initialsize];
        heapsize = 0;
    }

    public final void add(T o)
    {
        if (heapsize == objs.length) {
            HeapObject objs2[] = new HeapObject[objs.length * 2];

            for (int i = 0; i < objs.length; i++) {
                objs2[i] = objs[i];
            }
            objs = objs2;
        }

        int idx = heapsize;

        objs[heapsize] = o;
        heapsize++;

        double thisScore = o.heapValue;

        while (idx > 0) {
            int parent = (idx - 1) / 2;
            if (objs[parent].heapValue >= thisScore)
                break;

            swap(idx, parent);
            idx = parent;

            // don't need to recurse down other side of the tree,
            // because we couldn't possibly break the heap property by
            // making the parent bigger.
        }
    }

    final void swap(int idxa, int idxb)
    {
        HeapObject t = objs[idxa];
        objs[idxa] = objs[idxb];
        objs[idxb] = t;
    }

    public final int size()
    {
        return heapsize;
    }

    public final T removeMax()
    {
        HeapObject obj = objs[0];

        // move last node to top. But this may violate the heap
        // property. So recurse down to fix the heap property.
        heapsize--;

        if (heapsize > 0) {

            objs[0] = objs[heapsize];

            int parent = 0;
            // note that the index of the parent changes as we
            // traverse the tree, but its score does not.
            double parentScore = objs[0].heapValue;

            while (parent < heapsize) {

                int left = 2*parent + 1;
                int right = 2*parent + 2;

                double leftScore = (left < heapsize) ? objs[left].heapValue : -Double.MAX_VALUE;
                double rightScore = (right < heapsize) ? objs[right].heapValue : -Double.MAX_VALUE;

                // put the biggest of the parent,left,right as the parent.

                // easy case... heap property is already satisfied.
                if (parentScore >= leftScore && parentScore >= rightScore)
                    break;

                // if we got here, then one of the children is bigger
                // than the parent.
                if (leftScore >= rightScore) {
                    swap(parent, left);
                    parent = left;
                } else {
                    swap(parent, right);
                    parent = right;
                }
            }
        }

        return (T) obj;
    }

    public static class TestObject extends HeapObject
    {
        TestObject(double v)
        {
            this.heapValue = v;
        }
    }

    public static void main(String args[])
    {
        int cap = 10000;
        int sz = 0;
        int vals[] = new int[cap];
        MaxHeap2<TestObject> heap = new MaxHeap2<TestObject>();

        Random r = new Random(0);
        int maxsz = 0;
        int zcnt = 0;

        Tic tic = new Tic();

        for (int iter = 0; iter < 5000000; iter++) {

            double p = r.nextDouble();
            if (p < .5 && sz < cap) {
                // System.out.print("+");
                // add a value?
                int v = r.nextInt(1 << 16);
                vals[sz] = v;
                heap.add(new TestObject(v));
                sz++;
            } else if (sz > 0) {
                // System.out.print("-");
                // remove a value
                int maxv = -1, maxi = -1;

                for (int i = 0; i < sz; i++) {
                    if (vals[i] > maxv) {
                        maxv = vals[i];
                        maxi = i;
                    }
                }

                vals[maxi] = vals[sz - 1];
                int hv = (int) heap.removeMax().heapValue;
                if (hv != maxv)
                    System.out.println("error");
                sz--;
            } else {
                // System.out.print("?");
            }

            if (sz > maxsz)
                maxsz = sz;

            // count how many times we've returned to zero.
            if (maxsz > 0 && sz == 0)
                zcnt++;
//            System.out.flush();
        }
        System.out.printf("time: %15f ms\n", tic.toc()*1000);
        System.out.println("max size: " + maxsz + " zcount: " + zcnt);



    }
}
