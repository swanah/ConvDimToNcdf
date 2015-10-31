 /*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package akh.convdimtoncdf;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData.UTC;
import org.esa.beam.framework.datamodel.TiePointGrid;
import ucar.ma2.*;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFileWriteable;
import ucar.nc2.NetcdfFileWriter;

/**
 *
 * @author akheckel
 */
public class ProdConverterL2 {
    private static ProdConverterL2 instance = null;
    float[] f = {1f,1f};
    private final NetcdfVariableProperties latV = 
            new NetcdfVariableProperties("latitude", "latitude", "latitude", "degrees_north", DataType.FLOAT, -90f, 90f, -999f);
    private final NetcdfVariableProperties lonV = 
            new NetcdfVariableProperties("longitude", "longitude", "longitude", "degrees_east", DataType.FLOAT, -180f, 180f, -999f);
    
    private final NetcdfVariableProperties[] pixCornerLatV = new NetcdfVariableProperties[]{
        new NetcdfVariableProperties("pixel_corner_latitude1", "latitude_1st_corner", "latitude", "degrees_north", DataType.FLOAT, -90f, 90f, -999f),
        new NetcdfVariableProperties("pixel_corner_latitude2", "latitude_2nd_corner", "latitude", "degrees_north", DataType.FLOAT, -90f, 90f, -999f),
        new NetcdfVariableProperties("pixel_corner_latitude3", "latitude_3rd_corner", "latitude", "degrees_north", DataType.FLOAT, -90f, 90f, -999f),
        new NetcdfVariableProperties("pixel_corner_latitude4", "latitude_4th_corner", "latitude", "degrees_north", DataType.FLOAT, -90f, 90f, -999f)
    };
    
    private final NetcdfVariableProperties[] pixCornerLonV = new NetcdfVariableProperties[]{
        new NetcdfVariableProperties("pixel_corner_longitude1", "longitude_1st_corner", "longitude", "degrees_east", DataType.FLOAT, -180f, 180f, -999f),
        new NetcdfVariableProperties("pixel_corner_longitude2", "longitude_2nd_corner", "longitude", "degrees_east", DataType.FLOAT, -180f, 180f, -999f),
        new NetcdfVariableProperties("pixel_corner_longitude3", "longitude_3rd_corner", "longitude", "degrees_east", DataType.FLOAT, -180f, 180f, -999f),
        new NetcdfVariableProperties("pixel_corner_longitude4", "longitude_4th_corner", "longitude", "degrees_east", DataType.FLOAT, -180f, 180f, -999f)
    };
    
    private final NetcdfVariableProperties viewV = 
            new NetcdfVariableProperties("instrument_view", "Instrument view", "", "1", DataType.INT, 0, 1, -1);
    
    private final NetcdfVariableProperties aod550V = 
            new NetcdfVariableProperties("AOD550", "aerosol optical thickness at 550 nm", "atmosphere_optical_thickness_due_to_aerosol", "1", DataType.FLOAT, 0f, 2f, -999f);
    //private final NetcdfVariableProperties aod555V = 
    //        new NetcdfVariableProperties("AOD555", "aerosol optical thickness at 555 nm", "atmosphere_optical_thickness_due_to_aerosol", "1", 0f, 2f, -999f);
    private final NetcdfVariableProperties aod659V = 
            new NetcdfVariableProperties("AOD670", "aerosol optical thickness at 670 nm", "atmosphere_optical_thickness_due_to_aerosol", "1", DataType.FLOAT, 0f, 2f, -999f);
    private final NetcdfVariableProperties aod865V = 
            new NetcdfVariableProperties("AOD870", "aerosol optical thickness at 870 nm", "atmosphere_optical_thickness_due_to_aerosol", "1", DataType.FLOAT, 0f, 2f, -999f);
    private final NetcdfVariableProperties aod1610V = 
            new NetcdfVariableProperties("AOD1600", "aerosol optical thickness at 1600 nm", "atmosphere_optical_thickness_due_to_aerosol", "1", DataType.FLOAT, 0f, 2f, -999f);

    private final NetcdfVariableProperties sigAod550V = 
            new NetcdfVariableProperties("AOD_uncertainty550", "Uncertainty on AOT at 550 nm", "", "1", DataType.FLOAT, 0f, 10f, -999f);
    //private final NetcdfVariableProperties sigAod555V = 
    //        new NetcdfVariableProperties("AOD_uncertainty555", "Uncertainty on AOT at 555 nm", "", "1", 0f, 10f, -999f);
    private final NetcdfVariableProperties sigAod659V = 
            new NetcdfVariableProperties("AOD_uncertainty670", "Uncertainty on AOT at 670 nm", "", "1", DataType.FLOAT, 0f, 10f, -999f);
    private final NetcdfVariableProperties sigAod865V = 
            new NetcdfVariableProperties("AOD_uncertainty870", "Uncertainty on AOT at 870 nm", "", "1", DataType.FLOAT, 0f, 10f, -999f);
    private final NetcdfVariableProperties sigAod1610V = 
            new NetcdfVariableProperties("AOD_uncertainty1600", "Uncertainty on AOT at 1600 nm", "", "1", DataType.FLOAT, 0f, 10f, -999f);

