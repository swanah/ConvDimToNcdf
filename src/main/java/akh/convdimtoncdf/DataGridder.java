/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package akh.convdimtoncdf;

import java.io.*;
import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.ProductData.UTC;
import org.esa.beam.framework.datamodel.TiePointGrid;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

/**
 *
 * @author akheckel
 */
public class DataGridder {
    float[][][] aod550Grid;
    float[][][] sigAod550Grid;
    //float[][][] aod0555Grid;
    //float[][][] sigAod555Grid;
    float[][][] aod670Grid;
    float[][][] sigAod659Grid;
    float[][][] aod870Grid;
    float[][][] sigAod865Grid;
    float[][][] aod1600Grid;
    float[][][] sigAod1600Grid;
    float[][][] rsurf0555Grid;
    float[][][] rsurf0659Grid;
    float[][][] rsurf0865Grid;
    float[][][] rsurf1600Grid;
    float[][][] angstrom659Grid;
    float[][][] angstrom865Grid;
    float[][][] fineModeAodGrid;
    float[][][] absModeAodGrid;
    float[][][] ssaGrid;
    float[][][] dustModeAodGrid;
    float[][][] cldFracGrid;
    float[][][] landFracGrid;
    float[][][][] szaGrid;
    float[][][][] vzaGrid;
    float[][][][] razGrid;

    int[][] countGrid;
    final float[][] specAotInfo;
    private UTC startTime;
    private UTC endTime;
    private String products;
    private String source;
    private boolean doSyn;
    private boolean doSurfRefl;
    private int aodVersionId;
    private boolean doRecomputeAAOD;
    
    public DataGridder() throws Exception {
        this(false);
    }
    
    public DataGridder(boolean doMoreUnc) throws Exception {
        aod550Grid = new float[2][180][360];
        sigAod550Grid = new float[5][180][360]; // mean, sdev, min, max, sum of squares
        //aod0555Grid = new float[2][180][360];
        aod670Grid = new float[2][180][360];
        aod870Grid = new float[2][180][360];
        aod1600Grid = new float[2][180][360];
        //sigAod555Grid = new float[4][180][360];
        sigAod659Grid = new float[5][180][360];
        sigAod865Grid = new float[5][180][360];
        sigAod1600Grid = new float[5][180][360];

        initMinMaxArrays();
        
        angstrom659Grid = new float[2][180][360];
        angstrom865Grid = new float[2][180][360];
        fineModeAodGrid = new float[2][180][360];
        absModeAodGrid  = new float[2][180][360];
        dustModeAodGrid = new float[2][180][360];
        ssaGrid         = new float[2][180][360];

        rsurf0555Grid = new float[2][180][360];
        rsurf0659Grid = new float[2][180][360];
        rsurf0865Grid = new float[2][180][360];
        rsurf1600Grid = new float[2][180][360];
        cldFracGrid = new float[2][180][360];
        landFracGrid = new float[2][180][360];
        
        szaGrid = new float[2][180][360][2];
        vzaGrid = new float[2][180][360][2];
        razGrid = new float[2][180][360][2];
        countGrid = new int[180][360];

        startTime = new UTC(Double.MAX_VALUE);
        endTime = new UTC(Double.MIN_VALUE);
        products = "";
        specAotInfo = readSpecAot();
        
    }

    private void initMinMaxArrays() {
        for (int i=0; i<180; i++){
            for (int j=0; j<360; j++){
                sigAod550Grid[2][i][j] = Float.MAX_VALUE;
                sigAod550Grid[3][i][j] = Float.MIN_VALUE;
                //sigAod555Grid[2][i][j] = Float.MAX_VALUE;
                //sigAod555Grid[3][i][j] = Float.MIN_VALUE;
                sigAod659Grid[2][i][j] = Float.MAX_VALUE;
                sigAod659Grid[3][i][j] = Float.MIN_VALUE;
                sigAod865Grid[2][i][j] = Float.MAX_VALUE;
                sigAod865Grid[3][i][j] = Float.MIN_VALUE;
                sigAod1600Grid[2][i][j] = Float.MAX_VALUE;
                sigAod1600Grid[3][i][j] = Float.MIN_VALUE;
            }
        }
    }

    public UTC getEndTime() {
        return endTime;
    }

    public String getProducts() {
        return products;
    }

    public String getSourceStr() {
        return source;
    }

    public UTC getStartTime() {
        return startTime;
    }

    public boolean isDoSyn() {
        return doSyn;
    }

    public boolean isDoSurfRefl() {
        return doSurfRefl;
    }

