package uk.ac.rdg.resc.edal.graphics;

import java.util.List;

import uk.ac.rdg.resc.edal.coverage.grid.GridCoordinates2D;
import uk.ac.rdg.resc.edal.coverage.metadata.PlotStyle;

public class GridPointsFrameData extends FrameData {
    private List<GridCoordinates2D> pointData;

    public GridPointsFrameData(List<GridCoordinates2D> pointData) {
        super(PlotStyle.GRIDPOINT);
        this.pointData = pointData;
    }

    public List<GridCoordinates2D> getPointData() {
        return pointData;
    }
}