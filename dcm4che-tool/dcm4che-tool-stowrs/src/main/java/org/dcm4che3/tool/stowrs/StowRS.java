/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2017-2019
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4che3.tool.stowrs;

import org.apache.commons.cli.*;
import org.dcm4che3.data.*;
import org.dcm4che3.imageio.codec.XPEGParser;
import org.dcm4che3.imageio.codec.jpeg.JPEG;
import org.dcm4che3.imageio.codec.jpeg.JPEGParser;
import org.dcm4che3.imageio.codec.mp4.MP4Parser;
import org.dcm4che3.imageio.codec.mpeg.MPEG2Parser;
import org.dcm4che3.io.SAXReader;
import org.dcm4che3.io.SAXTransformer;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.tool.common.CLIUtils;
import org.dcm4che3.util.Base64;
import org.dcm4che3.util.StreamUtils;
import org.dcm4che3.util.UIDUtils;
import org.dcm4che3.ws.rs.MediaTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.core.MediaType;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Hesham Elbadawi <bsdreko@gmail.com>
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Apr 2017
 */
public class StowRS {
    private static final Logger LOG = LoggerFactory.getLogger(StowRS.class);
    private static final ResourceBundle rb = ResourceBundle.getBundle("org.dcm4che3.tool.stowrs.messages");
    private static String[] keys;
    private static String url;
    private boolean noApp;
    private boolean pixelHeader;
    private static boolean vlPhotographicImage;
    private static boolean videoPhotographicImage;
    private static String requestAccept;
    private static String requestContentType;
    private static String metadataFile;
    private static boolean allowAnyHost;
    private static boolean disableTM;
    private static boolean encapsulatedDocLength;
    private static String authorization;
    private boolean tsuid;
    private static final String boundary = "myboundary";
    private static final AtomicInteger fileCount = new AtomicInteger();
    private static FileContentType fileContentTypeFromCL;
    private static FileContentType firstBulkdataFileContentType;
    private static FileContentType bulkdataFileContentType;
    private static final Map<String, StowRSBulkdata> contentLocBulkdata = new HashMap<>();

    private static final int[] IUIDS_TAGS = {
            Tag.StudyInstanceUID,
            Tag.SeriesInstanceUID
    };

    private static final int[] TYPE2_TAGS = {
            Tag.ContentDate,
            Tag.ContentTime
    };

    private static final ElementDictionary DICT = ElementDictionary.getStandardElementDictionary();

