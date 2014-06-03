PUDL METS Compiler
=================

How to Build
------------
From the root directory of the project (the one that contains "pom.xml") call

me@my:{root}$ mvn clean assembly:assembly

If the build is successful, a target directory will be created. Is should contain. among other things, a jar file called mets-compiler-jar-with-dependencies.jar

How to Run
----------
The aforementioned jar file is executable, so

```
me@my:{root}$ java -jar target/mets-compiler-jar-with-dependencies.jar
```

There is also a shell script, `run.sh`, in the root of the project that wraps
this command.

Configuration
-------------
Each time the application is executed, it looks for a file called 
"config.xml" on the classpath.  If this file can not be found, it will be
created and the application will exit:

"A file called config.xml has been created in this directory. Please update 
it and run again."

Edit that file and run again. Paths may be absolute (safest) or relative to the 
project root directory.

This file can be edited before each run to facilitate
compilation of different projects.  The comments in the file should explain
the function of each property.

Note that calling `mvn clean` will delete the target directory, config.xml, 
and the application log.