    void binNcdfToGridV4(String fname, S3DataVersionNumbers version) throws IOException {
        doSurfRefl = false;
        NetcdfFile ncF = NetcdfFile.open(fname);
        String pname = getNcdfId(ncF);
        System.out.println("binning V" + version + " - " + fname);
        UTC pStartTime = getNcdfTime(ncF, "@time_coverage_start");
        if (pStartTime.getMJD() < startTime.getMJD()) startTime = pStartTime;
        UTC pEndTime = getNcdfTime(ncF, "@time_coverage_end");
        if (pEndTime.getMJD() > endTime.getMJD()) endTime = pEndTime;
        if (products.length() == 0) {
            products = pname;
        } else {
            products += " \n" + pname;
        }
        Attribute att = ncF.findGlobalAttribute("source");
        source = att.getStringValue();
        int pWidth = getNcdfRasterWidth(ncF);
        int pHeight = getNcdfRasterHeight(ncF);

        double[] lat = getVarAsDoubleArr(ncF, "latitude");
        double[] lon = getVarAsDoubleArr(ncF, "longitude");
        String[] aodNames = new String[]{"AOD_0550_fltr", "AOD_0659", "AOD_0865", "AOD_1610", "AOD_2250"};
        String[] aodUncNames = new String[]{"AOD_0550_uncertainty", "AOD_0659_uncertainty", "AOD_0865_uncertainty", "AOD_1610_uncertainty", "AOD_2250_uncertainty"};
        String[] sdrNames = new String[]{"S1_SDR_n", "S2_SDR_n", "S3_SDR_n", "S5_SDR_n", "S6_SDR_n"};
        float[][] aod = new float[aodNames.length][];
        float[][] aodUnc = new float[aodNames.length][];
        float[][] sdr = new float[aodNames.length][];
        for (int i = 0; i<aodNames.length; i++){
            aod[i] = getVarAsFloatArr(ncF, aodNames[i]);
            aodUnc[i] = getVarAsFloatArr(ncF, aodUncNames[i]);
            if (doSurfRefl){
                sdr[i] = getVarAsFloatArr(ncF, sdrNames[i]);
            }
        }
        float[] ang870  = getVarAsFloatArr(ncF, "ANG550_870");
        float[] fineAod = getVarAsFloatArr(ncF, "AAOD550");
        float[] absAod  = getVarAsFloatArr(ncF, "FM_AOD550");
        float[] dustAod = getVarAsFloatArr(ncF, "D_AOD550");
        float[] ssa     = getVarAsFloatArr(ncF, "SSA550");
        
        //float[] szaNad = getVarAsFloatArr(ncF, "solar_zenith_tn");
        //float[] vzaNad = getVarAsFloatArr(ncF, "sat_zenith_tn");
        //float[] razN   = getVarAsFloatArr(ncF, "rel_azimuth_an");
        //float[] razF   = getVarAsFloatArr(ncF, "rel_azimuth_ao");
        
        int ilat;
        int ilon;
        int idx;
        for (int iy=0; iy<pHeight; iy++){
            for (int ix=0; ix<pWidth; ix++){
                idx = iy * pWidth + ix;
                if (aod[0][idx]>0){
                    ilat = lat2idx(lat[idx]);
                    ilon = lon2idx(lon[idx]);

                    countGrid[ilat][ilon]++;

                    computeRunningMeanVar(aod550Grid, countGrid, ilat, ilon, aod[0][idx]);
                    computeRunningMeanVar(aod670Grid, countGrid, ilat, ilon, aod[1][idx]);
                    computeRunningMeanVar(aod870Grid, countGrid, ilat, ilon, aod[2][idx]);
                    computeRunningMeanVar(aod1600Grid, countGrid, ilat, ilon, aod[3][idx]);

                    computeRunningMeanVarMinMax(sigAod550Grid, countGrid, ilat, ilon, aodUnc[0][idx]);
                    computeRunningMeanVarMinMax(sigAod659Grid, countGrid, ilat, ilon, aodUnc[1][idx]);
                    computeRunningMeanVarMinMax(sigAod865Grid, countGrid, ilat, ilon, aodUnc[2][idx]);
                    computeRunningMeanVarMinMax(sigAod1600Grid, countGrid, ilat, ilon, aodUnc[3][idx]);

                    //computeRunningMeanVar(angstrom659Grid, countGrid, ilat, ilon, (float)(Math.log(aotNd[ix] / sAot0659[ix]) * angWvlLog659));
                    computeRunningMeanVar(angstrom865Grid, countGrid, ilat, ilon, ang870[idx]);

                    computeRunningMeanVar(fineModeAodGrid, countGrid, ilat, ilon, fineAod[idx]);
                    computeRunningMeanVar(absModeAodGrid,  countGrid, ilat, ilon, absAod[idx]);
                    computeRunningMeanVar(dustModeAodGrid, countGrid, ilat, ilon, dustAod[idx]);
                    computeRunningMeanVar(ssaGrid, countGrid, ilat, ilon, ssa[idx]);

                    if (doSurfRefl) {
                        computeRunningMeanVar(rsurf0555Grid, countGrid, ilat, ilon, sdr[0][idx]);
                        computeRunningMeanVar(rsurf0659Grid, countGrid, ilat, ilon, sdr[0][idx]);
                        computeRunningMeanVar(rsurf0865Grid, countGrid, ilat, ilon, sdr[0][idx]);
                        computeRunningMeanVar(rsurf1600Grid, countGrid, ilat, ilon, sdr[0][idx]);
                    }
                    //computeRunningMeanVar(cldFracGrid, countGrid, ilat, ilon, cldFrac[ix]);
                    //computeRunningMeanVar(landFracGrid, countGrid, ilat, ilon, (aotFlags[ix]&1));

                    //computeRunningMeanVar(szaGrid, countGrid, ilat, ilon, (szaNad[idx]), 0);
                    //computeRunningMeanVar(vzaGrid, countGrid, ilat, ilon, (vzaNad[idx]), 0);
                    //computeRunningMeanVar(razGrid, countGrid, ilat, ilon, razN[idx], razF[idx]);
                }
            }
        }
                
        ncF.close();
    }

