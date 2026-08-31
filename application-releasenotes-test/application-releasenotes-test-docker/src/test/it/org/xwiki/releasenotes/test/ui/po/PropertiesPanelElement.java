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

import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.xwiki.livedata.test.po.AbstractLiveDataAdvancedPanelElement;
import org.xwiki.livedata.test.po.LiveDataElement;

/**
 * Represents the advanced panel listing the properties of a Live Data, from which a property is displayed as a column
 * or hidden. The panel offers exactly the properties the Live Data declares, each one ticked when it is displayed.
 * <p>
 * This page object belongs to {@code xwiki-platform-livedata-test-pageobjects}, beside the panels that already have
 * one ({@code FiltersPanelElement}, {@code SortPanelElement}); it lives here only until the platform version these
 * tests build against carries it.
 *
 * @version $Id$
 */
public class PropertiesPanelElement extends AbstractLiveDataAdvancedPanelElement
{
    /**
     * Opens the panel of the given Live Data from its dropdown menu.
     *
     * @param liveData the Live Data to open the panel of
     * @return the opened panel
     */
    public static PropertiesPanelElement open(LiveDataElement liveData)
    {
        WebElement root = getUtil().getDriver().findElement(By.id(liveData.getId()));
        root.findElement(By.cssSelector(".livedata-dropdown-menu")).click();
        root.findElement(By.linkText("Properties...")).click();
        return new PropertiesPanelElement(liveData,
            root.findElement(By.className("livedata-advanced-panel-properties")));
    }

    /**
     * Default constructor.
     *
     * @param liveData the Live Data of the panel
     * @param container the container of the panel
     */
    public PropertiesPanelElement(LiveDataElement liveData, WebElement container)
    {
        super(liveData, container);
    }

    /**
     * @return the label of every property the panel offers, in the order they are offered
     */
    public List<String> getPropertyNames()
    {
        return getProperties().stream()
            .map(property -> property.findElement(By.className("property-name")).getText())
            .toList();
    }

    /**
     * @param propertyName the label of a property
     * @return {@code true} if the panel offers that property, {@code false} otherwise
     */
    public boolean hasProperty(String propertyName)
    {
        return getPropertyNames().contains(propertyName);
    }

    /**
     * @param propertyName the label of a property the panel offers
     * @return {@code true} if that property is displayed as a column, {@code false} if it is hidden
     */
    public boolean isPropertyDisplayed(String propertyName)
    {
        return getCheckbox(propertyName).isSelected();
    }

    /**
     * Displays or hides the column of a property, the way ticking or unticking its checkbox does.
     *
     * @param propertyName the label of a property the panel offers
     * @param displayed whether the property must be displayed as a column
     */
    public void setPropertyDisplayed(String propertyName, boolean displayed)
    {
        if (isPropertyDisplayed(propertyName) != displayed) {
            getCheckbox(propertyName).click();
        }
    }

    private WebElement getCheckbox(String propertyName)
    {
        return getProperty(propertyName).findElement(By.cssSelector("input[type='checkbox']"));
    }

    private WebElement getProperty(String propertyName)
    {
        return getProperties().stream()
            .filter(property -> property.findElement(By.className("property-name")).getText().equals(propertyName))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                String.format("The properties panel does not offer any property named [%s]. It offers %s.",
                    propertyName, getPropertyNames())));
    }

    private List<WebElement> getProperties()
    {
        return this.container.findElements(By.className("property"));
    }
}
