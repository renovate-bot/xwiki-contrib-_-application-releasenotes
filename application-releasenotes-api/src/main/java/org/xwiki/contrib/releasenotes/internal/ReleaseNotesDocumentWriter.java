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
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import org.xwiki.bridge.DocumentAccessBridge;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.releasenotes.ReleaseNotesAccessDeniedException;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.model.document.DocumentAuthors;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.user.CurrentUserReference;
import org.xwiki.user.UserReference;
import org.xwiki.user.UserReferenceResolver;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Checks that a page of the application may be written, and writes it.
 * <p>
 * The check lives here, and not in the entry points of the application, because {@code XWiki#saveDocument} checks no
 * right at all: a component reachable through a script service would otherwise be an unauthenticated way of writing
 * to the wiki. This is the one place a REST, a script and a Java caller all go through.
 *
 * @version $Id$
 * @since 2.7
 */
@Component(roles = ReleaseNotesDocumentWriter.class)
@Singleton
public class ReleaseNotesDocumentWriter
{
    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private ContextualAuthorizationManager contextualAuthorization;

    @Inject
    private AuthorizationManager authorization;

    @Inject
    private DocumentAccessBridge documentAccessBridge;

    @Inject
    private UserReferenceResolver<CurrentUserReference> currentUserResolver;

    /**
     * Checks that the passed page may be written, which the edit right is what it takes: that is the right the pages
     * of the application ask for before showing their creation forms, and this API must be neither more nor less
     * than they are.
     * <p>
     * The right is checked for the current user <em>and</em> for the author of the script that is calling, because a
     * script service is reachable with the script right alone: a check done for the user only would let a script
     * write on behalf of whoever happens to be reading the page it is on.
     *
     * @param reference the page about to be written
     * @throws ReleaseNotesAccessDeniedException when either of them may not edit that page
     */
    public void checkEditRight(DocumentReference reference) throws ReleaseNotesAccessDeniedException
    {
        if (!this.contextualAuthorization.hasAccess(Right.EDIT, reference)) {
            throw new ReleaseNotesAccessDeniedException(
                String.format("The current user is not allowed to edit the page [%s].", reference), reference);
        }

        DocumentReference author = this.documentAccessBridge.getCurrentAuthorReference();

        if (!this.authorization.hasAccess(Right.EDIT, author, reference)) {
            throw new ReleaseNotesAccessDeniedException(String
                .format("The author [%s] of the calling script is not allowed to edit the page [%s].", author,
                    reference), reference);
        }
    }

    /**
     * Saves the passed page as the current user, once {@link #checkEditRight(DocumentReference)} has allowed it.
     * <p>
     * The content author is set along with the author because the content of a release note is copied from a
     * template that scripts, and the content author is who that script runs as.
     *
     * @param document the page to save
     * @param comment the comment to record the new version of that page with
     * @throws ReleaseNotesException when the page may not be written, a listener refused the save, or the save
     *             failed
     */
    public void save(XWikiDocument document, String comment) throws ReleaseNotesException
    {
        DocumentReference reference = document.getDocumentReference();

        checkEditRight(reference);

        UserReference user = this.currentUserResolver.resolve(CurrentUserReference.INSTANCE);
        DocumentAuthors authors = document.getAuthors();
        authors.setEffectiveMetadataAuthor(user);
        authors.setOriginalMetadataAuthor(user);
        authors.setContentAuthor(user);

        if (document.isNew()) {
            authors.setCreator(user);
        }

        XWikiContext xcontext = this.xcontextProvider.get();

        try {
            // Let the listeners that watch what a user writes have their say, and cancel the save, as they do when a
            // page is saved from a script or from the editor.
            xcontext.getWiki().checkSavingDocument(xcontext.getUserReference(), document, comment, false, xcontext);
            xcontext.getWiki().saveDocument(document, comment, xcontext);
        } catch (XWikiException e) {
            throw new ReleaseNotesException(String.format("Failed to save the page [%s].", reference), e);
        }
    }
}
