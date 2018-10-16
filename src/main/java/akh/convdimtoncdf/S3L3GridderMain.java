/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package akh.convdimtoncdf;

import akh.findfilerecursive.FindFileRecursive;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import ucar.nc2.NetcdfFile;

/**
 *
 * @author akheckel
 */
public class S3L3GridderMain {

    static String inPath = ".";
    static String instrument = "SLSTR_SENTINEL_S3A";
    final static String prod = "AER_PRODUCTS";
    static String instTime = "SU_DAILY";
    static S3DataVersionNumbers version;
    static int year, month, day;

    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        int argOff=0;
        String yyyymmdd;
        switch (args.length){
            case 2:
                version = S3DataVersionNumbers.parseVersionString(args[argOff]);
                yyyymmdd = args[argOff+1];
                break;
            default:
                throw new IllegalArgumentException("please specify date string yyyymmdd");
        }
        BinPeroid binPeroid;
        switch (yyyymmdd.length()){
            case 8: 
                binPeroid = BinPeroid.DAILY;
                year  = Integer.parseInt(yyyymmdd.substring(0, 4));
                month = Integer.parseInt(yyyymmdd.substring(4, 6));
                day   = Integer.parseInt(yyyymmdd.substring(6, 8));
                break;
            case 6:
                binPeroid = BinPeroid.MONTHLY;
                year  = Integer.parseInt(yyyymmdd.substring(0, 4));
                month = Integer.parseInt(yyyymmdd.substring(4, 6));
                break;
            case 4:
                binPeroid = BinPeroid.ANNUAL;
                year  = Integer.parseInt(yyyymmdd.substring(0, 4));
                break;
            default:
                throw new IllegalArgumentException("date string on cmd line has illegal length (" + args.length + ") !");
        }                    

        //File outPath = new File(String.format("%s/L3_MONTHLY/%04d/%02d", inPath, year, month));
        //if (!outPath.exists()){
        //    outPath.mkdirs();
        //}
        //inPath += String.format("/%04d/%02d", year, month);
        File outPath = new File(inPath);
        System.out.printf("processing %s - %s - %s\n", yyyymmdd, version, binPeroid);
        String outName = null;
        L3Accumulator l3Acc = new L3Accumulator();
        switch (binPeroid){
            case DAILY: 
                accumulateDaily(l3Acc);
                l3Acc.normVari2StdErr();
                outName = String.format("%s/%04d%02d%02d-C3S-L3C_AEROSOL-%s-%s-%s-v%s.nc", outPath.getPath(), year, month, day, prod, instrument, instTime, version);
                break;
            case MONTHLY: 
                accumulateMonthly(l3Acc);
                l3Acc.normVari2StdErr();
                outName = String.format("%s/%04d%02d-C3S-L3C_AEROSOL-%s-%s-%s-v%s.nc", outPath.getPath(), year, month, prod, instrument, instTime.replaceFirst("DAILY", "MONTHLY"), version);
                break;
            case ANNUAL: 
                instTime = "SU_MONTHLY";
                accumulateAnnual(l3Acc);
                l3Acc.normVari2StdErr();
                outName = String.format("%s/%04d-C3S-L3C_AEROSOL-%s-%s-%s-v%s.nc", outPath.getPath(), year, prod, instrument, instTime.replaceFirst("MONTHLY", "ANNUAL"), version);
                break;
            default:
                throw new IllegalArgumentException("binPeriod undefined");
        }
        if (l3Acc.hasData()){
            //l3Acc.normalize();
            l3Acc.writeGrids(outName);
        }
        else {
            System.out.println("no data found for "+yyyymmdd);
        }
    }

    private static int getMaxDays(int year, int month) {
        int[] days = {0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        return (month==2 && (year%4)==0)?days[month]+1:days[month];
    }

    private static void accumulateDaily(L3Accumulator l3Acc) {
        l3Acc.setDoDaily(true);
        FilenameFilter filenameFilter = new FilenameFilter() {
            String regExpr = String.format("%04d%02d%02d\\d{6}-C3S-L3C_AEROSOL-%s-%s-%s-v%s.nc", year, month, day, prod, instrument, instTime, version);
            @Override
            public boolean accept(File dir, String fname) {
                return (fname.matches(regExpr));
            }
        };
        List<File> fileList = new FindFileRecursive().listFiles(inPath, filenameFilter, false);
        for (File f : fileList){
            System.out.println(f.getPath()+" "+ f.exists());
            if (f.exists()) {
                l3Acc.add(f);
            }
        }
    }

    private static void accumulateMonthly(L3Accumulator l3Acc) {
        l3Acc.setAccumulateCounts(false);
        l3Acc.setPropL3Unc(true);
        String fname;
        File f = null;
        int mlen = getMaxDays(year, month);
        for (int day=1; day<=mlen; day++){
            fname = String.format("%04d%02d%02d-C3S-L3C_AEROSOL-%s-%s-%s-v%s.nc", year, month, day, prod, instrument, instTime, version);
            f = new File(inPath, fname);
            System.out.println(f.getPath()+" "+ f.exists());
            if (f.exists()) {
                
                System.err.println(f.getPath());
                try {
                    NetcdfFile ncF = NetcdfFile.open(f.getPath());
                } catch (IOException ex) {
                    Logger.getLogger(S3L3GridderMain.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
                }
                
                l3Acc.add(f);
            }
        }
    }

    private static void accumulateAnnual(L3Accumulator l3Acc) {
        l3Acc.setAccumulateCounts(false);
        l3Acc.setPropL3Unc(true);
        String fname;
        File f = null;
        for (int imon=1; imon<=12; imon++){
            fname = String.format("%04d%02d-C3S-L3C_AEROSOL-%s-%s-%s-v%s.nc", year, imon, prod, instrument, instTime, version);
            f = new File(inPath, fname);
            System.out.println(f.getPath()+" "+ f.exists());
            if (f.exists()) {
                
                System.err.println(f.getPath());
                try {
                    NetcdfFile ncF = NetcdfFile.open(f.getPath());
                } catch (IOException ex) {
                    Logger.getLogger(S3L3GridderMain.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
                }
                
                l3Acc.add(f);
            }
        }
    }
  
    
    
    enum BinPeroid {
        DAILY, MONTHLY, ANNUAL
    }

}