    private final NetcdfVariableProperties angstromV = 
            new NetcdfVariableProperties("ANG550-870", "angstrom exponent computed on AOD550nm and AOD870nm", "aerosol_angstrom_exponent", "1", DataType.FLOAT, -5f, 5f, -999f);

    private final NetcdfVariableProperties fineFracV = 
            new NetcdfVariableProperties("fine_mode_fraction", "fraction of fine mode AOD of total AOD", "", "1", DataType.FLOAT, 0f, 1f, -999f);
    private final NetcdfVariableProperties weakFracV = 
            new NetcdfVariableProperties("weak_absorbing_fraction", "fraction of weak absorbing AOD of fine mode AOD", "", "1", DataType.FLOAT, 0f, 1f, -999f);
    private final NetcdfVariableProperties dustFracV = 
            new NetcdfVariableProperties("dust_fraction", "fraction of non-spherical dust AOD of coarse mode AOD", "", "1", DataType.FLOAT, 0f, 1f, -999f);

    private final NetcdfVariableProperties fmAodV = 
            new NetcdfVariableProperties("FM_AOD550", "fine-mode aerosol optical thickness at 550nm", "", "1", DataType.FLOAT, 0f, 2f, -999f);
    private final NetcdfVariableProperties dustAodV = 
            new NetcdfVariableProperties("D_AOD550", "dust aerosol optical thickness at 550nm", "", "1", DataType.FLOAT, 0f, 2f, -999f);
    private final NetcdfVariableProperties absAodV = 
            new NetcdfVariableProperties("AAOD550", "aerosol absorption optical thickness at 550nm", "", "1", DataType.FLOAT, 0f, 2f, -999f);
    
    private final NetcdfVariableProperties sreflec555V = 
            new NetcdfVariableProperties("surface_reflectance550", "mean bidirectional surface reflectance (nadir)", "", "1", DataType.FLOAT, 0f, 1f, -999f);
    private final NetcdfVariableProperties sreflec659V = 
            new NetcdfVariableProperties("surface_reflectance670", "mean bidirectional surface reflectance (nadir)", "", "1", DataType.FLOAT, 0f, 1f, -999f);
    private final NetcdfVariableProperties sreflec865V = 
            new NetcdfVariableProperties("surface_reflectance870", "mean bidirectional surface reflectance (nadir)", "", "1", DataType.FLOAT, 0f, 1f, -999f);
    private final NetcdfVariableProperties sreflec1610V = 
            new NetcdfVariableProperties("surface_reflectance1600", "mean bidirectional surface reflectance (nadir)", "", "1", DataType.FLOAT, 0f, 1f, -999f);

    private final NetcdfVariableProperties cldFracV = 
            new NetcdfVariableProperties("cloud_fraction", "fraction of cloud flagged pixels in 10km bin", "cloud_area_fraction", "1", DataType.FLOAT, 0f, 1f, -999f);
    private final NetcdfVariableProperties landFlagV = 
            new NetcdfVariableProperties("surface_type_number", "land / sea flag", "land_binary_mask", "1", DataType.INT, 0, 1, -1);

    private final NetcdfVariableProperties szaV = 
            new NetcdfVariableProperties("sun_zenith_at_center", "solar zenith angle", "solar_zenith_angle", "degrees", DataType.FLOAT, 0f, 90f, -999f);
    private final NetcdfVariableProperties vzaV = 
            new NetcdfVariableProperties("satellite_zenith_at_center", "satellite zenith angle", "zenith_angle", "degrees", DataType.FLOAT, 0f, 90f, -999f);
    private final NetcdfVariableProperties razV = 
            new NetcdfVariableProperties("relative_azimuth_at_center", "relative azimuth angle", "", "degrees", DataType.FLOAT, 0f, 90f, -999f);
    private final NetcdfVariableProperties timeV = 
            new NetcdfVariableProperties("time", "time seconds since 1970-01-01 00:00:00 UTC", "", "seconds", DataType.INT, new Integer(1), Integer.MAX_VALUE, 0);

    
    private final float[][] specAotInfo;

    /*
    private final String latVName = "latitude";
    private final String lonVName = "longitude";
    private final String aot0550VName = "AOD550";
    private final String sigAot0550VName = "AOD550_uncertainty";
    private final String aot0555VName = "AOD_0555_derived";
    private final String aot0659VName = "AOD_0659_derived";
    private final String aot0865VName = "AOD_0865_derived";
    private final String aot1600VName = "AOD_1600_derived";
    private final String sref0555VName = "surface_reflectance_nadir_0555";
    private final String sref0659VName = "surface_reflectance_nadir_0659";
    private final String sref0865VName = "surface_reflectance_nadir_0865";
    private final String sref1600VName = "surface_reflectance_nadir_1600";
    * 
    */