    @SuppressWarnings("static-access")
    private static CommandLine parseCommandLine(String[] args)
            throws ParseException {
        Options opts = new Options();
        opts.addOption(Option.builder("m")
                .numberOfArgs(2).argName("[seq.]attr=value")
                .desc(rb.getString("metadata"))
                .build());
        opts.addOption(Option.builder("f")
                .hasArg()
                .argName("file")
                .longOpt("file")
                .desc(rb.getString("file"))
                .build());
        opts.addOption(Option.builder()
                .hasArg()
                .argName("contentType")
                .longOpt("contentType")
                .desc(rb.getString("contentType"))
                .build());
        opts.addOption(Option.builder()
                .hasArg()
                .argName("url")
                .longOpt("url")
                .desc(rb.getString("url"))
                .build());
        opts.addOption(Option.builder("t")
                .hasArg()
                .argName("type")
                .longOpt("type")
                .desc(rb.getString("type"))
                .build());
        opts.addOption(Option.builder()
                .argName("tsuid")
                .longOpt("tsuid")
                .desc(rb.getString("tsuid"))
                .build());
        opts.addOption(Option.builder()
                .longOpt("pixel-header")
                .desc(rb.getString("pixel-header"))
                .build());
        opts.addOption(Option.builder()
                .longOpt("no-app")
                .desc(rb.getString("no-app"))
                .build());
        opts.addOption(Option.builder()
                .longOpt("xc")
                .desc(rb.getString("xc"))
                .build());
        opts.addOption(Option.builder()
                .longOpt("video")
                .desc(rb.getString("video"))
                .build());
        opts.addOption(Option.builder("a")
                .longOpt("accept")
                .hasArg()
                .desc(rb.getString("accept"))
                .build());
        opts.addOption(Option.builder()
                .longOpt("allowAnyHost")
                .desc(rb.getString("allowAnyHost"))
                .build());
        opts.addOption(Option.builder()
                .longOpt("disableTM")
                .desc(rb.getString("disableTM"))
                .build());
        opts.addOption(Option.builder()
                .longOpt("encapsulatedDocLength")
                .desc(rb.getString("encapsulatedDocLength"))
                .build());
        OptionGroup group = new OptionGroup();
        group.addOption(Option.builder("u")
                .hasArg()
                .argName("user:password")
                .longOpt("user")
                .desc(rb.getString("user"))
                .build());
        group.addOption(Option.builder()
                .hasArg()
                .argName("bearer")
                .longOpt("bearer")
                .desc(rb.getString("bearer"))
                .build());
        opts.addOptionGroup(group);
        CLIUtils.addCommonOptions(opts);
        return CLIUtils.parseComandLine(args, opts, rb, StowRS.class);
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        try {
            CommandLine cl = parseCommandLine(args);
            List<String> files = cl.getArgList();
            StowRS stowRS = new StowRS();
            stowRS.doNecessaryChecks(cl, files);
            LOG.info("Storing objects.");
            stowRS.stow(files);
        } catch (ParseException e) {
            System.err.println("stowrs: " + e.getMessage());
            System.err.println(rb.getString("try"));
            System.exit(2);
        } catch (Exception e) {
            LOG.error("Error: \n", e);
            e.printStackTrace();
            System.exit(2);
        }
    }

    private void doNecessaryChecks(CommandLine cl, List<String> files)
            throws Exception {
        if (files.isEmpty() && !cl.hasOption("f"))
            throw new MissingArgumentException(
                    "Neither bulk data / dicom files specified nor metadata file specified for non bulk data type of objects");
        if ((url = cl.getOptionValue("url")) == null)
            throw new MissingOptionException("Missing url.");
        if (cl.hasOption("f")
                && !Files.probeContentType(Paths.get((metadataFile = cl.getOptionValue("f")))).endsWith("xml"))
            throw new IllegalArgumentException("Metadata file extension not supported. Read -f option in stowrs help");

        tsuid = cl.hasOption("tsuid");
        pixelHeader = cl.hasOption("pixel-header");
        noApp = cl.hasOption("no-appn");
        keys = cl.getOptionValues("m");
        authorization = cl.hasOption("u")
                ? basicAuth(cl.getOptionValue("u"))
                : cl.hasOption("bearer") ? "Bearer " + cl.getOptionValue("bearer") : null;
        vlPhotographicImage = cl.hasOption("xc");
        videoPhotographicImage = cl.hasOption("video");
        allowAnyHost = cl.hasOption("allowAnyHost");
        disableTM = cl.hasOption("disableTM");
        encapsulatedDocLength = cl.hasOption("encapsulatedDocLength");
        if (cl.hasOption("contentType"))
            bulkdataFileContentType = fileContentTypeFromCL = fileContentType(cl.getOptionValue("contentType"));
        processFirstFile(cl);
    }

    private void processFirstFile(CommandLine cl) throws Exception {
        if (cl.getArgList().isEmpty())
            return;

        applyFunctionToFile(cl.getArgList().get(0), false, path -> {
            String contentType = Files.probeContentType(path);
            if (contentType == null || !contentType.equals(MediaTypes.APPLICATION_DICOM)) {
                firstBulkdataFileContentType = FileContentType.valueOf(contentType, path);
                if (fileContentTypeFromCL == null)
                    bulkdataFileContentType = firstBulkdataFileContentType;
            }
            setContentAndAcceptType(cl, contentType != null && contentType.equals(MediaTypes.APPLICATION_DICOM));
        });

    }

