/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package akh.convdimtoncdf;

/**
 *
 * @author akheckel
 */
public enum DataVersionNumbers {
    v1_0("1.0"), v1_1("1.1"), v1_2("1.2"), 
    v2_0("2.0"), v2_1("2.1"), v2_2("2.2"), 
    v3_0("3.0"), v3_1("3.1"), 
    v4_0("4.0"), v4_1("4.1"), v4_2("4.2"), v4_21("4.21"), v4_21u("4.21u"), v4_3("4.3"), v4_31("4.31"), v4_32("4.32"),
    vSyn1_0("Syn1.0"), vSyn1_1("Syn1.1"), vSyn1_2("Syn1.2");
    
    public String versionString;

    DataVersionNumbers(String versionString) {
        this.versionString = versionString;
    }

    public static DataVersionNumbers parseVersionString(String versionString) {
        if (!versionString.startsWith("v")){
            versionString = "v" + versionString;
        }
        return DataVersionNumbers.valueOf(versionString.replaceAll("\\.", "_"));
    }
    
    public boolean isLT(DataVersionNumbers dvn){
        return (this.compareTo(dvn)<0);
    }

    public boolean isLE(DataVersionNumbers dvn){
        return (this.compareTo(dvn)<=0);
    }

    public boolean isGE(DataVersionNumbers dvn){
        return (this.compareTo(dvn)>=0);
    }

    public boolean isGT(DataVersionNumbers dvn){
        return (this.compareTo(dvn)>0);
    }

    @Override
    public String toString() {
        return this.versionString;
    }
}
