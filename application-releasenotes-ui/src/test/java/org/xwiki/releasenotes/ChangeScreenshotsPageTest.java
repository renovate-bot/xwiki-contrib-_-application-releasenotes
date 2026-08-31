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
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Page test for the custom display of the {@code screenshots} property of
 * {@code ReleaseNotes.Code.Change.ChangeClass}.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
class ChangeScreenshotsPageTest extends PageTest
{
    private static final DocumentReference CHANGE_CLASS =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Code", "Change"), "ChangeClass");

    private static final DocumentReference CHANGE =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", "XWiki", "8.3M1", "Entry001"), "WebHome");

    private static final String FIELD_NAME = "ReleaseNotes.Code.Change.ChangeClass_0_screenshots";

    /**
     * The picker is a multiple SELECT, but the property is a String, for which XWiki only keeps the first submitted
     * value. The value that gets saved must therefore come from a hidden input holding the whole comma separated list,
     * and the picker itself must be named apart so that it cannot be mistaken for the property.
     */
    @Test
    void editDisplaysAnAttachmentPickerBackedByAHiddenInput() throws Exception
    {
        org.jsoup.nodes.Document html = Jsoup.parseBodyFragment(displayScreenshots("a.png, b.png", "edit"));

        Elements values = html.select("input[type=hidden][name=" + FIELD_NAME + "]");
        assertEquals(1, values.size(), "The property value must be submitted by a single hidden input.");
        assertEquals("a.png, b.png", values.get(0).attr("value"));

        Elements pickers = html.select("select.suggest-attachments");
        assertEquals(1, pickers.size(), "The property must be edited through the attachment picker.");
        assertTrue(pickers.get(0).hasAttr("multiple"), "A change can illustrate itself with several media.");
        assertEquals(FIELD_NAME + "_picker", pickers.get(0).attr("name"),
            "The picker must not carry the property name, otherwise only its first value would be saved.");
        assertEquals("true", pickers.get(0).attr("data-upload-allowed"),
            "Uploading a screenshot from the form is the whole point of the picker.");

        // The stored format tolerates spaces around the commas, and each item must end up pre-selected.
        assertEquals(List.of("a.png", "b.png"),
            pickers.get(0).select("option[selected]").eachAttr("value"));
    }

    @Test
    void viewDisplaysThePlainValue() throws Exception
    {
        assertEquals("a.png,b.png", displayScreenshots("a.png,b.png", "view").trim());
    }

    /**
     * A custom displayer that evaluates to nothing is ignored by XWiki, which would silently bring back the default
     * displayer for any mode the custom display doesn't know about.
     */
    @Test
    void unknownDisplayModeStillDisplaysSomething() throws Exception
    {
        assertTrue(displayScreenshots("a.png", "unknown").contains("a.png"));
    }

    private String displayScreenshots(String screenshots, String type) throws Exception
    {
        loadPage(CHANGE_CLASS);

        XWikiDocument change = new XWikiDocument(CHANGE);
        BaseObject changeObject = change.newXObject(CHANGE_CLASS, this.context);
        changeObject.setStringValue("screenshots", screenshots);
        this.xwiki.saveDocument(change, this.context);
        this.context.setDoc(change);

        return change.display("screenshots", type, changeObject, this.context);
    }
}
