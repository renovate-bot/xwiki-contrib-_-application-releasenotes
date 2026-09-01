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
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.edit.EditConfiguration;
import org.xwiki.edit.internal.DefaultEditorDescriptorBuilder;
import org.xwiki.edit.internal.DefaultEditorManager;
import org.xwiki.edit.internal.TextSyntaxContentEditor;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xpn.xwiki.doc.XWikiDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Page test for {@code ReleaseNotes.Code.Change.ChangeSheet} in edit mode.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
// The textarea properties of a change are edited through the editor components.
@ComponentList({
    DefaultEditorManager.class,
    DefaultEditorDescriptorBuilder.class,
    TextSyntaxContentEditor.class
})
class ChangeSheetPageTest extends PageTest
{
    private static final List<String> CODE_SPACE = List.of("ReleaseNotes", "Code");

    private static final List<String> CHANGE_SPACE = List.of("ReleaseNotes", "Code", "Change");

    private static final DocumentReference ENTRY_CLASS = new DocumentReference("xwiki", CODE_SPACE, "EntryClass");

    private static final DocumentReference CHANGE_CLASS = new DocumentReference("xwiki", CHANGE_SPACE, "ChangeClass");

    private static final DocumentReference CHANGE =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", "XWiki", "8.3M1", "Entry001"), "WebHome");

    @BeforeEach
    void setUp() throws Exception
    {
        // The textarea properties ask for the configured default editor before rendering themselves.
        this.componentManager.registerMockComponent(EditConfiguration.class);

        loadPage(ENTRY_CLASS);
        loadPage(CHANGE_CLASS);
        loadPage(new DocumentReference("xwiki", CODE_SPACE, "EntryVelocityMacros"));
        loadPage(new DocumentReference("xwiki", CHANGE_SPACE, "ChangeDisplayerVelocityMacros"));
        loadPage(new DocumentReference("xwiki", CHANGE_SPACE, "ChangeSheet"));
    }

    /**
     * The sheet lays its fields out as a definition list. A term is only text, so it gives the input it sits next to
     * no accessible name and does not move the focus into it when clicked: each term must hold a label bound to the
     * identifier that XWiki generates for the field.
     */
    @Test
    void everyEditedFieldIsNamedByALabelBoundToIt() throws Exception
    {
        Document html = renderChangeInEditMode();

        Elements labels = html.select("dt label");
        assertEquals(9, labels.size(), "Expected a label for each of the nine edited fields.");
        for (Element label : labels) {
            String target = label.attr("for");
            assertFalse(target.isEmpty(), "The '" + label.text() + "' label is bound to no field.");
            assertEquals(1, html.select("#" + escapeCssIdentifier(target)).size(),
                "Expected exactly one field with the '" + target + "' identifier, named by the '" + label.text()
                    + "' label.");
        }
    }

    /**
     * The screenshots displayer names its picker apart from the property, the property value itself being carried by
     * a hidden input. The label must point at the picker, which is the control the author interacts with, and not at
     * the hidden input, which no one can focus.
     */
    @Test
    void screenshotsLabelIsBoundToThePickerAndNotToTheHiddenValue() throws Exception
    {
        Document html = renderChangeInEditMode();

        String pickerId = "ReleaseNotes.Code.Change.ChangeClass_0_screenshots_picker";
        Elements labels = html.select("dt label[for='" + pickerId + "']");
        assertEquals(1, labels.size(), "Expected the screenshots label to be bound to the picker.");
        assertEquals("select", html.select("#" + escapeCssIdentifier(pickerId)).get(0).tagName());
    }

    private Document renderChangeInEditMode() throws Exception
    {
        XWikiDocument change = new XWikiDocument(CHANGE);
        change.newXObject(ENTRY_CLASS, this.context);
        change.newXObject(CHANGE_CLASS, this.context);
        // The sheet is included rather than applied so that the change stays the current document, which is what the
        // sheet mechanism does and what the sheet relies on to find its objects.
        change.setContent("{{include reference=\"ReleaseNotes.Code.Change.ChangeSheet\" context=\"current\"/}}");
        this.xwiki.saveDocument(change, this.context);
        this.context.setDoc(change);
        this.context.setAction("edit");

        return renderHTMLPage(change);
    }

    /**
     * The generated field identifiers hold the dots of the class reference, which a CSS identifier selector reads as
     * class names.
     */
    private String escapeCssIdentifier(String identifier)
    {
        return identifier.replace(".", "\\.");
    }
}
