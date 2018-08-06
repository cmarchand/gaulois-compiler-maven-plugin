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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
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
import javax.xml.transform.SourceLocator;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.MessageListener;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmSequenceIterator;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.XsltTransformer;
import net.sf.saxon.trans.XPathException;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLFilter;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.EntityResolver2;
import org.xml.sax.helpers.ParserAdapter;
import org.xml.sax.helpers.XMLFilterImpl;
import top.marchand.maven.gaulois.compiler.utils.GauloisConfigScanner;
import top.marchand.maven.gaulois.compiler.utils.GauloisSet;
import top.marchand.maven.gaulois.compiler.utils.GauloisXsl;
import top.marchand.maven.saxon.utils.SaxonOptions;
import top.marchand.xml.maven.plugin.xsl.AbstractCompiler;

@Mojo(name="gaulois-compiler", defaultPhase = LifecyclePhase.COMPILE, requiresDependencyResolution = ResolutionScope.COMPILE)
public class GCMojo extends AbstractCompiler {
    @Parameter( defaultValue = "${project}", readonly = true, required = true )
    private MavenProject project;
    @Override
    public MavenProject getProject() { return project; }
    @Component( hint = "default" )
    private DependencyGraphBuilder dependencyGraphBuilder;
    @Override
    public DependencyGraphBuilder getGraphBuilder() { return dependencyGraphBuilder; }
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
    
    /**
     * The catalog file to use to compile
     */
    @Parameter
    private File catalog;
    
    @Parameter(defaultValue = "${project.basedir}")
    private File projectBaseDir;
    
    /**
     * The directory where imported schemas will be copied to. Be aware that
     * if your schema structure uses relatives parent (../xxx) location, no
     * file could be copied outside of <tt>${project.build.outputDirectory}</tt>
     */
    @Parameter (defaultValue = "${project.build.outputDirectory}/gc/schemas")
    private File schemasDestination;
    
    /**
     * A XSL to post-compile the gaulois-pipe config file, if required
     */
    @Parameter
    private File postCompiler;
    
    /**
     * Saxon options, to configure Saxon. 
     * See {@linkplain https://github.com/cmarchand/saxonOptions-mvn-plug-utils/wiki}
     */
    @Parameter
    SaxonOptions saxonOptions;
    
    private XsltExecutable postCompilerXsl;

    private XsltExecutable gauloisCompilerXsl;
    private XsltExecutable xutScanner;
    private XsltExecutable xutFilter;
    
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
    private static final QName QN_NAME = new QName("name");
    private static final QName QN_PARAM_SCHEMAS = new QName("schemas");

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
        ThreadLocal<EntityResolver2> th = new ThreadLocal<>();
        th.set(getEntityResolver());
        getLog().warn(LOG_PREFIX+getXsltCompiler().getProcessor().getUnderlyingConfiguration().getSourceParserClass());

