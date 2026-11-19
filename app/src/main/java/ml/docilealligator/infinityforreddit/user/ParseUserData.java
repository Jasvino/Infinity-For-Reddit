package ml.docilealligator.infinityforreddit.user;

import android.os.AsyncTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.utils.JSONUtils;

public class ParseUserData {
    static void parseUserData(RedditDataRoomDatabase redditDataRoomDatabase, String response,
                              ParseUserDataListener parseUserDataListener) {
        new ParseUserDataAsyncTask(redditDataRoomDatabase, response, parseUserDataListener).execute();
    }

    static void parseGqlProfileData(String profileResponse, String searchPeopleResponse, String fallbackUserName,
                                    ParseUserDataListener parseUserDataListener) {
        new ParseGqlProfileDataAsyncTask(profileResponse, searchPeopleResponse, fallbackUserName, parseUserDataListener).execute();
    }

    static void parseUserListingData(String response, ParseUserListingDataListener parseUserListingDataListener) {
        new ParseUserListingDataAsyncTask(response, parseUserListingDataListener).execute();
    }

    private static UserData parseUserDataBase(JSONObject userDataJson, boolean parseFullKarma) throws JSONException {
        if (userDataJson == null) {
            return null;
        }

        userDataJson = userDataJson.getJSONObject(JSONUtils.DATA_KEY);
        String userName = userDataJson.getString(JSONUtils.NAME_KEY);
        String iconImageUrl = userDataJson.getString(JSONUtils.ICON_IMG_KEY);
        String bannerImageUrl = "";
        boolean canBeFollowed;
        if (userDataJson.has(JSONUtils.SUBREDDIT_KEY) && !userDataJson.isNull(JSONUtils.SUBREDDIT_KEY)) {
            bannerImageUrl = userDataJson.getJSONObject(JSONUtils.SUBREDDIT_KEY).getString(JSONUtils.BANNER_IMG_KEY);
            canBeFollowed = true;
        } else {
            canBeFollowed = false;
        }
        int linkKarma = userDataJson.getInt(JSONUtils.LINK_KARMA_KEY);
        int commentKarma = userDataJson.getInt(JSONUtils.COMMENT_KARMA_KEY);
        int awarderKarma = 0;
        int awardeeKarma = 0;
        int totalKarma = linkKarma + commentKarma;
        if (parseFullKarma) {
            awarderKarma = userDataJson.getInt(JSONUtils.AWARDER_KARMA_KEY);
            awardeeKarma = userDataJson.getInt(JSONUtils.AWARDEE_KARMA_KEY);
            totalKarma = userDataJson.getInt(JSONUtils.TOTAL_KARMA_KEY);
        }
        long cakeday = userDataJson.getLong(JSONUtils.CREATED_UTC_KEY) * 1000;
        boolean isGold = userDataJson.getBoolean(JSONUtils.IS_GOLD_KEY);
        boolean isFriend = userDataJson.getBoolean(JSONUtils.IS_FRIEND_KEY);
        boolean isNsfw = false;
        String description = "";
        String title = "";
        if (canBeFollowed) {
            JSONObject subredditJson = userDataJson.getJSONObject(JSONUtils.SUBREDDIT_KEY);
            isNsfw = subredditJson.optBoolean(JSONUtils.OVER_18_KEY, false);
            description = subredditJson.optString(JSONUtils.PUBLIC_DESCRIPTION_KEY, "");
            title = subredditJson.optString(JSONUtils.TITLE_KEY, "");
        }

        return new UserData(userName, iconImageUrl, bannerImageUrl, linkKarma, commentKarma, awarderKarma,
                awardeeKarma, totalKarma, cakeday, isGold, isFriend, canBeFollowed, isNsfw, description, title);
    }

