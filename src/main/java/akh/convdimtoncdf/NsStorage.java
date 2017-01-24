/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package akh.convdimtoncdf;

import java.io.PrintStream;
import java.lang.reflect.Field;

/**
 *
 * @author akheckel
 */
class NsStorage {
    
    interface ValueGetter<T extends Number> {
        T get(NsStorage src);
    }
    
    float lat, lon, doy2000;
    float aod550, varAod550, uncAod550;
    float aod865, varAod865, uncAod865;
    float fm550, varFm550;
    float pixSize;
    int pixCount;
    

    NsStorage(float lat, float lon, float doy2000) {
        this.lat = lat;
        this.lon = lon;
        this.doy2000 = doy2000;
        this.pixCount = 0;
        this.pixSize = 100; // 10x10km²
    }
    
    void addPixel(float aod550, float uncAod550, float aod865, float uncAod865, float fm550){
        pixCount++;
        float delta = aod550 - this.aod550;
        this.aod550 += delta / pixCount;
        this.varAod550 += delta * (aod550 - this.aod550);
        delta = uncAod550 - this.uncAod550;
        this.uncAod550 += delta / pixCount;

        delta = aod865 - this.aod865;
        this.aod865 += delta / pixCount;
        this.varAod865 += delta * (aod865 - this.aod865);
        delta = uncAod865 - this.uncAod865;
        this.uncAod865 += delta / pixCount;
        
        delta = fm550 - this.fm550;
        this.fm550 += delta / pixCount;
        this.varFm550 += delta * (fm550 - this.fm550);
        
    }
    
    void printCurrentState(PrintStream s) {
        int i=0;
        Class<? extends NsStorage> c = this.getClass();
        Field[] fields = c.getDeclaredFields();
        try {            
            for (Field f : fields) {
                s.print(f.getName() + " : " + f.get(this) + "   ");
                if ((++i) % 3 == 0) s.println();
            }
        } catch (IllegalAccessException ex) {
        } catch (IllegalArgumentException ex) {
        }
        s.println();
    }
}
