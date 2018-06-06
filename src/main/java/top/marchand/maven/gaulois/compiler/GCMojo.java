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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmSequenceIterator;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.XsltTransformer;
import net.sf.saxon.trans.XPathException;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.xerces.util.URI;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLFilter;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.ParserAdapter;
import org.xml.sax.helpers.XMLFilterImpl;
import top.marchand.maven.gaulois.compiler.utils.GauloisConfigScanner;
import top.marchand.maven.gaulois.compiler.utils.GauloisSet;
import top.marchand.maven.gaulois.compiler.utils.GauloisXsl;
import top.marchand.maven.saxon.utils.SaxonOptions;
import top.marchand.xml.maven.plugin.xsl.AbstractCompiler;

@Mojo(name="gaulois-compiler", defaultPhase = LifecyclePhase.COMPILE, requiresDependencyResolution = ResolutionScope.COMPILE)
public class GCMojo extends AbstractCompiler {
    
    /**
     * The directory containing generated classes of the project being tested. 
     * This will be included after the test classes in the test classpath.
     */
    @Parameter( defaultValue = "${project.build.outputDirectory}" )
    private File classesDirectory;
    
    /**
     * List of gauloisPipeFileset
     */
    @Parameter
    List<FileSet> gauloisPipeFilesets;
    
    @Parameter
    private File catalog;
    
    @Parameter(defaultValue = "${project.basedir}")
    private File projectBaseDir;
    
    @Parameter( defaultValue = "${project}", readonly = true, required = true )
    private MavenProject project;
    
    /**
     * A XSL to post-compile the gaulois-pipe config file, if required
     */
    @Parameter
    private File postCompiler;
    
    @Parameter
    SaxonOptions saxonOptions;
    
    private XsltExecutable postCompilerXsl;

    private XsltExecutable gauloisCompilerXsl;
    private XsltExecutable xutScanner;
    
    /**
     * The list of directories where XSL sources are located in
     */
    @Parameter
    List<File> xslSourceDirs;
    
    // inner working variables
    private Set<GauloisSet> gauloisSets;
    private Map<String, GauloisXsl> foundXsls;
    
