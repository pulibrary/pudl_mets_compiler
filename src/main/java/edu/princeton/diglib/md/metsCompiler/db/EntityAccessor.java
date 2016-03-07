/**
 * EntityAccessor.java <br/>
 * $LastChangedDate$ <br/>
 * $Author$ <br/>
 * $Rev$
 */
package edu.princeton.diglib.md.metsCompiler.db;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.persist.EntityStore;
import com.sleepycat.persist.PrimaryIndex;
import com.sleepycat.persist.SecondaryIndex;

import edu.princeton.diglib.md.metsCompiler.db.PUDLMETSEntity.TYPE;

public class EntityAccessor {
	private PrimaryIndex<String, PUDLMETSEntity> uriIndex;
	private SecondaryIndex<TYPE, String, PUDLMETSEntity> typeIndex;

	/**
	 * Encapsulates Access to our indicies.
	 * 
	 * @param store
	 * @throws DatabaseException
	 */
	// Open the indices
	public EntityAccessor(EntityStore store) throws DatabaseException {
		uriIndex = store.getPrimaryIndex(String.class, PUDLMETSEntity.class);
		typeIndex = store.getSecondaryIndex(uriIndex, TYPE.class, "type");
	}

	/**
	 * @return the uriIndex
	 */
	public PrimaryIndex<String, PUDLMETSEntity> getUriIndex() {
		return uriIndex;
	}

	/**
	 * @return the typeIndex
	 */
	public SecondaryIndex<TYPE, String, PUDLMETSEntity> getTypeIndex() {
		return typeIndex;
	}
	
	

}