    private static FileContentType fileContentType(String s) {
        switch (s.toLowerCase(Locale.ENGLISH)) {
            case "stl":
            case "model/stl":
                return FileContentType.STL;
            case "model/x.stl-binary":
                return FileContentType.STL_BINARY;
            case "application/sla":
                return FileContentType.SLA;
            case "pdf":
            case "application/pdf":
                return FileContentType.PDF;
            case "xml":
            case "application/xml":
                return FileContentType.CDA;
            case "mtl":
            case "model/mtl":
                return FileContentType.MTL;
            case "obj":
            case "model/obj":
                return FileContentType.OBJ;
            case "jpg":
            case "jpeg":
            case "image/jpeg":
                return FileContentType.JPEG;
            case "jp2":
            case "image/jp2":
                return FileContentType.JP2;
            case "png":
            case "image/png":
                return FileContentType.PNG;
            case "gif":
            case "image/gif":
                return FileContentType.GIF;
            case "mpeg":
            case "video/mpeg":
                return FileContentType.MPEG;
            case "mp4":
            case "video/mp4":
                return FileContentType.MP4;
            case "mov":
            case "video/quicktime":
                return FileContentType.QUICKTIME;
            default:
                throw new IllegalArgumentException(
                        MessageFormat.format(rb.getString("bulkdata-file-not-supported"), s));
        }
    }

    enum FileContentType {
        PDF(UID.EncapsulatedPDFStorage, Tag.EncapsulatedDocument, MediaTypes.APPLICATION_PDF,
                "encapsulatedPDFMetadata.xml"),
        CDA(UID.EncapsulatedCDAStorage, Tag.EncapsulatedDocument, MediaType.TEXT_XML,
                "encapsulatedCDAMetadata.xml"),
        SLA(UID.EncapsulatedSTLStorage, Tag.EncapsulatedDocument, MediaTypes.APPLICATION_SLA,
                "encapsulatedSTLMetadata.xml"),
        STL(UID.EncapsulatedSTLStorage, Tag.EncapsulatedDocument, MediaTypes.MODEL_STL,
                "encapsulatedSTLMetadata.xml"),
        STL_BINARY(UID.EncapsulatedSTLStorage, Tag.EncapsulatedDocument, MediaTypes.MODEL_X_STL_BINARY,
                "encapsulatedSTLMetadata.xml"),
        MTL(UID.EncapsulatedMTLStorage, Tag.EncapsulatedDocument, MediaTypes.MODEL_MTL,
                "encapsulatedMTLMetadata.xml"),
        OBJ(UID.EncapsulatedOBJStorage, Tag.EncapsulatedDocument, MediaTypes.MODEL_OBJ,
                "encapsulatedOBJMetadata.xml"),
        JPEG(vlPhotographicImage ? UID.VLPhotographicImageStorage : UID.SecondaryCaptureImageStorage,
                Tag.PixelData,
                MediaTypes.IMAGE_JPEG,
                vlPhotographicImage ? "vlPhotographicImageMetadata.xml" : "secondaryCaptureImageMetadata.xml"),
        JP2(vlPhotographicImage ? UID.VLPhotographicImageStorage : UID.SecondaryCaptureImageStorage,
                Tag.PixelData,
                MediaTypes.IMAGE_JP2,
                vlPhotographicImage ? "vlPhotographicImageMetadata.xml" : "secondaryCaptureImageMetadata.xml"),
        PNG(vlPhotographicImage ? UID.VLPhotographicImageStorage : UID.SecondaryCaptureImageStorage,
                Tag.PixelData,
                MediaTypes.IMAGE_PNG,
                vlPhotographicImage ? "vlPhotographicImageMetadata.xml" : "secondaryCaptureImageMetadata.xml"),
        GIF(videoPhotographicImage
                ? UID.VideoPhotographicImageStorage
                : vlPhotographicImage
                ? UID.VLPhotographicImageStorage : UID.SecondaryCaptureImageStorage,
                Tag.PixelData,
                MediaTypes.IMAGE_GIF,
                vlPhotographicImage || videoPhotographicImage
                        ? "vlPhotographicImageMetadata.xml" : "secondaryCaptureImageMetadata.xml"),
        MPEG(UID.VideoPhotographicImageStorage, Tag.PixelData, MediaTypes.VIDEO_MPEG,
                "vlPhotographicImageMetadata.xml"),
        MP4(UID.VideoPhotographicImageStorage, Tag.PixelData, MediaTypes.VIDEO_MP4,
                "vlPhotographicImageMetadata.xml"),
        QUICKTIME(UID.VideoPhotographicImageStorage, Tag.PixelData, MediaTypes.VIDEO_QUICKTIME,
                "vlPhotographicImageMetadata.xml");

