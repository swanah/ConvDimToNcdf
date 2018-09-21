/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package akh.convdimtoncdf;

import java.awt.Point;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;

/**
 *
 * @author akheckel
 */
public class SinProduct {
    SinProjection proj;
    Product p;
    final String bandNames[];
    final int nBands;
    final int hashMapCapacity = 30000;
    int nCells;
    Map<Point, SinCell> cellMap;
    float[][] latArr, lonArr;
    float[][] bandsArr;
    float minLon=180, minLat=90, maxLon=-180, maxLat=-90;
    ProductData.UTC startTime;
    ProductData.UTC endTime;
    ArrayList<String> productList;
    String source;
    
    SinProduct(String bandNames[]) {
        this(new SinProjection(), bandNames, null); //defaults to 4008 cells at equator
    }
    
    SinProduct(String bandNames[], Product p) {
        this(new SinProjection(), bandNames, p); //defaults to 4008 cells at equator
    }
    
    SinProduct(int nEquator, String bandNames[], Product p) {
        this(new SinProjection(nEquator), bandNames, p);
    }
    
    SinProduct(SinProjection proj, String bandNames[], Product p) {
        this.proj = proj;
        this.bandNames = bandNames;
        this.nBands = bandNames.length;
        this.cellMap = new LinkedHashMap<Point, SinCell>(hashMapCapacity);//new HashMap<>(hashMapCapacity);
        this.p = p;
    }


    public String getProductListAsString() {
        String s = null;
        for (String p : productList){
            if (s == null || s.isEmpty()){
                s = p;
            }
            else {
                s += " \n" + p;
            }
        }
        return s;
    }
    
    
    /*    void binToGrid(GeoPos gp, float val){
        PixelPos pp = proj.getPixelPos(gp, null);
        int index = (int)pp.x + (int)pp.y * proj.nEquator;
        Assert.argument((index>=0 && index<grid.length));
        grid[index]+=val;
        count[index]++;
    }*/

    void add(float lat, float lon, float[] vals) {
        Point p = proj.getBinPixelPos(lat, lon);
        if (cellMap.containsKey(p)){
            cellMap.get(p).addValues(vals);
        }
        else {
            cellMap.put(p, new SinCell(vals));
            minLon = Math.min(minLon, lon);
            maxLon = Math.max(maxLon, lon);
            minLat = Math.min(minLat, lat);
            maxLat = Math.max(maxLat, lat);
        }
        nCells = cellMap.size();
    }
    
    void convCellsToArray(){
        int iCell=0;
        float[] xOffset = {0.5f, 0.0f, 0.0f, 1.0f, 1.0f};
        float[] yOffset = {0.5f, 0.0f, 1.0f, 1.0f, 0.0f};
        nCells = cellMap.size();
        latArr = new float[5][nCells];
        lonArr = new float[5][nCells];
        bandsArr = new float[nBands][nCells];
        GeoPos gp = new GeoPos();
        for (Point p : cellMap.keySet()){
            for (int j=0; j<5; j++){
                gp = proj.getGeoPos(new PixelPos(p.x+xOffset[j], p.y+yOffset[j]), gp);
                latArr[j][iCell] = gp.lat;
                lonArr[j][iCell] = gp.lon;
            }
            SinCell cell = cellMap.get(p);
            for (int ib=0; ib<nBands; ib++){
                bandsArr[ib][iCell] = (cell.count > 0) ? cell.bandSums[ib]/cell.count : -999;
            }
            iCell++;
        }
    }
}
