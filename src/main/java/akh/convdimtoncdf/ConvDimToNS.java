/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package akh.convdimtoncdf;

import static akh.convdimtoncdf.ConvDimToNcdf.clearTempProduct;
import static akh.convdimtoncdf.ConvDimToNcdf.extractProduct;
import static akh.convdimtoncdf.ConvDimToNcdf.isTarGzFile;
import akh.findfilerecursive.FindFileRecursive;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.TiePointGrid;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.ma2.IndexIterator;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFileWriter;
import ucar.nc2.Variable;

/**
 *
 * @author akheckel
 */
public class ConvDimToNS {

    private static final NetcdfVariableProperties timeV
        = new NetcdfVariableProperties("time", "Time", null, "days since 2000-01-01 00:00:00", DataType.FLOAT, 2190f, 4018f, null, null, "UTC, days since 2000-01-01 00:00:00", null);
    private static final NetcdfVariableProperties latV
        = new NetcdfVariableProperties("latitude", "Latitude", null, "degrees_north", DataType.FLOAT, -90f, 90f, null, null, "-90° < latitude < 90°", null);
    private static final NetcdfVariableProperties lonV
        = new NetcdfVariableProperties("longitude", "Longitude", null, "degrees_east", DataType.FLOAT, -180f, 180f, null, null, "0° < longitude < 360°", null);
    private static final NetcdfVariableProperties pixCountV
        = new NetcdfVariableProperties("nobs", "Number of observations", null, "1", DataType.INT, 1, 1000, null, null, "Number of observations used in averaging", null);
    private static final NetcdfVariableProperties pixSizeV
        = new NetcdfVariableProperties("pixelsize", "Pixel size", null, "km²", DataType.FLOAT, 0f, 2f, null, null, "Estimated,%in%km 2", null);

