/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package akh.cloudutil;

import static akh.cloudutil.ExtractCloudMask.getGeoRec;
import static akh.cloudutil.ExtractCloudMask.readSceneFromResource;
import static akh.cloudutil.ExtractCloudMask.setSubsetStartStopTime;
import com.bc.ceres.core.Assert;
import java.awt.Color;
import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.dataio.ProductSubsetBuilder;
import org.esa.beam.framework.dataio.ProductSubsetDef;
import org.esa.beam.framework.dataio.ProductWriter;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.Mask;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.TiePointGrid;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

/**
 *
 * @author akheckel
 */
public class CombineCloudScene {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Locale.setDefault(Locale.ENGLISH);
        ArrayList<CloudSceneProperties> cloudSceneList = readSceneFromResource("cloudscenes.par");
        if (cloudSceneList == null || cloudSceneList.isEmpty()) {
            Logger.getLogger(ExtractCloudMask.class.getName()).log(Level.WARNING, "Scene List is empty!");
            return;
        }

        for (int iScene = 0; iScene < 8; iScene++) {

            String sceneName = String.format("%02d", iScene+1);
            String cldName = "Aerosol-cci-PM2-refcloud/" + sceneName + ".HDF";
            String atsName = cloudSceneList.get(iScene).getFilename();
            String outName = "Aerosol-cci-PM2-refcloud/" + sceneName + ".dim";
            System.out.println(sceneName);
                    
            DataPointList cloudPoints = getCloudPoints(cldName);
            Assert.notNull(cloudPoints);
            Assert.state(cloudPoints.points.size() > 0);

            Product p = null;
            Product subsetP = null;
            try {

                p = ProductIO.readProduct(atsName);
                Assert.notNull(p);

                Rectangle region = getGeoRec(p, cloudPoints.latMin, cloudPoints.latMax, -180, 180);
                ArrayList<String> bandNames = new ArrayList<String>();
                for (String bname : p.getBandNames()) {
                    if (bname.contains("flags")
                            || bname.startsWith("reflec_nadir")
                            || bname.startsWith("reflec_fward")) {
                        bandNames.add(bname);
                    }
                }
                ProductSubsetBuilder subsetter = new ProductSubsetBuilder();
                ProductSubsetDef subsetDef = new ProductSubsetDef();
                subsetDef.addNodeNames(p.getTiePointGridNames());
                subsetDef.addNodeNames((String[]) bandNames.toArray(new String[]{}));
                subsetDef.addNodeNames(p.getMetadataRoot().getElementNames());
                subsetDef.addNodeNames(p.getMetadataRoot().getAttributeNames());
                subsetDef.setRegion(region);
                subsetDef.setSubSampling(1, 1);
                subsetDef.setSubsetName(sceneName);
                subsetDef.setIgnoreMetadata(false);

                subsetP = subsetter.readProductNodes(p, subsetDef);
                setSubsetStartStopTime(p, subsetP, region);
                int rasterWidth = subsetP.getSceneRasterWidth();
                int rasterHeight = subsetP.getSceneRasterHeight();
                FlagCoding refFC = new FlagCoding("refCld");
                refFC.addFlag("refPixel", 1, "reference pixel");
                refFC.addFlag("refPixCld", 2, "reference pixel cloudy");
                subsetP.getFlagCodingGroup().add(refFC);
                Mask m = Mask.BandMathsType.create("refCldMask", null, rasterWidth, rasterHeight, "refCld.refPixCld", Color.RED, 0);
                subsetP.getMaskGroup().add(m);
                Band refCldBand = subsetP.addBand("refCld", ProductData.TYPE_INT8);
                refCldBand.setSampleCoding(refFC);

                String s;
                for (Band b : subsetP.getBands()) {
                    s = b.getValidMaskExpression();
                    if (s != null) {
                        b.setValidPixelExpression("(" + s + ")&&(refCld>0)");
                    }
                    else {
                        b.setValidPixelExpression("refCld>0");
                    }
                }
                TiePointGrid seaN = subsetP.getTiePointGrid("sun_elev_nadir");
                seaN.readRasterDataFully();
                TiePointGrid seaF = subsetP.getTiePointGrid("sun_elev_fward");
                seaF.readRasterDataFully();
                //TiePointGrid corrLatTpg = corrTpg(subsetP, "latitude","lat_corr_nadir", "latC");
                //TiePointGrid corrLonTpg = corrTpg(subsetP, "longitude","lon_corr_nadir", "lonC");
                //TiePointGeoCoding corrGC = new TiePointGeoCoding(corrLatTpg, corrLonTpg);
                //subsetP.addTiePointGrid(corrLatTpg);
                //subsetP.addTiePointGrid(corrLonTpg);
                //subsetP.setGeoCoding(corrGC);
                ProductWriter pw = ProductIO.getProductWriter("BEAM-DIMAP");
                pw.writeProductNodes(subsetP, outName);
                
                float line[] = new float[rasterWidth];
                for (Band b : subsetP.getBands()) {
                    if (b.getName().startsWith("reflec_nadir")) {
                        for (int y = 0; y < rasterHeight; y++) {
                            b.readPixels(0, y, rasterWidth, 1, line);
                            for (int x=0; x<rasterWidth; x++ ) {
                                line[x] /= ((float)Math.cos(Math.toRadians(90f-seaN.getPixelFloat(x, y))));
                            }
                            b.writePixels(0, y, rasterWidth, 1, line);
                        }
                    }
                    else if (b.getName().startsWith("reflec_fward")) {
                        for (int y = 0; y < rasterHeight; y++) {
                            b.readPixels(0, y, rasterWidth, 1, line);
                            for (int x=0; x<rasterWidth; x++ ) {
                                line[x] /= ((float)Math.cos(Math.toRadians(90f-seaF.getPixelFloat(x, y))));
                            }
                            b.writePixels(0, y, rasterWidth, 1, line);
                        }
                    }
                }

                
                byte refCldData[] = new byte[rasterHeight * rasterWidth];

                GeoCoding gc = subsetP.getGeoCoding();
                PixelPos pP = new PixelPos();

                for (DataPoint dp : cloudPoints.points) {
                    pP = gc.getPixelPos(dp.gp, pP);
                    refCldData[(int) (pP.x) + rasterWidth * (int) (pP.y)] = dp.val;
                }

                refCldBand.setRasterData(ProductData.createInstance(ProductData.TYPE_INT8, refCldData));
                refCldBand.writeRasterDataFully();

            } catch (IOException ex) {
                Logger.getLogger(CombineCloudScene.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                if (p != null) {
                    try {
                        p.closeIO();
                    } catch (IOException ex) {
                        Logger.getLogger(CombineCloudScene.class.getName()).log(Level.SEVERE, null, ex);
                    } finally {
                        p.dispose();
                    }
                }
                if (subsetP != null) {
                    try {
                        subsetP.closeIO();
                    } catch (IOException ex) {
                        Logger.getLogger(CombineCloudScene.class.getName()).log(Level.SEVERE, null, ex);
                    } finally {
                        subsetP.dispose();
                    }
                }
            }
        }
    }

