/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package akh.misc;

import java.io.IOException;
import java.util.ArrayList;
import ucar.ma2.Array;
import ucar.ma2.ArrayFloat;
import ucar.ma2.DataType;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFileWriteable;

/**
 *
 * @author akheckel
 */
public class GmtGridWriter {
    private final NetcdfFileWriteable ncdfFile;
    private final String latVName = "lat";
    private final String lonVName = "lon";
    private final String dataVName = "data";
    private final String countVName = "pixel_count";

    public GmtGridWriter(String fname, SimpleGridder grid) throws IOException {
        this.ncdfFile = createNcdfFile(fname);
    }

    private NetcdfFileWriteable createNcdfFile(String ncdfName) throws IOException {
        
        NetcdfFileWriteable ncfile = NetcdfFileWriteable.createNew(ncdfName, true);
        Dimension latDim = ncfile.addDimension("lat", 180);
        Dimension lonDim = ncfile.addDimension("lon", 360);
        ArrayList<Dimension> dimList = new ArrayList<Dimension>();
        dimList.add(latDim);
        createVcdfVar(ncfile, latVName, "latitude", dimList);
        dimList = new ArrayList<Dimension>();
        dimList.add(lonDim);
        createVcdfVar(ncfile, lonVName, "longitude", dimList);
        dimList = new ArrayList<Dimension>();
        dimList.add(latDim);
        dimList.add(lonDim);
        createGlobalAttrb(ncfile);
        ncfile.create();
        return ncfile;
    }
    
    private static void createGlobalAttrb(NetcdfFileWriteable ncfile) {
        ncfile.addGlobalAttribute("Units", "degree");
        ncfile.addGlobalAttribute("node_offset", 1);
    }

    private static void createVcdfVar(NetcdfFileWriteable ncfile, final String vName, final String longName, ArrayList<Dimension> dimList) {
        String unitStr = "1";
        if (vName.startsWith("lat"))  unitStr = "degrees_north";
        if (vName.startsWith("lon")) unitStr = "degrees_east";
        if (vName.equals("pixel_count")) {
            ncfile.addVariable(vName, DataType.INT, dimList);
        } else {
            ncfile.addVariable(vName, DataType.FLOAT, dimList);
        }
        ncfile.addVariableAttribute(vName, "long_name", longName);
        ncfile.addVariableAttribute(vName, "_FillValue", -999f);
        ncfile.addVariableAttribute(vName, "units", unitStr);
        float[] valRange = new float[]{0f, 999999f};
        if (vName.startsWith("lat")) valRange = new float[]{-90f, 90f};
        if (vName.startsWith("lon")) valRange = new float[]{-180f, 180f};
        if (vName.startsWith("AOD")) valRange = new float[]{0f, 2f};
        Array valRangeArr = ArrayFloat.factory(valRange);
        ncfile.addVariableAttribute(vName, "actual_range", valRangeArr);
        ncfile.addVariableAttribute(vName, "valid_range", valRangeArr);
        ncfile.addVariableAttribute(vName, "scale_factor", 1.0f);
        ncfile.addVariableAttribute(vName, "add_offset", 0.0f);
    }

}
