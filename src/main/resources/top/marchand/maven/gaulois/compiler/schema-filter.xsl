<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:xs="http://www.w3.org/2001/XMLSchema"
  xmlns:math="http://www.w3.org/2005/xpath-functions/math"
  xmlns:xd="http://www.oxygenxml.com/ns/doc/xsl"
  exclude-result-prefixes="xs math xd"
  version="3.0">
  <xd:doc scope="stylesheet">
    <xd:desc>
      <xd:p><xd:b>Created on:</xd:b> Jun 6, 2018</xd:p>
      <xd:p><xd:b>Author:</xd:b> cmarchand</xd:p>
      <xd:p></xd:p>
    </xd:desc>
  </xd:doc>
  
  <xsl:template match="/">
    <schemas>
      <xsl:apply-templates/>
    </schemas>
  </xsl:template>
  <xd:doc>
    <xd:desc>Start copying import-schemas</xd:desc>
  </xd:doc>
  <xsl:template match="file[@dependency-type eq 'xsl:import-schema']">
    <xsl:apply-templates select="." mode="copy"/>
  </xsl:template>
  
  <xd:doc>
    <xd:desc>Copy imported schemas in schemas</xd:desc>
  </xd:doc>
  <xsl:template match="node() | @*" mode="copy">
    <xsl:copy>
      <xsl:apply-templates select="node() | @*" mode="#current"/>
    </xsl:copy>
  </xsl:template>
  
  <xsl:template match="text()"/>
  
</xsl:stylesheet>