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
package org.xwiki.releasenotes.test.ui;

import java.util.List;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rest.model.jaxb.Page;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.ViewPage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI tests for the Release Notes Application.
 *
 * @version $Id$
 */
@UITest
class ReleaseNotesIT
{
    /**
     * Builds a release note page (with the contributors macro) plus, optionally, its Contributors child entry, then
     * asserts the macro shows a warning when the list is absent and renders the names when it is present.
     */
    @Test
    @Order(1)
    void contributorsList(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();

        DocumentReference releaseNote =
            new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", "TestProduct", "1.0"), "WebHome");
        setup.rest().delete(releaseNote);
        setup.createPage(releaseNote, "= Credits =\n\n{{releasenotecontributors/}}", "RN 1.0");
        setup.addObject(releaseNote, "ReleaseNotes.Code.ReleaseNoteClass", "product", "TestProduct", "version", "1.0");

        // Before any contributors list exists, the macro shows the warning.
        ViewPage beforePage = setup.gotoPage(releaseNote);
        assertTrue(beforePage.getContent().contains("The list of contributors has not been generated yet."),
            "Expected the not-generated-yet warning before the contributors list exists.");

        // Create the deterministic Contributors child entry with two names.
        DocumentReference contributorsEntry = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", "TestProduct", "1.0", "Contributors"), "WebHome");
        setup.rest().delete(contributorsEntry);
        setup.createPage(contributorsEntry, "", "Contributors");
        setup.addObject(contributorsEntry, "ReleaseNotes.Code.EntryClass",
            "product", "TestProduct", "type", "Contributors", "version", "1.0");
        // Store the names unsorted and with mixed case to exercise the case-insensitive alphabetical ordering.
        setup.addObject(contributorsEntry, "ReleaseNotes.Code.ContributorsClass",
            "contributors", "bob jones\nAlice Smith\nCarol Nguyen");

        // Now the macro renders the names, sorted alphabetically ignoring case, and drops the warning.
        ViewPage afterPage = setup.gotoPage(releaseNote);
        String content = afterPage.getContent();
        assertTrue(content.contains("Alice Smith"), "Expected the first contributor to be rendered.");
        assertTrue(content.contains("bob jones"), "Expected the second contributor to be rendered.");
        assertTrue(content.contains("Carol Nguyen"), "Expected the third contributor to be rendered.");
        assertTrue(content.indexOf("Alice Smith") < content.indexOf("bob jones")
            && content.indexOf("bob jones") < content.indexOf("Carol Nguyen"),
            "Contributors must be sorted alphabetically ignoring case, got: " + content);
        assertFalse(content.contains("has not been generated yet"),
            "Warning must disappear once the contributors list exists.");
    }

    /**
     * Exercises the aligned edit UX: the macro shows an "Add contributors" button when the list is absent, the button
     * opens the separate Contributors page in inline edit mode, and saving there renders the names back on the release
     * note and drops the warning.
     */
    @Test
    @Order(2)
    void contributorsListEditFlow(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();

        DocumentReference releaseNote =
            new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", "EditProduct", "1.0"), "WebHome");
        setup.rest().delete(releaseNote);
        DocumentReference contributorsEntry = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", "EditProduct", "1.0", "Contributors"), "WebHome");
        setup.rest().delete(contributorsEntry);

        setup.createPage(releaseNote, "= Credits =\n\n{{releasenotecontributors/}}", "RN Edit 1.0");
        setup.addObject(releaseNote, "ReleaseNotes.Code.ReleaseNoteClass",
            "product", "EditProduct", "version", "1.0");

        // Absent: warning is shown and the "Add contributors" button is available to an editor.
        ViewPage beforePage = setup.gotoPage(releaseNote);
        assertTrue(beforePage.getContent().contains("The list of contributors has not been generated yet."),
            "Expected the not-generated-yet warning before the contributors list exists.");
        WebElement addButton = setup.getDriver().findElementWithoutWaiting(
            By.cssSelector("input.button[value='Add contributors']"));

        // Click "Add contributors": lands on the Contributors page in inline edit mode.
        addButton.click();
        WebElement textarea = setup.getDriver().findElement(By.cssSelector("textarea"));
        textarea.clear();
        textarea.sendKeys("Alice Smith\nBob Jones");
        setup.getDriver().findElement(By.cssSelector("input[name='action_save']")).click();

        // Back on the release note: both names render and the warning is gone.
        ViewPage afterPage = setup.gotoPage(releaseNote);
        String content = afterPage.getContent();
        assertTrue(content.contains("Alice Smith"), "Expected the first contributor to be rendered.");
        assertTrue(content.contains("Bob Jones"), "Expected the second contributor to be rendered.");
        assertFalse(content.contains("The list of contributors has not been generated yet."),
            "Warning must disappear once the contributors list has been saved.");

        // The Contributors page created from the macro is a technical child page: it must be hidden.
        Page savedEntry = setup.rest().get(contributorsEntry);
        assertTrue(savedEntry.isHidden(), "The Contributors child page must be created as a hidden page.");
    }

