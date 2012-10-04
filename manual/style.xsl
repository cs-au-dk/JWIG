<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
 xmlns:fn="http://www.w3.org/2005/xpath-functions">

	<xsl:output
		method="html"
		encoding="ISO-8859-1"
		doctype-public="-//W3C//DTD HTML 4.01//EN"
		doctype-system="http://www.w3.org/TR/html4/strict.dtd"/>
	
	<xsl:key name="rel" match="h1|h2" use="@id"/>


    <xsl:template match="titlepage">
        <div id="titlepage">
            <h1><xsl:apply-templates select="title/node()"/> </h1>
            <h2><xsl:apply-templates select="authors/node()"/> <br/>
                <xsl:apply-templates select="emails/node()"/></h2>
            <h3>Last update: <xsl:apply-templates select="lastupdate"/></h3>
        </div>
    </xsl:template>

	<xsl:template match="head">
		<xsl:copy>
			<xsl:apply-templates select="node()"/>
			<script language="JavaScript" type="text/javascript">
				function toggle(x,s) {
  				  if (s)
    			    document.getElementById(x).style.display = '';
  				  else
    				document.getElementById(x).style.display ='none';
				}
			</script>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="div[@id='main']/h1" priority="1">
		<h1><a name="{generate-id()}"><xsl:if test="@id"><xsl:number/>. </xsl:if><xsl:apply-templates select="node()"/></a></h1>
	</xsl:template>

	<xsl:template match="div[@id='main']/h2" priority="1">
		<h2><a name="{generate-id()}"><xsl:if test="@id">
			<xsl:number level="any" count="div[@id='main']/h1"/>
			<xsl:text>.</xsl:text>
			<xsl:number level="any" from="h1" count="h2"/>
			<xsl:text> </xsl:text>
		</xsl:if><xsl:apply-templates select="node()"/></a></h2>
	</xsl:template>

	<xsl:template match="@*|*|text()" priority="-100">
		<xsl:copy>
			<xsl:apply-templates select="@*|*|text()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="xact">
	  <xsl:text>X</xsl:text><span class="smallcaps">act</span>
	</xsl:template>

	<xsl:template match="doc">
		<xsl:variable name="url">
			<xsl:choose>
				<xsl:when test="@ref">
						<xsl:value-of select="@ref"/>
				</xsl:when>
				<xsl:otherwise>
					<xsl:text>dk/brics/jwig/</xsl:text>
						<xsl:value-of select="text()"/>
					<xsl:text>.html</xsl:text>
				</xsl:otherwise>
			</xsl:choose>
		</xsl:variable>
		<a href="../doc/index.html?{$url}">
			<tt><xsl:apply-templates select="node()"/></tt>
		</a>
	</xsl:template>

	<xsl:template match="sidecontents">
        <p>Contents:</p>
		<dl class="contents">
    		<xsl:for-each select="//div[@id='main']/h1">
    			<dt><xsl:number/>.</dt>
    			<dd onmouseover="return toggle('{generate-id(node())}',true)"
	   				onmouseout="return toggle('{generate-id(node())}',false)">
	   				<a href="#{generate-id()}"><xsl:apply-templates select="node()"/></a>
    				<div id="{generate-id(node())}" style="display: none">
    					<dl>
    						<xsl:call-template name="subsidecontents"/>
    					</dl>
    				</div>
   				</dd>
   			</xsl:for-each>
	  	</dl>
        (hover mouse to see subsections)
	</xsl:template>

	<xsl:template name="subsidecontents">
		<xsl:for-each select="following-sibling::*[local-name() = 'h1' or local-name() = 'h2'][1]">
			<xsl:if test="local-name() = 'h2'">
	    		<dt>
	    			<xsl:number level="any" count="div[@id='main']/h1"/>
					<xsl:text>.</xsl:text>
					<xsl:number level="any" from="h1" count="h2"/>
					<xsl:text> </xsl:text>
				</dt>
    			<dd>
					<a href="#{generate-id()}"><xsl:apply-templates select="node()"/></a>
				</dd>
				<xsl:call-template name="subsidecontents"/>
			</xsl:if>
   		</xsl:for-each>
   	</xsl:template>

	<xsl:template match="contents">
        <h2>Contents</h2>
		<dl class="contents">
    		<xsl:for-each select="//div[@id='main']/h1">
    			<dt><xsl:number/>.</dt>
    			<dd>
	   				<a href="#{generate-id()}"><xsl:apply-templates select="node()"/></a>
   					<dl>
   						<xsl:call-template name="subcontents"/>
   					</dl>
   				</dd>
   			</xsl:for-each>
	  	</dl>
	</xsl:template>

	<xsl:template name="subcontents">
		<xsl:for-each select="following-sibling::*[local-name() = 'h1' or local-name() = 'h2'][1]">
			<xsl:if test="local-name() = 'h2'">
	    		<dt>
	    			<xsl:number level="any" count="div[@id='main']/h1"/>
					<xsl:text>.</xsl:text>
					<xsl:number level="any" from="h1" count="h2"/>
					<xsl:text> </xsl:text>
				</dt>
    			<dd>
					<a href="#{generate-id()}"><xsl:apply-templates select="node()"/></a>
				</dd>
				<xsl:call-template name="subcontents"/>
			</xsl:if>
   		</xsl:for-each>
   	</xsl:template>

	<xsl:template match="example">
	    <div>
            <span class="caption"><xsl:apply-templates select="caption/node()"/></span>
		    <pre class="code"><xsl:apply-templates select="text()|*[local-name() != 'caption']"/></pre>
        </div>
    </xsl:template>

	<xsl:template match="ref">
		<a href="#{generate-id(key('rel',@idref))}">Section <xsl:for-each select="key('rel',@idref)">
				<xsl:choose>
					<xsl:when test="local-name() = 'h2'">
						<xsl:number level="any" count="div[@id='main']/h1"/>
						<xsl:text>.</xsl:text>
						<xsl:number level="any" from="h1" count="h2"/>
					</xsl:when>
					<xsl:otherwise><xsl:number/></xsl:otherwise>
				</xsl:choose>
			</xsl:for-each>
		</a>
	</xsl:template>

	<xsl:template match="remark">
		<div class="remark">
			<xsl:apply-templates select="node()"/>
		</div>
	</xsl:template>

	<xsl:template match="date">
        <xsl:value-of select="fn:format-date(fn:current-date(),'[MNn] [D1] [Y]','en',(),())"/>
	</xsl:template>

	<xsl:template match="exampleapp">
		<a href="#{generate-id(key('rel',@idref))}"><tt><xsl:apply-templates select="node()"/></tt></a>
	</xsl:template>

	<xsl:template match="dl[@class='bullet']//dt">
		<dt>&#x2022; <xsl:apply-templates select="node()"/></dt>
	</xsl:template>

</xsl:stylesheet>
