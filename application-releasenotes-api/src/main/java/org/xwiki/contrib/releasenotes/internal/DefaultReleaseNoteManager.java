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

import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.releasenotes.ReleaseNote;
import org.xwiki.contrib.releasenotes.ReleaseNoteAlreadyExistsException;
import org.xwiki.contrib.releasenotes.ReleaseNoteManager;
import org.xwiki.contrib.releasenotes.ReleaseNotesConfiguration;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.stability.Unstable;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Default implementation of {@link ReleaseNoteManager}, which holds the release notes in the pages of the
 * {@code ReleaseNotes.Data} space.
 *
 * @version $Id$
 * @since 2.7
 */
@Component
@Singleton
@Unstable
public class DefaultReleaseNoteManager implements ReleaseNoteManager
{
    /**
     * The version of a milestone is written with this letter in a page name, e.g. {@code 8.3M1} for
     * {@code 8.3-milestone-1}.
     */
    private static final String MILESTONE_LETTER = "M";

    /**
     * The version of a release candidate is written with these letters in a page name, e.g. {@code 8.3RC1} for
     * {@code 8.3-rc-1}.
     */
    private static final String RELEASE_CANDIDATE_LETTERS = "RC";

    private static final String PRODUCT = "product";

    private static final String VERSION = "version";

    private static final String DATE = "date";

    private static final String RELEASED = "released";

    private static final String LEVEL = "level";

    /**
     * The key of the title a release note is given, which names the product and the version it is about.
     */
    private static final String TITLE_KEY = "releasenotes.releasenote.title";

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private ReleaseNotesConfiguration configuration;

    @Inject
    private ProductResolver productResolver;

    @Inject
    private ReleaseNotesDocumentWriter documentWriter;

    @Inject
    private QueryManager queryManager;

    @Inject
    private ContextualLocalizationManager localization;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @Inject
    @Named("local")
    private EntityReferenceSerializer<String> localEntityReferenceSerializer;

    @Override
    public DocumentReference createReleaseNote(ReleaseNote note) throws ReleaseNotesException
    {
        String version = StringUtils.trimToNull(note.getVersion());

        if (version == null) {
            throw new ReleaseNotesException("A release note needs the version it is about.");
        }

        String product = this.productResolver.resolve(note.getProduct());
        DocumentReference reference = getReleaseNoteReference(product, version);

        this.documentWriter.checkEditRight(reference);

        XWikiContext xcontext = this.xcontextProvider.get();
        XWikiDocument document = loadDocument(reference, xcontext);

        if (!document.isNew()) {
            throw new ReleaseNoteAlreadyExistsException(reference);
        }

        DocumentReference template =
            note.getTemplate() != null ? note.getTemplate() : this.configuration.getDefaultTemplate();

        try {
            applyTemplate(document, template, xcontext);
            document.setTitle(getTitle(product, version));

            BaseObject object = document.newXObject(ReleaseNotesReferences.RELEASE_NOTE_CLASS, xcontext);
            object.set(PRODUCT, product, xcontext);
            object.set(VERSION, version, xcontext);
            object.set(RELEASED, note.isReleased() ? "1" : "0", xcontext);

            if (note.getDate() == null) {
                // An empty release date, and not no release date at all: the Live Data listing the release notes
                // sorts them on their date and leaves out the ones that have no value for it.
                object.set(DATE, "", xcontext);
            } else {
                object.set(DATE, note.getDate(), xcontext);
            }
        } catch (XWikiException e) {
            throw new ReleaseNotesException(
                String.format("Failed to fill the page [%s] of the new release note.", reference), e);
        }

        this.documentWriter.save(document, "New Release note");

        return reference;
    }

    @Override
    public DocumentReference getReleaseNoteReference(String product, String version)
    {
        if (StringUtils.isBlank(product) || StringUtils.isBlank(version)) {
            throw new IllegalArgumentException(String.format(
                "A release note is located by a product and a version, and got the product [%s] and the version "
                    + "[%s].", product, version));
        }

        List<String> spaces = new ArrayList<>(ReleaseNotesReferences.DATA_SPACE);
        spaces.add(product);
        spaces.add(getShortVersion(version));

        return new DocumentReference(this.xcontextProvider.get().getWikiId(), spaces, "WebHome");
    }

    @Override
    public ReleaseNote getReleaseNote(DocumentReference reference) throws ReleaseNotesException
    {
        XWikiContext xcontext = this.xcontextProvider.get();
        BaseObject object =
            loadDocument(reference, xcontext).getXObject(ReleaseNotesReferences.RELEASE_NOTE_CLASS);

        if (object == null) {
            throw new ReleaseNotesException(String.format("The page [%s] holds no release note.", reference));
        }

        ReleaseNote note = new ReleaseNote();
        note.setProduct(object.getStringValue(PRODUCT));
        note.setVersion(object.getStringValue(VERSION));
        note.setDate(object.getDateValue(DATE));
        note.setReleased(object.getIntValue(RELEASED) == 1);

        return note;
    }

