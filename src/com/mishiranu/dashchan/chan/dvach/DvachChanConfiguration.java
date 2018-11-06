package com.mishiranu.dashchan.chan.dvach;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.res.Resources;
import android.util.Pair;

import chan.content.ChanConfiguration;
import chan.util.CommonUtils;
import chan.util.StringUtils;

public class DvachChanConfiguration extends ChanConfiguration {
	public static final String CAPTCHA_TYPE_2CHAPTCHA = "2chaptcha";
	public static final String CAPTCHA_TYPE_ANIMECAPTCHA = "animecaptcha";

	public static final List<Pair<String, String>> CAPTCHA_TYPES;

	static {
		ArrayList<Pair<String, String>> captchaTypes = new ArrayList<>();
		captchaTypes.add(new Pair<>("2chaptcha", CAPTCHA_TYPE_2CHAPTCHA));
		captchaTypes.add(new Pair<>("animecaptcha", CAPTCHA_TYPE_ANIMECAPTCHA));
		captchaTypes.add(new Pair<>("recaptchav1", CAPTCHA_TYPE_RECAPTCHA_1));
		captchaTypes.add(new Pair<>("recaptcha", CAPTCHA_TYPE_RECAPTCHA_2));
		captchaTypes.add(new Pair<>("mailru", CAPTCHA_TYPE_MAILRU));
		CAPTCHA_TYPES = Collections.unmodifiableList(captchaTypes);
	}

	private static final String KEY_CAPTCHA_CHECK_AVAILABLE = "captcha_check_available";

	private static final String KEY_ICONS = "icons";
	private static final String KEY_IMAGES_ENABLED = "images_enabled";
	private static final String KEY_NAMES_ENABLED = "names_enabled";
	private static final String KEY_TRIPCODES_ENABLED = "tripcodes_enabled";
	private static final String KEY_SUBJECTS_ENABLED = "subjects_enabled";
	private static final String KEY_SAGE_ENABLED = "sage_enabled";
	private static final String KEY_FLAGS_ENABLED = "flags_enabled";
	private static final String KEY_MAX_COMMENT_LENGTH = "max_comment_length";

	public DvachChanConfiguration() {
		request(OPTION_READ_THREAD_PARTIALLY);
		request(OPTION_READ_SINGLE_POST);
		request(OPTION_READ_POSTS_COUNT);
		request(OPTION_READ_USER_BOARDS);
		request(OPTION_ALLOW_CAPTCHA_PASS);
		setDefaultName("Аноним");
		setBumpLimit(500);
		for (Pair<String, String> pair : CAPTCHA_TYPES) {
			addCaptchaType(pair.second);
		}
		addCustomPreference(KEY_CAPTCHA_CHECK_AVAILABLE, false);
	}

	@Override
	public Board obtainBoardConfiguration(String boardName) {
		Board board = new Board();
		board.allowSearch = true;
		board.allowCatalog = true;
		board.allowArchive = true;
		board.allowPosting = true;
		board.allowReporting = true;
		return board;
	}

	@Override
	public Captcha obtainCustomCaptchaConfiguration(String captchaType) {
		if (CAPTCHA_TYPE_2CHAPTCHA.equals(captchaType)) {
			Captcha captcha = new Captcha();
			captcha.title = "2chaptcha";
			captcha.input = Captcha.Input.NUMERIC;
			captcha.validity = Captcha.Validity.IN_BOARD_SEPARATELY;
			return captcha;
		} else if (CAPTCHA_TYPE_ANIMECAPTCHA.equals(captchaType)) {
			Captcha captcha = new Captcha();
			captcha.title = "Animecaptcha";
			captcha.input = Captcha.Input.ALL;
			captcha.validity = Captcha.Validity.SHORT_LIFETIME;
			return captcha;
		}
		return null;
	}

	@Override
	public Posting obtainPostingConfiguration(String boardName, boolean newThread) {
		Posting posting = new Posting();
		posting.allowName = get(boardName, KEY_NAMES_ENABLED, true);
		posting.allowTripcode = get(boardName, KEY_TRIPCODES_ENABLED, true);
		posting.allowEmail = true;
		posting.allowSubject = get(boardName, KEY_SUBJECTS_ENABLED, true);
		posting.optionSage = get(boardName, KEY_SAGE_ENABLED, true);
		posting.optionOriginalPoster = true;
		posting.maxCommentLength = get(boardName, KEY_MAX_COMMENT_LENGTH, 15000);
		posting.maxCommentLengthEncoding = "UTF-8";
		posting.attachmentCount = get(boardName, KEY_IMAGES_ENABLED, true)
				? maxFilesCountEnabled ? Math.max(4, filesCount) : 4 : 0;
		posting.attachmentMimeTypes.add("image/*");
		posting.attachmentMimeTypes.add("video/webm");
		posting.attachmentMimeTypes.add("video/mp4");
		try {
			JSONArray jsonArray = new JSONArray(get(boardName, KEY_ICONS, "[]"));
			for (int i = 0; i < jsonArray.length(); i++) {
				JSONObject jsonObject = jsonArray.getJSONObject(i);
				String name = CommonUtils.getJsonString(jsonObject, "name");
				int num = jsonObject.getInt("num");
				posting.userIcons.add(new Pair<>(Integer.toString(num), name));
			}
		} catch (Exception e) {
			// Ignore exception
		}
		posting.hasCountryFlags = get(boardName, KEY_FLAGS_ENABLED, false);
		return posting;
	}