    public void binToGridV4(Product p, DataVersionNumbers version){
        aodVersionId = (version.equals(DataVersionNumbers.v4_31)) ? 3 : 1;
        doRecomputeAAOD = (version.equals(DataVersionNumbers.v4_32));
        doSurfRefl = p.containsBand(String.format("reflec_surf_nadir_0550_%d", aodVersionId));
        doSyn = version.isGE(DataVersionNumbers.vSyn1_0);
        String fname = p.getFileLocation().getPath();
        String pname = p.getFileLocation().getName();
        if (pname.endsWith(".dim")){
            pname = pname.replace(".dim", ".N1");
        }
        System.out.println("binning V" + version + " - " + fname);
        try {
            //p = ProductIO.readProduct(fname);
            UTC pStartTime = p.getStartTime();
            if (pStartTime.getMJD() < startTime.getMJD()) startTime = pStartTime;
            UTC pEndTime = p.getEndTime();
            if (pEndTime.getMJD() > endTime.getMJD()) endTime = pEndTime;
            if (products.length() == 0) {
                products = pname;
            } else {
                products += " \n" + pname;
            }
            int pWidth = p.getSceneRasterWidth();
            int pHeight = p.getSceneRasterHeight();
            
            TiePointGrid latTpg = p.getTiePointGrid("latitude");
            TiePointGrid lonTpg = p.getTiePointGrid("longitude");
            
            TiePointGrid seaNadTpg = null;
            TiePointGrid saaNadTpg = null;
            TiePointGrid veaNadTpg = null;
            TiePointGrid vaaNadTpg = null;

            TiePointGrid seaFwdTpg = null;
            TiePointGrid saaFwdTpg = null;
            TiePointGrid veaFwdTpg = null;
            TiePointGrid vaaFwdTpg = null;
            
            if (doSyn){
                seaNadTpg = p.getTiePointGrid("sun_zenith");
                saaNadTpg = p.getTiePointGrid("sun_azimuth");
                veaNadTpg = p.getTiePointGrid("view_zenith");
                vaaNadTpg = p.getTiePointGrid("view_azimuth");
            }
            else {
                seaNadTpg = p.getTiePointGrid("sun_elev_nadir");
                saaNadTpg = p.getTiePointGrid("sun_azimuth_nadir");
                veaNadTpg = p.getTiePointGrid("view_elev_nadir");
                vaaNadTpg = p.getTiePointGrid("view_azimuth_nadir");

                seaFwdTpg = p.getTiePointGrid("sun_elev_fward");
                saaFwdTpg = p.getTiePointGrid("sun_azimuth_fward");
                veaFwdTpg = p.getTiePointGrid("view_elev_fward");
                vaaFwdTpg = p.getTiePointGrid("view_azimuth_fward");
            }

            Band aotNdBand = p.getBand(String.format("aot_nd_%d", aodVersionId+1));
            Band aotUncNdBand = p.getBand(String.format("aot_brent_nd_%d", aodVersionId));
            Band fotB = p.getBand(String.format("frac_fine_total_%d", aodVersionId));
            Band wofB = p.getBand("frac_weakAbs_fine");
            Band docB = p.getBand("frac_dust_coarse");
            Band cldFracB = p.getBand("cld_frac");
            Band aotFlagsB = p.getBand("aot_flags");

            Band absAotB = p.getBand(String.format("aaot_nd_%d", aodVersionId));
            Band ssaB = p.getBand("ssa");

            //Band sAot0550B = p.getBand("aot_nd_0550_1");
            Band sAot0670B = p.getBand(String.format("aot_nd_0670_%d", aodVersionId));
            Band sAot0870B = p.getBand(String.format("aot_nd_0870_%d", aodVersionId));
            Band sAot1600B = p.getBand(String.format("aot_nd_1600_%d", aodVersionId));

            Band sref0555B = null;
            Band sref0659B = null;
            Band sref0865B = null;
            Band sref1610B = null;
            if (doSurfRefl){
                sref0555B = p.getBand(String.format("reflec_surf_nadir_0550_%d", aodVersionId));
                sref0659B = p.getBand(String.format("reflec_surf_nadir_0670_%d", aodVersionId));
                sref0865B = p.getBand(String.format("reflec_surf_nadir_0870_%d", aodVersionId));
                sref1610B = p.getBand(String.format("reflec_surf_nadir_1600_%d", aodVersionId));
            }
            float[] lat = new float[pWidth];
            float[] lon = new float[pWidth];
            
            float[] seaNad = new float[pWidth];
            float[] saaNad = new float[pWidth];
            float[] veaNad = new float[pWidth];
            float[] vaaNad = new float[pWidth];
            
            float[] seaFwd = new float[pWidth];
            float[] saaFwd = new float[pWidth];
            float[] veaFwd = new float[pWidth];
            float[] vaaFwd = new float[pWidth];
            
            float[] aotNd = new float[pWidth];
            float[] aotUnc = new float[pWidth];
            float[] fineFrac = new float[pWidth];
            float[] weakFrac = new float[pWidth];
            float[] dustFrac = new float[pWidth];
            float[] cldFrac = new float[pWidth];
            int[] aotFlags = new int[pWidth];

            float[] absAot = new float[pWidth];
            float[] ssa = new float[pWidth];

            //float[] sAot0555 = new float[pWidth];
            float[] sAot0659 = new float[pWidth];
            float[] sAot0865 = new float[pWidth];
            float[] sAot1610 = new float[pWidth];

            float[] sref0550 = new float[pWidth];
            float[] sref0670 = new float[pWidth];
            float[] sref0870 = new float[pWidth];
            float[] sref1600 = new float[pWidth];
            
            final int offset = 4;
            final int skip = 9;
            int ilat;
            int ilon;
            for (int iy=offset; iy<pHeight; iy+=skip){
                if (iy%10 == 9) {
                    System.out.printf("L3 binning %5.1f%%\r", (float)(iy)/(float)(pHeight)*100f);
                    System.out.flush();
                }
                latTpg.readPixels(0, iy, pWidth, 1, lat);
                lonTpg.readPixels(0, iy, pWidth, 1, lon);
                
                seaNadTpg.readPixels(0, iy, pWidth, 1, seaNad);
                saaNadTpg.readPixels(0, iy, pWidth, 1, saaNad);
                veaNadTpg.readPixels(0, iy, pWidth, 1, veaNad);
                vaaNadTpg.readPixels(0, iy, pWidth, 1, vaaNad);
                if (!doSyn){
                    seaFwdTpg.readPixels(0, iy, pWidth, 1, seaFwd);
                    saaFwdTpg.readPixels(0, iy, pWidth, 1, saaFwd);
                    veaFwdTpg.readPixels(0, iy, pWidth, 1, veaFwd);
                    vaaFwdTpg.readPixels(0, iy, pWidth, 1, vaaFwd);
                }
                
                aotNdBand.readPixels(0, iy, pWidth, 1, aotNd);
                aotUncNdBand.readPixels(0, iy, pWidth, 1, aotUnc);
                fotB.readPixels(0, iy, pWidth, 1, fineFrac);
                wofB.readPixels(0, iy, pWidth, 1, weakFrac);
                docB.readPixels(0, iy, pWidth, 1, dustFrac);
                cldFracB.readPixels(0, iy, pWidth, 1, cldFrac);
                aotFlagsB.readPixels(0, iy, pWidth, 1, aotFlags);
                
                //sAot0550B.readPixels(0, iy, pWidth, 1, sAot0555);
                sAot0670B.readPixels(0, iy, pWidth, 1, sAot0659);
                sAot0870B.readPixels(0, iy, pWidth, 1, sAot0865);
                sAot1600B.readPixels(0, iy, pWidth, 1, sAot1610);

                absAotB.readPixels(0, iy, pWidth, 1, absAot);
                ssaB.readPixels(0, iy, pWidth, 1, ssa);
                if (doRecomputeAAOD) {
                    for (int ii=0; ii<absAot.length; ii++){
                        if (aotNd[ii] > 0 && ssa[ii] > 0) {
                            absAot[ii] = (1 - ssa[ii]) * aotNd[ii]; 
                        }
                        else {
                            absAot[ii] = (float) absAotB.getGeophysicalNoDataValue(); 
                        }
                    }
                }

                if (doSurfRefl){
                    sref0555B.readPixels(0, iy, pWidth, 1, sref0550);
                    sref0659B.readPixels(0, iy, pWidth, 1, sref0670);
                    sref0865B.readPixels(0, iy, pWidth, 1, sref0870);
                    sref1610B.readPixels(0, iy, pWidth, 1, sref1600);
                }                
                double angWvlLog659 = -1.0 / Math.log( 550. / 659. );
                double angWvlLog865 = -1.0 / Math.log( 550. / 865. );
                float razN, razF;
                float fineAot, dustAot;
                float normAotUncert;
                for (int ix=offset; ix<pWidth; ix+=skip){
                    if (aotNd[ix]>0){
                        ilat = lat2idx(lat[ix]);
                        ilon = lon2idx(lon[ix]);
                        
                        countGrid[ilat][ilon]++;

                        computeRunningMeanVar(aod550Grid, countGrid, ilat, ilon, aotNd[ix]);
                        computeRunningMeanVarMinMax(sigAod550Grid, countGrid, ilat, ilon, aotUnc[ix]);
                        //computeRunningMeanVar(aod0555Grid, countGrid, ilat, ilon, sAot0555[ix]);
                        computeRunningMeanVar(aod670Grid, countGrid, ilat, ilon, sAot0659[ix]);
                        computeRunningMeanVar(aod870Grid, countGrid, ilat, ilon, sAot0865[ix]);
                        computeRunningMeanVar(aod1600Grid, countGrid, ilat, ilon, sAot1610[ix]);
                        
                        normAotUncert = aotUnc[ix] / aotNd[ix];
                        //computeRunningMeanVarMinMax(sigAod555Grid, countGrid, ilat, ilon, (normAotUncert * sAot0555[ix]));
                        computeRunningMeanVarMinMax(sigAod659Grid, countGrid, ilat, ilon, (normAotUncert * sAot0659[ix]));
                        computeRunningMeanVarMinMax(sigAod865Grid, countGrid, ilat, ilon, (normAotUncert * sAot0865[ix]));
                        computeRunningMeanVarMinMax(sigAod1600Grid, countGrid, ilat, ilon, (normAotUncert * sAot1610[ix]));
                        
                        computeRunningMeanVar(angstrom659Grid, countGrid, ilat, ilon, (float)(Math.log(aotNd[ix] / sAot0659[ix]) * angWvlLog659));
                        computeRunningMeanVar(angstrom865Grid, countGrid, ilat, ilon, (float)(Math.log(aotNd[ix] / sAot0865[ix]) * angWvlLog865));
                        
                        fineAot =         fineFrac[ix]                          * aotNd[ix];
                        //absAot  =         fineFrac[ix]  * (1.0f - weakFrac[ix]) * aotNd[ix];
                        dustAot = (1.0f - fineFrac[ix]) *         dustFrac[ix]  * aotNd[ix];
                        computeRunningMeanVar(fineModeAodGrid, countGrid, ilat, ilon, fineAot);
                        computeRunningMeanVar(absModeAodGrid,  countGrid, ilat, ilon, absAot[ix]);
                        computeRunningMeanVar(dustModeAodGrid, countGrid, ilat, ilon, dustAot);
                        computeRunningMeanVar(ssaGrid, countGrid, ilat, ilon, ssa[ix]);
                        
                        if (doSurfRefl) {
                            computeRunningMeanVar(rsurf0555Grid, countGrid, ilat, ilon, sref0550[ix]);
                            computeRunningMeanVar(rsurf0659Grid, countGrid, ilat, ilon, sref0670[ix]);
                            computeRunningMeanVar(rsurf0865Grid, countGrid, ilat, ilon, sref0870[ix]);
                            computeRunningMeanVar(rsurf1600Grid, countGrid, ilat, ilon, sref1600[ix]);
                        }
                        computeRunningMeanVar(cldFracGrid, countGrid, ilat, ilon, cldFrac[ix]);
                        computeRunningMeanVar(landFracGrid, countGrid, ilat, ilon, (aotFlags[ix]&1));
                        
                        if (doSyn){
                            computeRunningMeanVar(szaGrid, countGrid, ilat, ilon, (seaNad[ix]), 0);
                            computeRunningMeanVar(vzaGrid, countGrid, ilat, ilon, (veaNad[ix]), 0);
                        }
                        else {
                            computeRunningMeanVar(szaGrid, countGrid, ilat, ilon, (90.0f - seaNad[ix]), (90.0f - seaFwd[ix]));
                            computeRunningMeanVar(vzaGrid, countGrid, ilat, ilon, (90.0f - veaNad[ix]), (90.0f - veaFwd[ix]));
                        }
                        razN = Math.abs(saaNad[ix]-vaaNad[ix]);
                        if (razN > 180) razN = 360.0f - razN; 
                        razF = Math.abs(saaFwd[ix]-vaaFwd[ix]);
                        if (razF > 180) razF = 360.0f - razF; 
                        computeRunningMeanVar(razGrid, countGrid, ilat, ilon, razN, razF);
                        
                    }
                }
            }
            System.out.printf("L3 binning done                     \n");

        } catch (IOException ex) {
            Logger.getLogger(DataGridder.class.getName()).log(Level.SEVERE, null, ex);
        } catch (RuntimeException ex) {
            Logger.getLogger(DataGridder.class.getName()).log(Level.SEVERE, "Error: reading " + fname, ex);
        }
    }

