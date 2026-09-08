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

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.releasenotes.ReleaseNotesConfiguration;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Reads the release note defaults of the wiki from the configuration page of the application, which is where the
 * administration section of the application writes them.
 *
 * @version $Id$
 * @since 2.7
 */
@Component
@Singleton
public class DefaultReleaseNotesConfiguration implements ReleaseNotesConfiguration
{
    private static final String PRODUCT = "product";

    private static final String TEMPLATE = "template";

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    /**
     * The configured template is a page reference the administrator has typed, and is resolved the way the pages of
     * the application resolve it.
     */
    @Inject
    @Named("currentmixed")
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @Inject
    private Logger logger;

    @Override
    public String getDefaultProduct()
    {
        return StringUtils.trimToNull(getConfigurationValue(PRODUCT));
    }

    @Override
    public DocumentReference getDefaultTemplate()
    {
        String template = StringUtils.trimToNull(getConfigurationValue(TEMPLATE));

        return template == null ? null : this.documentReferenceResolver.resolve(template);
    }

    private String getConfigurationValue(String property)
    {
        XWikiContext xcontext = this.xcontextProvider.get();

        try {
            XWikiDocument document =
                xcontext.getWiki().getDocument(ReleaseNotesReferences.CONFIGURATION, xcontext);
            BaseObject object = document.getXObject(ReleaseNotesReferences.CONFIGURATION_CLASS);

            return object == null ? null : object.getStringValue(property);
        } catch (XWikiException e) {
            // A wiki whose configuration cannot be read has no defaults, which the callers report on their own: they
            // are the ones that know whether the value they were after was needed.
            this.logger.warn("Failed to read the release note configuration of the current wiki. Root cause: [{}]",
                ExceptionUtils.getRootCauseMessage(e));

            return null;
        }
    }
}
