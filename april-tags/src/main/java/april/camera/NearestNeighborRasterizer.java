package april.camera;

import java.awt.image.*;

import april.jmat.*;

public class NearestNeighborRasterizer implements Rasterizer
{
    int inputWidth, inputHeight;
    int outputWidth, outputHeight;

    int indices[];

    public NearestNeighborRasterizer(View input, View output)
    {
        this(input, null, output, null);
    }

    public NearestNeighborRasterizer(View input, double G2C_input[][],
                                     View output, double G2C_output[][])
    {
        DistortionFunctionVerifier inVerifier = new DistortionFunctionVerifier(input);
        DistortionFunctionVerifier outVerifier = new DistortionFunctionVerifier(output);

        ////////////////////////////////////////
        inputWidth  = input.getWidth();
        inputHeight = input.getHeight();
        outputWidth = output.getWidth();
        outputHeight= output.getHeight();

        int size = outputWidth * outputHeight;

        indices = new int[size];

        ////////////////////////////////////////
        // compute rotation to convert from "output" orientation to "input" orientation
        double R_OutToIn[][] = LinAlg.identity(3);
        if (G2C_input != null && G2C_output != null)
            R_OutToIn = LinAlg.matrixAB(LinAlg.select(G2C_input, 0, 2, 0, 2),
                                        LinAlg.inverse(LinAlg.select(G2C_output, 0, 2, 0, 2)));

        ////////////////////////////////////////
        // build table
        for (int y_rp = 0; y_rp < outputHeight; y_rp++) {
            for (int x_rp = 0; x_rp < outputWidth; x_rp++) {

                double xy_rp[] = new double[] { x_rp, y_rp };
                if (!outVerifier.validPixelCoord(xy_rp))
                    continue;

                double xyz_r[] = CameraMath.rayToPlane(output.pixelsToRay(xy_rp));
                xyz_r = CameraMath.pointTransform(R_OutToIn, xyz_r);

                if (!inVerifier.validRay(xyz_r))
                    continue;

                double xy_dp[] = input.rayToPixels(xyz_r);

                int x_dp = (int) Math.round(xy_dp[0]);
                int y_dp = (int) Math.round(xy_dp[1]);

                int idx = -1;
                if (x_dp >= 0 && x_dp+1 < inputWidth && y_dp >= 0 && y_dp+1 < inputHeight)
                    idx = y_dp * inputWidth + x_dp;

                indices[y_rp*outputWidth+ x_rp] = idx;
            }
        }
    }

    public BufferedImage rectifyImage(BufferedImage in)
    {
        switch(in.getType())
        {
            case BufferedImage.TYPE_INT_RGB:
                return rectifyImageIntRGB(in);

            case BufferedImage.TYPE_BYTE_GRAY:
                return rectifyImageByteGray(in);

            default:
                throw new RuntimeException("Unsupported image type: "+in.getType());
       }
    }

    /** Rectify a grayscale BufferedImage using the lookup tables.
      */
    public BufferedImage rectifyImageByteGray(BufferedImage in)
    {
        int width = in.getWidth();
        int height = in.getHeight();

        assert(width == inputWidth && height == inputHeight);

        BufferedImage out = new BufferedImage(outputWidth, outputHeight,
                                              BufferedImage.TYPE_BYTE_GRAY);

        byte _in[]  = ((DataBufferByte) (in.getRaster().getDataBuffer())).getData();
        byte _out[] = ((DataBufferByte) (out.getRaster().getDataBuffer())).getData();

        for (int i=0; i < _out.length; i++) {

            int idx = indices[i];

            if (idx == -1)
                continue;

            _out[i] = _in[idx];
        }

        return out;
    }

    /** Rectify an RGB BufferedImage using the lookup tables.
      */
    public BufferedImage rectifyImageIntRGB(BufferedImage in)
    {
        int width = in.getWidth();
        int height = in.getHeight();

        assert(width == inputWidth && height == inputHeight);

        BufferedImage out = new BufferedImage(outputWidth, outputHeight,
                                              BufferedImage.TYPE_INT_RGB);

        int _in[]  = ((DataBufferInt) (in.getRaster().getDataBuffer())).getData();
        int _out[] = ((DataBufferInt) (out.getRaster().getDataBuffer())).getData();

        for (int i=0; i < _out.length; i++) {

            int idx = indices[i];

            if (idx == -1)
                continue;

            _out[i] = _in[idx];
        }

        return out;
    }
}
