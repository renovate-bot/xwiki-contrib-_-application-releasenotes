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
import jakarta.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.releasenotes.ReleaseNotesConfiguration;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;

/**
 * Gives the product a release note or a change is about, which its author may leave out to use the product
 * configured for the wiki.
 *
 * @version $Id$
 * @since 2.7
 */
@Component(roles = ProductResolver.class)
@Singleton
public class ProductResolver
{
    @Inject
    private ReleaseNotesConfiguration configuration;

    /**
     * @param product the product that was asked for, which may be empty
     * @return that product, or the product configured for the wiki when none was asked for
     * @throws ReleaseNotesException when no product was asked for and the wiki has none configured, since the page a
     *             release note lives in is named after its product and cannot be left out
     */
    public String resolve(String product) throws ReleaseNotesException
    {
        String resolved = StringUtils.trimToNull(product);

        if (resolved == null) {
            resolved = StringUtils.trimToNull(this.configuration.getDefaultProduct());
        }

        if (resolved == null) {
            throw new ReleaseNotesException("No product was given, and this wiki has no default product configured.");
        }

        return resolved;
    }
}