    private ProdConverterL2() throws Exception {
        specAotInfo = readSpecAot();
    }

    public static ProdConverterL2 getInstance() throws Exception{
        if (instance == null){
            instance = new ProdConverterL2();
        }
        return instance;
    }
    
    public void convertV4(Product p, String ncdfName, DataVersionNumbers version) throws InvalidRangeException{
        String fname = p.getFileLocation().getPath();
        Array dI = ArrayInt.factory(new int[]{0});
        Array dF = ArrayFloat.factory(new float[]{25.04f});
        ArrayFloat.D2 dF2 = new ArrayFloat.D2(1, 2);
        Array dB = ArrayByte.factory(new int[]{0});
        System.out.println("processing V" + version + " - " + fname);
        try {
            //p = ProductIO.readProduct(fname);
            
            //String ncdfName = fname.replace(".dim", ".nc");
            NetcdfFileWriteable ncfile = createNcdfFile_V4(ncdfName, p, version);

            int pWidth = p.getSceneRasterWidth();
            int pHeight = p.getSceneRasterHeight();

            long startMilliSec1970 = utcToMilliSec1970(p.getStartTime());
            long endSec1970 = utcToMilliSec1970(p.getEndTime());
            long millisecPerLine = (endSec1970 - startMilliSec1970) / pHeight;
            
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

            Band aotNdBand = p.getBand("aot_nd_2");
            Band aotUncNdBand = p.getBand("aot_brent_nd_1");
            Band fotB = p.getBand("frac_fine_total_1");
            Band wofB = p.getBand("frac_weakAbs_fine");
            Band docB = p.getBand("frac_dust_coarse");
            Band cldFracB = p.getBand("cld_frac");
            Band aotFlagsB = p.getBand("aot_flags");

            Band sAot0550B = p.getBand("aot_nd_0550_1");
            Band sAot0670B = p.getBand("aot_nd_0670_1");
            Band sAot0870B = p.getBand("aot_nd_0870_1");
            Band sAot1600B = p.getBand("aot_nd_1600_1");

            Band sref0555B = p.getBand("reflec_surf_nadir_0550_1");
            Band sref0659B = p.getBand("reflec_surf_nadir_0670_1");
            Band sref0865B = p.getBand("reflec_surf_nadir_0870_1");
            Band sref1610B = p.getBand("reflec_surf_nadir_1600_1");

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

            float[] sAot0555 = new float[pWidth];
            float[] sAot0659 = new float[pWidth];
            float[] sAot0865 = new float[pWidth];
            float[] sAot1610 = new float[pWidth];

            float[] sref0550 = new float[pWidth];
            float[] sref0670 = new float[pWidth];
            float[] sref0870 = new float[pWidth];
            float[] sref1600 = new float[pWidth];

            int pixSec1970 = 0;
            final int offset = 4;
            final int skip = 9;
            int[] count = new int[]{0,0};
            for (int iy=offset; iy<pHeight; iy+=skip){
                if (iy%10 == 9){
                    System.err.printf("L2 processing %5.1f%%\r", (float)(iy)/(float)(pHeight)*100f);
                }
                pixSec1970 = (int)((startMilliSec1970 + millisecPerLine * iy) / 1000);
                
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
                fotB.readPixels(0, iy, pWidth, 1, fineFrac);
                wofB.readPixels(0, iy, pWidth, 1, weakFrac);
                docB.readPixels(0, iy, pWidth, 1, dustFrac);
                cldFracB.readPixels(0, iy, pWidth, 1, cldFrac);
                aotFlagsB.readPixels(0, iy, pWidth, 1, aotFlags);
                
                sAot0550B.readPixels(0, iy, pWidth, 1, sAot0555);
                sAot0670B.readPixels(0, iy, pWidth, 1, sAot0659);
                sAot0870B.readPixels(0, iy, pWidth, 1, sAot0865);
                sAot1600B.readPixels(0, iy, pWidth, 1, sAot1610);

                sref0555B.readPixels(0, iy, pWidth, 1, sref0550);
                sref0659B.readPixels(0, iy, pWidth, 1, sref0670);
                sref0865B.readPixels(0, iy, pWidth, 1, sref0870);
                sref1610B.readPixels(0, iy, pWidth, 1, sref1600);
                
                double angWvlLog = -1.0 / Math.log( 550. / 865.);
                
                for (int ix=offset; ix<pWidth; ix+=skip){
                    if (aotNd[ix]>0){
                        dI.setInt(0, pixSec1970);
                        ncfile.write(timeV.varName, count, dI);
                        dF.setFloat(0,lat[ix]);
                        ncfile.write(latV.varName, count, dF);
                        dF.setFloat(0,lon[ix]);
                        ncfile.write(lonV.varName, count, dF);
                        dF.setFloat(0,aotNd[ix]);
                        ncfile.write(aod550V.varName, count, dF);
                        dF.setFloat(0,aotUnc[ix]);
                        ncfile.write(sigAod550V.varName, count, dF);
                        
                        //dF.setFloat(0,sAot0555[ix]);
                        //ncfile.write(aod555V.varName, count, dF);
                        dF.setFloat(0,sAot0659[ix]);
                        ncfile.write(aod659V.varName, count, dF);
                        dF.setFloat(0,sAot0865[ix]);
                        ncfile.write(aod865V.varName, count, dF);
                        dF.setFloat(0,sAot1610[ix]);
                        ncfile.write(aod1610V.varName, count, dF);

                        //dF.setFloat(0, (aotUnc[ix] * sAot0555[ix] / aotNd[ix]));
                        //ncfile.write(sigAod555V.varName, count, dF);
                        dF.setFloat(0, (aotUnc[ix] * sAot0659[ix] / aotNd[ix]));
                        ncfile.write(sigAod659V.varName, count, dF);
                        dF.setFloat(0, (aotUnc[ix] * sAot0865[ix] / aotNd[ix]));
                        ncfile.write(sigAod865V.varName, count, dF);
                        dF.setFloat(0, (aotUnc[ix] * sAot1610[ix] / aotNd[ix]));
                        ncfile.write(sigAod1610V.varName, count, dF);
                        
                        dF.setFloat(0, (float)(Math.log(aotNd[ix] / sAot0865[ix]) * angWvlLog));
                        ncfile.write(angstromV.varName, count, dF);

                        dF.setFloat(0,sref0550[ix]);
                        ncfile.write(sreflec555V.varName, count, dF);
                        dF.setFloat(0,sref0670[ix]);
                        ncfile.write(sreflec659V.varName, count, dF);
                        dF.setFloat(0,sref0870[ix]);
                        ncfile.write(sreflec865V.varName, count, dF);
                        dF.setFloat(0,sref1600[ix]);
                        ncfile.write(sreflec1610V.varName, count, dF);
                        
                        dF.setFloat(0, cldFrac[ix]);
                        ncfile.write(cldFracV.varName, count, dF);
                        dB.setByte(0, (byte)(aotFlags[ix] & 1));
                        ncfile.write(landFlagV.varName, count, dB);
                        
                        //dF.setFloat(0, fineFrac[ix]);
                        //ncfile.write(fineFracV.varName, count, dF);
                        //dF.setFloat(0, weakFrac[ix]);
                        //ncfile.write(weakFracV.varName, count, dF);
                        //dF.setFloat(0, dustFrac[ix]);
                        //ncfile.write(dustFracV.varName, count, dF);

                        dF.setFloat(0, fineFrac[ix] * aotNd[ix]);
                        ncfile.write(fmAodV.varName, count, dF);
                        dF.setFloat(0, dustFrac[ix] * (1 - fineFrac[ix]) * aotNd[ix]);
                        ncfile.write(dustAodV.varName, count, dF);
                        dF.setFloat(0, -999f); //TODO: implement abosorption AOD
                        ncfile.write(absAodV.varName, count, dF);
                        
                        //dF2.setFloat(0, new float[]{(90.0f - seaNad[ix]), (90.0f - seaNad[ix])});
                        dF2.set(0, 0, (90.0f - seaNad[ix]));
                        dF2.set(0, 1, (90.0f - seaFwd[ix]));
                        ncfile.write(szaV.varName, count, dF2);
                        dF2.set(0, 0, (90.0f - veaNad[ix]));
                        dF2.set(0, 1, (90.0f - veaFwd[ix]));
                        ncfile.write(vzaV.varName, count, dF2);
                        dF2.set(0, 0, getRaz(saaNad[ix], vaaNad[ix]));
                        dF2.set(0, 1, getRaz(saaFwd[ix], vaaFwd[ix]));
                        ncfile.write(razV.varName, count, dF2);
                        
                        count[0]++;
                    }
                }
            }
            
            dB.setByte(0, (byte)0);
            ncfile.write(viewV.varName, new int[]{0}, dB);
            dB.setByte(0, (byte)1);
            ncfile.write(viewV.varName, new int[]{1}, dB);
            ncfile.close();
            System.err.printf("L2 processing done                    \n");

        } catch (IOException ex) {
            Logger.getLogger(ProdConverterL2.class.getName()).log(Level.SEVERE, null, ex);
        } catch (RuntimeException ex) {
            Logger.getLogger(ProdConverterL2.class.getName()).log(Level.SEVERE, "Error: reading " + fname, ex);
        }
    }

