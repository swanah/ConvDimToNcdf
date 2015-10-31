/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package akh.convdimtoncdf;

import java.io.IOException;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.TiePointGrid;

/**
 *
 * @author akheckel
 */
public class ProductInfoData {
    final int numberValidPixel;

    public ProductInfoData(Product p, String bandName) throws IOException {
        int validPixelCount = 0;
        int pHeight = p.getSceneRasterHeight();
        int pWidth = p.getSceneRasterWidth();
        TiePointGrid latTpg = p.getTiePointGrid("latitude");
        TiePointGrid lonTpg = p.getTiePointGrid("longitude");
        Band aodBand = p.getBand(bandName);
        float[] lat = new float[pWidth];
        float[] lon = new float[pWidth];
        float[] aotNd = new float[pWidth];
        final int offset = 4;
        final int skip = 9;
        int[] count = new int[]{0,0};
        for (int iy=offset; iy<pHeight; iy+=skip){
            aodBand.readPixels(0, iy, pWidth, 1, aotNd);
            for (int ix=offset; ix<pWidth; ix+=skip){
                if (aotNd[ix]>0){
                    validPixelCount++;
                }           
            }
        }
            
        this.numberValidPixel = 0;
    }
    
}