    public void binToGridV3(Product p, DataVersionNumbers version){
        String fname = p.getFileLocation().getPath();
        System.out.println("binning V" + version + " - " + fname);
        try {
            //p = ProductIO.readProduct(fname);
            UTC pStartTime = p.getStartTime();
            if (pStartTime.getMJD() < startTime.getMJD()) startTime = pStartTime;
            UTC pEndTime = p.getEndTime();
            if (pEndTime.getMJD() > endTime.getMJD()) endTime = pEndTime;
            if (products.length() == 0) {
                products = p.getName();
            } else {
                products += "; " + p.getName();
            }
            int pWidth = p.getSceneRasterWidth();
            int pHeight = p.getSceneRasterHeight();
            
            TiePointGrid latTpg = p.getTiePointGrid("latitude");
            TiePointGrid lonTpg = p.getTiePointGrid("longitude");

            TiePointGrid seaNadTpg = p.getTiePointGrid("sun_elev_nadir");
            TiePointGrid saaNadTpg = p.getTiePointGrid("sun_azimuth_nadir");
            TiePointGrid veaNadTpg = p.getTiePointGrid("view_elev_nadir");
            TiePointGrid vaaNadTpg = p.getTiePointGrid("view_elev_nadir");
            
            TiePointGrid seaFwdTpg = p.getTiePointGrid("sun_elev_fward");
            TiePointGrid saaFwdTpg = p.getTiePointGrid("sun_azimuth_fward");
            TiePointGrid veaFwdTpg = p.getTiePointGrid("view_elev_fward");
            TiePointGrid vaaFwdTpg = p.getTiePointGrid("view_elev_fward");

            Band aotNdBand = p.getBand("aot_nd");
            Band aotUncNdBand = p.getBand("aot_uncert_nd");
            Band maBand = p.getBand("ma_nd");
            Band mixNsdB = p.getBand("mix_nsdust");
            Band mixSeasB = p.getBand("mix_seas");
            Band mixStraB = p.getBand("mix_strongabs");
            Band mixWeakB = p.getBand("mix_weakabs");
            
            float[] lat = new float[pWidth];
            float[] lon = new float[pWidth];

            float[] seaNad = new float[pWidth];
            float[] saaNad = new float[pWidth];
            float[] veaNad = new float[pWidth];
            float[] vaaNad = new float[pWidth];
            
            float[] seaFwd = new float[pWidth];
            float[] saaFwd = new float[pWidth];
            float[] veaFwd = new float[pWidth];
            float[] vaaFwd = new float[pWidth];
            
            float[] aotNd = new float[pWidth];
            float[] aotUnc = new float[pWidth];
            int[] ma_nd = new int[pWidth];
            float[] mixNsd = new float[pWidth];
            float[] mixSeaS = new float[pWidth];
            float[] mixStrA = new float[pWidth];
            float[] mixWeakA = new float[pWidth];
            
            final int offset = 4;
            final int skip = 9;
            int ilat;
            int ilon;
            
            for (int iy=offset; iy<pHeight; iy+=skip){
                if (iy%10 == 9) System.err.printf("L3 binning %5.1f%%\r", (float)(iy)/(float)(pHeight)*100f);
                latTpg.readPixels(0, iy, pWidth, 1, lat);
                lonTpg.readPixels(0, iy, pWidth, 1, lon);
                
                seaNadTpg.readPixels(0, iy, pWidth, 1, seaNad);
                saaNadTpg.readPixels(0, iy, pWidth, 1, saaNad);
                veaNadTpg.readPixels(0, iy, pWidth, 1, veaNad);
                vaaNadTpg.readPixels(0, iy, pWidth, 1, vaaNad);
                
                seaFwdTpg.readPixels(0, iy, pWidth, 1, seaFwd);
                saaFwdTpg.readPixels(0, iy, pWidth, 1, saaFwd);
                veaFwdTpg.readPixels(0, iy, pWidth, 1, veaFwd);
                vaaFwdTpg.readPixels(0, iy, pWidth, 1, vaaFwd);
                
                aotNdBand.readPixels(0, iy, pWidth, 1, aotNd);
                aotUncNdBand.readPixels(0, iy, pWidth, 1, aotUnc);
                maBand.readPixels(0, iy, pWidth, 1, ma_nd);
                mixNsdB.readPixels(0, iy, pWidth, 1, mixNsd);
                mixSeasB.readPixels(0, iy, pWidth, 1, mixSeaS);
                mixStraB.readPixels(0, iy, pWidth, 1, mixStrA);
                mixWeakB.readPixels(0, iy, pWidth, 1, mixWeakA);

                float[] specAots = null;
                float razN, razF;
                for (int ix=offset; ix<pWidth; ix+=skip){
                    if (aotNd[ix]>0){
                        ilat = (int) Math.floor(lat[ix]) + 90;
                        if (ilat == 180) ilat = 179;
                        ilon = (int) Math.floor(lon[ix]) + 180;
                        if (ilon == 360) ilon = 359;
                        
                        countGrid[ilat][ilon]++;

                        computeRunningMeanVar(aod550Grid, countGrid, ilat, ilon, aotNd[ix]);
                        computeRunningMeanVar(sigAod550Grid, countGrid, ilat, ilon, aotUnc[ix]);

                        if (ma_nd[ix] == 8){
                            specAots = getSpecAots(aotNd[ix], mixNsd[ix], mixSeaS[ix], mixStrA[ix], mixWeakA[ix]);
                        } else {
                            specAots = getSpecAots(aotNd[ix], ma_nd[ix]);
                        }

                        //computeRunningMeanVar(aod0555Grid, countGrid, ilat, ilon, specAots[0]);
                        computeRunningMeanVar(aod670Grid, countGrid, ilat, ilon, specAots[1]);
                        computeRunningMeanVar(aod870Grid, countGrid, ilat, ilon, specAots[2]);
                        computeRunningMeanVar(aod1600Grid, countGrid, ilat, ilon, specAots[3]);

                        //computeRunningMeanVar(sigAod555Grid, countGrid, ilat, ilon, (aotUnc[ix] * specAots[0] / aotNd[ix]));
                        computeRunningMeanVar(sigAod659Grid, countGrid, ilat, ilon, (aotUnc[ix] * specAots[1] / aotNd[ix]));
                        computeRunningMeanVar(sigAod865Grid, countGrid, ilat, ilon, (aotUnc[ix] * specAots[2] / aotNd[ix]));
                        computeRunningMeanVar(sigAod1600Grid, countGrid, ilat, ilon, (aotUnc[ix] * specAots[3] / aotNd[ix]));

                        computeRunningMeanVar(szaGrid, countGrid, ilat, ilon, (90.0f - seaNad[ix]), (90.0f - seaFwd[ix]));
                        computeRunningMeanVar(vzaGrid, countGrid, ilat, ilon, (90.0f - veaNad[ix]), (90.0f - veaFwd[ix]));
                        
                        razN = Math.abs(saaNad[ix]-vaaNad[ix]);
                        if (razN > 180) razN = 360.0f - razN; 
                        razF = Math.abs(saaFwd[ix]-vaaFwd[ix]);
                        if (razF > 180) razF = 360.0f - razF; 
                        computeRunningMeanVar(razGrid, countGrid, ilat, ilon, razN, razF);
                    }
                }
            }
            System.err.printf("L3 binning done                     \n");

        } catch (IOException ex) {
            Logger.getLogger(DataGridder.class.getName()).log(Level.SEVERE, null, ex);
        } catch (RuntimeException ex) {
            Logger.getLogger(DataGridder.class.getName()).log(Level.SEVERE, "Error: reading " + fname, ex);
        }
    }

