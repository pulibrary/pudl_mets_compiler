package edu.princeton.diglib.md.metsCompiler.db;

import java.io.File;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

import org.codehaus.stax2.XMLInputFactory2;
import org.codehaus.stax2.XMLStreamReader2;

import com.ctc.wstx.stax.WstxInputFactory;

import edu.princeton.diglib.md.NS;
import edu.princeton.diglib.md.metsCompiler.db.PUDLMETSEntity.TYPE;

/**
 * <p>
 * Class for extracting URIs for XML documents in the PUDL. Optimized for speed.
 * <p>
 * To use, create in instance and call the {@link #extract(File)} method
 * repeatedly, i.e.:
 * 
 * <pre>
 * EntityBuilder uriExtractor = new EntityBuilder();
 * File[] files = new File[] { vraTest, modsTest, metsTest, eadTest, teiTest };
 * for (File f : files)
 *     System.out.println(uriExtractor.extract(new FileInputStream(f)));
 * </pre>
 * 
 * @author <a href="mailto:jstroop@princeton.edu">Jon Stroop</a>
 * @since Thursday, August 20 2009
 * 
 */
/*
 * This is complicated code, but it's built for speed, not looks :)
 */
public class EntityBuilder {
    private static XMLInputFactory2 xmlif;
    private static XMLStreamReader2 xmlr;

    // public static void main(String[] args) throws XMLStreamException,
    // MissingURIException, UnsupportedNamespaceException, MissingTypeException
    // {
    // File f = new
    // File("/home/jstroop/workspace/pudl-data-dev/mdata/pudl0032/ns1113.mods");
    // EntityBuilder eb = new EntityBuilder();
    // PUDLMETSEntity pme = eb.build(f);
    // System.out.println(pme.getUri());
    // }

    public EntityBuilder() {
        Boolean f = Boolean.FALSE;
        Boolean t = Boolean.TRUE;
        xmlif = (XMLInputFactory2) WstxInputFactory.newInstance();
        xmlif.configureForSpeed();
        xmlif.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, f);
        xmlif.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, f);
        xmlif.setProperty(XMLInputFactory.IS_VALIDATING, f);
        xmlif.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, t);
        xmlif.setProperty(XMLInputFactory.IS_COALESCING, f);
    }

    public PUDLMETSEntity build(File thisFile) throws XMLStreamException, MissingURIException,
            UnsupportedNamespaceException, MissingTypeException {
        xmlr = (XMLStreamReader2) xmlif.createXMLStreamReader(thisFile);

        // the namespace of the root element tells us what kind of doc

        do {} while (xmlr.next() != XMLEvent.START_ELEMENT);
        String uri = xmlr.getNamespaceURI();
        NS docNs = NS.fromNamespace(uri);
        PUDLMETSEntity entity = new PUDLMETSEntity(thisFile.getAbsolutePath());
        switch (docNs) {
            case METS:
                entityFromMETS(entity);
                break;
            case VRA_4:
                entityFromVRA(entity);
                break;
            case MODS:
                entityFromMODS(entity);
                break;
            case EAD:
                entityFromEAD(entity);
                break;
            case TEI:
                entityFromTEI(entity);
                break;
            default:
                throw new UnsupportedNamespaceException();
        }
        xmlr.closeCompletely();
        // closes the underlying input stream created from the File
        return entity;
    }

    private static void entityFromTEI(PUDLMETSEntity entity) throws XMLStreamException,
            MissingURIException {
        while (xmlr.hasNext() && entity.getUri() == null) {
            int e = xmlr.next();
            if (e == XMLEvent.START_ELEMENT && xmlr.getLocalName() == "idno") {
                // skip the attributes
                do {} while (xmlr.next() != XMLEvent.CHARACTERS);
                if (xmlr.getText().startsWith("http://")) entity.setUri(xmlr.getText());
            }
        }
        if (entity.getUri() == null)
            throw new MissingURIException(entity.getPath() + "is missing a URI");
    }

    private static void entityFromEAD(PUDLMETSEntity entity) throws XMLStreamException,
            MissingURIException {
        while (xmlr.hasNext() && entity.getUri() == null) {
            int e = xmlr.next();
            if (e == XMLEvent.START_ELEMENT && xmlr.getLocalName() == "eadid") {
                // skip the attributes
                do {} while (xmlr.next() != XMLEvent.CHARACTERS);
                if (xmlr.getText().startsWith("http://")) entity.setUri(xmlr.getText());
            }
        }
        if (entity.getUri() == null)
            throw new MissingURIException(entity.getPath() + "is missing a URI");
    }

    // untested
    private static void entityFromMETS(PUDLMETSEntity entity) throws MissingURIException,
            XMLStreamException, MissingTypeException {

        // we're already at the document element, which is where our @TYPE is
        // TYPE
        int d = 0;
        do {
            if (xmlr.getAttributeLocalName(d).equals("TYPE")) {
                String value = xmlr.getAttributeValue(d);
                entity.setType(TYPE.fromValue(value));
            } else
                d++;
        } while (entity.getType() == null);

        while (xmlr.hasNext() && (entity.getType() == null || entity.getUri() == null)) {
            int e = xmlr.next();
            // URI
            if (e == XMLEvent.START_ELEMENT && xmlr.getLocalName() == "altRecordID") {
                xmlr.next();
                entity.setUri(xmlr.getText());
            }
        }
        if (entity.getUri() == null)
            throw new MissingURIException(entity.getPath() + "is missing a URI");
        if (entity.getType() == null)
            throw new MissingTypeException(entity.getPath() + "is missing a @TYPE");
    }

    private static void entityFromMODS(PUDLMETSEntity entity) throws XMLStreamException,
            MissingURIException {
        entity.setType(TYPE.DESCRIPTIVE);
        while (xmlr.hasNext() && entity.getUri() == null) {
            int e = xmlr.next();
            if (e == XMLEvent.START_ELEMENT && xmlr.getLocalName() == "recordIdentifier") {
                // we may have other recordIdentifiers that aren't http uris
                // skip the attributes
                do {} while (xmlr.next() != XMLEvent.CHARACTERS);
                if (xmlr.getText().startsWith("http://")) entity.setUri(xmlr.getText());
            }
        }
        if (entity.getUri() == null)
            throw new MissingURIException(entity.getPath() + "is missing a URI");
    }

    private static void entityFromVRA(PUDLMETSEntity entity) throws XMLStreamException,
            MissingURIException {
        entity.setType(TYPE.DESCRIPTIVE);
        while (xmlr.hasNext() && entity.getUri() == null) {
            int e = xmlr.next();
            if (e == XMLEvent.START_ELEMENT && xmlr.getLocalName() == "work") {
                int c = 0;
                do {
                    if (xmlr.getAttributeLocalName(c).equals("refid"))
                        entity.setUri(xmlr.getAttributeValue(c));
                    else
                        c++;
                } while (entity.getUri() == null);
            }
        }
        if (entity.getUri() == null)
            throw new MissingURIException(entity.getPath() + "is missing a URI");
    }
}
