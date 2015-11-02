/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package akh.convdimtoncdf;

import akh.findfilerecursive.FindFileRecursive;
import java.io.File;
import java.io.FilenameFilter;
import java.util.List;

/**
 *
 * @author akheckel
 */
public class L3GridderMain {

    static String inPath = ".";
    static String instrument = "AATSR_ENVISAT";
    final static String prod = "AER_PRODUCTS";
    static String instTime = "SU_DAILY";
    static DataVersionNumbers version;
    static int year, month, day;

    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        int argOff=0;
        String yyyymmdd;
        switch (args.length){
            case 3:
                switch (args[argOff].toUpperCase()){
                    case "AATSR": instrument = "AATSR_ENVISAT"; break;
                    case "ATSR2": instrument = "ATSR2_ERS2"; break;
                }
                argOff++;
            case 2:
                version = DataVersionNumbers.parseVersionString(args[argOff]);
                if (version.equals(DataVersionNumbers.vSyn1_0)){
                    instrument = "MERIS-AATSR-SYN_ENVISAT";
                }
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
                outName = String.format("%s/%04d%02d%02d-ESACCI-L3C_AEROSOL-%s-%s-%s-v%s.nc", outPath.getPath(), year, month, day, prod, instrument, instTime, version);
                break;
            case MONTHLY: 
                accumulateMonthly(l3Acc);
                l3Acc.normVari2StdErr();
                outName = String.format("%s/%04d%02d-ESACCI-L3C_AEROSOL-%s-%s-%s-v%s.nc", outPath.getPath(), year, month, prod, instrument, instTime.replaceFirst("DAILY", "MONTHLY"), version);
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
        l3Acc.setAccumulateCounts(true);
        l3Acc.setPropL3Unc(false);
        FilenameFilter filenameFilter = new FilenameFilter() {
            String regExpr = String.format("%04d%02d%02d\\d{6}-ESACCI-L3C_AEROSOL-%s-%s-%s-v%s.nc", year, month, day, prod, instrument, instTime, version);
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
            fname = String.format("%04d%02d%02d-ESACCI-L3C_AEROSOL-%s-%s-%s-v%s.nc", year, month, day, prod, instrument, instTime, version);
            f = new File(inPath, fname);
            System.out.println(f.getPath()+" "+ f.exists());
            if (f.exists()) {
                l3Acc.add(f);
            }
        }
    }
  
    
    
    enum BinPeroid {
        DAILY, MONTHLY, ANNUAL
    }

}