    private static UserData parseGqlUserNode(JSONObject node) {
        if (node == null) {
            return null;
        }

        String userName = !node.isNull("name") ? node.optString("name", "") : "";
        if (userName.isEmpty() && !node.isNull("displayName")) {
            userName = node.optString("displayName", "");
        }
        if (userName.isEmpty()) {
            return null;
        }

        String iconImageUrl = "";
        JSONObject icon128 = node.optJSONObject("icon_128");
        if (icon128 != null && !icon128.isNull("url")) {
            iconImageUrl = icon128.optString("url", "");
        }
        if (iconImageUrl.isEmpty()) {
            JSONObject snoovatarIcon = node.optJSONObject("snoovatarIcon");
            if (snoovatarIcon != null && !snoovatarIcon.isNull("url")) {
                iconImageUrl = snoovatarIcon.optString("url", "");
            }
        }
        JSONObject profileObj = node.optJSONObject("profile");
        if (iconImageUrl.isEmpty() && profileObj != null) {
            JSONObject stylesObj = profileObj.optJSONObject("styles");
            if (stylesObj != null && !stylesObj.isNull("legacyIcon")) {
                Object legacyIconObj = stylesObj.opt("legacyIcon");
                if (legacyIconObj instanceof String) {
                    iconImageUrl = (String) legacyIconObj;
                } else if (legacyIconObj instanceof JSONObject) {
                    iconImageUrl = ((JSONObject) legacyIconObj).optString("url", "");
                }
            }
        }
        iconImageUrl = iconImageUrl.replace("&amp;", "&");

        int linkKarma = 0;
        int commentKarma = 0;
        int totalKarma = 0;
        JSONObject karmaObj = node.optJSONObject("karma");
        if (karmaObj != null) {
            if (!karmaObj.isNull("fromPosts")) {
                linkKarma = karmaObj.optInt("fromPosts", 0);
            }
            if (!karmaObj.isNull("fromComments")) {
                commentKarma = karmaObj.optInt("fromComments", 0);
            }
            if (!karmaObj.isNull("total")) {
                totalKarma = karmaObj.optInt("total", linkKarma + commentKarma);
            } else {
                totalKarma = linkKarma + commentKarma;
            }
        }

        long cakeday = 0;
        String description = "";
        if (profileObj != null) {
            if (!profileObj.isNull("createdAt")) {
                String createdAt = profileObj.optString("createdAt", "");
                if (!createdAt.isEmpty()) {
                    cakeday = ml.docilealligator.infinityforreddit.apis.GqlRequestBody.getUnixTime(createdAt);
                }
            }
            if (!profileObj.isNull("publicDescriptionText")) {
                description = profileObj.optString("publicDescriptionText", "");
            }
        }

        return new UserData(userName, iconImageUrl, "", linkKarma, commentKarma, 0,
                0, totalKarma, cakeday, false, false, true, false, description, "");
    }

    interface ParseUserDataListener {
        void onParseUserDataSuccess(UserData userData, int inboxCount);

        void onParseUserDataFailed();
    }

    interface ParseUserListingDataListener {
        void onParseUserListingDataSuccess(ArrayList<UserData> userData, String after);

        void onParseUserListingDataFailed();
    }

    private static class ParseUserDataAsyncTask extends AsyncTask<Void, Void, Void> {
        private RedditDataRoomDatabase redditDataRoomDatabase;
        private JSONObject jsonResponse;
        private ParseUserDataListener parseUserDataListener;
        private boolean parseFailed = false;

        private UserData userData;
        private int inboxCount = -1;

        ParseUserDataAsyncTask(RedditDataRoomDatabase redditDataRoomDatabase, String response, ParseUserDataListener parseUserDataListener) {
            this.redditDataRoomDatabase = redditDataRoomDatabase;
            this.parseUserDataListener = parseUserDataListener;
            try {
                jsonResponse = new JSONObject(response);
            } catch (JSONException e) {
                parseFailed = true;
                e.printStackTrace();
            }
        }

