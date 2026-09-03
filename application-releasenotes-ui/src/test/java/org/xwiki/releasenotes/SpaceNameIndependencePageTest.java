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

import org.junit.jupiter.api.Test;
import org.xwiki.localization.macro.internal.TranslationMacro;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.script.service.ScriptService;
import org.xwiki.rendering.wikimacro.internal.WikiMacroFactoryComponentClass;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.WikiMacroSetup;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Page test rendering a change from a page whose top-level space carries an awkward name.
 * <p>
 * A space name is free-form text chosen by whoever created the space, and it reaches the application through
 * {@code $doc}. What the application renders must not depend on it: the pages address each other by the space they
 * are installed in, so a change renders the same whatever the page calling for it is filed under.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
@WikiMacroFactoryComponentClass
// The pages under test display their strings with the translation macro.
@ComponentList(TranslationMacro.class)
class SpaceNameIndependencePageTest extends PageTest
{
    private static final List<String> CHANGE_SPACES = List.of("ReleaseNotes", "Code", "Change");

    private static final DocumentReference CHANGE_CLASS =
        new DocumentReference("xwiki", CHANGE_SPACES, "ChangeClass");

    private static final DocumentReference DISPLAY_CHANGES_MACRO =
        new DocumentReference("xwiki", CHANGE_SPACES, "DisplayChangesMacro");

    /**
     * A name made of the quotes and braces that delimit wiki syntax, which nothing stops a space name containing. Were
     * any of it to reach what the application builds, the marker below would surface in the rendered page.
     */
    private static final String AWKWARD_SPACE =
        "Odd\"/}}{{velocity}}NAME-LEAKED{{/velocity}}{{include reference=\"x";

    @Test
    void aChangeRendersTheSameWhateverTheCallingPageSpaceIsNamed() throws Exception
    {
        loadPage(new DocumentReference("xwiki", CHANGE_SPACES, "ChangeDisplayerVelocityMacros"));
        loadPage(new DocumentReference("xwiki", CHANGE_SPACES, "ChangeDisplayerList"));
        loadPage(CHANGE_CLASS);
        WikiMacroSetup.loadWikiMacro(this, this.componentManager, DISPLAY_CHANGES_MACRO);
        // A PageTest does not register $services.rendering, which the list displayer escapes the title with.
        this.componentManager.registerComponent(ScriptService.class, "rendering", new RenderingScriptServiceStub());

        DocumentReference change = createChange("Grid displayer", "Changes are now laid out by CSS grid.");
        String rendered = renderDisplayChangesFrom(AWKWARD_SPACE, change);

        assertFalse(rendered.contains("NAME-LEAKED"),
            "The calling page's space name reached what the application rendered: " + rendered);
        // The change still has to render, so that the assertion above is not passing on an empty page.
        assertTrue(rendered.contains("Grid displayer"), rendered);
        assertTrue(rendered.contains("Changes are now laid out by CSS grid."), rendered);
        // An unresolved include leaves an error block behind, and the Velocity macros it brings in would otherwise
        // reach the output as their own literal call.
        assertFalse(rendered.contains("generateDisplayEditLink"), rendered);
        assertFalse(rendered.contains("macroerror"), rendered);
    }

    /**
     * Renders a {@code displayChanges} call from the home page of the named top-level space.
     */
    private String renderDisplayChangesFrom(String space, DocumentReference change) throws Exception
    {
        XWikiDocument caller = new XWikiDocument(new DocumentReference("xwiki", List.of(space), "WebHome"));
        caller.setSyntax(Syntax.XWIKI_2_1);
        caller.setContent("{{velocity}}#set ($changes = ['" + serialize(change) + "']){{/velocity}}\n\n"
            + "{{displayChanges displayer=\"list\" contextVariable=\"changes\"/}}");
        this.xwiki.saveDocument(caller, this.context);
        this.context.setDoc(caller);
        return caller.getRenderedContent(this.context);
    }

    private DocumentReference createChange(String title, String summary) throws Exception
    {
        DocumentReference reference = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", "XWiki", "8.3M1", "Entry001"), "WebHome");
        XWikiDocument change = new XWikiDocument(reference);
        BaseObject changeObject = change.newXObject(CHANGE_CLASS, this.context);
        changeObject.setStringValue("title", title);
        changeObject.setLargeStringValue("summary", summary);
        this.xwiki.saveDocument(change, this.context);
        return reference;
    }

    private String serialize(DocumentReference reference) throws Exception
    {
        return this.componentManager.<EntityReferenceSerializer<String>>getInstance(
            EntityReferenceSerializer.TYPE_STRING).serialize(reference);
    }
}
