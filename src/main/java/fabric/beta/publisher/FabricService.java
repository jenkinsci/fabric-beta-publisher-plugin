package fabric.beta.publisher;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;

interface FabricService {
    @GET("android/ant/crashlytics.zip")
    Call<ResponseBody> crashlyticsTools();
}