    /**
     * A contributor name containing wiki syntax must be rendered literally, not interpreted (injection guard for the
     * $services.rendering.escape call in the macro).
     */
    @Test
    @Order(3)
    void contributorsListEscapesWikiSyntax(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();

        DocumentReference releaseNote =
            new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", "EscProduct", "1.0"), "WebHome");
        setup.rest().delete(releaseNote);
        setup.createPage(releaseNote, "{{releasenotecontributors/}}", "RN Esc 1.0");
        setup.addObject(releaseNote, "ReleaseNotes.Code.ReleaseNoteClass", "product", "EscProduct", "version", "1.0");

        DocumentReference contributorsEntry = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", "EscProduct", "1.0", "Contributors"), "WebHome");
        setup.rest().delete(contributorsEntry);
        setup.createPage(contributorsEntry, "", "Contributors");
        setup.addObject(contributorsEntry, "ReleaseNotes.Code.EntryClass",
            "product", "EscProduct", "type", "Contributors", "version", "1.0");
        // A name carrying bold wiki syntax: if escaped, the asterisks survive in the rendered text; if interpreted,
        // the text would render as bold and the asterisks would be gone.
        setup.addObject(contributorsEntry, "ReleaseNotes.Code.ContributorsClass",
            "contributors", "**Robert Tables**");

        String content = setup.gotoPage(releaseNote).getContent();
        assertTrue(content.contains("**Robert Tables**"),
            "Wiki syntax in a contributor name must be escaped and rendered literally, got: " + content);
    }

    /**
     * Adding the first change to a version that already has a Contributors entry must still number the new change
     * Entry001 (regression guard: the Contributors entry must not be counted by the change-numbering query).
     */
    @Test
    @Order(4)
    void changeNumberingIgnoresContributors(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();

        DocumentReference releaseNote =
            new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", "NumProduct", "1.0"), "WebHome");
        setup.rest().delete(releaseNote);
        setup.createPage(releaseNote, "{{releasenotechanges/}}", "RN Num 1.0");
        setup.addObject(releaseNote, "ReleaseNotes.Code.ReleaseNoteClass",
            "product", "NumProduct", "version", "1.0", "released", "0");

        // A Contributors entry exists in the version space, but no change entry does yet.
        DocumentReference contributorsEntry = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", "NumProduct", "1.0", "Contributors"), "WebHome");
        setup.rest().delete(contributorsEntry);
        setup.createPage(contributorsEntry, "", "Contributors");
        setup.addObject(contributorsEntry, "ReleaseNotes.Code.EntryClass",
            "product", "NumProduct", "type", "Contributors", "version", "1.0");

        // Trigger "Add User Change": handleAddAction computes the next Entry name and redirects to its inline editor.
        setup.gotoPage(releaseNote, "view",
            "action=useradd&template=ReleaseNotes.Code.Change.ChangeTemplate&product=NumProduct&version=1.0"
                + "&audience=user");
        String currentUrl = setup.getDriver().getCurrentUrl();
        assertTrue(currentUrl.contains("Entry001"),
            "The new change must be numbered Entry001 despite the Contributors entry, landed on: " + currentUrl);
    }

