package april.image;

import java.awt.image.*;
import april.util.*;

// Implementation of FH Segment using extra precision to enable
// results that are nearly identical to F&H published C implementation 06/11
public class FHFloatSegment
{
    static boolean verbose  = false;

    // an edge has three fields, ida, idb, and weight.  We represent
    // all three in a single long
    // (ida << IDA_SHIFT) + (idb << IDB_SHIFT) + weight.
    //
    // maximum id = 2^22 ==> maximum image size is 2048x2048
    static final int IDA_SHIFT = 42, IDB_SHIFT = 20, WEIGHT_SHIFT = 0;
    static final int WEIGHT_MASK = 1048575;//262143;//65535;
    static final int INDEX_MASK = (1<<22)-1;

    static double err(float r[] , float g[], float b[], int ida, int idb)
    {
        return Math.sqrt(((double)r[ida]-r[idb])*(r[ida]-r[idb]) +
                         ((double)g[ida]-g[idb])*(g[ida]-g[idb]) +
                         ((double)b[ida]-b[idb])*(b[ida]-b[idb]));

    }

    // Segments a 8-bit BufferedImage, including the pre-processing
    // gaussian blur with the recommendend sigma=0.5, then calls segment(...)
    // on resulting float images
    public static BufferedImage segmentWithBlur(BufferedImage im, double k, int minSz)
    {
        double sig = 0.5;
        int filtsz = ((int) (2*Math.ceil(4*sig))) | 1;
        float filt[] = SigProc.makeGaussianFilter(sig, filtsz);

        FloatImage fir = new FloatImage(im, 16);
        FloatImage fig = new FloatImage(im, 8);
        FloatImage fib = new FloatImage(im, 0);
        fir = fir.filterFactoredCentered(filt,filt);
        fig = fig.filterFactoredCentered(filt,filt);
        fib = fib.filterFactoredCentered(filt,filt);

        return segment(fir.d, fig.d, fib.d, 1.0f,
                       im.getWidth(), im.getHeight(),
                       k, minSz);
    }

    // Segments a floating point color image. 'r' ,'g', 'b' are the 3 channels,
    // with maxValue  correspoding to 'maxV' and dimensions 'width' and 'height'
    // segmentation paramters 'k' and 'minSz' correspond to Felzenszwalb & Huttenlochers paramters
    // both correlate positively with large segment sizes
    public static BufferedImage segment(float r[], float g[], float b[], float maxV,
                                        int width, int height,
                                        double k, int minSz)
    {
        Tic tic = new Tic();
        k *= (maxV / 255.0); // Rescale to match FH's parameter values

        // nbits determines the precision of our fixed point weight representation
        //   - selected to be minimal subject to good segmentation performance
        //       (e.g. be able to resolve distances on the floating point blurred inputs
        //   - selected WEIGHT_MASK such that WEIGHT_MASK > maxErr > ~sqrt(3)*(2^nbits -1)
        final int nbits = 16;

        int maxBitValue = (1 << nbits) - 1;
        float errScale = maxBitValue/maxV;
        int maxErr = (int)Math.ceil(Math.sqrt(3.0 * ((1<<nbits)-1.0)*((1<<nbits)-1.0)));

        // Make edges
        int nedges = 0;
        long edges[] = new long[height*width*4];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                long ida = y*width +x;

                if (y != 0 && x + 1 < width) {
                    long idb1 = (y-1)*width + x + 1;
                    long w1 = (long) (errScale*err(r,g,b, (int)ida, (int)idb1));
                    edges[nedges++] = (ida<<IDA_SHIFT) + (idb1<<IDB_SHIFT) + w1;
                }
                if (x + 1 < width) {
                    long idb2 =  y   *width + x + 1;
                    long w2 = (long) (errScale*err(r,g,b, (int)ida, (int)idb2));
                    edges[nedges++] = (ida<<IDA_SHIFT) + (idb2<<IDB_SHIFT) + w2;
                }
                if (x + 1 < width && y + 1 < height) {
                    long idb3 = (y+1)*width + x + 1;
                    long w3 = (long) (errScale*err(r,g,b, (int)ida, (int)idb3));
                    edges[nedges++] = (ida<<IDA_SHIFT) + (idb3<<IDB_SHIFT) + w3;
                }
                if (y + 1 < height) {
                    long idb4 = (y+1)*width + x;
                    long w4 = (long) (errScale*err(r,g,b, (int)ida, (int)idb4));
                    edges[nedges++] = (ida<<IDA_SHIFT) + (idb4<<IDB_SHIFT) + w4;
                }
            }
        }
        if (verbose)  System.out.printf(" %f -- edges \n",tic.toctic());

        if (true) { // counting sort
            // int maxv = maxErr;
            int counts[] = new int[maxErr + 1];
            for (int i = 0; i < nedges; i++) {
                int w = (int) (edges[i]&WEIGHT_MASK);
                counts[w]++;
            }

            // Integrate
            for (int i = 1; i < counts.length; i++)
                counts[i] += counts[i-1];

            // sort
            long newedges[] = new long[nedges];
            for (int i =0; i < nedges; i++) {
                int w = (int) (edges[i]&WEIGHT_MASK);
                counts[w]--;
                newedges[counts[w]] = edges[i];
            }
            edges = newedges;
        }

        if (verbose) System.out.printf(" %f -- sort \n",tic.toctic());

        // Find the segments
        UnionFindSimple uf = new UnionFindSimple(width*height);
        double threshold[] = new double[width*height];
        double errScale_d = 1.0 /errScale;
        for (int i = 0; i < threshold.length; i++)
            threshold[i] = k;

        for (int i = 0; i < nedges; i++) {
            int ida = (int) ((edges[i]>>IDA_SHIFT)&INDEX_MASK);
            int idb = (int) ((edges[i]>>IDB_SHIFT)&INDEX_MASK);
            ida = (int) uf.getRepresentative(ida);
            idb = (int) uf.getRepresentative(idb);

            if (ida == idb)
                continue;

            double w = (edges[i]&WEIGHT_MASK)*errScale_d;
            if (w <= threshold[ida] && w <= threshold[idb]) {
                ida = uf.connectNodes(ida, idb);
                threshold[ida] = w +  k/uf.getSetSize(ida);
            }
        }
        if (verbose) System.out.printf(" %f -- unionfind \n",tic.toctic());


        // Joint smaller segments
        if (true) {
            for (int i = 0; i < nedges; i++) {
                int ida = (int) ((edges[i]>>IDA_SHIFT)&INDEX_MASK);
                int idb = (int) ((edges[i]>>IDB_SHIFT)&INDEX_MASK);
                ida = (int) uf.getRepresentative(ida);
                idb = (int) uf.getRepresentative(idb);

                if (ida != idb && (uf.getSetSize(ida) < minSz || uf.getSetSize(idb) < minSz))
                    uf.connectNodes(ida,idb);
            }
        }
        if (verbose) System.out.printf(" %f -- small \n",tic.toctic());

        BufferedImage _out = new BufferedImage(width,height, BufferedImage.TYPE_INT_RGB);
        int out[] = ((DataBufferInt) (_out.getRaster().getDataBuffer())).getData();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                out[y*width+x] = (int) uf.getRepresentative(y*width+x);
            }
        }
        if (verbose) System.out.printf(" %f -- output \n",tic.toctic());
        if (verbose) System.out.printf(" %f -- TOTAL FloatSegment \n",tic.totalTime());

        return _out;
    }
}