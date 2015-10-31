/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package akh.misc;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;

/**
 *
 * @author akheckel
 */
public class DumbNetcdf4 {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        String ncName = "ORAC_v302\\L2\\2008\\20080902214528-ESACCI-L2P_AEROSOL-AER_PRODUCTS-AATSR-ENVISAT-ORAC_34036-fv03.02.nc";
        NetcdfFile ncF = null;
        try {
            ncF = NetcdfFile.open(ncName);
            System.out.println("id: "+ncF.getId());
            System.out.println("loc: "+ncF.getLocation());
            System.out.println("title: "+ncF.getTitle());
            List<Dimension> dims = ncF.getDimensions();
            for (int i=0; i<dims.size(); i++){
                System.out.println(dims.get(i).getShortName()+" - "+dims.get(i).getFullName());
            }
            
            
        } catch (IOException ex) {
            Logger.getLogger(DumbNetcdf4.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (ncF != null){
                try {
                    ncF.close();
                } catch (IOException ex) {
                    Logger.getLogger(DumbNetcdf4.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }
    
}