    @Override
    public List<ReleaseNote> getReleaseNotes(String product) throws ReleaseNotesException
    {
        String className =
            this.localEntityReferenceSerializer.serialize(ReleaseNotesReferences.RELEASE_NOTE_CLASS);
        StringBuilder statement = new StringBuilder("from doc.object(").append(className).append(") as note");

        if (StringUtils.isNotBlank(product)) {
            statement.append(" where note.product = :product");
        }

        // The pages of the release notes are named after their product and their version, so ordering them by page
        // is what gives the release notes of one product together, in version order.
        statement.append(" order by doc.fullName");

        List<String> pages;

        try {
            Query query = this.queryManager.createQuery(statement.toString(), Query.XWQL);

            if (StringUtils.isNotBlank(product)) {
                query.bindValue(PRODUCT, product);
            }

            pages = query.execute();
        } catch (QueryException e) {
            throw new ReleaseNotesException("Failed to look up the release notes of this wiki.", e);
        }

        List<ReleaseNote> notes = new ArrayList<>(pages.size());

        for (String page : pages) {
            notes.add(getReleaseNote(this.documentReferenceResolver.resolve(page)));
        }

        return notes;
    }

    @Override
    public List<String> getAggregatedVersions(DocumentReference noteReference)
    {
        String shortVersion = noteReference.getLastSpaceReference().getName();
        int position = shortVersion.indexOf(MILESTONE_LETTER);

        if (position > -1) {
            return List.of(shortVersion.substring(0, position) + "-milestone-"
                + shortVersion.substring(position + MILESTONE_LETTER.length()));
        }

        position = shortVersion.indexOf(RELEASE_CANDIDATE_LETTERS);

        if (position > -1) {
            return List.of(shortVersion.substring(0, position) + "-rc-"
                + shortVersion.substring(position + RELEASE_CANDIDATE_LETTERS.length()));
        }

        // A final version also displays the changes of its milestones and of its release candidates, which are
        // matched by pattern since their numbers are not known here.
        return List.of(shortVersion, shortVersion + "-milestone%", shortVersion + "-rc%");
    }

    /**
     * Gives the form a version is written in the page name of a release note: the separators taken out and the word
     * "milestone" shortened, so that {@code 8.3-milestone-1} is written {@code 8.3M1}.
     *
     * @param version the version in its long form
     * @return that version in its short form
     */
    private String getShortVersion(String version)
    {
        return StringUtils.upperCase(StringUtils.replaceChars(version, "-", "")).replace("MILESTONE",
            MILESTONE_LETTER);
    }

    /**
     * @param product the product the release note is about
     * @param version the version the release note is about
     * @return the title to give that release note, in the language of the user creating it
     */
    private String getTitle(String product, String version)
    {
        String title = this.localization.getTranslationPlain(TITLE_KEY, product, version);

        // The bundle holding that translation is a page of the application, so a wiki running the jar alone has no
        // value for it, and a release note is still better off with a title than with none.
        return title != null ? title : String.format("Release Notes for %s %s", product, version);
    }

    /**
     * Copies into the new release note what its template holds: the content, and the rights that content needs,
     * since content copied without them would not execute. The title is not copied but built, by
     * {@link #getTitle(String, String)}: a release note that took a title written in Velocity would need the script
     * right to display its own title.
     */
    private void applyTemplate(XWikiDocument document, DocumentReference templateReference, XWikiContext xcontext)
        throws XWikiException, ReleaseNotesException
    {
        if (templateReference == null) {
            return;
        }

        XWikiDocument template = loadDocument(templateReference, xcontext);

        if (template.isNew()) {
            throw new ReleaseNotesException(
                String.format("The release note template [%s] does not exist.", templateReference));
        }

        document.setContent(template.getContent());
        document.setSyntax(template.getSyntax());

        for (BaseObject requiredRight : template.getXObjects(ReleaseNotesReferences.REQUIRED_RIGHT_CLASS)) {
            if (requiredRight != null) {
                document.newXObject(ReleaseNotesReferences.REQUIRED_RIGHT_CLASS, xcontext)
                    .set(LEVEL, requiredRight.getStringValue(LEVEL), xcontext);
            }
        }

        document.setEnforceRequiredRights(template.isEnforceRequiredRights());
    }

    private XWikiDocument loadDocument(DocumentReference reference, XWikiContext xcontext)
        throws ReleaseNotesException
    {
        try {
            return xcontext.getWiki().getDocument(reference, xcontext);
        } catch (XWikiException e) {
            throw new ReleaseNotesException(String.format("Failed to load the page [%s].", reference), e);
        }
    }
}
