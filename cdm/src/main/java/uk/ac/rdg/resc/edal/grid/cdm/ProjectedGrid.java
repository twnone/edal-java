/*******************************************************************************
 * Copyright (c) 2011 The University of Reading
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the University of Reading, nor the names of the
 *    authors or contributors may be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *******************************************************************************/
package uk.ac.rdg.resc.edal.grid.cdm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.geotoolkit.metadata.iso.extent.DefaultGeographicBoundingBox;
import org.geotoolkit.referencing.crs.DefaultGeographicCRS;
import org.opengis.metadata.extent.GeographicBoundingBox;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import ucar.nc2.dataset.CoordinateAxis1D;
import ucar.nc2.dt.GridCoordSystem;
import ucar.unidata.geoloc.LatLonPoint;
import ucar.unidata.geoloc.Projection;
import ucar.unidata.geoloc.ProjectionImpl;
import ucar.unidata.geoloc.projection.RotatedPole;
import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.geometry.AbstractPolygon;
import uk.ac.rdg.resc.edal.geometry.BoundingBox;
import uk.ac.rdg.resc.edal.geometry.BoundingBoxImpl;
import uk.ac.rdg.resc.edal.geometry.Polygon;
import uk.ac.rdg.resc.edal.grid.GridCell2D;
import uk.ac.rdg.resc.edal.grid.GridCell2DImpl;
import uk.ac.rdg.resc.edal.grid.HorizontalGrid;
import uk.ac.rdg.resc.edal.grid.ReferenceableAxis;
import uk.ac.rdg.resc.edal.position.HorizontalPosition;
import uk.ac.rdg.resc.edal.position.LonLatPosition;
import uk.ac.rdg.resc.edal.util.AbstractImmutableArray;
import uk.ac.rdg.resc.edal.util.Array;
import uk.ac.rdg.resc.edal.util.GISUtils;
import uk.ac.rdg.resc.edal.util.GridCoordinates2D;
import uk.ac.rdg.resc.edal.util.cdm.CdmUtils;

/**
 * A two-dimensional {@link HorizontalGrid} that uses a {@link Projection} to
 * convert from lat-lon coordinates to grid coordinates.
 * 
 * @author Jon Blower
 * @author Guy Griffiths
 */
public class ProjectedGrid implements HorizontalGrid {
    private final ProjectionImpl proj;
    private final ReferenceableAxis<Double> xAxis;
    private final ReferenceableAxis<Double> yAxis;
    private final BoundingBox bbox;

    private transient Array<GridCell2D> domainObjs = null;

    /**
     * The GridCoordSystem must have one-dimensional x and y coordinate axes
     * 
     * @param coordSys
     */
    public ProjectedGrid(GridCoordSystem coordSys) {
        proj = coordSys.getProjection();
        /*
         * If this is a rotated-pole projection then the x axis is longitude and
         * hence wraps at 0/360 degrees.
         */
        boolean xAxisIsLongitude = proj instanceof RotatedPole;
        xAxis = CdmUtils.createReferenceableAxis((CoordinateAxis1D) coordSys.getXHorizAxis(),
                xAxisIsLongitude);
        yAxis = CdmUtils.createReferenceableAxis((CoordinateAxis1D) coordSys.getYHorizAxis());
        bbox = new BoundingBoxImpl(coordSys.getLatLonBoundingBox().getLonMin(), coordSys
                .getLatLonBoundingBox().getLatMin(), coordSys.getLatLonBoundingBox().getLonMax(),
                coordSys.getLatLonBoundingBox().getLatMax(), DefaultGeographicCRS.WGS84);
    }

    @Override
    public boolean contains(HorizontalPosition position) {
        if (GISUtils.crsMatch(getCoordinateReferenceSystem(),
                position.getCoordinateReferenceSystem())) {
            return xAxis.getCoordinateExtent().contains(position.getX())
                    && yAxis.getCoordinateExtent().contains(position.getY());
        } else {
            HorizontalPosition transformedPosition = GISUtils.transformPosition(position,
                    getCoordinateReferenceSystem());
            return xAxis.getCoordinateExtent().contains(transformedPosition.getX())
                    && yAxis.getCoordinateExtent().contains(transformedPosition.getY());
        }
    }

