package april.camera;

import java.awt.image.*;

import april.jmat.*;
import april.util.*;

public class BilinearRasterizer implements Rasterizer
{
    int inputWidth, inputHeight;
    int outputWidth, outputHeight;

    int indices[];
    int weights[];

    public BilinearRasterizer(View input, View output)
    {
        this(input, null, output, null);
    }

    public BilinearRasterizer(View input, double G2C_input[][],
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
        weights = new int[size*4];

        for (int i=0; i < indices.length; i++)
            indices[i] = -1;

        int twoPow16 = (int) Math.pow(2, 16);

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

                int x_dp = (int) Math.floor(xy_dp[0]);
                int y_dp = (int) Math.floor(xy_dp[1]);

                double dx = xy_dp[0] - x_dp;
                double dy = xy_dp[1] - y_dp;

                int idx = -1;
                if (x_dp >= 0 && x_dp+1 < inputWidth && y_dp >= 0 && y_dp+1 < inputHeight)
                    idx = y_dp * inputWidth + x_dp;

                indices[y_rp*outputWidth+ x_rp] = idx;

                if (idx == -1)
                    continue;

                // bilinear weights
                weights[4*(y_rp*outputWidth + x_rp) + 0] = (int) ((1-dx)*(1-dy) * twoPow16); // x0, y0
                weights[4*(y_rp*outputWidth + x_rp) + 1] = (int) ((  dx)*(1-dy) * twoPow16); // x1, y0
                weights[4*(y_rp*outputWidth + x_rp) + 2] = (int) ((1-dx)*(  dy) * twoPow16); // x0, y1
                weights[4*(y_rp*outputWidth + x_rp) + 3] = (int) ((  dx)*(  dy) * twoPow16); // x1, y1
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
            int v00 = _in[idx];             // x0, y0
            int v10 = _in[idx + 1];         // x1, y0
            int v01 = _in[idx + width];     // x0, y1
            int v11 = _in[idx + width + 1]; // x1, y1

            int b00 = ((v00      ) & 0xFF) * weights[4*i + 0];
            int b10 = ((v10      ) & 0xFF) * weights[4*i + 1];
            int b01 = ((v01      ) & 0xFF) * weights[4*i + 2];
            int b11 = ((v11      ) & 0xFF) * weights[4*i + 3];

            int b = (b00 + b10 + b01 + b11) >> 16;

            _out[i] =  (byte)(b & 0xFF);
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

            int v00 = _in[idx];             // x0, y0
            int v10 = _in[idx + 1];         // x1, y0
            int v01 = _in[idx + width];     // x0, y1
            int v11 = _in[idx + width + 1]; // x1, y1

            int r00 = ((v00 >> 16) & 0xFF) * weights[4*i + 0];
            int r10 = ((v10 >> 16) & 0xFF) * weights[4*i + 1];
            int r01 = ((v01 >> 16) & 0xFF) * weights[4*i + 2];
            int r11 = ((v11 >> 16) & 0xFF) * weights[4*i + 3];

            int g00 = ((v00 >>  8) & 0xFF) * weights[4*i + 0];
            int g10 = ((v10 >>  8) & 0xFF) * weights[4*i + 1];
            int g01 = ((v01 >>  8) & 0xFF) * weights[4*i + 2];
            int g11 = ((v11 >>  8) & 0xFF) * weights[4*i + 3];

            int b00 = ((v00      ) & 0xFF) * weights[4*i + 0];
            int b10 = ((v10      ) & 0xFF) * weights[4*i + 1];
            int b01 = ((v01      ) & 0xFF) * weights[4*i + 2];
            int b11 = ((v11      ) & 0xFF) * weights[4*i + 3];

            int r = (r00 + r10 + r01 + r11) >> 16;
            int g = (g00 + g10 + g01 + g11) >> 16;
            int b = (b00 + b10 + b01 + b11) >> 16;

            _out[i] = ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
        }

        return out;
    }
}
