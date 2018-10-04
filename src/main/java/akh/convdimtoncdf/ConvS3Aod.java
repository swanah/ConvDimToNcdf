/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package akh.convdimtoncdf;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import ucar.nc2.Attribute;
import ucar.nc2.NetcdfFile;

/**
 *
 * @author akheckel
 */
public class ConvS3Aod {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ENGLISH);
        ProdSinConverterL2 pSinConvL2 = ProdSinConverterL2.getInstance();
        ProdConverterL3 pConvL3 = ProdConverterL3.getInstance();
        DataGridder gridder = new DataGridder();
        boolean lv2Conv = false;
        

        if (args.length > 2){
            int versionIdx = 0;
            int lv2ConvIdx = 1;
            int firstInputFileIdx = 2;
            S3DataVersionNumbers version = S3DataVersionNumbers.parseVersionString(args[versionIdx]);            
            lv2Conv = args[lv2ConvIdx].equalsIgnoreCase("lv2");
            if (lv2Conv){
                pSinConvL2.initAsS3Ncdf(args[firstInputFileIdx]);
            }
            
            Arrays.sort(args, firstInputFileIdx, args.length);
            
            String l2NetcdfName = getL2NetcdfName(args[firstInputFileIdx], version);
            String l3NetcdfName = getL3NetcdfName(args[firstInputFileIdx], version, lv2Conv);
            for (int i=firstInputFileIdx; i<args.length; i++) {
                String fname = args[i];
                if ( ! new File(fname).exists()) {
                    System.err.println(fname + " not found!");
                    continue;
                }
                try {
                    switch (version){
                        case v1_00:
                        case v1_10:
                        case v1_11:
                        case v1_12:
                        case v1_13:
                        case v1_20:
                        case v1_21:
                        case v1_22:
                            pSinConvL2.convertS3(fname, l2NetcdfName, version);
                            gridder.binNcdfToGridV4(fname, version);
                            break;
                        
                        default: throw new IllegalArgumentException("Data Version: "+version+" not recognized!");
                    }
                    
                } catch (IOException ex){
                    Logger.getLogger(ConvDimToNcdf.class.getName()).log(Level.SEVERE, null, ex);
                } 
                
            }
            
            if (lv2Conv && pSinConvL2.getSinP().nCells > 0){
                System.out.println("writing L2 file: " + l2NetcdfName);
                pSinConvL2.writeS3Ncdf(l2NetcdfName, version);
            }
            
            if (gridder != null && !gridder.isEmpty()){
                System.out.println("writing L3 file: " + l3NetcdfName);
                pConvL3.writeGridsS3(gridder, l3NetcdfName, version);
                System.out.println();
            }
            else {
                Logger.getLogger(ConvDimToNcdf.class.getName()).log(Level.WARNING, "L3 file not created, grids empty!", l3NetcdfName);
            }
        }
        else {
            System.err.println("Require cmd line args: versionString files.dim");
            System.err.print("    versionString can be one of [");
            for (DataVersionNumbers v : DataVersionNumbers.values()) System.err.print(v+", ");
            System.err.printf("\b\b]\n");
        }
    }

    private static String getL2NetcdfName(String fname, S3DataVersionNumbers version) throws IOException {
        //20080101064007-C3S-L2P_AEROSOL-AER_PRODUCTS-SLSTR_SENTINEL-S3A-SU_30520-v1.00.nc
        //S3A_SL_1_RBT____20170702T074914_20170702T075214_20170702T100710_0180_019_249_1619_SVL_O_NR_002_aod.nc 
        File f = new File(fname);
        String stmp = f.getName();
        String yyyymmdd = stmp.substring(16, 16+8);
        String hhmmss = stmp.substring(16+8+1, 16+8+1+6);
        String orbit = getVersionNcdf(fname);
        String instrument = "SLSTR_Sentinel_S3A";
        String l2NetcdfName = yyyymmdd + hhmmss + "-C3S-L2P_AEROSOL-AER_PRODUCTS-" + instrument + "-SU_" + orbit + "-v" + version + ".nc";
        f = new File(f.getParentFile(), l2NetcdfName);
        l2NetcdfName = f.getPath();
        return l2NetcdfName;
    }

    private static String getL3NetcdfName(String fname, S3DataVersionNumbers version, boolean lv3SingleOrbit) {
        //2008010100-ESACCI-L3C_AEROSOL-AOD-AATSR_ENVISAT-ADV_DAILY-v1.40.nc
        //20080101-ESACCI-L3C_AEROSOL-ALL-AATSR_ENVISAT-ORAC-DAILY-fv02.02.nc
        //ATS_TOA_1P  RUPA200801  01_064007_  0000652720  64_00406_3  0520_9956.N1.gz
        File f = new File(fname);
        String stmp = f.getName();
        String yyyymmdd = stmp.substring(16, 16+8);
        String hhmmss = stmp.substring(16+8+1, 16+8+1+6);
        String instrument = "SLSTR_SENTINEL_S3A";
        String l3NetcdfName = yyyymmdd;
        if (lv3SingleOrbit) l3NetcdfName += hhmmss;
        l3NetcdfName += "-C3S-L3C_AEROSOL-AER_PRODUCTS-" + instrument + "-SU_DAILY-v" + version + ".nc";
        f = new File(f.getParentFile(), l3NetcdfName);
        l3NetcdfName = f.getPath();
        return l3NetcdfName;
    }

    private static String getVersionNcdf(String fname) throws IOException {
        NetcdfFile ncFile = NetcdfFile.open(fname);
        
        Attribute orbAtt = ncFile.findAttribute("@abs_orbit");
        String abs_orbit = orbAtt.getNumericValue().toString();
        
        if (ncFile != null) {
            ncFile.close();
        }
        
        return abs_orbit;
    }
    
}
