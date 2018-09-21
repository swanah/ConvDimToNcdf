/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package akh.convdimtoncdf;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import ucar.ma2.Array;
import ucar.ma2.ArrayFloat;
import ucar.ma2.ArrayInt;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFileWriteable;
import ucar.nc2.NetcdfFileWriter;
import ucar.nc2.Variable;
import ucar.nc2.write.Nc4Chunking;

/**
 *
 * @author akheckel
 */
public class ProdConverterL3 {
    
    //
    // TODO: change long name of mean lv2 uncertainty fields!!!
    //
    
    private static ProdConverterL3 instance = null;
    
    private static boolean doSyn;
    private static boolean doSurfRefl;

    private final NetcdfVariableProperties latV = 
            new NetcdfVariableProperties("latitude", "latitude", "latitude", "degrees_north", DataType.FLOAT, -90f, 90f, null);
    private final NetcdfVariableProperties lonV = 
            new NetcdfVariableProperties("longitude", "longitude", "longitude", "degrees_east", DataType.FLOAT, -180f, 180f, null);
    private final NetcdfVariableProperties viewV = 
            new NetcdfVariableProperties("instrument_view", "Instrument view", "", "1", DataType.INT, 0, 1, null);
    private final NetcdfVariableProperties pixCountV = 
            new NetcdfVariableProperties("pixel_count", "number of retrieval pixels in grid cell", "", "1", DataType.INT, 1, 1000, 0, "latitude longitude");
    
