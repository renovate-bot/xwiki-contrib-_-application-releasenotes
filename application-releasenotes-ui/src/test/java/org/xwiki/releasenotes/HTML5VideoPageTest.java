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
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.wikimacro.internal.WikiMacroFactoryComponentClass;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.WikiMacroSetup;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xpn.xwiki.doc.XWikiDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Page test for the {@code html5video} wiki macro, defined in {@code ReleaseNotes.Code.HTML5Video}.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
@WikiMacroFactoryComponentClass
// The macro resolves the attachment it embeds through $services.model.
@ComponentList(ModelScriptService.class)
class HTML5VideoPageTest extends PageTest
{
    private static final DocumentReference HTML5_VIDEO_MACRO =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Code"), "HTML5Video");

    private static final DocumentReference TEST_PAGE =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Data"), "TestPage");

    @BeforeEach
    void setUp() throws Exception
    {
        // The macro emits its player from a clean=false HTML block, which its (privileged) author is allowed to do.
        when(this.oldcore.getMockRightService().hasProgrammingRights(any())).thenReturn(true);

        WikiMacroSetup.loadWikiMacro(this, this.componentManager, HTML5_VIDEO_MACRO);
    }

    /**
     * The {@code width} the macro is given is placed into an HTML attribute of the player, so a value carrying an
     * attribute delimiter must be emitted escaped: left raw, it would break out of the attribute and inject markup
     * that runs for everyone who views the page embedding the player.
     */
    @Test
    void widthIsEscapedIntoThePlayerAttribute() throws Exception
    {
        // The width as it reaches the macro: a value carrying a quote and a script element. The quote is written
        // ~" in XWiki 2.1 syntax so that it is part of the parameter value rather than closing it.
        String widthWikiSyntax = "~\"><script>alert(1)</script>";
        String widthValue = "\"><script>alert(1)</script>";

        Document html = renderVideo("ReleaseNotes.Data.TestPage@demo.mp4", widthWikiSyntax);

        Element video = html.selectFirst("video");
        assertFalse(video == null, "Expected the macro to render a video player.");
        // jsoup decodes the attribute, so an escaped value round-trips to the value; an unescaped one would have
        // been truncated at the first quote.
        assertEquals(widthValue, video.attr("width"),
            "The width must be carried as a single attribute value, not broken out of it.");
        assertTrue(html.select("script").isEmpty(),
            "The width must not be able to inject a script element into the page.");
    }

    private Document renderVideo(String attachment, String widthWikiSyntax) throws Exception
    {
        XWikiDocument testPage = this.xwiki.getDocument(TEST_PAGE, this.context);
        testPage.setSyntax(Syntax.XWIKI_2_1);
        testPage.setContent(String.format("{{html5video attachment=\"%s\" width=\"%s\"/}}", attachment,
            widthWikiSyntax));
        this.xwiki.saveDocument(testPage, this.context);
        this.context.setDoc(testPage);

        return renderHTMLPage(testPage);
    }
}
