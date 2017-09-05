/**
 * Copyright © 2017, Christophe Marchand
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package top.marchand.maven.gaulois.compiler;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import top.marchand.xml.maven.plugin.xsl.AbstractCompiler;

@Mojo(name="gaulois-compiler", defaultPhase = LifecyclePhase.COMPILE)
public class GCMojo extends AbstractCompiler {
    
    /**
     * The directory containing generated classes of the project being tested. 
     * This will be included after the test classes in the test classpath.
     */
    @Parameter( defaultValue = "${project.build.outputDirectory}" )
    private File classesDirectory;
    
    @Parameter
    List<FileSet> filesets;
    
    @Parameter
    private File catalog;
    
    @Parameter(defaultValue = "${project.basedir}")
    private File projectBaseDir;
    
    /**
     * The list of directories where XSL sources are located in
     */
    @Parameter
    List<File> xslSourceDirs;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if(filesets==null) {
            getLog().error(LOG_PREFIX+"\n"+ERROR_MESSAGE);
            throw new MojoExecutionException(ERROR_MESSAGE);
        }
        if(xslSourceDirs==null) xslSourceDirs = new ArrayList<>();
        if(xslSourceDirs.isEmpty()) {
            xslSourceDirs.add(new File(projectBaseDir, "src/main/xsl"));
        }
        Log log = getLog();
        initSaxon();
        Path targetDir = classesDirectory.toPath();
        boolean hasError = false;
        Map<File,File> gauloisConfigToCompile = new HashMap<>();
        Map<File,File> xslToCompile = new HashMap<>();
        for(FileSet fs: filesets) {
            Path basedir = new File(fs.getDir()).toPath();
            for(Path p: fs.getFiles(log)) {
                File sourceFile = basedir.resolve(p).toFile();
                Path targetPath = p.getParent()==null ? targetDir : targetDir.resolve(p.getParent());
                String sourceFileName = sourceFile.getName();
//                getLog().debug(LOG_PREFIX+" sourceFileName="+sourceFileName);
//                String targetFileName = FilenameUtils.getBaseName(sourceFileName).concat(".sef");
//                getLog().debug(LOG_PREFIX+" targetFileName="+targetFileName);
                // we keep the same extension for gaulois config files
                File targetFile = targetPath.resolve(sourceFileName).toFile();
                //compileFile(sourceFile, targetFile);
                gauloisConfigToCompile.put(sourceFile, targetFile);
                scanGauloisFile(sourceFile, targetFile, gauloisConfigToCompile, xslToCompile, targetDir);
            }
        }
    }
    
    private static final String LOG_PREFIX = "[gaulois-compiler]";
    private static final String ERROR_MESSAGE = "<filesets>\n\t<fileset>\n\t\t<dir>src/main/xsl...</dir>\n\t</fileset>\n</filesets>\n is required in gaulois-compiler-maven-plugin configuration";

    @Override
    public File getCatalogFile() {
        return catalog;
    }
    /**
     * Scans a gaulois config file to extract all xslt files, and store them into <tt>xslToCompile</tt>
     * <tt>xslt/@href</tt> <strong>MUST</strong> be an absolute URI, in cp:/ protocol. 
     * Else, the whole gaulois-pipe config file is ignored.
     * @param sourceFile
     * @param xslToCompile
     * @param targetDir 
     */
    private void scanGauloisFile(File sourceFile, File targetFile, Map<File, File> gauloisConfigToCompile, Map<File, File> xslToCompile, Path targetDir) {
        
    }
}