    public static final SAXParserFactory PARSER_FACTORY = SAXParserFactory.newInstance();
    private ArrayList<String> classpaths;
    private static final String XUT_NS = "https://github.com/mricaud/xml-utilities";
    private static final QName QN_DEP_TYPE = new QName("dependency-type");
    private static final QName QN_URI = new QName("uri");
    private static final QName QN_ABS_URI = new QName("abs-uri");

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
        try {
            initSaxon();
        } catch(XPathException ex) {
            getLog().error("while configuring saxon:",ex);
        }
        loadClasspath();
        gauloisSets = new TreeSet<>();
        foundXsls = new HashMap<>();
        try {
            URL url = getClass().getResource("/org/mricuad/xml-utilities/get-xml-file-static-dependency-tree.xsl");
            xutScanner = getXsltCompiler().compile(new StreamSource(url.openStream()));
        } catch(SaxonApiException | IOException ex) {
            throw new MojoFailureException("while compiling xut xsl", ex);
        }
        Path targetDir = classesDirectory.toPath();
        boolean hasError = false;
        getLog().debug(LOG_PREFIX+" looking for gaulois-pipe config files");
        for(FileSet fs: gauloisPipeFilesets) {
            if(fs.getUri()!=null && !fs.getUri().isEmpty()) {
                try {
                    Source source = compiler.getURIResolver().resolve(fs.getUri(), null);
                    String sPath = fs.getUriPath();
                    getLog().debug(LOG_PREFIX+" sPath="+sPath);
                    Path targetPath = targetDir.resolve(sPath).getParent();
                    getLog().debug(LOG_PREFIX+" targetPath="+targetPath.toString());
                    String sourceFileName = sPath.substring(sPath.lastIndexOf("/")+1);
                    if(sourceFileName.contains("?")) {
                        sourceFileName = sourceFileName.substring(0, sourceFileName.indexOf("?")-1);
                    }
                    // we keep the same extension for gaulois config files
                    File targetFile = targetPath.resolve(sourceFileName).toFile();
                    getLog().debug(LOG_PREFIX+" targetFile="+targetFile.getAbsolutePath());
                    hasError |= scanGauloisFile(source, targetFile, targetDir);
                } catch(TransformerException ex) {
                    hasError = true;
                    getLog().error("while parsing "+fs.getUri(), ex);
                }
            } else {
                List<Path> pathes = fs.getFiles(projectBaseDir, log);
                // this must be call <strong>after</strong> the call to fs.getFiles, as fs.dir is modified by fs.getFiles
                Path basedir = new File(fs.getDir()).toPath();
                getLog().debug("looking in "+basedir.toString());
                for(Path p: pathes) {
                    getLog().debug("found "+p.toString());
                    File sourceFile = basedir.resolve(p).toFile();
                    Path targetPath = p.getParent()==null ? targetDir : targetDir.resolve(p.getParent());
                    String sourceFileName = sourceFile.getName();
                    // we keep the same extension for gaulois config files
                    File targetFile = targetPath.resolve(sourceFileName).toFile();
//                    gauloisConfigToCompile.put(sourceFile, targetFile);
                    try {
                        hasError |= scanGauloisFile(sourceFile, targetFile, targetDir);
                    } catch(FileNotFoundException ex) {
                        // it can not be thrown but we are required to catch it
                        hasError = true;
                        getLog().error("while parsing "+p.toString(), ex);
                    }
                }
            }
        }
        if(!hasError) {
            for(String xslSystemId: foundXsls.keySet()) {
                try {
                    getLog().debug(LOG_PREFIX+" compiling "+xslSystemId);
                    Source xslSource = new StreamSource(xslSystemId);
                    File targetFile = foundXsls.get(xslSystemId).getTargetFile();
                    compileFile(xslSource, targetFile);
                    // ici, scanner la XSL pour détecter des xsl:import-schema
                } catch (FileNotFoundException | SaxonApiException ex) {
                    getLog().warn(LOG_PREFIX+" while compiling "+xslSystemId, ex);
                    hasError = true;
                }
            }
            Source xsl = new StreamSource(this.getClass().getResourceAsStream("/top/marchand/maven/gaulois/compiler/gaulois-compiler.xsl"));
            try {
                gauloisCompilerXsl = getXsltCompiler().compile(xsl);
                for(GauloisSet gs: gauloisSets) {
                    getLog().debug(LOG_PREFIX+" compiling "+gs.getGauloisConfigSystemId());
                    // passer ici les schemas à déclarer
                    compileGaulois(new StreamSource(gs.getGauloisConfigSystemId()), gs.getTargetFile(), gs.getAllSchemas());
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
     * @param targetDir The build dir
     * @return <tt>false</tt> if an error occured
     * @throws java.io.FileNotFoundException If a file is not found. Should never be thrown.
     */
    protected boolean scanGauloisFile(File sourceFile, File targetFile, Path targetDir) throws FileNotFoundException {
        return scanGauloisFile(new SAXSource(new InputSource(new FileInputStream(sourceFile))), targetFile, targetDir);
    }
    protected boolean scanGauloisFile(Source source, File targetFile, Path targetDir) {
        try {
            final XMLReader reader = new ParserAdapter(PARSER_FACTORY.newSAXParser().getParser());
            final GauloisConfigScanner scanner = new GauloisConfigScanner(xslSourceDirs, classesDirectory, getUriResolver(), getLog(), classpaths);
            XMLFilter filter = new XMLFilterImpl(reader) {
                @Override
                public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
                    super.startElement(uri, localName, qName, atts);
                    scanner.startElement(uri, localName, qName, atts);
                }
            };
            // use systemId to create a new InputSource, and to keep the Source not consumed
            filter.parse(source.getSystemId());
            if(scanner.hasErrors()) {
                for(String errorMsg: scanner.getErrorMessages()) {
                    getLog().error(errorMsg);
                }
            } else {
                GauloisSet set = new GauloisSet(source.getSystemId(), targetFile);
                if(!gauloisSets.contains(set)) {
                    gauloisSets.add(set);
                    for(Source xslSource: scanner.getXslToCompile().keySet()) {
                        GauloisXsl xsl = foundXsls.get(xslSource.getSystemId());
                        if(xsl==null) {
                            xsl = new GauloisXsl(xslSource.getSystemId(), scanner.getXslToCompile().get(xslSource));
                            foundXsls.put(xslSource.getSystemId(), xsl);
                            scanForSchemas(xsl);
                        }
                        set.getXsls().add(xsl);
                    }
                }
            }
            return scanner.hasErrors();
        } catch(ParserConfigurationException | SAXException | SaxonApiException | IOException ex) {
            getLog().error("while scanning "+source.getSystemId(), ex);
            return true;
        }
    }
    protected void compileGaulois(Source source, File target, Set<String> schemas) throws SaxonApiException {
        XsltTransformer tr = gauloisCompilerXsl.load();
        // TODO: set parameter for schemas
        XsltTransformer first = tr;
        // post compiler ?
        XsltTransformer pc = getPostCompiler();
        if(pc!=null) {
            tr.setDestination(pc);
            tr = pc;
        }
        Serializer ser = getProcessor().newSerializer(target);
        tr.setDestination(ser);
        XdmNode sourceNode = getBuilder().build(source);
        first.setInitialContextNode(sourceNode);
        first.transform();
        first.close();
    }
    protected XsltTransformer getPostCompiler() {
        if(postCompilerXsl==null && postCompiler!=null && postCompiler.exists() && postCompiler.isFile()) {
            try {
                postCompilerXsl = getXsltCompiler().compile(new StreamSource(new FileInputStream(postCompiler)));
            } catch(SaxonApiException | FileNotFoundException ex) {
                getLog().error("while compiling post-compiler "+postCompiler.getAbsolutePath(), ex);
            }
        }
        return postCompilerXsl==null ? null : postCompilerXsl.load();
    }
    protected void scanForSchemas(GauloisXsl xsl) throws SaxonApiException {
        XsltTransformer xut = xutScanner.load();
        XdmDestination dest = new XdmDestination();
        xut.setDestination(dest);
        XdmNode xslDocument = getBuilder().build(new StreamSource(xsl.getXslSystemId()));
        xut.setInitialContextNode(xslDocument);
        xut.setParameter(new QName(XUT_NS, "xut:get-xml-file-static-dependency-tree.filterDuplicatedDependencies"), new XdmAtomicValue(true));
        xut.transform();
        XdmNode dependencies = dest.getXdmNode();
        // now, walk through dependencies
        XdmNode file = (XdmNode)(dependencies.axisIterator(Axis.CHILD).next());
        XdmSequenceIterator xsi = file.axisIterator(Axis.CHILD);
        while(xsi.hasNext()) {
            exploreFile(xsl, (XdmNode)(xsi.next()), xsl.getXslSystemId(), xsl.getTargetFile());
        }
    }
    private void exploreFile(GauloisXsl xsl, XdmNode node, String baseUri, File targetParentFile) {
        String dependencyType = node.getAttributeValue(QN_DEP_TYPE);
        if(dependencyType.equals("xsl:import-schema")) {
            String uri = node.getAttributeValue(QN_URI);
            String absUri = node.getAttributeValue(QN_ABS_URI);
            SchemaTarget targetSchema = getTargetSchemaFile(targetParentFile, uri, absUri);
        }
    }
    private SchemaTarget getTargetSchemaFile(File targetParentFile, String uri, String absUri) {
        // TODO
        return null;
    }
    private void loadClasspath() {
        try {
            classpaths = new ArrayList<>(project.getCompileClasspathElements().size());
            for(Object i:project.getCompileClasspathElements()) {
                File f = new File(i.toString());
                classpaths.add(f.toURI().toString());
            }
            getLog().debug(LOG_PREFIX+"classpaths="+classpaths);
        } catch(DependencyResolutionRequiredException ex) {
            getLog().error(LOG_PREFIX+ex.getMessage(),ex);
        }
    }
    private class SchemaTarget {
        private String accessUri;
        private File fileLocation;
    }

    @Override
    public SaxonOptions getSaxonOptions() {
        return saxonOptions;
    }
}
