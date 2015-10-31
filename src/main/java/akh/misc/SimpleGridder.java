/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package akh.misc;

import java.io.IOException;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.TiePointGrid;

/**
 *
 * @author akheckel
 */
public class SimpleGridder {

    public final int nlat;
    public final int nlon;
    public final double dlat;
    public final double dlon;
    public final double[] dataGrd;
    public final int[] countGrd;

    public SimpleGridder(double dlat, double dlon) {
        this.dlat = dlat;
        this.dlon = dlon;
        this.nlat = (int) (180.0 / dlat);
        this.nlon = (int) (180.0 / dlon);
        this.dataGrd = new double[nlat * nlon];
        this.countGrd = new int[nlat * nlon];
    }

    public void addGrid(Product p, String bandName) {
        int pWidth = p.getSceneRasterWidth();
        int pHeight = p.getSceneRasterHeight();

        TiePointGrid latTpg = p.getTiePointGrid("latitude");
        TiePointGrid lonTpg = p.getTiePointGrid("longitude");
        Band aotNdBand = p.getBand(bandName);

        float[] lat = new float[pWidth];
        float[] lon = new float[pWidth];
        float[] data = new float[pWidth];

        final int offset = 0;
        final int skip = 1;
        int ilat;
        int ilon;

        try {
            for (int iy = offset; iy < pHeight; iy += skip) {
                if (iy % 10 == 9) {
                    System.err.printf("L3 binning %5.1f%%\r", (float) (iy) / (float) (pHeight) * 100f);
                }
                latTpg.readPixels(0, iy, pWidth, 1, lat);
                lonTpg.readPixels(0, iy, pWidth, 1, lon);
                aotNdBand.readPixels(0, iy, pWidth, 1, data);

                for (int ix = offset; ix < pWidth; ix += skip) {
                    if (data[ix] > 0) {
                        ilat = (int) Math.floor(lat[ix]) + 90;
                        if (ilat == 180) {
                            ilat = 179;
                        }
                        ilon = (int) Math.floor(lon[ix]) + 180;
                        if (ilon == 360) {
                            ilon = 359;
                        }
                        final int idx = ilat * nlon + ilon;
                        dataGrd[idx] += data[ix];
                        countGrd[idx]++;
                    }
                }
            }
            System.err.printf("L3 binning done                     \n");
        } catch (IOException ex) {
            System.err.println(ex.getMessage());
        }
    }
}
