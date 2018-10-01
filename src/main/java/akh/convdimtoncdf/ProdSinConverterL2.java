/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package akh.convdimtoncdf;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.ProductData.UTC;
import org.esa.beam.framework.datamodel.TiePointGrid;
import org.esa.beam.util.Guardian;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.NetcdfFileWriter;
import ucar.nc2.Variable;

/**
 *
 * @author akheckel
 */
class ProdSinConverterL2 extends BasicConverter {
    public static ProdSinConverterL2 instance = null;
    private SinProduct sinP;
    private boolean doSyn;
    private boolean doSurfRefl;
    int aodVersionId = 1;
    Map<String, String> s3ProdInfo = new HashMap<String, String>();
    
    private final NetcdfVariableProperties pixV = 
            new NetcdfVariableProperties("pixel_number", "Sinusoidal pixel index", "", "1", DataType.INT, 0, 0, null);
    //private final NetcdfVariableProperties viewV = 
    //        new NetcdfVariableProperties("instrument_view", "Instrument view", "", "1", DataType.INT, 0, 1, null);
    
    private final NetcdfVariableProperties latV = 
            new NetcdfVariableProperties("latitude", "Latitude at pixel centre", "latitude", "degrees_north", DataType.FLOAT, -90f, 90f, -999f, null);
    private final NetcdfVariableProperties lonV = 
            new NetcdfVariableProperties("longitude", "Longitude at pixel centre", "longitude", "degrees_east", DataType.FLOAT, -180f, 180f, -999f, null);
    
    private final NetcdfVariableProperties[] pixCornerLatV = new NetcdfVariableProperties[]{
        new NetcdfVariableProperties("pixel_corner_latitude1", "latitude_1st_corner", "latitude", "degrees_north", DataType.FLOAT, -90f, 90f, -999f, null),
        new NetcdfVariableProperties("pixel_corner_latitude2", "latitude_2nd_corner", "latitude", "degrees_north", DataType.FLOAT, -90f, 90f, -999f, null),
        new NetcdfVariableProperties("pixel_corner_latitude3", "latitude_3rd_corner", "latitude", "degrees_north", DataType.FLOAT, -90f, 90f, -999f, null),
        new NetcdfVariableProperties("pixel_corner_latitude4", "latitude_4th_corner", "latitude", "degrees_north", DataType.FLOAT, -90f, 90f, -999f, null)
    };
    
    private final NetcdfVariableProperties[] pixCornerLonV = new NetcdfVariableProperties[]{
        new NetcdfVariableProperties("pixel_corner_longitude1", "longitude_1st_corner", "longitude", "degrees_east", DataType.FLOAT, -180f, 180f, -999f, null),
        new NetcdfVariableProperties("pixel_corner_longitude2", "longitude_2nd_corner", "longitude", "degrees_east", DataType.FLOAT, -180f, 180f, -999f, null),
        new NetcdfVariableProperties("pixel_corner_longitude3", "longitude_3rd_corner", "longitude", "degrees_east", DataType.FLOAT, -180f, 180f, -999f, null),
        new NetcdfVariableProperties("pixel_corner_longitude4", "longitude_4th_corner", "longitude", "degrees_east", DataType.FLOAT, -180f, 180f, -999f, null)
    };
    
    private final NetcdfVariableProperties aod550V = 
            new NetcdfVariableProperties("AOD550", "aerosol optical thickness at 550 nm", "atmosphere_optical_thickness_due_to_ambient_aerosol", "1", DataType.FLOAT, 0f, 4f, -999f, "latitude longitude time");
    private final NetcdfVariableProperties aod670V = 
            new NetcdfVariableProperties("AOD670", "aerosol optical thickness at 670 nm", "atmosphere_optical_thickness_due_to_ambient_aerosol", "1", DataType.FLOAT, 0f, 4f, -999f, "latitude longitude time");
    private final NetcdfVariableProperties aod870V = 
            new NetcdfVariableProperties("AOD870", "aerosol optical thickness at 870 nm", "atmosphere_optical_thickness_due_to_ambient_aerosol", "1", DataType.FLOAT, 0f, 4f, -999f, "latitude longitude time");
    private final NetcdfVariableProperties aod1600V = 
            new NetcdfVariableProperties("AOD1600", "aerosol optical thickness at 1600 nm", "atmosphere_optical_thickness_due_to_ambient_aerosol", "1", DataType.FLOAT, 0f, 4f, -999f, "latitude longitude time");

    private final NetcdfVariableProperties sigAod550V = 
            new NetcdfVariableProperties("AOD550_uncertainty", "Uncertainty on AOT at 550 nm", "", "1", DataType.FLOAT, 0f, 10f, -999f, "latitude longitude time");
    private final NetcdfVariableProperties sigAod670V = 
            new NetcdfVariableProperties("AOD670_uncertainty", "Uncertainty on AOT at 670 nm", "", "1", DataType.FLOAT, 0f, 10f, -999f, "latitude longitude time");
    private final NetcdfVariableProperties sigAod870V = 
            new NetcdfVariableProperties("AOD870_uncertainty", "Uncertainty on AOT at 870 nm", "", "1", DataType.FLOAT, 0f, 10f, -999f, "latitude longitude time");
    private final NetcdfVariableProperties sigAod1600V = 
            new NetcdfVariableProperties("AOD1600_uncertainty", "Uncertainty on AOT at 1600 nm", "", "1", DataType.FLOAT, 0f, 10f, -999f, "latitude longitude time");

    private final NetcdfVariableProperties angstromV = 
            new NetcdfVariableProperties("ANG550_870", "aerosol Angstrom exponent between 550 and 870 nm", "angstrom_exponent_of_ambient_aerosol_in_air", "1", DataType.FLOAT, -5f, 5f, -999f, "latitude longitude time");

