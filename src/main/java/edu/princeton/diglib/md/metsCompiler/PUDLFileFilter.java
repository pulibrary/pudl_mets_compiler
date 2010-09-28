/**
 * PudlFileFilter.java
 */
package edu.princeton.diglib.md.metsCompiler;

import java.io.File;
import java.io.FileFilter;

/**
 * @author <a href="mailto:jstroop@princeton.edu">Jon Stroop</a>
 * @since Friday, August 14 2009
 */
public class PUDLFileFilter implements FileFilter {

	public PUDLFileFilter() {}

	public boolean accept(File file) {
		return !file.isHidden() && !file.isDirectory()
				&& allowedExtension(file);
	}

	private boolean allowedExtension(File file) {
		String name = file.getName();
		if (name.endsWith(".mets"))
			return true;
		if (name.endsWith(".mods"))
			return true;
		if (name.endsWith(".vra"))
			return true;
		if (name.endsWith(".ead"))
			return true;
		if (name.endsWith(".tei"))
			return true;
		else
			return false;

	}
}
