package fabric.beta.publisher;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;

import java.io.PrintStream;
import java.util.concurrent.TimeUnit;

class FabricApi {
    private static FabricService service;

    static FabricService service(PrintStream logger) {
        if (service == null) {
            service = retrofit(logger).create(FabricService.class);
        }
        return service;
    }

    private static Retrofit retrofit(PrintStream logger) {
        return new Retrofit.Builder()
                .baseUrl("https://ssl-download-crashlytics-com.s3.amazonaws.com/")
                .client(client(logger))
                .build();
    }

    private static OkHttpClient client(final PrintStream logger) {
        return new OkHttpClient.Builder()
                .addNetworkInterceptor(interceptor(logger))
                .readTimeout(60, TimeUnit.SECONDS)
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    private static HttpLoggingInterceptor interceptor(final PrintStream logger) {
        HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
            @Override
            public void log(String s) {
                logger.println(s);
            }
        });
        interceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);
        return interceptor;
    }
}