	@Override
	public Reporting obtainReportingConfiguration(String boardName) {
		Reporting reporting = new Reporting();
		reporting.comment = true;
		reporting.multiplePosts = true;
		return reporting;
	}

	@Override
	public CustomPreference obtainCustomPreferenceConfiguration(String key) {
		if (KEY_CAPTCHA_CHECK_AVAILABLE.equals(key)) {
			Resources resources = getResources();
			CustomPreference customPreference = new CustomPreference();
			customPreference.title = resources.getString(R.string.pref_captcha_check_available);
			customPreference.summary = resources.getString(R.string.pref_captcha_check_available_summary);
			return customPreference;
		}
		return null;
	}

	public boolean isCaptchaCheckAvailable() {
		return get(null, KEY_CAPTCHA_CHECK_AVAILABLE, false);
	}

	private volatile int filesCount = -1;
	private volatile boolean maxFilesCountEnabled = false;

	public void setMaxFilesCount(int filesCount) {
		this.filesCount = filesCount;
	}

	public void revokeMaxFilesCount() {
		setMaxFilesCount(-1);
	}

	public void setMaxFilesCountEnabled(boolean enabled) {
		maxFilesCountEnabled = enabled;
	}

	public boolean isSageEnabled(String boardName) {
		return get(boardName, KEY_SAGE_ENABLED, true);
	}

	public void updateFromBoardsJson(JSONArray jsonArray) {
		try {
			for (int i = 0; i < jsonArray.length(); i++) {
				JSONObject jsonObject = jsonArray.getJSONObject(i);
				String boardName = CommonUtils.getJsonString(jsonObject, "id");
				String defaultName = CommonUtils.optJsonString(jsonObject, "default_name");
				int bumpLimit = jsonObject.optInt("bump_limit");
				if (!StringUtils.isEmpty(defaultName)) {
					storeDefaultName(boardName, defaultName);
				}
				if (bumpLimit > 0) {
					storeBumpLimit(boardName, bumpLimit);
				}
			}
		} catch (JSONException e) {
			// Ignore exception
		}
	}

	public void updateFromThreadsPostsJson(String boardName, JSONObject jsonObject) {
		String title = CommonUtils.optJsonString(jsonObject, "BoardName");
		String description = CommonUtils.optJsonString(jsonObject, "BoardInfoOuter");
		description = transformBoardDescription(description);
		String defaultName = CommonUtils.optJsonString(jsonObject, "default_name");
		int bumpLimit = jsonObject.optInt("bump_limit");
		int maxCommentLength = jsonObject.optInt("max_comment");
		if (!StringUtils.isEmpty(title)) {
			storeBoardTitle(boardName, title);
		}
		if (!StringUtils.isEmpty(description)) {
			storeBoardDescription(boardName, description);
		}
		if (!StringUtils.isEmpty(defaultName)) {
			storeDefaultName(boardName, defaultName);
		}
		if (bumpLimit > 0) {
			storeBumpLimit(boardName, bumpLimit);
		}
		if (maxCommentLength > 0) {
			set(boardName, KEY_MAX_COMMENT_LENGTH, maxCommentLength);
		}
		editBoards(boardName, jsonObject, KEY_IMAGES_ENABLED, "enable_images");
		editBoards(boardName, jsonObject, KEY_NAMES_ENABLED, "enable_names");
		editBoards(boardName, jsonObject, KEY_TRIPCODES_ENABLED, "enable_trips");
		editBoards(boardName, jsonObject, KEY_SUBJECTS_ENABLED, "enable_subject");
		editBoards(boardName, jsonObject, KEY_SAGE_ENABLED, "enable_sage");
		editBoards(boardName, jsonObject, KEY_FLAGS_ENABLED, "enable_flags");
		JSONArray pagesArray = jsonObject.optJSONArray("pages");
		if (pagesArray != null) {
			storePagesCount(boardName, pagesArray.length());
		}
		JSONArray iconsArray = jsonObject.optJSONArray("icons");
		set(boardName, KEY_ICONS, iconsArray != null ? iconsArray.toString() : null);
	}

	private void editBoards(String boardName, JSONObject jsonObject, String key, String name) {
		if (jsonObject.has(name)) {
			boolean value = jsonObject.optInt(name, 1) != 0;
			set(boardName, key, value);
		}
	}

	public String transformBoardDescription(String description) {
		description = StringUtils.nullIfEmpty(StringUtils.clearHtml(description).trim());
		if (description != null) {
			StringBuilder builder = new StringBuilder();
			builder.append(description.substring(0, 1).toUpperCase(Locale.getDefault()));
			builder.append(description.substring(1));
			if (!description.endsWith(".") && !description.endsWith("!")) {
				builder.append(".");
			}
			description = builder.toString();
		}
		return description;
	}
}
