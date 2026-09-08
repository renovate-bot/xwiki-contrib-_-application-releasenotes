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
package org.xwiki.contrib.releasenotes.test.ui.po;

import org.openqa.selenium.By;
import org.xwiki.test.ui.po.ViewPage;

/**
 * The page of a change, as {@code ReleaseNotes.Code.Change.ChangeSheet} renders it.
 *
 * @version $Id$
 */
public class ChangeViewPage extends ViewPage
{
    private static final By CONTENT = By.id("xwikicontent");

    /**
     * @param fileName the file name of the medium, as stored in the {@code screenshots} property
     * @return {@code true} when the page displays that medium as an image, {@code false} otherwise
     */
    public boolean hasScreenshot(String fileName)
    {
        return !getDriver().findElementsWithoutWaiting(
            By.cssSelector(String.format("img[src*='%s']", fileName))).isEmpty();
    }

    /**
     * Unlike {@link #getContent()}, which returns the content as text, this keeps the markup the change sheet
     * produced, which is what tells a rendered macro from one displayed inert.
     *
     * @return the rendered content of the change, as HTML
     */
    public String getContentHtml()
    {
        return getDriver().findElementWithoutWaiting(CONTENT).getAttribute("innerHTML");
    }
}