        private final String cuid;
        private final int bulkdataTypeTag;
        private final String mediaType;
        private final String sampleMetadataFile;

        public String getSOPClassUID() {
            return cuid;
        }

        public String getSampleMetadataResourceURL() {
            return "resource:" + sampleMetadataFile;
        }

        public int getBulkdataTypeTag() {
            return bulkdataTypeTag;
        }

        public String getMediaType() {
            return mediaType;
        }

        FileContentType(String cuid, int bulkdataTypeTag, String mediaType, String sampleMetadataFile) {
            this.cuid = cuid;
            this.bulkdataTypeTag = bulkdataTypeTag;
            this.sampleMetadataFile = sampleMetadataFile;
            this.mediaType = mediaType;
        }

        static FileContentType valueOf(String contentType, Path path) {
            String fileName = path.toFile().getName();
            String ext = fileName.substring(fileName.lastIndexOf('.') + 1);
            return fileContentType(contentType != null ? contentType : ext);
        }
    }

    private static void setContentAndAcceptType(CommandLine cl, boolean dicom) {
        if (dicom) {
            requestContentType = MediaTypes.APPLICATION_DICOM;
            requestAccept = cl.hasOption("a") ? getOptionValue("a", cl) : MediaTypes.APPLICATION_DICOM_XML;
            return;
        }

        requestContentType = cl.hasOption("t") ? getOptionValue("t", cl) : MediaTypes.APPLICATION_DICOM_XML;
        requestAccept = cl.hasOption("a") ? getOptionValue("a", cl) : requestContentType;
    }

    private static String getOptionValue(String option, CommandLine cl) {
        String optionValue = cl.getOptionValue(option);
        if (optionValue.equalsIgnoreCase("xml") || optionValue.equalsIgnoreCase("json"))
            return "application/dicom+" + optionValue;

        throw new IllegalArgumentException("Unsupported type. Read -" + option + " option in stowrs help");
    }

    private Attributes createMetadata(Attributes staticMetadata) {
        Attributes metadata = new Attributes(staticMetadata);
        supplementMissingUID(metadata, Tag.SOPInstanceUID);
        supplementType2Tags(metadata);
        return metadata;
    }

    private Attributes supplementMetadataFromFile(Path bulkdataFilePath, Attributes metadata) {
        LOG.info(MessageFormat.format(rb.getString("supplement-metadata-from-file"), bulkdataFilePath));
        String contentLoc = "bulk" + UIDUtils.createUID();
        metadata.setValue(bulkdataFileContentType.getBulkdataTypeTag(), VR.OB, new BulkData(null, contentLoc, false));
        StowRSBulkdata stowRSBulkdata = new StowRSBulkdata(bulkdataFilePath);
        switch (bulkdataFileContentType) {
            case SLA:
            case STL:
            case STL_BINARY:
            case OBJ:
                supplementMissingUID(metadata, Tag.FrameOfReferenceUID);
            case PDF:
            case CDA:
            case MTL:
                supplementEncapsulatedDocAttrs(metadata, stowRSBulkdata);
                contentLocBulkdata.put(contentLoc, stowRSBulkdata);
                break;
            case JPEG:
            case JP2:
            case PNG:
            case GIF:
            case MPEG:
            case MP4:
            case QUICKTIME:
                pixelMetadata(contentLoc, stowRSBulkdata, metadata);
                break;
        }
        return metadata;
    }

