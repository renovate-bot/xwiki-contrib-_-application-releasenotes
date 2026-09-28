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
package org.xwiki.contrib.releasenotes.script;

import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.bridge.DocumentAccessBridge;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.releasenotes.Change;
import org.xwiki.contrib.releasenotes.ChangeManager;
import org.xwiki.contrib.releasenotes.ChangeQuery;
import org.xwiki.contrib.releasenotes.ChangeQueryParser;
import org.xwiki.contrib.releasenotes.ChangeSearchResult;
import org.xwiki.contrib.releasenotes.ReleaseNote;
import org.xwiki.contrib.releasenotes.ReleaseNoteManager;
import org.xwiki.contrib.releasenotes.ReleaseNotesAccessDeniedException;
import org.xwiki.contrib.releasenotes.ReleaseNotesConfiguration;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.contrib.releasenotes.internal.ProductResolver;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.script.service.ScriptService;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.stability.Unstable;

/**
 * Creates and reads the release notes and the changes of a wiki from a script.
 * <p>
 * Every method that can fail throws, and the caller catches with the {@code #try()} directive:
 * </p>
 * <pre>{@code
 * #try('exception')
 *   #set ($reference = $services.releasenotes.createChange({'version': '8.3', 'title': 'Faster startup',
 *     'audience': 'user', 'importance': 'high'}))
 * #end
 * #if ($exception)
 *   ...
 * #end
 * }</pre>
 * <p>
 * A release note and a change are written as a map of their properties, which a converter reads into the bean the
 * methods below take.
 * </p>
 *
 * @version $Id$
 * @since 2.7
 */
@Component
@Named("releasenotes")
@Singleton
@Unstable
public class ReleaseNotesScriptService implements ScriptService
{
    @Inject
    private ReleaseNoteManager releaseNoteManager;

    @Inject
    private ChangeManager changeManager;

    @Inject
    private ChangeQueryParser changeQueryParser;

    @Inject
    private ReleaseNotesConfiguration configuration;

    @Inject
    private ContextualAuthorizationManager authorization;

    @Inject
    private AuthorizationManager authorAuthorization;

    @Inject
    private DocumentAccessBridge documentAccessBridge;

    @Inject
    private ProductResolver productResolver;

    /**
     * @param note the release note to create
     * @return the page the release note was created in
     * @throws ReleaseNotesAccessDeniedException when the current user or the author of the calling script may not
     *             edit the page
     * @throws ReleaseNotesException when it could not be created
     * @see ReleaseNoteManager#createReleaseNote(ReleaseNote)
     */
    public DocumentReference createReleaseNote(ReleaseNote note) throws ReleaseNotesException
    {
        checkEditRight(note.getProduct(), note.getVersion());

        return this.releaseNoteManager.createReleaseNote(note);
    }

    /**
     * @param product the product the release note is about
     * @param version the version the release note is about, in its long form
     * @return the page that release note lives in, whether or not it exists
     * @see ReleaseNoteManager#getReleaseNoteReference(String, String)
     */
    public DocumentReference getReleaseNoteReference(String product, String version)
    {
        return this.releaseNoteManager.getReleaseNoteReference(product, version);
    }

    /**
     * @param reference the page of a release note
     * @return the release note that page holds
     * @throws ReleaseNotesAccessDeniedException when the current user may not view that page
     * @throws ReleaseNotesException when that page holds no release note
     * @see ReleaseNoteManager#getReleaseNote(DocumentReference)
     */
    public ReleaseNote getReleaseNote(DocumentReference reference) throws ReleaseNotesException
    {
        checkViewRight(reference);

        return this.releaseNoteManager.getReleaseNote(reference);
    }

    /**
     * @param note the release note to replace, located by its product and its version
     * @return the release note as it is stored once replaced
     * @throws ReleaseNotesAccessDeniedException when the current user or the author of the calling script may not
     *             edit the page
     * @throws ReleaseNotesException when it could not be replaced
     * @see ReleaseNoteManager#updateReleaseNote(ReleaseNote)
     * @since 2.8
     */
    public ReleaseNote updateReleaseNote(ReleaseNote note) throws ReleaseNotesException
    {
        checkEditRight(note.getProduct(), note.getVersion());

        return this.releaseNoteManager.updateReleaseNote(note);
    }

    /**
     * @param product the product to list the release notes of, or {@code null} to list them all
     * @return the release notes of that product the current user may view
     * @throws ReleaseNotesException when they could not be looked up
     * @see ReleaseNoteManager#getReleaseNotes(String, java.util.function.Predicate)
     */
    public List<ReleaseNote> getReleaseNotes(String product) throws ReleaseNotesException
    {
        return this.releaseNoteManager.getReleaseNotes(product, this::canView);
    }

    /**
     * @param noteReference the page of a release note
     * @return the versions of the changes that release note displays
     * @see ReleaseNoteManager#getAggregatedVersions(DocumentReference)
     */
    public List<String> getAggregatedVersions(DocumentReference noteReference)
    {
        return this.releaseNoteManager.getAggregatedVersions(noteReference);
    }

    /**
     * @param change the change to create
     * @return the page the change was created in
     * @throws ReleaseNotesAccessDeniedException when the current user or the author of the calling script may not
     *             edit the page
     * @throws ReleaseNotesException when it could not be created
     * @see ChangeManager#createChange(Change)
     */
    public DocumentReference createChange(Change change) throws ReleaseNotesException
    {
        // A change is written to a new entry page of its release note, which is not known until it is taken, so the
        // right checked is the right to edit the release note, as the pages of the application do before offering
        // to add a change.
        checkEditRight(change.getProduct(), change.getVersion());

        return this.changeManager.createChange(change);
    }