    public void binToGridV11(Product p, DataVersionNumbers version){
        String fname = p.getFileLocation().getPath();
        System.out.println("binning V" + version + " - " + fname);
        try {
            //p = ProductIO.readProduct(fname);
            UTC pStartTime = p.getStartTime();
            if (pStartTime.getMJD() < startTime.getMJD()) startTime = pStartTime;
            UTC pEndTime = p.getEndTime();
            if (pEndTime.getMJD() > endTime.getMJD()) endTime = pEndTime;
            if (products.length() == 0) {
                products = p.getName();
            } else {
                products += "; " + p.getName();
            }
            int pWidth = p.getSceneRasterWidth();
            int pHeight = p.getSceneRasterHeight();
            
            TiePointGrid latTpg = p.getTiePointGrid("latitude");
            TiePointGrid lonTpg = p.getTiePointGrid("longitude");
            Band aotNdBand = p.getBand("aot_nd");
            Band aotUncNdBand = p.getBand("aot_uncert_nd");
            Band maBand = p.getBand("ma_nd");
            
            float[] lat = new float[pWidth];
            float[] lon = new float[pWidth];
            float[] aotNd = new float[pWidth];
            float[] aotUnc = new float[pWidth];
            int[] ma_nd = new int[pWidth];
            
            final int offset = 4;
            final int skip = 9;
            int ilat;
            int ilon;
            
            for (int iy=offset; iy<pHeight; iy+=skip){
                if (iy%10 == 9) System.err.printf("L3 binning %5.1f%%\r", (float)(iy)/(float)(pHeight)*100f);
                latTpg.readPixels(0, iy, pWidth, 1, lat);
                lonTpg.readPixels(0, iy, pWidth, 1, lon);
                aotNdBand.readPixels(0, iy, pWidth, 1, aotNd);
                aotUncNdBand.readPixels(0, iy, pWidth, 1, aotUnc);
                maBand.readPixels(0, iy, pWidth, 1, ma_nd);
                
                float[] specAots = null;
                for (int ix=offset; ix<pWidth; ix+=skip){
                    if (aotNd[ix]>0){
                        ilat = (int) Math.floor(lat[ix]) + 90;
                        if (ilat == 180) ilat = 179;
                        ilon = (int) Math.floor(lon[ix]) + 180;
                        if (ilon == 360) ilon = 359;
                        aod550Grid[0][ilat][ilon] += aotNd[ix];
                        sigAod550Grid[0][ilat][ilon] += aotUnc[ix];
                        
                        //specAots = getSpecAotsPure(aotNd[ix], ma_nd[ix]);
                        specAots = getSpecAots(aotNd[ix], ma_nd[ix]);
                        
                        //aod0555Grid[0][ilat][ilon] += specAots[0];
                        aod670Grid[0][ilat][ilon] += specAots[1];
                        aod870Grid[0][ilat][ilon] += specAots[2];
                        aod1600Grid[0][ilat][ilon] += specAots[3];
                        
                        countGrid[ilat][ilon]++;
                    }
                }
            }
            System.err.printf("L3 binning done                     \n");

        } catch (IOException ex) {
            Logger.getLogger(DataGridder.class.getName()).log(Level.SEVERE, null, ex);
        } catch (RuntimeException ex) {
            Logger.getLogger(DataGridder.class.getName()).log(Level.SEVERE, "Error: reading " + fname, ex);
        }
    }