    private void pixelMetadata(String contentLoc, StowRSBulkdata stowRSBulkdata, Attributes metadata) {
        File bulkdataFile = stowRSBulkdata.getBulkdataFile();
        if (pixelHeader || tsuid || noApp) {
            CompressedPixelData compressedPixelData = CompressedPixelData.valueOf();
            try(FileInputStream fis = new FileInputStream(bulkdataFile)) {
                compressedPixelData.parse(fis.getChannel());
                XPEGParser parser = compressedPixelData.getParser();
                if (pixelHeader)
                    parser.getAttributes(metadata);
                stowRSBulkdata.setParser(parser);
            } catch (IOException e) {
                LOG.info("Exception caught getting pixel data from file {}: {}", bulkdataFile, e.getMessage());
            }
        }
        contentLocBulkdata.put(contentLoc, stowRSBulkdata);
    }

    private static Attributes createStaticMetadata() throws Exception {
        LOG.info("Creating static metadata. Set defaults, if essential attributes are not present.");
        Attributes metadata;
        if (firstBulkdataFileContentType == null)
            metadata = SAXReader.parse(StreamUtils.openFileOrURL(Paths.get(metadataFile).toString()));
        else {
            metadata = SAXReader.parse(StreamUtils.openFileOrURL(firstBulkdataFileContentType.getSampleMetadataResourceURL()));
            addAttributesFromFile(metadata);
            supplementSOPClass(metadata, firstBulkdataFileContentType.getSOPClassUID());
        }
        CLIUtils.addAttributes(metadata, keys);
        if (!url.endsWith("studies"))
            metadata.setString(Tag.StudyInstanceUID, VR.UI, url.substring(url.lastIndexOf("/") + 1));
        supplementMissingUIDs(metadata);
        return metadata;
    }

    private static void addAttributesFromFile(Attributes metadata) throws Exception {
        if (metadataFile == null)
            return;

        metadata.addAll(SAXReader.parse(Paths.get(metadataFile).toString(), metadata));
    }

    private static void supplementMissingUIDs(Attributes metadata) {
        for (int tag : IUIDS_TAGS)
            if (!metadata.containsValue(tag))
                metadata.setString(tag, VR.UI, UIDUtils.createUID());
    }

    private static void supplementMissingUID(Attributes metadata, int tag) {
        if (!metadata.containsValue(tag))
            metadata.setString(tag, VR.UI, UIDUtils.createUID());
    }

    private static void supplementSOPClass(Attributes metadata, String value) {
        if (!metadata.containsValue(Tag.SOPClassUID))
            metadata.setString(Tag.SOPClassUID, VR.UI, value);
    }

    private static void supplementType2Tags(Attributes metadata) {
        for (int tag : TYPE2_TAGS)
            if (!metadata.contains(tag))
                metadata.setNull(tag, DICT.vrOf(tag));
    }

    private static void supplementEncapsulatedDocAttrs(Attributes metadata, StowRSBulkdata stowRSBulkdata) {
        if (!metadata.contains(Tag.AcquisitionDateTime))
            metadata.setNull(Tag.AcquisitionDateTime, VR.DT);
        if (encapsulatedDocLength)
            metadata.setLong(Tag.EncapsulatedDocumentLength, VR.UL, stowRSBulkdata.getFileLength());
    }

    private enum CompressedPixelData {
        JPEG {
            @Override
            void parse(SeekableByteChannel channel) throws IOException {
                setParser(new JPEGParser(channel));
            }
        },
        MPEG {
            @Override
            void parse(SeekableByteChannel channel) throws IOException {
                setParser(new MPEG2Parser(channel));
            }
        },
        MP4 {
            @Override
            void parse(SeekableByteChannel channel) throws IOException {
                setParser(new MP4Parser(channel));
            }
        };

        abstract void parse(SeekableByteChannel channel) throws IOException;

        private XPEGParser parser;

        public XPEGParser getParser() {
            return parser;
        }

        void setParser(XPEGParser parser) {
            this.parser = parser;
        }

