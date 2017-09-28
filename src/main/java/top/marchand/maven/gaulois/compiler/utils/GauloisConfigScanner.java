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
package top.marchand.maven.gaulois.compiler.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.sax.SAXSource;
import org.apache.commons.io.FilenameUtils;
import org.apache.maven.plugin.logging.Log;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;

/**
 *
 * @author <a href="mailto:christophe@marchand.top">Christophe Marchand</a>
 */
public class GauloisConfigScanner extends DefaultHandler2 {
    public static final String GAULOIS_NS = "http://efl.fr/chaine/saxon-pipe/config";
    boolean hasError = false;
    private final List<File> xslDirectories;
    private final File outputDirectory;
    private final Map<Source,File> xslToCompile;
    private final List<String> errors;
    private final URIResolver resolver;
    private int uriErrorCount = 0;
    private final Log log;
    private final List<String> classpathes;
    
    public GauloisConfigScanner(List<File> xslDirectories, File outputDirectory, URIResolver resolver, Log log, List<String> classpathes) {
        super();
        this.xslDirectories=xslDirectories;
        this.outputDirectory=outputDirectory;
        this.resolver=resolver;
        this.log=log;
        this.classpathes = classpathes;
        xslToCompile = new HashMap<>();
        errors = new ArrayList<>();
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        super.startElement(uri, localName, qName, attributes);
        if(GAULOIS_NS.equals(uri) && "xslt".equals(localName)) {
            String href = attributes.getValue("href");
            log.debug("found XSL: "+href);
            if(!href.startsWith("cp:/")) {
                try {
                    Source source = resolver.resolve(href, "");
                    log.debug("source is a "+source.getClass().getName());
                    String systemId = source.getSystemId();
                    String xslPath = systemId.contains("!") ? systemId.substring(systemId.indexOf("!")+1) : systemId;
                    log.debug("xslPath="+xslPath);
                    String shortPath = FilenameUtils.getPath(xslPath);
                    log.debug("shortPath="+shortPath);
                    String baseName = FilenameUtils.getBaseName(xslPath);
                    log.debug("baseName="+baseName);
                    String targetPath = shortPath.concat(baseName).concat(".sef");
                    log.debug("targetPath="+targetPath);
                    File targetXsl = new File(outputDirectory, targetPath);
                    
                    xslToCompile.put(source, targetXsl);
                } catch(TransformerException | NullPointerException ex) {
                    hasError = true;
                    if(uriErrorCount<10) {
                        errors.add(href+" is an invalid URI. Only URI based on cp:/ protocol are supported, or the one that can be resolved via the catalog");
                        uriErrorCount++;
                    }
                }
            } else if(href.startsWith("cp:/")) {
                String path = href.substring(4);
                log.debug("searching for xsl "+path);
                boolean found = false;
                for(File dir:xslDirectories) {
                    File xsl = new File(dir, path);
                    if(xsl.exists() && xsl.isFile()) {
                        log.debug("\tfound at "+xsl.getAbsolutePath());
                        String shortPath = FilenameUtils.getPath(path);
                        String baseName = FilenameUtils.getBaseName(path);
                        String targetPath = shortPath.concat(baseName).concat(".sef");
                        File targetXsl = new File(outputDirectory, targetPath);
                        try {
                            InputSource is = new InputSource(new FileInputStream(xsl));
                            String systemID = xsl.toURI().toString();
                            is.setPublicId(systemID);
                            SAXSource ss = new SAXSource(is);
                            ss.setSystemId(systemID);
                            xslToCompile.put(ss, targetXsl);
                            found = true;
                        } catch(FileNotFoundException ex) {
                            // is it really possible ? We have checked xsl.exists()
                            // so ignore it
                        }
                    }
                    if(found) break;
                }
                if(!found) {
                    // could it be referenced from catalog ?
                    try {
                        Source source = resolver.resolve(href, "");
                        if(source!=null) {
                            log.debug("source is a "+source.getClass().getName());
                            String systemId = source.getSystemId();
                            String xslPath = systemId.contains("!") ? systemId.substring(systemId.indexOf("!")+1) : systemId;
                            log.debug("xslPath="+xslPath);
                            String shortPath = FilenameUtils.getPath(xslPath);
                            log.debug("shortPath="+shortPath);
                            String baseName = FilenameUtils.getBaseName(xslPath);
                            log.debug("baseName="+baseName);
                            String targetPath = shortPath.concat(baseName).concat(".sef");
                            log.debug("targetPath="+targetPath);
                            File targetXsl = new File(outputDirectory, targetPath);
                            xslToCompile.put(source, targetXsl);
                            found = true;
                        }
                    } catch(TransformerException | NullPointerException ex) {
                        hasError = true;
                        if(uriErrorCount<10) {
                            errors.add(href+" is an invalid URI. Only URI based on cp:/ protocol are supported, or the one that can be resolved via the catalog");
                            uriErrorCount++;
                        }
                    }
                }
                if(!found) {
                    // search throw classpath
                    log.debug("searching for "+href+" in classpath");
                    for(String cp: classpathes) {
                        try {
                            String systemId = cp;
                            if(systemId.endsWith(".jar")) systemId = "jar:"+systemId;
                            systemId = systemId + "!"+href.substring(3);
                            URL url = new URL(systemId);
                            InputStream is = url.openStream();
                            if(is!=null) {
                                log.debug("systemId="+systemId);
                                String xslPath = systemId.contains("!") ? systemId.substring(systemId.indexOf("!")+1) : systemId;
                                log.debug("xslPath="+xslPath);
                                String shortPath = FilenameUtils.getPath(xslPath);
                                log.debug("shortPath="+shortPath);
                                String baseName = FilenameUtils.getBaseName(xslPath);
                                log.debug("baseName="+baseName);
                                String targetPath = shortPath.concat(baseName).concat(".sef");
                                log.debug("targetPath="+targetPath);
                                File targetXsl = new File(outputDirectory, targetPath);
                                Source source = new SAXSource(new InputSource(is));
                                source.setSystemId(systemId);
                                xslToCompile.put(source, targetXsl);
                                found = true;
                            }
                        } catch(FileNotFoundException ex) {
                            // we do nothing, we want to ignore this exception
                        } catch (IOException ex) {
                            log.warn("while searching "+href+"in classpath:",ex);
                        }
                        
                    }
                }
                if(!found) {
                    log.warn(href+" could not be found ! It won't be compiled");
                }
            }
        }
    }
    
    public boolean hasErrors() { return hasError; }
    public List<String> getErrorMessages() { return errors; }
    public Map<Source,File> getXslToCompile() { return xslToCompile; }
    
}