        try {
            URL url = getClass().getResource("/org/mricaud/xml-utilities/get-xml-file-static-dependency-tree.xsl");
            StreamSource ssource = new StreamSource(url.openStream());
            ssource.setSystemId(url.toExternalForm());
            xutScanner = getXsltCompiler().compile(ssource);
            xutFilter = getXsltCompiler().compile(new StreamSource(getClass().getResource("/top/marchand/maven/gaulois/compiler/schema-filter.xsl").openStream()));
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
                } catch(TransformerException | URISyntaxException ex) {
                    hasError = true;
                    getLog().error("while parsing "+fs.getUri(), ex);
                }
            } else {
                List<Path> pathes = fs.getFiles(projectBaseDir, log);
                // this must be call <strong>after</strong> the call to fs.getFiles, as fs.dir is modified by fs.getFiles
                Path basedir = new File(fs.getDir()).toPath();
                getLog().debug(LOG_PREFIX+"looking in "+basedir.toString());
                for(Path p: pathes) {
                    getLog().debug(LOG_PREFIX+"found "+p.toString());
                    File sourceFile = basedir.resolve(p).toFile();
                    Path targetPath = p.getParent()==null ? targetDir : targetDir.resolve(p.getParent());
                    String sourceFileName = sourceFile.getName();
                    // we keep the same extension for gaulois config files
                    File targetFile = targetPath.resolve(sourceFileName).toFile();
                    try {
                        hasError |= scanGauloisFile(sourceFile, targetFile, targetDir);
                    } catch(FileNotFoundException | URISyntaxException ex) {
                        // it can not be thrown but we are required to catch it
                        hasError = true;
                        getLog().error(LOG_PREFIX+"while parsing "+p.toString(), ex);
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
                } catch (FileNotFoundException | SaxonApiException ex) {
                    getLog().warn(LOG_PREFIX+" while compiling "+xslSystemId, ex);
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
            } catch(SaxonApiException ex) {
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
     * @throws java.net.URISyntaxException Maybe... or not...
     */
    protected boolean scanGauloisFile(File sourceFile, File targetFile, Path targetDir) throws FileNotFoundException, URISyntaxException {
        String systemId = sourceFile.toURI().toString();
        getLog().debug("scanGauloisFile("+systemId+",File, Path);");
        InputSource is = new InputSource(new FileInputStream(sourceFile));
        is.setSystemId(systemId);
        SAXSource source = new SAXSource(is);
        source.setSystemId(systemId);
        return scanGauloisFile(source, targetFile, targetDir);
    }
    protected boolean scanGauloisFile(Source source, File targetFile, Path targetDir) throws URISyntaxException {
        assert(source.getSystemId()!=null);
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
        ArrayList<XdmAtomicValue> values = new ArrayList<>();
        for(String schema:schemas) {
            getLog().info(LOG_PREFIX+target.getName()+" has schema: "+schema);
            values.add(new XdmAtomicValue(schema));
        }
        XdmValue sequence = new XdmValue(values);
        tr.setParameter(QN_PARAM_SCHEMAS, sequence);
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
    protected void scanForSchemas(GauloisXsl xsl) throws SaxonApiException, URISyntaxException, IOException {
        getLog().debug(LOG_PREFIX+" scanning for schema "+xsl.getXslSystemId());
        XsltTransformer xut = xutScanner.load();
        xut.setMessageListener(new NullMessageListener());
        XsltTransformer filter = xutFilter.load();
        xut.setDestination(filter);
        XdmDestination dest = new XdmDestination();
        filter.setDestination(dest);
        xut.setMessageListener(new MessageListener() {
            @Override
            public void message(XdmNode xn, boolean bln, SourceLocator sl) { }
        });
        XdmNode xslDocument = getBuilder().build(new StreamSource(xsl.getXslSystemId()));
        xut.setInitialContextNode(xslDocument);
        xut.setParameter(new QName(XUT_NS, "xut:get-xml-file-static-dependency-tree.filterDuplicatedDependencies"), new XdmAtomicValue(true));
        xut.transform();
        XdmNode dependencies = dest.getXdmNode();
        // now, walk through dependencies
        // all first-level childs are imported schemas
        XdmNode file = (XdmNode)(dependencies.axisIterator(Axis.CHILD).next());
        XdmSequenceIterator xsi = file.axisIterator(Axis.CHILD);
        while(xsi.hasNext()) {
            exploreFile(xsl, (XdmNode)(xsi.next()));
        }
    }
    private void exploreFile(GauloisXsl xsl, XdmNode node) throws URISyntaxException, IOException {
        String dependencyType = node.getAttributeValue(QN_DEP_TYPE);
        String absUri = node.getAttributeValue(QN_ABS_URI);
        getLog().debug(LOG_PREFIX+"\texploreFile <"+node.getNodeName()+" "+QN_DEP_TYPE.toString()+"="+dependencyType+" absUri="+absUri);
        if(dependencyType.equals("xsl:import-schema")) { // always true, but for documentation
            String uri = node.getAttributeValue(QN_URI);
            String name = node.getAttributeValue(QN_NAME);
            SchemaTarget targetSchema = getTargetSchemaFile(name, absUri);
            xsl.getSchemas().add(targetSchema.getAccessUri());
            getLog().debug(LOG_PREFIX+"\turi is "+absUri);
            copyUriToFile(absUri, targetSchema.getFileLocation());
            XdmSequenceIterator it = node.axisIterator(Axis.CHILD);
            while(it.hasNext()) {
                XdmNode schemaNode = (XdmNode)it.next();
                copySubSchema(targetSchema.getFileLocation(), schemaNode);
            }
        }
    }
    private void copySubSchema(File parent, XdmNode schemaNode) throws URISyntaxException, IOException {
        String uri = schemaNode.getAttributeValue(QN_URI);
        String absUri = schemaNode.getAttributeValue(QN_ABS_URI);
        File schemaFile = parent.toPath().resolve(uri).toFile();
        copyFile(new File(new URI(absUri)), schemaFile);
        XdmSequenceIterator it = schemaNode.axisIterator(Axis.CHILD);
        while(it.hasNext()) {
            XdmNode subSchemaNode = (XdmNode)it.next();
            copySubSchema(schemaFile, subSchemaNode);
        }
    }
    private SchemaTarget getTargetSchemaFile(String name, String absUri) {
        File destSchema = new File(getSchemasDestination(), name);
        Path p = classesDirectory.toPath().relativize(destSchema.toPath());
        String accessUri = "cp:/"+p.toString();
        return new SchemaTarget(accessUri, destSchema);
    }
    
    private void copyFile(File source, File dest) throws IOException {
        dest.getParentFile().mkdirs();
        try (
                FileChannel in = new FileInputStream(source).getChannel(); 
                FileChannel out = new FileOutputStream(dest).getChannel()) {
            in.transferTo (0, in.size(), out);
        }
    }
    
    private void copyUriToFile(String uri, File dest) throws IOException, URISyntaxException {
        dest.getParentFile().mkdirs();
        URL url = new URI(uri).toURL();
        InputStream is = url.openStream();
        try (
                ReadableByteChannel in = Channels.newChannel(is);
                FileChannel out = new FileOutputStream(dest).getChannel()) {
            final long size = 5*1024;
            long offset = 0;
            long  vol = out.transferFrom(in, 0, size);
            while(vol==size) {
                offset+=vol;
                vol = out.transferFrom(in, offset, size);
            }
        }
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

    /**
     * Returns the schemas destination
     * @return The schemas destination directory
     */
    public File getSchemasDestination() {
        return schemasDestination;
    }
    
    /**
     * A class to store a schema location.
     */
    private class SchemaTarget {
        private final String accessUri;
        private final File fileLocation;
        public SchemaTarget(final String accessUri, final File fileLocation) {
            super();
            this.accessUri = accessUri;
            this.fileLocation = fileLocation;
        }

        public String getAccessUri() {
            return accessUri;
        }

        public File getFileLocation() {
            return fileLocation;
        }
        
    }

    @Override
    public SaxonOptions getSaxonOptions() {
        return saxonOptions;
    }
    
    private class NullMessageListener implements MessageListener {
        @Override
        public void message(XdmNode xn, boolean bln, SourceLocator sl) {}
    }
}