    private final NetcdfVariableProperties aod550V[] = new NetcdfVariableProperties[]{
            new NetcdfVariableProperties("AOD550_mean", "aerosol optical thickness at 550 nm", "atmosphere_optical_thickness_due_to_ambient_aerosol", "1", DataType.FLOAT, 0f, 4f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("AOD550_sdev", "standard deviation aerosol optical thickness at 550 nm", "", "1", DataType.FLOAT, 0f, 4f, -999f, "latitude longitude")
    };
    //private final NetcdfVariableProperties aod555V[] = new NetcdfVariableProperties[]{
    //        new NetcdfVariableProperties("AOD555_mean", "aerosol optical thickness at 555 nm", "atmosphere_optical_thickness_due_to_ambient_aerosol", "1", 0f, 2f, -999f),
    //        new NetcdfVariableProperties("AOD555_sdev", "standard deviation aerosol optical thickness at 555 nm", "", "1", 0f, 2f, -999f)
    //};
    private final NetcdfVariableProperties aod659V[] = new NetcdfVariableProperties[]{
            new NetcdfVariableProperties("AOD670_mean", "aerosol optical thickness at 670 nm", "atmosphere_optical_thickness_due_to_ambient_aerosol", "1", DataType.FLOAT, 0f, 4f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("AOD670_sdev", "standard deviation aerosol optical thickness at 670 nm", "", "1", DataType.FLOAT, 0f, 4f, -999f, "latitude longitude")
    };
    private final NetcdfVariableProperties aod865V[] = new NetcdfVariableProperties[]{
            new NetcdfVariableProperties("AOD870_mean", "aerosol optical thickness at 870 nm", "atmosphere_optical_thickness_due_to_ambient_aerosol", "1", DataType.FLOAT, 0f, 4f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("AOD870_sdev", "standard deviation aerosol optical thickness at 870 nm", "", "1", DataType.FLOAT, 0f, 4f, -999f, "latitude longitude")
    };
    private final NetcdfVariableProperties aod1610V[] = new NetcdfVariableProperties[]{
            new NetcdfVariableProperties("AOD1600_mean", "aerosol optical thickness at 1600 nm", "atmosphere_optical_thickness_due_to_ambient_aerosol", "1", DataType.FLOAT, 0f, 4f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("AOD1600_sdev", "standard deviation aerosol optical thickness at 1600 nm", "", "1", DataType.FLOAT, 0f, 4f, -999f, "latitude longitude")
    };

    private final NetcdfVariableProperties sigAod550V[] = new NetcdfVariableProperties[]{
            new NetcdfVariableProperties("AOD550_uncertainty_mean", "Mean of L2 uncertainty on AOT at 550 nm", "", "1", DataType.FLOAT, 0f, 10f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("AOD550_uncertainty_sdev", "standard deviation of L2 Uncertainty on AOT at 550 nm", "", "1", DataType.FLOAT, 0f, 10f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("AOD550_uncertainty", "propagated L2 uncertainty in aerosol optical thickness at 550 nm", "", "1", DataType.FLOAT, 0f, 10f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("AOD550_uncertainty_min", "minimum L2 uncertainty on AOT at 550 nm", "", "1", DataType.FLOAT, 0f, 10f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("AOD550_uncertainty_max", "maximum L2 uncertainty on AOT at 550 nm", "", "1", DataType.FLOAT, 0f, 10f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("AOD550_uncertainty_3", "Root-mean-square of L2 uncertainty in aerosol optical thickness at 550 nm", "", "1", DataType.FLOAT, 0f, 10f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("AOD550_uncertainty_4", "AOD550 L2 standard deviation + RMS of L2 uncertainty for aerosol optical thickness at 550 nm", "", "1", DataType.FLOAT, 0f, 10f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("AOD550_uncertainty_6", "worst-case propagation of uncertainty in aerosol optical thickness at 550 nm", "", "1", DataType.FLOAT, 0f, 10f, -999f, "latitude longitude"),
    };
    //private final NetcdfVariableProperties sigAod555V[] = new NetcdfVariableProperties[]{
    //        new NetcdfVariableProperties("AOD_uncertainty555_mean", "Uncertainty on AOT at 555 nm", "", "1", 0f, 10f, -999f),
    //        new NetcdfVariableProperties("AOD_uncertainty555_sdev", "standard deviation, Uncertainty on AOT at 555 nm", "", "1", 0f, 10f, -999f),
    //        new NetcdfVariableProperties("AOD_uncertainty555_min", "minimum Uncertainty on AOT at 555 nm", "", "1", 0f, 10f, -999f),
    //        new NetcdfVariableProperties("AOD_uncertainty555_max", "maximum Uncertainty on AOT at 555 nm", "", "1", 0f, 10f, -999f)
    //};
    private final NetcdfVariableProperties sigAod670V[] = new NetcdfVariableProperties[]{
            new NetcdfVariableProperties("AOD670_uncertainty_mean", "Mean of L2 Uncertainty on AOT at 670 nm", "", "1", DataType.FLOAT, 0f, 10f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("AOD670_uncertainty_sdev", "standard deviation of L2 Uncertainty on AOT at 670 nm", "", "1", DataType.FLOAT, 0f, 10f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("AOD670_uncertainty", "propagated L2 uncertainty in aerosol optical thickness at 670 nm", "", "1", DataType.FLOAT, 0f, 10f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("AOD670_uncertainty_min", "minimum L2 Uncertainty on AOT at 670 nm", "", "1", DataType.FLOAT, 0f, 10f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("AOD670_uncertainty_max", "maximum L2 Uncertainty on AOT at 670 nm", "", "1", DataType.FLOAT, 0f, 10f, -999f, "latitude longitude")
    };
    private final NetcdfVariableProperties sigAod870V[] = new NetcdfVariableProperties[]{
            new NetcdfVariableProperties("AOD870_uncertainty_mean", "Mean of L2 uncertainty on AOT at 870 nm", "", "1", DataType.FLOAT, 0f, 10f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("AOD870_uncertainty_sdev", "standard deviation of L2 uncertainty on AOT at 870 nm", "", "1", DataType.FLOAT, 0f, 10f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("AOD870_uncertainty", "propagated L2 uncertainty aerosol optical thickness at 870 nm", "", "1", DataType.FLOAT, 0f, 10f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("AOD870_uncertainty_min", "minimum L2 uncertainty on AOT at 870 nm", "", "1", DataType.FLOAT, 0f, 10f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("AOD870_uncertainty_max", "maximum L2 uncertainty on AOT at 870 nm", "", "1", DataType.FLOAT, 0f, 10f, -999f, "latitude longitude")
    };
    private final NetcdfVariableProperties sigAod1600V[] = new NetcdfVariableProperties[]{
            new NetcdfVariableProperties("AOD1600_uncertainty_mean", "Mean of L2 uncertainty on AOT at 1600 nm", "", "1", DataType.FLOAT, 0f, 10f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("AOD1600_uncertainty_sdev", "standard deviation of L2 uncertainty on AOT at 1600 nm", "", "1", DataType.FLOAT, 0f, 10f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("AOD1600_uncertainty", "propagated L2 uncertainty in aerosol optical thickness at 1600 nm", "", "1", DataType.FLOAT, 0f, 10f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("AOD1600_uncertainty_min", "minimum L2 uncertainty on AOT at 1600 nm", "", "1", DataType.FLOAT, 0f, 10f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("AOD1600_uncertainty_max", "maximum L2 uncertainty on AOT at 1600 nm", "", "1", DataType.FLOAT, 0f, 10f, -999f, "latitude longitude")
    };

    //private final NetcdfVariableProperties angstrom659V[] = new NetcdfVariableProperties[]{
    //        new NetcdfVariableProperties("Angstrom_555_659_mean", "angstrom exponent computed on AOD555nm and AOD659nm", "aerosol_angstrom_exponent", "1", -5f, 5f, -999f),
    //        new NetcdfVariableProperties("Angstrom_555_659_sdev", "standard deviation angstrom exponent computed on AOD555nm and AOD659nm", "", "1", -5f, 5f, -999f)
    //};

    private final NetcdfVariableProperties angstrom865V[] = new NetcdfVariableProperties[]{
            new NetcdfVariableProperties("ANG550_870_mean", "angstrom exponent computed on AOD550nm and AOD870nm", "angstrom_exponent_of_ambient_aerosol_in_air", "1", DataType.FLOAT, -5f, 5f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("ANG550_870_sdev", "standard deviation angstrom exponent computed on AOD550nm and AOD870nm", "", "1", DataType.FLOAT, -5f, 5f, -999f, "latitude longitude")
    };

    private final NetcdfVariableProperties fineModeV[] = new NetcdfVariableProperties[]{
            new NetcdfVariableProperties("FM_AOD550_mean", "fine mode AOD", "atmosphere_optical_thickness_due_to_ambient_aerosol", "1", DataType.FLOAT, 0f, 4f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("FM_AOD550_sdev", "standard deviation fine mode AOD", "", "1", DataType.FLOAT, 0f, 4f, -999f, "latitude longitude")
    };
    private final NetcdfVariableProperties absModeV[] = new NetcdfVariableProperties[]{
            new NetcdfVariableProperties("AAOD550_mean", "absorbing AOD", "atmosphere_optical_thickness_due_to_ambient_aerosol", "1", DataType.FLOAT, 0f, 4f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("AAOD550_sdev", "standard deviation absorbing AOD", "", "1", DataType.FLOAT, 0f, 4f, -999f, "latitude longitude")
    };
    private final NetcdfVariableProperties dustModeV[] = new NetcdfVariableProperties[]{
            new NetcdfVariableProperties("D_AOD550_mean", "non-spherical dust AOD", "atmosphere_optical_thickness_due_to_ambient_aerosol", "1", DataType.FLOAT, 0f, 4f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("D_AOD550_sdev", "standard deviation non-spherical dust AOD", "", "1", DataType.FLOAT, 0f, 4f, -999f, "latitude longitude")
    };
    private final NetcdfVariableProperties ssaV[] = new NetcdfVariableProperties[]{
            new NetcdfVariableProperties("SSA550_mean", "single scattering albedo at 550nm", "", "1", DataType.FLOAT, 0f, 2f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("SSA550_sdev", "standard deviation of SSA", "", "1", DataType.FLOAT, 0f, 2f, -999f, "latitude longitude")
    };
    
    private final NetcdfVariableProperties sreflec555V[] = new NetcdfVariableProperties[]{
            new NetcdfVariableProperties("surface_reflectance550_mean", "mean bidirectional surface reflectance (nadir)", "", "1", DataType.FLOAT, 0f, 2f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("surface_reflectance550_sdev", "standard deviation mean bidirectional surface reflectance (nadir)", "", "1", DataType.FLOAT, 0f, 2f, -999f, "latitude longitude")
    };
    private final NetcdfVariableProperties sreflec659V[] = new NetcdfVariableProperties[]{
            new NetcdfVariableProperties("surface_reflectance670_mean", "mean bidirectional surface reflectance (nadir)", "", "1", DataType.FLOAT, 0f, 2f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("surface_reflectance670_sdev", "standard deviation mean bidirectional surface reflectance (nadir)", "", "1", DataType.FLOAT, 0f, 2f, -999f, "latitude longitude")
    };
    private final NetcdfVariableProperties sreflec865V[] = new NetcdfVariableProperties[]{
            new NetcdfVariableProperties("surface_reflectance870_mean", "mean bidirectional surface reflectance (nadir)", "", "1", DataType.FLOAT, 0f, 2f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("surface_reflectance870_sdev", "standard deviation mean bidirectional surface reflectance (nadir)", "", "1", DataType.FLOAT, 0f, 2f, -999f, "latitude longitude")
    };
    private final NetcdfVariableProperties sreflec1610V[] = new NetcdfVariableProperties[]{
            new NetcdfVariableProperties("surface_reflectance1600_mean", "mean bidirectional surface reflectance (nadir)", "", "1", DataType.FLOAT, 0f, 2f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("surface_reflectance1600_sdev", "standard deviation mean bidirectional surface reflectance (nadir)", "", "1", DataType.FLOAT, 0f, 2f, -999f, "latitude longitude")
    };

    private final NetcdfVariableProperties cldFracV[] = new NetcdfVariableProperties[]{
            new NetcdfVariableProperties("cloud_fraction_mean", "mean fraction of cloud flagged pixels in 10km bin", "cloud_area_fraction", "1", DataType.FLOAT, 0f, 1f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("cloud_fraction_sdev", "standard deviation mean fraction of cloud flagged pixels in 10km bin", "", "1", DataType.FLOAT, 0f, 1f, -999f, "latitude longitude")
    };
    private final NetcdfVariableProperties landFlagV[] = new NetcdfVariableProperties[]{
            new NetcdfVariableProperties("surface_type_number_mean", "mean land fraction", "land_area_fraction", "1", DataType.FLOAT, 0f, 1f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("surface_type_number_sdev", "standard deviation mean land fraction", "land_area_fraction", "1", DataType.FLOAT, 0f, 1f, -999f, "latitude longitude")
    };

    private final NetcdfVariableProperties szaV[] = new NetcdfVariableProperties[]{
            new NetcdfVariableProperties("sun_zenith_mean", "solar zenith angle", "solar_zenith_angle", "degrees", DataType.FLOAT, 0f, 90f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("sun_zenith_sdev", "standard deviation solar zenith angle", "", "degrees", DataType.FLOAT, 0f, 90f, -999f, "latitude longitude")
    };
    private final NetcdfVariableProperties vzaV[] = new NetcdfVariableProperties[]{
            new NetcdfVariableProperties("satellite_zenith_mean", "satellite zenith angle", "zenith_angle", "degrees", DataType.FLOAT, 0f, 90f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("satellite_zenith_sdev", "standard deviation satellite zenith angle", "", "degrees", DataType.FLOAT, 0f, 90f, -999f, "latitude longitude")
    };
    private final NetcdfVariableProperties razV[] = new NetcdfVariableProperties[]{
            new NetcdfVariableProperties("relative_azimuth_mean", "relative azimuth angle", "", "degrees", DataType.FLOAT, 0f, 90f, -999f, "latitude longitude"),
            new NetcdfVariableProperties("relative_azimuth_sdev", "standard deviation relative azimuth angle", "", "degrees", DataType.FLOAT, 0f, 90f, -999f, "latitude longitude")
    };
    
    
    private ProdConverterL3() throws Exception {
        latV.axis = "Y";
        lonV.axis = "X";
        viewV.flagValues = Array.factory(new int[]{0,1});
        viewV.flagMeanings = "nadir forward";
    }

    public static ProdConverterL3 getInstance() throws Exception{
        if (instance == null){
            instance = new ProdConverterL3();
        }
        return instance;
    }

    public void writeGridsS3(DataGridder gridder, String ncName, S3DataVersionNumbers version) throws IOException, InvalidRangeException {
        doSyn = gridder.isDoSyn();
        doSurfRefl = gridder.isDoSurfRefl();
        final SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.ENGLISH);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        Map<String, String> prodInfo = new HashMap<String, String>(4);
        prodInfo.put("StartDate", df.format(gridder.getStartTime().getAsDate()));
        prodInfo.put("StopDate", df.format(gridder.getEndTime().getAsDate()));
        prodInfo.put("Name", "");
        prodInfo.put("ProductList", gridder.getProducts());
        prodInfo.put("Source", gridder.getSourceStr());
        NetcdfFileWriter ncdfFile = createNcdfFile_S3(ncName, prodInfo, version);
        
        float[] latArr = new float[180];
        for (int i=0; i<180; i++) latArr[i] = i - 89.5f;
        ncdfFile.write(latV.ncV, ArrayFloat.factory(latArr));
        float[] lonArr = new float[360];
        for (int i=0; i<360; i++) lonArr[i] = i - 179.5f;
        ncdfFile.write(lonV.ncV, ArrayFloat.factory(lonArr));

        //ncdfFile.write(viewV.ncV, ArrayFloat.factory(new int[]{0, 1}));
        
        Array dI = new ArrayInt.D2(1, 1);
        Array dF = new ArrayFloat.D2(1, 1);
        ArrayFloat.D3 dF3 = new ArrayFloat.D3(1, 1, 2);
        int[] origin = new int[3];
        origin[2] = 0;
        for (origin[0]=0; origin[0]<180; origin[0]++){
            for (origin[1] = 0; origin[1] < 360; origin[1]++) {
                if (gridder.countGrid[origin[0]][origin[1]] > 0) {
                    dI.setInt(0, gridder.countGrid[origin[0]][origin[1]]);
                    ncdfFile.write(pixCountV.ncV, origin, dI);
                    for (int i = 0; i < 2; i++) {
                        dF.setFloat(0, getMeanOrSdev(i, gridder.aod550Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(aod550V[i].ncV, origin, dF);

                        dF.setFloat(0, getMeanOrSdev(i, gridder.sigAod550Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(sigAod550V[i].ncV, origin, dF);

                        //dF.setFloat(0, getMeanOrSdev(i, gridder.aod0555Grid, gridder.countGrid, origin[0], origin[1]));
                        //ncdfFile.write(aod555V[i].ncV, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.aod670Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(aod659V[i].ncV, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.aod870Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(aod865V[i].ncV, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.aod1600Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(aod1610V[i].ncV, origin, dF);

                        //dF.setFloat(0, getMeanOrSdev(i, gridder.sigAod555Grid, gridder.countGrid, origin[0], origin[1]));
                        //ncdfFile.write(sigAod555V[i].ncV, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.sigAod659Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(sigAod670V[i].ncV, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.sigAod865Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(sigAod870V[i].ncV, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.sigAod1600Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(sigAod1600V[i].ncV, origin, dF);

                        if (doSurfRefl){
                            dF.setFloat(0, getMeanOrSdev(i, gridder.rsurf0555Grid, gridder.countGrid, origin[0], origin[1]));
                            ncdfFile.write(sreflec555V[i].ncV, origin, dF);
                            dF.setFloat(0, getMeanOrSdev(i, gridder.rsurf0659Grid, gridder.countGrid, origin[0], origin[1]));
                            ncdfFile.write(sreflec659V[i].ncV, origin, dF);
                            dF.setFloat(0, getMeanOrSdev(i, gridder.rsurf0865Grid, gridder.countGrid, origin[0], origin[1]));
                            ncdfFile.write(sreflec865V[i].ncV, origin, dF);
                            dF.setFloat(0, getMeanOrSdev(i, gridder.rsurf1600Grid, gridder.countGrid, origin[0], origin[1]));
                            ncdfFile.write(sreflec1610V[i].ncV, origin, dF);
                        }
                        //dF.setFloat(0, getMeanOrSdev(i, gridder.angstrom659Grid, gridder.countGrid, origin[0], origin[1]));
                        //ncdfFile.write(angstrom659V[i].ncV, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.angstrom865Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(angstrom865V[i].ncV, origin, dF);
                        
                        dF.setFloat(0, getMeanOrSdev(i, gridder.fineModeAodGrid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(fineModeV[i].ncV, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.absModeAodGrid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(absModeV[i].ncV, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.dustModeAodGrid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(dustModeV[i].ncV, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.ssaGrid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(ssaV[i].ncV, origin, dF);

                        //dF.setFloat(0, getMeanOrSdev(i, gridder.cldFracGrid, gridder.countGrid, origin[0], origin[1]));
                        //ncdfFile.write(cldFracV[i].ncV, origin, dF);
                        //dF.setFloat(0, getMeanOrSdev(i, gridder.landFracGrid, gridder.countGrid, origin[0], origin[1]));
                        //ncdfFile.write(landFlagV[i].ncV, origin, dF);

                        //dF3.set(0, 0, 0, getMeanOrSdev(i, gridder.szaGrid, gridder.countGrid, origin[0], origin[1], 0));
                        //dF3.set(0, 0, 1, getMeanOrSdev(i, gridder.szaGrid, gridder.countGrid, origin[0], origin[1], 1));
                        //ncdfFile.write(szaV[i].ncV, origin, dF3);
                        //dF3.set(0, 0, 0, getMeanOrSdev(i, gridder.vzaGrid, gridder.countGrid, origin[0], origin[1], 0));
                        //dF3.set(0, 0, 1, getMeanOrSdev(i, gridder.vzaGrid, gridder.countGrid, origin[0], origin[1], 1));
                        //ncdfFile.write(vzaV[i].ncV, origin, dF3);
                        //dF3.set(0, 0, 0, getMeanOrSdev(i, gridder.razGrid, gridder.countGrid, origin[0], origin[1], 0));
                        //dF3.set(0, 0, 1, getMeanOrSdev(i, gridder.razGrid, gridder.countGrid, origin[0], origin[1], 1));
                        //ncdfFile.write(razV[i].ncV, origin, dF3);

                    }
                    //
                    // write propagted Uncertainty
                    //
/**/
                    float unc = propagateUnc(gridder.aod550Grid, gridder.sigAod550Grid, gridder.countGrid, origin[0], origin[1]);
                    dF.setFloat(0, unc);
                    ncdfFile.write(sigAod550V[2].ncV, origin, dF);
                    unc = propagateUnc(gridder.aod670Grid, gridder.sigAod659Grid, gridder.countGrid, origin[0], origin[1]);
                    dF.setFloat(0, unc);
                    ncdfFile.write(sigAod670V[2].ncV, origin, dF);
                    unc = propagateUnc(gridder.aod870Grid, gridder.sigAod865Grid, gridder.countGrid, origin[0], origin[1]);
                    dF.setFloat(0, unc);
                    ncdfFile.write(sigAod870V[2].ncV, origin, dF);
                    unc = propagateUnc(gridder.aod1600Grid, gridder.sigAod1600Grid, gridder.countGrid, origin[0], origin[1]);
                    dF.setFloat(0, unc);
                    ncdfFile.write(sigAod1600V[2].ncV, origin, dF);
/**/
                    //
                    // write Min Max values of L2 Uncertainties
                    //
/**/
                    for (int i = 3; i < 5; i++) {
                        dF.setFloat(0, getMeanOrSdev(i, gridder.sigAod550Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(sigAod550V[i].ncV, origin, dF);
                        //dF.setFloat(0, getMeanOrSdev(i, gridder.sigAod555Grid, gridder.countGrid, origin[0], origin[1]));
                        //ncdfFile.write(sigAod555V[i].ncV, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.sigAod659Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(sigAod670V[i].ncV, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.sigAod865Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(sigAod870V[i].ncV, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.sigAod1600Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(sigAod1600V[i].ncV, origin, dF);
                    }
/**/
                }
            }
        }
    }
    
    public void writeGrids(DataGridder gridder, String ncName, DataVersionNumbers version) throws IOException, InvalidRangeException {
        
        if ( version.isGT(DataVersionNumbers.v4_0)) {
            writeGrids_V4(gridder, ncName, version);
            return;
        } else if (version.equals("1.0")) {
            writeGrids_V10(gridder, ncName, version);
            return;
        }
        
        Map<String, String> prodInfo = new HashMap<String, String>(4);
        prodInfo.put("StartDate", gridder.getStartTime().format());
        prodInfo.put("StopDate", gridder.getEndTime().format());
        prodInfo.put("Name", "");
        prodInfo.put("Products", gridder.getProducts());
        NetcdfFileWriteable ncdfFile = createNcdfFile(ncName, prodInfo, version);

        float[] latArr = new float[180];
        for (int i=0; i<180; i++) latArr[i] = i - 89.5f;
        ncdfFile.write(latV.varName, ArrayFloat.factory(latArr));
        float[] lonArr = new float[360];
        for (int i=0; i<360; i++) lonArr[i] = i - 179.5f;
        ncdfFile.write(lonV.varName, ArrayFloat.factory(lonArr));
        
        Array dI = new ArrayInt.D2(1, 1);
        Array dF = new ArrayFloat.D2(1, 1);
        ArrayFloat.D3 dF3 = new ArrayFloat.D3(1, 1, 2);
        int[] origin = new int[3];
        origin[2] = 0;
        for (origin[0]=0; origin[0]<180; origin[0]++){
            for (origin[1]=0; origin[1]<360; origin[1]++){
                if (gridder.countGrid[origin[0]][origin[1]] > 0){
                    dI.setInt(0, gridder.countGrid[origin[0]][origin[1]]);
                    ncdfFile.write(pixCountV.varName, origin, dI);
                    for (int i = 0; i < 2; i++) {
                        dF.setFloat(0, getMeanOrSdev(i, gridder.aod550Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(aod550V[i].varName, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.sigAod550Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(sigAod550V[i].varName, origin, dF);

                        //dF.setFloat(0, getMeanOrSdev(i, gridder.aod0555Grid, gridder.countGrid, origin[0], origin[1]));
                        //ncdfFile.write(aod555V[i].varName, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.aod670Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(aod659V[i].varName, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.aod870Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(aod865V[i].varName, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.aod1600Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(aod1610V[i].varName, origin, dF);

                        //dF.setFloat(0, getMeanOrSdev(i, gridder.sigAod555Grid, gridder.countGrid, origin[0], origin[1]));
                        //ncdfFile.write(sigAod555V[i].varName, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.sigAod659Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(sigAod670V[i].varName, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.sigAod865Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(sigAod870V[i].varName, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.sigAod1600Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(sigAod1600V[i].varName, origin, dF);

                        dF3.set(0, 0, 0, getMeanOrSdev(i, gridder.szaGrid, gridder.countGrid, origin[0], origin[1], 0));
                        dF3.set(0, 0, 1, getMeanOrSdev(i, gridder.szaGrid, gridder.countGrid, origin[0], origin[1], 1));
                        ncdfFile.write(szaV[i].varName, origin, dF3);
                        dF3.set(0, 0, 0, getMeanOrSdev(i, gridder.vzaGrid, gridder.countGrid, origin[0], origin[1], 0));
                        dF3.set(0, 0, 1, getMeanOrSdev(i, gridder.vzaGrid, gridder.countGrid, origin[0], origin[1], 1));
                        ncdfFile.write(vzaV[i].varName, origin, dF3);
                        dF3.set(0, 0, 0, getMeanOrSdev(i, gridder.razGrid, gridder.countGrid, origin[0], origin[1], 0));
                        dF3.set(0, 0, 1, getMeanOrSdev(i, gridder.razGrid, gridder.countGrid, origin[0], origin[1], 1));
                        ncdfFile.write(razV[i].varName, origin, dF3);
                    }
                }
            }
        }

        
    }
    
    public void writeGrids_V4(DataGridder gridder, String ncName, DataVersionNumbers version) throws IOException, InvalidRangeException {
        doSyn = gridder.isDoSyn();
        doSurfRefl = gridder.isDoSurfRefl();
        final SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.ENGLISH);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        Map<String, String> prodInfo = new HashMap<String, String>(4);
        prodInfo.put("StartDate", df.format(gridder.getStartTime().getAsDate()));
        prodInfo.put("StopDate", df.format(gridder.getEndTime().getAsDate()));
        prodInfo.put("Name", "");
        prodInfo.put("ProductList", gridder.getProducts());
        NetcdfFileWriter ncdfFile = createNcdfFile_V4(ncName, prodInfo, version);
        
        float[] latArr = new float[180];
        for (int i=0; i<180; i++) latArr[i] = i - 89.5f;
        ncdfFile.write(latV.ncV, ArrayFloat.factory(latArr));
        float[] lonArr = new float[360];
        for (int i=0; i<360; i++) lonArr[i] = i - 179.5f;
        ncdfFile.write(lonV.ncV, ArrayFloat.factory(lonArr));

        //ncdfFile.write(viewV.ncV, ArrayFloat.factory(new int[]{0, 1}));
        
        Array dI = new ArrayInt.D2(1, 1);
        Array dF = new ArrayFloat.D2(1, 1);
        ArrayFloat.D3 dF3 = new ArrayFloat.D3(1, 1, 2);
        int[] origin = new int[3];
        origin[2] = 0;
        for (origin[0]=0; origin[0]<180; origin[0]++){
            for (origin[1] = 0; origin[1] < 360; origin[1]++) {
                if (gridder.countGrid[origin[0]][origin[1]] > 0) {
                    dI.setInt(0, gridder.countGrid[origin[0]][origin[1]]);
                    ncdfFile.write(pixCountV.ncV, origin, dI);
                    for (int i = 0; i < 2; i++) {
                        dF.setFloat(0, getMeanOrSdev(i, gridder.aod550Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(aod550V[i].ncV, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.sigAod550Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(sigAod550V[i].ncV, origin, dF);

                        //dF.setFloat(0, getMeanOrSdev(i, gridder.aod0555Grid, gridder.countGrid, origin[0], origin[1]));
                        //ncdfFile.write(aod555V[i].ncV, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.aod670Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(aod659V[i].ncV, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.aod870Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(aod865V[i].ncV, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.aod1600Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(aod1610V[i].ncV, origin, dF);

                        //dF.setFloat(0, getMeanOrSdev(i, gridder.sigAod555Grid, gridder.countGrid, origin[0], origin[1]));
                        //ncdfFile.write(sigAod555V[i].ncV, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.sigAod659Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(sigAod670V[i].ncV, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.sigAod865Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(sigAod870V[i].ncV, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.sigAod1600Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(sigAod1600V[i].ncV, origin, dF);

                        if (doSurfRefl){
                            dF.setFloat(0, getMeanOrSdev(i, gridder.rsurf0555Grid, gridder.countGrid, origin[0], origin[1]));
                            ncdfFile.write(sreflec555V[i].ncV, origin, dF);
                            dF.setFloat(0, getMeanOrSdev(i, gridder.rsurf0659Grid, gridder.countGrid, origin[0], origin[1]));
                            ncdfFile.write(sreflec659V[i].ncV, origin, dF);
                            dF.setFloat(0, getMeanOrSdev(i, gridder.rsurf0865Grid, gridder.countGrid, origin[0], origin[1]));
                            ncdfFile.write(sreflec865V[i].ncV, origin, dF);
                            dF.setFloat(0, getMeanOrSdev(i, gridder.rsurf1600Grid, gridder.countGrid, origin[0], origin[1]));
                            ncdfFile.write(sreflec1610V[i].ncV, origin, dF);
                        }
                        //dF.setFloat(0, getMeanOrSdev(i, gridder.angstrom659Grid, gridder.countGrid, origin[0], origin[1]));
                        //ncdfFile.write(angstrom659V[i].ncV, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.angstrom865Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(angstrom865V[i].ncV, origin, dF);
                        
                        dF.setFloat(0, getMeanOrSdev(i, gridder.fineModeAodGrid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(fineModeV[i].ncV, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.absModeAodGrid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(absModeV[i].ncV, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.dustModeAodGrid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(dustModeV[i].ncV, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.ssaGrid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(ssaV[i].ncV, origin, dF);

                        dF.setFloat(0, getMeanOrSdev(i, gridder.cldFracGrid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(cldFracV[i].ncV, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.landFracGrid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(landFlagV[i].ncV, origin, dF);

                        //dF3.set(0, 0, 0, getMeanOrSdev(i, gridder.szaGrid, gridder.countGrid, origin[0], origin[1], 0));
                        //dF3.set(0, 0, 1, getMeanOrSdev(i, gridder.szaGrid, gridder.countGrid, origin[0], origin[1], 1));
                        //ncdfFile.write(szaV[i].ncV, origin, dF3);
                        //dF3.set(0, 0, 0, getMeanOrSdev(i, gridder.vzaGrid, gridder.countGrid, origin[0], origin[1], 0));
                        //dF3.set(0, 0, 1, getMeanOrSdev(i, gridder.vzaGrid, gridder.countGrid, origin[0], origin[1], 1));
                        //ncdfFile.write(vzaV[i].ncV, origin, dF3);
                        //dF3.set(0, 0, 0, getMeanOrSdev(i, gridder.razGrid, gridder.countGrid, origin[0], origin[1], 0));
                        //dF3.set(0, 0, 1, getMeanOrSdev(i, gridder.razGrid, gridder.countGrid, origin[0], origin[1], 1));
                        //ncdfFile.write(razV[i].ncV, origin, dF3);
                    }
                    //
                    // write propagted Uncertainty
                    //
                    float unc = propagateUnc(gridder.aod550Grid, gridder.sigAod550Grid, gridder.countGrid, origin[0], origin[1]);
                    dF.setFloat(0, unc);
                    ncdfFile.write(sigAod550V[2].ncV, origin, dF);
                    unc = propagateUnc(gridder.aod670Grid, gridder.sigAod659Grid, gridder.countGrid, origin[0], origin[1]);
                    dF.setFloat(0, unc);
                    ncdfFile.write(sigAod670V[2].ncV, origin, dF);
                    unc = propagateUnc(gridder.aod870Grid, gridder.sigAod865Grid, gridder.countGrid, origin[0], origin[1]);
                    dF.setFloat(0, unc);
                    ncdfFile.write(sigAod870V[2].ncV, origin, dF);
                    unc = propagateUnc(gridder.aod1600Grid, gridder.sigAod1600Grid, gridder.countGrid, origin[0], origin[1]);
                    dF.setFloat(0, unc);
                    ncdfFile.write(sigAod1600V[2].ncV, origin, dF);
                    //
                    // write Min Max values of L2 Uncertainties
                    //
                    for (int i = 3; i < 5; i++) {
                        dF.setFloat(0, getMeanOrSdev(i, gridder.sigAod550Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(sigAod550V[i].ncV, origin, dF);
                        //dF.setFloat(0, getMeanOrSdev(i, gridder.sigAod555Grid, gridder.countGrid, origin[0], origin[1]));
                        //ncdfFile.write(sigAod555V[i].ncV, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.sigAod659Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(sigAod670V[i].ncV, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.sigAod865Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(sigAod870V[i].ncV, origin, dF);
                        dF.setFloat(0, getMeanOrSdev(i, gridder.sigAod1600Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(sigAod1600V[i].ncV, origin, dF);
                    }
                    if (version.equals(DataVersionNumbers.v4_21u)) {
                        float[] moreUnc = getMoreUnc(gridder.aod550Grid, gridder.sigAod550Grid, gridder.countGrid, origin[0], origin[1]);
                        for (int i = 5; i < 8; i++) {
                            dF.setFloat(0, moreUnc[i - 5]);
                            ncdfFile.write(sigAod550V[i].ncV, origin, dF);
                        }
                    }
                }
            }
        }
    }

    public void writeGrids_V10(DataGridder gridder, String ncName, DataVersionNumbers version) throws IOException, InvalidRangeException {
        
        Map<String, String> prodInfo = new HashMap<String, String>(4);
        prodInfo.put("StartDate", gridder.getStartTime().format());
        prodInfo.put("StopDate", gridder.getEndTime().format());
        prodInfo.put("Name", "");
        prodInfo.put("Products", gridder.getProducts());
        NetcdfFileWriteable ncdfFile = createNcdfFile_V10(ncName, prodInfo, version);

        float[] latArr = new float[180];
        for (int i=0; i<180; i++) latArr[i] = i - 89.5f;
        ncdfFile.write(latV.varName, ArrayFloat.factory(latArr));
        float[] lonArr = new float[360];
        for (int i=0; i<360; i++) lonArr[i] = i - 179.5f;
        ncdfFile.write(lonV.varName, ArrayFloat.factory(lonArr));
        
        Array dI = new ArrayInt.D2(1, 1);
        Array dF = new ArrayFloat.D2(1, 1);
        ArrayFloat.D3 dF3 = new ArrayFloat.D3(1, 1, 2);
        int[] origin = new int[3];
        origin[2] = 0;
        for (origin[0]=0; origin[0]<180; origin[0]++){
            for (origin[1]=0; origin[1]<360; origin[1]++){
                if (gridder.countGrid[origin[0]][origin[1]] > 0){
                    dI.setInt(0, gridder.countGrid[origin[0]][origin[1]]);
                    ncdfFile.write(pixCountV.varName, origin, dI);
                    for (int i = 0; i < 2; i++) {
                        dF.setFloat(0, getMeanOrSdev(i, gridder.aod550Grid, gridder.countGrid, origin[0], origin[1]));
                        ncdfFile.write(aod550V[i].varName, origin, dF);

                        dF3.set(0, 0, 0, getMeanOrSdev(i, gridder.szaGrid, gridder.countGrid, origin[0], origin[1], 0));
                        dF3.set(0, 0, 1, getMeanOrSdev(i, gridder.szaGrid, gridder.countGrid, origin[0], origin[1], 1));
                        ncdfFile.write(szaV[i].varName, origin, dF3);
                        dF3.set(0, 0, 0, getMeanOrSdev(i, gridder.vzaGrid, gridder.countGrid, origin[0], origin[1], 0));
                        dF3.set(0, 0, 1, getMeanOrSdev(i, gridder.vzaGrid, gridder.countGrid, origin[0], origin[1], 1));
                        ncdfFile.write(vzaV[i].varName, origin, dF3);
                        dF3.set(0, 0, 0, getMeanOrSdev(i, gridder.razGrid, gridder.countGrid, origin[0], origin[1], 0));
                        dF3.set(0, 0, 1, getMeanOrSdev(i, gridder.razGrid, gridder.countGrid, origin[0], origin[1], 1));
                        ncdfFile.write(razV[i].varName, origin, dF3);
                    }
                }
            }
        }

        
    }
    
    private NetcdfFileWriteable createNcdfFile(String ncdfName, Map<String, String> prodInfo, DataVersionNumbers version) throws IOException {

        NetcdfFileWriteable ncfile = NetcdfFileWriteable.createNew(ncdfName, true);
        Dimension dimLat = ncfile.addDimension("latitude", 180);
        Dimension dimLon = ncfile.addDimension("longitude", 360);
        Dimension dimView = ncfile.addDimension("instrument_view", 2);

        ArrayList<Dimension> dimList = new ArrayList<Dimension>();
        dimList.add(dimLat);
        createVcdfVar(ncfile, latV, dimList);
        dimList = new ArrayList<Dimension>();
        dimList.add(dimLon);
        createVcdfVar(ncfile, lonV, dimList);
        
        dimList = new ArrayList<Dimension>();
        dimList.add(dimLat);
        dimList.add(dimLon);
        createVcdfVar(ncfile, pixCountV, dimList);

        for (int i=0; i<2; i++){
            dimList = new ArrayList<Dimension>();
            dimList.add(dimLat);
            dimList.add(dimLon);
            createVcdfVar(ncfile, aod550V[i], dimList);
            createVcdfVar(ncfile, sigAod550V[i], dimList);
            //createVcdfVar(ncfile, aod555V[i], dimList);
            //createVcdfVar(ncfile, sigAod555V[i], dimList);
            createVcdfVar(ncfile, aod659V[i], dimList);
            createVcdfVar(ncfile, sigAod670V[i], dimList);
            createVcdfVar(ncfile, aod865V[i], dimList);
            createVcdfVar(ncfile, sigAod870V[i], dimList);
            createVcdfVar(ncfile, aod1610V[i], dimList);
            createVcdfVar(ncfile, sigAod1600V[i], dimList);

            dimList.add(dimView);
            createVcdfVar(ncfile, szaV[i], dimList);
            createVcdfVar(ncfile, vzaV[i], dimList);
            createVcdfVar(ncfile, razV[i], dimList);
        }

        createGlobalAttrb(ncfile, prodInfo, ncdfName, version);
        ncfile.create();
        return ncfile;
    }

    private NetcdfFileWriter createNcdfFile_S3(String ncdfName, Map<String,String> prodInfo, S3DataVersionNumbers version) throws IOException {
        NetcdfFileWriter ncfile = NetcdfFileWriter.createNew(NetcdfFileWriter.Version.netcdf4, ncdfName);
        Dimension dimLat = ncfile.addDimension(null, "latitude", 180);
        Dimension dimLon = ncfile.addDimension(null, "longitude", 360);
        Dimension dimView = ncfile.addDimension(null, "instrument_view", 2);
        ArrayList<Dimension> latLst = new ArrayList<Dimension>();
        latLst.add(dimLat);
        ArrayList<Dimension> lonLst = new ArrayList<Dimension>();
        lonLst.add(dimLon);
        ArrayList<Dimension> viewLst = new ArrayList<Dimension>();
        viewLst.add(dimView);
        ArrayList<Dimension> latLonLst = new ArrayList<Dimension>();
        latLonLst.add(dimLat);
        latLonLst.add(dimLon);
        //ArrayList<Dimension> latLonViewLst = new ArrayList<>();
        //latLonViewLst.add(dimLat);
        //latLonViewLst.add(dimLon);
        //latLonViewLst.add(dimView);

        createVcdfVar(ncfile, latV, latLst);
        createVcdfVar(ncfile, lonV, lonLst);
        //createVcdfVar(ncfile, viewV, viewLst);
        createVcdfVar(ncfile, pixCountV, latLonLst);
        
        createVcdfVar(ncfile, aod550V[0], latLonLst);
        createVcdfVar(ncfile, aod550V[1], latLonLst);
        createVcdfVar(ncfile, aod659V[0], latLonLst);
        createVcdfVar(ncfile, aod659V[1], latLonLst);
        createVcdfVar(ncfile, aod865V[0], latLonLst);
        createVcdfVar(ncfile, aod865V[1], latLonLst);
        createVcdfVar(ncfile, aod1610V[0], latLonLst);
        createVcdfVar(ncfile, aod1610V[1], latLonLst);

        //createVcdfVar(ncfile, angstrom659V[i], dimList);
        createVcdfVar(ncfile, angstrom865V[0], latLonLst);
        createVcdfVar(ncfile, angstrom865V[1], latLonLst);

        createVcdfVar(ncfile, fineModeV[0], latLonLst);
        createVcdfVar(ncfile, fineModeV[1], latLonLst);
        createVcdfVar(ncfile, dustModeV[0], latLonLst);
        createVcdfVar(ncfile, dustModeV[1], latLonLst);
        createVcdfVar(ncfile, absModeV[0], latLonLst);
        createVcdfVar(ncfile, absModeV[1], latLonLst);
        createVcdfVar(ncfile, ssaV[0], latLonLst);
        createVcdfVar(ncfile, ssaV[1], latLonLst);

        if (doSurfRefl){
            createVcdfVar(ncfile, sreflec555V[0], latLonLst);
            createVcdfVar(ncfile, sreflec555V[1], latLonLst);
            createVcdfVar(ncfile, sreflec659V[0], latLonLst);
            createVcdfVar(ncfile, sreflec659V[1], latLonLst);
            createVcdfVar(ncfile, sreflec865V[0], latLonLst);
            createVcdfVar(ncfile, sreflec865V[1], latLonLst);
            createVcdfVar(ncfile, sreflec1610V[0], latLonLst);
            createVcdfVar(ncfile, sreflec1610V[1], latLonLst);
        }
        createVcdfVar(ncfile, sigAod550V[0], latLonLst);
        createVcdfVar(ncfile, sigAod550V[1], latLonLst);
        createVcdfVar(ncfile, sigAod550V[2], latLonLst);
        createVcdfVar(ncfile, sigAod550V[3], latLonLst);
        createVcdfVar(ncfile, sigAod550V[4], latLonLst);
        if(version.equals(DataVersionNumbers.v4_21u)){
            createVcdfVar(ncfile, sigAod550V[5], latLonLst);
            createVcdfVar(ncfile, sigAod550V[6], latLonLst);
            createVcdfVar(ncfile, sigAod550V[7], latLonLst);
        }
        createVcdfVar(ncfile, sigAod670V[0], latLonLst);
        createVcdfVar(ncfile, sigAod670V[1], latLonLst);
        createVcdfVar(ncfile, sigAod670V[2], latLonLst);
        createVcdfVar(ncfile, sigAod670V[3], latLonLst);
        createVcdfVar(ncfile, sigAod670V[4], latLonLst);
        createVcdfVar(ncfile, sigAod870V[0], latLonLst);
        createVcdfVar(ncfile, sigAod870V[1], latLonLst);
        createVcdfVar(ncfile, sigAod870V[2], latLonLst);
        createVcdfVar(ncfile, sigAod870V[3], latLonLst);
        createVcdfVar(ncfile, sigAod870V[4], latLonLst);
        createVcdfVar(ncfile, sigAod1600V[0], latLonLst);
        createVcdfVar(ncfile, sigAod1600V[1], latLonLst);
        createVcdfVar(ncfile, sigAod1600V[2], latLonLst);
        createVcdfVar(ncfile, sigAod1600V[3], latLonLst);
        createVcdfVar(ncfile, sigAod1600V[4], latLonLst);

        //createVcdfVar(ncfile, cldFracV[0], latLonLst);
        //createVcdfVar(ncfile, cldFracV[1], latLonLst);
        //createVcdfVar(ncfile, landFlagV[0], latLonLst);
        //createVcdfVar(ncfile, landFlagV[1], latLonLst);

        //createVcdfVar(ncfile, szaV[0], latLonViewLst);
        //createVcdfVar(ncfile, szaV[1], latLonViewLst);
        //createVcdfVar(ncfile, vzaV[0], latLonViewLst);
        //createVcdfVar(ncfile, vzaV[1], latLonViewLst);
        //createVcdfVar(ncfile, razV[0], latLonViewLst);
        //createVcdfVar(ncfile, razV[1], latLonViewLst);
        
        createGlobalAttrb(ncfile, prodInfo, ncdfName, version);
        ncfile.create();
        return ncfile;
    }
    
    private NetcdfFileWriter createNcdfFile_V4(String ncdfName, Map<String,String> prodInfo, DataVersionNumbers version) throws IOException {
        NetcdfFileWriter ncfile = NetcdfFileWriter.createNew(NetcdfFileWriter.Version.netcdf4, ncdfName);
        Dimension dimLat = ncfile.addDimension(null, "latitude", 180);
        Dimension dimLon = ncfile.addDimension(null, "longitude", 360);
        Dimension dimView = ncfile.addDimension(null, "instrument_view", 2);
        ArrayList<Dimension> latLst = new ArrayList<Dimension>();
        latLst.add(dimLat);
        ArrayList<Dimension> lonLst = new ArrayList<Dimension>();
        lonLst.add(dimLon);
        ArrayList<Dimension> viewLst = new ArrayList<Dimension>();
        viewLst.add(dimView);
        ArrayList<Dimension> latLonLst = new ArrayList<Dimension>();
        latLonLst.add(dimLat);
        latLonLst.add(dimLon);
        //ArrayList<Dimension> latLonViewLst = new ArrayList<>();
        //latLonViewLst.add(dimLat);
        //latLonViewLst.add(dimLon);
        //latLonViewLst.add(dimView);

        createVcdfVar(ncfile, latV, latLst);
        createVcdfVar(ncfile, lonV, lonLst);
        //createVcdfVar(ncfile, viewV, viewLst);
        createVcdfVar(ncfile, pixCountV, latLonLst);
        
        createVcdfVar(ncfile, aod550V[0], latLonLst);
        createVcdfVar(ncfile, aod550V[1], latLonLst);
        createVcdfVar(ncfile, aod659V[0], latLonLst);
        createVcdfVar(ncfile, aod659V[1], latLonLst);
        createVcdfVar(ncfile, aod865V[0], latLonLst);
        createVcdfVar(ncfile, aod865V[1], latLonLst);
        createVcdfVar(ncfile, aod1610V[0], latLonLst);
        createVcdfVar(ncfile, aod1610V[1], latLonLst);

        //createVcdfVar(ncfile, angstrom659V[i], dimList);
        createVcdfVar(ncfile, angstrom865V[0], latLonLst);
        createVcdfVar(ncfile, angstrom865V[1], latLonLst);

        createVcdfVar(ncfile, fineModeV[0], latLonLst);
        createVcdfVar(ncfile, fineModeV[1], latLonLst);
        createVcdfVar(ncfile, dustModeV[0], latLonLst);
        createVcdfVar(ncfile, dustModeV[1], latLonLst);
        createVcdfVar(ncfile, absModeV[0], latLonLst);
        createVcdfVar(ncfile, absModeV[1], latLonLst);
        createVcdfVar(ncfile, ssaV[0], latLonLst);
        createVcdfVar(ncfile, ssaV[1], latLonLst);

        if (doSurfRefl){
            createVcdfVar(ncfile, sreflec555V[0], latLonLst);
            createVcdfVar(ncfile, sreflec555V[1], latLonLst);
            createVcdfVar(ncfile, sreflec659V[0], latLonLst);
            createVcdfVar(ncfile, sreflec659V[1], latLonLst);
            createVcdfVar(ncfile, sreflec865V[0], latLonLst);
            createVcdfVar(ncfile, sreflec865V[1], latLonLst);
            createVcdfVar(ncfile, sreflec1610V[0], latLonLst);
            createVcdfVar(ncfile, sreflec1610V[1], latLonLst);
        }
        createVcdfVar(ncfile, sigAod550V[0], latLonLst);
        createVcdfVar(ncfile, sigAod550V[1], latLonLst);
        createVcdfVar(ncfile, sigAod550V[2], latLonLst);
        createVcdfVar(ncfile, sigAod550V[3], latLonLst);
        createVcdfVar(ncfile, sigAod550V[4], latLonLst);
        if(version.equals(DataVersionNumbers.v4_21u)){
            createVcdfVar(ncfile, sigAod550V[5], latLonLst);
            createVcdfVar(ncfile, sigAod550V[6], latLonLst);
            createVcdfVar(ncfile, sigAod550V[7], latLonLst);
        }
        createVcdfVar(ncfile, sigAod670V[0], latLonLst);
        createVcdfVar(ncfile, sigAod670V[1], latLonLst);
        createVcdfVar(ncfile, sigAod670V[2], latLonLst);
        createVcdfVar(ncfile, sigAod670V[3], latLonLst);
        createVcdfVar(ncfile, sigAod670V[4], latLonLst);
        createVcdfVar(ncfile, sigAod870V[0], latLonLst);
        createVcdfVar(ncfile, sigAod870V[1], latLonLst);
        createVcdfVar(ncfile, sigAod870V[2], latLonLst);
        createVcdfVar(ncfile, sigAod870V[3], latLonLst);
        createVcdfVar(ncfile, sigAod870V[4], latLonLst);
        createVcdfVar(ncfile, sigAod1600V[0], latLonLst);
        createVcdfVar(ncfile, sigAod1600V[1], latLonLst);
        createVcdfVar(ncfile, sigAod1600V[2], latLonLst);
        createVcdfVar(ncfile, sigAod1600V[3], latLonLst);
        createVcdfVar(ncfile, sigAod1600V[4], latLonLst);

        createVcdfVar(ncfile, cldFracV[0], latLonLst);
        createVcdfVar(ncfile, cldFracV[1], latLonLst);
        createVcdfVar(ncfile, landFlagV[0], latLonLst);
        createVcdfVar(ncfile, landFlagV[1], latLonLst);

        //createVcdfVar(ncfile, szaV[0], latLonViewLst);
        //createVcdfVar(ncfile, szaV[1], latLonViewLst);
        //createVcdfVar(ncfile, vzaV[0], latLonViewLst);
        //createVcdfVar(ncfile, vzaV[1], latLonViewLst);
        //createVcdfVar(ncfile, razV[0], latLonViewLst);
        //createVcdfVar(ncfile, razV[1], latLonViewLst);
        
        createGlobalAttrb(ncfile, prodInfo, ncdfName, version);
        ncfile.create();
        return ncfile;
    }
    
    private NetcdfFileWriteable createNcdfFile_V10(String ncdfName, Map<String,String> prodInfo, DataVersionNumbers version) throws IOException {
        
        NetcdfFileWriteable ncfile = NetcdfFileWriteable.createNew(ncdfName, true);
        Dimension dimLon = ncfile.addDimension("lon", 360);
        Dimension dimLat = ncfile.addDimension("lat", 180);
        Dimension dimView = ncfile.addDimension("instrument_view", 2);
        ArrayList<Dimension> dimList = new ArrayList<Dimension>();
        dimList.add(dimLat);
        createVcdfVar(ncfile, latV, dimList);
        dimList = new ArrayList<Dimension>();
        dimList.add(dimLon);
        createVcdfVar(ncfile, lonV, dimList);
        
        dimList = new ArrayList<Dimension>();
        dimList.add(dimLat);
        dimList.add(dimLon);
        createVcdfVar(ncfile, pixCountV, dimList);

        for (int i=0; i<2; i++){
            dimList = new ArrayList<Dimension>();
            dimList.add(dimLat);
            dimList.add(dimLon);
            createVcdfVar(ncfile, aod550V[i], dimList);

            dimList.add(dimView);
            createVcdfVar(ncfile, szaV[i], dimList);
            createVcdfVar(ncfile, vzaV[i], dimList);
            createVcdfVar(ncfile, razV[i], dimList);
        }
        createGlobalAttrb(ncfile, prodInfo, ncdfName, version);
        ncfile.create();
        return ncfile;
    }
    
    private static void createGlobalAttrb(NetcdfFileWriteable ncfile, Map<String, String> prodInfo, String ncdfName, DataVersionNumbers version) {
        final String StartDate = prodInfo.get("StartDate");
        final String StopDate = prodInfo.get("StopDate");
        final String CurrentTime = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss", Locale.ENGLISH).format(new Date());
        final String prodName = prodInfo.get("Products");        
        final boolean atsr2 = prodName.startsWith("AT2");
        final String idStr = new File(ncdfName).getName();
        
        ncfile.addGlobalAttribute("Conventions", "CF-1.4");
        if (atsr2) {
            ncfile.addGlobalAttribute("platform", "ERS2");
            ncfile.addGlobalAttribute("sensor", "ATSR2");
        }
        else {
            ncfile.addGlobalAttribute("platform", "ENVISAT");
            ncfile.addGlobalAttribute("sensor", "AATSR");
        }
        ncfile.addGlobalAttribute("startdate", StartDate);
        ncfile.addGlobalAttribute("stopdate", StopDate);
        ncfile.addGlobalAttribute("datetime", CurrentTime);
        ncfile.addGlobalAttribute("product", prodName);
        ncfile.addGlobalAttribute("title", "ESA CCI aerosol product level 3");
        ncfile.addGlobalAttribute("source", "ATS_TOA_1P, V6.03");
        ncfile.addGlobalAttribute("version", version.toString());
        ncfile.addGlobalAttribute("references", "http://www.esa-aerosol-cci.org");
        ncfile.addGlobalAttribute("originator", "SU");
        ncfile.addGlobalAttribute("originator_long", "Swansea University");
        ncfile.addGlobalAttribute("originator_url", "http://www.swan.ac.uk/staff/academic/environmentsociety/geography/northpeter/");
        ncfile.addGlobalAttribute("email", "p.r.j.north@swansea.ac.uk; a.heckel@swansea.ac.uk");
        ncfile.addGlobalAttribute("Projection", "equal angle (lat / lon)");
        ncfile.addGlobalAttribute("Resolution", Array.factory(new float[]{1.0f, 1.0f}));
        ncfile.addGlobalAttribute("Units", "degree");
        ncfile.addGlobalAttribute("node_offset", 1);
		ncfile.addGlobalAttribute("institution", "Swansea University");
		ncfile.addGlobalAttribute("history", "Level 3 product from Swansea algorithm");
		//ncfile.addGlobalAttribute("tracking_id", "5907363c-b03d-11e3-b9db-00163ef7a124");
		ncfile.addGlobalAttribute("conventions", "CF-1.4");
		ncfile.addGlobalAttribute("summary", "This dataset contains the level-3 aerosol optical depths from AATSR observations. Data are processed by Swansea algorithm");
		ncfile.addGlobalAttribute("keywords", "satellite,observation,aerosol");
		ncfile.addGlobalAttribute("id", idStr);
		ncfile.addGlobalAttribute("naming_authority", "swan.ac.uk");
		ncfile.addGlobalAttribute("keywords_vocabulary", "NASA Global Change Master Directory (GCMD) Science Keywords");
		ncfile.addGlobalAttribute("cdm_data_type", "grid");
		ncfile.addGlobalAttribute("comment", "These data were produced at ESA CCI as part of the ESA Aerosol CCI project.");
		//ncfile.addGlobalAttribute("date_created", "20120918T163335Z");
		ncfile.addGlobalAttribute("creator_name", "Swansea University");
		ncfile.addGlobalAttribute("creator_url", "http://www.swan.ac.uk/staff/academic/environmentsociety/geography/northpeter/");
		ncfile.addGlobalAttribute("creator_email", "p.r.j.north@swansea.ac.uk, a.heckel@swansea.ac.uk");
		ncfile.addGlobalAttribute("project", "Climate Change Initiative - European Space Agency");
		ncfile.addGlobalAttribute("geospatial_lat_min", "-90.0");
		ncfile.addGlobalAttribute("geospatial_lat_max", "90.0");
		ncfile.addGlobalAttribute("geospatial_lon_min", "-180.0");
		ncfile.addGlobalAttribute("geospatial_lon_max", "180.0");
		ncfile.addGlobalAttribute("geospatial_vertical_min", "0 km");
		ncfile.addGlobalAttribute("geospatial_vertical_max", "0 km");
		ncfile.addGlobalAttribute("time_coverage_duration", "P1D");
		ncfile.addGlobalAttribute("time_coverage_resolution", "P1D");
		ncfile.addGlobalAttribute("standard_name_vocabulary", "NetCDF Climate and Forecast (CF) Metadata Convention version 18");
		ncfile.addGlobalAttribute("license", "ESA CCI Data Policy: free and open access");
		ncfile.addGlobalAttribute("geospatial_lat_units", "degrees_north");
		ncfile.addGlobalAttribute("geospatial_lon_units", "degrees_east");         
    }

    private static void createGlobalAttrb(NetcdfFileWriter ncfile, Map<String, String> prodInfo, String ncdfName, S3DataVersionNumbers version) {
        final String StartDate = prodInfo.get("StartDate");
        final String StopDate = prodInfo.get("StopDate");
        final SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.ENGLISH);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        final String CurrentTime = df.format(new Date());

        final String prodList = prodInfo.get("ProductList");        
        final String idStr = new File(ncdfName).getName();
        
        ncfile.addGroupAttribute(null, new Attribute("Conventions", "CF-1.6"));
        ncfile.addGroupAttribute(null, new Attribute("tracking_id", UUID.randomUUID().toString()));
        ncfile.addGroupAttribute(null, new Attribute("naming_authority", "uk.ac.su.aatsraerosol"));
        ncfile.addGroupAttribute(null, new Attribute("title", "AARDVARC C3S aerosol product level 3"));
        ncfile.addGroupAttribute(null, new Attribute("product_version", version.toString()));
		ncfile.addGroupAttribute(null, new Attribute("summary", "This dataset contains the level-3 daily mean aerosol properties products from SLSTR satellite observations. Data are processed by Swansea algorithm"));
		ncfile.addGroupAttribute(null, new Attribute("id", idStr));
        ncfile.addGroupAttribute(null, new Attribute("sensor", "SLSTR"));
        ncfile.addGroupAttribute(null, new Attribute("platform", "SENTINEL-S3A"));
        ncfile.addGroupAttribute(null, new Attribute("resolution", "1x1 degrees"));
        ncfile.addGroupAttribute(null, new Attribute("projection", "equirectangular"));
		ncfile.addGroupAttribute(null, new Attribute("cdm_data_type", "grid"));
		ncfile.addGroupAttribute(null, new Attribute("lon", 360));
		ncfile.addGroupAttribute(null, new Attribute("lat", 180));
		ncfile.addGroupAttribute(null, new Attribute("time", "1"));
		ncfile.addGroupAttribute(null, new Attribute("time_coverage_start", StartDate));
		ncfile.addGroupAttribute(null, new Attribute("time_coverage_end", StopDate));
		ncfile.addGroupAttribute(null, new Attribute("geospatial_lat_min", "-90.0"));
		ncfile.addGroupAttribute(null, new Attribute("geospatial_lat_max", "90.0"));
		ncfile.addGroupAttribute(null, new Attribute("geospatial_lon_min", "-180.0"));
		ncfile.addGroupAttribute(null, new Attribute("geospatial_lon_max", "180.0"));
		ncfile.addGroupAttribute(null, new Attribute("geospatial_lat_units", "degrees_north"));
		ncfile.addGroupAttribute(null, new Attribute("geospatial_lon_units", "degrees_east"));         
		ncfile.addGroupAttribute(null, new Attribute("geospatial_lat_resolution", "1.0"));
		ncfile.addGroupAttribute(null, new Attribute("geospatial_lon_resolution", "1.0"));         
        ncfile.addGroupAttribute(null, new Attribute("date_created", CurrentTime));
		ncfile.addGroupAttribute(null, new Attribute("project", "Climate Change Initiative - European Space Agency"));
        ncfile.addGroupAttribute(null, new Attribute("references", "http://www.esa-aerosol-cci.org"));
		//ncfile.addGroupAttribute(null, new Attribute("institution", "Swansea University"));
		ncfile.addGroupAttribute(null, new Attribute("creator_name", "Swansea University"));
		ncfile.addGroupAttribute(null, new Attribute("creator_url", "http://www.swan.ac.uk/staff/academic/environmentsociety/geography/northpeter/"));
		ncfile.addGroupAttribute(null, new Attribute("creator_email", "p.r.j.north@swansea.ac.uk, a.heckel@swansea.ac.uk"));
        ncfile.addGroupAttribute(null, new Attribute("source", prodInfo.get("Source")));
		ncfile.addGroupAttribute(null, new Attribute("history", "Level 3 product from Swansea algorithm"));
		ncfile.addGroupAttribute(null, new Attribute("keywords", "satellite,observation,atmosphere"));
		ncfile.addGroupAttribute(null, new Attribute("keywords_vocabulary", "NASA Global Change Master Directory (GCMD) Science Keywords"));
		ncfile.addGroupAttribute(null, new Attribute("standard_name_vocabulary", "NetCDF Climate and Forecast (CF) Metadata Convention version 18"));
		ncfile.addGroupAttribute(null, new Attribute("license", "ESA CCI Data Policy: free and open access"));
        ncfile.addGroupAttribute(null, new Attribute("inputfilelist", prodList));
        //ncfile.addGroupAttribute(null, new Attribute("Units", "degree"));
        //ncfile.addGroupAttribute(null, new Attribute("node_offset", 1));
    }

    private static void createGlobalAttrb(NetcdfFileWriter ncfile, Map<String, String> prodInfo, String ncdfName, DataVersionNumbers version) {
        final String StartDate = prodInfo.get("StartDate");
        final String StopDate = prodInfo.get("StopDate");
        final SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.ENGLISH);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        final String CurrentTime = df.format(new Date());

        final String prodList = prodInfo.get("ProductList");        
        final boolean atsr2 = prodList.startsWith("AT2");
        final String idStr = new File(ncdfName).getName();
        
        ncfile.addGroupAttribute(null, new Attribute("Conventions", "CF-1.6"));
        ncfile.addGroupAttribute(null, new Attribute("tracking_id", UUID.randomUUID().toString()));
        ncfile.addGroupAttribute(null, new Attribute("naming_authority", "uk.ac.su.aatsraerosol"));
        ncfile.addGroupAttribute(null, new Attribute("title", "AARDVARC CCI aerosol product level 3"));
        ncfile.addGroupAttribute(null, new Attribute("product_version", version.toString()));
		ncfile.addGroupAttribute(null, new Attribute("summary", "This dataset contains the level-3 daily mean aerosol properties products from AATSR satellite observations. Data are processed by Swansea algorithm"));
		ncfile.addGroupAttribute(null, new Attribute("id", idStr));
        if (atsr2) {
            ncfile.addGroupAttribute(null, new Attribute("sensor", "ATSR2"));
            ncfile.addGroupAttribute(null, new Attribute("platform", "ERS2"));
        }
        else if (doSyn){
            String[] s = new String[]{"MERIS","AATSR"};
            Array a = Array.makeArray(DataType.STRING, s);
            ncfile.addGroupAttribute(null, new Attribute("sensor", a));
            ncfile.addGroupAttribute(null, new Attribute("platform", "ENVISAT"));
        }
        else {
            ncfile.addGroupAttribute(null, new Attribute("sensor", "AATSR"));
            ncfile.addGroupAttribute(null, new Attribute("platform", "ENVISAT"));
        }
        ncfile.addGroupAttribute(null, new Attribute("resolution", "1x1 degrees"));
        ncfile.addGroupAttribute(null, new Attribute("projection", "equirectangular"));
		ncfile.addGroupAttribute(null, new Attribute("cdm_data_type", "grid"));
		ncfile.addGroupAttribute(null, new Attribute("lon", 360));
		ncfile.addGroupAttribute(null, new Attribute("lat", 180));
		ncfile.addGroupAttribute(null, new Attribute("time", "1"));
		ncfile.addGroupAttribute(null, new Attribute("time_coverage_start", StartDate));
		ncfile.addGroupAttribute(null, new Attribute("time_coverage_end", StopDate));
		ncfile.addGroupAttribute(null, new Attribute("geospatial_lat_min", "-90.0"));
		ncfile.addGroupAttribute(null, new Attribute("geospatial_lat_max", "90.0"));
		ncfile.addGroupAttribute(null, new Attribute("geospatial_lon_min", "-180.0"));
		ncfile.addGroupAttribute(null, new Attribute("geospatial_lon_max", "180.0"));
		ncfile.addGroupAttribute(null, new Attribute("geospatial_lat_units", "degrees_north"));
		ncfile.addGroupAttribute(null, new Attribute("geospatial_lon_units", "degrees_east"));         
		ncfile.addGroupAttribute(null, new Attribute("geospatial_lat_resolution", "1.0"));
		ncfile.addGroupAttribute(null, new Attribute("geospatial_lon_resolution", "1.0"));         
        ncfile.addGroupAttribute(null, new Attribute("date_created", CurrentTime));
		ncfile.addGroupAttribute(null, new Attribute("project", "Climate Change Initiative - European Space Agency"));
        ncfile.addGroupAttribute(null, new Attribute("references", "http://www.esa-aerosol-cci.org"));
		//ncfile.addGroupAttribute(null, new Attribute("institution", "Swansea University"));
		ncfile.addGroupAttribute(null, new Attribute("creator_name", "Swansea University"));
		ncfile.addGroupAttribute(null, new Attribute("creator_url", "http://www.swan.ac.uk/staff/academic/environmentsociety/geography/northpeter/"));
		ncfile.addGroupAttribute(null, new Attribute("creator_email", "p.r.j.north@swansea.ac.uk, a.heckel@swansea.ac.uk"));
        ncfile.addGroupAttribute(null, new Attribute("source", "ATS_TOA_1P, V6.05"));
		ncfile.addGroupAttribute(null, new Attribute("history", "Level 3 product from Swansea algorithm"));
		ncfile.addGroupAttribute(null, new Attribute("keywords", "satellite,observation,atmosphere"));
		ncfile.addGroupAttribute(null, new Attribute("keywords_vocabulary", "NASA Global Change Master Directory (GCMD) Science Keywords"));
		ncfile.addGroupAttribute(null, new Attribute("standard_name_vocabulary", "NetCDF Climate and Forecast (CF) Metadata Convention version 18"));
		ncfile.addGroupAttribute(null, new Attribute("license", "ESA CCI Data Policy: free and open access"));
        ncfile.addGroupAttribute(null, new Attribute("inputfilelist", prodList));
        //ncfile.addGroupAttribute(null, new Attribute("Units", "degree"));
        //ncfile.addGroupAttribute(null, new Attribute("node_offset", 1));
    }

    private static void createVcdfVar(NetcdfFileWriteable ncfile, final String vName, final String longName, ArrayList<Dimension> dimList) {
        String unitStr = "1";
        if (vName.startsWith("lat"))  unitStr = "degrees_north";
        if (vName.startsWith("lon")) unitStr = "degrees_east";
        if (vName.equals("pixel_count")) {
            ncfile.addVariable(vName, DataType.INT, dimList);
        } else {
            ncfile.addVariable(vName, DataType.FLOAT, dimList);
        }
        ncfile.addVariableAttribute(vName, "long_name", longName);
        ncfile.addVariableAttribute(vName, "_FillValue", -999f);
        ncfile.addVariableAttribute(vName, "units", unitStr);
        float[] valRange = new float[]{0f, 999999f};
        if (vName.startsWith("lat")) valRange = new float[]{-90f, 90f};
        if (vName.startsWith("lon")) valRange = new float[]{-180f, 180f};
        if (vName.startsWith("AOD")) valRange = new float[]{0f, 2f};
        Array valRangeArr = ArrayFloat.factory(valRange);
        ncfile.addVariableAttribute(vName, "actual_range", valRangeArr);
        ncfile.addVariableAttribute(vName, "valid_range", valRangeArr);
        ncfile.addVariableAttribute(vName, "scale_factor", 1.0f);
        ncfile.addVariableAttribute(vName, "add_offset", 0.0f);
    }

    private void createVcdfVar(NetcdfFileWriter ncfile, NetcdfVariableProperties var, ArrayList<Dimension> dimList) {
        Attribute longNameAtt = null;
        Attribute stdNameAtt = null;
        Attribute unitsAtt = null;
        Attribute validRangeAtt = null;
        Attribute fillValueAtt = null;
        Attribute axisAtt = null;
        Attribute coordsAtt = null;
        Attribute flagValuesAtt = null;
        Attribute flagMeanAtt = null;
        
        if (var.longName != null && var.longName.length() > 0){
            longNameAtt = new Attribute("long_name", var.longName);
        }
        if (var.stdName != null && var.stdName.length() > 0){
            stdNameAtt = new Attribute("standard_name", var.stdName);
        }
        if (var.units != null && var.units.length() > 0){
            unitsAtt = new Attribute("units", var.units);
        }
        if (var.validRange != null){
            validRangeAtt = new Attribute("valid_range", var.validRange);
        }
        if (var.fillValue != null){
            fillValueAtt = new Attribute("_FillValue", var.fillValue);
        }
        if (var.axis != null){
            axisAtt = new Attribute("axis", var.axis);
        }
        if (var.coords != null){
            coordsAtt = new Attribute("coordinates", var.coords);
        }
        if (var.flagMeanings != null && var.flagValues != null){
            flagMeanAtt = new Attribute("flag_meanings", var.flagMeanings);
            flagValuesAtt = new Attribute("flag_values", var.flagValues);
        }

        var.ncV = ncfile.addVariable(null, var.varName, var.dataType, dimList);
        
        addVarAtt(var.ncV, longNameAtt);
        addVarAtt(var.ncV, stdNameAtt);
        addVarAtt(var.ncV, unitsAtt);
        addVarAtt(var.ncV, validRangeAtt);
        addVarAtt(var.ncV, flagValuesAtt);
        addVarAtt(var.ncV, flagMeanAtt);
        addVarAtt(var.ncV, coordsAtt);
        addVarAtt(var.ncV, fillValueAtt);
        addVarAtt(var.ncV, axisAtt);
        
    }

    private void createVcdfVar(NetcdfFileWriteable ncfile, NetcdfVariableProperties var, ArrayList<Dimension> dimList) {
        if (var.varName.equals("pixel_count")) {
            ncfile.addVariable(var.varName, DataType.INT, dimList);
            ncfile.addVariableAttribute(var.varName, "long_name", var.longName);
            ncfile.addVariableAttribute(var.varName, "standard_name", var.stdName);
            ncfile.addVariableAttribute(var.varName, "_FillValue", (Integer)var.fillValue);
            //a = Array.factory(new float[]{(Integer) var.lowerRangeLimit, (Integer) var.upperRangeLimit});
        } 
        else if (var.varName.equals("pixel_count")) {
            
        }
        else {
            ncfile.addVariable(var.varName, DataType.FLOAT, dimList);
            ncfile.addVariableAttribute(var.varName, "long_name", var.longName);
            ncfile.addVariableAttribute(var.varName, "standard_name", var.stdName);
            ncfile.addVariableAttribute(var.varName, "_FillValue", (Float)var.fillValue);
            //a = Array.factory(new float[]{(Float) var.lowerRangeLimit, (Float) var.upperRangeLimit});
        }
        ncfile.addVariableAttribute(var.varName, "units", var.units);
        ncfile.addVariableAttribute(var.varName, "valid_range", var.validRange);
        ncfile.addVariableAttribute(var.varName, "actual_range", var.validRange);
        ncfile.addVariableAttribute(var.varName, "scale_factor", 1.0f);
        ncfile.addVariableAttribute(var.varName, "add_offset", 0.0f);
        
    }

    private float getMean(float[][][] grd, int ilat, int ilon) {
        return grd[0][ilat][ilon];
    }

    private float getSdev(float[][][] grd, int[][] countGrd, int ilat, int ilon) {
        double variance = (countGrd[ilat][ilon] > 1) ? grd[1][ilat][ilon] / (countGrd[ilat][ilon] - 1) : grd[1][ilat][ilon] / (countGrd[ilat][ilon]);
        return (float) Math.sqrt(variance);
    }

    private float getRms(float[][][] grd, int[][] countGrd, int ilat, int ilon) {
        float rms = -999;
        if (countGrd[ilat][ilon] > 0){
            rms = (float) Math.sqrt(grd[4][ilat][ilon] / countGrd[ilat][ilon]);
        }        
        return rms;
    }

    private float getMin(float[][][] grd, int ilat, int ilon) {
        return grd[2][ilat][ilon];
    }

    private float getMax(float[][][] grd, int ilat, int ilon) {
        return grd[3][ilat][ilon];
    }

    private float getMean(float[][][][] grd, int ilat, int ilon, int iview) {
        return grd[0][ilat][ilon][iview];
    }

    private float getSdev(float[][][][] grd, int[][] countGrd, int ilat, int ilon, int iview) {
        double variance = (countGrd[ilat][ilon] > 1) ? grd[1][ilat][ilon][iview] / (countGrd[ilat][ilon] - 1) : grd[1][ilat][ilon][iview] / (countGrd[ilat][ilon]);
        return (float) Math.sqrt(variance);
    }

    private float getMeanOrSdev(int mode, float[][][][] grd, int[][] countGrd, int ilat, int ilon, int iview) {
        float value = -999;
        switch (mode){
            case 0: value = getMean(grd, ilat, ilon, iview); break;
            case 1: value = getSdev(grd, countGrd, ilat, ilon, iview); break;
            default: break;
        }
        return value;
    }

    private float getMeanOrSdev(int mode, float[][][] grd, int[][] countGrd, int ilat, int ilon) {
        float value = -999;
        switch (mode){
            case 0: value = getMean(grd, ilat, ilon); break;
            case 1: value = getSdev(grd, countGrd, ilat, ilon); break;
            case 3: value = getMin(grd, ilat, ilon); break;
            case 4: value = getMax(grd, ilat, ilon); break;
            case 5: value = getRms(grd, countGrd, ilat, ilon); break;
            default: break;
        }
        return value;
    }

    private float propagateUnc(float[][][] aodGrd, float[][][] sigGrd, int[][] countGrd, int ilat, int ilon) {
        float meanUnc = getMean(sigGrd, ilat, ilon);
        float sdevAod = getSdev(aodGrd, countGrd, ilat, ilon);
        float unc = (float) Math.sqrt(Math.pow(meanUnc, 2) + Math.pow(sdevAod, 2));
        return unc;
    }

    private float[] getMoreUnc(float[][][] aodGrd, float[][][] sigGrd, int[][] countGrd, int ilat, int ilon) {
        float meanUnc = getMean(sigGrd, ilat, ilon);
        float rmsUnc = getRms(sigGrd, countGrd, ilat, ilon);
        float sdevAod = getSdev(aodGrd, countGrd, ilat, ilon);
        float[] addUnc = new float[]{rmsUnc, (rmsUnc + sdevAod), 2*meanUnc};       
        return addUnc;
    }

    private void addVarAtt(Variable v, Attribute a) {
        if (a != null) v.addAttribute(a);
    }

}
