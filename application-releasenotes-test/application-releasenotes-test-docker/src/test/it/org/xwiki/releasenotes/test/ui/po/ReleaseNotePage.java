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
package org.xwiki.releasenotes.test.ui.po;

import org.openqa.selenium.By;
import org.xwiki.test.ui.po.InlinePage;
import org.xwiki.test.ui.po.ViewPage;

/**
 * A release note, as the {@code releasenotechanges} and {@code releasenotecontributors} macros render it.
 *
 * @version $Id$
 */
public class ReleaseNotePage extends ViewPage
{
    private static final By CONTENT = By.id("xwikicontent");

    /**
     * The button submitting the form that creates the contributors list. The form is located through the entry type
     * it carries rather than through the label of its button, which is translated.
     */
    private static final By ADD_CONTRIBUTORS_BUTTON =
        By.xpath("//input[@name = 'type'][@value = 'Contributors']/ancestor::form//input[@type = 'submit']");

    /**
     * Creates the contributors list of the note, which the contributors macro offers while no such list exists.
     *
     * @return the inline edit form the button lands on, editing the contributors entry from its template
     */
    public InlinePage clickAddContributors()
    {
        getDriver().findElementWithoutWaiting(ADD_CONTRIBUTORS_BUTTON).click();
        return new InlinePage();
    }

    /**
     * Unlike {@link #getContent()}, which returns the content as text, this keeps the markup the macros produced,
     * which is what tells a rendered macro from one displayed inert.
     *
     * @return the rendered content of the note, as HTML
     */
    public String getContentHtml()
    {
        return getDriver().findElementWithoutWaiting(CONTENT).getAttribute("innerHTML");
    }
}
