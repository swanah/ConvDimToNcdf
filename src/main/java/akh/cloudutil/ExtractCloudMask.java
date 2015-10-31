/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package akh.cloudutil;

import akh.convdimtoncdf.ConvDimToNcdf;
import akh.convdimtoncdf.ProdConverterL2;
import akh.findfilerecursive.FindFileRecursive;
import java.awt.Rectangle;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.dataio.ProductSubsetBuilder;
import org.esa.beam.framework.dataio.ProductSubsetDef;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.datamodel.RasterDataNode;
import org.esa.beam.framework.datamodel.TiePointGrid;
import org.esa.beam.util.BitSetter;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFileWriter;
import ucar.nc2.Variable;

/**
 *
 * @author akheckel
 */
public class ExtractCloudMask {

    private static final int aatsrLineStep = 10;

    public static void main(String[] args) throws InvalidRangeException {
        boolean isTarFile;

        ArrayList<CloudSceneProperties> cloudSceneList = readSceneFromResource("cloudscenes.par");
        if (cloudSceneList == null || cloudSceneList.isEmpty()) {
            Logger.getLogger(ExtractCloudMask.class.getName()).log(Level.WARNING, "Scene List is empty!");
            return;
        }

        for (CloudSceneProperties scene : cloudSceneList) {
            String fname = scene.getFilename();
            String cldNetcdfName = fname.replaceFirst("\\..*", "_cldMask.nc");
            System.err.printf("%s\n : (%6.2f .. %6.2f N / %7.2f .. %7.2f E)\n", scene.getFilename(), scene.getLatLower(), scene.getLatUpper(), scene.getLonLower(), scene.getLonUpper());

            isTarFile = isTarGzFile(fname);
            if (isTarFile) {
                System.err.println("extracting....");
                fname = extractProduct(fname);
            }
            if (!new File(fname).exists()) {
                System.err.println(fname + " not found!");
                continue;
            }
            Product p = null;
            Product subsetP = null;
            try {
                p = ProductIO.readProduct(fname);
                Rectangle region = getGeoRec(p, scene.getLatLower(), scene.getLatUpper(),
                        scene.getLonLower(), scene.getLonUpper());
                
                if (region.isEmpty()){
                    System.err.println("region empty... skipping file");
                    continue;
                }

                ProductSubsetBuilder subsetter = new ProductSubsetBuilder();
                ProductSubsetDef subsetDef = new ProductSubsetDef();
                subsetDef.addNodeNames(p.getTiePointGridNames());
                subsetDef.addNodeNames(p.getBandNames());
                subsetDef.addNodeNames(p.getMetadataRoot().getElementNames());
                subsetDef.addNodeNames(p.getMetadataRoot().getAttributeNames());
                subsetDef.setRegion(region);
                subsetDef.setSubSampling(1, 1);
                subsetDef.setSubsetName(p.getName());
                subsetDef.setIgnoreMetadata(false);

                subsetP = subsetter.readProductNodes(p, subsetDef);
                setSubsetStartStopTime(p, subsetP, region);
                int rasterWidth = subsetP.getSceneRasterWidth();
                int rasterHeight = subsetP.getSceneRasterHeight();

                //NetcdfFileWriteable ncfile = NetcdfFileWriteable.createNew(cldNetcdfName, false);
                NetcdfFileWriter ncfile = NetcdfFileWriter.createNew(NetcdfFileWriter.Version.netcdf4, cldNetcdfName);
                Dimension xDim = ncfile.addDimension(null, "image_column", rasterWidth);
                Dimension yDim = ncfile.addDimension(null, "image_row", rasterHeight);

                ArrayList<Dimension> dimList = new ArrayList<Dimension>();
                dimList.add(xDim);
                Variable imgColVar = createNetCdfVar(ncfile, "image_column", DataType.SHORT, dimList,
                        "projection_x_coordinate",
                        "AATSR across track pixel number",
                        null,
                        Array.factory(new int[]{1, 512}),
                        Short.MIN_VALUE);

                dimList.clear();
                dimList.add(yDim);
                Variable imgRowVar = createNetCdfVar(ncfile, "image_row", DataType.INT, dimList,
                        "projection_y_coordinate",
                        "AATSR along track pixel number",
                        null,
                        Array.factory(new int[]{1, 45000}),
                        (int) Short.MIN_VALUE);

                dimList.add(xDim);
                Variable latVar = createNetCdfVar(ncfile, "lat", DataType.FLOAT, dimList,
                        "latitude",
                        "latitude",
                        "degrees_north",
                        Array.factory(new float[]{-90f, 90f}),
                        -999f);

                Variable lonVar = createNetCdfVar(ncfile, "lon", DataType.FLOAT, dimList,
                        "longitude",
                        "longitude",
                        "degrees_east",
                        Array.factory(new float[]{-180f, 180f}),
                        -999f);

                Variable seaLandFlagVar = createNetCdfFlagVar(ncfile, "surface_type_number", DataType.BYTE, dimList,
                        "land_binary_mask",
                        "Sea/Land flag",
                        "water land",
                        Array.factory(new byte[]{0, 1}),
                        (byte) -1);

                Variable cloudFlagVar = createNetCdfFlagVar(ncfile, "cloud_flag", DataType.BYTE, dimList,
                        "cloud_binary_mask",
                        "final cloud flag",
                        "clear cloud",
                        Array.factory(new byte[]{0, 1}),
                        (byte) -1);

                Variable dustFlagVar = createNetCdfFlagVar(ncfile, "dust_flag", DataType.BYTE, dimList,
                        "dust_binary_mask",
                        "dust over ocean",
                        "not-dust dust",
                        Array.factory(new byte[]{0, 1}),
                        (byte) -1);

                Variable timeVar = createNetCdfVar(ncfile, "time", DataType.LONG, dimList,
                        "time",
                        "TAI70 time",
                        "seconds since 1970-01-01 00:00:00 UTC",
                        Array.factory(new long[]{0}),
                        0L);

                //Variable refl550NVar = createNetCdfVar(ncfile, "reflec_nadir_0550", DataType.FLOAT, dimList,
                //        "",
                //        "AATSR TOA Nadir reflectance at 550nm",
                //        "1",
                //        Array.factory(new float[]{0f, 1f}),
                //        -1f);

                //Variable refl550FVar = createNetCdfVar(ncfile, "reflec_fward_0550", DataType.FLOAT, dimList,
                //        "",
                //        "AATSR TOA Fward reflectance at 550nm",
                //        "1",
                //        Array.factory(new float[]{0f, 1f}),
                //        -1f);

                final SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.ENGLISH);
                df.setTimeZone(TimeZone.getTimeZone("UTC"));
                final String currentTime = df.format(new Date());
                ncfile.addGroupAttribute(null, new Attribute("date_created", currentTime));
                ncfile.addGroupAttribute(null, new Attribute("creator_name", "Swansea University"));
                ncfile.addGroupAttribute(null, new Attribute("creator_email", "a.heckel@swansea.ac.uk"));
                ncfile.addGroupAttribute(null, new Attribute("title", "SU AARDVARC cloud mask for Aerosol CCI"));
                ncfile.addGroupAttribute(null, new Attribute("summary", "This file contains cloud mask imagery from SU Aardvarc aerosol_cci processing chain, version 4.2"));
                ncfile.addGroupAttribute(null, new Attribute("start_date", subsetP.getStartTime().format()));
                ncfile.addGroupAttribute(null, new Attribute("stop_date", subsetP.getEndTime().format()));
                ncfile.addGroupAttribute(null, new Attribute("latitude_lower_bound", scene.getLatLower()));
                ncfile.addGroupAttribute(null, new Attribute("latitude_upper_bound", scene.getLatUpper()));
                ncfile.addGroupAttribute(null, new Attribute("longitude_lower_bound", scene.getLonLower()));
                ncfile.addGroupAttribute(null, new Attribute("longitude_upper_bound", scene.getLonUpper()));

                ncfile.create();

                short[] imgCol = new short[rasterWidth];
                int[] imgRow = new int[rasterHeight];
                long[][] time = new long[rasterHeight][rasterWidth];
                float[][] lat = new float[rasterHeight][rasterWidth];
                float[][] lon = new float[rasterHeight][rasterWidth];
                byte[][] cloudFlag = new byte[rasterHeight][rasterWidth];
                byte[][] seaLandFlag = new byte[rasterHeight][rasterWidth];
                byte[][] dustFlag = new byte[rasterHeight][rasterWidth];
                //float[][] refl550N = new float[rasterHeight][rasterWidth];
                //float[][] refl550F = new float[rasterHeight][rasterWidth];
                

                final ProdConverterL2 pConvL2 = ProdConverterL2.getInstance();
                long startMilliSec1970 = pConvL2.utcToMilliSec1970(p.getStartTime());
                long endSec1970 = pConvL2.utcToMilliSec1970(p.getEndTime());
                long millisecPerLine = (endSec1970 - startMilliSec1970) / rasterHeight;

                TiePointGrid latB = subsetP.getTiePointGrid("latitude");
                latB.readRasterDataFully();
                TiePointGrid lonB = subsetP.getTiePointGrid("longitude");
                lonB.readRasterDataFully();
                Band apolloB = subsetP.getBand("cloud_flags_apollo");
                apolloB.readRasterDataFully();
                Band fmiNB = subsetP.getBand("cloud_flags_fmi_nad");
                fmiNB.readRasterDataFully();
                Band fmiFB = subsetP.getBand("cloud_flags_fmi_fwd");
                fmiFB.readRasterDataFully();
                Band cfnB = subsetP.getBand("cloud_flags_nadir");
                cfnB.readRasterDataFully();
                Band cffB = subsetP.getBand("cloud_flags_fward");
                cffB.readRasterDataFully();
                //Band refl550NB = subsetP.getBand("reflec_nadir_0550");
                //refl550NB.readRasterDataFully();
                //Band refl550FB = subsetP.getBand("reflec_fward_0550");
                //refl550FB.readRasterDataFully();

                boolean land;
                long pixSec1970 = 0;
                for (int iy = 0; iy < rasterHeight; iy++) {
                    //if ((iy % 100) == 0) {
                    //    System.err.printf("processing %5.1f%%                                        \r", (float) (iy) / rasterHeight * 100);
                    //}
                    pixSec1970 = (int) ((startMilliSec1970 + millisecPerLine * iy) / 1000);
                    for (int ix = 0; ix < rasterWidth; ix++) {
                        cloudFlag[iy][ix] = (byte) (1 - apolloB.getPixelInt(ix, iy));
                        land = BitSetter.isFlagSet(cfnB.getPixelInt(ix, iy), 0);
                        land = land && BitSetter.isFlagSet(cffB.getPixelInt(ix, iy), 0);
                        if (land && (cloudFlag[iy][ix] == 1)
                                && (fmiNB.getPixelInt(ix, iy) == 0)
                                && (fmiFB.getPixelInt(ix, iy) == 0)) {

                            cloudFlag[iy][ix] = 0;
                        }
                        dustFlag[iy][ix] = 0;
                        seaLandFlag[iy][ix] = (byte) ((land) ? 1 : 0);
                        lat[iy][ix] = (float) latB.getPixelDouble(ix, iy);
                        lon[iy][ix] = (float) lonB.getPixelDouble(ix, iy);
                        time[iy][ix] = pixSec1970;
                        //refl550N[iy][ix] = refl550NB.getSampleFloat(ix, iy)/100f;
                        //refl550F[iy][ix] = refl550FB.getSampleFloat(ix, iy)/100f;
                    }
                }

                for (int i = 0; i < imgCol.length; i++) {
                    imgCol[i] = (short) i;
                }
                for (int i = 0; i < imgRow.length; i++) {
                    imgRow[i] = (short) i;
                }
                Array dataArray = Array.factory(imgCol);
                ncfile.write(imgColVar, dataArray);
                dataArray = Array.factory(imgRow);
                ncfile.write(imgRowVar, dataArray);
                dataArray = Array.factory(lat);
                ncfile.write(latVar, dataArray);
                dataArray = Array.factory(lon);
                ncfile.write(lonVar, dataArray);
                dataArray = Array.factory(time);
                ncfile.write(timeVar, dataArray);
                dataArray = Array.factory(cloudFlag);
                ncfile.write(cloudFlagVar, dataArray);
                dataArray = Array.factory(dustFlag);
                ncfile.write(dustFlagVar, dataArray);
                dataArray = Array.factory(seaLandFlag);
                ncfile.write(seaLandFlagVar, dataArray);
                //dataArray = Array.factory(refl550N);
                //ncfile.write(refl550NVar, dataArray);
                //dataArray = Array.factory(refl550F);
                //ncfile.write(refl550FVar, dataArray);

                ncfile.close();
                imgCol = null;
                imgRow = null;
                time = null;
                lat = null;
                lon = null;
                cloudFlag = null;
                dustFlag = null;
                seaLandFlag = null;
                dataArray = null;
                System.err.printf("processing done                                        \r\n");
            } catch (InvalidRangeException ex) {
                Logger.getLogger(ExtractCloudMask.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(ExtractCloudMask.class.getName()).log(Level.SEVERE, null, ex);
            } catch (Exception ex) {
                Logger.getLogger(ExtractCloudMask.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                if (p != null) {
                    p.dispose();
                }
                if (subsetP != null) {
                    subsetP.dispose();
                }
            }

            if (isTarFile) {
                clearTempProduct(fname);
            }
        }
    }

    static ArrayList<CloudSceneProperties> readSceneFromResource(String sceneResourceName) {
        String searchPath = null;
        ArrayList<CloudSceneProperties> cloudSceneList = new ArrayList<>();
        InputStream paramStream = ExtractCloudMask.class.getResourceAsStream(sceneResourceName);
        BufferedReader paramReader = new BufferedReader(new InputStreamReader(paramStream));
        String line = null;
        try {
            while ((line = paramReader.readLine()) != null) {
                line = line.trim();
                if (line != null && !line.isEmpty() && !line.startsWith("*")) {
                    String[] stmpArr = line.split("\\s+");
                    if (stmpArr[0].equals("SEARCHPATH")) {
                        searchPath = stmpArr[2];
                    } else {
                        CloudSceneProperties scene = new CloudSceneProperties();
                        scene.setDateS(stmpArr[0]);
                        if (stmpArr.length > 1) {
                            scene.setOrbit(stmpArr[1]);
                            scene.setLatLower(Float.parseFloat(stmpArr[2]));
                            scene.setLatUpper(Float.parseFloat(stmpArr[3]));
                            scene.setLonLower(Float.parseFloat(stmpArr[4]));
                            scene.setLonUpper(Float.parseFloat(stmpArr[5]));
                        } else {
                            scene.setOrbit("?????");
                            scene.setLatLower(-90);
                            scene.setLatUpper(90);
                            scene.setLonLower(-180);
                            scene.setLonUpper(180);
                        }
                        cloudSceneList.add(scene);
                    }
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(ExtractCloudMask.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (paramReader != null) {
                    paramReader.close();
                }
                if (paramStream != null) {
                    paramStream.close();
                }
            } catch (IOException ex) {
                Logger.getLogger(ExtractCloudMask.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        if (searchPath != null) {
            if (!cloudSceneList.isEmpty()){
                ArrayList<CloudSceneProperties> cloudSceneAddList = new ArrayList<>();
                for (CloudSceneProperties scene : cloudSceneList){
                    WildcardFileFilter wFF = new WildcardFileFilter("ATS_TOA*" + scene.getDateS() + "_*_"+scene.getOrbit()+"_*.dim");
                    File[] fl = new FindFileRecursive().listFilesAsArray(searchPath, wFF, false);
                    for (int i=0; i<fl.length; i++){
                        if (i==0){
                            scene.setFilename(fl[i].getPath());
                        }
                        else {
                            CloudSceneProperties newScene = new CloudSceneProperties(fl[i].getPath()
                                    , scene.getLatLower(), scene.getLatUpper(), scene.getLonLower(), scene.getLonUpper());
                            cloudSceneAddList.add(newScene);
                        }
                    }
                }
                cloudSceneList.addAll(cloudSceneAddList);
                cloudSceneAddList = null;
            }
        } else {
            Logger.getLogger(ExtractCloudMask.class.getName()).log(Level.WARNING, "Scene-Searchpath empty!");
            return null;
        }
        return cloudSceneList;
    }

    private static Variable createNetCdfVar(NetcdfFileWriter ncfile, String varName, DataType dataType, ArrayList<Dimension> dimList,
            String stdName, String longName, String units, Array validRange, Number fillValue) {
        Variable imgCol = ncfile.addVariable(null, varName, dataType, dimList);
        if (stdName != null) {
            ncfile.addVariableAttribute(imgCol, new Attribute("standard_name", stdName));
        }
        if (longName != null) {
            ncfile.addVariableAttribute(imgCol, new Attribute("long_name", longName));
        }
        if (units != null) {
            ncfile.addVariableAttribute(imgCol, new Attribute("units", units));
        }
        if (validRange != null) {
            ncfile.addVariableAttribute(imgCol, new Attribute("valid_range", validRange));
        }
        ncfile.addVariableAttribute(imgCol, new Attribute("_FillValue", fillValue));
        return imgCol;
    }

    private static Variable createNetCdfFlagVar(NetcdfFileWriter ncfile, String varName, DataType dataType, ArrayList<Dimension> dimList,
            String stdName, String longName, String flagMeaning, Array flagValues, Number fillValue) {
        Variable imgCol = ncfile.addVariable(null, varName, dataType, dimList);
        if (stdName != null) {
            ncfile.addVariableAttribute(imgCol, new Attribute("standard_name", stdName));
        }
        if (longName != null) {
            ncfile.addVariableAttribute(imgCol, new Attribute("long_name", longName));
        }
        if (flagValues != null) {
            ncfile.addVariableAttribute(imgCol, new Attribute("flag_values", flagValues));
        }
        if (flagMeaning != null) {
            ncfile.addVariableAttribute(imgCol, new Attribute("flag_meanings", flagMeaning));
        }
        ncfile.addVariableAttribute(imgCol, new Attribute("_FillValue", fillValue));
        return imgCol;
    }

    /**
     * determines regional overlap of AATSR product with a given lat/lon window Attention: subsets AATSR also by Solar Elevation Angle > 30 to reduce the output to the day time / descending orbit part
     *
     * @param aatsrProduct
     * @param latmin
     * @param latmax
     * @param lonmin
     * @param lonmax
     * @return
     */
    static Rectangle getGeoRec(Product aatsrProduct, double latmin, double latmax, double lonmin, double lonmax) {
        Rectangle overlapRec = new Rectangle();
        TiePointGrid latTPG = aatsrProduct.getTiePointGrid("latitude");
        TiePointGrid lonTPG = aatsrProduct.getTiePointGrid("longitude");
        TiePointGrid seaTPG = aatsrProduct.getTiePointGrid("sun_elev_nadir");
        int rasterWidth = aatsrProduct.getSceneRasterWidth();
        int rasterHeight = aatsrProduct.getSceneRasterHeight();
        float leftLat;
        float rightLat;
        float leftLon;
        float rightLon;
        boolean overlapFound = false;
        int iy = 0;
        while (iy < rasterHeight) {
            if (seaTPG.getPixelFloat(0, iy) > 15) {
                leftLat = latTPG.getPixelFloat(0, iy);
                rightLat = latTPG.getPixelFloat(rasterWidth - 1, iy);
                boolean leftLatInside = (leftLat < latmax && leftLat > latmin);
                boolean rightLatInside = (rightLat < latmax && rightLat > latmin);
                if (leftLatInside || rightLatInside) {
                    leftLon = lonTPG.getPixelFloat(0, iy);
                    rightLon = lonTPG.getPixelFloat(rasterWidth - 1, iy);
                    boolean leftLonInside = (leftLon < lonmax && leftLon > lonmin);
                    boolean rightLonInside = (rightLon < lonmax && rightLon > lonmin);
                    if (leftLonInside || rightLonInside) {
                        if (!overlapFound) {
                            overlapRec = new Rectangle(0, iy, rasterWidth, 0);
                            overlapFound = true;
                        }
                    } else {
                        if (overlapFound) {
                            overlapRec.height = 1 + iy - overlapRec.y;
                            return overlapRec;
                        }
                    }
                } else {
                    if (overlapFound) {
                        overlapRec.height = 1 + iy - overlapRec.y;
                        return overlapRec;
                    }
                }
            }
            else {
                if (overlapFound) {
                    overlapRec.height = 1 + iy - overlapRec.y;
                    return overlapRec;
                }
            }
            iy += aatsrLineStep;
        }
        if (overlapFound) {
            overlapRec.height = 1 + rasterHeight - overlapRec.y;
            return overlapRec;
        }
        return overlapRec;
    }

    static void setSubsetStartStopTime(Product sourceProduct, Product targetProduct, Rectangle region) {
        long startTimeInMillis = sourceProduct.getStartTime().getAsCalendar().getTimeInMillis();
        long endTimeInMillis = sourceProduct.getEndTime().getAsCalendar().getTimeInMillis();
        long delta = endTimeInMillis - startTimeInMillis;
        long tarStartTimeInMicros = (long) ((startTimeInMillis + (double) (region.y) / (sourceProduct.getSceneRasterHeight() - 1) * delta) * 1000);
        long tarEndTimeInMicros = (long) ((startTimeInMillis + (double) (region.y + region.height - 1) / (sourceProduct.getSceneRasterHeight() - 1) * delta) * 1000);
        ProductData.UTC tarStart = getUtcTime(tarStartTimeInMicros);
        ProductData.UTC tarEnd = getUtcTime(tarEndTimeInMicros);
        targetProduct.setStartTime(tarStart);
        targetProduct.setEndTime(tarEnd);
    }

    private static ProductData.UTC getUtcTime(long timeInMicros) {
        final long millisPerSecond = 1000;
        final long microsPerSecond = 1000000;
        final long secondsPerDay = 24 * 60 * 60;
        final long micros = timeInMicros % microsPerSecond;
        final long seconds = (timeInMicros / microsPerSecond) % secondsPerDay;
        final long days = timeInMicros / (secondsPerDay * microsPerSecond);
        final ProductData.UTC utcTime = ProductData.UTC.create(new Date((days * secondsPerDay + seconds) * millisPerSecond), micros);
        return utcTime;
    }

    private static boolean isTarGzFile(String fileName) {
        return fileName.toLowerCase().endsWith("tar.gz");
    }

    private static String extractProduct(String fname) {
        String dsName = null;
        TarArchiveEntry entry;
        final int BUFFER = 64 * 1024 * 1024; //(64MB)
        try {
            GZIPInputStream gzInStream = new GZIPInputStream(new FileInputStream(fname));
            TarArchiveInputStream tarInput = new TarArchiveInputStream(gzInStream);
            System.err.printf("extracting %s%30s                                        \r", fname, "");
            while ((entry = tarInput.getNextTarEntry()) != null) {
                int count;
                byte[] data = new byte[BUFFER];
                //String tempName = "/dev/shm/" + entry.getName();
                String tempName = System.getProperty("java.io.tmpdir") + "/" + entry.getName();
                //System.err.printf("extracting %s%30s\r", tempName, "");
                if (tempName.endsWith(".dim")) {
                    dsName = tempName;
                }
                File tempF = new File(tempName);
                if (!tempF.exists()) {
                    if (entry.isDirectory()) {
                        boolean mkdirs = new File(tempName).mkdirs();
                        if (!mkdirs) {
                            throw new IOException("could not create dir " + tempName);
                        }
                    } else {
                        BufferedOutputStream dest = new BufferedOutputStream(new FileOutputStream(tempName), BUFFER);
                        while ((count = tarInput.read(data, 0, BUFFER)) != -1) {
                            dest.write(data, 0, count);
                        }
                        dest.flush();
                        dest.close();
                    }
                } else {
                    System.err.println("skipping extraction " + tempName + " exists!");
                }
            }
            System.err.printf("extraction completed                                        \r");
            tarInput.close();
        } catch (IOException ex) {
            Logger.getLogger(ConvDimToNcdf.class.getName()).log(Level.SEVERE, null, ex);
        }

        return dsName;
    }

    private static void clearTempProduct(String prodFname) {
        System.err.println("deleting temp files...");
        String dataDir = prodFname.replaceFirst("\\.dim", ".data");
        String namePat = new File(prodFname).getName().replace(".dim", ".d");
        File parentFile = new File(prodFname).getParentFile();
        FilenameFilter filter = new TmpFileFilterImpl(namePat);
        File[] tmpFiles = new FindFileRecursive().listFilesAsArray(parentFile, filter, true);
        for (int i = tmpFiles.length - 1; i >= 0; i--) {
            System.err.println("deleting temp file " + tmpFiles[i].getPath());
            tmpFiles[i].delete();
        }
        /*        
         File dataDirF = new File(dataDir);
         if (dataDirF.exists()) {
         File[] allFiles = dataDirF.listFiles();
         for (File f : allFiles) if (f.exists()) f.delete();
         dataDirF.delete();
         }
         File prodF = new File(prodFname);
         if (prodF.exists()) prodF.delete();
         * 
         */
    }

    private static class TmpFileFilterImpl implements FilenameFilter {

        final String pattern;

        public TmpFileFilterImpl(String pattern) {
            this.pattern = pattern;
        }

        @Override
        public boolean accept(File dir, String name) {
            try {
                return name.contains(pattern) || dir.getCanonicalPath().contains(pattern);
            } catch (IOException ex) {
                Logger.getLogger(ConvDimToNcdf.class.getName()).log(Level.SEVERE, null, ex);
            }
            return false;
        }
    }
}
