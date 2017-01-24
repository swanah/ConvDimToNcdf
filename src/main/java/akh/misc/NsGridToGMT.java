/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package akh.misc;

import akh.convdimtoncdf.ConvDimToNS;
import akh.convdimtoncdf.NetcdfVariableProperties;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.ma2.Index;
import ucar.ma2.IndexIterator;
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
public class NsGridToGMT {

    private static final NetcdfVariableProperties latV
        = new NetcdfVariableProperties("lat", "latitude", null, "degrees_north", DataType.FLOAT, -90f, 90f, null, null, "-90° < latitude < 90°", null);
    private static final NetcdfVariableProperties lonV
        = new NetcdfVariableProperties("lon", "longitude", null, "degrees_east", DataType.FLOAT, 0f, 360f, null, null, "0° < longitude < 360°", null);
    private static final NetcdfVariableProperties aod550V 
        = new NetcdfVariableProperties("od550aer", "Aerosol Optical Thickness", null, "1", DataType.FLOAT, 0f, 2f, null, null, "Average of AOT observations at 550nm", "550 nm");
    private static final NetcdfVariableProperties zV 
        = new NetcdfVariableProperties("z", "Aerosol Optical Thickness", null, "1", DataType.FLOAT, 1e-12f, 2f, 0f, null, "Average of AOT observations at 550nm", "550 nm");

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        String yyyymm = "200806";
        String nsName = "e:\\sat\\AerosolCCI\\CCI_v42_1\\schutgensTest\\ENVISAT_AATSR_AER-PRODUCTS-SU_"+yyyymm+".nc";
        String ncdfName = "e:\\sat\\AerosolCCI\\CCI_v42_1\\schutgensTest\\AOD_"+yyyymm+".nc";
        
        NetcdfFileWriter ncfile = null;
        NetcdfFile inFile = null;
        try {
            inFile = NetcdfFile.open(nsName);
            Array aLat = inFile.readSection("latitude");
            Array aLon = inFile.readSection("longitude");
            Array aAod = inFile.readSection("od550aer");
            inFile.close();
            float[][] aod = binData(aLat, aLon, aAod);
            
            ncfile = NetcdfFileWriter.createNew(NetcdfFileWriter.Version.netcdf4, ncdfName);

            Dimension dIdLat = ncfile.addDimension(null, "lat", 180);
            Dimension dIdLon = ncfile.addDimension(null, "lon", 360);
            ArrayList<Dimension> dimLst = new ArrayList<Dimension>(2);

            dimLst.add(dIdLon);
            createVcdfVar(ncfile, lonV, dimLst);
            dimLst.remove(dIdLon);
            dimLst.add(dIdLat);
            createVcdfVar(ncfile, latV, dimLst);
            dimLst.add(dIdLon);
            createVcdfVar(ncfile, zV, dimLst);

            createGlobalAttrb(ncfile, ncdfName);
            
            ncfile.create();

            Array a = Array.factory(latV.dataType, latV.ncV.getShape());
            for (int i=0; i<180; i++){
                a.setFloat(i, -89.5f+i);
            }
            ncfile.write(latV.ncV, a);

            a = Array.factory(lonV.dataType, lonV.ncV.getShape());
            for (int i=0; i<360; i++){
                a.setFloat(i, 0.5f+i);
            }
            ncfile.write(lonV.ncV, a);

            a = Array.factory(zV.dataType, zV.ncV.getShape());
            for (int i=0; i<360; i++){
                for (int j=0; j<180; j++){
                    a.setFloat(i+j*360, aod[j][i]);
                }
            }
            ncfile.write(zV.ncV, a);

            
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
    
    private static void writeVar(Array a, NetcdfVariableProperties nvp, NetcdfFileWriter ncfile) throws InvalidRangeException, IOException {
        //Array a = Array.factory(nvp.dataType, nvp.ncV.getShape());
        IndexIterator idxIter = a.getIndexIterator();
        float i=0, step = 1f / (360f * 180f);
        while (idxIter.hasNext()){
            idxIter.setFloatNext(i);
            i *= step;
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
            validRangeAtt = new Attribute("actual_range", var.validRange);
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

    private static void createGlobalAttrb(NetcdfFileWriter ncfile, String ncdfName) {
        final SimpleDateFormat df = new SimpleDateFormat("yyyy:MM:dd", Locale.ENGLISH);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        final String CurrentTime = df.format(new Date());

        ncfile.addGroupAttribute(null, new Attribute("Conventions", "COARDS, CF-1.5"));
        ncfile.addGroupAttribute(null, new Attribute("title", ""));
        ncfile.addGroupAttribute(null, new Attribute("node_offset", 1));
    }

    private static float[][] binData(Array aLat, Array aLon, Array aAod) {
        float[][] data = new float[180][360];
        int[][] count = new int[180][360];
        int[] shape = aLat.getShape();
        float lat, lon, aod;
        int iLat, iLon;
        for(int i=0; i<shape[0]; i++){
            lat = aLat.getFloat(i);
            lon = aLon.getFloat(i);
            if (lon < 0 || lon > 360){
                throw new AssertionError("lon ("+lon+") invalid", null);
            }
            aod = aAod.getFloat(i);
            iLat = (int)(lat + 89.5);
            iLon = (int)(lon);
            data[iLat][iLon] += aod;
            count[iLat][iLon] ++;
        }
        for (int i=0; i<180; i++){
            for(int j=0; j<360; j++){
                if(count[i][j]>0) data[i][j] /= count[i][j];
            }
        }
        return data;
    }

}
