/*
 * Copyright 2014 Vaadin Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.vaadin.tori.data;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.vaadin.tori.PortletRequestAware;
import org.vaadin.tori.data.entity.Attachment;
import org.vaadin.tori.data.entity.DiscussionThread;
import org.vaadin.tori.exception.DataSourceException;

import com.liferay.portal.kernel.exception.NestableException;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.util.ObjectValuePair;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.portletfilerepository.PortletFileRepositoryUtil;
import com.liferay.portal.service.SubscriptionLocalServiceUtil;
import com.liferay.portlet.messageboards.model.MBMessage;
import com.liferay.portlet.messageboards.model.MBMessageConstants;
import com.liferay.portlet.messageboards.model.MBThread;
import com.liferay.portlet.messageboards.model.MBThreadConstants;
import com.liferay.portlet.messageboards.service.MBCategoryServiceUtil;
import com.liferay.portlet.messageboards.service.MBMessageServiceUtil;
import com.liferay.portlet.messageboards.service.MBThreadFlagLocalServiceUtil;
import com.liferay.portlet.messageboards.service.MBThreadLocalServiceUtil;

public class LiferayDataSource extends LiferayCommonDataSource implements
        DataSource, PortletRequestAware {

    private static final Logger log = Logger.getLogger(LiferayDataSource.class);

    @Override
    public void followThread(long threadId) throws DataSourceException {
        try {
            SubscriptionLocalServiceUtil.addSubscription(currentUserId,
                    currentUser.getGroupId(), MBThread.class.getName(),
                    threadId);
        } catch (final NestableException e) {
            log.error(String.format("Cannot follow thread %d", threadId), e);
            throw new DataSourceException(e);
        }
    }

    @Override
    public int getUnreadThreadCount(long categoryId) throws DataSourceException {
        if (currentUserId <= 0) {
            return 0;
        }

        // FIXME Directly accessing Liferay's JDBC DataSource seems very
        // fragile, but the most straightforward way to access the total unread
        // count.
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet result = null;
        final long totalThreadCount = getThreadCountRecursively(categoryId);
        try {
            connection = LIferayCommonJdbcUtil.getJdbcConnection();
            statement = connection
                    .prepareStatement("select (? - count(MBThreadFlag.threadFlagId)) "
                            + "from MBThreadFlag, MBThread "
                            + "where MBThreadFlag.threadId = MBThread.threadId "
                            + "and MBThread.categoryId = ? and MBThreadFlag.userId = ?");

            statement.setLong(1, totalThreadCount);
            statement.setLong(2, categoryId);
            statement.setLong(3, currentUserId);

            result = statement.executeQuery();
            if (result.next()) {
                return result.getInt(1);
            } else {
                return 0;
            }
        } catch (final SQLException e) {
            log.error(e);
            throw new DataSourceException(e);
        } finally {
            LIferayCommonJdbcUtil.closeAndLogException(result);
            LIferayCommonJdbcUtil.closeAndLogException(statement);
            LIferayCommonJdbcUtil.closeAndLogException(connection);
        }
    }

    @Override
    public boolean isThreadRead(long threadId) {
        boolean result = true;
        if (currentUserId > 0) {
            try {
                result = MBThreadFlagLocalServiceUtil.hasThreadFlag(
                        currentUserId,
                        MBThreadLocalServiceUtil.getThread(threadId));
            } catch (final NestableException e) {
                log.error(
                        String.format(
                                "Couldn't check for read flag on thread %d.",
                                threadId), e);
            }
        }
        // default to read in case of an anonymous user
        return result;
    }

    @Override
    public void markThreadRead(long threadId) throws DataSourceException {
        if (currentUserId > 0) {
            try {
                MBThreadFlagLocalServiceUtil.addThreadFlag(currentUserId,
                        MBThreadLocalServiceUtil.getThread(threadId),
                        flagsServiceContext);
            } catch (final NestableException e) {
                log.error(String.format("Couldn't mark thread %d as read.",
                        threadId), e);
                throw new DataSourceException(e);
            }
        }
    }

    @Override
    public void saveNewCategory(Long parentCategoryId, String name,
            String description) throws DataSourceException {
        try {
            log.debug("Adding new category: " + name);
            final long parentId = normalizeCategoryId(parentCategoryId);

            final String displayStyle = "default";

            MBCategoryServiceUtil.addCategory(parentId, name, description,
                    displayStyle, null, null, null, 0, false, null, null, 0,
                    null, false, null, 0, false, null, null, false, false,
                    mbCategoryServiceContext);
        } catch (final NestableException e) {
            log.error("Cannot persist category", e);
            throw new DataSourceException(e);
        }
    }

    @Override
    protected List<Attachment> getAttachments(final MBMessage message)
            throws NestableException {
        if (message.getAttachmentsFileEntriesCount() > 0) {
            final List<FileEntry> filenames = message
                    .getAttachmentsFileEntries();
            final List<Attachment> attachments = new ArrayList<Attachment>(
                    filenames.size());
            for (final FileEntry fileEntry : filenames) {
                String downloadUrl = PortletFileRepositoryUtil
                        .getPortletFileEntryURL(themeDisplay, fileEntry,
                                StringPool.BLANK);

                final String shortFilename = fileEntry.getTitle();
                final long fileSize = fileEntry.getSize();

                final Attachment attachment = new Attachment(shortFilename,
                        fileSize);
                attachment.setDownloadUrl(downloadUrl);
                attachments.add(attachment);
            }
            return attachments;
        }
        return Collections.emptyList();
    }

    @Override
    protected MBMessage internalSaveAsCurrentUser(final String rawBody,
            final Map<String, byte[]> files, DiscussionThread thread,
            final long parentMessageId) throws PortalException, SystemException {
        final long groupId = scopeGroupId;
        final long categoryId = thread.getCategory().getId();

        // trim because liferay seems to bug out otherwise
        String subject = thread.getTopic().trim();
        final String body = rawBody.trim();
        final List<ObjectValuePair<String, InputStream>> attachments = new ArrayList<ObjectValuePair<String, InputStream>>();

        if (files != null) {
            for (final Entry<String, byte[]> file : files.entrySet()) {
                final String fileName = file.getKey();
                final byte[] bytes = file.getValue();

                if ((bytes != null) && (bytes.length > 0)) {
                    final ObjectValuePair<String, InputStream> ovp = new ObjectValuePair<String, InputStream>(
                            fileName, new ByteArrayInputStream(bytes));

                    attachments.add(ovp);
                }
            }
        }

        final boolean anonymous = false;
        final double priority = MBThreadConstants.PRIORITY_NOT_GIVEN;
        final boolean allowPingbacks = false;
        final String format = "bbcode";

        MBMessage message = null;

        if (parentMessageId == MBMessageConstants.DEFAULT_PARENT_MESSAGE_ID) {
            // Post new thread
            message = MBMessageServiceUtil.addMessage(groupId, categoryId,
                    subject, body, format, attachments, anonymous, priority,
                    allowPingbacks, mbMessageServiceContext);
        } else {
            // Post reply
            message = MBMessageServiceUtil.addMessage(parentMessageId, "RE: "
                    + subject, body, format, attachments, anonymous, priority,
                    allowPingbacks, mbMessageServiceContext);
        }
        return message;
    }

    @Override
    protected String getThemeDisplayKey() {
        return WebKeys.THEME_DISPLAY;
    }

}
