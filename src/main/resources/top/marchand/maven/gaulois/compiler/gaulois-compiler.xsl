<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema"
  xmlns:math="http://www.w3.org/2005/xpath-functions/math"
  xmlns:xd="http://www.oxygenxml.com/ns/doc/xsl"
  xmlns:gc="http://efl.fr/chaine/saxon-pipe/config"
  xmlns:map="http://www.w3.org/2005/xpath-functions/map"
  xmlns:saxon="http://saxon.sf.net/"
  exclude-result-prefixes="#all"
  expand-text="true"
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
  <xsl:param name="xslMap" as="map(xs:string,xs:string)" required="true"/>
  <xsl:param name="targetPath" as="xs:string" required="true"/>
  
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
    <xsl:message>schemas: {$schemas}</xsl:message>
    <xsl:message>xslMap: 
{for $key in map:keys($xslMap) return concat(' ',$key, ' -> ', map:get($xslMap, $key), '
')}</xsl:message>
    <xsl:message>targetPath: {$targetPath}</xsl:message>
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
    <!-- resolve through catalog -->
    <xsl:variable name="xslUri" select="string(.)" as="xs:string"/>
    <xsl:message>xslUri: {$xslUri}</xsl:message>
    <!-- look for target location in map -->
    <xsl:variable name="compiledLocation" select="map:get($xslMap,$xslUri)" as="xs:string*"/>
    <xsl:message>compiledLocation: {$compiledLocation}</xsl:message>
    <!-- removes target directory name and adds cp:/ protocol -->
    <xsl:variable name="compiledUri" as="xs:string*" select="concat('cp:', substring-after($compiledLocation, $targetPath))"/>
    <xsl:message>compiledUri: {$compiledUri}</xsl:message>
    <xsl:attribute name="{name(.)}" select="$compiledUri"/>
  </xsl:template>
  
</xsl:stylesheet>