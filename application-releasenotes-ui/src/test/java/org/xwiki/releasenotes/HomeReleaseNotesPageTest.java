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
import org.xwiki.model.script.ModelScriptService;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Page test for {@code ReleaseNotes.Code.HomeReleaseNotes}.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
// Creating a release note resolves its reference through $services.model.
@ComponentList(ModelScriptService.class)
class HomeReleaseNotesPageTest extends PageTest
{
    private static final List<String> CODE_SPACE = List.of("ReleaseNotes", "Code");

    private static final DocumentReference HOME_RELEASE_NOTES =
        new DocumentReference("xwiki", CODE_SPACE, "HomeReleaseNotes");

    private static final String VALID_TOKEN = "valid-token";

    /**
     * The location the page redirected to, as passed to {@code $response.sendRedirect}, or {@code null} when the
     * page did not redirect.
     */
    private String redirect;

    @BeforeEach
    void setUp() throws Exception
    {
        // The creation form is only displayed to a user who can edit.
        registerVelocityTool("hasEdit", true);

        // The forms carry a CSRF token, and the creation action only runs when a valid one comes back.
        this.componentManager.registerComponent(ScriptService.class, "csrf",
            new CSRFTokenScriptServiceStub(VALID_TOKEN));

        // Creating the release note saves it, which the application pages are allowed to do on the author's behalf.
        when(this.oldcore.getMockRightService().hasAccessLevel(anyString(), anyString(), anyString(), any()))
            .thenReturn(true);
        when(this.oldcore.getMockRightService().hasProgrammingRights(any())).thenReturn(true);

        this.context.setResponse(new XWikiServletResponseStub()
        {
            @Override
            public void sendRedirect(String location)
            {
                HomeReleaseNotesPageTest.this.redirect = location;
            }
        });

        loadPage(new DocumentReference("xwiki", CODE_SPACE, "EntryVelocityMacros"));
    }

    /**
     * The creation of a release note changes the wiki, so it must not be doable by a forged cross-site request: a
     * request that does not carry back the form token creates nothing.
     */
    @Test
    void aForgedCreationRequestCreatesNoReleaseNote() throws Exception
    {
        this.request.put("action", "addReleaseNotes");
        this.request.put("product", "XWiki");
        this.request.put("version", "9.0");
        this.request.put("form_token", "not-the-token");

        renderHTMLPage(HOME_RELEASE_NOTES);

        assertTrue(releaseNote("9.0").isNew(),
            "A request without a valid form token must not create the release note.");
        assertNull(this.redirect, "A request that created nothing must not redirect to the new release note.");
    }

    /**
     * A creation request that carries back the form token, as the page's own form does, goes through and creates
     * the release note.
     */
    @Test
    void aCreationRequestCarryingTheTokenCreatesTheReleaseNote() throws Exception
    {
        this.request.put("action", "addReleaseNotes");
        this.request.put("product", "XWiki");
        this.request.put("version", "9.0");
        this.request.put("form_token", VALID_TOKEN);

        renderHTMLPage(HOME_RELEASE_NOTES);

        assertFalse(releaseNote("9.0").isNew(),
            "A request carrying a valid form token must create the release note.");
        assertNotNull(this.redirect, "The author must be redirected to the release note that was created.");
    }

    private XWikiDocument releaseNote(String shortVersion) throws Exception
    {
        return this.xwiki.getDocument(new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", "XWiki", shortVersion), "WebHome"), this.context);
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

    /**
     * The product field is pre-filled with the product name configured for the wiki, and no product name is shipped
     * by default so that the administrator has to choose one. Its prompt therefore has to be a placeholder rather
     * than a value: a value would be submitted as the product, and until a default is configured the placeholder is
     * the only thing the field shows.
     */
    @Test
    void theProductFieldPromptsWithAPlaceholder() throws Exception
    {
        Document html = renderHTMLPage(HOME_RELEASE_NOTES);

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
