package collectionlogcommand;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;

@Slf4j
@Singleton
public class CollectionLogCommandApiClient
{
    private static final String COLLECTION_LOG_API_BASE = "api.collectionlog.net";
    private static final String COLLECTION_LOG_USER_PATH = "user";
    private static final String COLLECTION_LOG_LOG_PATH = "collectionlog";
    private static final String COLLECTION_LOG_USER_AGENT = "Runelite collection-log-command/1.0";

    @Inject
    private OkHttpClient okHttpClient;

    public CollectionLog getCollectionLog(String username) throws IOException
    {
        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host(COLLECTION_LOG_API_BASE)
                .addPathSegment(COLLECTION_LOG_LOG_PATH)
                .addPathSegment(COLLECTION_LOG_USER_PATH)
                .addEncodedPathSegment(username)
                .build();

        return new GsonBuilder()
                .registerTypeAdapter(CollectionLog.class, new CollectionLogCommandDeserilizer())
                .create()
                .fromJson(getRequest(url), CollectionLog.class);
    }

    private JsonObject getRequest(HttpUrl url) throws IOException
    {
        Request request = new Request.Builder()
                .header("User-Agent", COLLECTION_LOG_USER_AGENT)
                .url(url)
                .get()
                .build();

        return apiRequest(request);
    }

    private JsonObject apiRequest(Request request) throws IOException
    {
        Response response =  okHttpClient.newCall(request).execute();
        JsonObject responseJson = processResponse(response);
        response.close();
        return responseJson;
    }

    private JsonObject processResponse(Response response) throws IOException
    {
        if (!response.isSuccessful())
        {
            return null;
        }

        ResponseBody resBody = response.body();
        if (resBody == null)
        {
            return null;
        }
        return new JsonParser().parse(resBody.string()).getAsJsonObject();
    }
}