    private static final NetcdfVariableProperties aod550V[] = new NetcdfVariableProperties[]{
        new NetcdfVariableProperties("od550aer", "Aerosol Optical Thickness", null, "1", DataType.FLOAT, 0f, 2f, null, null, "Average of AOT observations at 550nm", "550 nm"),
        new NetcdfVariableProperties("od550aer_s", "Spatial standard deviation in AOT", null, "1", DataType.FLOAT, 0f, 2f, null, null, "Standard deviation over individual observations", "550 nm")
    };
    private static final NetcdfVariableProperties sigAod550V
        = new NetcdfVariableProperties("od550aer_e", "Retrieval error in AOT", null, "1", DataType.FLOAT, 0f, 2f, null, null, "Averaged retrieval error for individual observations", "550 nm");
    private static final NetcdfVariableProperties aod865V[] = new NetcdfVariableProperties[]{
        new NetcdfVariableProperties("od865aer", "Aerosol Optical Thickness", null, "1", DataType.FLOAT, 0f, 2f, null, null, "Average of AOT observations at 865nm", "865 nm"),
        new NetcdfVariableProperties("od865aer_s", "Spatial standard deviation in AOT", null, "1", DataType.FLOAT, 0f, 2f, null, null, "Standard deviation over individual observations", "865 nm")
    };
    private static final NetcdfVariableProperties sigAod865V
        = new NetcdfVariableProperties("od865aer_e", "Retrieval error in AOT", null, "1", DataType.FLOAT, 0f, 2f, null, null, "Averaged retrieval error for individual observations", "865 nm");
    private static final NetcdfVariableProperties fmAodV[] = new NetcdfVariableProperties[]{
        new NetcdfVariableProperties("od550lt1aer", "", null, "1", DataType.FLOAT, 0f, 2f, null, null, "Average of fAOT observations at 550nm", "550 nm"),
        new NetcdfVariableProperties("od550lt1aer_s", "", null, "1", DataType.FLOAT, 0f, 2f, null, null, "Standard deviation over individual observations", "550 nm")
    };  

    
    private static final Map<NetcdfVariableProperties, NsStorage.ValueGetter<? extends Number>> ncdf2VgetMap = new HashMap<NetcdfVariableProperties, NsStorage.ValueGetter<? extends Number>>();
    static {
        ncdf2VgetMap.put(timeV, new NsStorage.ValueGetter<Float>() {public Float get(NsStorage src) {return src.doy2000;}});
        ncdf2VgetMap.put(latV, new NsStorage.ValueGetter<Float>() {public Float get(NsStorage src) {return src.lat;}});
        ncdf2VgetMap.put(lonV, new NsStorage.ValueGetter<Float>() {public Float get(NsStorage src) {return src.lon;}});
        ncdf2VgetMap.put(pixCountV, new NsStorage.ValueGetter<Integer>() {public Integer get(NsStorage src) {return src.pixCount;}});
        ncdf2VgetMap.put(pixSizeV, new NsStorage.ValueGetter<Float>() {public Float get(NsStorage src) {return src.pixSize;}});
        ncdf2VgetMap.put(aod550V[0], new NsStorage.ValueGetter<Float>() {public Float get(NsStorage src) {return src.aod550;}});
        ncdf2VgetMap.put(aod550V[1], new NsStorage.ValueGetter<Float>() {public Float get(NsStorage src) {return src.varAod550;}});
        ncdf2VgetMap.put(sigAod550V, new NsStorage.ValueGetter<Float>() {public Float get(NsStorage src) {return src.uncAod550;}});
        ncdf2VgetMap.put(aod865V[0], new NsStorage.ValueGetter<Float>() {public Float get(NsStorage src) {return src.aod865;}});
        ncdf2VgetMap.put(aod865V[1], new NsStorage.ValueGetter<Float>() {public Float get(NsStorage src) {return src.varAod865;}});
        ncdf2VgetMap.put(sigAod865V, new NsStorage.ValueGetter<Float>() {public Float get(NsStorage src) {return src.uncAod865;}});
        ncdf2VgetMap.put(fmAodV[0], new NsStorage.ValueGetter<Float>() {public Float get(NsStorage src) {return src.fm550;}});
        ncdf2VgetMap.put(fmAodV[1], new NsStorage.ValueGetter<Float>() {public Float get(NsStorage src) {return src.varFm550;}});
    }
    
    private static final Map<Integer, NsStorage> dataVector = new TreeMap<Integer, NsStorage>();
    private static int nData;


    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Locale.setDefault(Locale.ENGLISH);
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        CmdParam pars = parseCmdPars(args);
        
        /*
        String[] fnames = {"e:\\sat\\AerosolCCI\\CCI_v42_1\\ATS_TOA_1PUUPA20080126_083505_000065272065_00264_30879_4112.dim"};
        String date_s = "200802";
        String searchPath = "e:\\sat\\AerosolCCI\\CCI_v42_1\\%YYYY\\%MM";
        String ext = ".tar.gz";
        String ncdfName = "e:\\sat\\AerosolCCI\\CCI_v42_1\\schutgensTest\\ENVISAT_AATSR_AER-PRODUCTS-SU_"+date_s+".nc";
        */
        
        File[] fileList = getFileList(pars);
        binData(pars.date_s, fileList);
        if (dataVector == null || dataVector.size() < 1){
            System.err.println("no data found to process ... exiting");
            return;
        }
        nData = dataVector.size();

