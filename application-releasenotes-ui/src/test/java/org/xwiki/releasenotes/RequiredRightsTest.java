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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks the rights the application's pages declare, against the level XWiki's own required-rights analyzer computes
 * for each of them.
 * <p>
 * A page that enforces its required rights refuses edit to a user who does not hold them, which is what keeps a code
 * page from being rewritten by a user who merely holds edit right — and a right check written in a page's content
 * only lasts as long as nobody rewrites that content.
 * <p>
 * The expectation is spelled out page by page rather than derived, so that adding a page to the application fails
 * this test until someone decides what that page requires.
 *
 * @version $Id$
 */
class RequiredRightsTest
{
    private static final String TOP_SPACE = "ReleaseNotes";

    private static final String SCRIPT = "script";

    private static final String WIKI_ADMIN = "wiki_admin";

    /**
     * Enforcing with no required right at all is the strongest setting: it stops a later editor from introducing
     * script into the page.
     */
    private static final String NOTHING = "";

    /**
     * Enforcement caps the rights of everything a page {@code {{include}}}s, and every document its script saves.
     * The four pages that build a release note out of {@code Code.ReleaseNoteTemplate} therefore cannot enforce
     * without either breaking the template's Velocity on the created note or making script right a condition of
     * authoring a release note. They are restricted instead by the rights object on {@code ReleaseNotes.Code}, and
     * the two of them outside that space carry no script of their own.
     */
    private static final String NOT_ENFORCED = null;

    private static final Map<String, String> EXPECTED_RIGHTS = new LinkedHashMap<>();

    static {
        // A wiki-scoped UI extension, and a wiki-scoped translation bundle, are wiki-administration-level acts in
        // themselves — this is not about the script they carry.
        EXPECTED_RIGHTS.put("ReleaseNotes/Code/ApplicationsPanelEntry.xml", WIKI_ADMIN);
        EXPECTED_RIGHTS.put("ReleaseNotes/Code/Translations.xml", WIKI_ADMIN);
        // The analyzer computes script for the migration, seeing only a Velocity macro. It stays at wiki_admin: the
        // page tests for wiki administration in its own content and then writes across the whole wiki, and script
        // right is no protection against the user who can rewrite that check — one who holds script right.
        EXPECTED_RIGHTS.put("ReleaseNotes/Code/MigrationFrom1x.xml", WIKI_ADMIN);

        EXPECTED_RIGHTS.put("ReleaseNotes/Code/Change/ChangeClass.xml", SCRIPT);
        EXPECTED_RIGHTS.put("ReleaseNotes/Code/Change/ChangeDisplayerFlow.xml", SCRIPT);
        EXPECTED_RIGHTS.put("ReleaseNotes/Code/Change/ChangeDisplayerGrid.xml", SCRIPT);
        EXPECTED_RIGHTS.put("ReleaseNotes/Code/Change/ChangeDisplayerList.xml", SCRIPT);
        EXPECTED_RIGHTS.put("ReleaseNotes/Code/Change/ChangeDisplayerSimple.xml", SCRIPT);
        EXPECTED_RIGHTS.put("ReleaseNotes/Code/Change/ChangeDisplayerVelocityMacros.xml", SCRIPT);
        EXPECTED_RIGHTS.put("ReleaseNotes/Code/Change/ChangeSheet.xml", SCRIPT);
        EXPECTED_RIGHTS.put("ReleaseNotes/Code/Change/DisplayChangesMacro.xml", SCRIPT);
        EXPECTED_RIGHTS.put("ReleaseNotes/Code/Change/GetChangesMacro.xml", SCRIPT);
        EXPECTED_RIGHTS.put("ReleaseNotes/Code/Change/ReleaseNotesChangesMacro.xml", SCRIPT);
        EXPECTED_RIGHTS.put("ReleaseNotes/Code/ContributorsSheet.xml", SCRIPT);
        EXPECTED_RIGHTS.put("ReleaseNotes/Code/EntryVelocityMacros.xml", SCRIPT);
        EXPECTED_RIGHTS.put("ReleaseNotes/Code/HTML5Video.xml", SCRIPT);
        EXPECTED_RIGHTS.put("ReleaseNotes/Code/HomeCustomReport.xml", SCRIPT);
        EXPECTED_RIGHTS.put("ReleaseNotes/Code/HomeReleaseChanges.xml", SCRIPT);
        EXPECTED_RIGHTS.put("ReleaseNotes/Code/ReleaseNotesContributorsMacro.xml", SCRIPT);
        EXPECTED_RIGHTS.put("ReleaseNotes/Code/Report.xml", SCRIPT);

        EXPECTED_RIGHTS.put("ReleaseNotes/Code/Change/ChangeTemplate.xml", NOTHING);
        EXPECTED_RIGHTS.put("ReleaseNotes/Code/Change/WebHome.xml", NOTHING);
        EXPECTED_RIGHTS.put("ReleaseNotes/Code/ContributorsClass.xml", NOTHING);
        EXPECTED_RIGHTS.put("ReleaseNotes/Code/ContributorsTemplate.xml", NOTHING);
        EXPECTED_RIGHTS.put("ReleaseNotes/Code/EntryClass.xml", NOTHING);
        EXPECTED_RIGHTS.put("ReleaseNotes/Code/ReleaseNoteClass.xml", NOTHING);
        EXPECTED_RIGHTS.put("ReleaseNotes/Code/ReleaseNotesConfig.xml", NOTHING);
        EXPECTED_RIGHTS.put("ReleaseNotes/Code/ReleaseNotesConfigClass.xml", NOTHING);
        EXPECTED_RIGHTS.put("ReleaseNotes/Code/WebHome.xml", NOTHING);
        EXPECTED_RIGHTS.put("ReleaseNotes/Code/WebPreferences.xml", NOTHING);

        EXPECTED_RIGHTS.put("ReleaseNotes/WebHome.xml", NOT_ENFORCED);
        EXPECTED_RIGHTS.put("ReleaseNotes/Data/WebHome.xml", NOT_ENFORCED);
        EXPECTED_RIGHTS.put("ReleaseNotes/Code/HomeReleaseNotes.xml", NOT_ENFORCED);
        EXPECTED_RIGHTS.put("ReleaseNotes/Code/ReleaseNoteTemplate.xml", NOT_ENFORCED);
    }