        @Override
        protected Void doInBackground(Void... voids) {
            if (!parseFailed) {
                try {
                    userData = parseUserDataBase(jsonResponse, true);
                    if (redditDataRoomDatabase != null) {
                        redditDataRoomDatabase.accountDao().updateAccountInfo(userData.getName(), userData.getIconUrl(), userData.getBanner(), userData.getTotalKarma());
                    }
                    if (jsonResponse.getJSONObject(JSONUtils.DATA_KEY).has(JSONUtils.INBOX_COUNT_KEY)) {
                        inboxCount = jsonResponse.getJSONObject(JSONUtils.DATA_KEY).getInt(JSONUtils.INBOX_COUNT_KEY);
                    }
                } catch (JSONException e) {
                    parseFailed = true;
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (!parseFailed) {
                parseUserDataListener.onParseUserDataSuccess(userData, inboxCount);
            } else {
                parseUserDataListener.onParseUserDataFailed();
            }
        }
    }

    private static class ParseUserListingDataAsyncTask extends AsyncTask<Void, Void, Void> {
        private String response;
        private JSONObject jsonResponse;
        private ParseUserListingDataListener parseUserListingDataListener;
        private String after;
        private boolean parseFailed;

        private ArrayList<UserData> userDataArrayList;

        ParseUserListingDataAsyncTask(String response, ParseUserListingDataListener parseUserListingDataListener) {
            this.parseUserListingDataListener = parseUserListingDataListener;
            this.response = response;
            try {
                jsonResponse = new JSONObject(response);
                parseFailed = false;
                userDataArrayList = new ArrayList<>();
            } catch (JSONException e) {
                e.printStackTrace();
                parseFailed = true;
            }
        }

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                if (!parseFailed) {
                    if (jsonResponse.has("errors") && !jsonResponse.isNull("errors")
                            && jsonResponse.getJSONArray("errors").length() > 0) {
                        parseFailed = true;
                        return null;
                    }
                    if (!jsonResponse.has(JSONUtils.DATA_KEY) || jsonResponse.isNull(JSONUtils.DATA_KEY)) {
                        parseFailed = true;
                        return null;
                    }
                    JSONObject dataObj = jsonResponse.getJSONObject(JSONUtils.DATA_KEY);
                    if (!dataObj.has("search") || dataObj.isNull("search")) {
                        parseFailed = true;
                        return null;
                    }
                    JSONObject searchObj = dataObj.getJSONObject("search");
                    JSONObject generalObj = searchObj.optJSONObject("general");
                    if (generalObj == null) {
                        after = null;
                        return null;
                    }
                    JSONObject authorsObj = generalObj.optJSONObject("authors");
                    if (authorsObj == null) {
                        after = null;
                        return null;
                    }

                    JSONObject pageInfo = authorsObj.optJSONObject("pageInfo");
                    if (pageInfo != null && pageInfo.optBoolean("hasNextPage", false) && !pageInfo.isNull("endCursor")) {
                        after = pageInfo.optString("endCursor", null);
                    } else {
                        after = null;
                    }

                    JSONArray edges = authorsObj.optJSONArray("edges");
                    if (edges != null) {
                        for (int i = 0; i < edges.length(); i++) {
                            JSONObject edge = edges.optJSONObject(i);
                            if (edge == null || edge.isNull("node")) {
                                continue;
                            }
                            JSONObject node = edge.optJSONObject("node");
                            if (node == null) {
                                continue;
                            }
                            UserData userData = parseGqlUserNode(node);
                            if (userData != null) {
                                userDataArrayList.add(userData);
                            }
                        }
                    }
                }
            } catch (JSONException e) {
                parseFailed = true;
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (!parseFailed) {
                parseUserListingDataListener.onParseUserListingDataSuccess(userDataArrayList, after);
            } else {
                if ("\"{}\"".equals(response)) {
                    parseUserListingDataListener.onParseUserListingDataSuccess(new ArrayList<>(), null);
                } else {
                    parseUserListingDataListener.onParseUserListingDataFailed();
                }
            }
        }
    }

    private static class ParseGqlProfileDataAsyncTask extends AsyncTask<Void, Void, Void> {
        private JSONObject profileJson;
        private JSONObject searchJson;
        private final String fallbackUserName;
        private final ParseUserDataListener parseUserDataListener;
        private boolean parseFailed = false;
        private UserData userData;

        ParseGqlProfileDataAsyncTask(String profileResponse, String searchPeopleResponse, String fallbackUserName,
                                     ParseUserDataListener parseUserDataListener) {
            this.fallbackUserName = fallbackUserName;
            this.parseUserDataListener = parseUserDataListener;
            try {
                profileJson = new JSONObject(profileResponse);
            } catch (JSONException e) {
                parseFailed = true;
                e.printStackTrace();
            }
            if (searchPeopleResponse != null) {
                try {
                    searchJson = new JSONObject(searchPeopleResponse);
                } catch (JSONException ignored) {
                }
            }
        }

