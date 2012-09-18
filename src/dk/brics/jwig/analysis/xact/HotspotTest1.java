package dk.brics.jwig.analysis.xact;

import dk.brics.xact.XML;

public class HotspotTest1 {

    public XML test1() {
        return XML
                .parseTemplate(
                        "<foo a='b' c=[D] e=[F]><[FOO]><[BAR]><bar/><bar/><input name=\"foo\"/><input name=\"bar\"/><input name=\"foo\"/><input name=\"baz\"/></foo>")
                .plug("D", "d").plug("FOO", XML.parseTemplate("<foo/>"));
    }

    public XML test2() {
        Object o = new Object();
        return XML.parseTemplate("<foo><[FOO]><[BAR]><[FOO]></foo>")
                .plug("FOO", o).plug("BAR", o);
    }
    public static void main(String[] args) {
        new HotspotTest1().test1();
        new HotspotTest1().test2();

    }

}