    public long utcToMilliSec1970(UTC pixTime) {
        Date pixDate = pixTime.getAsDate();
        long milliSec1970 = pixDate.getTime();
        return milliSec1970;
    }

    public void convertV3(Product p, String ncdfName, DataVersionNumbers version) throws InvalidRangeException{
        String fname = p.getFileLocation().getPath();
        Array dF = ArrayFloat.factory(new float[]{25.04f});
        ArrayFloat.D2 dF2 = new ArrayFloat.D2(1, 2);
        
        System.out.println("processing V" + version + " - " + fname);
        try {
            //p = ProductIO.readProduct(fname);
            
            //String ncdfName = fname.replace(".dim", ".nc");
            NetcdfFileWriteable ncfile = createNcdfFile(ncdfName, p, version);

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

            Band aotNdBand = p.getBand("aot_filter_nd");
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
            int[] count = new int[]{0,0};
            for (int iy=offset; iy<pHeight; iy+=skip){
                if (iy%10 == 9) System.err.printf("L2 processing %5.1f%%\r", (float)(iy)/(float)(pHeight)*100f);
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
                for (int ix=offset; ix<pWidth; ix+=skip){
                    if (aotNd[ix]>0){
                        dF.setFloat(0,lat[ix]);
                        ncfile.write(latV.varName, count, dF);
                        dF.setFloat(0,lon[ix]);
                        ncfile.write(lonV.varName, count, dF);
                        dF.setFloat(0,aotNd[ix]);
                        ncfile.write(aod550V.varName, count, dF);
                        dF.setFloat(0,aotUnc[ix]);
                        ncfile.write(sigAod550V.varName, count, dF);
                        
                        if (ma_nd[ix] == 8){
                            specAots = getSpecAots(aotNd[ix], mixNsd[ix], mixSeaS[ix], mixStrA[ix], mixWeakA[ix]);
                        } else {
                            specAots = getSpecAots(aotNd[ix], ma_nd[ix]);
                        }
                        //dF.setFloat(0,specAots[0]);
                        //ncfile.write(aod555V.varName, count, dF);
                        dF.setFloat(0,specAots[1]);
                        ncfile.write(aod659V.varName, count, dF);
                        dF.setFloat(0,specAots[2]);
                        ncfile.write(aod865V.varName, count, dF);
                        dF.setFloat(0,specAots[3]);
                        ncfile.write(aod1610V.varName, count, dF);

                        //dF.setFloat(0, aotUnc[ix] * specAots[0] / aotNd[ix]);
                        //ncfile.write(sigAod555V.varName, count, dF);
                        dF.setFloat(0, aotUnc[ix] * specAots[1] / aotNd[ix]);
                        ncfile.write(sigAod659V.varName, count, dF);
                        dF.setFloat(0, aotUnc[ix] * specAots[2] / aotNd[ix]);
                        ncfile.write(sigAod865V.varName, count, dF);
                        dF.setFloat(0, aotUnc[ix] * specAots[3] / aotNd[ix]);
                        ncfile.write(sigAod1610V.varName, count, dF);

                        dF2.set(0, 0, (90.0f - seaNad[ix]));
                        dF2.set(0, 1, (90.0f - seaFwd[ix]));
                        ncfile.write(szaV.varName, count, dF2);
                        dF2.set(0, 0, (90.0f - veaNad[ix]));
                        dF2.set(0, 1, (90.0f - veaFwd[ix]));
                        ncfile.write(vzaV.varName, count, dF2);
                        dF2.set(0, 0, getRaz(saaNad[ix], vaaNad[ix]));
                        dF2.set(0, 1, getRaz(saaFwd[ix], vaaFwd[ix]));
                        ncfile.write(razV.varName, count, dF2);

                        count[0]++;
                    }
                }
            }
            ncfile.close();
            System.err.printf("L2 processing done                    \n");

        } catch (IOException ex) {
            Logger.getLogger(ProdConverterL2.class.getName()).log(Level.SEVERE, null, ex);
        } catch (RuntimeException ex) {
            Logger.getLogger(ProdConverterL2.class.getName()).log(Level.SEVERE, "Error: reading " + fname, ex);
        }
    }

