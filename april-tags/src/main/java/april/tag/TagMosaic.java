package april.tag;

import java.awt.image.BufferedImage;
import java.util.*;

import april.jmat.geom.GLine2D;

// NOTE: This class uses a COLUMN major convention to match the x,y convention for indexing the mosaic.
//       e.g mosaic.getPositionMeters(col, row)

public class TagMosaic
{
    List<double[]> tagPositionsMeters = new ArrayList<double[]>();
    List<double[]> tagPositionsPixels = new ArrayList<double[]>();

    // x=column, y=row
    List<int[]> tagColumnAndRow = new ArrayList<int[]>();

    public TagFamily tf;
    public double tagSpacingMeters;

    int mosaicWidth, mosaicHeight;
    int tagWidthPixels, tagHeightPixels;

    BufferedImage mosaicImage;

    public TagMosaic(TagFamily tf, double tagSpacingMeters)
    {
        this.tf = tf;
        this.tagSpacingMeters = tagSpacingMeters;

        tagWidthPixels  = tf.d + tf.whiteBorder*2 + tf.blackBorder*2;
        tagHeightPixels = tagWidthPixels;

        mosaicWidth     = (int) Math.sqrt(tf.codes.length);
        mosaicHeight    = tf.codes.length / mosaicWidth + 1;

        for (int row = 0; row < mosaicHeight; row++) {
            for (int col = 0; col < mosaicWidth; col++) {

                int id = row*mosaicWidth + col;

                if (id >= tf.codes.length)
                    continue;

                tagPositionsMeters.add(getPositionMeters(col, row));
                tagPositionsPixels.add(getPositionPixels(col, row));
                tagColumnAndRow.add(new int[] { col, row });

                assert(tagPositionsMeters.size() == id+1);
                assert(tagPositionsPixels.size() == id+1);
                assert(tagColumnAndRow.size() == id+1);
            }
        }

        assert(tagPositionsMeters.size() == tf.codes.length);
        assert(tagPositionsPixels.size() == tf.codes.length);
        assert(tagColumnAndRow.size() == tf.codes.length);
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Utility functions

    /** Get the tag ID for a tag at this {column, row} position.
      */
    public int getID(int column, int row)
    {
        return row*mosaicWidth+column;
    }

    /** Get the column for a tag.
      */
    public int getColumn(int id)
    {
        int colrow[] = tagColumnAndRow.get(id);
        return colrow[0];
    }

    /** Get the row for a tag.
      */
    public int getRow(int id)
    {
        int colrow[] = tagColumnAndRow.get(id);
        return colrow[1];
    }

    /** Get the position of this tag on the tag mosaic image. Positions
      * are relative to the top left corner of tag zero in pixels.
      */
    public double[] getPositionPixels(int id)
    {
        return tagPositionsPixels.get(id);
    }

    /** Get the position of this tag on the physical tag mosaic. Positions are
      * measured from the center of tag zero in meters.
      */
    public double[] getPositionMeters(int id)
    {
        return tagPositionsMeters.get(id);
    }

    /** Get the position on the tag mosaic (in meters) from the column and row.
      */
    public double[] getPositionMeters(double col, double row)
    {
        return new double[] { col*tagSpacingMeters ,
                              row*tagSpacingMeters ,
                              0                    };
    }

    /** Get the position on the tag mosaic (in pixels) from the column and row.
      */
    public double[] getPositionPixels(int col, int row)
    {
        return new double[] { tagWidthPixels  * (0.5 + col) ,
                              tagHeightPixels * (0.5 + row) ,
                              0                             };
    }

    /** Get the image of the whole mosaic (from the TagFamily).
      */
    public BufferedImage getImage()
    {
        if (mosaicImage == null)
            mosaicImage = tf.getAllImagesMosaic();

        return mosaicImage;
    }

    /** Get the image of a single tag (from the TagFamily).
      */
    public BufferedImage getImage(int id)
    {
        return tf.makeImage(id);
    }

    ////////////////////////////////////////
    // size functions

    public int getMosaicWidth()
    {
        return this.mosaicWidth;
    }

    public int getMosaicHeight()
    {
        return this.mosaicHeight;
    }

    public int getTagWidthPixels()
    {
        return this.tagWidthPixels;
    }

    public int getTagHeightPixels()
    {
        return this.tagHeightPixels;
    }

    ////////////////////////////////////////////////////////////////////////////////
    // Group actual tag detections into row and column groups

    public class GroupedDetections
    {
        public final static int COL_GROUP = 0;
        public final static int ROW_GROUP = 1;
        public final static int POS_DIAG_GROUP = 2;
        public final static int NEG_DIAG_GROUP = 3;
        public int type;

        // row or column index in the tag mosaic
        public int index;

        // list of detections in this row or column
        public ArrayList<TagDetection> detections;

        private GLine2D line;

        public GLine2D fitLine()
        {
            if (line == null) {
                ArrayList<double[]> centers = new ArrayList<double[]>();
                for (TagDetection d : detections)
                    centers.add(d.cxy);

                line = GLine2D.lsqFit(centers);
            }

            return line;
        }
    }

    /** Sort the tag detections into groups by row.
      */
    public ArrayList<GroupedDetections> getRowDetections(List<TagDetection> detections)
    {
        return getGroupedDetections(detections, GroupedDetections.ROW_GROUP);
    }

    /** Sort the tag detections into groups by column.
      */
    public ArrayList<GroupedDetections> getColumnDetections(List<TagDetection> detections)
    {
        return getGroupedDetections(detections, GroupedDetections.COL_GROUP);
    }

    /** Sort the tag detections into groups by diagonal (positive 45 degree diagonal).
      */
    public ArrayList<GroupedDetections> getPositiveDiagonalDetections(List<TagDetection> detections)
    {
        return getGroupedDetections(detections, GroupedDetections.POS_DIAG_GROUP);
    }

    /** Sort the tag detections into groups by diagonal (negative 45 degree diagonal).
      */
    public ArrayList<GroupedDetections> getNegativeDiagonalDetections(List<TagDetection> detections)
    {
        return getGroupedDetections(detections, GroupedDetections.NEG_DIAG_GROUP);
    }

    private ArrayList<GroupedDetections> getGroupedDetections(List<TagDetection> detections,
                                                              int groupType)
    {
        HashMap<Integer,ArrayList<TagDetection>> groupLists = new HashMap<Integer,ArrayList<TagDetection>>();

        for (int i=0; i < detections.size(); i++) {

            TagDetection d = detections.get(i);
            int group = getGroupNumber(d.id, groupType);

            ArrayList<TagDetection> groupList = groupLists.get(group);
            if (groupList == null)
                groupList = new ArrayList<TagDetection>();

            groupList.add(d);
            groupLists.put(group, groupList);
        }

        Set<Integer> groupKeys = groupLists.keySet();

        ArrayList<GroupedDetections> groups = new ArrayList<GroupedDetections>();
        for (Integer group : groupKeys) {

            GroupedDetections gd = new GroupedDetections();
            gd.type = groupType;
            gd.index = group;
            gd.detections = groupLists.get(group);

            groups.add(gd);
        }

        return groups;
    }

    public int getGroupNumber(int id, int groupType)
    {
        int colrow[] = tagColumnAndRow.get(id);
        int col = colrow[0];
        int row = colrow[1];

        switch (groupType)
        {
            case GroupedDetections.COL_GROUP:
                return col;
            case GroupedDetections.ROW_GROUP:
                return row;
            case GroupedDetections.POS_DIAG_GROUP:
                return row + col;
            case GroupedDetections.NEG_DIAG_GROUP:
                return (row - col) + (mosaicWidth -1);
            default:
                return -1;
        }
    }

    public static void main(String args[])
    {
        TagFamily tf = new Tag36h11();
        TagMosaic tm = new TagMosaic(tf, 1.0);

        int mosaicWidth = tm.getMosaicWidth();
        int mosaicHeight = tm.getMosaicHeight();

        int groupTypes[] = new int[] { GroupedDetections.COL_GROUP,
                                       GroupedDetections.ROW_GROUP,
                                       GroupedDetections.POS_DIAG_GROUP,
                                       GroupedDetections.NEG_DIAG_GROUP };
        String groupNames[] = new String[] { "Column", "Row", "Positive diagonal", "Negative diagonal" };

        for (int i=0; i < groupTypes.length; i++) {
            int groupType = groupTypes[i];

            System.out.printf("%s grouping numbers:\n", groupNames[i]);

            for (int row=0; row < mosaicHeight; row++) {
                for (int col=0; col < mosaicWidth; col++) {
                    int id = row*mosaicWidth + col;
                    if (id >= tf.codes.length)
                        continue;

                    int group = tm.getGroupNumber(id, groupType);

                    System.out.printf("%3d ", group);
                }

                System.out.println();
            }

            System.out.println();
        }
    }
}
