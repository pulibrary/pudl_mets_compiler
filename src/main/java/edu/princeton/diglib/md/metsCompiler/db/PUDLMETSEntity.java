/**
 * PUDLMETSEntity.java <br/>
 * $LastChangedDate: 2010-09-15 17:05:17 -0400 (Wed, 15 Sep 2010) $ <br/>
 * $Author: jstroop $ <br/>
 * $Rev: 928 $
 */
package edu.princeton.diglib.md.metsCompiler.db;

import com.sleepycat.persist.model.Entity;
import com.sleepycat.persist.model.PrimaryKey;
import com.sleepycat.persist.model.Relationship;
import com.sleepycat.persist.model.SecondaryKey;

/**
 * @author <a href="mailto:jstroop@princeton.edu">Jon Stroop</a>
 * @since Sep 14, 2010
 */
@Entity
public class PUDLMETSEntity {
	@PrimaryKey
	private String uri;
	@SecondaryKey(relate = Relationship.MANY_TO_ONE)
	private TYPE type;

	private String path; // not indexed

	public PUDLMETSEntity() {}

	public PUDLMETSEntity(String path) {
		this.path = path;
	}

	/**
	 * @return the uri
	 */
	public String getUri() {
		return uri;
	}

	/**
	 * @param uri
	 *            the uri to set
	 */
	public void setUri(String recordURI) {
		this.uri = recordURI;
	}

	/**
	 * @return the path
	 */
	public String getPath() {
		return path;
	}

	/**
	 * @param path
	 *            the path to set
	 */
	public void setPath(String systemPath) {
		this.path = systemPath;
	}

	/**
	 * @return the type
	 */
	public TYPE getType() {
		return type;
	}

	/**
	 * @param type
	 *            the type to set
	 */
	public void setType(TYPE recordType) {
		this.type = recordType;
	}

	public static enum TYPE {
		OBJECT("DigitalObject"), //
		IMAGE("DigitalImage"), //
		DESCRIPTIVE("DESCRIPTIVE"), //
		TEI("TEI"), //
		EAD("EAD");

		private String value;

		private TYPE(String value) {
			this.value = value;
		}

		public String value() {
			return value;
		}

		public static TYPE fromValue(String v) {
			for (TYPE t : TYPE.values()) {
				if (t.value.equals(v)) {
					return t;
				}
			}
			throw new IllegalArgumentException(v);
		}
	}
}