    public void binToGridV10(Product p, DataVersionNumbers version) {
        String fname = p.getFileLocation().getPath();
        System.out.println("binning V" + version + " - " + fname);
        try {
            //p = ProductIO.readProduct(fname);
            UTC pStartTime = p.getStartTime();
            if (pStartTime.getMJD() < startTime.getMJD()) startTime = pStartTime;
            UTC pEndTime = p.getEndTime();
            if (pEndTime.getMJD() > endTime.getMJD()) endTime = pEndTime;
            if (products.length() == 0) {
                products = p.getName();
            } else {
                products += "; " + p.getName();
            }
            int pWidth = p.getSceneRasterWidth();
            int pHeight = p.getSceneRasterHeight();
            
            TiePointGrid latTpg = p.getTiePointGrid("latitude");
            TiePointGrid lonTpg = p.getTiePointGrid("longitude");
            
            TiePointGrid seaNadTpg = p.getTiePointGrid("sun_elev_nadir");
            TiePointGrid saaNadTpg = p.getTiePointGrid("sun_azimuth_nadir");
            TiePointGrid veaNadTpg = p.getTiePointGrid("view_elev_nadir");
            TiePointGrid vaaNadTpg = p.getTiePointGrid("view_elev_nadir");
            
            TiePointGrid seaFwdTpg = p.getTiePointGrid("sun_elev_fward");
            TiePointGrid saaFwdTpg = p.getTiePointGrid("sun_azimuth_fward");
            TiePointGrid veaFwdTpg = p.getTiePointGrid("view_elev_fward");
            TiePointGrid vaaFwdTpg = p.getTiePointGrid("view_elev_fward");

            Band aotNdBand = p.getBand("aot_nd");
            
            float[] lat = new float[pWidth];
            float[] lon = new float[pWidth];
            
            float[] seaNad = new float[pWidth];
            float[] saaNad = new float[pWidth];
            float[] veaNad = new float[pWidth];
            float[] vaaNad = new float[pWidth];
            
            float[] seaFwd = new float[pWidth];
            float[] saaFwd = new float[pWidth];
            float[] veaFwd = new float[pWidth];
            float[] vaaFwd = new float[pWidth];
            
            float[] aotNd = new float[pWidth];
            
            final int offset = 4;
            final int skip = 9;
            int ilat;
            int ilon;
            
            for (int iy=offset; iy<pHeight; iy+=skip){
                if (iy%10 == 9) System.err.printf("L3 binning %5.1f%%\r", (float)(iy)/(float)(pHeight)*100f);
                latTpg.readPixels(0, iy, pWidth, 1, lat);
                lonTpg.readPixels(0, iy, pWidth, 1, lon);
                
                seaNadTpg.readPixels(0, iy, pWidth, 1, seaNad);
                saaNadTpg.readPixels(0, iy, pWidth, 1, saaNad);
                veaNadTpg.readPixels(0, iy, pWidth, 1, veaNad);
                vaaNadTpg.readPixels(0, iy, pWidth, 1, vaaNad);
                
                seaFwdTpg.readPixels(0, iy, pWidth, 1, seaFwd);
                saaFwdTpg.readPixels(0, iy, pWidth, 1, saaFwd);
                veaFwdTpg.readPixels(0, iy, pWidth, 1, veaFwd);
                vaaFwdTpg.readPixels(0, iy, pWidth, 1, vaaFwd);
                
                aotNdBand.readPixels(0, iy, pWidth, 1, aotNd);
                
                double angWvlLog = -1.0 / Math.log( 555 / 659);
                float razN, razF;
                for (int ix=offset; ix<pWidth; ix+=skip){
                    if (aotNd[ix]>0){
                        ilat = (int) Math.floor(lat[ix]) + 90;
                        if (ilat == 180) ilat = 179;
                        ilon = (int) Math.floor(lon[ix]) + 180;
                        if (ilon == 360) ilon = 359;
                        
                        countGrid[ilat][ilon]++;

                        computeRunningMeanVar(aod550Grid, countGrid, ilat, ilon, aotNd[ix]);
                        
                        computeRunningMeanVar(szaGrid, countGrid, ilat, ilon, (90.0f - seaNad[ix]), (90.0f - seaFwd[ix]));
                        computeRunningMeanVar(vzaGrid, countGrid, ilat, ilon, (90.0f - veaNad[ix]), (90.0f - veaFwd[ix]));
                        
                        razN = Math.abs(saaNad[ix]-vaaNad[ix]);
                        if (razN > 180) razN = 360.0f - razN; 
                        razF = Math.abs(saaFwd[ix]-vaaFwd[ix]);
                        if (razF > 180) razF = 360.0f - razF; 
                        computeRunningMeanVar(razGrid, countGrid, ilat, ilon, razN, razF);
                        
                    }
                }
            }
            System.err.printf("L3 binning done                     \n");

        } catch (IOException ex) {
            Logger.getLogger(DataGridder.class.getName()).log(Level.SEVERE, null, ex);
        } catch (RuntimeException ex) {
            Logger.getLogger(DataGridder.class.getName()).log(Level.SEVERE, "Error: reading " + fname, ex);
        }
    }

