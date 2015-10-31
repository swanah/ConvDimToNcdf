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
class SinCell {
    int nBands;
    float bandSums[];
    int count;

    private SinCell(){
    };
    
    SinCell(int nBands) {
        this.nBands = nBands;
        this.bandSums = new float[nBands];
    }
    
    SinCell(float bandValues[]) {
        this.nBands = bandValues.length;
        this.bandSums = new float[nBands];
        System.arraycopy(bandValues, 0, this.bandSums, 0, this.nBands);
        count++;
    }
    
    void addValues(float bandValues[]){
        for (int i=0; i<nBands; i++){
            bandSums[i] += bandValues[i];
        }
        count++;
    }
    
}
