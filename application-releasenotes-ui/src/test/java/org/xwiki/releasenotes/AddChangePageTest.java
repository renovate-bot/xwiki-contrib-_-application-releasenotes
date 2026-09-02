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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.localization.macro.internal.TranslationMacro;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.script.ModelScriptService;
import org.xwiki.query.internal.ScriptQuery;
import org.xwiki.query.script.QueryManagerScriptService;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.script.service.ScriptService;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.web.XWikiServletResponseStub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Page test for the {@code handleAddAction} macro of {@code ReleaseNotes.Code.EntryVelocityMacros}, which hands the
 * author of a new change the page that change is going to live in.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
// The macro addresses the pages of the release note through $services.model, and displays its error messages
// with the translation macro.
@ComponentList({ ModelScriptService.class, TranslationMacro.class })
class AddChangePageTest extends PageTest
{
    private static final DocumentReference ENTRY_VELOCITY_MACROS =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Code"), "EntryVelocityMacros");

    private static final DocumentReference TEST_PAGE =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Data"), "TestPage");

    /**
     * The spaces of the release note the changes are added to. The last one is the short form of the
     * {@code 8.3-milestone-1} version the macro is passed, and it carries a dot, so that the escaping the pages of
     * the release note are looked up with is exercised.
     */
    private static final List<String> VERSION_SPACES = List.of("ReleaseNotes", "Data", "XWiki", "8.3M1");

    @Mock
    private ScriptQuery query;

    @Mock
    private QueryManagerScriptService queryManagerScriptService;

    /**
     * The location the author is sent to, as passed to {@code $response.sendRedirect}.
     */
    private String redirect;

    @BeforeEach
    void setUp() throws Exception
    {
        this.componentManager.registerComponent(ScriptService.class, "query", this.queryManagerScriptService);
        when(this.queryManagerScriptService.xwql(anyString())).thenReturn(this.query);
        when(this.query.bindValue(anyString(), any())).thenReturn(this.query);
        when(this.query.execute()).thenReturn(List.of());

        // Taking the page of a new entry saves it, which its author needs the edit right for, and which the
        // application pages are allowed to do on their behalf.
        when(this.oldcore.getMockRightService().hasAccessLevel(anyString(), anyString(), anyString(), any()))
            .thenReturn(true);
        when(this.oldcore.getMockRightService().hasProgrammingRights(any())).thenReturn(true);

        this.context.setResponse(new XWikiServletResponseStub()
        {
            @Override
            public void sendRedirect(String location)
            {
                AddChangePageTest.this.redirect = location;
            }
        });

        loadPage(ENTRY_VELOCITY_MACROS);
    }

    /**
     * The page of a new entry is created before its author is sent to the editor, so that a second author adding a
     * change to the same release note at the same time is handed another page rather than that same one.
     */
    @Test
    void entryPageIsCreatedBeforeItsAuthorIsSentToTheEditor() throws Exception
    {
        addChange();

        XWikiDocument entryPage = entryPage("Entry001");
        assertFalse(entryPage.isNew(), "Expected the page of the new entry to have been created.");
        // The entry template is applied by the "edit" action the author is sent to, which only applies it to a page
        // without content.
        assertEquals("", entryPage.getContent());
        assertNotNull(this.redirect, "Expected the author to be sent to the page of the new entry.");
        assertTrue(this.redirect.contains("Entry001"), this.redirect);
    }

    /**
     * A second author adding a change while the first one is still filling theirs in is handed the page after the one
     * the first author was handed. The first change is only saved at the end of the editing session, so the pages the
     * query sees are still the ones saved before either author started.
     */
    @Test
    void twoAuthorsAddingAChangeAtTheSameTimeAreHandedTwoPages() throws Exception
    {
        addChange();
        String firstRedirect = this.redirect;
        addChange();

        assertFalse(entryPage("Entry001").isNew());
        assertFalse(entryPage("Entry002").isNew(), "Expected the second author to be handed another page.");
        assertTrue(firstRedirect.contains("Entry001"), firstRedirect);
        assertTrue(this.redirect.contains("Entry002"), this.redirect);
    }

    /**
     * The number of a new entry is one past the highest number in use, compared as a number: sorted as strings,
     * {@code Entry999} comes out above {@code Entry1000} and the number 1000 is handed out over and over.
     */
    @Test
    void entryNumbersAreComparedAsNumbersAndNotAsStrings() throws Exception
    {
        existingEntryPages("Entry999", "Entry1000");

        addChange();

        assertFalse(entryPage("Entry1001").isNew(), "Expected the new entry to be numbered 1001.");
    }

    /**
     * Renders a {@code handleAddAction} call, which is what the "Add Change" buttons of the application do.
     */
    private void addChange() throws Exception
    {
        XWikiDocument testPage = this.xwiki.getDocument(TEST_PAGE, this.context);
        testPage.setSyntax(Syntax.XWIKI_2_1);
        testPage.setContent("{{include reference=\"ReleaseNotes.Code.EntryVelocityMacros\"/}}\n\n"
            + "{{velocity}}\n"
            + "#handleAddAction('XWiki', '8.3-milestone-1', 'template=ReleaseNotes.Code.Change.ChangeTemplate')\n"
            + "{{/velocity}}");
        this.xwiki.saveDocument(testPage, this.context);
        this.context.setDoc(testPage);
        testPage.getRenderedContent(this.context);
    }

    /**
     * Creates the passed entry pages and makes the query looking for the pages of the release note return them, as it
     * does once they are saved.
     */
    private void existingEntryPages(String... names) throws Exception
    {
        List<Object> references = new ArrayList<>();
        for (String name : names) {
            XWikiDocument entryPage = entryPage(name);
            this.xwiki.saveDocument(entryPage, this.context);
            references.add(serialize(entryPage.getDocumentReference()));
        }
        when(this.query.execute()).thenReturn(references);
    }

    private XWikiDocument entryPage(String name) throws Exception
    {
        List<String> spaces = new ArrayList<>(VERSION_SPACES);
        spaces.add(name);
        return this.xwiki.getDocument(new DocumentReference("xwiki", spaces, "WebHome"), this.context);
    }

    private String serialize(DocumentReference reference) throws Exception
    {
        return this.componentManager.<EntityReferenceSerializer<String>>getInstance(
            EntityReferenceSerializer.TYPE_STRING).serialize(reference);
    }
}
