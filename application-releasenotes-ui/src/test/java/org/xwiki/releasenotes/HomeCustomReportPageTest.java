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
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xpn.xwiki.doc.XWikiDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Page test for {@code ReleaseNotes.Code.HomeCustomReport}.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
class HomeCustomReportPageTest extends PageTest
{
    private static final DocumentReference HOME_CUSTOM_REPORT =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Code"), "HomeCustomReport");

    /**
     * A radio button cannot be unchecked once one of its group has been selected, so the screenshot filter needs a
     * third option to get back to reporting on both kinds of changes. That option must be the default one and must
     * send an empty value, since the report page ignores empty parameters and thus applies no filter at all.
     */
    @Test
    void screenshotFilterOffersAnOptionForBothKinds() throws Exception
    {
        XWikiDocument customReport = loadPage(HOME_CUSTOM_REPORT);

        Elements radios = Jsoup.parse(customReport.getRenderedContent(this.context))
            .select("input[name=containsScreenshots]");

        assertEquals(3, radios.size(), "Expected an option for each of: both kinds, with, without.");
        assertOption(radios.get(0), "", true);
        assertOption(radios.get(1), "true", false);
        assertOption(radios.get(2), "false", false);
    }

    /**
     * A prompt written into the input's own {@code value} attribute gives the field no accessible name, and it is
     * submitted as the filter value when the author leaves the field untouched. Every control of the form must
     * instead be named by a label bound to it, and start out empty.
     */
    @Test
    void everyFilterIsNamedByALabelAndStartsEmpty() throws Exception
    {
        XWikiDocument customReport = loadPage(HOME_CUSTOM_REPORT);

        Document html = Jsoup.parse(customReport.getRenderedContent(this.context));

        Elements fields = html.select("form select, form input[type=text]");
        assertEquals(6, fields.size(), "Expected the layout selector and the five filters.");
        for (Element field : fields) {
            String id = field.attr("id");
            assertFalse(id.isEmpty(), "A field can only be bound to a label through an identifier.");
            assertEquals(1, html.select("label[for='" + id + "']").size(),
                "Expected exactly one label bound to the '" + id + "' field.");
            assertEquals("", field.attr("value"),
                "The '" + id + "' field must start empty, otherwise its prompt is submitted as a filter value.");
        }
    }

    private void assertOption(Element radio, String expectedValue, boolean expectedChecked)
    {
        assertEquals(expectedValue, radio.attr("value"));
        if (expectedChecked) {
            assertTrue(radio.hasAttr("checked"), "Expected the option to be the selected one.");
        } else {
            assertFalse(radio.hasAttr("checked"), "Expected the option not to be the selected one.");
        }
    }
}
