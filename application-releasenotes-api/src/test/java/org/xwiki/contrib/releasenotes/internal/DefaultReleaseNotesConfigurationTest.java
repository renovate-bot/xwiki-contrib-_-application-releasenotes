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
package org.xwiki.contrib.releasenotes.internal;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.junit5.mockito.InjectMockComponents;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;
import com.xpn.xwiki.test.reference.ReferenceComponentList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link DefaultReleaseNotesConfiguration}.
 *
 * @version $Id$
 */
@OldcoreTest
@ReferenceComponentList
class DefaultReleaseNotesConfigurationTest
{
    @InjectMockComponents
    private DefaultReleaseNotesConfiguration configuration;

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    @BeforeEach
    void setUp() throws Exception
    {
        ReleaseNotesXClasses.install(this.oldcore);
    }

    /**
     * A wiki whose administrator has configured nothing has no defaults, which every release note then has to name
     * for itself.
     */
    @Test
    void aWikiWithoutAConfigurationPageHasNoDefaults()
    {
        assertNull(this.configuration.getDefaultProduct());
        assertNull(this.configuration.getDefaultTemplate());
    }

    @Test
    void anEmptyConfigurationValueIsNoValue() throws Exception
    {
        configure("  ", "");

        assertNull(this.configuration.getDefaultProduct());
        assertNull(this.configuration.getDefaultTemplate());
    }

    @Test
    void theConfiguredValuesAreRead() throws Exception
    {
        configure("XWiki", "ReleaseNotes.Code.ReleaseNoteTemplate");

        assertEquals("XWiki", this.configuration.getDefaultProduct());
        assertEquals(new DocumentReference("xwiki", List.of("ReleaseNotes", "Code"), "ReleaseNoteTemplate"),
            this.configuration.getDefaultTemplate());
    }

    private void configure(String product, String template) throws Exception
    {
        DocumentReference reference =
            ReleaseNotesXClasses.reference(this.oldcore, ReleaseNotesReferences.CONFIGURATION);
        XWikiDocument document =
            this.oldcore.getSpyXWiki().getDocument(reference, this.oldcore.getXWikiContext());
        BaseObject object = document.newXObject(ReleaseNotesReferences.CONFIGURATION_CLASS,
            this.oldcore.getXWikiContext());
        object.setStringValue("product", product);
        object.setStringValue("template", template);
        this.oldcore.getSpyXWiki().saveDocument(document, this.oldcore.getXWikiContext());
    }
}