        @Override
        protected Void doInBackground(Void... voids) {
            if (parseFailed) {
                return null;
            }
            try {
                if (profileJson.has("errors") && !profileJson.isNull("errors")
                        && profileJson.getJSONArray("errors").length() > 0) {
                    parseFailed = true;
                    return null;
                }
                if (!profileJson.has(JSONUtils.DATA_KEY) || profileJson.isNull(JSONUtils.DATA_KEY)) {
                    parseFailed = true;
                    return null;
                }
                JSONObject dataObj = profileJson.getJSONObject(JSONUtils.DATA_KEY);
                if (!dataObj.has("profileByName") || dataObj.isNull("profileByName")) {
                    parseFailed = true;
                    return null;
                }
                JSONObject profile = dataObj.getJSONObject("profileByName");

                String userName = !profile.isNull("name") ? profile.optString("name", "") : "";
                if (userName.startsWith("u_")) {
                    userName = userName.substring(2);
                }
                if (userName.isEmpty()) {
                    userName = fallbackUserName != null ? fallbackUserName : "";
                }
                if (userName.isEmpty()) {
                    parseFailed = true;
                    return null;
                }

                String title = !profile.isNull("title") ? profile.optString("title", "") : "";
                String description = !profile.isNull("publicDescriptionText") ? profile.optString("publicDescriptionText", "") : "";
                boolean isNsfw = !profile.isNull("isNsfw") && profile.optBoolean("isNsfw", false);

                long cakeday = 0;
                if (!profile.isNull("createdAt")) {
                    String createdAt = profile.optString("createdAt", "");
                    if (!createdAt.isEmpty()) {
                        cakeday = ml.docilealligator.infinityforreddit.apis.GqlRequestBody.getUnixTime(createdAt);
                    }
                }

                String bannerImageUrl = "";
                String profileIconUrl = "";
                JSONObject styles = profile.optJSONObject("styles");
                if (styles != null) {
                    if (!styles.isNull("bannerBackgroundImage")) {
                        bannerImageUrl = styles.optString("bannerBackgroundImage", "");
                    }
                    if (bannerImageUrl.isEmpty() && !styles.isNull("mobileBannerImage")) {
                        bannerImageUrl = styles.optString("mobileBannerImage", "");
                    }
                    if (!styles.isNull("icon")) {
                        profileIconUrl = styles.optString("icon", "");
                    }
                    if (profileIconUrl.isEmpty() && !styles.isNull("legacyIcon")) {
                        Object legacyIconObj = styles.opt("legacyIcon");
                        if (legacyIconObj instanceof String) {
                            profileIconUrl = (String) legacyIconObj;
                        } else if (legacyIconObj instanceof JSONObject) {
                            profileIconUrl = ((JSONObject) legacyIconObj).optString("url", "");
                        }
                    }
                }
                bannerImageUrl = bannerImageUrl.replace("&amp;", "&");
                profileIconUrl = profileIconUrl.replace("&amp;", "&");

                int linkKarma = 0;
                int commentKarma = 0;
                int totalKarma = 0;
                String iconImageUrl = profileIconUrl;

                if (searchJson != null && searchJson.has(JSONUtils.DATA_KEY) && !searchJson.isNull(JSONUtils.DATA_KEY)) {
                    JSONObject searchData = searchJson.getJSONObject(JSONUtils.DATA_KEY);
                    JSONObject searchObj = searchData.optJSONObject("search");
                    JSONObject generalObj = searchObj != null ? searchObj.optJSONObject("general") : null;
                    JSONObject authorsObj = generalObj != null ? generalObj.optJSONObject("authors") : null;
                    JSONArray edges = authorsObj != null ? authorsObj.optJSONArray("edges") : null;
                    if (edges != null) {
                        for (int i = 0; i < edges.length(); i++) {
                            JSONObject edge = edges.optJSONObject(i);
                            JSONObject node = edge != null ? edge.optJSONObject("node") : null;
                            if (node == null) {
                                continue;
                            }
                            String nodeName = !node.isNull("name") ? node.optString("name", "") : "";
                            if (nodeName.isEmpty() && !node.isNull("displayName")) {
                                nodeName = node.optString("displayName", "");
                            }
                            if (nodeName.equalsIgnoreCase(userName)) {
                                UserData matchedUser = parseGqlUserNode(node);
                                if (matchedUser != null) {
                                    linkKarma = matchedUser.getLinkKarma();
                                    commentKarma = matchedUser.getCommentKarma();
                                    totalKarma = matchedUser.getTotalKarma();
                                    if (cakeday == 0) {
                                        cakeday = matchedUser.getCakeday();
                                    }
                                    if (description.isEmpty()) {
                                        description = matchedUser.getDescription();
                                    }
                                    JSONObject icon128 = node.optJSONObject("icon_128");
                                    String icon128Url = (icon128 != null && !icon128.isNull("url"))
                                            ? icon128.optString("url", "").replace("&amp;", "&")
                                            : "";
                                    if (!icon128Url.isEmpty()) {
                                        iconImageUrl = icon128Url;
                                    } else if (iconImageUrl.isEmpty()) {
                                        iconImageUrl = matchedUser.getIconUrl();
                                    }
                                }
                                break;
                            }
                        }
                    }
                }

                userData = new UserData(userName, iconImageUrl, bannerImageUrl, linkKarma, commentKarma,
                        0, 0, totalKarma, cakeday, false, false, true, isNsfw, description, title);
            } catch (JSONException e) {
                parseFailed = true;
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (!parseFailed && userData != null) {
                parseUserDataListener.onParseUserDataSuccess(userData, -1);
            } else {
                parseUserDataListener.onParseUserDataFailed();
            }
        }
    }
}
