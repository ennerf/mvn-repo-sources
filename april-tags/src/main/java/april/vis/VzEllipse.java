package april.vis;

import april.jmat.*;

public class VzEllipse implements VisObject
{

    VisChain vch;

    //XXX These ellipses degrade visually when elongated due to underlying circle having only 16 points.
    //    we may want to employ VzCircle makeCircleOutline/Fill at higher quality here
    public VzEllipse(double mu[], double P[][], Style ... styles)
    {
        assert(mu.length == 2);
        assert(P.length == 2 && P[0].length == 2 && P[1].length == 2);

        if (true) {
            double U[][] = LinAlg.identity(4);
            double sv[] = new double[2];

            LinAlg.svd22(P, sv, U);



            vch = new VisChain(LinAlg.translate(mu),
                               U,
                               LinAlg.scale(Math.sqrt(sv[0]),Math.sqrt(sv[1]),1.0),
                               new VzCircle(1, styles));
        } else {
            double a = P[0][0], b = P[0][1], c = P[1][1];
            double x = Math.sqrt(a);
            double y = b/x;
            double z = Math.sqrt(c - y*y);

            double L[][] = { { x, 0, 0, 0 }, { y, z, 0, 0 }, { 0, 0, 1, 0 }, { 0, 0, 0, 1} };

            LinAlg.print(L);

            LinAlg.print(LinAlg.matrixABt(L, L));

            vch = new VisChain(LinAlg.translate(mu),
                               (L),
                               new VzCircle(1, styles));
        }
    }

    public void render(VisCanvas vc, VisLayer layer, VisCanvas.RenderInfo rinfo, GL gl)
    {
        vch.render(vc,layer,rinfo,gl);
    }
}
