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
package org.xwiki.contrib.releasenotes;

import org.xwiki.component.annotation.Role;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.stability.Unstable;

/**
 * The release note defaults an administrator has configured for the wiki.
 *
 * @version $Id$
 * @since 2.7
 */
@Role
@Unstable
public interface ReleaseNotesConfiguration
{
    /**
     * @return the product the release notes of the wiki are about, or {@code null} when the administrator has not
     *         configured one, in which case every release note and every change has to name its product
     */
    String getDefaultProduct();

    /**
     * @return the page the content and the title of a new release note are copied from, or {@code null} when the
     *         administrator has configured no template
     */
    DocumentReference getDefaultTemplate();
}
