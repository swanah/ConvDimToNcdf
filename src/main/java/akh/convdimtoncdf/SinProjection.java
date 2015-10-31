/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package akh.convdimtoncdf;

import java.awt.Point;
import java.awt.geom.Point2D;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;

/**
 *
 * @author akheckel
 */
public class SinProjection {
    final double radiusEarth = 6378.137;
    final double resolution;
    final int nEquator;
    final double rg;
    final double u0;
    final double v0;
    final double lon0;
    final int nrow;
    final double lat[];
    final int[] nbin;
    final int[] basebin;

    public SinProjection() {
        this(4008);
    }

    public SinProjection(int nEquator) {
        this.nEquator = nEquator;
        resolution = 2 * Math.PI * radiusEarth / nEquator;
        
        rg = nEquator / 2 / Math.PI; //radiusEarth / resolution;
        
        //grid coord of lat=0, lon=0
        u0 = -(nEquator)/2.; //-(Math.PI * rg + 0.5);
        v0 = -(nEquator)/4.; //-(Math.PI * rg / 2 + 0.5);
        
        // center lon
        lon0 = 0.;
        nrow = (int)((nEquator + 0.5) / 2);
        
        lat = new double[nrow];
        nbin = new int[nrow];
        basebin = new int[nrow];
        int nEqMod2 = ((nEquator % 2) == 0)?1:0;
        for (int i=0; i<nrow; i++){
            lat[i] = (i+0.5)*360/nEquator-90.; //(i + 0.5) / rg / Math.PI * 180 - 90;
            nbin[i] = (int)(Math.cos(Math.toRadians(lat[i])) * nrow * 2);
            if ((nbin[i] % 2) == nEqMod2) {
                nbin[i]++;
            }
            if (i>0) basebin[i] = basebin[i-1]+nbin[i-1];
        }
    }
    
    PixelPos getPixelPos(GeoPos gp, PixelPos pp){
        if (pp == null) pp = new PixelPos();
        double dlon = gp.lon - lon0;
        dlon = Math.toDegrees(Math.atan2(Math.sin(Math.toRadians(dlon)), Math.cos(Math.toRadians(dlon))));
        double phi = Math.toRadians(gp.lat);
        double lam = Math.toRadians(dlon);

        pp.x =  (float) (rg * lam * Math.cos(phi) - u0);
        pp.y =  (float) (rg * phi                 - v0);
        return pp;
    }
    
    Point getBinPixelPos(float lat, float lon){
        PixelPos pp = getPixelPos(new GeoPos(lat, lon), null);
        return new Point((int)pp.x, (int)pp.y);
    }
    
    GeoPos getGeoPos(PixelPos pp, GeoPos gp){
        if (gp == null) gp = new GeoPos();

        double phi = (pp.y + v0) / rg;
        double lam = (pp.x + u0) / (rg * Math.cos(phi));

        gp.lat = (float) Math.toDegrees(phi);
        gp.lon = (float) (Math.toDegrees(lam) + lon0);

        if (gp.lon <-180  || gp.lon > 180) gp.lon = -999;
        return gp;
    }
}