    private static final String REQUIRED_RIGHT_CLASS = "XWiki.RequiredRightClass";

    private static final String GLOBAL_RIGHTS_CLASS = "XWiki.XWikiGlobalRights";

    @Test
    void everyPageDeclaresTheRightItRequires() throws Exception
    {
        Map<String, String> declared = new LinkedHashMap<>();
        for (Path page : pages()) {
            declared.put(relativeName(page), declaredRight(page));
        }

        assertEquals(EXPECTED_RIGHTS, declared,
            "Each page must enforce its required rights and declare the level XWiki's required-rights analyzer "
                + "computes for it. A page missing from the expected values is a new page whose required right "
                + "nobody has decided yet.");
    }

    /**
     * The application's code is only as safe as the rule that says who may rewrite it, so the rule ships with it. An
     * allow rule is exclusive, which is why {@code view} is deliberately absent: listing it would deny view to
     * everyone outside the group.
     */
    @Test
    void onlyAdministratorsMayEditTheCodeSpace() throws Exception
    {
        Element webPreferences = parse(page("ReleaseNotes/Code/WebPreferences.xml"));
        List<Element> rights = objects(webPreferences, GLOBAL_RIGHTS_CLASS);

        assertEquals(1, rights.size(), "The code space's preferences page must carry exactly one rights object.");
        assertEquals("1", propertyValue(rights.get(0), "allow"), "The rule must allow rather than deny.");
        assertEquals("XWiki.XWikiAdminGroup", propertyValue(rights.get(0), "groups"),
            "Editing the application's own pages is an administrator's business.");
        assertEquals("delete,edit", propertyValue(rights.get(0), "levels"),
            "Only edit and delete are restricted: view must stay open, since an allow rule is exclusive.");
    }

    /**
     * @return the level the passed page declares, {@link #NOTHING} when it enforces without requiring anything, or
     *     {@link #NOT_ENFORCED} when it does not enforce at all
     */
    private String declaredRight(Path page) throws Exception
    {
        Element document = parse(page);
        if (!"true".equals(childText(document, "enforceRequiredRights"))) {
            return NOT_ENFORCED;
        }
        List<Element> requiredRights = objects(document, REQUIRED_RIGHT_CLASS);
        return requiredRights.isEmpty() ? NOTHING : propertyValue(requiredRights.get(0), "level");
    }

    private List<Path> pages() throws Exception
    {
        try (Stream<Path> paths = Files.walk(applicationRoot())) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".xml")).sorted().toList();
        }
    }

    private Path page(String relativeName) throws Exception
    {
        return applicationRoot().getParent().resolve(relativeName);
    }

    private Path applicationRoot() throws Exception
    {
        return Path.of(getClass().getClassLoader().getResource(TOP_SPACE).toURI());
    }

    private String relativeName(Path page) throws Exception
    {
        return applicationRoot().getParent().relativize(page).toString().replace('\\', '/');
    }

    private Element parse(Path page) throws Exception
    {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return factory.newDocumentBuilder().parse(page.toFile()).getDocumentElement();
    }

    private List<Element> objects(Element document, String className)
    {
        List<Element> found = new ArrayList<>();
        for (Element object : children(document, "object")) {
            if (className.equals(childText(object, "className"))) {
                found.add(object);
            }
        }
        return found;
    }

    /**
     * @return the value of the named property of the passed object, looking only at the object's own properties and
     *     not at the class definition it carries, which repeats every property name
     */
    private String propertyValue(Element object, String propertyName)
    {
        for (Element property : children(object, "property")) {
            for (Element value : children(property, propertyName)) {
                return value.getTextContent();
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
