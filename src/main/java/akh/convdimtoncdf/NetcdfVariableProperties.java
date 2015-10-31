/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package akh.convdimtoncdf;

import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.nc2.Variable;

/**
 *
 * @author akheckel
 */
public class NetcdfVariableProperties {
    Variable ncV;
    final String varName;
    final String longName;
    final String stdName;
    final String units;
    final DataType dataType;
    final String coords;
    String axis;
    String flagMeanings;
    Array flagValues;
    Array validRange;
    Number lowerRangeLimit;
    Number upperRangeLimit;
    Number fillValue;

    public NetcdfVariableProperties(String varName, String longName, String stdName, String units, DataType datatype, Number lower, Number upper, Number fillValue) {
        this.varName = varName;
        this.longName = longName;
        this.stdName = stdName;
        this.units = units;
        this.dataType = datatype;
        this.validRange = getValidRangeArray(datatype, lower, upper);
        this.lowerRangeLimit = lower;
        this.upperRangeLimit = upper;
        this.fillValue = fillValue;
        this.coords = null;
    }

    public NetcdfVariableProperties(String varName, String longName, String stdName, String units, DataType datatype, Number lower, Number upper, Number fillValue, String coords) {
        this.varName = varName;
        this.longName = longName;
        this.stdName = stdName;
        this.units = units;
        this.dataType = datatype;
        this.validRange = getValidRangeArray(datatype, lower, upper);
        this.lowerRangeLimit = lower;
        this.upperRangeLimit = upper;
        this.fillValue = fillValue;
        this.coords = coords;
    }

    private Array getValidRangeArray(DataType dt, Number low, Number high){
        Array a = null;
        switch (dt){
            case FLOAT: a = Array.factory(new float[]{(float)(low), (float)(high)}); break;
            case INT: a = Array.factory(new int[]{(int)(low), (int)(high)}); break;
            case BYTE: a = Array.factory(new byte[]{(byte)(low), (byte)(high)}); break;
            default: a = null; break;
        }
        return a;
    }

}
