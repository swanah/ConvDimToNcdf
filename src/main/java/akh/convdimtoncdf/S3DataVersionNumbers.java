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
public enum S3DataVersionNumbers {
    v1_00("1.00"), v1_10("1.10");
    
    public String versionString;

    S3DataVersionNumbers(String versionString) {
        this.versionString = versionString;
    }

    public static S3DataVersionNumbers parseVersionString(String versionString) {
        if (!versionString.startsWith("v")){
            versionString = "v" + versionString;
        }
        return S3DataVersionNumbers.valueOf(versionString.replaceAll("\\.", "_"));
    }
    
    public boolean isLT(S3DataVersionNumbers dvn){
        return (this.compareTo(dvn)<0);
    }

    public boolean isLE(S3DataVersionNumbers dvn){
        return (this.compareTo(dvn)<=0);
    }

    public boolean isGE(S3DataVersionNumbers dvn){
        return (this.compareTo(dvn)>=0);
    }

    public boolean isGT(S3DataVersionNumbers dvn){
        return (this.compareTo(dvn)>0);
    }

    @Override
    public String toString() {
        return this.versionString;
    }
}
