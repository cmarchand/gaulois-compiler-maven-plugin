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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FilenameUtils;
import org.xml.sax.Attributes;
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
    private final Map<File,File> xslToCompile;
    private final List<String> errors;
    private int uriErrorCount = 0;
    
    public GauloisConfigScanner(List<File> xslDirectories, File outputDirectory) {
        super();
        this.xslDirectories=xslDirectories;
        this.outputDirectory=outputDirectory;
        xslToCompile = new HashMap<>();
        errors = new ArrayList<>();
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        super.startElement(uri, localName, qName, attributes);
        if(GAULOIS_NS.equals(uri) && "xslt".equals(localName)) {
            String href = attributes.getValue("href");
            if(!href.startsWith("cp:/")) {
                hasError = true;
                if(uriErrorCount<10) {
                    errors.add(href+" is an invalid URI. Only URI based on cp:/ protocol are supported");
                    uriErrorCount++;
                }
            } else {
                String path = href.substring(4);
                boolean found = false;
                for(File dir:xslDirectories) {
                    File xsl = new File(dir, path);
                    if(xsl.exists() && xsl.isFile()) {
                        String shortPath = FilenameUtils.getPath(path);
                        String baseName = FilenameUtils.getBaseName(path);
                        String targetPath = shortPath.concat(baseName).concat(".sef");
                        File targetXsl = new File(outputDirectory, targetPath);
                        xslToCompile.put(xsl, targetXsl);
                        found = true;
                    }
                    if(found) break;
                }
            }
        }
    }
    
    public boolean hasErrors() { return hasError; }
    public List<String> getErrorMessages() { return errors; }
    public Map<File,File> getXslToCompile() { return xslToCompile; }
    
}
