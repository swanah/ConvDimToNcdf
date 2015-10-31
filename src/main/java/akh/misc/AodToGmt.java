/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package akh.misc;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Product;

/**
 *
 * @author akheckel
 */
public class AodToGmt {
    public static final String bandName = "aot_nd_1";
    
    public static void main(String[] args){
        
        SimpleGridder aodGrid = new SimpleGridder(0.125, 0.125);
        Product p = null;
        try {
            p = ProductIO.readProduct(args[0]);
            if (p != null){
                if (p.containsBand(bandName)){
                    aodGrid.addGrid(p, bandName);
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(AodToGmt.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (p!=null) p.dispose();
        }
        
        try {
            new GmtGridWriter(args[0].replace(".dim", ".grd"), aodGrid);
        } catch (IOException ex) {
            Logger.getLogger(AodToGmt.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
