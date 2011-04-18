/**
 * METSCompiler.java <br/>
 * $LastChangedDate$ <br/>
 * $Author$ <br/>
 * $Rev$
 */
package edu.princeton.diglib.md.metsCompiler;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import edu.princeton.diglib.jpxData.JpxDataExtractor;
import edu.princeton.diglib.md.mets.AmdSec;
import edu.princeton.diglib.md.mets.FileSec;
import edu.princeton.diglib.md.mets.MdSec;
import edu.princeton.diglib.md.mets.Mets;
import edu.princeton.diglib.md.mets.MetsHdr;
import edu.princeton.diglib.md.mets.MetsReader;
import edu.princeton.diglib.md.mets.MetsWriter;
import edu.princeton.diglib.md.mets.StructMap;
import edu.princeton.diglib.md.mets.FileSec.FileGrp;
import edu.princeton.diglib.md.mets.FileSec.FileGrp.File;
import edu.princeton.diglib.md.mets.FileSec.FileGrp.File.FLocat;
import edu.princeton.diglib.md.mets.LocatorElement.LOCTYPE;
import edu.princeton.diglib.md.mets.MdSec.MDTYPE;
import edu.princeton.diglib.md.mets.MdSec.MdWrap;
import edu.princeton.diglib.md.mets.StructMap.Div;
import edu.princeton.diglib.md.mets.StructMap.Div.Fptr;
import edu.princeton.diglib.md.mets.StructMap.Div.Mptr;
import edu.princeton.diglib.md.metsCompiler.db.EntityAccessor;
import edu.princeton.diglib.md.utils.IDGen;

/**
 * @author <a href="mailto:jstroop@princeton.edu">Jon Stroop</a>
 * @since Sep 15, 2010
 */
public class METSCompiler {

    // static?
    private static MetsReader metsReader;
    private static MetsWriter metsWriter;
    private static EntityAccessor accessor;
    private static IDGen idgen;
    private static boolean opaquifyOBJID;

    private static Map<String, String> idMap;
    private static Map<String, String> admidMap;

    /**
     * Setting the boolean argument to true will make the OBJIDs on the METS
     * uuids if the currrent value is a path.
     * 
     * @throws DatatypeConfigurationException
     * @throws ParserConfigurationException
     */
    public METSCompiler(EntityAccessor accessor, String outURL, boolean opaquify)
            throws ParserConfigurationException, DatatypeConfigurationException {
        metsReader = new MetsReader();
        metsWriter = new MetsWriter();
        METSCompiler.accessor = accessor;
        opaquifyOBJID = opaquify;
        idgen = new IDGen(4);
        idMap = new HashMap<String, String>(250);
        admidMap = new HashMap<String, String>(250);

    }

