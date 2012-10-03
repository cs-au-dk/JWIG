<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:fn="http://www.w3.org/2005/xpath-functions"
        xmlns:s="http://cs.au.dk/~schwarz">

	<xsl:output
		method="text"
		encoding="ISO-8859-1"/>
	
	<xsl:key name="rel" match="h1|h2" use="@id"/>

	<xsl:template match="head">
	</xsl:template>

	<xsl:template match="div[@id='main']/h1" priority="1">
		\section{<xsl:apply-templates select="node()"/>}
        <xsl:if test="@id">\label{<xsl:value-of select="@id"/>}</xsl:if>
	</xsl:template>

    <xsl:template match="div[@id='main']/h3" priority="1">
            \subsubsection{<xsl:apply-templates select="node()"/>}
            <xsl:if test="@id">\label{<xsl:value-of select="@id"/>}</xsl:if>
    </xsl:template>

	<xsl:template match="div[@id='main']/h2" priority="1">
		\subsection{<xsl:apply-templates select="node()"/>}
        <xsl:if test="@id">\label{<xsl:value-of select="@id"/>}</xsl:if>
	</xsl:template>

	<xsl:template match="@*|*|text()" priority="-100">
		<xsl:copy>
			<xsl:apply-templates select="@*|*|text()"/>
		</xsl:copy>
	</xsl:template>

	<xsl:template match="xact">
	  <xsl:text>X</xsl:text><span class="smallcaps">act</span>
	</xsl:template>

	<xsl:template match="doc"><xsl:variable name="url">
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
		</xsl:variable>\href{../doc/index.html?<xsl:value-of select="$url"/>}{\texttt{<xsl:apply-templates select="node()"/>}}</xsl:template>


	<xsl:template match="contents">
        \tableofcontents
	</xsl:template>



	<xsl:template match="example">
        \begin{Verbatim}[frame=single, label=<xsl:apply-templates select="caption/node()"/>]
		    <xsl:value-of select="text()|*[local-name() != 'caption']"/>
        \end{Verbatim}
    </xsl:template>

	<xsl:template match="ref">Section \ref{<xsl:value-of select="@idref"/>}</xsl:template>

	<xsl:template match="remark">
		\fxfatal{<xsl:apply-templates select="node()"/>}
	</xsl:template>

	<xsl:template match="date">
        <xsl:value-of select="fn:format-date(fn:current-date(),'[MNn] [D] [Y]','en',(),())"/>
	</xsl:template>

	<xsl:template match="exampleapp">
		<a href="#{generate-id(key('rel',@idref))}"><tt><xsl:apply-templates select="node()"/></tt></a>
	</xsl:template>

    <xsl:template match="dl">
        \begin{description}
        <xsl:apply-templates/>
        \end{description}
    </xsl:template>

    <xsl:template match="dt">\item{<xsl:apply-templates select="node()"/>}</xsl:template>

	<xsl:template match="html">
        \documentclass[11pt]{article}

        \usepackage[latin1]{inputenc}
        \usepackage[american]{babel}
        \usepackage{a4}
        \usepackage{latexsym}
        \usepackage{fancyvrb}
        \usepackage{hyperref}
        \usepackage{parskip}
        \usepackage[draft]{fixme}


        \begin{document}
        <xsl:apply-templates/>
        \end{document}
    </xsl:template>

    <xsl:function name="s:escapelatex">
        <xsl:param name="in"/>
        <xsl:value-of select="
        fn:replace(
        fn:replace(
        fn:replace(
        fn:replace(
        fn:replace(
        fn:replace($in, '\\', '\\\\'),
        '\$', '\\\$'),
        '&amp;','\\&amp;'),
        '&lt;','\$&lt;\$'),
        '&gt;','\$&gt;\$'),
        '_','\\_')"/>
    </xsl:function>

    <xsl:template match="text()"><xsl:value-of select="s:escapelatex(.)"/></xsl:template>

    <xsl:template match="tt">\texttt{<xsl:apply-templates/>}</xsl:template>

    <xsl:template match="i">\textit{<xsl:apply-templates/>}</xsl:template>

    <xsl:template match="b">\textbf{<xsl:apply-templates/>}</xsl:template>

    <xsl:template match="pre">\texttt{<xsl:apply-templates/>}\\</xsl:template>

    <xsl:template match="pre//text()"><xsl:value-of select="fn:replace(s:escapelatex(.), '\n','\\\\')"/></xsl:template>


    <xsl:template match="ul">\begin{itemize}
        <xsl:apply-templates/>
        \end{itemize}</xsl:template>

    <xsl:template match="ol">\begin{enumerate}
        <xsl:apply-templates/>
        \end{enumerate}</xsl:template>

    <xsl:template match="li">\item <xsl:apply-templates/></xsl:template>

    <xsl:template match="p"><xsl:apply-templates/>\hspace*{\fill}\\</xsl:template>

    <xsl:template match="a">
        <xsl:if test="text() = @href">\url{<xsl:value-of select="@href"/>}</xsl:if>
        <xsl:if test="text() != @href">\href{<xsl:value-of select="@href"/>}{<xsl:value-of select="text()"/>}</xsl:if>

    </xsl:template>

    <xsl:template match="table">
        \begin{tabular}{lll}
        <xsl:apply-templates/>
        \end{tabular}
    </xsl:template>

    <xsl:template match="tr">
        <xsl:apply-templates/>\\
    </xsl:template>

<xsl:template match="td"><xsl:apply-templates/>&amp;</xsl:template>
    <xsl:template match="th">\textbf{<xsl:apply-templates/>}&amp;</xsl:template>


</xsl:stylesheet>
