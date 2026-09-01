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

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Page test for {@code ReleaseNotes.Code.HomeReleaseNotes}.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
class HomeReleaseNotesPageTest extends PageTest
{
    private static final List<String> CODE_SPACE = List.of("ReleaseNotes", "Code");

    private static final DocumentReference HOME_RELEASE_NOTES =
        new DocumentReference("xwiki", CODE_SPACE, "HomeReleaseNotes");

    @BeforeEach
    void setUp() throws Exception
    {
        // The creation form is only displayed to a user who can edit.
        registerVelocityTool("hasEdit", true);

        loadPage(new DocumentReference("xwiki", CODE_SPACE, "EntryVelocityMacros"));
    }

    /**
     * A prompt written into the input's own {@code value} attribute gives the field no accessible name, and when the
     * author leaves the field untouched it is submitted as the version, naming the created release note after the
     * prompt. The version is also what the creation refuses to do without, which the form has to say up front.
     */
    @Test
    void theCreationFieldsAreNamedByALabelAndTheVersionIsMarkedRequired() throws Exception
    {
        Document html = renderHTMLPage(HOME_RELEASE_NOTES);

        assertLabelled(html, "product");
        Element version = assertLabelled(html, "version");
        assertEquals("", version.attr("value"),
            "The version must start empty, otherwise its prompt names the created release note.");
        assertTrue(version.hasAttr("required"), "Creating a release note without a version is refused.");
    }

    private Element assertLabelled(Document html, String id)
    {
        Element field = html.selectFirst("input[type=text]#" + id);
        assertEquals(1, html.select("label[for='" + id + "']").size(),
            "Expected exactly one label bound to the '" + id + "' field.");
        return field;
    }
}