    public boolean isEmpty() {
        boolean isEmpty = true;
        for (int iy=0; iy<180; iy++) {
            for (int ix=0; ix<360; ix++) {
                if (countGrid[iy][ix] > 0) {
                    isEmpty = false;
                    return isEmpty;
                }
            }
        }
        
        return isEmpty;
    }

    private float[][] readSpecAot() throws Exception{
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
            throw new Exception("Error reading model info");
        }
        if (bufR != null) {
            bufR.close();
        }
        return modelInfo;
    }


    private float[] getSpecAots(float aot550, float nsd, float seas, float str, float weak) {
        final int idxNsd  = 35;
        final int idxSeaS = 15;
        final int idxStr  = 5;
        final int idxWeak = 1;
        float[] specAots = new float[4];
        for (int i=0; i<4; i++){
            specAots[i]  = nsd  * specAotInfo[idxNsd-1][2+i];
            specAots[i] += seas * specAotInfo[idxSeaS-1][2+i];
            specAots[i] += str  * specAotInfo[idxStr-1][2+i];
            specAots[i] += weak * specAotInfo[idxWeak-1][2+i];
            specAots[i] *= aot550;
            specAots[i] /= 100;
        }
        return specAots;
    }

    private float[] getSpecAots(float aot550, int ma) {
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
            specAots[i] = aot550 * specAotInfo[modelIdx-1][i];
        }
        return specAots;
    }
    
    private float[] getSpecAotsPure(float aot550, int ma) {
        int modelIdx = 0;
        switch (ma) {
            case 1: modelIdx = 1; break;
            case 2: modelIdx = 5; break;
            case 3: modelIdx = 15; break;
            case 5: modelIdx = 35; break;
        }
        float[] specAots = new float[4];
        for (int i=0; i<4; i++){
            specAots[i] = aot550 * specAotInfo[modelIdx-1][i];
        }
        return specAots;
    }
    
    private void computeRunningMeanVar(float[][][] meanVarGrd, int[][] countGrd, int ilat, int ilon, float value) {
        double delta = value - meanVarGrd[0][ilat][ilon];
        meanVarGrd[0][ilat][ilon] += delta / countGrd[ilat][ilon];
        meanVarGrd[1][ilat][ilon] += delta * (value - meanVarGrd[0][ilat][ilon]);
    }

    private void computeRunningMeanVar(float[][][][] meanVarGrd, int[][] countGrd, int ilat, int ilon, float valueNad, float valueFwd) {
        double delta = valueNad - meanVarGrd[0][ilat][ilon][0];
        meanVarGrd[0][ilat][ilon][0] += delta / countGrd[ilat][ilon];
        meanVarGrd[1][ilat][ilon][0] += delta * (valueNad - meanVarGrd[0][ilat][ilon][0]);

        delta = valueFwd - meanVarGrd[0][ilat][ilon][1];
        meanVarGrd[0][ilat][ilon][1] += delta / countGrd[ilat][ilon];
        meanVarGrd[1][ilat][ilon][1] += delta * (valueFwd - meanVarGrd[0][ilat][ilon][1]);
    }

    private void computeRunningMeanVarMinMax(float[][][] meanVarGrd, int[][] countGrid, int ilat, int ilon, float value) {     
        computeRunningMeanVar(meanVarGrd, countGrid, ilat, ilon, value);
        meanVarGrd[2][ilat][ilon] = Math.min(value, meanVarGrd[2][ilat][ilon]);
        meanVarGrd[3][ilat][ilon] = Math.max(value, meanVarGrd[3][ilat][ilon]);
        meanVarGrd[4][ilat][ilon] += value * value;
        float rms = (float) Math.sqrt(meanVarGrd[4][ilat][ilon]/countGrid[ilat][ilon]);
        if (countGrid[ilat][ilon] > 1
            && meanVarGrd[1][ilat][ilon] > 1e-6 && rms > meanVarGrd[3][ilat][ilon]){
            System.err.printf("lat %d lon %d n %3d min %9.6f rms %9.6f max %9.6f\n", ilat, ilon, countGrid[ilat][ilon], meanVarGrd[2][ilat][ilon], (float)Math.sqrt(meanVarGrd[4][ilat][ilon]/countGrid[ilat][ilon]), meanVarGrd[3][ilat][ilon]);
            System.err.println("möp");
        }
    }

    private int lon2idx(float lon) {
        int ilon;
        ilon = (int) Math.floor(lon) + 180;
        if (ilon == 360) ilon = 359;
        return ilon;
    }

    private int lat2idx(float lat) {
        int ilat;
        ilat = (int) Math.floor(lat) + 90;
        if (ilat == 180) ilat = 179;
        return ilat;
    }

    private int lon2idx(double lon) {
        int ilon;
        ilon = (int) Math.floor(lon) + 180;
        if (ilon == 360) ilon = 359;
        return ilon;
    }

    private int lat2idx(double lat) {
        int ilat;
        ilat = (int) Math.floor(lat) + 90;
        if (ilat == 180) ilat = 179;
        return ilat;
    }

    private float idx2lat(int ilat) {
        float lat;
        lat = -89.5f + ilat;
        return lat;
    }

    private float idx2lon(int ilon) {
        float lon;
        lon = -179.5f + ilon;
        return lon;
    }

    private String getNcdfId(NetcdfFile ncF) {
        Attribute idAtt = ncF.findAttribute("@id");
        String idS;
        if (idAtt == null) {
            Attribute att = ncF.findAttribute("@inputFileList");
            idS = att.getStringValue();
        }
        else {
            idS = idAtt.getStringValue();
        }
        return idS;
    }

    private UTC getNcdfTime(NetcdfFile ncF, String attStr) {
        Attribute att = ncF.findAttribute(attStr);
        UTC utcTime = null;
        try {
            String timeStr = att.getStringValue();
            if (timeStr.endsWith("Z")) {
                timeStr = timeStr.substring(0, timeStr.length()-1);
            }
            utcTime = ProductData.UTC.parse(timeStr, "yyyyMMdd'T'HHmmss");
        } catch (ParseException ex) {
            Logger.getLogger(DataGridder.class.getName()).log(Level.SEVERE, null, ex);
        }
        return utcTime;
    }

    private int getNcdfRasterWidth(NetcdfFile ncF) {
        Dimension dim = ncF.findDimension("columns");
        return dim.getLength();
    }

    private int getNcdfRasterHeight(NetcdfFile ncF) {
        Dimension dim = ncF.findDimension("rows");
        return dim.getLength();
    }

    public float[] getVarAsFloatArr(NetcdfFile ncF, String varName) throws IOException {
        Variable var = ncF.findVariable(null, varName);
        float[] arr = (float[]) var.read().copyTo1DJavaArray();
        return arr;
    }

    public double[] getVarAsDoubleArr(NetcdfFile ncF, String varName) throws IOException {
        Variable var = ncF.findVariable(null, varName);
        double[] arr = (double[]) var.read().copyTo1DJavaArray();
        return arr;
    }


}
