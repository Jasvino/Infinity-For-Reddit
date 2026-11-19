package ml.docilealligator.infinityforreddit.user;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.SortType;
import ml.docilealligator.infinityforreddit.apis.GqlAPI;
import ml.docilealligator.infinityforreddit.apis.GqlRequestBody;
import ml.docilealligator.infinityforreddit.apis.RedditAPI;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;

public class FetchUserData {
    public static void fetchUserData(Retrofit retrofit, String userName, FetchUserDataListener fetchUserDataListener) {
        fetchUserData(null, retrofit, null, userName, fetchUserDataListener);
    }

    public static void fetchUserData(RedditDataRoomDatabase redditDataRoomDatabase, Retrofit retrofit,
                                     String accessToken, String userName, FetchUserDataListener fetchUserDataListener) {
        if (redditDataRoomDatabase == null && accessToken == null) {
            Retrofit gqlRetrofit = retrofit.newBuilder()
                    .baseUrl(APIUtils.GQL_BASE_URL)
                    .build();
            GqlAPI gqlApi = gqlRetrofit.create(GqlAPI.class);

            RequestBody profileBody = GqlRequestBody.profileDetailsBody(userName);

            gqlApi.getProfileDetails(Collections.emptyMap(), profileBody).enqueue(new Callback<>() {
                @Override
                public void onResponse(@NonNull Call<String> call, @NonNull retrofit2.Response<String> profileResponse) {
                    if (profileResponse.isSuccessful() && profileResponse.body() != null) {
                        RequestBody searchBody = GqlRequestBody.userSearchBody(userName, null, 5);
                        gqlApi.searchUsers(Collections.emptyMap(), searchBody).enqueue(new Callback<>() {
                            @Override
                            public void onResponse(@NonNull Call<String> searchCall, @NonNull retrofit2.Response<String> searchResponse) {
                                String searchBodyStr = searchResponse.isSuccessful() ? searchResponse.body() : null;
                                ParseUserData.parseGqlProfileData(profileResponse.body(), searchBodyStr, userName,
                                        new ParseUserData.ParseUserDataListener() {
                                            @Override
                                            public void onParseUserDataSuccess(UserData userData, int inboxCount) {
                                                fetchUserDataListener.onFetchUserDataSuccess(userData, inboxCount);
                                            }

                                            @Override
                                            public void onParseUserDataFailed() {
                                                fetchUserDataListener.onFetchUserDataFailed();
                                            }
                                        });
                            }

                            @Override
                            public void onFailure(@NonNull Call<String> searchCall, @NonNull Throwable t) {
                                ParseUserData.parseGqlProfileData(profileResponse.body(), null, userName,
                                        new ParseUserData.ParseUserDataListener() {
                                            @Override
                                            public void onParseUserDataSuccess(UserData userData, int inboxCount) {
                                                fetchUserDataListener.onFetchUserDataSuccess(userData, inboxCount);
                                            }

                                            @Override
                                            public void onParseUserDataFailed() {
                                                fetchUserDataListener.onFetchUserDataFailed();
                                            }
                                        });
                            }
                        });
                    } else {
                        fetchUserDataListener.onFetchUserDataFailed();
                    }
                }

                @Override
                public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                    fetchUserDataListener.onFetchUserDataFailed();
                }
            });
            return;
        }

        RedditAPI api = retrofit.create(RedditAPI.class);

        Call<String> userInfo;
        if (redditDataRoomDatabase == null) {
            userInfo = api.getUserData(userName);
        } else {
            userInfo = api.getUserDataOauth(APIUtils.getOAuthHeader(accessToken), userName);
        }
        userInfo.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull retrofit2.Response<String> response) {
                if (response.isSuccessful()) {
                    ParseUserData.parseUserData(redditDataRoomDatabase, response.body(), new ParseUserData.ParseUserDataListener() {
                        @Override
                        public void onParseUserDataSuccess(UserData userData, int inboxCount) {
                            fetchUserDataListener.onFetchUserDataSuccess(userData, inboxCount);
                        }

                        @Override
                        public void onParseUserDataFailed() {
                            fetchUserDataListener.onFetchUserDataFailed();
                        }
                    });
                } else {
                    fetchUserDataListener.onFetchUserDataFailed();
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                fetchUserDataListener.onFetchUserDataFailed();
            }
        });
    }

    public static void fetchUserListingData(Retrofit retrofit, String query, String after, SortType.Type sortType, boolean nsfw,
                                            FetchUserListingDataListener fetchUserListingDataListener) {
        fetchUserListingData(retrofit, query, after, sortType, nsfw, null, fetchUserListingDataListener);
    }

    public static void fetchUserListingData(Retrofit retrofit, String query, String after, SortType.Type sortType, boolean nsfw,
                                            String accessToken, FetchUserListingDataListener fetchUserListingDataListener) {
        GqlAPI api = retrofit.create(GqlAPI.class);

        Map<String, String> headers = accessToken != null
                ? APIUtils.getOAuthHeader(accessToken)
                : Collections.emptyMap();
        RequestBody body = GqlRequestBody.userSearchBody(query, after);

        Call<String> userInfo = api.searchUsers(headers, body);
        userInfo.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull retrofit2.Response<String> response) {
                if (response.isSuccessful()) {
                    ParseUserData.parseUserListingData(response.body(), new ParseUserData.ParseUserListingDataListener() {
                        @Override
                        public void onParseUserListingDataSuccess(ArrayList<UserData> userData, String after) {
                            fetchUserListingDataListener.onFetchUserListingDataSuccess(userData, after);
                        }

                        @Override
                        public void onParseUserListingDataFailed() {
                            fetchUserListingDataListener.onFetchUserListingDataFailed();
                        }
                    });
                } else {
                    fetchUserListingDataListener.onFetchUserListingDataFailed();
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                fetchUserListingDataListener.onFetchUserListingDataFailed();
            }
        });
    }

    public interface FetchUserDataListener {
        void onFetchUserDataSuccess(UserData userData, int inboxCount);

        void onFetchUserDataFailed();
    }

    public interface FetchUserListingDataListener {
        void onFetchUserListingDataSuccess(ArrayList<UserData> userData, String after);

        void onFetchUserListingDataFailed();
    }
}