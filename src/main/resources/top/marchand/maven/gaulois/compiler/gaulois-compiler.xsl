<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema"
  xmlns:math="http://www.w3.org/2005/xpath-functions/math"
  xmlns:xd="http://www.oxygenxml.com/ns/doc/xsl"
  xmlns:gc="http://efl.fr/chaine/saxon-pipe/config"
  xmlns:saxon="http://saxon.sf.net/"
  exclude-result-prefixes="#all"
  version="3.0">
  <xd:doc scope="stylesheet">
    <xd:desc>
      <xd:p><xd:b>Created on:</xd:b> Sep 5, 2017</xd:p>
      <xd:p><xd:b>Author:</xd:b> cmarchand</xd:p>
      <xd:p></xd:p>
    </xd:desc>
  </xd:doc>
  
  <xsl:output saxon:indent-spaces="2" indent="yes"/>
  
  
  <xsl:param name="schemas" as="xs:string*"/>
  
  <xd:doc>
    <xd:desc>Une simple recopie</xd:desc>
  </xd:doc>
  <xsl:template match="@* | node()">
    <xsl:copy>
      <xsl:apply-templates select="@* | node()"/>
    </xsl:copy>
  </xsl:template>
  
  <xd:doc>
    <xd:desc>Overwrited to put schemas declaration in</xd:desc>
  </xd:doc>
  <xsl:template match="gc:config">
    <xsl:copy>
      <xsl:apply-templates select="gc:namespaces"/>
      <xsl:if test="not(empty($schemas))">
        <grammars xmlns="http://efl.fr/chaine/saxon-pipe/config">
          <xsl:for-each select="$schemas">
            <schema xmlns="http://efl.fr/chaine/saxon-pipe/config" href="{.}"/>
          </xsl:for-each>
        </grammars>
      </xsl:if>
      <xsl:apply-templates select="* except (gc:namespaces, gc:grammars)"/>
    </xsl:copy>
  </xsl:template>
  
  <xd:doc>
    <xd:desc>
      <xd:p>Rewrite extension of xslt URI</xd:p>
    </xd:desc>
  </xd:doc>
  <xsl:template match="gc:xslt/@href">
    <!-- changement de l'extension -->
    <xsl:variable name="newName" as="xs:string" select="string-join((tokenize(.,'\.')[position() lt last()],'sef'),'.')"/>
    <!-- changement du protocole, on va tout packager ensemble -->
    <xsl:variable name="newName2" as="xs:string" select="concat('cp:',substring-after($newName,':'))"/>
    <xsl:attribute name="{name(.)}" select="$newName2"></xsl:attribute>
  </xsl:template>
  
</xsl:stylesheet>