        NetcdfFileWriter ncfile = null;
        try {
            ncfile = NetcdfFileWriter.createNew(NetcdfFileWriter.Version.netcdf4, pars.ncdfName);

            //Dimension dimId = ncfile.addDimension(null, "pixid", 0, false, true, false);
            Dimension dimId = ncfile.addDimension(null, "data_length", nData);
            ArrayList<Dimension> dimLst = new ArrayList<Dimension>();
            dimLst.add(dimId);

            createVcdfVar(ncfile, aod550V[0], dimLst);
            createVcdfVar(ncfile, aod550V[1], dimLst);
            createVcdfVar(ncfile, sigAod550V, dimLst);
            createVcdfVar(ncfile, aod865V[0], dimLst);
            createVcdfVar(ncfile, aod865V[1], dimLst);
            createVcdfVar(ncfile, sigAod865V, dimLst);
            createVcdfVar(ncfile, fmAodV[0], dimLst);
            createVcdfVar(ncfile, fmAodV[1], dimLst);
            createVcdfVar(ncfile, lonV, dimLst);
            createVcdfVar(ncfile, latV, dimLst);
            createVcdfVar(ncfile, timeV, dimLst);
            createVcdfVar(ncfile, pixSizeV, dimLst);
            createVcdfVar(ncfile, pixCountV, dimLst);

            createGlobalAttrb(ncfile, pars.ncdfName, DataVersionNumbers.v4_21);
            
            ncfile.create();

            writeVar(timeV, ncfile);
            writeVar(latV, ncfile);
            writeVar(lonV, ncfile);
            writeVar(pixCountV, ncfile);
            writeVar(pixSizeV, ncfile);
            writeVar(aod550V[0], ncfile);
            writeVar(aod550V[1], ncfile);
            writeVar(sigAod550V, ncfile);
            writeVar(aod865V[0], ncfile);
            writeVar(aod865V[1], ncfile);
            writeVar(sigAod865V, ncfile);
            writeVar(fmAodV[0], ncfile);
            writeVar(fmAodV[1], ncfile);
            
        } catch (IOException ex) {
            Logger.getLogger(ConvDimToNS.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidRangeException ex) {
            Logger.getLogger(ConvDimToNS.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (ncfile != null) {
                try {
                    ncfile.close();
                } catch (IOException ex) {
                    Logger.getLogger(ConvDimToNS.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    private static void writeVar(NetcdfVariableProperties nvp, NetcdfFileWriter ncfile) throws InvalidRangeException, IOException {
        NsStorage.ValueGetter<? extends Number> vget = ncdf2VgetMap.get(nvp);
        Array a = Array.factory(nvp.dataType, nvp.ncV.getShape());
        IndexIterator idxIter = a.getIndexIterator();
        for (Map.Entry<Integer, NsStorage> entry : dataVector.entrySet()){
            NsStorage ds = entry.getValue();
            Number value = vget.get(ds);
            if (value instanceof Float){
                idxIter.setFloatNext(value.floatValue());
            }
            else if (value instanceof Integer){
                idxIter.setFloatNext(value.intValue());
            }
        }
        ncfile.write(nvp.ncV, a);
    }

    private static void createVcdfVar(NetcdfFileWriter ncfile, NetcdfVariableProperties var, ArrayList<Dimension> dimList) {
        Attribute longNameAtt = null;
        Attribute stdNameAtt = null;
        Attribute unitsAtt = null;
        Attribute validRangeAtt = null;
        Attribute fillValueAtt = null;
        Attribute axisAtt = null;
        Attribute coordsAtt = null;
        Attribute flagValuesAtt = null;
        Attribute flagMeanAtt = null;
        Attribute commentAtt = null;
        Attribute wavelengthAtt = null;

        if (var.longName != null && var.longName.length() > 0) {
            longNameAtt = new Attribute("long_name", var.longName);
        }
        if (var.stdName != null && var.stdName.length() > 0) {
            stdNameAtt = new Attribute("standard_name", var.stdName);
        }
        if (var.units != null && var.units.length() > 0) {
            unitsAtt = new Attribute("units", var.units);
        }
        if (var.validRange != null) {
            validRangeAtt = new Attribute("valid_range", var.validRange);
        }
        if (var.fillValue != null) {
            fillValueAtt = new Attribute("_FillValue", var.fillValue);
        }
        if (var.axis != null) {
            axisAtt = new Attribute("axis", var.axis);
        }
        if (var.coords != null) {
            coordsAtt = new Attribute("coordinates", var.coords);
        }
        if (var.flagMeanings != null && var.flagValues != null) {
            flagMeanAtt = new Attribute("flag_meanings", var.flagMeanings);
            flagValuesAtt = new Attribute("flag_values", var.flagValues);
        }
        if (var.comment != null && var.comment.length() > 0) {
            commentAtt = new Attribute("Comment", var.comment);
        }
        if (var.wavelength != null && var.wavelength.length() > 0) {
            wavelengthAtt = new Attribute("Wavelength", var.wavelength);
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
        addVarAtt(var.ncV, wavelengthAtt);
        addVarAtt(var.ncV, commentAtt);

    }

    private static void addVarAtt(Variable v, Attribute a) {
        if (a != null) {
            v.addAttribute(a);
        }
    }

    private static void createGlobalAttrb(NetcdfFileWriter ncfile, String ncdfName, DataVersionNumbers version) {
        //final String StartDate = prodInfo.get("StartDate");
        //final String StopDate = prodInfo.get("StopDate");
        final SimpleDateFormat df = new SimpleDateFormat("yyyy:MM:dd", Locale.ENGLISH);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        final String CurrentTime = df.format(new Date());

        //final String prodList = prodInfo.get("ProductList");        
        //final boolean atsr2 = prodList.startsWith("AT2");
        //final String idStr = new File(ncdfName).getName();
        //ncfile.addGroupAttribute(null, new Attribute("Conventions", "CF-1.6"));
        //ncfile.addGroupAttribute(null, new Attribute("tracking_id", UUID.randomUUID().toString()));
        //ncfile.addGroupAttribute(null, new Attribute("naming_authority", "uk.ac.su.aatsraerosol"));
        ncfile.addGroupAttribute(null, new Attribute("Product", "AARDVARC CCI aerosol product level 3 Version " + version));
        ncfile.addGroupAttribute(null, new Attribute("Institute", "GEMEO, School of Science, Swansea University, Wales"));
        ncfile.addGroupAttribute(null, new Attribute("Author", "Andreas Heckel"));
        //ncfile.addGroupAttribute(null, new Attribute("product_version", version.toString()));
        //ncfile.addGroupAttribute(null, new Attribute("summary", "This dataset contains the level-3 daily mean aerosol properties products from AATSR satellite observations. Data are processed by Swansea algorithm"));
        //ncfile.addGroupAttribute(null, new Attribute("id", idStr));
        ncfile.addGroupAttribute(null, new Attribute("Sensor", "AATSR"));
        ncfile.addGroupAttribute(null, new Attribute("Platform", "ENVISAT"));
        ncfile.addGroupAttribute(null, new Attribute("Creation_Date", CurrentTime));
        ncfile.addGroupAttribute(null, new Attribute("Comment", "na"));
        /*
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
         */
        //ncfile.addGroupAttribute(null, new Attribute("Units", "degree"));
        //ncfile.addGroupAttribute(null, new Attribute("node_offset", 1));
    }

    private static void binData(String date_s, File[] fList) {
        StartEndMilli startEndMilli = getStartEndMilli(date_s);

        for (File f : fList) {
            String fname = f.toString();
            System.out.println("binning " + fname);
            
            boolean isTarFile = isTarGzFile(fname);
            if (isTarFile){
                fname = extractProduct(fname);
            }
            Product p = null;
            try {
                p = ProductIO.readProduct(fname);
                int pWidth = p.getSceneRasterWidth();
                int pHeight = p.getSceneRasterHeight();

                long startMilliSec1970 = utcToMilliSec1970(p.getStartTime());
                long endMilliSec1970 = utcToMilliSec1970(p.getEndTime());
                long millisecPerLine = (endMilliSec1970 - startMilliSec1970) / pHeight;
                
                if (!isProductInTimePeriod(startEndMilli, startMilliSec1970, endMilliSec1970)) {
                    System.err.println("skipping " + p.getName());
                    continue;
                }

                TiePointGrid latTpg = p.getTiePointGrid("latitude");
                TiePointGrid lonTpg = p.getTiePointGrid("longitude");
                Band aot550Band = p.getBand("aot_nd_2");
                Band aotUnc550Band = p.getBand("aot_brent_nd_1");
                Band aot865B = p.getBand("aot_nd_0870_1");
                Band aotUnc865B = p.getBand("aot_brent_nd_0870_1");
                Band fotB = p.getBand("frac_fine_total_1");
                float[] lat = new float[pWidth];
                float[] lon = new float[pWidth];
                float[] aot550 = new float[pWidth];
                float[] sigAot550 = new float[pWidth];
                float[] aot865 = new float[pWidth];
                float[] sigAot865 = new float[pWidth];
                float[] fot = new float[pWidth];
                long pixMilliSec1970;
                float doy2000;
                final int offset = 4;
                final int skip = 9;
                int ilat = -1, ilon = -1, itime = -1;
                int idx3d;
                NsStorage nsData;
                for (int iy = offset; iy < pHeight; iy += skip) {
                    pixMilliSec1970 = (startMilliSec1970 + millisecPerLine * iy);
                    if (pixMilliSec1970 < startEndMilli.start || pixMilliSec1970 > startEndMilli.end) {
                        continue;
                    }
                    doy2000 = (float) milliToDoy30m(pixMilliSec1970, 2000);

                    latTpg.readPixels(0, iy, pWidth, 1, lat);
                    lonTpg.readPixels(0, iy, pWidth, 1, lon);
                    aot550Band.readPixels(0, iy, pWidth, 1, aot550);
                    aotUnc550Band.readPixels(0, iy, pWidth, 1, sigAot550);
                    aot865B.readPixels(0, iy, pWidth, 1, aot865);
                    aotUnc865B.readPixels(0, iy, pWidth, 1, sigAot865);
                    fotB.readPixels(0, iy, pWidth, 1, fot);
                    double angWvlLog = -1.0 / Math.log(550. / 865.);

                    for (int ix = offset; ix < pWidth; ix += skip) {
                        if (aot550[ix] > 0) {
                            ilat = lat2idx(lat[ix]);
                            ilon = lon2idx(lon[ix]);
                            itime = time2idx(pixMilliSec1970 - startEndMilli.start);
                            idx3d = itime * 180 * 360 + ilat * 360 + ilon;
                            if (dataVector.containsKey(idx3d)) {
                                nsData = dataVector.get(idx3d);
                            }
                            else {
                                nsData = new NsStorage(lat2Bin(lat[ix]), lon2Bin(lon[ix]), doy2000);
                                dataVector.put(idx3d, nsData);
                            }
                            //nsData.addPixel(aot550[ix], 0, 0, 0, 0);
                            nsData.addPixel(aot550[ix], sigAot550[ix], aot865[ix], sigAot865[ix], (fot[ix]*aot550[ix]));
                        } // if aotNd >0
                    } // for ix
                } // for iy

            } catch (IOException ex) {
                Logger.getLogger(ConvDimToNS.class.getName()).log(Level.WARNING, null, ex);
            } finally {
                if (p != null) {
                    p.dispose();
                }
                if (isTarFile){
                    clearTempProduct(fname);
                }
            }
        }
    }

    private static long utcToMilliSec1970(ProductData.UTC pixTime) {
        Date pixDate = pixTime.getAsDate();
        long milliSec1970 = pixDate.getTime();
        return milliSec1970;
    }

    private static float lon180To360(float lon){
        return (lon > 0) ? lon : 360f + lon;
    }
    
    private static int lon2idx(float lon) {
        int ilon;
        ilon = (int) Math.floor(lon180To360(lon));
        if (ilon == 360) {
            ilon = 359;
        }
        return ilon;
    }

    private static int lat2idx(float lat) {
        int ilat;
        ilat = (int) Math.floor(lat) + 90;
        if (ilat == 180) {
            ilat = 179;
        }
        return ilat;
    }

    private static int time2idx(double time) {
        int itime;
        // MilliSecPerDay = 1000 * 60 * 60 * 24
        // TimeStep is half hourly, meaning: 24 * 2 time steps per day
        // so need to convert from millisec to half hour with 1000 * 60 * 60 * 24 / (24 * 2) = 60 * 30 * 1000
        int timeSteps = 1800000;
        itime = (int) Math.floor(time / timeSteps);
        return itime;
    }

    private static float idx2lat(int ilat) {
        float lat;
        lat = -89.5f + ilat;
        return lat;
    }

    private static float idx2lon(int ilon) {
        float lon;
        lon = -179.5f + ilon;
        return lon;
    }
    
    private static float lon2Bin(float lon) {
        return (float)(lon2idx(lon) + 0.5f);
    }

    private static float lat2Bin(float lat) {
        int latBin;
        latBin = (int) Math.floor(lat+90);
        if (latBin == 180) {
            latBin = 179;
        }
        return latBin - 89.5f;
    }

    /**
     * compute length of month in days of the specified date
     * 
     * @param date
     * @return length of month in days
     */
    private static int getDaysOfMonth(Date date) {
        GregorianCalendar cal = new GregorianCalendar();
        cal.setTime(date);
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    /**
     * convert date/time given in milliseconds since 1970 to 
     * fractional days since 01 Jan of specified base year
     * 
     * @param milTime1970 - date/time given in milliseconds since 1970
     * @param year - base / reference year
     * @return fractional day of year
     */
    static double milliToDoy(long mtime1970, int year) {
        double doy2k = -1;
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            double base2k = df.parse("2000-01-01 00:00:00").getTime();
            double pixMilli = (double) mtime1970;
            double mSecPerDay = 1000 * 60 * 60 * 24;
            doy2k = (pixMilli - base2k) / mSecPerDay;
        } catch (ParseException ex) {
            Logger.getLogger(ConvDimToNS.class.getName()).log(Level.SEVERE, null, ex);
        }

        return doy2k;
    }

    /**
     * convert date/time given in milliseconds since 1970 to 
     * fractional days since 01 Jan of specified base year and return closest half hour interval
     * e.g. times between 00:00:00 - 00:29:59 will be 00:15:00
     *      times between 00:30:00 - 00:59:59 will be 00:45:00
     * 
     * @param milTime1970 - date/time given in milliseconds since 1970
     * @param year - base / reference year
     * @return fractional day of year half hourly
     */
    static double milliToDoy30m(long milTime1970, int year) {
        double doy2k = -1;
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            double base2k = df.parse("2000-01-01 00:00:00").getTime();
            double pixMilli = (double) milTime1970;
            double mSecPerDay = 1000 * 60 * 60 * 24;
            double a = (pixMilli - base2k) / (1000 * 60 * 30);
            doy2k = (Math.floor(a) + 0.5) / (2 * 24);
        } catch (ParseException ex) {
            Logger.getLogger(ConvDimToNS.class.getName()).log(Level.SEVERE, null, ex);
        }

        return doy2k;
    }

    /**
     * convert a Date to fractional days since 01 Jan of specified base year
     * 
     * @param date - Date to convert
     * @param year - base / reference year
     * @return day of year since 01. Jan of year
     */
    static double dateToDoy(Date date, int year) {
        double doy = Double.NaN;
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd");
        try {
            long dateMilli = date.getTime();
            long baseMilli = df.parse(String.format("%4d0101", year)).getTime();
            int mSecPerDay = 1000 * 60 * 60 * 24;
            doy = (dateMilli - baseMilli) / mSecPerDay;
        } catch (ParseException ex) {
            Logger.getLogger(ConvDimToNS.class.getName()).log(Level.SEVERE, null, ex);
        }

        return doy;
    }

    /**
     * convert frational day of year (doy) with reference year (year) to a Date
     *
     * @param doy - day of year
     * @param year - base / reference year
     * @return Date
     */
    static Date doyToDate(double doy, int year) {
        Date d = null;
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd");
        try {
            long baseYear = df.parse(String.format("%4d0101", year)).getTime();
            int mSecPerDay = 1000 * 60 * 60 * 24;
            long pixMilli = (long) (doy * mSecPerDay + baseYear);
            d = new Date(pixMilli);
        } catch (ParseException ex) {
            Logger.getLogger(ConvDimToNS.class.getName()).log(Level.SEVERE, null, ex);
        }

        return d;
    }

    static File[] getFileList(final CmdParam pars) {
        FilenameFilter fltr = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(pars.searchExt);
            }
        };
        File[] fList = new FindFileRecursive().listFilesAsArray(pars.searchPath, fltr, true);
        return fList;
    }
    
    static StartEndMilli getStartEndMilli(String date_s){
        long start = 0;
        long end = 0;
        try {
            if (date_s.length() == 8) {
                SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd");
                start = df.parse(date_s).getTime();
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(start);
                c.add(Calendar.DAY_OF_MONTH, 1);
                end = c.getTimeInMillis();
            } else if (date_s.length() == 6) {
                SimpleDateFormat df = new SimpleDateFormat("yyyyMM");
                start = df.parse(date_s).getTime();
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(start);
                c.add(Calendar.MONTH, 1);
                end = c.getTimeInMillis();
            }
            return new StartEndMilli(start, end);
        } catch (ParseException ex) {
            Logger.getLogger(ConvDimToNS.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return null;
    }

    private static boolean isProductInTimePeriod(StartEndMilli startEndMilli, long startMilliSec1970, long endMilliSec1970) {
        return ((endMilliSec1970 > startEndMilli.start) && (startMilliSec1970 < startEndMilli.end));
    }

    private static CmdParam parseCmdPars(String[] args) {
        
        if (args.length != 2){
            String msg= "Wrong number of cmd line args! \n Usage: " + ConvDimToNS.class.getName() + " <paramFile.par> <date(yyyymm)>";
            System.err.println(msg);
            System.exit(1);
        }
        
        CmdParam p = new CmdParam();
        p.date_s = args[1];
        p.yyyy = p.date_s.substring(0, 4);
        p.mm   = p.date_s.substring(4, 6);
        String parName = args[0];
        BufferedReader bufR = null;
        try {
            bufR = new BufferedReader(new FileReader(parName));
            String line;
            while ((line = bufR.readLine()) != null) {
                line = line.trim();
                if (line.length() > 0){
                    String[] sarr = line.split("\\s+");
                    if (sarr[0].equals("SRCPATH")) {
                        p.searchPath = sarr[2].replace("%MM", p.mm).replace("%YYYY", p.yyyy);
                    }
                    if (sarr[0].equals("SRCEXT")) {
                        p.searchExt = sarr[2];
                    }
                    if (sarr[0].equals("OUTPATH")) {
                        p.ncdfName = sarr[2].replace("%MM", p.mm).replace("%YYYY", p.yyyy);
                    }
                }
            }
            return p;
        } catch (FileNotFoundException ex) {
            Logger.getLogger(ConvDimToNS.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(ConvDimToNS.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (bufR != null){
                try {
                    bufR.close();
                } catch (IOException ex) {
                    Logger.getLogger(ConvDimToNS.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        return null;
    }

    private static class StartEndMilli {
        long start, end;

        public StartEndMilli(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }
    
    private static class CmdParam {
        String searchPath;
        String searchExt;
        String date_s;
        String ncdfName;
        String yyyy;
        String mm;
    }

}
