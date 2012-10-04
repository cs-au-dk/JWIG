<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:fn="http://www.w3.org/2005/xpath-functions"
        xmlns:s="http://cs.au.dk/~schwarz">

	<xsl:output
		method="text"
		encoding="ISO-8859-1"/>

    <xsl:strip-space elements="*"/>
	
	<xsl:key name="rel" match="h1|h2" use="@id"/>

	<xsl:template match="head">
	</xsl:template>

     <xsl:template match="titlepage">
            \title{<xsl:apply-templates select="title/node()"/>}
            \author{<xsl:apply-templates select="authors/node()"/> \\
                <xsl:apply-templates select="emails/node()"/>}
            \date{<xsl:apply-templates select="lastupdate"/>}
         \maketitle
    </xsl:template>

	<xsl:template match="div[@id='main']/h1" priority="1">
		<xsl:text>\section{</xsl:text><xsl:apply-templates select="node()"/><xsl:text>}</xsl:text>
        <xsl:if test="@id"><xsl:text>\label{</xsl:text><xsl:value-of select="@id"/><xsl:text>}</xsl:text></xsl:if>
	</xsl:template>

    <xsl:template match="div[@id='main']/h3" priority="1">
            <xsl:text>\subsubsection{</xsl:text><xsl:apply-templates select="node()"/><xsl:text>}</xsl:text>
            <xsl:if test="@id"><xsl:text>\label{</xsl:text><xsl:value-of select="@id"/><xsl:text>}</xsl:text></xsl:if>
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
	  <xsl:text>\textsc{Xact}</xsl:text>
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
		</xsl:variable>\href{http://www.brics.dk/JWIG/doc-public/index.html?<xsl:value-of select="$url"/>}{\texttt{<xsl:apply-templates select="node()"/>}}</xsl:template>


	<xsl:template match="contents">
        \tableofcontents
        \newpage
	</xsl:template>



	<xsl:template match="example">
        <xsl:variable name="size" select="if(fn:string-length(fn:string-join(text(), '')) > 500) then '\tiny' else '\scriptsize'" /><xsl:text>\begin{lstlisting}[basicstyle=</xsl:text><xsl:value-of select="$size"/><xsl:text> ,frame=single,title=</xsl:text><xsl:apply-templates select="caption/node()"/>]<xsl:value-of select="text()|*[local-name() != 'caption']"/>
        <xsl:text>\end{lstlisting}</xsl:text>
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
        <xsl:text>\begin{description}</xsl:text>
        <xsl:apply-templates/><xsl:text>
        \end{description}</xsl:text>
    </xsl:template>

    <xsl:template match="dt">
        \item{<xsl:apply-templates select="node()"/>}\\
    </xsl:template>

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
        \usepackage{listings}
        \setcounter{tocdepth}{2}
        \lstset{language=java,morekeywords={\[\[,\]\],&lt;\[,\]&gt;}}
        \usepackage{tabularx}



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
        fn:replace(
        fn:replace(
        fn:replace($in, '\\', '\\\\'),
        '\$', '\\\$'),
        '&amp;','\\&amp;'),
        '&lt;','\$&lt;\$'),
        '&gt;','\$&gt;\$'),
        '\{','\\{'),
        '\}','\\}'),
        '_','\\_')"/>
    </xsl:function>

    <xsl:template match="text()"><xsl:value-of select="s:escapelatex(.)"/></xsl:template>

    <xsl:template match="tt">\texttt{<xsl:apply-templates/>}</xsl:template>

    <xsl:template match="i">\textit{<xsl:apply-templates/>}</xsl:template>

    <xsl:template match="b">\textbf{<xsl:apply-templates/>}</xsl:template>

    <xsl:template match="pre">\\\texttt{<xsl:apply-templates/>}<xsl:if test="following-sibling::*">\\\\</xsl:if></xsl:template>

    <xsl:template match="pre//text()"><xsl:value-of select="fn:replace(s:escapelatex(fn:replace(., '\s+$', '', 'm')), '\n','\\\\')"/></xsl:template>


    <xsl:template match="ul">
        <xsl:text>\begin{itemize}</xsl:text>
        <xsl:apply-templates/>
        <xsl:text>\end{itemize}</xsl:text>
    </xsl:template>

    <xsl:template match="ol">
        <xsl:text>\begin{enumerate}</xsl:text>
        <xsl:apply-templates/>
        <xsl:text>\end{enumerate}</xsl:text>
    </xsl:template>

    <xsl:template match="li">
        <xsl:text>\item </xsl:text>
        <xsl:apply-templates/>
    </xsl:template>

    <xsl:template match="p">
        <xsl:apply-templates/>
        <xsl:text>

        </xsl:text>
    </xsl:template>

    <xsl:template match="a">
        <xsl:choose>
            <xsl:when test="text() = @href">\url{<xsl:value-of select="@href"/>}</xsl:when>
            <xsl:otherwise>\href{<xsl:value-of select="@href"/>}{<xsl:apply-templates/>}</xsl:otherwise>
        </xsl:choose>
    </xsl:template>

    <xsl:template match="table">
        <xsl:text>

            \begin{tabularx}{\linewidth}{lll}</xsl:text>
        <xsl:apply-templates/>
        <xsl:text>\end{tabularx}

        </xsl:text>
    </xsl:template>

    <xsl:template match="tr">
        <xsl:apply-templates/>\\
    </xsl:template>

    <xsl:template match="td"><xsl:apply-templates/>&amp;</xsl:template>

    <xsl:template match="th">\textbf{<xsl:apply-templates/>}&amp;</xsl:template>
</xsl:stylesheet>
