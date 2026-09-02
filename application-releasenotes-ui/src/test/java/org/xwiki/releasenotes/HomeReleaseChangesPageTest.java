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

import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.icon.IconManager;
import org.xwiki.livedata.internal.macro.LiveDataMacroComponentList;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.skinx.SkinExtension;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xpn.xwiki.doc.XWikiDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Page test for {@code ReleaseNotes.Code.HomeReleaseChanges}.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
@LiveDataMacroComponentList
class HomeReleaseChangesPageTest extends PageTest
{
    private static final DocumentReference HOME_RELEASE_CHANGES =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Code"), "HomeReleaseChanges");

    private static final DocumentReference ENTRY_VELOCITY_MACROS =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Code"), "EntryVelocityMacros");

    @BeforeEach
    void setUp() throws Exception
    {
        // The Live Data macro asks the icon manager for the icons of its layouts, and pulls its JavaScript through
        // the jsfx skin extension, which wraps a plugin that a page test does not start.
        this.componentManager.registerMockComponent(IconManager.class);
        this.componentManager.registerMockComponent(SkinExtension.class, "jsfx");
    }

    /**
     * The changes are queried sorted on their creation date, most recent change first, since a reader of that list
     * looks for what has been added last. The list has no order of its own otherwise: the results page adds no
     * {@code order by} clause when it is given no sort, which leaves the order of the rows to the database.
     * <p>
     * The sort is asserted on the configuration the macro hands to Live Data rather than on the order of the
     * displayed rows, because a list of rows that happens to come back in the expected order proves nothing about
     * the sort being applied.
     */
    @Test
    void changesAreSortedOnTheirCreationDateMostRecentFirst() throws Exception
    {
        loadPage(ENTRY_VELOCITY_MACROS);
        XWikiDocument homeReleaseChanges = loadPage(HOME_RELEASE_CHANGES);

        String html = homeReleaseChanges.getRenderedContent(this.context);
        Element liveData = Jsoup.parse(html).selectFirst("#releasenoteschanges");
        assertNotNull(liveData, "Expected the changes Live Data to be rendered, got: " + html);

        JsonNode sort = new ObjectMapper().readTree(liveData.attr("data-config")).path("query").path("sort");

        assertEquals(1, sort.size(), "Expected the changes to be sorted on a single property.");
        assertEquals("doc.creationDate", sort.get(0).path("property").asText());
        assertTrue(sort.get(0).path("descending").asBoolean(), "Expected the most recent changes first.");
    }

    /**
     * A prompt written into the input's own {@code value} attribute gives the field no accessible name, and when the
     * author leaves the field untouched it is submitted as the version. The version is also what
     * {@code #handleAddAction} refuses to work without, which the form has to say up front.
     */
    @Test
    void theCreationFieldsAreNamedByALabelAndTheVersionIsMarkedRequired() throws Exception
    {
        // The creation form is only displayed to a user who can edit.
        registerVelocityTool("hasEdit", true);
        loadPage(ENTRY_VELOCITY_MACROS);

        Document html = renderHTMLPage(HOME_RELEASE_CHANGES);

        assertLabelled(html, "product");
        Element version = assertLabelled(html, "version");
        assertEquals("", version.attr("value"),
            "The version must start empty, otherwise its prompt is submitted as the version of the change.");
        assertTrue(version.hasAttr("required"), "Creating a change without a version is refused.");
    }

    /**
     * The product field is pre-filled with the product name configured for the wiki, and no product name is shipped
     * by default so that the administrator has to choose one. Its prompt therefore has to be a placeholder rather
     * than a value: a value would be submitted as the product of the change, and until a default is configured the
     * placeholder is the only thing the field shows.
     */
    @Test
    void theProductFieldPromptsWithAPlaceholder() throws Exception
    {
        // The creation form is only displayed to a user who can edit.
        registerVelocityTool("hasEdit", true);
        loadPage(ENTRY_VELOCITY_MACROS);

        Document html = renderHTMLPage(HOME_RELEASE_CHANGES);

        Element product = html.selectFirst("input[type=text]#product");
        assertFalse(product.attr("placeholder").isEmpty(),
            "Expected the product field to prompt with a placeholder, got: " + product);
    }

    private Element assertLabelled(Document html, String id)
    {
        Element field = html.selectFirst("input[type=text]#" + id);
        assertEquals(1, html.select("label[for='" + id + "']").size(),
            "Expected exactly one label bound to the '" + id + "' field.");
        return field;
    }
}
