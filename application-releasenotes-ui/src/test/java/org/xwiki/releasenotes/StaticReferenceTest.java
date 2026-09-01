/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.releasenotes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks that the pages of the application address each other by the space they are installed in, {@code ReleaseNotes},
 * rather than working out at runtime which space they are running in.
 * <p>
 * Resolving the space at runtime was there to let the application be copied to another top-level space. It cannot
 * deliver that: the {@code XWiki.ClassSheetBinding} objects, {@code Code.ReleaseNotesConfig} and the applications
 * panel entry hold plain xobject values that no interpolation reaches, so a copy goes on using the original's sheets,
 * configuration and panel entry regardless.
 * <p>
 * What it did deliver was a reference, an XWQL statement, an HTML attribute and a wiki macro parameter each built out
 * of the name of whichever page was being rendered — free-form text the application neither chooses nor can predict.
 * Naming the install space keeps every one of them a constant.
 *
 * @version $Id$
 */
class StaticReferenceTest
{
    private static final String TOP_SPACE = "ReleaseNotes";

    /**
     * A script extension that does not parse its code sends it to the browser verbatim, so nothing in it resolves at
     * runtime. Every other kind of code carried by an object, a wiki macro's in particular, is evaluated.
     */
    private static final Set<String> SCRIPT_EXTENSION_CLASSES =
        Set.of("XWiki.JavaScriptExtension", "XWiki.StyleSheetExtension");

    /**
     * How a page used to name the space it was running in, and the variable it kept the answer in.
     */
    private static final List<String> RUNTIME_SPACE_RESOLUTION =
        List.of("extractFirstReference('SPACE')", "topSpace");

    @Test
    void noPageWorksOutAtRuntimeWhichSpaceItIsRunningIn() throws Exception
    {
        List<String> offendingLines =
            collectLines(line -> RUNTIME_SPACE_RESOLUTION.stream().anyMatch(line::contains));

        assertEquals(List.of(), offendingLines,
            "These lines take a space name from the page being rendered. References must name " + TOP_SPACE
                + " instead, so that nothing the application builds depends on what that page is called.");
    }

    /**
     * Applies the passed predicate to every line of every text the wiki evaluates: each page's content, and the code
     * of each object carrying some.
     *
     * @return the lines the predicate accepted, each prefixed by where it was found
     */
    private List<String> collectLines(Predicate<String> offends) throws Exception
    {
        Path applicationRoot = Path.of(getClass().getClassLoader().getResource(TOP_SPACE).toURI());
        Path resourceRoot = applicationRoot.getParent();

        List<String> offendingLines = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(applicationRoot)) {
            for (Path page : paths.filter(path -> path.getFileName().toString().endsWith(".xml")).sorted().toList()) {
                collectLines(page, resourceRoot.relativize(page).toString(), offends, offendingLines);
            }
        }
        return offendingLines;
    }

    private void collectLines(Path page, String name, Predicate<String> offends, List<String> offendingLines)
        throws Exception
    {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        Element document = factory.newDocumentBuilder().parse(page.toFile()).getDocumentElement();

        collectLines(childText(document, "content"), name + " (content)", offends, offendingLines);
        for (Element object : children(document, "object")) {
            String className = childText(object, "className");
            if (SCRIPT_EXTENSION_CLASSES.contains(className) && !"1".equals(propertyValue(object, "parse"))) {
                continue;
            }
            collectLines(propertyValue(object, "code"), name + " (" + className + ")", offends, offendingLines);
        }
    }

    private void collectLines(String code, String location, Predicate<String> offends, List<String> offendingLines)
    {
        for (String line : code.split("\n")) {
            if (offends.test(line)) {
                offendingLines.add(location + ": " + line.trim());
            }
        }
    }

    /**
     * @return the value of the named property of the passed object, looking only at the object's own properties and
     *     not at the class definition it carries, which repeats every property name
     */
    private String propertyValue(Element object, String propertyName)
    {
        for (Element property : children(object, "property")) {
            String value = childText(property, propertyName);
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private String childText(Element parent, String childName)
    {
        List<Element> found = children(parent, childName);
        return found.isEmpty() ? "" : found.get(0).getTextContent();
    }

    private List<Element> children(Element parent, String childName)
    {
        List<Element> found = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && childName.equals(node.getNodeName())) {
                found.add((Element) node);
            }
        }
        return found;
    }
}