    /**
     * The {@code >=} and {@code <=} version filters of the getChanges macro must include the boundary version itself,
     * unlike their strict {@code >} and {@code <} counterparts.
     */
    @Test
    @Order(5)
    void getChangesComparisonFiltersIncludeTheBoundaryVersion(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();

        createChange(setup, "1.0", "Change One");
        createChange(setup, "2.0", "Change Two");
        createChange(setup, "3.0", "Change Three");

        // ">=2.0" must return 2.0 and 3.0, i.e. the boundary version is part of the result.
        String greaterOrEqual = renderFilter(setup, "GreaterOrEqual", ">=2.0");
        assertFalse(greaterOrEqual.contains("Change One"), "\">=2.0\" must exclude 1.0, got: " + greaterOrEqual);
        assertTrue(greaterOrEqual.contains("Change Two"), "\">=2.0\" must include 2.0, got: " + greaterOrEqual);
        assertTrue(greaterOrEqual.contains("Change Three"), "\">=2.0\" must include 3.0, got: " + greaterOrEqual);

        // "<=2.0" must return 1.0 and 2.0, i.e. the boundary version is part of the result.
        String lowerOrEqual = renderFilter(setup, "LowerOrEqual", "<=2.0");
        assertTrue(lowerOrEqual.contains("Change One"), "\"<=2.0\" must include 1.0, got: " + lowerOrEqual);
        assertTrue(lowerOrEqual.contains("Change Two"), "\"<=2.0\" must include 2.0, got: " + lowerOrEqual);
        assertFalse(lowerOrEqual.contains("Change Three"), "\"<=2.0\" must exclude 3.0, got: " + lowerOrEqual);

        // The strict operators must still exclude the boundary version.
        String greater = renderFilter(setup, "Greater", ">2.0");
        assertFalse(greater.contains("Change Two"), "\">2.0\" must exclude 2.0, got: " + greater);
        assertTrue(greater.contains("Change Three"), "\">2.0\" must include 3.0, got: " + greater);

        String lower = renderFilter(setup, "Lower", "<2.0");
        assertTrue(lower.contains("Change One"), "\"<2.0\" must include 1.0, got: " + lower);
        assertFalse(lower.contains("Change Two"), "\"<2.0\" must exclude 2.0, got: " + lower);
    }

    /**
     * Creates a single user change of the {@code CmpProduct} product for the passed version.
     */
    private void createChange(TestUtils setup, String version, String title) throws Exception
    {
        DocumentReference entry = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", "CmpProduct", version, "Entry001"), "WebHome");
        setup.rest().delete(entry);
        setup.createPage(entry, "", title);
        setup.addObject(entry, "ReleaseNotes.Code.EntryClass",
            "product", "CmpProduct", "type", "Change", "version", version);
        setup.addObject(entry, "ReleaseNotes.Code.Change.ChangeClass",
            "title", title, "summary", title + " summary", "audience", "user", "importance", "1",
            "category", "development");
    }

    /**
     * Renders the {@code CmpProduct} changes matching the passed {@code versions} filter and returns the page content.
     */
    private String renderFilter(TestUtils setup, String pageName, String versions) throws Exception
    {
        DocumentReference reportPage =
            new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", "CmpProduct", pageName), "WebHome");
        setup.rest().delete(reportPage);
        setup.createPage(reportPage,
            String.format("{{getChanges products=\"CmpProduct\" versions=\"%s\" contextVariable=\"changeDocs\"/}}"
                + "\n\n{{displayChanges contextVariable=\"changeDocs\" displayer=\"simple\"/}}", versions),
            pageName);
        return setup.gotoPage(reportPage).getContent();
    }

    /**
     * Checks that the configuration is reachable from the wiki Administration (thanks to the ConfigurableClass
     * xobject) and that the application home page offers a shortcut to that administration section.
     */
    @Test
    @Order(6)
    void configureFromAdministration(TestUtils setup)
    {
        setup.loginAsSuperAdmin();

        // The section displays the fields of the configuration xobject, bound to the configuration page.
        setup.gotoPage("XWiki", "XWikiPreferences", "admin", "section=releasenotes");
        WebElement product =
            setup.getDriver().findElement(By.name("ReleaseNotes.Code.ReleaseNotesConfigClass_0_product"));
        assertEquals("XWiki", product.getAttribute("value"), "Expected the default product name to be displayed.");
        WebElement template =
            setup.getDriver().findElement(By.name("ReleaseNotes.Code.ReleaseNotesConfigClass_0_template"));
        assertEquals("ReleaseNotes.Code.ReleaseNoteTemplate", template.getAttribute("value"),
            "Expected the default template reference to be displayed.");

        setup.gotoPage("ReleaseNotes", "WebHome");
        WebElement configureLink =
            setup.getDriver().findElement(By.linkText("Configure the Release Notes Application"));
        assertTrue(configureLink.getAttribute("href").contains("section=releasenotes"),
            "The home page link must point to the administration section, got: "
                + configureLink.getAttribute("href"));
    }
}
