package com.mishiranu.dashchan.chan.arhivach;

import android.util.Log;

import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.model.FileAttachment;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.text.ParseException;
import chan.text.TemplateParser;
import chan.util.StringUtils;

public class ArhivachThreadsParser {
    private final String mSource;
    private final ArhivachChanConfiguration mConfiguration;
    private final ArhivachChanLocator mLocator;
    private final boolean mHandlePagesCount;

    private Post mPost;
    private final LinkedHashMap<Post, Integer> mPostHolders = new LinkedHashMap<>();
    private final ArrayList<FileAttachment> mAttachments = new ArrayList<>();
    private boolean mNextThumbnail;

    private static final Pattern PATTERN_BLOCK_TEXT = Pattern.compile("<a style=\"display:block;\".*?>(.*)</a>");
    private static final Pattern PATTERN_SUBJECT = Pattern.compile("^<b>(.*?)</b> &mdash; ");
    private static final Pattern PATTERN_NOT_ARCHIVED = Pattern.compile("<a.*?>\\[.*?\\] Ожидание обновления</a>");

    public ArhivachThreadsParser(String source, Object linked, boolean handlePagesCount) {
        mSource = source;
        mConfiguration = ChanConfiguration.get(linked);
        mLocator = ChanLocator.get(linked);
        mHandlePagesCount = handlePagesCount;
    }

    public ArrayList<Posts> convertThreads() throws ParseException {
        PARSER.parse(mSource, this);
        if (mPostHolders.size() > 0) {
            ArrayList<Posts> threads = new ArrayList<>(mPostHolders.size());
            for (LinkedHashMap.Entry<Post, Integer> entry : mPostHolders.entrySet()) {
                threads.add(new Posts(entry.getKey()).addPostsCount(entry.getValue()));
            }
            return threads;
        }
        return null;
    }

    public ArrayList<Post> convertPosts() throws ParseException {
        PARSER.parse(mSource, this);
        if (mPostHolders.size() > 0) {
            ArrayList<Post> posts = new ArrayList<>(mPostHolders.size());
            for (Post post : mPostHolders.keySet()) posts.add(post);
            return posts;
        }
        return null;
    }

    private static final TemplateParser<ArhivachThreadsParser> PARSER = new TemplateParser<ArhivachThreadsParser>()
            .starts("tr", "id", "thread_row_").open((instance, holder, tagName, attributes) ->
            {
                String number = attributes.get("id").substring(11);
                holder.mPost = new Post().setThreadNumber(number).setPostNumber(number);
                holder.mAttachments.clear();
                return false;

            }).equals("span", "class", "thread_posts_count").content((instance, holder, text) ->
            {
                int postsCount = Integer.parseInt(text.trim());
                if (postsCount >= 0) holder.mPostHolders.put(holder.mPost, postsCount);
                else holder.mPost = null; // Thread is not archived

            }).equals("a", "class", "expand_image").open((instance, holder, tagName, attributes) ->
            {
                if (holder.mPost != null) {
                    FileAttachment attachment = ArhivachPostsParser.parseExpandImage(attributes, holder.mLocator);
                    if (attachment != null) {
                        holder.mAttachments.add(attachment);
                        holder.mNextThumbnail = true;
                    }
                }
                return false;

            }).name("img").open((instance, holder, tagName, attributes) ->
            {
                if (holder.mPost != null && holder.mNextThumbnail) {
                    ArhivachPostsParser.parseImageThumbnail(attributes, holder.mAttachments, holder.mLocator);
                    holder.mNextThumbnail = false;
                }
                return false;

            }).name("iframe").open((instance, holder, tagName, attributes) ->
            {
                if (holder.mPost != null && holder.mNextThumbnail) {
                    ArhivachPostsParser.parseIframeThumbnail(attributes, holder.mAttachments, holder.mLocator);
                    holder.mNextThumbnail = false;
                }
                return false;

            }).equals("div", "class", "thread_text").open((instance, holder, tagName, attributes) -> holder.mPost != null)
            .content((instance, holder, text) ->
            {
                holder.mNextThumbnail = false;
                text = text.trim();
                if (PATTERN_NOT_ARCHIVED.matcher(text).matches()) {
                    holder.mPostHolders.remove(holder.mPost);
                    holder.mPost = null; // Thread is not archived
                    return;
                }
                Matcher matcher = PATTERN_BLOCK_TEXT.matcher(text);
                if (matcher.matches()) text = matcher.group(1);
                matcher = PATTERN_SUBJECT.matcher(text);
                if (matcher.find()) {
                    holder.mPost.setSubject(StringUtils.nullIfEmpty(StringUtils.clearHtml(matcher.group(1)).trim()));
                    text = text.substring(matcher.group(0).length());
                }
                if (text.length() > 500 && !text.endsWith(".")) text += '\u2026';
                holder.mPost.setComment(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim()));

            }).equals("td", "class", "thread_date").open((instance, holder, tagName, attributes) -> holder.mPost != null)
            .content((instance, holder, text) ->
            {
                GregorianCalendar calendar = ArhivachPostsParser.parseCommonTime(text);
                if (calendar != null) {
                    calendar.add(GregorianCalendar.HOUR, -3);
                    holder.mPost.setTimestamp(calendar.getTimeInMillis());
                }
                if (holder.mAttachments.size() > 0) holder.mPost.setAttachments(holder.mAttachments);
                holder.mPost = null;

            }).equals("a", "title", "Последняя страница").open((instance, holder, tagName, a) -> holder.mHandlePagesCount)
            .content((instance, holder, text) ->
            {
                try {
                    holder.mConfiguration.storePagesCount(null, Integer.parseInt(text.trim()));
                } catch (NumberFormatException e) {
                    Log.e("Exception", e.toString());
                }

            }).prepare();
}
