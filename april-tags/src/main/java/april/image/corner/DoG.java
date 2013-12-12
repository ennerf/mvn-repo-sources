package april.image.corner;

import april.jmat.*;
import april.image.*;

public class DoG
{
    double sigma1, sigma2;

    public DoG(double sigma1, double sigma2)
    {
        this.sigma1 = sigma1;
        this.sigma2 = sigma2;
    }

    public FloatImage computeResponse(FloatImage data)
    {
        int width = data.width;
        int height = data.height;

        int f1sz = ((int) Math.max(3, 3*sigma1)) | 1;
        int f2sz = ((int) Math.max(3, 3*sigma2)) | 1;

        float f1[] = SigProc.makeGaussianFilter(sigma1, f1sz);
        float f2[] = SigProc.makeGaussianFilter(sigma2, f2sz);

        FloatImage resp1 = data.filterFactoredCentered(f1, f1);
        FloatImage resp2 = data.filterFactoredCentered(f2, f2);

        FloatImage response = resp1.subtract(resp2).abs();
        return response;
    }
}