    public void convertV11(Product p, String ncdfName, DataVersionNumbers version) throws InvalidRangeException{
        String fname = p.getFileLocation().getPath();
        Array dF = ArrayFloat.factory(new float[]{25.04f});
        System.out.println("processing V" + version + " - " + fname);
        try {
            //p = ProductIO.readProduct(fname);
            
            //String ncdfName = fname.replace(".dim", ".nc");
            NetcdfFileWriteable ncfile = createNcdfFile(ncdfName, p, version);

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
            int[] count = new int[]{0};
            for (int iy=offset; iy<pHeight; iy+=skip){
                if (iy%10 == 9) System.err.printf("L2 processing %5.1f%%\r", (float)(iy)/(float)(pHeight)*100f);
                latTpg.readPixels(0, iy, pWidth, 1, lat);
                lonTpg.readPixels(0, iy, pWidth, 1, lon);
                aotNdBand.readPixels(0, iy, pWidth, 1, aotNd);
                aotUncNdBand.readPixels(0, iy, pWidth, 1, aotUnc);
                maBand.readPixels(0, iy, pWidth, 1, ma_nd);
                
                float[] specAots = null;
                for (int ix=offset; ix<pWidth; ix+=skip){
                    if (aotNd[ix]>0){
                        dF.setFloat(0,lat[ix]);
                        ncfile.write(latV.varName, count, dF);
                        dF.setFloat(0,lon[ix]);
                        ncfile.write(lonV.varName, count, dF);
                        dF.setFloat(0,aotNd[ix]);
                        ncfile.write(aod550V.varName, count, dF);
                        dF.setFloat(0,aotUnc[ix]);
                        ncfile.write(sigAod550V.varName, count, dF);
                        
                        //specAots = getSpecAotsPure(aotNd[ix], ma_nd[ix]);
                        specAots = getSpecAots(aotNd[ix], ma_nd[ix]);

                        //dF.setFloat(0,specAots[0]);
                        //ncfile.write(aod555V.varName, count, dF);
                        dF.setFloat(0,specAots[1]);
                        ncfile.write(aod659V.varName, count, dF);
                        dF.setFloat(0,specAots[2]);
                        ncfile.write(aod865V.varName, count, dF);
                        dF.setFloat(0,specAots[3]);
                        ncfile.write(aod1610V.varName, count, dF);
                        count[0]++;
                    }
                }
            }
            ncfile.close();
            System.err.printf("L2 processing done                    \n");

        } catch (IOException ex) {
            Logger.getLogger(ProdConverterL2.class.getName()).log(Level.SEVERE, null, ex);
        } catch (RuntimeException ex) {
            Logger.getLogger(ProdConverterL2.class.getName()).log(Level.SEVERE, "Error: reading " + fname, ex);
        }
    }