    public void compileToFile(String pathToSeed, java.io.File file) throws SAXException,
            IOException, MissingRecordException, ParseException {

        Mets srcMets = metsReader.read(new FileInputStream(pathToSeed));
        Mets cmpMets = new Mets();

        compile(srcMets, cmpMets);

        try {
            metsWriter.writeToFile(cmpMets, file);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void compileToOutputStream(String pathToSeed, OutputStream out)
            throws TransformerException, SAXException, IOException, MissingRecordException,
            ParseException {

        Mets srcMets = metsReader.read(new FileInputStream(pathToSeed));
        Mets cmpMets = new Mets();

        compile(srcMets, cmpMets);

        metsWriter.writeToOutputStream(cmpMets, out);

    }

    private static void compile(Mets srcMets, Mets cmpMets) throws SAXException, IOException,
            MissingRecordException, ParseException {
        idgen.reset();
        idMap.clear();
        cmpMets.setFileSec(new FileSec());// No fileSec by default
        doRoot(srcMets, cmpMets);
        doHeader(srcMets, cmpMets);
        doDmdSec(srcMets, cmpMets);
        doAmdSec(srcMets, cmpMets);
        doFileSecThumb(srcMets, cmpMets);
        doStructMaps(srcMets, cmpMets);
    }

    private static void doRoot(Mets src, Mets cmp) {
        // OBJID
        if (!src.getOBJID().contains("/")) // slashes indicate temporary paths
            // used during development
            cmp.setOBJID(src.getOBJID());
        else if (src.getOBJID().contains("/") && opaquifyOBJID)
            cmp.setOBJID(UUID.randomUUID().toString());
        else
            cmp.setOBJID(src.getOBJID());
        // TYPE
        cmp.setType("CompiledDigitalObject");
    }

    private static void doHeader(Mets src, Mets cmp) {
        MetsHdr sHdr = src.getMetsHdr();

        MetsHdr cHdr = new MetsHdr();

        cmp.setMetsHdr(cHdr);
        cHdr.setCREATEDATE(sHdr.getCREATEDATE());
        cHdr.setMetsDocumentID(sHdr.getAltRecordID().get(0));
        cHdr.getAgent().add(sHdr.getAgent().get(0));
    }

    /**
     * @param srcMets
     * @param cmpMets
     * @throws IOException
     * @throws SAXException
     * @throws MissingRecordException
     */

    // this is OK as a test, but we're going to need to do as part of the
    // structMap, so that we can keep track of the ID
    // maybe we call this function for each DMDID we run accross? (with the ID
    // as an arg?)
    private static void doDmdSec(Mets src, Mets cmp) throws SAXException, IOException,
            MissingRecordException {

        for (MdSec dmdSec : src.getDmdSec()) { // assumes there's an mdRef and
            // not an mdWrap
            if (dmdSec.getMdRef() != null) {
                String oldId = dmdSec.getID();
                String newId = idgen.mint();
                idMap.put(oldId, newId);

                String mdataUri = dmdSec.getMdRef().getXlinkHREF();
                MdSec.MDTYPE mdtype = dmdSec.getMdRef().getMDTYPE();

                try {// db
                    String path = accessor.getUriIndex().get(mdataUri).getPath();
                    Document doc = metsReader.getDocBuilder().parse(path);
                    Element root = doc.getDocumentElement();

                    MdSec cmpDmdSec = new MdSec(newId);
                    MdWrap wrap = new MdWrap(mdtype);
                    wrap.getXmlData().add(root);
                    cmpDmdSec.setMdWrap(wrap);

                    cmp.getDmdSec().add(cmpDmdSec);

                } catch (NullPointerException e) {
                    MetsHdr srcHdr = src.getMetsHdr();
                    String srcUri = srcHdr.getAltRecordID().get(0).getIdentifier();
                    String msg = "Could not retrieve " + mdataUri + " from the database. Skipping "
                            + srcUri;
                    throw new MissingRecordException(msg);
                }
            }
        }
    }

    /**
     * Important - call this BEFORE {@link #doStructMaps(Mets, Mets)}. It
     * populates our {@link #admidMap}. Not ideal, but we're in a hurry.
     * 
     * @param src
     * @param cmp
     * @throws MissingRecordException
     */
    private static void doAmdSec(Mets src, Mets cmp) throws MissingRecordException {
        List<Div> aggregatesDivs = null;
        for (StructMap smap : src.getStructMap()) {
            if (smap.getType().equals("RelatedObjects")) {
                Div rootDiv = smap.getDiv();
                for (Div div : rootDiv.getDiv()) {
                    if (div.getID().equals("aggregates")) {
                        aggregatesDivs = div.getDiv();
                    }
                }
            }
        }

        AmdSec amdSec = new AmdSec();
        cmp.getAmdSec().add(amdSec);

        for (Div div : aggregatesDivs) {
            String mptrUri = div.getMptr().get(0).getXlinkHREF();

            try {
                String path;

                // NPE gets thrown here if we can't find the record
                path = accessor.getUriIndex().get(mptrUri).getPath();

                Mets iMets;
                iMets = metsReader.read(new FileInputStream(path));
                FileGrp fileGrp;
                fileGrp = iMets.getFileSec().getFileGrp().get(0);
                for (File file : fileGrp.getFile()) {
                    if (file.getUse().equals("deliverable")) {

                        // check if the URI is in our lookup, if not, mint new
                        String admid = admidMap.get(mptrUri);

                        if (admid == null) { // then we haven't worked on this
                            // one yet
                            admid = idgen.mint();
                            admidMap.put(mptrUri, admid);
                        }

                        // FLocat
                        FLocat fcat = file.getFLocat().get(0);
                        // href
                        String url = fcat.getXlinkHREF();
                        java.io.File imgFile;
                        imgFile = new java.io.File(delivUriPath(url));
                        Element mix = JpxDataExtractor.extractDimensionsAsMix(imgFile);
                        MdSec techMd = new MdSec(admid);
                        MdWrap wrap = new MdWrap(MDTYPE.NISOIMG);
                        techMd.setMdWrap(wrap);
                        wrap.getXmlData().add(mix);
                        amdSec.getTechMD().add(techMd);
                    }
                }
            } catch (Exception e) {
                MetsHdr srcHdr = src.getMetsHdr();
                String srcUri = srcHdr.getAltRecordID().get(0).getIdentifier();
                String msg = "Could not retrieve " + mptrUri + " from the database. Skipping "
                        + srcUri;
                String cause = "Cause: " + e.getMessage();
                throw new MissingRecordException(msg + System.getProperty("line.separator") + cause);
            }

        }
    }

    private static void doStructMaps(Mets src, Mets cmp) throws MissingRecordException,
            FileNotFoundException, SAXException, ParseException, IOException {
        // get rid of the default structMap; just easier
        cmp.getStructMap().clear();

        // Set up the fileGrp for the devliverable images
        FileGrp fileGrp = new FileGrp();
        fileGrp.setUse("deliverables");
        cmp.getFileSec().getFileGrp().add(1, fileGrp);

        for (StructMap srcSmap : src.getStructMap()) {

            StructMap cmpSmap = new StructMap();
            cmp.getStructMap().add(cmpSmap);

            if (srcSmap.getLabel() != null) cmpSmap.setLabel(srcSmap.getLabel());
            if (srcSmap.getType() != null) cmpSmap.setType(srcSmap.getType());

            doDiv(srcSmap.getDiv(), cmpSmap.getDiv(), src, cmp);

        }
    }

    private static void doDiv(Div srcDiv, Div cmpDiv, Mets src, Mets cmp)
            throws MissingRecordException, FileNotFoundException, SAXException, ParseException,
            IOException {

        if (!srcDiv.getDMDID().isEmpty()) {
            String srcId = srcDiv.getDMDID().get(0); // assuming one for now
            String cmpId = idMap.get(srcId); // error handling?
            cmpDiv.getDMDID().add(cmpId);
        }

        if (srcDiv.getLabel() != null) cmpDiv.setLabel(srcDiv.getLabel());
        if (srcDiv.getType() != null) cmpDiv.setType(srcDiv.getType());
        if (srcDiv.getORDER() != null) cmpDiv.setORDER(srcDiv.getORDER());

        // mptr
        if (!srcDiv.getMptr().isEmpty()) {
            for (Mptr mptr : srcDiv.getMptr()) {
                // a new fptr
                Fptr fptr = new Fptr();
                cmpDiv.getFptr().add(fptr);

                // get the mptr hrefL the URI for the record we need
                String mptrUri = mptr.getXlinkHREF();

                /*
                 * check if the URI is in our ID lookup, if not, mint a new id
                 * and update our new filesec
                 */
                String fileId = idMap.get(mptrUri);
                if (fileId == null) {
                    fileId = idgen.mint();
                    idMap.put(mptrUri, fileId);

                    /*
                     * Check if the URI is in our ADMID lookup, if not, mint a
                     * new id. doAmdSec also uses.
                     */
                    String admid = admidMap.get(mptrUri);
                    if (admid == null) { // then we haven't worked on this one
                        // yet
                        admid = idgen.mint();
                        admidMap.put(mptrUri, admid);
                    }

                    try {
                        String path;

                        // NPE gets thrown here if we can't find the record
                        path = accessor.getUriIndex().get(mptrUri).getPath();

                        Mets iMets;
                        iMets = metsReader.read(new FileInputStream(path));
                        FileGrp fileGrp;
                        fileGrp = iMets.getFileSec().getFileGrp().get(0);
                        for (File file : fileGrp.getFile()) {
                            if (file.getUse().equals("deliverable")) {
                                // ID
                                file.setID(fileId);
                                file.setUse(null);
                                // ADMID
                                file.getADMID().add(admid);
                                // FLocat
                                FLocat fcat = file.getFLocat().get(0);
                                // href
                                String url = fcat.getXlinkHREF();
                                fcat.setXlinkHREF(delivUriUrn(url));
                                // loctype
                                fcat.setLOCTYPE(LOCTYPE.URN);

                                cmp.getFileSec().getFileGrp().get(1).getFile().add(file);
                            }
                        }
                    } catch (NullPointerException e) {
                        MetsHdr srcHdr = src.getMetsHdr();
                        String srcUri = srcHdr.getAltRecordID().get(0).getIdentifier();
                        String msg = "Could not retrieve " + mptrUri
                                + " from the database. Skipping " + srcUri;
                        throw new MissingRecordException(msg);
                    }

                }

                fptr.setFILEID(fileId);
            }
        }

        for (Div childDiv : srcDiv.getDiv()) {

            String id = childDiv.getID();
            // skip over isAggregatedBy for now.
            if ((id != null && !id.equals("isAggregatedBy")) || id == null) {
                Div newChildDiv = new Div();
                cmpDiv.getDiv().add(newChildDiv);
                doDiv(childDiv, newChildDiv, src, cmp); // recursive
            } else if ((id != null && id.equals("isAggregatedBy"))) {
                Div newChildDiv = new Div();
                cmpDiv.getDiv().add(newChildDiv);

                newChildDiv.setType("IsPartOf");

                Mptr badMptr = childDiv.getDiv().get(0).getMptr().get(0);
                String coll = hackOutPudlNo(badMptr.getXlinkHREF());
                newChildDiv.getCONTENTIDS().add(coll);

            }

            // TODO: collection identifier
        }
    }

    // TODO: throw something if we there is no fileGrp
    private static void doFileSecThumb(Mets src, Mets cmp) {
        FileGrp grp = src.getFileSec().getFileGrp().get(0);
        for (File file : grp.getFile()) {
            if (file.getUse().equals("deliverable")) {
                file.setUse(null);
                file.setID(idgen.mint());
                FileGrp newGrp = new FileGrp();
                newGrp.setUse("thumbnail");
                newGrp.getFile().add(file);
                // FLocat
                FLocat fcat = file.getFLocat().get(0);
                // href
                String url = fcat.getXlinkHREF();
                fcat.setXlinkHREF(delivUriUrn(url));
                // loctype
                fcat.setLOCTYPE(LOCTYPE.URN);
                cmp.getFileSec().getFileGrp().add(newGrp);
            }
        }
    }

    /*
     * String hack to change devliverable file URIs to URNs (METS will all have
     * this eventually
     */
    private static String delivUriUrn(String uri) {
        String oldBase = "http://diglib.princeton.edu/images/deliverable/";
        String newBase = "urn:pudl:images:deliverable:";
        return uri.replace(oldBase, newBase);
    }

    /*
     * String hack to change devliverable file URIs to URNs (METS will all have
     * this eventually
     */
    // TODO: make a property
    private static String delivUriPath(String uri) {

        String oldBase = "http://diglib.princeton.edu/images/deliverable/";
        String newBase = "/mnt/libserv37/dps/";
        return uri.replace(oldBase, newBase);
    }

    private static String hackOutPudlNo(String aggBy) {
        int begin = aggBy.indexOf("pudl");
        int end = begin + 8;
        return aggBy.substring(begin, end);
    }

}
