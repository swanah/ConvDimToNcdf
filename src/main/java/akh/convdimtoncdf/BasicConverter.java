/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package akh.convdimtoncdf;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFileWriter;

/**
 *
 * @author akheckel
 */
abstract class BasicConverter {
    protected float[][] modelInfo;

    abstract void convert(Product p, String fileName, DataVersionNumbers version);
    
    abstract NetcdfFileWriter createNetCdfFile(String ncdfName, Product p, DataVersionNumbers version);

    abstract NetcdfFileWriter createNetCdfFile(String ncdfName, SinProduct sinP, DataVersionNumbers version);
    
    abstract void createGlobalAttrb(NetcdfFileWriter ncfile, Product p, String ncdfName, DataVersionNumbers version);
    
    abstract void createGlobalAttrb(NetcdfFileWriter ncfile, SinProduct sinP, String ncdfName, DataVersionNumbers version);
    
    abstract void createVcdfVar(NetcdfFileWriter ncfile, NetcdfVariableProperties var, ArrayList<Dimension> dimList);

    
    public long utcToMilliSec1970(ProductData.UTC pixTime) {
        Date pixDate = pixTime.getAsDate();
        long milliSec1970 = pixDate.getTime();
        return milliSec1970;
    }

    protected float[][] readAerosolModelInfo(String resourceName) throws IOException{
        InputStream inS = ProdConverterL2.class.getResourceAsStream("ccimodel.info");
        BufferedReader bufR = new BufferedReader(new InputStreamReader(inS));
        float[][] modelInfo = new float[35][6];
        String line;
        String[] tmparr;
        int iModel = 0;
        while ((line = bufR.readLine()) != null) {
            if (!line.startsWith("*")) {
                tmparr = line.split("\\s+");
                iModel = Integer.parseInt(tmparr[0])-1;
                for (int i = 0; i < 6; i++) {
                    modelInfo[iModel][i] = Float.parseFloat(tmparr[i + 1]);
                }
            }
        }
        if (iModel != 34) {
            throw new IOException("Error reading model info");
        }
        if (bufR != null) {
            bufR.close();
        }
        return modelInfo;
    }

    protected float[] getSpecAots(float aot550, float nsd, float seas, float str, float weak) {
        final int idxNsd  = 35;
        final int idxSeaS = 15;
        final int idxStr  = 5;
        final int idxWeak = 1;
        float[] specAots = new float[4];
        for (int i=0; i<4; i++){
            specAots[i]  = nsd  * modelInfo[idxNsd-1][2+i];
            specAots[i] += seas * modelInfo[idxSeaS-1][2+i];
            specAots[i] += str  * modelInfo[idxStr-1][2+i];
            specAots[i] += weak * modelInfo[idxWeak-1][2+i];
            specAots[i] *= aot550;
            specAots[i] /= 100;
        }
        return specAots;
    }

    protected float[] getSpecAots(float aot550, int ma) {
        int modelIdx = 0;
        switch (ma) {
            case 1: modelIdx = 1; break;
            case 2: modelIdx = 2; break;
            case 3: modelIdx = 3; break;
            case 5: modelIdx = 21; break;
            case 6: modelIdx = 13; break;
            case 7: modelIdx = 32; break;
        }
        float[] specAots = new float[4];
        for (int i=0; i<4; i++){
            specAots[i] = aot550 * modelInfo[modelIdx-1][i];
        }
        return specAots;
    }

    protected float[] getSpecAotsPure(float aot550, int ma) {
        int modelIdx = 0;
        switch (ma) {
            case 1: modelIdx = 1; break;
            case 2: modelIdx = 5; break;
            case 3: modelIdx = 15; break;
            case 5: modelIdx = 35; break;
        }
        float[] specAots = new float[4];
        for (int i=0; i<4; i++){
            specAots[i] = aot550 * modelInfo[modelIdx-1][i];
        }
        return specAots;
    }

    protected float getRaz(float saa, float vaa) {
        float raz = Math.abs(saa - vaa);
        return (raz > 180) ? 360.0f - raz : raz;
    }
}