    private NetcdfFileWriteable createNcdfFile(String ncdfName, Product p, DataVersionNumbers version) throws IOException {
        
        NetcdfFileWriteable ncfile = NetcdfFileWriteable.createNew(ncdfName, false);
        Dimension dimPix = ncfile.addUnlimitedDimension("pixel_number");
        Dimension dimView = ncfile.addDimension("instrument_view", 2);
        ArrayList<Dimension> dimList = new ArrayList<Dimension>();
        dimList.add(dimView);
        createVcdfVar(ncfile, viewV, dimList);

        dimList = new ArrayList<Dimension>();
        dimList.add(dimPix);
        createVcdfVar(ncfile, latV, dimList);
        createVcdfVar(ncfile, lonV, dimList);
        createVcdfVar(ncfile, aod550V, dimList);
        createVcdfVar(ncfile, sigAod550V, dimList);
        //createVcdfVar(ncfile, aod555V, dimList);
        //createVcdfVar(ncfile, sigAod555V, dimList);
        createVcdfVar(ncfile, aod659V, dimList);
        createVcdfVar(ncfile, sigAod659V, dimList);
        createVcdfVar(ncfile, aod865V, dimList);
        createVcdfVar(ncfile, sigAod865V, dimList);
        createVcdfVar(ncfile, aod1610V, dimList);
        createVcdfVar(ncfile, sigAod1610V, dimList);

        dimList = new ArrayList<Dimension>();
        dimList.add(dimPix);
        dimList.add(dimView);
        createVcdfVar(ncfile, szaV, dimList);
        createVcdfVar(ncfile, vzaV, dimList);
        createVcdfVar(ncfile, razV, dimList);

        createGlobalAttrb(ncfile, p, ncdfName, version);
        ncfile.create();
        return ncfile;
    }
    
