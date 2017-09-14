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
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.XsltTransformer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLFilter;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.ParserAdapter;
import org.xml.sax.helpers.XMLFilterImpl;
import top.marchand.maven.gaulois.compiler.utils.GauloisConfigScanner;
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
    List<FileSet> gauloisPipeFilesets;
    
    @Parameter
    private File catalog;
    
    @Parameter(defaultValue = "${project.basedir}")
    private File projectBaseDir;
    
    private XsltExecutable gauloisCompilerXsl;
    
    /**
     * The list of directories where XSL sources are located in
     */
    @Parameter
    List<File> xslSourceDirs;
    
    public static final SAXParserFactory PARSER_FACTORY = SAXParserFactory.newInstance();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if(gauloisPipeFilesets==null) {
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
        Map<Source,File> xslToCompile = new HashMap<>();
        getLog().debug(LOG_PREFIX+" looking for gaulois-pipe config files");
        for(FileSet fs: gauloisPipeFilesets) {
            Path basedir = new File(fs.getDir()).toPath();
            getLog().debug("looking in "+basedir.toString());
            for(Path p: fs.getFiles(log)) {
                getLog().debug("found "+p.toString());
                File sourceFile = basedir.resolve(p).toFile();
                Path targetPath = p.getParent()==null ? targetDir : targetDir.resolve(p.getParent());
                String sourceFileName = sourceFile.getName();
                // we keep the same extension for gaulois config files
                File targetFile = targetPath.resolve(sourceFileName).toFile();
                gauloisConfigToCompile.put(sourceFile, targetFile);
                hasError |= scanGauloisFile(sourceFile, targetFile, gauloisConfigToCompile, xslToCompile, targetDir);
            }
        }
        if(!hasError) {
            for(Source xslSource: xslToCompile.keySet()) {
                try {
                    getLog().debug(LOG_PREFIX+" compiling "+xslSource.getSystemId());
                    compileFile(xslSource, xslToCompile.get(xslSource));
                } catch (FileNotFoundException | SaxonApiException ex) {
                    getLog().warn(LOG_PREFIX+" while compiling "+xslSource.getSystemId(), ex);
                    hasError = true;
                }
            }
            Source xsl = new StreamSource(this.getClass().getResourceAsStream("/top/marchand/maven/gaulois/compiler/gaulois-compiler.xsl"));
            try {
                gauloisCompilerXsl = getXsltCompiler().compile(xsl);
                for(File gSrc: gauloisConfigToCompile.keySet()) {
                    getLog().debug(LOG_PREFIX+" compiling "+gSrc.getAbsolutePath());
                    compileGaulois(gSrc, gauloisConfigToCompile.get(gSrc));
                }
            } catch(Exception ex) {
                hasError = true;
                getLog().error(ex);
            }
        } else {
            getLog().warn(LOG_PREFIX+" Errors occured");
        }
    }
    
    private static final String LOG_PREFIX = "[gaulois-compiler]";
    private static final String ERROR_MESSAGE = "<gauloisPipeFilesets>\n\t<gauloisPipeFileset>\n\t\t<dir>src/main/xsl...</dir>\n\t</gauloisPipeFileset>\n</gauloisPipeFilesets>\n is required in gaulois-compiler-maven-plugin configuration";

    @Override
    public File getCatalogFile() {
        return catalog;
    }
    /**
     * Scans a gaulois config file to extract all xslt files, and store them into <tt>xslToCompile</tt>
     * <tt>xslt/@href</tt> <strong>MUST</strong> be an absolute URI, in cp:/ protocol. 
     * Else, the whole gaulois-pipe config file is ignored.
     * @param sourceFile The file to scan. It <strong>MUST</strong> be a gaulois config file.
     * @param targetFile The target file where scanned file will be stored.
     * @param gauloisConfigToCompile The Map to store all gaulois config file to compile
     * @param xslToCompile The Map to store all XSL to compile
     * @param targetDir The build dir
     * @return <tt>true</tt> if an error occured
     */
    protected boolean scanGauloisFile(File sourceFile, File targetFile, Map<File, File> gauloisConfigToCompile, Map<Source, File> xslToCompile, Path targetDir) {
        try {
            final XMLReader reader = new ParserAdapter(PARSER_FACTORY.newSAXParser().getParser());
            final GauloisConfigScanner scanner = new GauloisConfigScanner(xslSourceDirs, classesDirectory, getUriResolver(), getLog());
            XMLFilter filter = new XMLFilterImpl(reader) {
                @Override
                public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
                    super.startElement(uri, localName, qName, atts);
                    scanner.startElement(uri, localName, qName, atts);
                }
            };
            filter.parse(sourceFile.getAbsolutePath());
            if(scanner.hasErrors()) {
                for(String errorMsg: scanner.getErrorMessages()) {
                    getLog().error(errorMsg);
                }
            } else {
                xslToCompile.putAll(scanner.getXslToCompile());
                gauloisConfigToCompile.put(sourceFile, targetFile);
            }
            return scanner.hasErrors();
        } catch(ParserConfigurationException | SAXException | IOException ex) {
            getLog().error("while scanning "+sourceFile.getAbsolutePath(), ex);
            return true;
        }
    }
    protected void compileGaulois(File source, File target) throws SaxonApiException {
        XsltTransformer tr = gauloisCompilerXsl.load();
        Serializer ser = getProcessor().newSerializer(target);
        tr.setDestination(ser);
        XdmNode sourceNode = getBuilder().build(source);
        tr.setInitialContextNode(sourceNode);
        tr.transform();
        tr.close();
    }
}