<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema"
  xmlns:math="http://www.w3.org/2005/xpath-functions/math"
  xmlns:xd="http://www.oxygenxml.com/ns/doc/xsl"
  xmlns:gc="http://efl.fr/chaine/saxon-pipe/config"
  exclude-result-prefixes="#all"
  version="3.0">
  <xd:doc scope="stylesheet">
    <xd:desc>
      <xd:p><xd:b>Created on:</xd:b> Sep 5, 2017</xd:p>
      <xd:p><xd:b>Author:</xd:b> cmarchand</xd:p>
      <xd:p></xd:p>
    </xd:desc>
  </xd:doc>
  
  <xsl:template match="@* | node()">
    <xsl:copy>
      <xsl:apply-templates select="@* | node()"/>
    </xsl:copy>
  </xsl:template>
  
  <xd:doc>
    <xd:desc>
      <xd:p>Rewrite extension of xslt URI</xd:p>
    </xd:desc>
  </xd:doc>
  <xsl:template match="gc:xslt/@href">
    <xsl:variable name="newName" as="xs:string" select="string-join((tokenize(.,'\.')[position() lt last()],'sef'),'.')"/>
    <xsl:attribute name="{name(.)}" select="$newName"></xsl:attribute>
  </xsl:template>
  
</xsl:stylesheet>