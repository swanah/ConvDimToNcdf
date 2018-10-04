/**/
package akh.convdimtoncdf;

import akh.findfilerecursive.FindFileRecursive;
import java.io.*;
import java.text.*;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.esa.beam.framework.dataio.ProductIO;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import ucar.ma2.InvalidRangeException;

/**
 * Hello world!
 *
 */
public class ConvDimToNcdf {
    //private static final String[] VERSIONS = {"1.1","1.2","2.1","2.2","3.0","3.1","4.0","1.0","4.1","4.2","4.21","4.3","vSyn1.0"};

    public static void main(String[] args) throws Exception {
        ProdConverterL2 pConvL2 = ProdConverterL2.getInstance();
        ProdSinConverterL2 pSinConvL2 = ProdSinConverterL2.getInstance();
        ProdConverterL3 pConvL3 = ProdConverterL3.getInstance();
        DataGridder gridder = new DataGridder();
        boolean isTarFile;
        boolean lv3SingleOrbit;
        

        if (args.length > 1){
            int argOffset = 0;
            DataVersionNumbers version = DataVersionNumbers.parseVersionString(args[argOffset++]);
            lv3SingleOrbit = (args.length == 2);
            String l3NetcdfName = null;
            for (int i=argOffset; i<args.length; i++) {
                String fname = args[i];
                String l2NetcdfName = getL2NetcdfName(fname, version);
                if (i == argOffset) l3NetcdfName = getL3NetcdfName(fname, version, lv3SingleOrbit);
                
                isTarFile = isTarGzFile(fname);
                if (isTarFile){
                    fname = extractProduct(fname);
                }
                if ( ! new File(fname).exists()) {
                    System.err.println(fname + " not found!");
                    continue;
                }
                Product p = null;
                
                try {
                    p = ProductIO.readProduct(fname);
                    if (p == null) {
                        Logger.getLogger(ConvDimToNcdf.class.getName()).log(Level.WARNING, "product null! skipping ", fname);
                        continue;
                    }
                    if (p.getName().startsWith("AT2")){
                        correctTime(p);
                    }
                    
                    switch (version){
                        case v1_0:
                            pConvL2.convertV4(p, l2NetcdfName, version);
                            gridder.binToGridV10(p, version);
                            break;
                        case v1_1:
                        case v2_1:
                            pConvL2.convertV11(p, l2NetcdfName, version);
                            gridder.binToGridV11(p, version);
                            break;
                        case v1_2:
                        case v2_2:
                        case v3_0:
                        case v3_1:
                            pConvL2.convertV3(p, l2NetcdfName, version);
                            gridder.binToGridV3(p, version);
                            break;
                        case v4_0:
                        case v4_1:
                        case v4_2: 
                            pConvL2.convertV4(p, l2NetcdfName, version);
                            gridder.binToGridV4(p, version);
                            break;
                        case v4_21u:
                        case v4_21:
                        case v4_3: 
                        case v4_31: 
                        case v4_32: 
                        case vSyn1_0: 
                        case vSyn1_1: 
                        case vSyn1_2: 
                            pSinConvL2.convert(p, l2NetcdfName, version);
                            gridder.binToGridV4(p, version);
                            break;
                        
                        default: throw new IllegalArgumentException("Data Version: "+version+" not recognized!");
                    }
                    
/*
                    if (version.equals(VERSIONS[1])        // 1.2, 2.2, 3.0, 3.1
                        || version.equals(VERSIONS[3])
                        || version.equals(VERSIONS[4])
                        || version.equals(VERSIONS[5])){
                        pConvL2.convertV3(p, l2NetcdfName, version);
                        gridder.binToGridV3(p, version);
                    }
                    else if (version.equals(VERSIONS[0])    // 1.1, 2.1
                            || version.equals(VERSIONS[2])){
                        pConvL2.convertV11(p, l2NetcdfName, version);
                        gridder.binToGridV11(p, version);
                    }
                    else if (version.equals(VERSIONS[6])    // 4.0, 4.1, 4.2
                            || version.equals(VERSIONS[8])
                            || version.equals(VERSIONS[9])){
                        pConvL2.convertV4(p, l2NetcdfName, version);
                        gridder.binToGridV4(p, version);
                    }
                    else if (version.equals(VERSIONS[7])){  // 1.0
                        pConvL2.convertV4(p, l2NetcdfName, version);
                        gridder.binToGridV10(p, version);
                    }
                    else{
                        System.err.println("Version "+version+"not implemented! valid versions : " + VERSIONS);
                    }
*/
                } catch (IOException ex){
                    Logger.getLogger(ConvDimToNcdf.class.getName()).log(Level.SEVERE, null, ex);
                } catch (InvalidRangeException ex){
                    Logger.getLogger(ConvDimToNcdf.class.getName()).log(Level.SEVERE, null, ex);
                } finally {
                    if (p != null) p.dispose();
                    if (isTarFile){
                        clearTempProduct(fname);
                    }
                }
                
            }
            
            if (gridder != null && !gridder.isEmpty()){
                System.out.println("writing L3 file: " + l3NetcdfName);
                pConvL3.writeGrids(gridder, l3NetcdfName, version);
                System.out.println();
            }
            else {
                Logger.getLogger(ConvDimToNcdf.class.getName()).log(Level.WARNING, "L3 file not created, grids empty!", l3NetcdfName);
            }
        }
        else {
            System.err.println("Require cmd line args: versionString files.dim");
            System.err.print("    versionString can be one of [");
            for (DataVersionNumbers v : DataVersionNumbers.values()) System.err.print(v+", ");
            System.err.printf("\b\b]\n");
        }
    }

