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
package org.xwiki.contrib.releasenotes.internal.converter;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.xwiki.component.internal.ContextComponentManagerProvider;
import org.xwiki.contrib.releasenotes.Audience;
import org.xwiki.contrib.releasenotes.Change;
import org.xwiki.contrib.releasenotes.Importance;
import org.xwiki.properties.ConverterManager;
import org.xwiki.properties.converter.ConversionException;
import org.xwiki.properties.internal.DefaultBeanManager;
import org.xwiki.properties.internal.DefaultConverterManager;
import org.xwiki.properties.internal.converter.ConvertUtilsConverter;
import org.xwiki.properties.internal.converter.EnumConverter;
import org.xwiki.properties.internal.converter.ListConverter;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.mockito.MockitoComponentManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link ChangeConverter}.
 * <p>
 * The conversion is asked of the converter manager rather than of the converter itself, since that is how a script
 * reaches it: the method arguments uberspector converts a map literal written in a script into the bean the API
 * takes, and nothing else has to be written for a change to be created from a script.
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList({ ChangeConverter.class, ContextComponentManagerProvider.class, DefaultConverterManager.class,
    DefaultBeanManager.class, EnumConverter.class, ListConverter.class, ConvertUtilsConverter.class })
class ChangeConverterTest
{
    @InjectComponentManager
    private MockitoComponentManager componentManager;

    @Test
    void aChangeIsWrittenAsAMapOfItsProperties() throws Exception
    {
        Change change = convert(Map.of(
            "product", "XWiki",
            "version", "8.3-milestone-1",
            "title", "Faster startup",
            "summary", "Starting a wiki now takes half the time.",
            "description", "The long story.",
            "audience", "developer",
            "importance", "high",
            "category", "Performance",
            "screenshots", List.of("before.png", "after.png")));

        assertEquals("XWiki", change.getProduct());
        assertEquals("8.3-milestone-1", change.getVersion());
        assertEquals("Faster startup", change.getTitle());
        assertEquals("Starting a wiki now takes half the time.", change.getSummary());
        assertEquals("The long story.", change.getDescription());
        assertEquals(Audience.DEVELOPER, change.getAudience());
        assertEquals(Importance.HIGH, change.getImportance());
        assertEquals("Performance", change.getCategory());
        assertEquals(List.of("before.png", "after.png"), change.getScreenshots());
    }

    /**
     * The audience and the importance are written the way an author says them, and not the way they are stored: the
     * importance is stored as a number so that the database can order the changes by it.
     */
    @Test
    void theAudienceAndTheImportanceAreReadWhateverTheirCase() throws Exception
    {
        Change change = convert(Map.of("version", "8.3", "title", "A change", "audience", "ADMINISTRATOR",
            "importance", "Low"));

        assertEquals(Audience.ADMINISTRATOR, change.getAudience());
        assertEquals(Importance.LOW, change.getImportance());
    }

    @Test
    void thePropertiesLeftOutAreLeftUnset() throws Exception
    {
        Change change = convert(Map.of("version", "8.3", "title", "A change"));

        assertNull(change.getProduct());
        assertNull(change.getAudience());
        assertNull(change.getImportance());
        assertNull(change.getScreenshots());
    }

    /**
     * A version is what decides the release note a change belongs to, and a title is the only part of a change that
     * every displayer shows, so both are named as mandatory and their absence is reported.
     */
    @Test
    void theMandatoryPropertiesAreReported()
    {
        Map<String, String> withoutAVersion = Map.of("title", "A change");

        ConversionException exception = assertThrows(ConversionException.class, () -> convert(withoutAVersion));

        assertEquals("Failed to read a [Change] from [{title=A change}].", exception.getMessage());
        assertEquals("Property [version] mandatory", exception.getCause().getMessage());
    }

    @Test
    void aChangeIsNotWrittenAsAnythingButAMap()
    {
        ConversionException exception = assertThrows(ConversionException.class, () -> convert("a change"));

        assertEquals("A [Change] is written as a map of its properties, and got a value of type "
            + "[java.lang.String].", exception.getMessage());
    }

    @Test
    void nothingConvertsToNoChange() throws Exception
    {
        assertNull(convert(null));
    }

    private Change convert(Object value) throws Exception
    {
        return this.componentManager.<ConverterManager>getInstance(ConverterManager.class)
            .convert(Change.class, value);
    }
}
