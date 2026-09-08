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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.releasenotes.Audience;
import org.xwiki.contrib.releasenotes.Change;
import org.xwiki.contrib.releasenotes.ChangeManager;
import org.xwiki.contrib.releasenotes.Importance;
import org.xwiki.contrib.releasenotes.ReleaseNoteManager;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.stability.Unstable;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Default implementation of {@link ChangeManager}, which holds the changes of a release note in the {@code Entry###}
 * pages under it.
 *
 * @version $Id$
 * @since 2.7
 */
@Component
@Singleton
@Unstable
public class DefaultChangeManager implements ChangeManager
{
    /**
     * The name of the page an entry of a release note lives in, whose number is zero-padded so that the entries of a
     * release note are displayed in the order they were added in.
     */
    private static final String ENTRY_NAME_FORMAT = "Entry%03d";

    /**
     * The pages of the entries of a release note, which is what the number of a new entry is derived from.
     */
    private static final Pattern ENTRY_NAME_PATTERN = Pattern.compile("Entry(\\d+)");

    /**
     * How many page names are tried before giving up. Every page of the release note has just been looked at, so the
     * page of the next number is free unless another author took it in the meantime, which is why more than one is
     * tried.
     */
    private static final int CANDIDATE_COUNT = 10;

    /**
     * The value the {@code type} property of an entry holds when that entry is a change and not the contributors of
     * the release note. Every query looking for changes filters on it.
     */
    private static final String CHANGE_TYPE = "Change";

    /**
     * The media of a change are stored as one comma-separated value, so a media name holding a comma cannot be
     * stored.
     */
    private static final String SCREENSHOT_SEPARATOR = ",";

    private static final String PRODUCT = "product";

    private static final String VERSION = "version";

    private static final String TITLE = "title";

    private static final String SUMMARY = "summary";

    private static final String DESCRIPTION = "description";

    private static final String AUDIENCE = "audience";

    private static final String IMPORTANCE = "importance";

    private static final String CATEGORY = "category";

    private static final String SCREENSHOTS = "screenshots";

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private ReleaseNoteManager releaseNoteManager;

    @Inject
    private ProductResolver productResolver;

    @Inject
    private ReleaseNotesDocumentWriter documentWriter;

    @Inject
    private QueryManager queryManager;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @Inject
    @Named("local")
    private EntityReferenceSerializer<String> localEntityReferenceSerializer;

    @Override
    public DocumentReference createChange(Change change) throws ReleaseNotesException
    {
        String version = StringUtils.trimToNull(change.getVersion());

        if (version == null) {
            throw new ReleaseNotesException("A change needs the version it was made in.");
        }

        String title = StringUtils.trimToNull(change.getTitle());

        if (title == null) {
            // A change with no title is displayed as an empty line by every displayer, which makes it look like the
            // change is missing rather than like its title is.
            throw new ReleaseNotesException("A change needs a title.");
        }

        String product = this.productResolver.resolve(change.getProduct());
        XWikiContext xcontext = this.xcontextProvider.get();
        XWikiDocument document = takeNextEntryPage(product, version, xcontext);

        if (document == null) {
            throw new ReleaseNotesException(
                String.format("No page was free for a new change of the version [%s] of [%s].", version, product));
        }

        try {
            // The objects of a change are created by the change template, and not here, so that a template an
            // administrator has customised is what a change is made of, whichever way it was created.
            DocumentReference templateReference = new DocumentReference(ReleaseNotesReferences.CHANGE_TEMPLATE,
                new WikiReference(xcontext.getWikiId()));
            document.readFromTemplate(templateReference, xcontext);
            // A change enforces its required rights when its template does. That is set here because applying a
            // template does not carry the setting over on every XWiki version the application supports.
            document.setEnforceRequiredRights(
                loadDocument(templateReference, xcontext).isEnforceRequiredRights());

            BaseObject entry = document.getXObject(ReleaseNotesReferences.ENTRY_CLASS, true, xcontext);
            entry.set(PRODUCT, product, xcontext);
            entry.set(VERSION, version, xcontext);
            entry.set("type", CHANGE_TYPE, xcontext);

            BaseObject changeObject = document.getXObject(ReleaseNotesReferences.CHANGE_CLASS, true, xcontext);
            changeObject.set(TITLE, title, xcontext);
            // Only the values the change carries are set, so that the ones it leaves out keep the default the
            // template gives them, which is what a template is for.
            setIfNotNull(changeObject, SUMMARY, change.getSummary(), xcontext);
            setIfNotNull(changeObject, DESCRIPTION, change.getDescription(), xcontext);
            setIfNotNull(changeObject, CATEGORY, change.getCategory(), xcontext);

            if (change.getAudience() != null) {
                changeObject.set(AUDIENCE, change.getAudience().getStoredValue(), xcontext);
            }

            if (change.getImportance() != null) {
                changeObject.set(IMPORTANCE, change.getImportance().getStoredValue(), xcontext);
            }

            if (change.getScreenshots() != null) {
                changeObject.set(SCREENSHOTS, String.join(SCREENSHOT_SEPARATOR, change.getScreenshots()), xcontext);
            }
        } catch (XWikiException e) {
            throw new ReleaseNotesException(
                String.format("Failed to fill the page [%s] of the new change.", document.getDocumentReference()), e);
        }

        this.documentWriter.save(document, "New change");

        return document.getDocumentReference();
    }

    @Override
    public DocumentReference reserveNextEntry(String product, String version) throws ReleaseNotesException
    {
        XWikiContext xcontext = this.xcontextProvider.get();
        XWikiDocument document =
            takeNextEntryPage(this.productResolver.resolve(product), version, xcontext);

        if (document == null) {
            return null;
        }

        this.documentWriter.save(document, "Take the page of a new release note entry");

        return document.getDocumentReference();
    }

    @Override
    public Change getChange(DocumentReference reference) throws ReleaseNotesException
    {
        XWikiContext xcontext = this.xcontextProvider.get();
        XWikiDocument document = loadDocument(reference, xcontext);
        BaseObject entry = document.getXObject(ReleaseNotesReferences.ENTRY_CLASS);
        BaseObject changeObject = document.getXObject(ReleaseNotesReferences.CHANGE_CLASS);

        if (entry == null || changeObject == null) {
            throw new ReleaseNotesException(String.format("The page [%s] holds no change.", reference));
        }

        Change change = new Change();
        change.setProduct(entry.getStringValue(PRODUCT));
        change.setVersion(entry.getStringValue(VERSION));
        change.setTitle(changeObject.getStringValue(TITLE));
        change.setSummary(changeObject.getLargeStringValue(SUMMARY));
        change.setDescription(changeObject.getLargeStringValue(DESCRIPTION));
        change.setAudience(Audience.fromStoredValue(changeObject.getStringValue(AUDIENCE)));
        change.setImportance(Importance.fromStoredValue(changeObject.getStringValue(IMPORTANCE)));
        change.setCategory(changeObject.getStringValue(CATEGORY));
        change.setScreenshots(splitScreenshots(changeObject.getStringValue(SCREENSHOTS)));

        return change;
    }

    /**
     * Gives the page of the next entry of a release note, loaded and not yet saved, so that the caller decides what
     * that page holds when it is saved.
     *
     * @return that page, or {@code null} when no page name was free
     */
    private XWikiDocument takeNextEntryPage(String product, String version, XWikiContext xcontext)
        throws ReleaseNotesException
    {
        DocumentReference noteReference = this.releaseNoteManager.getReleaseNoteReference(product, version);

        this.documentWriter.checkEditRight(noteReference);

        SpaceReference versionSpace = noteReference.getLastSpaceReference();
        int highestNumber = getHighestEntryNumber(versionSpace);

        for (int number = highestNumber + 1; number <= highestNumber + CANDIDATE_COUNT; number++) {
            DocumentReference candidate = new DocumentReference("WebHome",
                new SpaceReference(String.format(ENTRY_NAME_FORMAT, number), versionSpace));
            XWikiDocument document = loadDocument(candidate, xcontext);

            if (document.isNew()) {
                return document;
            }
        }

        return null;
    }

    /**
     * Gives the highest number an entry of the passed release note is using.
     * <p>
     * Every page of the release note is looked at, and not only the entries that hold a change, so that a page taken
     * by an author who has not saved their change yet is seen as taken. The highest number is computed here rather
     * than asked of the query, because a query can only order the page names as strings, which sorts
     * {@code Entry999} above {@code Entry1000} and hands the number 1000 out over and over.
     */
    private int getHighestEntryNumber(SpaceReference versionSpace) throws ReleaseNotesException
    {
        String spaceName = this.localEntityReferenceSerializer.serialize(versionSpace);
        // MySQL uses "\" as an escape character, and that character is what separates the spaces of a space
        // reference whose space names hold a dot, so another escape character is asked for.
        String spaceLike = spaceName.replaceAll("([%_!])", "!$1") + ".%";
        List<String> pages;

        try {
            Query query = this.queryManager.createQuery("where doc.space like :space escape '!'", Query.XWQL);
            pages = query.bindValue("space", spaceLike).execute();
        } catch (QueryException e) {
            throw new ReleaseNotesException(
                String.format("Failed to look up the entries of the release note [%s].", spaceName), e);
        }

        int highestNumber = 0;

        for (String page : pages) {
            String entryName =
                this.documentReferenceResolver.resolve(page).getLastSpaceReference().getName();
            Matcher matcher = ENTRY_NAME_PATTERN.matcher(entryName);

            if (matcher.matches()) {
                highestNumber = Math.max(highestNumber, Integer.parseInt(matcher.group(1)));
            }
        }

        return highestNumber;
    }

    private List<String> splitScreenshots(String screenshots)
    {
        List<String> mediaNames = new ArrayList<>();

        for (String mediaName : StringUtils.split(StringUtils.defaultString(screenshots), SCREENSHOT_SEPARATOR)) {
            if (StringUtils.isNotBlank(mediaName)) {
                mediaNames.add(mediaName.trim());
            }
        }

        return mediaNames;
    }

    private void setIfNotNull(BaseObject object, String property, String value, XWikiContext xcontext)
        throws XWikiException
    {
        if (value != null) {
            object.set(property, value, xcontext);
        }
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