    private static String getL3NetcdfName(String fname, DataVersionNumbers version, boolean lv3SingleOrbit) {
        //2008010100-ESACCI-L3C_AEROSOL-AOD-AATSR_ENVISAT-ADV_DAILY-v1.40.nc
        //20080101-ESACCI-L3C_AEROSOL-ALL-AATSR_ENVISAT-ORAC-DAILY-fv02.02.nc
        //ATS_TOA_1P  RUPA200801  01_064007_  0000652720  64_00406_3  0520_9956.N1.gz
        File f = new File(fname);
        String stmp = f.getName();
        String yyyymmdd = stmp.substring(14, 14+8);
        String hhmmss = stmp.substring(14+8+1, 14+8+1+6);
        String instrument = "AATSR_ENVISAT";
        if (stmp.startsWith("AT2")){
            instrument = "ATSR2_ERS2";
        }
        if (version.isGE(DataVersionNumbers.vSyn1_0)){
            instrument = "MERIS_AATSR_SYN_ENVISAT";
            yyyymmdd = stmp.substring(12, 12+8);
            hhmmss = stmp.substring(12+8+1, 12+8+1+6);
        }
        String l3NetcdfName = yyyymmdd;
        if (lv3SingleOrbit) l3NetcdfName += hhmmss;
        l3NetcdfName += "-ESACCI-L3C_AEROSOL-AER_PRODUCTS-" + instrument + "-SU_DAILY-v" + version + ".nc";
        f = new File(f.getParentFile(), l3NetcdfName);
        l3NetcdfName = f.getPath();
        return l3NetcdfName;
    }

    private static String getL2NetcdfName(String fname, DataVersionNumbers version) {
        //20080101064007-ESACCI-L2P_AEROSOL-ALL-AATSR_ENVISAT-ORAC_30520-fv02.02.nc
        //ATS_TOA_1P  RUPA200801  01_064007_  0000652720  64_00406_3  0520_9956.N1.gz
        File f = new File(fname);
        String stmp = f.getName();
        String yyyymmdd = stmp.substring(14, 14+8);
        String hhmmss = stmp.substring(14+8+1, 14+8+1+6);
        String orbit = (version.isGE(DataVersionNumbers.v4_0) && stmp.length() > 49) ? stmp.substring(49, 49+5) : "00000";
        String instrument = "AATSR_ENVISAT";
        if (stmp.startsWith("AT2")){
            instrument = "ATSR2_ERS2";
        }
        if (version.isGE(DataVersionNumbers.vSyn1_0)){
            instrument = "MERIS_AATSR_SYN_ENVISAT";
            yyyymmdd = stmp.substring(12, 12+8);
            hhmmss = stmp.substring(12+8+1, 12+8+1+6);
            orbit = "00000";
        }
        String l2NetcdfName = yyyymmdd + hhmmss + "-ESACCI-L2P_AEROSOL-AER_PRODUCTS-" + instrument + "-SU_" + orbit + "-v" + version + ".nc";
        f = new File(f.getParentFile(), l2NetcdfName);
        l2NetcdfName = f.getPath();
        return l2NetcdfName;
    }

