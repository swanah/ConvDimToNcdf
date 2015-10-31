/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package akh.cloudutil;

/**
 *
 * @author akheckel
 */
public class CloudSceneProperties {
    private String dateS;
    private String orbit;
    private String filename;
    private float latLower;
    private float latUpper;
    private float lonLower;
    private float lonUpper;

    public CloudSceneProperties() {
    }

    public CloudSceneProperties(String filename, float latLower, float latUpper, float lonLower, float lonUpper) {
        this.filename = filename;
        this.latLower = latLower;
        this.latUpper = latUpper;
        this.lonLower = lonLower;
        this.lonUpper = lonUpper;
    }

    public String getDateS() {
        return dateS;
    }

    public void setDateS(String dateS) {
        this.dateS = dateS;
    }

    public String getOrbit() {
        return orbit;
    }

    public void setOrbit(String orbit) {
        this.orbit = orbit;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public float getLatLower() {
        return latLower;
    }

    public void setLatLower(float latLower) {
        this.latLower = latLower;
    }

    public float getLatUpper() {
        return latUpper;
    }

    public void setLatUpper(float latUpper) {
        this.latUpper = latUpper;
    }

    public float getLonLower() {
        return lonLower;
    }

    public void setLonLower(float lonLower) {
        this.lonLower = lonLower;
    }

    public float getLonUpper() {
        return lonUpper;
    }

    public void setLonUpper(float lonUpper) {
        this.lonUpper = lonUpper;
    }

    
    
}