    /**
     * Always returns {@link DefaultGeographicCRS#WGS84}.
     */
    @Override
    public CoordinateReferenceSystem getCoordinateReferenceSystem() {
        return DefaultGeographicCRS.WGS84;
    }

    @Override
    public BoundingBox getBoundingBox() {
        return bbox;
    }

    @Override
    public GeographicBoundingBox getGeographicBoundingBox() {
        return new DefaultGeographicBoundingBox(bbox.getMinX(), bbox.getMaxX(), bbox.getMinY(),
                bbox.getMaxY());
    }

    @Override
    public long size() {
        return xAxis.size() * yAxis.size();
    }

    @Override
    public Array<GridCell2D> getDomainObjects() {
        if (domainObjs == null) {
            domainObjs = new AbstractImmutableArray<GridCell2D>(GridCell2D.class, getYSize(),
                    getXSize()) {
                @Override
                public GridCell2D get(final int... coords) {
                    double x = xAxis.getCoordinateValue(coords[1]);
                    double y = yAxis.getCoordinateValue(coords[0]);
                    /* Translate this point to lon-lat coordinates */
                    LatLonPoint latLon = proj.projToLatLon(x, y);
                    HorizontalPosition centre = new LonLatPosition(latLon.getLongitude(),
                            latLon.getLatitude());

                    Extent<Double> xExtent = xAxis.getCoordinateBounds(coords[1]);
                    Extent<Double> yExtent = yAxis.getCoordinateBounds(coords[0]);
                    List<HorizontalPosition> vertices = new ArrayList<HorizontalPosition>(4);
                    vertices.add(new LonLatPosition(xExtent.getLow(), yExtent.getLow()));
                    vertices.add(new LonLatPosition(xExtent.getHigh(), yExtent.getLow()));
                    vertices.add(new LonLatPosition(xExtent.getHigh(), yExtent.getHigh()));
                    vertices.add(new LonLatPosition(xExtent.getLow(), yExtent.getHigh()));
                    final List<HorizontalPosition> iVertices = Collections
                            .unmodifiableList(vertices);

                    Polygon footprint = new AbstractPolygon() {
                        @Override
                        public CoordinateReferenceSystem getCoordinateReferenceSystem() {
                            return ProjectedGrid.this.getCoordinateReferenceSystem();
                        }

                        @Override
                        public List<HorizontalPosition> getVertices() {
                            return iVertices;
                        }

                        @Override
                        public boolean contains(double x, double y) {
                            /*
                             * The x,y coordinates are in the external CRS of
                             * this grid
                             */
                            GridCoordinates2D posCoords = ProjectedGrid.this
                                    .findIndexOf(new HorizontalPosition(x, y,
                                            DefaultGeographicCRS.WGS84));
                            if (posCoords == null)
                                return false;
                            return (posCoords.getX() == coords[1] && posCoords.getY() == coords[0]);
                        }
                    };

                    return new GridCell2DImpl(coords, centre, footprint, ProjectedGrid.this);
                }
            };
        }
        return domainObjs;
    }

    @Override
    public GridCoordinates2D findIndexOf(HorizontalPosition position) {
        if (GISUtils.crsMatch(getCoordinateReferenceSystem(),
                position.getCoordinateReferenceSystem())) {
            return new GridCoordinates2D(xAxis.findIndexOf(position.getX()),
                    yAxis.findIndexOf(position.getY()));
        } else {
            HorizontalPosition transformedPosition = GISUtils.transformPosition(position,
                    getCoordinateReferenceSystem());
            return new GridCoordinates2D(xAxis.findIndexOf(transformedPosition.getX()),
                    yAxis.findIndexOf(transformedPosition.getY()));
        }
    }

    @Override
    public int getXSize() {
        return xAxis.size();
    }

    @Override
    public int getYSize() {
        return yAxis.size();
    }
}