    private static void readMetaDataFromOriginal(String origName, final DimMetaData dimMetaData) throws NumberFormatException {
        SAXBuilder builder = new SAXBuilder();
        try {
            Document doc = builder.build(new File(origName));
            Element root = doc.getRootElement();
            Element dsSrc = root.getChild("Dataset_Sources");
            Element metadata = dsSrc.getChild("MDElem");
            for (Element mdEle : metadata.getChildren("MDElem")){
                String mdEleName = mdEle.getAttributeValue("name");
                if (mdEleName.equals("MPH")){
                    for (Element mphAttr : mdEle.getChildren("MDATTR")){
                        String mphEleName = mphAttr.getAttributeValue("name");
                        if (mphEleName.equals("SENSING_START")){
                            dimMetaData.setSensingStart(mphAttr.getValue());
                        }
                        else if (mphEleName.equals("SENSING_STOP")){
                            dimMetaData.setSensingStop(mphAttr.getValue());
                        }
                    }
                }
                else if (mdEleName.equals("DSD")){
                    for (Element dsdEle : mdEle.getChildren("MDElem")){
                        String dsdName = dsdEle.getAttributeValue("name");
                        if (dsdName.equals("DSD.15")){
                            for (Element dsdAttr : dsdEle.getChildren("MDATTR")){
                                String dsdAttrName = dsdAttr.getAttributeValue("name");
                                if (dsdAttrName.equals("NUM_RECORDS")){
                                    dimMetaData.setOrigRasterHeight(Integer.parseInt(dsdAttr.getValue()));
                                }
                            }
                        }
                    }
                }
                else if (mdEleName.equals("history")){
                    for (Element histEle : mdEle.getChildren("MDElem")){
                        String histEleName = histEle.getAttributeValue("name");
                        if (histEleName.equals("SubsetInfo")){
                            for (Element subsetAttr : histEle.getChildren("MDATTR")){
                                String subsetAttrName = subsetAttr.getAttributeValue("name");
                                if (subsetAttrName.equals("SubRegion.y")){
                                    dimMetaData.setSubsetY(Integer.parseInt(subsetAttr.getValue()));
                                }
                                else if (subsetAttrName.equals("SubRegion.height")){
                                    dimMetaData.setSubsetHeight(Integer.parseInt(subsetAttr.getValue()));
                                }
                            }
                        }
                    }
                }
            }
        } catch (JDOMException ex) {
            Logger.getLogger(ConvDimToNcdf.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(ConvDimToNcdf.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    static boolean isTarGzFile(String fileName) {
        return fileName.toLowerCase().endsWith("tar.gz");
    }

    static String extractProduct(String fname) {
        String dsName = null;
        TarArchiveEntry entry;
        final int BUFFER = 64*1024*1024; //(64MB)
        try {
            GZIPInputStream gzInStream = new GZIPInputStream(new FileInputStream(fname));
            TarArchiveInputStream tarInput = new TarArchiveInputStream(gzInStream);
            while ((entry = tarInput.getNextTarEntry()) != null) {
                int count;
                byte[] data = new byte[BUFFER];
                //String tempName = "/dev/shm/" + entry.getName();
                String tempName = System.getProperty("java.io.tmpdir") + "/" + entry.getName();
                //System.err.printf("extracting %s\r", tempName);
                if (tempName.endsWith(".dim")) dsName = tempName;
                File tempF = new File(tempName);
                if (!tempF.exists()){
                    if (entry.isDirectory()){
                        boolean mkdirs = new File(tempName).mkdirs();
                        if (!mkdirs) throw new IOException("could not create dir " + tempName);
                    }
                    else {
                        BufferedOutputStream dest = new BufferedOutputStream(new FileOutputStream(tempName), BUFFER);
                        while ((count = tarInput.read(data, 0, BUFFER)) != -1) {
                            dest.write(data, 0, count);
                        }
                        dest.flush();
                        dest.close();
                    }
                }
                else {
                    //System.err.println("skipping extraction " + tempName + " exists!");
                }
            }
            tarInput.close();
        } catch (IOException ex) {
            Logger.getLogger(ConvDimToNcdf.class.getName()).log(Level.SEVERE, null, ex);
        }

        return dsName;
    }
    
    static void clearTempProduct(String prodFname) {
        String dataDir = prodFname.replaceFirst("\\.dim", ".data");
        String namePat = new File(prodFname).getName().replace(".dim", ".d");
        File parentFile = new File(prodFname).getParentFile();
        FilenameFilter filter = new TmpFileFilterImpl(namePat);
        File[] tmpFiles = new FindFileRecursive().listFilesAsArray(parentFile, filter, true);
        for (int i=tmpFiles.length-1; i>=0; i--) {
            //System.out.printf("deleting temp file %s\r", tmpFiles[i].getPath());
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

    private static void correctTime(Product p) throws ParseException {
        String fname = p.getFileLocation().getPath();
        final DimMetaData dimData = new DimMetaData();
        readMetaDataFromOriginal(fname+".orig", dimData);

        System.err.println("end parsing - rasterheight: " + dimData.getOrigRasterHeight());
        System.err.println("original sens start: " + dimData.getSensingStart());
        System.err.println("original sens stop:  " + dimData.getSensingStop());
        System.err.println("subset y          :  " + dimData.getSubsetY());
        System.err.println("subset height     :  " + dimData.getSubsetHeight());

        TimeZone defaultTZ = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SimpleDateFormat df = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss.SSS", Locale.ENGLISH);
        Date startDate = df.parse(dimData.getSensingStart().substring(0, 24));
        Date stopDate = df.parse(dimData.getSensingStop().substring(0, 24));
        
        System.out.println("Start Time : " + startDate);
        System.out.println("Stop  Time : " + stopDate);

        long startTimeInMillis = startDate.getTime();
        long endTimeInMillis = stopDate.getTime();
        long delta = endTimeInMillis - startTimeInMillis;
        long tarStartTimeInMicros = (long) ((startTimeInMillis + (double)(dimData.getSubsetY()) / (dimData.getOrigRasterHeight()-1) * delta) * 1000);
        long tarEndTimeInMicros   = (long) ((startTimeInMillis + (double)(dimData.getSubsetY() + dimData.getSubsetHeight() - 1) / (dimData.getOrigRasterHeight()-1) * delta) * 1000);
        ProductData.UTC tarStart = getUtcTime(tarStartTimeInMicros);
        ProductData.UTC tarEnd = getUtcTime(tarEndTimeInMicros);
        p.setStartTime(tarStart);
        p.setEndTime(tarEnd);

        System.out.println("Product Start Time set : " + p.getStartTime());
        System.out.println("Product Stop  Time set : " + p.getEndTime());
        
        TimeZone.setDefault(defaultTZ);
    }

    private static ProductData.UTC getUtcTime(long timeInMicros) {
        final long millisPerSecond = 1000;
        final long microsPerSecond = 1000000;
        final long secondsPerDay   = 24*60*60;
        final long micros  = timeInMicros % microsPerSecond;
        final long seconds = (timeInMicros / microsPerSecond) % secondsPerDay;
        final long days    = timeInMicros / (secondsPerDay * microsPerSecond);
        final ProductData.UTC utcTime = ProductData.UTC.create(new Date((days * secondsPerDay + seconds) * millisPerSecond), micros);
        return utcTime;
    }

    
    
    static class TmpFileFilterImpl implements FilenameFilter {
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
    
    private static class DimMetaData {
        private int origRasterHeight;
        private int subsetHeight;
        private int subsetY;
        private String sensingStart;
        private String sensingStop;

        public DimMetaData(){
        }
        
        public DimMetaData(int origRasterHeight, int subsetheight, int subsetY, String sensingStart, String sensingStop) {
            this.origRasterHeight = origRasterHeight;
            this.subsetHeight = subsetheight;
            this.subsetY = subsetY;
            this.sensingStart = sensingStart;
            this.sensingStop = sensingStop;
        }

        public int getOrigRasterHeight() {
            return origRasterHeight;
        }

        public void setOrigRasterHeight(int origRasterHeight) {
            this.origRasterHeight = origRasterHeight;
        }

        public String getSensingStart() {
            return sensingStart;
        }

        public void setSensingStart(String sensingStart) {
            this.sensingStart = sensingStart;
        }

        public String getSensingStop() {
            return sensingStop;
        }

        public void setSensingStop(String sensingStop) {
            this.sensingStop = sensingStop;
        }

        public int getSubsetY() {
            return subsetY;
        }

        public void setSubsetY(int subsetY) {
            this.subsetY = subsetY;
        }

        public int getSubsetHeight() {
            return subsetHeight;
        }

        public void setSubsetHeight(int subsetheight) {
            this.subsetHeight = subsetheight;
        }
    }
}
