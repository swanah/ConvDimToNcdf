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
    public Variable ncV;
    public final String varName;
    public final String longName;
    public final String stdName;
    public final String units;
    public final DataType dataType;
    public final String coords;
    public String axis;
    public String flagMeanings;
    public Array flagValues;
    public Array validRange;
    public Number lowerRangeLimit;
    public Number upperRangeLimit;
    public Number fillValue;
    public String comment;
    public String wavelength;

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
        this.comment = null;
        this.wavelength = null;
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
        this.comment = null;
        this.wavelength = null;
    }

    public NetcdfVariableProperties(String varName, String longName, String stdName, String units, DataType datatype, Number lower, Number upper, Number fillValue, String coords, String comment, String wavelength) {
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
        this.comment = comment;
        this.wavelength = wavelength;
    }

    private Array getValidRangeArray(DataType dt, Number low, Number high){
        Array a = null;
        switch (dt){
            case FLOAT: a = Array.factory(new float[]{(Float)(low), (Float)(high)}); break;
            case INT: a = Array.factory(new int[]{(Integer)(low), (Integer)(high)}); break;
            case BYTE: a = Array.factory(new byte[]{(Byte)(low), (Byte)(high)}); break;
            default: a = null; break;
        }
        return a;
    }

}
