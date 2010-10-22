/**
 * Filters.java <br/>
 * $LastChangedDate$ <br/>
 * $Author$ <br/>
 * $Rev$
 */
package edu.princeton.diglib.md.metsCompiler;

import java.io.File;
import java.io.FileFilter;

/**
 * @author <a href="mailto:jstroop@princeton.edu">Jon Stroop</a>
 * @since Sep 28, 2010
 */
public class Filters {

    /**
     * Filter for our METS files
     * 
     * @author <a href="mailto:jstroop@princeton.edu">Jon Stroop</a>
     * @since Sep 28, 2010
     */
    public static class METSFilter implements FileFilter {
        public boolean accept(File file) {
            boolean visible = !file.isHidden();
            boolean isFile = file.isFile();
            boolean isMETS = file.getName().endsWith(".mets");
            return visible && isFile && isMETS;
        }
    }

    /**
     * Filter for our EAD files
     * 
     * @author <a href="mailto:jstroop@princeton.edu">Jon Stroop</a>
     * @since Sep 28, 2010
     */
    public static class EADFilter implements FileFilter {
        public boolean accept(File file) {
            boolean visible = !file.isHidden();
            boolean isFile = file.isFile();
            boolean isEAD = file.getName().endsWith(".ead");
            return visible && isFile && isEAD;
        }
    }

    /**
     * Filter for all files known by the PUDL environment
     * 
     * @author <a href="mailto:jstroop@princeton.edu">Jon Stroop</a>
     * @since Friday, August 14 2009
     */
    public static class PUDLFileFilter implements FileFilter {

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
    
    
    
    
    
    
    /**
     * Filter for our Metadata directories
     * 
     * @author <a href="mailto:jstroop@princeton.edu">Jon Stroop</a>
     * @since Sep 28, 2010
     */
    public static class MDDirFilter implements FileFilter {
        public boolean accept(File dir) {
            boolean visible = !dir.isHidden();
            boolean isDir = dir.isDirectory();
            boolean notWork = !dir.getName().equals("work");
            return visible && isDir && notWork;
        }
    }

}