        static CompressedPixelData valueOf() {
            return bulkdataFileContentType == FileContentType.JP2
                    ? JPEG
                    : bulkdataFileContentType == FileContentType.QUICKTIME
                        ? MP4 : valueOf(bulkdataFileContentType.name());
        }
    }

    private void stow(List<String> files) throws Exception {
        URL newUrl = new URL(url);
        if (url.startsWith("https")) {
            stowHttps(files, newUrl);
            return;
        }

        final HttpURLConnection connection = (HttpURLConnection) newUrl.openConnection();
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type",
                MediaTypes.MULTIPART_RELATED + "; type=\"" + requestContentType + "\"; boundary=" + boundary);
        connection.setRequestProperty("Accept", requestAccept);
        if (authorization != null)
            connection.setRequestProperty("Authorization", authorization);
        logOutgoing(newUrl, connection.getRequestProperty("Content-Type"));
        try (OutputStream out = connection.getOutputStream()) {
            writeData(files, out);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes());
            out.flush();
            logIncoming(connection.getResponseCode(), connection.getResponseMessage(), connection.getHeaderFields(),
                        connection.getInputStream());
            connection.disconnect();
            LOG.info("STOW successful!");
        } catch (Exception e) {
            LOG.error("Exception : " + e.getMessage());
        }
    }

    private void stowHttps(List<String> files, URL url) throws Exception {
        final HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type",
                MediaTypes.MULTIPART_RELATED + "; type=\"" + requestContentType + "\"; boundary=" + boundary);
        connection.setRequestProperty("Accept", requestAccept);
        if (authorization != null)
            connection.setRequestProperty("Authorization", authorization);
        if (disableTM)
            connection.setSSLSocketFactory(sslContext().getSocketFactory());
        connection.setHostnameVerifier((hostname, session) -> allowAnyHost);
        logOutgoing(url, connection.getRequestProperty("Content-Type"));
        try (OutputStream out = connection.getOutputStream()) {
            writeData(files, out);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes());
            out.flush();
            logIncoming(connection.getResponseCode(), connection.getResponseMessage(), connection.getHeaderFields(),
                        connection.getInputStream());
            connection.disconnect();
            LOG.info("STOW successful!");
        } catch (Exception e) {
            LOG.error("Exception : " + e.getMessage());
        }
    }

    SSLContext sslContext() throws GeneralSecurityException {
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, trustManagers(), new java.security.SecureRandom());
        return ctx;
    }

    TrustManager[] trustManagers() {
        return new TrustManager[] { new X509TrustManager() {
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
            }
            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }
            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
        }
        };
    }

    private String basicAuth(String user) {
        byte[] userPswdBytes = user.getBytes();
        int len = (userPswdBytes.length * 4 / 3 + 3) & ~3;
        char[] ch = new char[len];
        Base64.encode(userPswdBytes, 0, userPswdBytes.length, ch, 0);
        return "Basic " + new String(ch);
    }

    private void logOutgoing(URL url, String contentType) {
        LOG.info("> POST " + url.toString());
        LOG.info("> Content-Type: " + contentType);
        LOG.info("> Accept: " + requestAccept);
        if (authorization != null)
            LOG.info("> Authorization: " + authorization);
    }

    private void logIncoming(int respCode, String respMsg, Map<String, List<String>> headerFields, InputStream is) {
        LOG.info("< HTTP/1.1 Response: " + respCode + " " + respMsg);
        for (Map.Entry<String, List<String>> header : headerFields.entrySet())
            if (header.getKey() != null)
                LOG.info("< " + header.getKey() + " : " + String.join(";", header.getValue()));
        LOG.info("< Response Content: ");
        try {
            LOG.debug(readFullyAsString(is));
        } catch (Exception e) {
            LOG.info("Exception caught on reading response body \n", e);
        }
    }

    private static String readFullyAsString(InputStream inputStream)
            throws IOException {
        return readFully(inputStream).toString("UTF-8");
    }

    private static ByteArrayOutputStream readFully(InputStream inputStream) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16384];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, length);
            }
            return baos;
        }
    }

    private void writeData(List<String> files, final OutputStream out) throws Exception {
        if (!requestContentType.equals(MediaTypes.APPLICATION_DICOM)) {
            writeMetadataAndBulkData(out, files, createStaticMetadata());
            return;
        }

        for (String file : files) 
            applyFunctionToFile(file, true, path -> writeDicomFile(out, path));
    }

    private static void writeDicomFile(OutputStream out, Path path) throws IOException {
        if (Files.probeContentType(path) == null) {
            LOG.info(MessageFormat.format(rb.getString("not-dicom-file"), path));
            return;
        }
        writePartHeaders(out, requestContentType, null);
        Files.copy(path, out);
    }

    private void writeMetadataAndBulkData(OutputStream out, List<String> files, final Attributes staticMetadata)
            throws Exception {
        if (requestContentType.equals(MediaTypes.APPLICATION_DICOM_XML))
            writeXMLMetadataAndBulkdata(out, files, staticMetadata);

        else {
            try (ByteArrayOutputStream bOut = new ByteArrayOutputStream()) {
                try (JsonGenerator gen = Json.createGenerator(bOut)) {
                    gen.writeStartArray();
                    if (files.isEmpty())
                        new JSONWriter(gen).write(createMetadata(staticMetadata));

                    for (String file : files)
                        applyFunctionToFile(file, true, path -> {
                            if (ignoreNonMatchingFileContentTypes(path))
                                LOG.info(MessageFormat.format(rb.getString(
                                        "ignore-non-matching-file-content-type"),
                                        path,
                                        bulkdataFileContentType,
                                        fileContentTypeFromCL != null
                                                ? fileContentTypeFromCL : firstBulkdataFileContentType));
                            else
                                new JSONWriter(gen).write(
                                        supplementMetadataFromFile(path, createMetadata(staticMetadata)));
                        });
                    gen.writeEnd();
                    gen.flush();
                }
                writeMetadata(out, bOut);

                for (String contentLocation : contentLocBulkdata.keySet())
                    writeFile(contentLocation, out);
            }
        }
        contentLocBulkdata.clear();
    }

    private void writeXMLMetadataAndBulkdata(final OutputStream out, List<String> files, final Attributes staticMetadata)
            throws Exception {
        if (files.isEmpty())
            writeXMLMetadata(out, staticMetadata);

        for (String file : files) 
            applyFunctionToFile(file, true, path -> writeXMLMetadataAndBulkdataForEach(out, staticMetadata, path));
    }

    private void writeXMLMetadataAndBulkdataForEach(OutputStream out, Attributes staticMetadata, Path bulkdataFilePath) {
        try {
            if (ignoreNonMatchingFileContentTypes(bulkdataFilePath)) {
                LOG.info(MessageFormat.format(rb.getString(
                        "ignore-non-matching-file-content-type"),
                        bulkdataFilePath,
                        bulkdataFileContentType,
                        fileContentTypeFromCL != null ? fileContentTypeFromCL : firstBulkdataFileContentType));
                return;
            }
            
            Attributes metadata = supplementMetadataFromFile(bulkdataFilePath, createMetadata(staticMetadata));
            try (ByteArrayOutputStream bOut = new ByteArrayOutputStream()) {
                SAXTransformer.getSAXWriter(new StreamResult(bOut)).write(metadata);
                writeMetadata(out, bOut);
            }
            writeFile(((BulkData) metadata.getValue(bulkdataFileContentType.getBulkdataTypeTag())).getURI(), out);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    private boolean ignoreNonMatchingFileContentTypes(Path path) throws IOException {
        if (fileCount.incrementAndGet() == 1 || fileContentTypeFromCL != null)
            return false;

        bulkdataFileContentType = FileContentType.valueOf(Files.probeContentType(path), path);
        return !firstBulkdataFileContentType.equals(bulkdataFileContentType);
    }

    private void writeXMLMetadata(OutputStream out, Attributes staticMetadata) {
        Attributes metadata = createMetadata(staticMetadata);
        try (ByteArrayOutputStream bOut = new ByteArrayOutputStream()) {
            SAXTransformer.getSAXWriter(new StreamResult(bOut)).write(metadata);
            writeMetadata(out, bOut);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void writeMetadata(OutputStream out, ByteArrayOutputStream bOut)
            throws IOException {
        LOG.info("> Metadata Content Type: " + requestContentType);
        writePartHeaders(out, requestContentType, null);
        LOG.debug("Metadata being sent is : " + bOut.toString());
        out.write(bOut.toByteArray());
    }

    private static void writePartHeaders(OutputStream out, String contentType, String contentLocation) throws IOException {
        out.write(("\r\n--" + boundary + "\r\n").getBytes());
        out.write(("Content-Type: " + contentType + "\r\n").getBytes());
        if (contentLocation != null)
            out.write(("Content-Location: " + contentLocation + "\r\n").getBytes());
        out.write("\r\n".getBytes());
    }

    private void writeFile(String contentLocation, OutputStream out) throws Exception {
        String bulkdataContentType1 = bulkdataFileContentType.getMediaType();
        StowRSBulkdata stowRSBulkdata = contentLocBulkdata.get(contentLocation);
        XPEGParser parser = stowRSBulkdata.getParser();
        if (bulkdataFileContentType.getBulkdataTypeTag() == Tag.PixelData && tsuid)
            bulkdataContentType1 = bulkdataContentType1 + "; transfer-syntax=" + parser.getTransferSyntaxUID();
        LOG.info("> Bulkdata Content Type: " + bulkdataContentType1);
        writePartHeaders(out, bulkdataContentType1, contentLocation);

        int offset = 0;
        int length = (int) stowRSBulkdata.getFileLength();
        long positionAfterAPPSegments = parser != null ? parser.getPositionAfterAPPSegments() : -1L;
        if (noApp && positionAfterAPPSegments != -1L) {
            offset = (int) positionAfterAPPSegments;
            out.write(-1);
            out.write((byte) JPEG.SOI);
        }
        length -= offset;
        out.write(Files.readAllBytes(stowRSBulkdata.getBulkdataFilePath()), offset, length);
    }

    static class StowRSBulkdata {
        Path bulkdataFilePath;
        File bulkdataFile;
        XPEGParser parser;
        long fileLength;

        StowRSBulkdata(Path bulkdataFilePath) {
            this.bulkdataFilePath = bulkdataFilePath;
            this.bulkdataFile = bulkdataFilePath.toFile();
            this.fileLength = bulkdataFile.length();
        }

        Path getBulkdataFilePath() {
            return bulkdataFilePath;
        }

        File getBulkdataFile() {
            return bulkdataFile;
        }

        long getFileLength() {
            return fileLength;
        }

        XPEGParser getParser() {
            return parser;
        }

        void setParser(XPEGParser parser) {
            this.parser = parser;
        }
    }

    private void applyFunctionToFile(String file, boolean continueVisit, final StowRSFileFunction<Path> function) throws IOException {
        Path path = Paths.get(file);
        if (Files.isDirectory(path)) {
            Files.walkFileTree(path, new StowRSFileVisitor(function::apply, continueVisit));
        } else
            function.apply(path);
    }

    static class StowRSFileVisitor extends SimpleFileVisitor<Path> {
        private final StowRSFileConsumer<Path> consumer;
        private final boolean continueVisit;

        StowRSFileVisitor(StowRSFileConsumer<Path> consumer, boolean continueVisit){
            this.consumer = consumer;
            this.continueVisit = continueVisit;
        }

        @Override
        public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
            consumer.accept(path);
            return continueVisit ? FileVisitResult.CONTINUE : FileVisitResult.TERMINATE;
        }
    }

    interface StowRSFileConsumer<Path> {
        void accept(Path path) throws IOException;
    }

    interface StowRSFileFunction<Path> {
        void apply(Path path) throws IOException;
    }
}