    private static DataPointList getCloudPoints(String cldName) {
        NetcdfFile ncFile = null;
        DataPointList cldPointList = null;

        try {
            ncFile = NetcdfFile.open(cldName);

            Variable lonVar = ncFile.findVariable("longitude");
            Variable latVar = ncFile.findVariable("latitude");
            Variable cldVar = ncFile.findVariable("cloud_mask");

            int ny = cldVar.getDimension(0).getLength();
            int nx = cldVar.getDimension(1).getLength();
            int n = nx * ny;
            float latNan = latVar.findAttribute("_FillValue").getNumericValue().floatValue();
            float lonNan = lonVar.findAttribute("_FillValue").getNumericValue().floatValue();
            short cldNan = cldVar.findAttribute("_FillValue").getNumericValue().shortValue();

            float lonArr[] = (float[]) lonVar.read().copyTo1DJavaArray();
            float latArr[] = (float[]) latVar.read().copyTo1DJavaArray();
            short cldArr[] = (short[]) cldVar.read().copyTo1DJavaArray();

            Assert.state((lonArr.length == n), String.format("length of lonArr not equal to n(%d)", n));
            Assert.state((lonArr.length == n), String.format("length of latArr not equal to n(%d)", n));
            Assert.state((lonArr.length == n), String.format("length of cldArr not equal to n(%d)", n));

            cldPointList = new DataPointList(n);

            for (int i = 0; i < n; i++) {
                boolean valid = !(Float.isNaN(latArr[i]) || Float.compare(latArr[i], latNan) == 0 || Float.compare(latArr[i], 0) == 0);
                valid = valid && !(Float.isNaN(lonArr[i]) || Float.compare(lonArr[i], lonNan) == 0 || Float.compare(lonArr[i], 0) == 0);
                valid = valid && !(cldArr[i] == cldNan);
                if (valid) {
                    cldPointList.add(new GeoPos(latArr[i], lonArr[i]), (byte) (cldArr[i] + 1));
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(CombineCloudScene.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (ncFile != null) {
                try {
                    ncFile.close();
                } catch (IOException ex) {
                    Logger.getLogger(CombineCloudScene.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        return cldPointList;
    }

    private static TiePointGrid corrTpg(Product p, String tpgName, String tpgCName, String newName) throws IOException {
        TiePointGrid tpg = p.getTiePointGrid(tpgName);
        tpg.readRasterDataFully();
        //float data[] = new float[tpg.getRasterWidth()*tpg.getRasterHeight()];
        float[] data = (float[]) tpg.getRasterData().getElems();
        TiePointGrid tpgC = p.getTiePointGrid(tpgCName);
        tpgC.readRasterDataFully();
        //float latArr[] = new float[lat.getRasterWidth()*lat.getRasterHeight()];
        float[] dataC = (float[]) tpgC.getRasterData().getElems();
        for (int i=0; i<data.length; i++) data[i] -= dataC[i];
        TiePointGrid dummyTpg = new TiePointGrid(newName, tpg.getRasterWidth(), tpg.getRasterHeight(), 
                tpg.getOffsetX(), tpg.getOffsetY(), tpg.getSubSamplingX(), tpg.getSubSamplingY(), data);
        tpg.setSourceImage(dummyTpg.getSourceImage());
        return dummyTpg;
        //tpg.getRasterData().setElems(data);
        //tpg.writeRasterDataFully();
    }

    private static class DataPoint {

        GeoPos gp;
        byte val;

        public DataPoint(GeoPos gp, byte val) {
            this.gp = gp;
            this.val = val;
        }
    }

    private static class DataPointList {

        ArrayList<DataPoint> points;
        float latMin = 99999;
        float latMax = -99999;

        public DataPointList(int n) {
            points = new ArrayList<DataPoint>(n);
        }

        public void add(GeoPos gp, byte val) {
            points.add(new DataPoint(gp, val));
            latMin = Math.min(latMin, gp.lat);
            latMax = Math.max(latMax, gp.lat);
        }
    }
}
