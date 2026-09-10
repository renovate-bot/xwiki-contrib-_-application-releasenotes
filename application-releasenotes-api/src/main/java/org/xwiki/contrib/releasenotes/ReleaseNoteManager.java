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

import java.util.List;

import org.xwiki.component.annotation.Role;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.stability.Unstable;

/**
 * Creates and reads the release notes of a wiki.
 * <p>
 * A release note lives in the page {@code ReleaseNotes.Data.<product>.<short version>.WebHome}, where the short
 * version is the version with its separators taken out, e.g. {@code 8.3M1} for {@code 8.3-milestone-1}. That page
 * name is what the release note is found by and what the changes of the milestones and the release candidates of a
 * final version are aggregated from, so it is derived from the version rather than chosen.
 *
 * @version $Id$
 * @since 2.7
 */
@Role
@Unstable
public interface ReleaseNoteManager
{
    /**
     * Creates the release note of one version of one product, from the template it names or, when it names none, from
     * the template configured for the wiki: the content, the title and the required rights of that template are
     * copied, then the values of the release note are set on top of them.
     *
     * @param note the release note to create, which needs at least a version, and a product unless one is configured
     *            for the wiki
     * @return the page the release note was created in
     * @throws ReleaseNoteAlreadyExistsException when a release note already exists for that product and version
     * @throws ReleaseNotesAccessDeniedException when the current user or the author of the calling script cannot
     *             edit the page
     * @throws ReleaseNotesException when the release note carries no version, no product could be determined, or the
     *             save failed
     */
    DocumentReference createReleaseNote(ReleaseNote note) throws ReleaseNotesException;

    /**
     * Replaces a release note: the release note of that product and version becomes the one passed, and a property
     * the passed release note leaves out is emptied rather than kept. This is how a version is marked released on
     * the day it ships.
     * <p>
     * The product and the version locate the release note rather than being replaced by this, since they are what
     * its page is named after. The content, the title and the template are not touched either: they are what a
     * release note is created with, and an administrator is free to have edited the content since.
     *
     * @param note the release note to replace, located by its version and by its product, or by the product
     *            configured for the wiki when it carries none
     * @return the release note as it is stored once replaced
     * @throws ReleaseNotesNotFoundException when there is no release note for that product and version
     * @throws ReleaseNotesAccessDeniedException when the current user or the author of the calling script cannot
     *             edit the page
     * @throws ReleaseNotesException when the release note carries no version, no product could be determined, or the
     *             save failed
     * @since 2.8
     */
    ReleaseNote updateReleaseNote(ReleaseNote note) throws ReleaseNotesException;

    /**
     * Gives the page a release note lives in, whether or not that page exists. The name of the last space of that
     * page is the short version, which is the form the version is displayed in.
     *
     * @param product the product the release note is about
     * @param version the version the release note is about, in its long form, e.g. {@code 8.3-milestone-1}
     * @return the page of that release note
     */
    DocumentReference getReleaseNoteReference(String product, String version);

    /**
     * @param reference the page of a release note
     * @return the release note that page holds
     * @throws ReleaseNotesNotFoundException when that page holds no release note
     * @throws ReleaseNotesException when that page could not be loaded
     */
    ReleaseNote getReleaseNote(DocumentReference reference) throws ReleaseNotesException;

    /**
     * Lists the release notes of the wiki, ordered by the page they live in. No right is checked: the release notes a
     * wiki holds are what the pages of the application list, and those are not filtered by right either.
     *
     * @param product the product to list the release notes of, or {@code null} to list them all
     * @return the release notes of that product
     * @throws ReleaseNotesException when the release notes could not be looked up
     */
    List<ReleaseNote> getReleaseNotes(String product) throws ReleaseNotesException;

    /**
     * Gives the versions the changes of a release note are gathered from. A milestone or a release candidate gathers
     * only its own changes, whereas a final version also gathers the changes of its milestones and of its release
     * candidates, which are returned as the {@code like} patterns that match them.
     *
     * @param noteReference the page of a release note, whose name is what the versions are derived from
     * @return the versions of the changes that release note displays
     */
    List<String> getAggregatedVersions(DocumentReference noteReference);
}
