/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package akh.convdimtoncdf;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.Attribute;
import ucar.nc2.CDMNode;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.NetcdfFileWriter;
import ucar.nc2.Variable;

/**
 *
 * @author akheckel
 */
class L3Accumulator {

    //static String[] formatStrings = {"yyyyMMdd'T'HHmmss'Z'", "dd-MMM-yyyy HH:mm:ss.SSS"};
    static DateFormat[] sdf = {
        new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.ENGLISH),
        new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss.SSS", Locale.ENGLISH),
    };
    
    boolean needsInit;
    private List<Dimension> dimLst;
    private List<Variable> varLst;
    private List<Attribute> globalAttLst;
    private float[][] meanGrids;
    private float[][] varianceGrids;
    private float[][] rmsGrids;
    private int[] count;
    private int[] weight;
    private int nCells;
    private int nVars;
    private int nDims;
    private String[] varNames;
    private float[] varNoData;
    private Variable countVar;
    private Array[] dimDataArr;
    private ArrayList<Variable> dimVarLst;
    private ArrayList<String> inputFileLst;
    private Date startDate = null;
    private Date endDate = null;
    private boolean accumulateCounts;
    private boolean hasData;
    private boolean propL3Unc;
    private final String[] aodMeanNames;
    private final String[] uncMeanNames;
    private final String[] aodSdevNames;
    private final String[] uncNames;
    private final int nAods;

    public L3Accumulator() {
        needsInit = true;
        propL3Unc = false;
        aodMeanNames = new String[]{"AOD550_mean",             "AOD670_mean",             "AOD870_mean",             "AOD1600_mean"};
        uncMeanNames = new String[]{"AOD550_uncertainty_mean", "AOD670_uncertainty_mean", "AOD870_uncertainty_mean", "AOD1600_uncertainty_mean"};
        aodSdevNames = new String[]{"AOD550_sdev",             "AOD670_sdev",             "AOD870_sdev",             "AOD1600_sdev"};
        uncNames     = new String[]{"AOD550_uncertainty",      "AOD670_uncertainty",      "AOD870_uncertainty",      "AOD1600_uncertainty"};
        nAods = aodMeanNames.length;
    }

    void initAccumulator(NetcdfFile ncFile) {
        dimLst = new ArrayList<>(ncFile.getDimensions());
        varLst = new ArrayList<>(ncFile.getVariables());
        Iterator<Variable> vIter = varLst.iterator();
        while (vIter.hasNext()) {
            String vName = vIter.next().getShortName();
            if (vName.contains("zenith") || vName.contains("azimuth")) {
                vIter.remove();
            }
        }
        CDMNode node = ncFile.findDimension("instrument_view");
        if (node != null) {
            dimLst.remove((Dimension) node);
        }
        node = ncFile.findVariable("instrument_view");
        if (node != null) {
            varLst.remove((Variable) node);
        }

        dimVarLst = new ArrayList<>(dimLst.size());
        Iterator<Dimension> dimIter = dimLst.iterator();
        while (dimIter.hasNext()) {
            String dimName = dimIter.next().getShortName();
            Variable dimVar = ncFile.findVariable(dimName);
            if (dimVar != null) {
                dimVarLst.add(dimVar);
                varLst.remove(dimVar);
            }
        }
        countVar = ncFile.findVariable("pixel_count");
        varLst.remove(countVar);
        
        nDims = dimLst.size();
        nVars = varLst.size();
        nCells = (int) (ncFile.findVariable("AOD550_mean").getSize());
        count = new int[nCells];
        weight = new int[nCells];
        meanGrids = new float[nVars][nCells];
        varianceGrids = new float[nVars][nCells];
        rmsGrids = new float[nVars][nCells];
        varNames = new String[nVars];
        varNoData = new float[nVars];
        Variable v;
        for (int i = 0; i < nVars; i++) {
            v = varLst.get(i);
            varNames[i] = v.getShortName();
            varNoData[i] = v.findAttribute("_FillValue").getNumericValue().floatValue();
        }
        dimDataArr = new Array[nDims];
        try {
            for (int i = 0; i < nDims; i++) {
                dimDataArr[i] = ncFile.findVariable(dimVarLst.get(i).getShortName()).read();
            }
        } catch (IOException ex) {
            Logger.getLogger(L3Accumulator.class.getName()).log(Level.SEVERE, null, ex);
        }

        globalAttLst = new ArrayList<>(ncFile.getGlobalAttributes());
        inputFileLst = new ArrayList<>(31);
        hasData = false;
        needsInit = false;
    }

    void add(File netcdfFile) {
        float aod, unc;
        try {
            NetcdfFile ncFile = NetcdfFile.open(netcdfFile.getPath());
            if (needsInit) {
                initAccumulator(ncFile);
            }
            setStartStopDate(ncFile.getGlobalAttributes());
            System.out.println(startDate + " --> " + endDate);
            inputFileLst.add(netcdfFile.getName());
            Variable v = ncFile.findVariable("pixel_count");
            int[] countArr = (int[]) v.read().copyTo1DJavaArray();
            for (int i = 0; i < nCells; i++) {
                if (countArr[i] > 0) {
                    if (!hasData) hasData = true;
                    if (accumulateCounts){
                        count[i] ++;
                        weight[i] += countArr[i];
                    }
                    else{
                        count[i]++;
                        weight[i]++;
                    }
                }
            }
            Array arr;
            for (int iVar = 0; iVar < nVars; iVar++) {
                v = ncFile.findVariable(varNames[iVar]);
                boolean doVariance = Arrays.asList(aodMeanNames).contains(v.getShortName());
                boolean doRms      = Arrays.asList(aodSdevNames).contains(v.getShortName());
                assert (nCells == v.getSize()) : "nCells(" + nCells + ") != v.getSize(" + v.getSize() + ")";
                if (v.getDataType() != DataType.FLOAT) {
                    System.out.println("not float");
                }
                float delta;
                arr = v.read();
                for (int i = 0; i < nCells; i++) {
                    if (countArr[i] > 0) {
                        delta = arr.getFloat(i) - meanGrids[iVar][i];
                        if (accumulateCounts) {
                            delta *= countArr[i];
                        }
                        meanGrids[iVar][i] += delta / weight[i];
                        if (doVariance){
                            varianceGrids[iVar][i] += delta * (arr.getFloat(i) - meanGrids[iVar][i]);
                        }
                        if (doRms){
                            rmsGrids[iVar][i] += (float)Math.pow(arr.getFloat(i), 2);
                        }
                    }
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(L3Accumulator.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    void normalize() {
        for (int i = 0; i < nCells; i++) {
            if (count[i] > 0) {
                for (int iVar = 0; iVar < nVars; iVar++) {
                    meanGrids[iVar][i] /= count[i];
                }
            }
            else {
                for (int iVar = 0; iVar < nVars; iVar++) {
                    meanGrids[iVar][i] = varNoData[iVar];
                }
            }
        }
    }
    
    /*
    * computes standard err from variance sum of squares
    * stderr = sqrt( var / (n(n-1)) )
    */
    void normVari2StdErr() {
        for (int i = 0; i < nCells; i++) {
            if (count[i] > 0) {
                for (int iVar = 0; iVar < nVars; iVar++) {
                    if (count[i] > 1 && varianceGrids[iVar][i] > 0){
                        varianceGrids[iVar][i] = (float) Math.sqrt(varianceGrids[iVar][i] / (weight[i] * (count[i] - 1)));
                    }
                    else {
                        varianceGrids[iVar][i] = 0;
                    }
                    if (rmsGrids[iVar][i] > 0){
                        rmsGrids[iVar][i] = (float) Math.sqrt(rmsGrids[iVar][i] / weight[i]);
                    }
                    else {
                        rmsGrids[iVar][i] = 0;
                    }
                }
            }
            else {
                for (int iVar = 0; iVar < nVars; iVar++) {
                    meanGrids[iVar][i] = varNoData[iVar];
                    varianceGrids[iVar][i] = 0;
                    rmsGrids[iVar][i]      = 0;
                }
            }
        }
    }

    void writeGrids(String fname) {
        System.out.println("writing "+fname);
        Map<String, Dimension> dimMap = new HashMap<>(nDims);
        Variable[] vArr = new Variable[nVars + 1 + nDims];
        NetcdfFileWriter ncFile = null;
        try {
            ncFile = NetcdfFileWriter.createNew(NetcdfFileWriter.Version.netcdf4, fname);
            for (int i = 0; i < nDims; i++) {
                Dimension d = dimLst.get(i);
                dimMap.put(d.getShortName(), ncFile.addDimension(null, d.getShortName(), d.getLength()));
            }
            for (int i = 0; i < nDims; i++) {
                vArr[i] = copyVar(dimVarLst.get(i), dimMap, ncFile);
            }
            vArr[nDims] = copyVar(countVar, dimMap, ncFile);
            for (int i = 0; i < nVars; i++) {
                vArr[i + nDims + 1] = copyVar(varLst.get(i), dimMap, ncFile);
            }
            
            modifyGloablAtts();
            for (int i = 0; i < globalAttLst.size(); i++) {
                Attribute a = globalAttLst.get(i);
                ncFile.addGroupAttribute(null, a);
            }
            ncFile.create();

            // write dimension vars
            for (int i = 0; i < nDims; i++) {
                ncFile.write(vArr[i], dimDataArr[i]);
            }
            // write pixel_counts
            ncFile.write(vArr[nDims], Array.factory(weight).reshapeNoCopy(vArr[nDims].getShape()));
            // write vars
            List<String> uncNamesL = Arrays.asList(uncNames);
            float[] tmp;
            for (int i = 0; i < nVars; i++) {
                if (propL3Unc && uncNamesL.contains(varNames[i])){
                    float[] meanAod = meanGrids[getAodId(varNames[i])];         // mean of aod (only used aas fall back if only one sample)
                    float[] meanUnc = meanGrids[getUncMeanId(varNames[i])];     // mean of uncertainty   = 2a) systematic
                    float[] varianceAod = varianceGrids[getAodId(varNames[i])]; // std error of aod mean = 2b) sampling monthly
                    float[] rmsSdevAod = rmsGrids[getAodSdevId(varNames[i])];   // rms of aodSdev        = 2c) sampling daily
                    tmp = computePropUncert(meanUnc, varianceAod, rmsSdevAod, meanAod, i);
                    ncFile.write(vArr[i + nDims + 1], Array.factory(tmp).reshapeNoCopy(vArr[i + nDims + 1].getShape()));
                }
                else {
                    ncFile.write(vArr[i + nDims + 1], Array.factory(meanGrids[i]).reshapeNoCopy(vArr[i + nDims + 1].getShape()));
                }
            }
            
        } catch (IOException ex) {
            Logger.getLogger(L3Accumulator.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidRangeException ex) {
            Logger.getLogger(L3Accumulator.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (ncFile != null) {
                try {
                    ncFile.close();
                } catch (IOException ex) {
                    Logger.getLogger(L3Accumulator.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    Variable copyVar(Variable v, Map<String, Dimension> dimMap, NetcdfFileWriter ncF) {
        List<Dimension> vDimLst = new ArrayList<>(nDims);
        for (int i=0; i<v.getRank(); i++){
            vDimLst.add(dimMap.get(v.getDimension(i).getShortName()));
        }
        Variable vCpy = ncF.addVariable(null, v.getShortName(), v.getDataType(), vDimLst);
        vCpy.addAll(v.getAttributes());
        return vCpy;
    }

    private void modifyGloablAtts() {
        Collections.sort(inputFileLst);
        String inputFileString = inputFileLst.get(0);
        for (int i = 1; i < inputFileLst.size(); i++) {
            inputFileString += " \n" + inputFileLst.get(i);
        }
        String dateStr;
        Attribute newAtt;
        ListIterator<Attribute> iter = globalAttLst.listIterator();
        try {
            while (iter.hasNext()) {
                Attribute a = iter.next();
                if (a.getShortName().equals("inputfilelist")) {
                    newAtt = new Attribute("inputfilelist", inputFileString);
                    iter.set(newAtt);
                }
                if (a.getShortName().equals("product")) {
                    newAtt = new Attribute("inputfilelist", inputFileString);
                    iter.set(newAtt);
                }
                if (a.getShortName().equals("time_coverage_start")) {
                    dateStr = sdf[0].format(startDate);
                    newAtt = new Attribute("time_coverage_start", dateStr);
                    iter.set(newAtt);
                }
                if (a.getShortName().equals("time_coverage_end")) {
                    dateStr = sdf[0].format(endDate);
                    newAtt = new Attribute("time_coverage_end", dateStr);
                    iter.set(newAtt);
                }
                if (a.getShortName().equals("startdate")) {
                    dateStr = sdf[1].format(startDate);
                    newAtt = new Attribute("startdate", dateStr);
                    iter.set(newAtt);
                }
                if (a.getShortName().equals("stopdate")) {
                    dateStr = sdf[1].format(endDate);
                    newAtt = new Attribute("stopdate", dateStr);
                    iter.set(newAtt);
                }
            }
        } catch (UnsupportedOperationException | ClassCastException | IllegalArgumentException | IllegalStateException ex) {
            System.err.println(ex);
        }
    }

    private Date getAttributeAsDate(Attribute a) {        
        String dateStr = a.getStringValue();
        for (int i=0; i<sdf.length; i++) {
            try {
                if (i==1){
                    if (dateStr.matches(".*\\.\\d{6}$")){
                        dateStr = dateStr.substring(0, dateStr.length()-3);
                    }
                }
                return sdf[i].parse(dateStr);
            } catch (ParseException ex) {
            }
        }
        return null;
    }
    
    private int[] findAttsByName(List<Attribute> attList, String[] attNames){
        int[] idx = new int[attNames.length];
        ArrayList<String> attNameList = new ArrayList<>(attList.size());
        for (int i=0; i<attList.size(); i++){
            attNameList.add(i, attList.get(i).getShortName());
        }
        for (int i=0; i<attNames.length; i++){
            idx[i] = attNameList.indexOf(attNames[i]);
        }
        return idx;
    }
    
    private void setStartStopDate(List<Attribute> globalAtts) {
        Date d;
        int[] timeIdx = findAttsByName(globalAtts, new String[]{"time_coverage_start", "time_coverage_end", "startdate", "stopdate"});
        if (timeIdx[0] > -1 && timeIdx[1] > -1){
            d = getAttributeAsDate(globalAtts.get(timeIdx[0]));
            if (startDate == null || d.before(startDate)) startDate = d;
            d = getAttributeAsDate(globalAtts.get(timeIdx[1]));
            if (endDate   == null || d.after(endDate))    endDate   = d;
        }
        else if (timeIdx[2] > -1 && timeIdx[3] > -1){
            d = getAttributeAsDate(globalAtts.get(timeIdx[2]));
            if (startDate == null || d.before(startDate)) startDate = d;
            d = getAttributeAsDate(globalAtts.get(timeIdx[3]));
            if (d==null){
                System.err.println("mist");
            }
            if (endDate   == null || d.after(endDate))    endDate   = d;
        }
        else {
            Logger.getLogger(L3Accumulator.class.getName()).log(Level.WARNING, "no Start / Stop Dates found");
        }
    }

    public void setAccumulateCounts(boolean accumulateCounts) {
        this.accumulateCounts = accumulateCounts;
    }

    public void setPropL3Unc(boolean propL3Unc) {
        this.propL3Unc = propL3Unc;
    }

    public boolean hasData (){
        return hasData;
    }

    private int getAodId(String uncVarName){
        int i=0;
        while (i < nAods && !uncNames[i].equals(uncVarName)) i++;
        if (i == nAods) throw new IllegalStateException("error getting aod var name from uncertainty name ("+uncVarName+")");
        String aodName = aodMeanNames[i];
        return getVarId(aodName);
    }
    
    private int getAodSdevId(String uncVarName){
        int i=0;
        while (i < nAods && !uncNames[i].equals(uncVarName)) i++;
        if (i == nAods) throw new IllegalStateException("error getting aod var name from uncertainty name ("+uncVarName+")");
        String aodName = aodSdevNames[i];
        return getVarId(aodName);
    }
    
    private int getUncMeanId(String uncVarName){
        int i=0;
        while (i < nAods && !uncNames[i].equals(uncVarName)) i++;
        if (i == nAods) throw new IllegalStateException("error getting aod var name from uncertainty name ("+uncVarName+")");
        String aodName = uncMeanNames[i];
        return getVarId(aodName);
    }
    
    private int getVarId(String name) {
        for (int i=0; i<nVars; i++){
            if (varNames[i].equals(name)){
                return i;
            }
        }
        return -1;
    }

    private float[] computePropUncert(float[] sigAODmean, float[] stderrAOD, float[] rmsAODsdev, float[] AODmean, int iVar) {
        float[] arr = new float[nCells];
        for (int i=0; i<nCells; i++){
            if (count[i] > 0){
                if (count[i] == 1){
                    stderrAOD[i] = (rmsAODsdev[i] > 0) ? 0.1f*rmsAODsdev[i] : 0.1f*AODmean[i];
                }
                arr[i] = (float)Math.sqrt( Math.pow(sigAODmean[i], 2) + Math.pow(stderrAOD[i], 2) + Math.pow(rmsAODsdev[i], 2) );
            }
            else {
                arr[i] = varNoData[iVar];
            }
        }
        return arr;
    }

    private float[] getStdErr(float[] f) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private float[] getRms(float[] f) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }


}