    private final NetcdfVariableProperties fmAodV = 
            new NetcdfVariableProperties("FM_AOD550", "fine-mode aerosol optical thickness at 550nm", "atmosphere_optical_thickness_due_to_ambient_aerosol", "1", DataType.FLOAT, 0f, 2f, -999f, "latitude longitude time");
    private final NetcdfVariableProperties dustAodV = 
            new NetcdfVariableProperties("D_AOD550", "dust aerosol optical thickness at 550nm", "atmosphere_optical_thickness_due_to_ambient_aerosol", "1", DataType.FLOAT, 0f, 2f, -999f, "latitude longitude time");
    private final NetcdfVariableProperties absAodV = 
            new NetcdfVariableProperties("AAOD550", "aerosol absorption optical thickness at 550nm", "atmosphere_optical_thickness_due_to_ambient_aerosol", "1", DataType.FLOAT, 0f, 2f, -999f, "latitude longitude time");
    private final NetcdfVariableProperties ssaV = 
            new NetcdfVariableProperties("SSA550", "single scattering albedo at 550nm", "", "1", DataType.FLOAT, 0f, 2f, -999f, "latitude longitude time");
    
    private final NetcdfVariableProperties sreflec550V = 
            new NetcdfVariableProperties("surface_reflectance550", "mean bidirectional surface reflectance (nadir)", "", "1", DataType.FLOAT, 0f, 1f, -999f, "latitude longitude time");
    private final NetcdfVariableProperties sreflec670V = 
            new NetcdfVariableProperties("surface_reflectance670", "mean bidirectional surface reflectance (nadir)", "", "1", DataType.FLOAT, 0f, 1f, -999f, "latitude longitude time");
    private final NetcdfVariableProperties sreflec870V = 
            new NetcdfVariableProperties("surface_reflectance870", "mean bidirectional surface reflectance (nadir)", "", "1", DataType.FLOAT, 0f, 1f, -999f, "latitude longitude time");
    private final NetcdfVariableProperties sreflec1600V = 
            new NetcdfVariableProperties("surface_reflectance1600", "mean bidirectional surface reflectance (nadir)", "", "1", DataType.FLOAT, 0f, 1f, -999f, "latitude longitude time");

    private final NetcdfVariableProperties cldFracV = 
            new NetcdfVariableProperties("cloud_fraction", "fraction of cloud flagged pixels in 10km bin", "cloud_area_fraction", "1", DataType.FLOAT, 0f, 1f, -999f, "latitude longitude time");
    private final NetcdfVariableProperties landFlagV = 
            new NetcdfVariableProperties("surface_type_number", "land / sea flag", "land_binary_mask", "1", DataType.INT, 0, 1, -999, "latitude longitude time");

    private final NetcdfVariableProperties szaV = 
            new NetcdfVariableProperties("sun_zenith_at_center", "solar zenith angle", "solar_zenith_angle", "degrees", DataType.FLOAT, 0f, 90f, -999f, "latitude longitude time");
    private final NetcdfVariableProperties vzaV = 
            new NetcdfVariableProperties("satellite_zenith_at_center", "satellite zenith angle", "zenith_angle", "degrees", DataType.FLOAT, 0f, 90f, -999f, "latitude longitude time");
    private final NetcdfVariableProperties razV = 
            new NetcdfVariableProperties("relative_azimuth_at_center", "relative azimuth angle", "", "degrees", DataType.FLOAT, 0f, 180f, -999f, "latitude longitude time");
    private final NetcdfVariableProperties timeV = 
            new NetcdfVariableProperties("time", "time seconds since 1970-01-01 00:00:00 UTC", "time", "seconds", DataType.INT, new Integer(1), Integer.MAX_VALUE, 0);

    
    final String[] sinBandNames = new String[]{
        timeV.varName,
        aod550V.varName,
        aod670V.varName,
        aod870V.varName,
        aod1600V.varName,

        angstromV.varName,
        fmAodV.varName,
        dustAodV.varName, 
        absAodV.varName,
        ssaV.varName,
        
        sreflec550V.varName,
        sreflec670V.varName,
        sreflec870V.varName,
        sreflec1600V.varName,
        
        sigAod550V.varName,
        sigAod670V.varName,
        sigAod870V.varName,
        sigAod1600V.varName,

        szaV.varName,
        vzaV.varName,
        razV.varName,
        
        cldFracV.varName,
        landFlagV.varName
    };

    private ProdSinConverterL2() throws IOException {
        modelInfo = readAerosolModelInfo("ccimodel.info");
        //viewV.flagValues = Array.factory(new int[]{0,1});
        //viewV.flagMeanings = "nadir forward";
        landFlagV.flagValues = Array.factory(new int[]{0,1});
        landFlagV.flagMeanings = "sea land";
        doSyn = false;
        doSurfRefl = true;
    }

