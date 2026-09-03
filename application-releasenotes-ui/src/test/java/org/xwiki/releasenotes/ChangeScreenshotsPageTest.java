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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.script.ModelScriptService;
import org.xwiki.rendering.internal.macro.gallery.GalleryMacro;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.wikimacro.internal.WikiMacroFactoryComponentClass;
import org.xwiki.script.service.ScriptService;
import org.xwiki.skinx.SkinExtension;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.WikiMacroSetup;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Page test for the {@code screenshots} property of {@code ReleaseNotes.Code.Change.ChangeClass}: the custom display
 * that stores it, and {@code #displayScreenshots} in {@code ReleaseNotes.Code.Change.ChangeDisplayerVelocityMacros},
 * which reads it back and turns it into the media of a change.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
@WikiMacroFactoryComponentClass
// The media block puts its screenshots in a gallery and its videos in the html5video macro of the application.
@ComponentList({
    GalleryMacro.class,
    ModelScriptService.class
})
class ChangeScreenshotsPageTest extends PageTest
{
    private static final List<String> CHANGE_SPACE = List.of("ReleaseNotes", "Code", "Change");

    private static final DocumentReference CHANGE_CLASS =
        new DocumentReference("xwiki", CHANGE_SPACE, "ChangeClass");

    private static final DocumentReference CHANGE =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", "XWiki", "8.3M1", "Entry001"), "WebHome");

    private static final DocumentReference HTML5_VIDEO_MACRO =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Code"), "HTML5Video");

    /**
     * The change whose media are displayed. It sits in a space with no dot in its name so that the macro tests can
     * name it in wiki syntax as it stands.
     */
    private static final DocumentReference MEDIA_CHANGE =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Data"), "TestChange");

    private static final DocumentReference TEST_PAGE =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Data"), "TestPage");

    private static final String FIELD_NAME = "ReleaseNotes.Code.Change.ChangeClass_0_screenshots";

    /**
     * A media reference as it is really stored: an absolute attachment reference, which the macro recognises by its
     * at-sign and passes on as it stands.
     */
    private static final String SCREENSHOT_REFERENCE = "ReleaseNotes.Data.TestChange@a.png";

    private static final String VIDEO_REFERENCE = "ReleaseNotes.Data.TestChange@a.mp4";

    @BeforeEach
    void setUp() throws Exception
    {
        // The gallery macro pulls the skin extensions of its slide-show.
        this.componentManager.registerMockComponent(SkinExtension.class, "jsfx");
        this.componentManager.registerMockComponent(SkinExtension.class, "ssfx");
        // The html5video macro emits its player from a clean=false HTML block, which its (privileged) author may do.
        when(this.oldcore.getMockRightService().hasProgrammingRights(any())).thenReturn(true);

        loadPage(CHANGE_CLASS);
    }

    /**
     * The picker is a multiple SELECT, but the property is a String, for which XWiki only keeps the first submitted
     * value. The value that gets saved must therefore come from a hidden input holding the whole comma separated list,
     * and the picker itself must be named apart so that it cannot be mistaken for the property.
     */
    @Test
    void editDisplaysAnAttachmentPickerBackedByAHiddenInput() throws Exception
    {
        Document html = Jsoup.parseBodyFragment(displayScreenshots("a.png, b.png", "edit"));

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

    /**
     * A stored reference is emitted into the reference part of an image link, so it must be emitted escaped: left
     * raw, a reference holding the sequence that closes a link would break out of it and have the rest of its value
     * parsed as content of the page, macro calls included.
     *
     * @param useGallery whether the media block is asked for a gallery, which decides which of the two image links
     *     of the macro is emitted
     */
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void screenshotReferenceIsEscapedIntoTheImageLink(boolean useGallery) throws Exception
    {
        this.componentManager.registerComponent(ScriptService.class, "rendering",
            new RenderingScriptServiceStub(RenderingScriptServiceStub.xwikiSyntaxEscaper()));
        String reference = SCREENSHOT_REFERENCE + "]] {{html}}<b>escaped</b>{{/html}} [[image:other.png";

        Document html = renderMedia(reference, useGallery);

        Elements images = html.select("img");
        assertEquals(1, images.size(),
            "The reference must stay a single image link: " + html.body().html());
        assertEquals(reference, images.get(0).attr("src"),
            "The whole reference must stay inside the image link.");
        assertTrue(html.select("b").isEmpty(),
            "A macro in the reference must not end up rendered: " + html.body().html());
    }

    /**
     * The escaping must leave a reference as it is really stored alone, so that the image still points at the
     * attachment the author picked.
     *
     * @param useGallery whether the media block is asked for a gallery
     */
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void plainScreenshotReferenceStillReachesTheImage(boolean useGallery) throws Exception
    {
        this.componentManager.registerComponent(ScriptService.class, "rendering",
            new RenderingScriptServiceStub(RenderingScriptServiceStub.xwikiSyntaxEscaper()));

        Document html = renderMedia(SCREENSHOT_REFERENCE, useGallery);

        assertEquals(List.of(SCREENSHOT_REFERENCE), html.select("img").eachAttr("src"));
    }

    /**
     * The video half of the same story: the reference is emitted as the value of a macro parameter, so a reference
     * holding the quote that closes that value must be emitted escaped.
     */
    @Test
    void videoReferenceIsEscapedIntoThePlayerParameter() throws Exception
    {
        this.componentManager.registerComponent(ScriptService.class, "rendering",
            new RenderingScriptServiceStub(RenderingScriptServiceStub.xwikiSyntaxEscaper()));
        WikiMacroSetup.loadWikiMacro(this, this.componentManager, HTML5_VIDEO_MACRO);
        // The reference still ends on a video extension, which is what the macro sorts the media by.
        String reference =
            SCREENSHOT_REFERENCE + "\" /}}{{html}}<b>escaped</b>{{/html}}{{html5video attachment=\"other.mp4";

        Document html = renderMedia(reference, true);

        assertEquals(1, html.select("video").size(),
            "The reference must stay a single macro parameter value: " + html.body().html());
        assertTrue(html.select("b").isEmpty(),
            "A macro in the reference must not end up rendered: " + html.body().html());
    }

    /**
     * The counterpart of {@link #plainScreenshotReferenceStillReachesTheImage} for the video path.
     */
    @Test
    void plainVideoReferenceStillReachesThePlayer() throws Exception
    {
        this.componentManager.registerComponent(ScriptService.class, "rendering",
            new RenderingScriptServiceStub(RenderingScriptServiceStub.xwikiSyntaxEscaper()));
        WikiMacroSetup.loadWikiMacro(this, this.componentManager, HTML5_VIDEO_MACRO);

        Document html = renderMedia(VIDEO_REFERENCE, true);

        Elements players = html.select("video");
        assertEquals(1, players.size(), "Expected the video to be played: " + html.body().html());
        Element player = players.get(0);
        assertTrue(player.attr("src").contains("a.mp4"),
            "The player must point at the attachment the reference names, got: " + player.attr("src"));
    }

    /**
     * Renders a page asking {@code #displayScreenshots} for the media block of a change holding the passed
     * {@code screenshots} value.
     *
     * @param screenshots the stored property value
     * @param useGallery the {@code $useGallery} argument of the macro
     */
    private Document renderMedia(String screenshots, boolean useGallery) throws Exception
    {
        XWikiDocument change = new XWikiDocument(MEDIA_CHANGE);
        change.setSyntax(Syntax.XWIKI_2_1);
        BaseObject changeObject = change.newXObject(CHANGE_CLASS, this.context);
        changeObject.setStringValue("screenshots", screenshots);
        this.xwiki.saveDocument(change, this.context);

        loadPage(new DocumentReference("xwiki", CHANGE_SPACE, "ChangeDisplayerVelocityMacros"));

        XWikiDocument page = new XWikiDocument(TEST_PAGE);
        page.setSyntax(Syntax.XWIKI_2_1);
        // The macros are included and then called on the change object, the way every displayer calls them.
        page.setContent(String.format(
            "{{include reference=\"ReleaseNotes.Code.Change.ChangeDisplayerVelocityMacros\"/}}%n%n"
                + "{{velocity}}%n"
                + "#set ($changeDoc = $xwiki.getDocument('ReleaseNotes.Data.TestChange'))%n"
                + "#set ($changeObject = $changeDoc.getObject('ReleaseNotes.Code.Change.ChangeClass'))%n"
                + "#displayScreenshots($changeObject, %s, false)%n"
                + "{{/velocity}}", useGallery));
        this.xwiki.saveDocument(page, this.context);
        this.context.setDoc(page);

        return Jsoup.parseBodyFragment(page.getRenderedContent(this.context));
    }

    private String displayScreenshots(String screenshots, String type) throws Exception
    {
        XWikiDocument change = new XWikiDocument(CHANGE);
        BaseObject changeObject = change.newXObject(CHANGE_CLASS, this.context);
        changeObject.setStringValue("screenshots", screenshots);
        this.xwiki.saveDocument(change, this.context);
        this.context.setDoc(change);

        return change.display("screenshots", type, changeObject, this.context);
    }
}