    /**
     * @param reference the page of the change to replace
     * @param change the change that page is to hold
     * @return the change as it is stored once replaced
     * @throws ReleaseNotesAccessDeniedException when the current user or the author of the calling script may not
     *             edit the page
     * @throws ReleaseNotesException when it could not be replaced
     * @see ChangeManager#updateChange(DocumentReference, Change)
     * @since 2.8
     */
    public Change updateChange(DocumentReference reference, Change change) throws ReleaseNotesException
    {
        checkEditRight(reference);

        return this.changeManager.updateChange(reference, change);
    }

    /**
     * @param product the product of the release note to add an entry to
     * @param version the version of the release note to add an entry to, in its long form
     * @return the page that was taken, or {@code null} when no page name was free
     * @throws ReleaseNotesAccessDeniedException when the current user or the author of the calling script may not
     *             edit the page
     * @throws ReleaseNotesException when the page could not be taken
     * @see ChangeManager#reserveNextEntry(String, String)
     */
    public DocumentReference reserveNextEntry(String product, String version) throws ReleaseNotesException
    {
        checkEditRight(product, version);

        return this.changeManager.reserveNextEntry(product, version);
    }

    /**
     * @param reference the page of a change
     * @return the change that page holds
     * @throws ReleaseNotesAccessDeniedException when the current user may not view that page
     * @throws ReleaseNotesException when that page holds no change
     * @see ChangeManager#getChange(DocumentReference)
     */
    public Change getChange(DocumentReference reference) throws ReleaseNotesException
    {
        checkViewRight(reference);

        return this.changeManager.getChange(reference);
    }

    /**
     * Reads a change search written as text, e.g. the parameters of a macro call or of a URL:
     * <pre>{@code
     * #set ($query = $services.releasenotes.parseQuery({'products': 'XWiki', 'versions': '>=9.0',
     *   'audience': 'User', 'limit': 20}))
     * }</pre>
     *
     * @param parameters the filters and the page to return
     * @return the query those parameters ask for
     * @see ChangeQueryParser#parse(Map)
     */
    public ChangeQuery parseQuery(Map<String, ?> parameters)
    {
        return this.changeQueryParser.parse(parameters);
    }

    /**
     * @param query the changes to look for
     * @return the page of the matching changes the current user may view that the query asks for, and whether more
     *         of them matched
     * @throws ReleaseNotesException when the changes could not be looked up
     * @see ChangeManager#search(ChangeQuery, java.util.function.Predicate)
     */
    public ChangeSearchResult search(ChangeQuery query) throws ReleaseNotesException
    {
        return this.changeManager.search(query, this::canView);
    }

    /**
     * @return the product the release notes of this wiki are about, or {@code null} when none is configured
     * @see ReleaseNotesConfiguration#getDefaultProduct()
     */
    public String getDefaultProduct()
    {
        return this.configuration.getDefaultProduct();
    }

    /**
     * @return the page a new release note is created from, or {@code null} when none is configured
     * @see ReleaseNotesConfiguration#getDefaultTemplate()
     */
    public DocumentReference getDefaultTemplate()
    {
        return this.configuration.getDefaultTemplate();
    }

    /**
     * The components of the application read a page whoever asks for it, since the rights are checked where a call
     * enters the wiki: this is where a script call does. Only the current user is checked, as the platform does when a
     * script reads a page.
     *
     * @param reference a page a script asked to read
     * @return whether the current user may view that page
     */
    private boolean canView(DocumentReference reference)
    {
        return this.authorization.hasAccess(Right.VIEW, reference);
    }

    private void checkViewRight(DocumentReference reference) throws ReleaseNotesAccessDeniedException
    {
        if (!canView(reference)) {
            throw new ReleaseNotesAccessDeniedException(
                String.format("The current user is not allowed to view the page [%s].", reference), reference);
        }
    }

    /**
     * Checks that the page of the release note of the passed product and version may be written.
     * <p>
     * A release note without a version has no page whose right could be checked, and is refused by the manager
     * before anything is written, so it is left for the manager to refuse.
     *
     * @param product the product of the release note, or {@code null} for the product configured for the wiki
     * @param version the version of the release note, in its long form
     */
    private void checkEditRight(String product, String version) throws ReleaseNotesException
    {
        if (StringUtils.isNotBlank(version)) {
            checkEditRight(this.releaseNoteManager.getReleaseNoteReference(this.productResolver.resolve(product),
                version.trim()));
        }
    }

    /**
     * The components of the application write a page whoever asks them to, since the rights are checked where a call
     * enters the wiki: this is where a script call does. The right is checked for the current user <em>and</em> for
     * the author of the calling script, because this service is reachable with the script right alone: a check done
     * for the user only would let a script write on behalf of whoever happens to be reading the page it is on.
     *
     * @param reference a page a script asked to write
     */
    private void checkEditRight(DocumentReference reference) throws ReleaseNotesAccessDeniedException
    {
        if (!this.authorization.hasAccess(Right.EDIT, reference)) {
            throw new ReleaseNotesAccessDeniedException(
                String.format("The current user is not allowed to edit the page [%s].", reference), reference);
        }

        DocumentReference author = this.documentAccessBridge.getCurrentAuthorReference();

        if (!this.authorAuthorization.hasAccess(Right.EDIT, author, reference)) {
            throw new ReleaseNotesAccessDeniedException(String.format(
                "The author [%s] of the calling script is not allowed to edit the page [%s].", author, reference),
                reference);
        }
    }
}