    static synchronized ProdSinConverterL2 getInstance() {
        if (instance == null){
            try {
                instance = new ProdSinConverterL2();
            } catch (IOException ex) {
                Logger.getLogger(ProdSinConverterL2.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return instance;
    }

    @Override
    void convert(Product p, String ncdfName, DataVersionNumbers version) {
        String fname = p.getFileLocation().getPath();
        System.out.println("processing V" + version + " - " + fname);
        sinP = new SinProduct(sinBandNames, p);
        aodVersionId = (version.equals(DataVersionNumbers.v4_31)) ? 3 : 1;
        doSyn = version.isGE(DataVersionNumbers.vSyn1_0);
        doSurfRefl = p.containsBand(String.format("reflec_surf_nadir_0550_%d", aodVersionId));
        binProductToSin(p, sinP);
        sinP.convCellsToArray();
        writeNcdf(ncdfName, sinP, version);
        
    }

    void convertS3(String fname, String ncdfName, S3DataVersionNumbers version) throws IOException {
        System.out.println("processing V" + version + " - " + fname);
        doSyn = false;
        doSurfRefl = false;
        updateProdInfo(fname);
        binNcdfToSin(fname, sinP);
    }
    
    @Override
    NetcdfFileWriter createNetCdfFile(String ncdfName, Product p, DataVersionNumbers version) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    NetcdfFileWriter createNetCdfFile(String ncdfName, SinProduct sinP, DataVersionNumbers version) {
        NetcdfFileWriter ncfile4 = null;
        try {
            ncfile4 = NetcdfFileWriter.createNew(NetcdfFileWriter.Version.netcdf4, ncdfName);
            Dimension dimPix  = ncfile4.addDimension(null, "pixel_number", sinP.cellMap.size());
            //Dimension dimView = ncfile4.addDimension(null, "instrument_view", 2);

            ArrayList<Dimension> pixList = new ArrayList<Dimension>();
            pixList.add(dimPix);
            //ArrayList<Dimension> viewList = new ArrayList<Dimension>();
            //viewList.add(dimView);
            //ArrayList<Dimension> viewPixList = new ArrayList<Dimension>();
            //viewPixList.add(dimView);
            //viewPixList.add(dimPix);
            
            createVcdfVar(ncfile4, pixV, pixList);
            //createVcdfVar(ncfile4, viewV, viewList);
            createVcdfVar(ncfile4, latV, pixList);
            createVcdfVar(ncfile4, lonV, pixList);
            for (int i=0; i<4; i++){
                createVcdfVar(ncfile4, pixCornerLatV[i], pixList);
                createVcdfVar(ncfile4, pixCornerLonV[i], pixList);
            }
            
            createVcdfVar(ncfile4, aod550V, pixList);
            createVcdfVar(ncfile4, aod670V, pixList);
            createVcdfVar(ncfile4, aod870V, pixList);
            createVcdfVar(ncfile4, aod1600V, pixList);
            
            createVcdfVar(ncfile4, angstromV, pixList);
            createVcdfVar(ncfile4, fmAodV, pixList);
            createVcdfVar(ncfile4, dustAodV, pixList);
            createVcdfVar(ncfile4, absAodV, pixList);
            createVcdfVar(ncfile4, ssaV, pixList);
            
            if (doSurfRefl){
                createVcdfVar(ncfile4, sreflec550V, pixList);
                createVcdfVar(ncfile4, sreflec670V, pixList);
                createVcdfVar(ncfile4, sreflec870V, pixList);
                createVcdfVar(ncfile4, sreflec1600V, pixList);
            }
            createVcdfVar(ncfile4, sigAod550V, pixList);
            createVcdfVar(ncfile4, sigAod670V, pixList);
            createVcdfVar(ncfile4, sigAod870V, pixList);
            createVcdfVar(ncfile4, sigAod1600V, pixList);
            
            createVcdfVar(ncfile4, szaV, pixList);
            createVcdfVar(ncfile4, vzaV, pixList);
            createVcdfVar(ncfile4, razV, pixList);
            createVcdfVar(ncfile4, timeV, pixList);
            createVcdfVar(ncfile4, cldFracV, pixList);
            createVcdfVar(ncfile4, landFlagV, pixList);

            createGlobalAttrb(ncfile4, sinP, ncdfName, version);
            ncfile4.create();
        } catch (IOException ex) {
            Logger.getLogger(ProdSinConverterL2.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return ncfile4;
        
    }

    NetcdfFileWriter createNetCdfFile(String ncdfName, SinProduct sinP, S3DataVersionNumbers version) {
        NetcdfFileWriter ncfile4 = null;
        try {
            ncfile4 = NetcdfFileWriter.createNew(NetcdfFileWriter.Version.netcdf4, ncdfName);
            Dimension dimPix  = ncfile4.addDimension(null, "pixel_number", sinP.cellMap.size());
            //Dimension dimView = ncfile4.addDimension(null, "instrument_view", 2);

            ArrayList<Dimension> pixList = new ArrayList<Dimension>();
            pixList.add(dimPix);
            //ArrayList<Dimension> viewList = new ArrayList<Dimension>();
            //viewList.add(dimView);
            //ArrayList<Dimension> viewPixList = new ArrayList<Dimension>();
            //viewPixList.add(dimView);
            //viewPixList.add(dimPix);
            
            createVcdfVar(ncfile4, pixV, pixList);
            //createVcdfVar(ncfile4, viewV, viewList);
            createVcdfVar(ncfile4, latV, pixList);
            createVcdfVar(ncfile4, lonV, pixList);
            for (int i=0; i<4; i++){
                createVcdfVar(ncfile4, pixCornerLatV[i], pixList);
                createVcdfVar(ncfile4, pixCornerLonV[i], pixList);
            }
            
            createVcdfVar(ncfile4, aod550V, pixList);
            createVcdfVar(ncfile4, aod670V, pixList);
            createVcdfVar(ncfile4, aod870V, pixList);
            createVcdfVar(ncfile4, aod1600V, pixList);
            
            createVcdfVar(ncfile4, angstromV, pixList);
            createVcdfVar(ncfile4, fmAodV, pixList);
            createVcdfVar(ncfile4, dustAodV, pixList);
            createVcdfVar(ncfile4, absAodV, pixList);
            createVcdfVar(ncfile4, ssaV, pixList);
            
            if (doSurfRefl){
                createVcdfVar(ncfile4, sreflec550V, pixList);
                createVcdfVar(ncfile4, sreflec670V, pixList);
                createVcdfVar(ncfile4, sreflec870V, pixList);
                createVcdfVar(ncfile4, sreflec1600V, pixList);
            }
            createVcdfVar(ncfile4, sigAod550V, pixList);
            createVcdfVar(ncfile4, sigAod670V, pixList);
            createVcdfVar(ncfile4, sigAod870V, pixList);
            createVcdfVar(ncfile4, sigAod1600V, pixList);
            
            createVcdfVar(ncfile4, szaV, pixList);
            createVcdfVar(ncfile4, vzaV, pixList);
            createVcdfVar(ncfile4, razV, pixList);
            createVcdfVar(ncfile4, timeV, pixList);
            createVcdfVar(ncfile4, cldFracV, pixList);
            createVcdfVar(ncfile4, landFlagV, pixList);

            createGlobalAttrb(ncfile4, sinP, ncdfName, version);
            ncfile4.create();
        } catch (IOException ex) {
            Logger.getLogger(ProdSinConverterL2.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return ncfile4;
        
    }

    @Override
    void createGlobalAttrb(NetcdfFileWriter ncfile, Product p, String ncdfName, DataVersionNumbers version) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    void createGlobalAttrb(NetcdfFileWriter ncfile, SinProduct sinP, String ncdfName, DataVersionNumbers version) {
        String prodFileName = sinP.p.getFileLocation().getName();
        if (prodFileName.endsWith(".dim")){
            prodFileName = prodFileName.replace(".dim", ".N1");
        }
        final SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.ENGLISH);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        final String currentTime = df.format(new Date());
        //final String CurrentTime = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss", Locale.ENGLISH).format(new Date());
        final String StartDate = df.format(sinP.p.getStartTime().getAsDate());    //p.getStartTime().format();
        final String StopDate = df.format(sinP.p.getEndTime().getAsDate());
        final String prodName = sinP.p.getName();
        final boolean atsr2 = prodName.startsWith("AT2");
        final String idStr = new File(ncdfName).getName();
        ncfile.addGroupAttribute(null, new Attribute("Conventions", "CF-1.6"));
        ncfile.addGroupAttribute(null, new Attribute("tracking_id", UUID.randomUUID().toString()));
		//ncfile.addGroupAttribute(null, new Attribute("naming authority", "swan.ac.uk"));
        ncfile.addGroupAttribute(null, new Attribute("naming_authority", "uk.ac.su.aatsraerosol"));
        ncfile.addGroupAttribute(null, new Attribute("title", "AARDVARC CCI aerosol product level 2"));
        ncfile.addGroupAttribute(null, new Attribute("product_version", version.toString()));
		ncfile.addGroupAttribute(null, new Attribute("summary", "This dataset contains the level-2 aerosol properties products from AATSR satellite observations. Data are processed by Swansea algorithm"));
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
        ncfile.addGroupAttribute(null, new Attribute("resolution", "10km x 10km"));
        ncfile.addGroupAttribute(null, new Attribute("projection", "sinusoidal [neq = "+sinP.proj.nEquator+"]"));
		ncfile.addGroupAttribute(null, new Attribute("cdm_data_type", "Swath"));
        ncfile.addGroupAttribute(null, new Attribute("cell", sinP.nCells));
        ncfile.addGroupAttribute(null, new Attribute("inputFileList", prodFileName)); //TODO: inputFileList should be blank space and carriage return separated 
        ncfile.addGroupAttribute(null, new Attribute("time_coverage_start", StartDate));
        ncfile.addGroupAttribute(null, new Attribute("time_coverage_end", StopDate));
        //ncfile.addGroupAttribute(null, new Attribute("time_coverage_duration", "P1D"));
		//ncfile.addGroupAttribute(null, new Attribute("time_coverage_resolution", "P1D"));
		ncfile.addGroupAttribute(null, new Attribute("geospatial_lat_min", sinP.minLat));
		ncfile.addGroupAttribute(null, new Attribute("geospatial_lat_max", sinP.maxLat));
		ncfile.addGroupAttribute(null, new Attribute("geospatial_lon_min", sinP.minLon));
		ncfile.addGroupAttribute(null, new Attribute("geospatial_lon_max", sinP.maxLon));
        ncfile.addGroupAttribute(null, new Attribute("date_created", currentTime));
		ncfile.addGroupAttribute(null, new Attribute("project", "Climate Change Initiative - European Space Agency"));
        ncfile.addGroupAttribute(null, new Attribute("references", "http://www.esa-aerosol-cci.org"));
		ncfile.addGroupAttribute(null, new Attribute("creator_name", "College of Science, Swansea University"));
		ncfile.addGroupAttribute(null, new Attribute("creator_url", "http://www.swan.ac.uk/staff/academic/environmentsociety/geography/northpeter/"));
		ncfile.addGroupAttribute(null, new Attribute("creator_email", "p.r.j.north@swansea.ac.uk, a.heckel@swansea.ac.uk"));
        ncfile.addGroupAttribute(null, new Attribute("source", "ATS_TOA_1P, V6.05"));
		ncfile.addGroupAttribute(null, new Attribute("keywords", "satellite,observation,atmosphere"));
		ncfile.addGroupAttribute(null, new Attribute("keywords_vocabulary", "NASA Global Change Master Directory (GCMD) Science Keywords"));
		//ncfile.addGroupAttribute(null, new Attribute("comment", "These data were produced at ESA CCI as part of the ESA Aerosol CCI project."));
		ncfile.addGroupAttribute(null, new Attribute("standard_name_vocabulary", "NetCDF Climate and Forecast (CF) Metadata Convention version 18"));
		ncfile.addGroupAttribute(null, new Attribute("license", "ESA CCI Data Policy: free and open access"));
        //ncfile.addGroupAttribute(null, new Attribute("product", prodName));
        //ncfile.addGroupAttribute(null, new Attribute("originator", "SU"));
        //ncfile.addGroupAttribute(null, new Attribute("originator_long", "Swansea University"));
        //ncfile.addGroupAttribute(null, new Attribute("originator_url", "http://www.swan.ac.uk/staff/academic/environmentsociety/geography/northpeter/"));
        //ncfile.addGroupAttribute(null, new Attribute("email", "p.r.j.north@swansea.ac.uk; a.heckel@swansea.ac.uk"));
		//ncfile.addGroupAttribute(null, new Attribute("institution", "Swansea University"));
		//ncfile.addGroupAttribute(null, new Attribute("history", "Level 2 product from Swansea algorithm"));
		//ncfile.addGroupAttribute(null, new Attribute("date_created", "20120918T163335Z"));
		//ncfile.addGroupAttribute(null, new Attribute("geospatial_vertical_min", "0 km"));
		//ncfile.addGroupAttribute(null, new Attribute("geospatial_vertical_max", "0 km"));
		//ncfile.addGroupAttribute(null, new Attribute("geospatial_lat_units", "degrees_north"));
		//ncfile.addGroupAttribute(null, new Attribute("geospatial_lon_units", "degrees_east"));         
    }

    void createGlobalAttrb(NetcdfFileWriter ncfile, SinProduct sinP, String ncdfName, S3DataVersionNumbers version) {
        final SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.ENGLISH);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        final String currentTime = df.format(new Date());
        //final String CurrentTime = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss", Locale.ENGLISH).format(new Date());
        final String StartDate = df.format(sinP.startTime.getAsDate());    //p.getStartTime().format();
        final String StopDate = df.format(sinP.endTime.getAsDate());
        final String idStr = new File(ncdfName).getName();
        ncfile.addGroupAttribute(null, new Attribute("Conventions", "CF-1.6"));
        ncfile.addGroupAttribute(null, new Attribute("tracking_id", UUID.randomUUID().toString()));
        ncfile.addGroupAttribute(null, new Attribute("naming_authority", "uk.ac.su.aatsraerosol"));
        ncfile.addGroupAttribute(null, new Attribute("title", "AARDVARC C3S aerosol product level 2"));
        ncfile.addGroupAttribute(null, new Attribute("product_version", version.toString()));
		ncfile.addGroupAttribute(null, new Attribute("summary", "This dataset contains the level-2 aerosol properties products from SLSTR satellite observations. Data are processed by Swansea algorithm"));
		ncfile.addGroupAttribute(null, new Attribute("id", idStr));
        ncfile.addGroupAttribute(null, new Attribute("sensor", "SLSTR"));
        ncfile.addGroupAttribute(null, new Attribute("platform", "SENTINEL-S3A"));
        ncfile.addGroupAttribute(null, new Attribute("resolution", "10km x 10km"));
        ncfile.addGroupAttribute(null, new Attribute("projection", "sinusoidal [neq = "+sinP.proj.nEquator+"]"));
		ncfile.addGroupAttribute(null, new Attribute("cdm_data_type", "Swath"));
        ncfile.addGroupAttribute(null, new Attribute("cell", sinP.nCells));
        ncfile.addGroupAttribute(null, new Attribute("inputFileList", sinP.getProductListAsString())); //TODO: inputFileList should be blank space and carriage return separated 
        ncfile.addGroupAttribute(null, new Attribute("time_coverage_start", StartDate));
        ncfile.addGroupAttribute(null, new Attribute("time_coverage_end", StopDate));
        //ncfile.addGroupAttribute(null, new Attribute("time_coverage_duration", "P1D"));
		//ncfile.addGroupAttribute(null, new Attribute("time_coverage_resolution", "P1D"));
		ncfile.addGroupAttribute(null, new Attribute("geospatial_lat_min", sinP.minLat));
		ncfile.addGroupAttribute(null, new Attribute("geospatial_lat_max", sinP.maxLat));
		ncfile.addGroupAttribute(null, new Attribute("geospatial_lon_min", sinP.minLon));
		ncfile.addGroupAttribute(null, new Attribute("geospatial_lon_max", sinP.maxLon));
        ncfile.addGroupAttribute(null, new Attribute("date_created", currentTime));
		ncfile.addGroupAttribute(null, new Attribute("project", "Climate Change Initiative - European Space Agency"));
        ncfile.addGroupAttribute(null, new Attribute("references", "http://www.esa-aerosol-cci.org"));
		ncfile.addGroupAttribute(null, new Attribute("creator_name", "College of Science, Swansea University"));
		ncfile.addGroupAttribute(null, new Attribute("creator_url", "http://www.swan.ac.uk/staff/academic/environmentsociety/geography/northpeter/"));
		ncfile.addGroupAttribute(null, new Attribute("creator_email", "p.r.j.north@swansea.ac.uk, a.heckel@swansea.ac.uk"));
        ncfile.addGroupAttribute(null, new Attribute("source", sinP.source));
		ncfile.addGroupAttribute(null, new Attribute("keywords", "satellite,observation,atmosphere"));
		ncfile.addGroupAttribute(null, new Attribute("keywords_vocabulary", "NASA Global Change Master Directory (GCMD) Science Keywords"));
		//ncfile.addGroupAttribute(null, new Attribute("comment", "These data were produced at ESA CCI as part of the ESA Aerosol CCI project."));
		ncfile.addGroupAttribute(null, new Attribute("standard_name_vocabulary", "NetCDF Climate and Forecast (CF) Metadata Convention version 18"));
		ncfile.addGroupAttribute(null, new Attribute("license", "ESA CCI Data Policy: free and open access"));
        //ncfile.addGroupAttribute(null, new Attribute("product", prodName));
        //ncfile.addGroupAttribute(null, new Attribute("originator", "SU"));
        //ncfile.addGroupAttribute(null, new Attribute("originator_long", "Swansea University"));
        //ncfile.addGroupAttribute(null, new Attribute("originator_url", "http://www.swan.ac.uk/staff/academic/environmentsociety/geography/northpeter/"));
        //ncfile.addGroupAttribute(null, new Attribute("email", "p.r.j.north@swansea.ac.uk; a.heckel@swansea.ac.uk"));
		//ncfile.addGroupAttribute(null, new Attribute("institution", "Swansea University"));
		//ncfile.addGroupAttribute(null, new Attribute("history", "Level 2 product from Swansea algorithm"));
		//ncfile.addGroupAttribute(null, new Attribute("date_created", "20120918T163335Z"));
		//ncfile.addGroupAttribute(null, new Attribute("geospatial_vertical_min", "0 km"));
		//ncfile.addGroupAttribute(null, new Attribute("geospatial_vertical_max", "0 km"));
		//ncfile.addGroupAttribute(null, new Attribute("geospatial_lat_units", "degrees_north"));
		//ncfile.addGroupAttribute(null, new Attribute("geospatial_lon_units", "degrees_east"));         
    }

    @Override
    void createVcdfVar(NetcdfFileWriter ncfile, NetcdfVariableProperties var, ArrayList<Dimension> dimList) {
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

    SinProduct getSinP() {
        return sinP;
    }
    
    private void binProductToSin(Product p, SinProduct sinP) {
        try {
            int pWidth = p.getSceneRasterWidth();
            int pHeight = p.getSceneRasterHeight();

            long startMilliSec1970 = utcToMilliSec1970(p.getStartTime());
            long endSec1970 = utcToMilliSec1970(p.getEndTime());
            long millisecPerLine = (endSec1970 - startMilliSec1970) / pHeight;
            
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
            Band ssaB    = p.getBand("ssa");

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

            int pixSec1970 = 0;
            for (int iy = 0; iy < pHeight; iy++) {
                if (iy%100 == 9) {
                    System.out.printf("L2 processing %5.1f%%\r", (float)(iy)/(float)(pHeight)*100f);
                    System.out.flush();
                }
                pixSec1970 = (int)((startMilliSec1970 + millisecPerLine * iy) / 1000);
                
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
                
                absAotB.readPixels(0, iy, pWidth, 1, absAot);
                ssaB.readPixels(0, iy, pWidth, 1, ssa);

                //sAot0550B.readPixels(0, iy, pWidth, 1, sAot0555);
                sAot0670B.readPixels(0, iy, pWidth, 1, sAot0659);
                sAot0870B.readPixels(0, iy, pWidth, 1, sAot0865);
                sAot1600B.readPixels(0, iy, pWidth, 1, sAot1610);

                if (doSurfRefl){
                    sref0555B.readPixels(0, iy, pWidth, 1, sref0550);
                    sref0659B.readPixels(0, iy, pWidth, 1, sref0670);
                    sref0865B.readPixels(0, iy, pWidth, 1, sref0870);
                    sref1610B.readPixels(0, iy, pWidth, 1, sref1600);
                }
                
                cldFracB.readPixels(0, iy, pWidth, 1, cldFrac);
                aotFlagsB.readPixels(0, iy, pWidth, 1, aotFlags);

                double angWvlLog = -1.0 / Math.log( 550. / 865.);
                float[] vals = new float[sinBandNames.length];
                for (int ix=0; ix<pWidth; ix++){
                    if (aotNd[ix] > 0){
                        vals[0] = pixSec1970;
                        vals[1] = aotNd[ix];
                        vals[2] = sAot0659[ix];
                        vals[3] = sAot0865[ix];
                        vals[4] = sAot1610[ix];
                        
                        vals[5] = (float)(Math.log(aotNd[ix] / sAot0865[ix]) * angWvlLog);
                        vals[6] = fineFrac[ix] * aotNd[ix];
                        vals[7] = dustFrac[ix] * (1 - fineFrac[ix]) * aotNd[ix];
                        vals[8] = absAot[ix];
                        vals[9] = ssa[ix];
                        
                        vals[10] = sref0550[ix];
                        vals[11] = sref0670[ix];
                        vals[12] = sref0870[ix];
                        vals[13] = sref1600[ix];

                        vals[14] = aotUnc[ix];
                        vals[15] = aotUnc[ix] * sAot0659[ix] / aotNd[ix];
                        vals[16] = aotUnc[ix] * sAot0659[ix] / aotNd[ix];
                        vals[17] = aotUnc[ix] * sAot0659[ix] / aotNd[ix];
                        
                        vals[18] = (doSyn)?(seaNad[ix]):(90.0f - seaNad[ix]);
                        vals[19] = (doSyn)?(seaNad[ix]):(90.0f - veaNad[ix]);
                        vals[20] = getRaz(saaNad[ix], vaaNad[ix]);
                        vals[21] = cldFrac[ix];
                        vals[22] = (aotFlags[ix] & 1);
                        if (sinBandNames.length != 23){
                            throw new IllegalStateException("mismatch in assigned and declared vals!");
                        }
                        sinP.add(lat[ix], lon[ix], vals);
                    }
                }
            }
            System.out.printf("L2 processing done                     \n");
            
        } catch (IOException ex) {
            Logger.getLogger(ProdSinConverterL2.class.getName()).log(Level.SEVERE, null, ex);
        } catch (RuntimeException ex) {
            Logger.getLogger(ProdSinConverterL2.class.getName()).log(Level.SEVERE, "Error: reading " + p.getFileLocation().getPath(), ex);
        }
    }

    private void binNcdfToSin(String fname, SinProduct sinP) {
        NetcdfFile ncF = null;
        try {
            ncF = NetcdfFile.open(fname);
            
            int pWidth = getNcdfRasterWidth(ncF);
            int pHeight = getNcdfRasterHeight(ncF);

            long startMilliSec1970 = utcToMilliSec1970(getNcdfTime(ncF, "@time_coverage_start"));
            long endSec1970 = utcToMilliSec1970(getNcdfTime(ncF, "@time_coverage_end"));
            long millisecPerLine = (endSec1970 - startMilliSec1970) / pHeight;
            
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
                sdr[i] = getShortVarAsFloatArr(ncF, sdrNames[i]);
            }
            float[] ang870  = getVarAsFloatArr(ncF, "ANG550_870");
            float[] fineAod = getVarAsFloatArr(ncF, "AAOD550");
            float[] absAod  = getVarAsFloatArr(ncF, "FM_AOD550");
            float[] dustAod = getVarAsFloatArr(ncF, "D_AOD550");
            float[] ssa     = getVarAsFloatArr(ncF, "SSA550");

            double[] szaNad = getVarAsDoubleArr(ncF, "solar_zenith_tn");
            double[] vzaNad = getVarAsDoubleArr(ncF, "sat_zenith_tn");
            float[] razNad   = getVarAsFloatArr(ncF, "rel_azimuth_an");
            //double[] razF   = getVarAsDoubleArr(ncF, "rel_azimuth_ao");
            
            int pixSec1970 = 0;
            float[] vals = new float[sinBandNames.length];
            for (int iy = 0; iy < pHeight; iy++) {
                if (iy%100 == 9) {
                    System.out.printf("L2 processing %5.1f%%\r", (float)(iy)/(float)(pHeight)*100f);
                    System.out.flush();
                }
                pixSec1970 = (int)((startMilliSec1970 + millisecPerLine * iy) / 1000);
                int idx;
                for (int ixx=0; ixx<pWidth; ixx++){
                    idx = iy * pWidth + ixx;
                    if (aod[0][idx] > 0){
                        vals[0] = pixSec1970;
                        vals[1] = aod[0][idx];
                        vals[2] = aod[1][idx];
                        vals[3] = aod[2][idx];
                        vals[4] = aod[3][idx];
                        
                        vals[5] = ang870[idx];
                        vals[6] = fineAod[idx];
                        vals[7] = dustAod[idx];
                        vals[8] = absAod[idx];
                        vals[9] = ssa[idx];
                        
                        vals[10] = sdr[0][idx];
                        vals[11] = sdr[1][idx];
                        vals[12] = sdr[2][idx];
                        vals[13] = sdr[3][idx];

                        vals[14] = aodUnc[0][idx];
                        vals[15] = aodUnc[1][idx];
                        vals[16] = aodUnc[2][idx];
                        vals[17] = aodUnc[3][idx];
                        
                        vals[18] = (float) szaNad[idx];
                        vals[19] = (float) vzaNad[idx];
                        vals[20] = (float) razNad[idx];
                        vals[21] = 0;
                        vals[22] = 0;
                        if (sinBandNames.length != 23){
                            throw new IllegalStateException("mismatch in assigned and declared vals!");
                        }
                        sinP.add((float)lat[idx], (float)lon[idx], vals);
                    }
                }
            }
            
            System.out.printf("L2 processing done                     \n");
            
        } catch (IOException ex) {
            Logger.getLogger(ProdSinConverterL2.class.getName()).log(Level.SEVERE, null, ex);
        } catch (RuntimeException ex) {
            Logger.getLogger(ProdSinConverterL2.class.getName()).log(Level.SEVERE, "Error: reading " + fname, ex);
        } finally {
            if (ncF != null) {
                try {
                    ncF.close();
                } catch (IOException ex) {
                    Logger.getLogger(ProdSinConverterL2.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    private void writeNcdf(String ncdfName, SinProduct sinP, DataVersionNumbers version) {
        pixV.validRange.setInt(1, sinP.nCells);
        NetcdfFileWriter ncFile = createNetCdfFile(ncdfName, sinP, version);
        Guardian.assertNotNull("ncFile null", ncFile);
        try {
            ncFile.write(pixV.ncV, Array.factory(genIdxArr(sinP.nCells)));
            //ncFile.write(viewV.ncV, Array.factory(new int[]{0, 1}));
            ncFile.write(latV.ncV, Array.factory(sinP.latArr[0]));
            ncFile.write(lonV.ncV, Array.factory(sinP.lonArr[0]));
            for (int i=0; i<4; i++){
                ncFile.write(pixCornerLatV[i].ncV, Array.factory(sinP.latArr[i+1]));
                ncFile.write(pixCornerLonV[i].ncV, Array.factory(sinP.lonArr[i+1]));
            }
            
            ncFile.write(timeV.ncV,    Array.factory(fArrToIntArr(sinP.bandsArr[0])));
            
            ncFile.write(aod550V.ncV,  Array.factory(sinP.bandsArr[1]));
            ncFile.write(aod670V.ncV,  Array.factory(sinP.bandsArr[2]));
            ncFile.write(aod870V.ncV,  Array.factory(sinP.bandsArr[3]));
            ncFile.write(aod1600V.ncV, Array.factory(sinP.bandsArr[4]));

            ncFile.write(angstromV.ncV, Array.factory(sinP.bandsArr[5]));
            ncFile.write(fmAodV.ncV,    Array.factory(sinP.bandsArr[6]));
            ncFile.write(dustAodV.ncV,  Array.factory(sinP.bandsArr[7]));
            ncFile.write(absAodV.ncV,   Array.factory(sinP.bandsArr[8]));
            ncFile.write(ssaV.ncV,      Array.factory(sinP.bandsArr[9]));
            
            if (doSurfRefl){
                ncFile.write(sreflec550V.ncV,  Array.factory(sinP.bandsArr[10]));
                ncFile.write(sreflec670V.ncV,  Array.factory(sinP.bandsArr[11]));
                ncFile.write(sreflec870V.ncV,  Array.factory(sinP.bandsArr[12]));
                ncFile.write(sreflec1600V.ncV, Array.factory(sinP.bandsArr[13]));
            }
            
            ncFile.write(sigAod550V.ncV,  Array.factory(sinP.bandsArr[14]));
            ncFile.write(sigAod670V.ncV,  Array.factory(sinP.bandsArr[15]));
            ncFile.write(sigAod870V.ncV,  Array.factory(sinP.bandsArr[16]));
            ncFile.write(sigAod1600V.ncV, Array.factory(sinP.bandsArr[17]));
            
            ncFile.write(szaV.ncV, Array.factory(sinP.bandsArr[18]));
            ncFile.write(vzaV.ncV, Array.factory(sinP.bandsArr[19]));
            ncFile.write(razV.ncV, Array.factory(sinP.bandsArr[20]));
            
            ncFile.write(cldFracV.ncV, Array.factory(sinP.bandsArr[21]));
            ncFile.write(landFlagV.ncV, Array.factory(fArrToIntArr(sinP.bandsArr[22])));
        } catch (IOException ex) {
            Logger.getLogger(ProdSinConverterL2.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidRangeException ex) {
            Logger.getLogger(ProdSinConverterL2.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                ncFile.close();
            } catch (IOException ex) {
                Logger.getLogger(ProdSinConverterL2.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    void writeS3Ncdf(String ncdfName, S3DataVersionNumbers version) {
        sinP.convCellsToArray();
        pixV.validRange.setInt(1, sinP.nCells);
        NetcdfFileWriter ncFile = createNetCdfFile(ncdfName, sinP, version);
        Guardian.assertNotNull("ncFile null", ncFile);
        try {
            ncFile.write(pixV.ncV, Array.factory(genIdxArr(sinP.nCells)));
            //ncFile.write(viewV.ncV, Array.factory(new int[]{0, 1}));
            ncFile.write(latV.ncV, Array.factory(sinP.latArr[0]));
            ncFile.write(lonV.ncV, Array.factory(sinP.lonArr[0]));
            for (int i=0; i<4; i++){
                ncFile.write(pixCornerLatV[i].ncV, Array.factory(sinP.latArr[i+1]));
                ncFile.write(pixCornerLonV[i].ncV, Array.factory(sinP.lonArr[i+1]));
            }
            
            ncFile.write(timeV.ncV,    Array.factory(fArrToIntArr(sinP.bandsArr[0])));
            
            ncFile.write(aod550V.ncV,  Array.factory(sinP.bandsArr[1]));
            ncFile.write(aod670V.ncV,  Array.factory(sinP.bandsArr[2]));
            ncFile.write(aod870V.ncV,  Array.factory(sinP.bandsArr[3]));
            ncFile.write(aod1600V.ncV, Array.factory(sinP.bandsArr[4]));

            ncFile.write(angstromV.ncV, Array.factory(sinP.bandsArr[5]));
            ncFile.write(fmAodV.ncV,    Array.factory(sinP.bandsArr[6]));
            ncFile.write(dustAodV.ncV,  Array.factory(sinP.bandsArr[7]));
            ncFile.write(absAodV.ncV,   Array.factory(sinP.bandsArr[8]));
            ncFile.write(ssaV.ncV,      Array.factory(sinP.bandsArr[9]));
            
            if (doSurfRefl){
                ncFile.write(sreflec550V.ncV,  Array.factory(sinP.bandsArr[10]));
                ncFile.write(sreflec670V.ncV,  Array.factory(sinP.bandsArr[11]));
                ncFile.write(sreflec870V.ncV,  Array.factory(sinP.bandsArr[12]));
                ncFile.write(sreflec1600V.ncV, Array.factory(sinP.bandsArr[13]));
            }
            
            ncFile.write(sigAod550V.ncV,  Array.factory(sinP.bandsArr[14]));
            ncFile.write(sigAod670V.ncV,  Array.factory(sinP.bandsArr[15]));
            ncFile.write(sigAod870V.ncV,  Array.factory(sinP.bandsArr[16]));
            ncFile.write(sigAod1600V.ncV, Array.factory(sinP.bandsArr[17]));
            
            ncFile.write(szaV.ncV, Array.factory(sinP.bandsArr[18]));
            ncFile.write(vzaV.ncV, Array.factory(sinP.bandsArr[19]));
            ncFile.write(razV.ncV, Array.factory(sinP.bandsArr[20]));
            
            ncFile.write(cldFracV.ncV, Array.factory(sinP.bandsArr[21]));
            ncFile.write(landFlagV.ncV, Array.factory(fArrToIntArr(sinP.bandsArr[22])));
        } catch (IOException ex) {
            Logger.getLogger(ProdSinConverterL2.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidRangeException ex) {
            Logger.getLogger(ProdSinConverterL2.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                ncFile.close();
            } catch (IOException ex) {
                Logger.getLogger(ProdSinConverterL2.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private int[] genIdxArr(int length) {
        int[] arr = new int[length];
        for (int i=0; i<length; i++) arr[i]=i;
        return arr;
    }

    private void addVarAtt(Variable v, Attribute a) {
        if (a != null) v.addAttribute(a);
    }
    
    private int[] fArrToIntArr(float[] fa){
        int ia[] = new int[fa.length];
        for (int i=0; i<fa.length; i++){
            ia[i] = (int)(fa[i]+0.5);
        }
        return ia;
    }

    void initAsS3Ncdf(String fname) throws IOException {
        sinP = new SinProduct(this.sinBandNames);
        //updateProdInfo(fname);
    }

    private void updateProdInfo(String fname) throws IOException {
        NetcdfFile ncF = NetcdfFile.open(fname);
        ProductData.UTC pStartTime = getNcdfTime(ncF, "@time_coverage_start");
        if (sinP.startTime == null || pStartTime.getMJD() < sinP.startTime.getMJD()) {
            sinP.startTime = pStartTime;
        }
        ProductData.UTC pEndTime = getNcdfTime(ncF, "@time_coverage_end");
        if (sinP.endTime == null || pEndTime.getMJD() > sinP.endTime.getMJD()) {
            sinP.endTime = pEndTime;
        }

        if (sinP.productList == null) {
            sinP.productList = new ArrayList<String>();
        }
        sinP.productList.add(getNcdfId(ncF));

        if (sinP.source == null || sinP.source.isEmpty()){
            Attribute att = ncF.findGlobalAttribute("source");
            sinP.source = att.getStringValue();
        }
        ncF.close();
    }

    private ProductData.UTC getNcdfTime(NetcdfFile ncF, String attStr) {
        Attribute att = ncF.findAttribute(attStr);
        ProductData.UTC utcTime = null;
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

    public float[] getShortVarAsFloatArr(NetcdfFile ncF, String varName) throws IOException {
        Variable var = ncF.findVariable(null, varName);
        short[] sArr = (short[]) var.read().copyTo1DJavaArray();
        Attribute att = var.findAttribute("add_offset");
        double off = att.getNumericValue().doubleValue();
        att = var.findAttribute("scale_factor");
        double scale = att.getNumericValue().doubleValue();
        att = var.findAttribute("_FillValue");
        short noData = att.getNumericValue().shortValue();
        float[] arr = new float[sArr.length];
        for (int i = 0; i < sArr.length; i++){
            if (sArr[i] == noData) {
                arr[i] = -1;
            }
            else {
                arr[i] = (float) (scale * (double)sArr[i] + off);
            }
        }
        return arr;
    }

}