    private NetcdfFileWriteable createNcdfFile_V4(String ncdfName, Product p, DataVersionNumbers version) throws IOException {
        
        NetcdfFileWriter ncfile4 = NetcdfFileWriter.createNew(NetcdfFileWriter.Version.netcdf4, ncdfName+"4");
        Dimension one = ncfile4.addUnlimitedDimension("pixel_number");
        Dimension two = ncfile4.addDimension(null, "instrument_view", 2);
        ncfile4.create();
        ncfile4.close();
        
        NetcdfFileWriteable ncfile = NetcdfFileWriteable.createNew(ncdfName, false);
        Dimension dimPix = ncfile.addUnlimitedDimension("pixel_number");
        Dimension dimView = ncfile.addDimension("instrument_view", 2);
        ArrayList<Dimension> dimList = new ArrayList<Dimension>();
        dimList.add(dimPix);
        for (int i=0; i<4; i++){
            createVcdfVar(ncfile, pixCornerLatV[i], dimList);
            createVcdfVar(ncfile, pixCornerLonV[i], dimList);
        }
        createVcdfVar(ncfile, aod550V, dimList);
        createVcdfVar(ncfile, aod659V, dimList);
        createVcdfVar(ncfile, aod865V, dimList);
        createVcdfVar(ncfile, aod1610V, dimList);

        createVcdfVar(ncfile, angstromV, dimList);

        createVcdfVar(ncfile, fmAodV, dimList);
        createVcdfVar(ncfile, dustAodV, dimList);
        createVcdfVar(ncfile, absAodV, dimList);

        createVcdfVar(ncfile, sreflec555V, dimList);
        createVcdfVar(ncfile, sreflec659V, dimList);
        createVcdfVar(ncfile, sreflec865V, dimList);
        createVcdfVar(ncfile, sreflec1610V, dimList);
        
        createVcdfVar(ncfile, sigAod550V, dimList);
        createVcdfVar(ncfile, sigAod659V, dimList);
        createVcdfVar(ncfile, sigAod865V, dimList);
        createVcdfVar(ncfile, sigAod1610V, dimList);

        createVcdfVar(ncfile, cldFracV, dimList);
        createVcdfVar(ncfile, landFlagV, dimList);
        createVcdfVar(ncfile, timeV, dimList);
        
        dimList = new ArrayList<Dimension>();
        dimList.add(dimPix);
        dimList.add(dimView);
        createVcdfVar(ncfile, szaV, dimList);
        createVcdfVar(ncfile, vzaV, dimList);
        createVcdfVar(ncfile, razV, dimList);

        dimList = new ArrayList<Dimension>();
        dimList.add(dimView);
        createVcdfVar(ncfile, viewV, dimList);
        dimList = new ArrayList<Dimension>();
        dimList.add(dimPix);
        createVcdfVar(ncfile, latV, dimList);
        createVcdfVar(ncfile, lonV, dimList);

        createGlobalAttrb(ncfile, p, ncdfName, version);
        ncfile.create();
        return ncfile;
    }
    
