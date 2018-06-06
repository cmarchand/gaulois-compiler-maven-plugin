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
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * A set, a gaulois-pipe config file, and all its XSL
 * @author cmarchand
 */
public class GauloisSet implements Comparable<GauloisSet> {
    private final String gauloisConfigSystemId;
    private final Set<GauloisXsl> xsls;
    private final File targetFile;
    
    public GauloisSet(String systemId, final File targetFile) {
        this.gauloisConfigSystemId=systemId;
        this.targetFile = targetFile;
        xsls = new TreeSet<>();
    }

    public String getGauloisConfigSystemId() {
        return gauloisConfigSystemId;
    }

    public Set<GauloisXsl> getXsls() {
        return xsls;
    }

    public File getTargetFile() {
        return targetFile;
    }
    
    public Set<String> getAllSchemas() {
        Set<String> ret = new TreeSet<>();
        for(GauloisXsl gx: getXsls()) ret.addAll(gx.getSchemas());
        return ret;
    }

    @Override
    public int compareTo(GauloisSet o) {
        return gauloisConfigSystemId.compareTo(o.getGauloisConfigSystemId());
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof GauloisSet) {
            return gauloisConfigSystemId.equals(((GauloisSet)obj).getGauloisConfigSystemId());
        } else return false;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 13 * hash + Objects.hashCode(this.gauloisConfigSystemId);
        return hash;
    }
    
    
}
