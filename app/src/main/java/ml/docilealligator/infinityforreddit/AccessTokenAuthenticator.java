package ml.docilealligator.infinityforreddit;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Map;

import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.apis.RedditAccountsAPI;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import okhttp3.Authenticator;
import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import retrofit2.Call;
import retrofit2.Retrofit;

class AccessTokenAuthenticator implements Authenticator {
    private Retrofit mRetrofit;
    private RedditDataRoomDatabase mRedditDataRoomDatabase;
    private SharedPreferences mCurrentAccountSharedPreferences;
    private SharedPreferences mAnonymousAccountSharedPreferences;

    AccessTokenAuthenticator(Retrofit retrofit, RedditDataRoomDatabase accountRoomDatabase,
                             SharedPreferences currentAccountSharedPreferences,
                             SharedPreferences anonymousAccountSharedPreferences) {
        mRetrofit = retrofit.newBuilder().baseUrl(APIUtils.LOGIN_BASE_URL).build();
        mRedditDataRoomDatabase = accountRoomDatabase;
        mCurrentAccountSharedPreferences = currentAccountSharedPreferences;
        mAnonymousAccountSharedPreferences = anonymousAccountSharedPreferences;
    }

    @Nullable
    @Override
    public Request authenticate(Route route, @NonNull Response response) {
        if (response.code() == 401) {
            if (response.priorResponse() != null) {
                return null;
            }
            String accessTokenHeader = response.request().header(APIUtils.AUTHORIZATION_KEY);
            if (accessTokenHeader == null || !accessTokenHeader.regionMatches(true, 0, APIUtils.AUTHORIZATION_BASE, 0, APIUtils.AUTHORIZATION_BASE.length())) {
                return null;
            }

            String accessToken = accessTokenHeader.substring(APIUtils.AUTHORIZATION_BASE.length());
            synchronized (this) {
                Account account = mRedditDataRoomDatabase.accountDao().getCurrentAccount();
                if (account == null) {
                    String anonTokenFromPrefs = mAnonymousAccountSharedPreferences.getString(SharedPreferencesUtils.ACCESS_TOKEN, "");
                    if (!"".equals(anonTokenFromPrefs) && !accessToken.equals(anonTokenFromPrefs)) {
                        return response.request().newBuilder().headers(Headers.of(APIUtils.getOAuthHeader(anonTokenFromPrefs))).build();
                    }
                    String newAccessToken = refreshAnonymousAccessToken();
                    if (!newAccessToken.equals("")) {
                        return response.request().newBuilder().headers(Headers.of(APIUtils.getOAuthHeader(newAccessToken))).build();
                    }
                    return null;
                }
                String accessTokenFromDatabase = account.getAccessToken();
                if (accessToken.equals(accessTokenFromDatabase)) {
                    String newAccessToken = refreshAccessToken(account);
                    if (!newAccessToken.equals("")) {
                        return response.request().newBuilder().headers(Headers.of(APIUtils.getOAuthHeader(newAccessToken))).build();
                    } else {
                        return null;
                    }
                } else {
                    return response.request().newBuilder().headers(Headers.of(APIUtils.getOAuthHeader(accessTokenFromDatabase))).build();
                }
            }
        }
        return null;
    }

    private String refreshAnonymousAccessToken() {
        RedditAccountsAPI api = mRetrofit.create(RedditAccountsAPI.class);

        Call<String> accessTokenCall = api.getAccessToken(APIUtils.getHttpBasicAuthHeader(), APIUtils.SCOPE);
        try {
            retrofit2.Response<String> response = accessTokenCall.execute();
            if (response.isSuccessful() && response.body() != null) {
                JSONObject jsonObject = new JSONObject(response.body());
                String newAccessToken = jsonObject.getString(APIUtils.ACCESS_TOKEN_KEY);
                mAnonymousAccountSharedPreferences.edit()
                        .putString(SharedPreferencesUtils.ACCESS_TOKEN, newAccessToken)
                        .apply();
                return newAccessToken;
            }
            return "";
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }

        return "";
    }

    private String refreshAccessToken(Account account) {
        RedditAccountsAPI api = mRetrofit.create(RedditAccountsAPI.class);

        String redditSession = mCurrentAccountSharedPreferences.getString(SharedPreferencesUtils.SESSION_COOKIE, "");
        Map<String, String> accessTokenHeaders = APIUtils.getHttpBasicAuthHeader();
        accessTokenHeaders.put("cookie", redditSession);

        Call<String> accessTokenCall = api.getAccessToken(accessTokenHeaders, APIUtils.SCOPE);
        try {
            retrofit2.Response<String> response = accessTokenCall.execute();
            if (response.isSuccessful() && response.body() != null) {
                JSONObject jsonObject = new JSONObject(response.body());
                String newAccessToken = jsonObject.getString(APIUtils.ACCESS_TOKEN_KEY);
                mRedditDataRoomDatabase.accountDao().updateAccessToken(account.getAccountName(), newAccessToken);

                if (mCurrentAccountSharedPreferences.getString(SharedPreferencesUtils.ACCOUNT_NAME, "").equals(account.getAccountName())) {
                    mCurrentAccountSharedPreferences.edit().putString(SharedPreferencesUtils.ACCESS_TOKEN, newAccessToken).apply();
                }

                return newAccessToken;
            }
            return "";
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }

        return "";
    }
}
