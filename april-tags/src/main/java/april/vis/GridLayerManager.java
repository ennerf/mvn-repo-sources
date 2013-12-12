package april.vis;

// Simple layer manager to handle a regular grid of layers that completely
// fills the viewport
public class GridLayerManager implements VisLayerManager
{
    final int rowIdx, colIdx, numRows, numCols;

    private int winWidth, winHeight;

    // Arguments: zero-based index for the position of this layer in the grid,
    // in addition to the total number of rows/cols
    public GridLayerManager(int _rowIdx, int _colIdx, int _numRows, int _numCols)
    {
        rowIdx =_rowIdx;
        colIdx = _colIdx;
        numRows = _numRows;
        numCols = _numCols;
    }

    public int[] getLayerPosition(VisCanvas vc, int viewport[], VisLayer vl, long mtime)
    {
        winWidth = (int)Math.round(1.0*(viewport[2] - viewport[0]) / numCols);
        winHeight = (int)Math.round(1.0*(viewport[3] - viewport[1]) / numRows);

        return new int[]{viewport[0] + colIdx*winWidth, viewport[1] + rowIdx*winHeight,
                         winWidth, winHeight};
    }

    // Get the most recent dimensions of this layer, {width,height}, in pixels
    public int[] getDimensions()
    {
        return new int[]{winWidth, winHeight};
    }
}
