package edu.princeton.diglib.md.metsCompiler.db;

public class UnsupportedNamespaceException extends Exception {

    private static final long serialVersionUID = 1939313225538016478L;

    public UnsupportedNamespaceException() {}

    public UnsupportedNamespaceException(String message) {
        super(message);
    }

}
