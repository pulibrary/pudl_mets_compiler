package edu.princeton.diglib.md.metsCompiler;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import edu.princeton.diglib.md.mets.FileSec;
import edu.princeton.diglib.md.mets.FileSec.FileGrp;
import edu.princeton.diglib.md.mets.FileSec.FileGrp.File;
import edu.princeton.diglib.md.mets.FileSec.FileGrp.File.FLocat;
import edu.princeton.diglib.md.mets.LocatorElement.LOCTYPE;
import edu.princeton.diglib.md.mets.MdSec;
import edu.princeton.diglib.md.mets.MdSec.MdWrap;
import edu.princeton.diglib.md.mets.Mets;
import edu.princeton.diglib.md.mets.MetsHdr;
import edu.princeton.diglib.md.mets.MetsReader;
import edu.princeton.diglib.md.mets.MetsWriter;
import edu.princeton.diglib.md.mets.StructMap;
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

	private static String imagesHome;

	/**
	 * Setting the boolean argument to true will make the OBJIDs on the METS
	 * uuids if the currrent value is a path.
	 * 
	 * @throws DatatypeConfigurationException
	 * @throws ParserConfigurationException
	 */
	public METSCompiler(EntityAccessor accessor, String outURL, boolean opaquify) throws ParserConfigurationException,
			DatatypeConfigurationException {
		metsReader = new MetsReader();
		metsWriter = new MetsWriter();
		METSCompiler.accessor = accessor;
		opaquifyOBJID = opaquify;
		idgen = new IDGen(5);
		idMap = new HashMap<String, String>(250);
		imagesHome = App.getLocalProps().getProperty("METSCompiler.imagesHome");

	}

	public void compileToFile(String pathToSeed, java.io.File file) throws SAXException, IOException,
			MissingRecordException, ParseException {

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

	public void compileToOutputStream(String pathToSeed, OutputStream out) throws TransformerException, SAXException,
			IOException, MissingRecordException, ParseException {

		Mets srcMets = metsReader.read(new FileInputStream(pathToSeed));
		Mets cmpMets = new Mets();

		compile(srcMets, cmpMets);

		metsWriter.writeToOutputStream(cmpMets, out);

	}

	/*
	 * TODO: 
	 *  * ARK (not just NOID) in objid : ark:/88435/
	 *  * Checksums 
	 *     * calc SHA1
	 *     * check
	 *     * add to <file>
	 *  * RecordID from MODS
	 *  * Fix thumbnail
	 *  * URIs to /mnt paths
	 * 
	 */
			
	private static void compile(Mets srcMets, Mets cmpMets) throws SAXException, IOException, MissingRecordException,
			ParseException {
		idgen.reset();
		idMap.clear();
		cmpMets.setFileSec(new FileSec());// No fileSec by default
		doRoot(srcMets, cmpMets);
		doHeader(srcMets, cmpMets);
		doDmdSec(srcMets, cmpMets);
		doFileSecThumb(srcMets, cmpMets);
		doStructMaps(srcMets, cmpMets);
		doStructLink(srcMets, cmpMets);
	}

	private static void doRoot(Mets src, Mets cmp) {
		// OBJID
		// slashes indicate temporary paths used during development
		if (!src.getOBJID().contains("/"))
			cmp.setOBJID("ark:/88435/" + src.getOBJID());
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
		String docId = sHdr.getAltRecordID().get(0).getIdentifier();
		docId = docId.replace("http://diglib.princeton.edu/mdata/", "");
		
//		cHdr.setMetsDocumentID(sHdr.getAltRecordID().get(0));
		MetsHdr.RecordID rid = new MetsHdr.RecordID(docId);
		rid.setType("PUDL");
		cHdr.setMetsDocumentID(rid);
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
	private static void doDmdSec(Mets src, Mets cmp) throws SAXException, IOException, MissingRecordException {

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
					String msg = "Could not retrieve " + mdataUri + " from the database. Skipping " + srcUri;
					throw new MissingRecordException(msg);
				}
			}
		}
	}



	private static void doStructMaps(Mets src, Mets cmp) throws MissingRecordException, FileNotFoundException,
			SAXException, ParseException, IOException {
		// get rid of the default structMap; just easier
		cmp.getStructMap().clear();

		// Set up the fileGrp for the devliverable images
		FileGrp fileGrp = new FileGrp();
		fileGrp.setUse("masters");
		cmp.getFileSec().getFileGrp().add(1, fileGrp);

		for (StructMap srcSmap : src.getStructMap()) {

			StructMap cmpSmap = new StructMap();
			cmp.getStructMap().add(cmpSmap);

			if (srcSmap.getLabel() != null)
				cmpSmap.setLabel(srcSmap.getLabel());
			if (srcSmap.getType() != null)
				cmpSmap.setType(srcSmap.getType());

			doDiv(srcSmap.getDiv(), cmpSmap.getDiv(), src, cmp);

		}
	}

	private static void doDiv(Div srcDiv, Div cmpDiv, Mets src, Mets cmp) throws MissingRecordException,
			FileNotFoundException, SAXException, ParseException, IOException {

		if (!srcDiv.getDMDID().isEmpty()) {
			String srcId = srcDiv.getDMDID().get(0); // assuming one for now
			String cmpId = idMap.get(srcId); // error handling?
			cmpDiv.getDMDID().add(cmpId);
		}

		if (srcDiv.getLabel() != null)
			cmpDiv.setLabel(srcDiv.getLabel());
		if (srcDiv.getType() != null)
			cmpDiv.setType(srcDiv.getType());
		if (srcDiv.getORDER() != null)
			cmpDiv.setORDER(srcDiv.getORDER());
		if (srcDiv.getID() == null) {
			cmpDiv.setID(idgen.mint());
		}
		else {
			cmpDiv.setID(srcDiv.getID());
		}
		

		// mptr
		if (!srcDiv.getMptr().isEmpty()) {
			// for aggregated Object METS, as opposed to Image METS, which are
			// more common.
			if (srcDiv.getType() != null && srcDiv.getType().equals("AggregatedObject")) {
				for (Mptr mptr : srcDiv.getMptr()) {
					String mptrUri = mptr.getXlinkHREF();
					try {
						for (String contentId : srcDiv.getCONTENTIDS()) {
							cmpDiv.getCONTENTIDS().add(contentId);
						}

					} catch (NullPointerException e) {
						MetsHdr srcHdr = src.getMetsHdr();
						String srcUri = srcHdr.getAltRecordID().get(0).getIdentifier();
						String msg = "Could not retrieve Object METS " + mptrUri + " from the database. Skipping "
								+ srcUri;
						throw new MissingRecordException(msg);
					}

				}
			} else {
				for (Mptr mptr : srcDiv.getMptr()) {
					// a new fptr
					Fptr fptr = new Fptr();
					cmpDiv.getFptr().add(fptr);

					// get the mptr href
					// the URI for the record we need
					String mptrUri = mptr.getXlinkHREF();

					/*
					 * check if the URI is in our ID lookup, if not, mint a new
					 * id and update our new filesec
					 */
					String fileId = idMap.get(mptrUri);
					if (fileId == null) {
						fileId = idgen.mint();
						idMap.put(mptrUri, fileId);

						try {
							String path;

							// NPE gets thrown here if we can't find the record
							path = accessor.getUriIndex().get(mptrUri).getPath();

							Mets iMets;
							iMets = metsReader.read(new FileInputStream(path));
							FileGrp fileGrp;
							fileGrp = iMets.getFileSec().getFileGrp().get(0);
							// TODO: make this the archival files instead
							for (File file : fileGrp.getFile()) {
								if (file.getUse().equals("master")) {
									// ID
									file.setID(fileId);
									file.setUse(null);
									// FLocat
									FLocat fcat = file.getFLocat().get(0);
									// href
									String url = fcat.getXlinkHREF();
									
									fcat.setXlinkHREF(delivUriUrn(url));
									fcat.setLOCTYPE(LOCTYPE.URL);

									cmp.getFileSec().getFileGrp().get(1).getFile().add(file);
								}
							}
						} catch (NullPointerException e) {
							MetsHdr srcHdr = src.getMetsHdr();
							String srcUri = srcHdr.getAltRecordID().get(0).getIdentifier();
							String msg = "Could not retrieve Image METS " + mptrUri + " from the database. Skipping "
									+ srcUri;
							throw new MissingRecordException(msg);
						}

					}

					fptr.setFILEID(fileId);
				}
			}
		}

		for (Div childDiv : srcDiv.getDiv()) {

			String id = childDiv.getID();
			// skip over isAggregatedBy for now.
			if ((id != null && !id.equals("isAggregatedBy")) || id == null) {
				Div newChildDiv = new Div();
				cmpDiv.getDiv().add(newChildDiv);
				doDiv(childDiv, newChildDiv, src, cmp); // RECURSIVE CALL
			} else if ((id != null && id.equals("isAggregatedBy"))) {
				Div newChildDiv = new Div();
				cmpDiv.getDiv().add(newChildDiv);

				newChildDiv.setType("IsPartOf");

				Mptr badMptr = childDiv.getDiv().get(0).getMptr().get(0);
				String coll = getCollectionId(badMptr.getXlinkHREF());
				newChildDiv.getCONTENTIDS().add(coll);

			}

			// TODO: collection identifier
		}
	}

	// TODO: throw something if we there is no fileGrp
	private static void doFileSecThumb(Mets src, Mets cmp) {
		FileGrp grp = src.getFileSec().getFileGrp().get(0);
		for (File file : grp.getFile()) {
			if (file.getUse().equals("master")) {
				file.setUse(null);
				file.setID(idgen.mint());
				FileGrp newGrp = new FileGrp();
				newGrp.setUse("thumbnail");
				newGrp.getFile().add(file);
				// FLocat
				FLocat fcat = file.getFLocat().get(0);

				String url = fcat.getXlinkHREF();
				fcat.setXlinkHREF(delivUriUrn(url));
				fcat.setLOCTYPE(LOCTYPE.URL);
				
				cmp.getFileSec().getFileGrp().add(newGrp);
				
			}
		}
	}

	private static void doStructLink(Mets src, Mets cmp) {
		if (src.getStructLink() != null) {
			cmp.setStructLink(src.getStructLink());
		}
	}

	/*
	 * String hack to change devliverable file URIs to URNs (METS will all have
	 * this eventually
	 */
	private static String delivUriUrn(String uri) {
		String oldBase = "http://diglib.princeton.edu/images/master/";
		String newBase = "file://" + imagesHome;
		newBase = imagesHome.endsWith("/") ? newBase : newBase + "/";
		return uri.replace(oldBase, newBase);
	}


	private static String getCollectionId(String aggBy) {
	    int begin = "http://diglib.princeton.edu/mdata/".length();
	    int end = aggBy.length() - ".ead".length();
		return aggBy.substring(begin, end);
	}

}