    private static void createGlobalAttrb(NetcdfFileWriteable ncfile, Product p, String ncdfName, DataVersionNumbers version) {
        final String prodFileName = p.getFileLocation().getName();
        final SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.ENGLISH);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        final String currentTime = df.format(new Date());
        //final String CurrentTime = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss", Locale.ENGLISH).format(new Date());
        final String StartDate = df.format(p.getStartTime().getAsDate());    //p.getStartTime().format();
        final String StopDate = df.format(p.getEndTime().getAsDate());
        final String prodName = p.getName();
        final boolean atsr2 = prodName.startsWith("AT2");
        final String idStr = new File(ncdfName).getName();
        ncfile.addGlobalAttribute("Conventions", "CF-1.6");
        ncfile.addGlobalAttribute("tracking_id", UUID.randomUUID().toString());
		ncfile.addGlobalAttribute("naming authority", "swan.ac.uk");
        //ncfile.addGlobalAttribute("naming authority", "uk.ac.su");
        ncfile.addGlobalAttribute("title", "AARDVARC CCI aerosol product level 2");
        ncfile.addGlobalAttribute("product_version", version.toString());
		ncfile.addGlobalAttribute("summary", "This dataset contains the level-2 aerosol optical depths from AATSR observations. Data are processed by Swansea algorithm");
		ncfile.addGlobalAttribute("id", idStr);
        if (atsr2) {
            ncfile.addGlobalAttribute("sensor", "ATSR2");
            ncfile.addGlobalAttribute("platform", "ERS2");
        }
        else {
            ncfile.addGlobalAttribute("sensor", "AATSR");
            ncfile.addGlobalAttribute("platform", "ENVISAT");
        }
        ncfile.addGlobalAttribute("resolution", "10km x 10km");
        ncfile.addGlobalAttribute("projection", "sinosoidal");
        ncfile.addGlobalAttribute("time_coverage_start", StartDate);
        ncfile.addGlobalAttribute("time_coverage_stop", StopDate);
        ncfile.addGlobalAttribute("time_coverage_duration", "P1D");
		//ncfile.addGlobalAttribute("time_coverage_resolution", "P1D");
		ncfile.addGlobalAttribute("geospatial_lat_min", "-90.0");
		ncfile.addGlobalAttribute("geospatial_lat_max", "90.0");
		ncfile.addGlobalAttribute("geospatial_lon_min", "-180.0");
		ncfile.addGlobalAttribute("geospatial_lon_max", "180.0");
        ncfile.addGlobalAttribute("date_created", currentTime);
		ncfile.addGlobalAttribute("project", "Climate Change Initiative - European Space Agency");
        ncfile.addGlobalAttribute("references", "http://www.esa-aerosol-cci.org");
		ncfile.addGlobalAttribute("creator_name", "Swansea University");
		ncfile.addGlobalAttribute("creator_url", "http://www.swan.ac.uk/staff/academic/environmentsociety/geography/northpeter/");
		ncfile.addGlobalAttribute("creator_email", "p.r.j.north@swansea.ac.uk, a.heckel@swansea.ac.uk");
        ncfile.addGlobalAttribute("source", "ATS_TOA_1P, V6.03");
		ncfile.addGlobalAttribute("keywords", "satellite,observation,aerosol");
		ncfile.addGlobalAttribute("keywords_vocabulary", "NASA Global Change Master Directory (GCMD) Science Keywords");
		ncfile.addGlobalAttribute("comment", "These data were produced at ESA CCI as part of the ESA Aerosol CCI project.");
		ncfile.addGlobalAttribute("standard_name_vocabulary", "NetCDF Climate and Forecast (CF) Metadata Convention version 18");
		ncfile.addGlobalAttribute("license", "ESA CCI Data Policy: free and open access");
        ncfile.addGlobalAttribute("inputFileList", prodFileName); //TODO: inputFileList should be blank space and carriage return separated 
        //ncfile.addGlobalAttribute("product", prodName);
        //ncfile.addGlobalAttribute("originator", "SU");
        //ncfile.addGlobalAttribute("originator_long", "Swansea University");
        //ncfile.addGlobalAttribute("originator_url", "http://www.swan.ac.uk/staff/academic/environmentsociety/geography/northpeter/");
        //ncfile.addGlobalAttribute("email", "p.r.j.north@swansea.ac.uk; a.heckel@swansea.ac.uk");
		//ncfile.addGlobalAttribute("institution", "Swansea University");
		//ncfile.addGlobalAttribute("history", "Level 2 product from Swansea algorithm");
		//ncfile.addGlobalAttribute("cdm_data_type", "Swath");
		//ncfile.addGlobalAttribute("date_created", "20120918T163335Z");
		//ncfile.addGlobalAttribute("geospatial_vertical_min", "0 km");
		//ncfile.addGlobalAttribute("geospatial_vertical_max", "0 km");
		//ncfile.addGlobalAttribute("geospatial_lat_units", "degrees_north");
		//ncfile.addGlobalAttribute("geospatial_lon_units", "degrees_east");         
    }

    private void createVcdfVar(NetcdfFileWriteable ncfile, NetcdfVariableProperties var, ArrayList<Dimension> dimList) {
        if (var.varName.equals("surface_type_number") || var.varName.equals("instrument_view")) {
            ncfile.addVariable(var.varName, DataType.BYTE, dimList);
        }
        else if (var.varName.equals("time")) {
            ncfile.addVariable(var.varName, DataType.INT, dimList);
        }
        else {
            ncfile.addVariable(var.varName, DataType.FLOAT, dimList);
        }
        ncfile.addVariableAttribute(var.varName, "long_name", var.longName);
        if (var.stdName.length() > 0) ncfile.addVariableAttribute(var.varName, "standard_name", var.stdName);
        ncfile.addVariableAttribute(var.varName, "units", var.units);
        if (var.varName.equals("surface_type_number")) {
        //    ncfile.addVariableAttribute(var.varName, "flag_values", ArrayByte.factory(new int[]{0,1}));
        //    ncfile.addVariableAttribute(var.varName, "flag_meanings", "sea, land");
        }
        else if (var.varName.equals("instrument_view")) {
        //    ncfile.addVariableAttribute(var.varName, "flag_values", ArrayByte.factory(new int[]{0,1}));
        //    ncfile.addVariableAttribute(var.varName, "flag_meanings", "nadir, forward");
        }
        else {
            if ( var.lowerRangeLimit instanceof Float) {
                Array a = Array.factory(new float[]{(Float) var.lowerRangeLimit, (Float) var.upperRangeLimit});
                ncfile.addVariableAttribute(var.varName, "valid_range", a);
                //ncfile.addVariableAttribute(var.varName, "actual_range", a);
                ncfile.addVariableAttribute(var.varName, "_FillValue", (Float)var.fillValue);
            }
            if ( var.lowerRangeLimit instanceof Integer) {
                Array a = Array.factory(new int[]{(Integer) var.lowerRangeLimit, (Integer) var.upperRangeLimit});
                ncfile.addVariableAttribute(var.varName, "valid_range", a);
                //ncfile.addVariableAttribute(var.varName, "actual_range", a);
                ncfile.addVariableAttribute(var.varName, "_FillValue", (Integer)var.fillValue);
            }
        }
        //ncfile.addVariableAttribute(var.varName, "scale_factor", 1.0f);
        //ncfile.addVariableAttribute(var.varName, "add_offset", 0.0f);
    }
    
    private static void createVcdfVar(NetcdfFileWriteable ncfile, final String vName, final String longName, ArrayList<Dimension> dimList) {
        String unitStr = "1";
        if (vName.equals("latitude"))  unitStr = "degrees_north";
        if (vName.equals("longitude")) unitStr = "degrees_east";
        ncfile.addVariable(vName, DataType.FLOAT, dimList);
        ncfile.addVariableAttribute(vName, "long_name", longName);
        ncfile.addVariableAttribute(vName, "_FillValue", -999f);
        ncfile.addVariableAttribute(vName, "units", unitStr);
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

    private float getRaz(float saa, float vaa) {
        float raz = Math.abs(saa - vaa);
        return (raz > 180) ? 360.0f - raz : raz;
    }

    
    
}
