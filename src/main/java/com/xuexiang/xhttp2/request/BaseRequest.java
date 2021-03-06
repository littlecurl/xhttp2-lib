/*
 * Copyright (C) 2018 xuexiangjys(xuexiangjys@163.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xuexiang.xhttp2.request;

import android.content.Context;
import android.text.TextUtils;

import com.google.gson.reflect.TypeToken;
import com.xuexiang.xhttp2.XHttp;
import com.xuexiang.xhttp2.annotation.ThreadType;
import com.xuexiang.xhttp2.api.ApiService;
import com.xuexiang.xhttp2.cache.RxCache;
import com.xuexiang.xhttp2.cache.converter.IDiskConverter;
import com.xuexiang.xhttp2.cache.model.CacheMode;
import com.xuexiang.xhttp2.cache.model.CacheResult;
import com.xuexiang.xhttp2.callback.CallBack;
import com.xuexiang.xhttp2.callback.CallBackProxy;
import com.xuexiang.xhttp2.callback.CallClazzProxy;
import com.xuexiang.xhttp2.https.HttpsUtils;
import com.xuexiang.xhttp2.interceptor.BaseDynamicInterceptor;
import com.xuexiang.xhttp2.interceptor.CacheInterceptor;
import com.xuexiang.xhttp2.interceptor.CacheInterceptorOffline;
import com.xuexiang.xhttp2.interceptor.HeadersInterceptor;
import com.xuexiang.xhttp2.interceptor.NoCacheInterceptor;
import com.xuexiang.xhttp2.model.ApiResult;
import com.xuexiang.xhttp2.model.HttpHeaders;
import com.xuexiang.xhttp2.model.HttpParams;
import com.xuexiang.xhttp2.subsciber.CallBackSubscriber;
import com.xuexiang.xhttp2.transform.HttpResultTransformer;
import com.xuexiang.xhttp2.transform.HttpSchedulersTransformer;
import com.xuexiang.xhttp2.transform.func.ApiResultFunc;
import com.xuexiang.xhttp2.transform.func.CacheResultFunc;
import com.xuexiang.xhttp2.transform.func.RetryExceptionFunc;
import com.xuexiang.xhttp2.utils.Utils;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.ObservableTransformer;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import okhttp3.Cache;
import okhttp3.Cookie;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.CallAdapter;
import retrofit2.Converter;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;

import static com.xuexiang.xhttp2.XHttp.DEFAULT_CACHE_NEVER_EXPIRE;

/**
 * ?????????????????????
 *
 * @author xuexiang
 * @since 2018/5/23 ??????10:03
 */
@SuppressWarnings(value = {"unchecked"})
public abstract class BaseRequest<R extends BaseRequest> {
    protected Context mContext;
    //====????????????=====//
    protected HttpUrl mHttpUrl;
    protected String mBaseUrl;                                              //baseUrl
    private String mSubUrl;                                                 //SubUrl,??????BaseUrl?????????url??????
    protected String mUrl;                                                  //??????url
    //====????????????=====//
    protected boolean mIsSyncRequest = false;                                //?????????????????????
    protected boolean mIsOnMainThread = true;                                //???????????????????????????
    protected boolean mKeepJson = false;                                    //?????????????????????json??????
    //====????????????=====//
    private boolean mSign = false;                                          //??????????????????
    private boolean mTimeStamp = false;                                     //???????????????????????????
    private boolean mAccessToken = false;                                   //??????????????????token
    //====??????????????????=====//
    protected long mReadTimeOut;                                            //?????????
    protected long mWriteTimeOut;                                           //?????????
    protected long mConnectTimeout;                                         //????????????
    protected int mRetryCount;                                              //??????????????????3???
    protected int mRetryDelay;                                              //??????xxms??????
    protected int mRetryIncreaseDelay;                                      //????????????
    //====?????????????????????????????????=====//
    protected HttpHeaders mHeaders = new HttpHeaders();                     //?????????header
    protected HttpParams mParams = new HttpParams();                        //?????????param
    //====????????????=====//
    protected RxCache mRxCache;                                             //rxCache??????
    protected Cache mCache;
    protected CacheMode mCacheMode;                                         //???????????????
    protected long mCacheTime;                                              //????????????
    protected String mCacheKey;                                             //??????Key
    protected IDiskConverter mDiskConverter;                                //??????RxCache???????????????
    //====OkHttpClient????????????????????????=====//
    protected OkHttpClient mOkHttpClient;
    protected Proxy mProxy;
    protected final List<Interceptor> mNetworkInterceptors = new ArrayList<>();
    protected final List<Interceptor> mInterceptors = new ArrayList<>();
    //====Retrofit???Api???Factory=====//
    protected Retrofit mRetrofit;
    protected ApiService mApiManager;                                       //????????????api??????
    protected List<Converter.Factory> mConverterFactories = new ArrayList<>();
    protected List<CallAdapter.Factory> mAdapterFactories = new ArrayList<>();
    //====Https??????=====//
    protected HttpsUtils.SSLParams mSSLParams;
    protected HostnameVerifier mHostnameVerifier;
    //====Cookie??????=====//
    protected List<Cookie> mCookies = new ArrayList<>();                    //?????????????????????Cookie

    /**
     * ??????????????????
     *
     * @param url ?????????url
     */
    public BaseRequest(String url) {
        mContext = XHttp.getContext();
        mUrl = url;
        mBaseUrl = XHttp.getBaseUrl();
        mSubUrl = XHttp.getSubUrl();
        if (!TextUtils.isEmpty(mBaseUrl)) {
            mHttpUrl = HttpUrl.parse(mBaseUrl);
        }
        mCacheMode = XHttp.getCacheMode();                                //??????????????????
        mCacheTime = XHttp.getCacheTime();                                //????????????
        mRetryCount = XHttp.getRetryCount();                              //??????????????????
        mRetryDelay = XHttp.getRetryDelay();                              //??????????????????
        mRetryIncreaseDelay = XHttp.getRetryIncreaseDelay();              //????????????????????????
        //OKHttp  mCache
        mCache = XHttp.getHttpCache();
        //???????????? Accept-Language
        String acceptLanguage = HttpHeaders.getAcceptLanguage();
        if (!TextUtils.isEmpty(acceptLanguage))
            headers(HttpHeaders.HEAD_KEY_ACCEPT_LANGUAGE, acceptLanguage);
        //???????????? User-Agent
        String userAgent = HttpHeaders.getUserAgent();
        if (!TextUtils.isEmpty(userAgent)) headers(HttpHeaders.HEAD_KEY_USER_AGENT, userAgent);
        //????????????????????????
        if (XHttp.getCommonParams() != null) mParams.put(XHttp.getCommonParams());
        if (XHttp.getCommonHeaders() != null) mHeaders.put(XHttp.getCommonHeaders());
    }

    //===========================================//
    //               ??????url??????                  //
    //===========================================//
    /**
     * ??????url??????
     *
     * @param url
     * @return
     */
    public R url(String url) {
        mUrl = Utils.checkNotNull(url, "mUrl == null");
        return (R) this;
    }

    /**
     * ????????????url??????
     *
     * @param baseUrl
     * @return
     */
    public R baseUrl(String baseUrl) {
        mBaseUrl = baseUrl;
        if (!TextUtils.isEmpty(mBaseUrl)) {
            mHttpUrl = HttpUrl.parse(baseUrl);
        }
        return (R) this;
    }

    /**
     * ????????????subUrl??????
     *
     * @param subUrl
     * @return
     */
    public R subUrl(String subUrl) {
        mSubUrl = Utils.checkNotNull(subUrl, "mSubUrl == null");
        return (R) this;
    }

    /**
     * ?????????????????????
     *
     * @return
     */
    public String getUrl() {
        return mSubUrl + mUrl;
    }

    /**
     * @return ??????????????????
     */
    public String getBaseUrl() {
        return mBaseUrl;
    }

    //===========================================//
    //               ????????????                     //
    //===========================================//

    /**
     * ??????json????????????????????????????????????Json???String?????????????????????????????????String.class???
     *
     * @param keepJson
     * @return
     */
    public R keepJson(boolean keepJson) {
        mKeepJson = keepJson;
        return (R) this;
    }

    /**
     * ???????????????????????????????????????????????????false???
     *
     * @param syncRequest
     * @return
     */
    public R syncRequest(boolean syncRequest) {
        mIsSyncRequest = syncRequest;
        return (R) this;
    }

    /**
     * ????????????????????????????????????????????????true???
     *
     * @param onMainThread
     * @return
     */
    public R onMainThread(boolean onMainThread) {
        mIsOnMainThread = onMainThread;
        return (R) this;
    }

    /**
     * ?????????????????????????????????
     *
     * @param threadType
     * @return
     */
    public R threadType(@ThreadType String threadType) {
        if (ThreadType.TO_MAIN.equals(threadType)) { // -> main -> io -> main
            syncRequest(false).onMainThread(true);
        } else if (ThreadType.TO_IO.equals(threadType)) { // -> main -> io -> io
            syncRequest(false).onMainThread(false);
        } else if (ThreadType.IN_THREAD.equals(threadType)) { // -> io -> io -> io
            syncRequest(true).onMainThread(false);
        }
        return (R) this;
    }

    //===========================================//
    //             ???????????????????????????                //
    //===========================================//

    /**
     * ?????????????????????????????????false???
     *
     * @param sign
     * @return
     */
    public R sign(boolean sign) {
        mSign = sign;
        return (R) this;
    }

    /**
     * ??????????????????????????????false???
     *
     * @param timeStamp
     * @return
     */
    public R timeStamp(boolean timeStamp) {
        mTimeStamp = timeStamp;
        return (R) this;
    }

    /**
     * ??????????????????token?????????false)
     *
     * @param accessToken
     * @return
     */
    public R accessToken(boolean accessToken) {
        mAccessToken = accessToken;
        return (R) this;
    }



    //===========================================//
    //             ???????????????????????????                //
    //===========================================//

    /**
     * ????????????????????????
     *
     * @param readTimeOut
     * @return
     */
    public R readTimeOut(long readTimeOut) {
        mReadTimeOut = readTimeOut;
        return (R) this;
    }

    /**
     * ????????????????????????
     *
     * @param writeTimeOut
     * @return
     */
    public R writeTimeOut(long writeTimeOut) {
        mWriteTimeOut = writeTimeOut;
        return (R) this;
    }

    /**
     * ????????????????????????????????????
     *
     * @param connectTimeout
     * @return
     */
    public R connectTimeout(long connectTimeout) {
        mConnectTimeout = connectTimeout;
        return (R) this;
    }

    /**
     * ??????????????????????????????????????????????????????????????????
     *
     * @param timeOut
     * @return
     */
    public R timeOut(long timeOut) {
        mReadTimeOut = timeOut;
        mWriteTimeOut = timeOut;
        mConnectTimeout = timeOut;
        return (R) this;
    }

    /**
     * ???????????????????????????
     *
     * @param retryCount
     * @return
     */
    public R retryCount(int retryCount) {
        if (retryCount < 0) {
            throw new IllegalArgumentException("mRetryCount must > 0");
        }
        mRetryCount = retryCount;
        return (R) this;
    }

    /**
     * ?????????????????????????????????
     *
     * @param retryDelay
     * @return
     */
    public R retryDelay(int retryDelay) {
        if (retryDelay < 0) {
            throw new IllegalArgumentException("mRetryDelay must > 0");
        }
        mRetryDelay = retryDelay;
        return (R) this;
    }

    /**
     * ??????????????????????????????
     *
     * @param retryIncreaseDelay
     * @return
     */
    public R retryIncreaseDelay(int retryIncreaseDelay) {
        if (retryIncreaseDelay < 0) {
            throw new IllegalArgumentException("mRetryIncreaseDelay must > 0");
        }
        mRetryIncreaseDelay = retryIncreaseDelay;
        return (R) this;
    }

    //===========================================//
    //            ?????????????????????????????????             //
    //===========================================//

    /**
     * ???????????????
     */
    public R headers(HttpHeaders headers) {
        mHeaders.put(headers);
        return (R) this;
    }

    /**
     * ???????????????
     */
    public R headers(String key, String value) {
        mHeaders.put(key, value);
        return (R) this;
    }

    /**
     * ???????????????
     */
    public R removeHeader(String key) {
        mHeaders.remove(key);
        return (R) this;
    }

    /**
     * ?????????????????????
     */
    public R removeAllHeaders() {
        mHeaders.clear();
        return (R) this;
    }

    /**
     * ????????????
     */
    public R params(HttpParams params) {
        mParams.put(params);
        return (R) this;
    }

    /**
     * ????????????
     */
    public R params(Map<String, Object> params) {
        mParams.put(params);
        return (R) this;
    }

    /**
     * ????????????
     */
    public R params(String key, Object value) {
        mParams.put(key, value);
        return (R) this;
    }

    /**
     * ????????????
     */
    public R removeParam(String key) {
        mParams.remove(key);
        return (R) this;
    }

    /**
     * ??????????????????
     */
    public R removeAllParams() {
        mParams.clear();
        return (R) this;
    }

    public HttpParams getParams() {
        return mParams;
    }

    //===========================================//
    //               ??????????????????                  //
    //===========================================//

    /**
     * ?????????????????????????????????
     *
     * @param cache
     * @return
     */
    public R okCache(Cache cache) {
        mCache = cache;
        return (R) this;
    }

    /**
     * ??????????????????????????????????????????
     *
     * @param cacheMode
     * @return
     */
    public R cacheMode(CacheMode cacheMode) {
        mCacheMode = cacheMode;
        return (R) this;
    }

    /**
     * ???????????????key
     *
     * @param cacheKey
     * @return
     */
    public R cacheKey(String cacheKey) {
        mCacheKey = cacheKey;
        return (R) this;
    }

    /**
     * ????????????????????????????????????????????????????????????????????????
     *
     * @param cacheTime
     * @return
     */
    public R cacheTime(long cacheTime) {
        if (cacheTime <= -1) {
            cacheTime = DEFAULT_CACHE_NEVER_EXPIRE;
        }
        mCacheTime = cacheTime;
        return (R) this;
    }

    /**
     * ????????????????????????
     */
    public R cacheDiskConverter(IDiskConverter converter) {
        mDiskConverter = Utils.checkNotNull(converter, "converter == null");
        return (R) this;
    }

    //===========================================//
    //     OkHttpClient??????????????????????????????          //
    //===========================================//

    /**
     * ????????????
     */
    public R okproxy(Proxy proxy) {
        mProxy = proxy;
        return (R) this;
    }

    /**
     * ?????????????????????
     *
     * @param interceptor
     * @return
     */
    public R addInterceptor(Interceptor interceptor) {
        mInterceptors.add(Utils.checkNotNull(interceptor, "interceptor == null"));
        return (R) this;
    }

    /**
     * ?????????????????????
     *
     * @param interceptor
     * @return
     */
    public R addNetworkInterceptor(Interceptor interceptor) {
        mNetworkInterceptors.add(Utils.checkNotNull(interceptor, "interceptor == null"));
        return (R) this;
    }

    //===========================================//
    //         Retrofit???Factory??????              //
    //===========================================//

    /**
     * ??????Converter.Factory,??????GsonConverterFactory.create()
     */
    public R addConverterFactory(Converter.Factory factory) {
        mConverterFactories.add(factory);
        return (R) this;
    }

    /**
     * ??????CallAdapter.Factory,??????RxJavaCallAdapterFactory.create()
     */
    public R addCallAdapterFactory(CallAdapter.Factory factory) {
        mAdapterFactories.add(factory);
        return (R) this;
    }

    //===========================================//
    //                Https??????                   //
    //===========================================//

    /**
     * https?????????????????????
     */
    public R hostnameVerifier(HostnameVerifier hostnameVerifier) {
        mHostnameVerifier = hostnameVerifier;
        return (R) this;
    }

    /**
     * https????????????????????????
     */
    public R certificates(InputStream... certificates) {
        mSSLParams = HttpsUtils.getSslSocketFactory(null, null, certificates);
        return (R) this;
    }

    /**
     * https??????????????????
     */
    public R certificates(InputStream bksFile, String password, InputStream... certificates) {
        mSSLParams = HttpsUtils.getSslSocketFactory(bksFile, password, certificates);
        return (R) this;
    }

    //===========================================//
    //                Cookie??????                  //
    //===========================================//

    public R addCookie(String name, String value) {
        Cookie.Builder builder = new Cookie.Builder();
        Cookie cookie = builder.name(name).value(value).domain(mHttpUrl.host()).build();
        mCookies.add(cookie);
        return (R) this;
    }

    public R addCookie(Cookie cookie) {
        mCookies.add(cookie);
        return (R) this;
    }

    public R addCookies(List<Cookie> cookies) {
        mCookies.addAll(cookies);
        return (R) this;
    }

    //===========================================//
    //               ????????????                     //
    //===========================================//

    /**
     * ??????????????????
     *
     * @return ?????????????????????
     */
    protected abstract Observable<ResponseBody> generateRequest();

    /**
     * ?????????????????????????????????????????????OkClient
     */
    private OkHttpClient.Builder generateOkClient() {
        if (mReadTimeOut <= 0 && mWriteTimeOut <= 0 && mConnectTimeout <= 0 && mSSLParams == null
                && mCookies.size() == 0 && mHostnameVerifier == null && mProxy == null && mHeaders.isEmpty()) {
            OkHttpClient.Builder builder = XHttp.getOkHttpClientBuilder();
            for (Interceptor interceptor : builder.interceptors()) {
                if (interceptor instanceof BaseDynamicInterceptor) {
                    ((BaseDynamicInterceptor) interceptor)
                            .sign(mSign)
                            .timeStamp(mTimeStamp)
                            .accessToken(mAccessToken);
                }
            }
            return builder;
        } else {
            final OkHttpClient.Builder newClientBuilder = XHttp.getOkHttpClient().newBuilder();
            if (mReadTimeOut > 0)
                newClientBuilder.readTimeout(mReadTimeOut, TimeUnit.MILLISECONDS);
            if (mWriteTimeOut > 0)
                newClientBuilder.writeTimeout(mWriteTimeOut, TimeUnit.MILLISECONDS);
            if (mConnectTimeout > 0)
                newClientBuilder.connectTimeout(mConnectTimeout, TimeUnit.MILLISECONDS);
            if (mHostnameVerifier != null) newClientBuilder.hostnameVerifier(mHostnameVerifier);
            if (mSSLParams != null)
                newClientBuilder.sslSocketFactory(mSSLParams.sSLSocketFactory, mSSLParams.trustManager);
            if (mProxy != null) newClientBuilder.proxy(mProxy);
            if (mCookies.size() > 0) XHttp.getCookieJar().addCookies(mCookies);
            for (Interceptor interceptor : mInterceptors) {
                if (interceptor instanceof BaseDynamicInterceptor) {
                    ((BaseDynamicInterceptor) interceptor)
                            .sign(mSign)
                            .timeStamp(mTimeStamp)
                            .accessToken(mAccessToken);
                }
                newClientBuilder.addInterceptor(interceptor);
            }
            for (Interceptor interceptor : newClientBuilder.interceptors()) {
                if (interceptor instanceof BaseDynamicInterceptor) {
                    ((BaseDynamicInterceptor) interceptor)
                            .sign(mSign)
                            .timeStamp(mTimeStamp)
                            .accessToken(mAccessToken);
                }
            }
            if (mNetworkInterceptors.size() > 0) {
                for (Interceptor interceptor : mNetworkInterceptors) {
                    newClientBuilder.addNetworkInterceptor(interceptor);
                }
            }
            //?????????
            newClientBuilder.addInterceptor(new HeadersInterceptor(mHeaders));
            return newClientBuilder;
        }
    }

    /**
     * ?????????????????????????????????????????????Retrofit
     */
    private Retrofit.Builder generateRetrofit() {
        if (mConverterFactories.isEmpty() && mAdapterFactories.isEmpty()) {
            return XHttp.getRetrofitBuilder().baseUrl(mBaseUrl);
        } else {
            final Retrofit.Builder retrofitBuilder = new Retrofit.Builder();
            if (!mConverterFactories.isEmpty()) {
                for (Converter.Factory converterFactory : mConverterFactories) {
                    retrofitBuilder.addConverterFactory(converterFactory);
                }
            } else {
                //?????????????????????????????????
                List<Converter.Factory> listConverterFactory = XHttp.getRetrofitBuilder().converterFactories();
                for (Converter.Factory factory : listConverterFactory) {
                    retrofitBuilder.addConverterFactory(factory);
                }
            }
            if (!mAdapterFactories.isEmpty()) {
                for (CallAdapter.Factory adapterFactory : mAdapterFactories) {
                    retrofitBuilder.addCallAdapterFactory(adapterFactory);
                }
            } else {
                //?????????????????????????????????
                List<CallAdapter.Factory> listAdapterFactory = XHttp.getRetrofitBuilder().callAdapterFactories();
                for (CallAdapter.Factory factory : listAdapterFactory) {
                    retrofitBuilder.addCallAdapterFactory(factory);
                }
            }
            return retrofitBuilder.baseUrl(mBaseUrl);
        }
    }

    /**
     * ?????????????????????????????????????????????RxCache???Cache
     */
    private RxCache.Builder generateRxCache() {
        final RxCache.Builder rxCacheBuilder = XHttp.getRxCacheBuilder();
        switch (mCacheMode) {
            case NO_CACHE://???????????????
                final NoCacheInterceptor NOCACHEINTERCEPTOR = new NoCacheInterceptor();
                mInterceptors.add(NOCACHEINTERCEPTOR);
                mNetworkInterceptors.add(NOCACHEINTERCEPTOR);
                break;
            case DEFAULT://??????OkHttp?????????
                if (mCache == null) {
                    File cacheDirectory = XHttp.getCacheDirectory();
                    if (cacheDirectory == null) {
                        cacheDirectory = new File(XHttp.getContext().getCacheDir(), "okhttp-cache");
                    } else {
                        if (cacheDirectory.isDirectory() && !cacheDirectory.exists()) {
                            cacheDirectory.mkdirs();
                        }
                    }
                    mCache = new Cache(cacheDirectory, Math.max(5 * 1024 * 1024, XHttp.getCacheMaxSize()));
                }
                String cacheControlValue = String.format("max-age=%d", Math.max(-1, mCacheTime));
                final CacheInterceptor REWRITE_CACHE_CONTROL_INTERCEPTOR = new CacheInterceptor(XHttp.getContext(), cacheControlValue);
                final CacheInterceptorOffline REWRITE_CACHE_CONTROL_INTERCEPTOR_OFFLINE = new CacheInterceptorOffline(XHttp.getContext(), cacheControlValue);
                mNetworkInterceptors.add(REWRITE_CACHE_CONTROL_INTERCEPTOR);
                mNetworkInterceptors.add(REWRITE_CACHE_CONTROL_INTERCEPTOR_OFFLINE);
                mInterceptors.add(REWRITE_CACHE_CONTROL_INTERCEPTOR_OFFLINE);
                break;
            case FIRST_REMOTE:
            case FIRST_CACHE:
            case ONLY_REMOTE:
            case ONLY_CACHE:
            case CACHE_REMOTE:
            case CACHE_REMOTE_DISTINCT:
                mInterceptors.add(new NoCacheInterceptor());
                if (mDiskConverter == null) {
                    final RxCache.Builder tempRxCacheBuilder = rxCacheBuilder;
                    tempRxCacheBuilder.cacheKey(Utils.checkNotNull(mCacheKey, "mCacheKey == null"))
                            .cacheTime(mCacheTime);
                    return tempRxCacheBuilder;
                } else {
                    final RxCache.Builder cacheBuilder = XHttp.getRxCache().newBuilder();
                    cacheBuilder.diskConverter(mDiskConverter)
                            .cacheKey(Utils.checkNotNull(mCacheKey, "mCacheKey == null"))
                            .cacheTime(mCacheTime);
                    return cacheBuilder;
                }
        }
        return rxCacheBuilder;
    }

    /**
     * ?????????????????????RxCache???OkHttpClient???Retrofit???mApiManager???
     *
     * @return
     */
    protected R build() {
        final RxCache.Builder rxCacheBuilder = generateRxCache();
        OkHttpClient.Builder okHttpClientBuilder = generateOkClient();
        if (mCacheMode == CacheMode.DEFAULT) {//okHttp??????
            okHttpClientBuilder.cache(mCache);
        }
        final Retrofit.Builder retrofitBuilder = generateRetrofit();
        retrofitBuilder.addCallAdapterFactory(RxJava2CallAdapterFactory.create());//??????RxJavaCallAdapterFactory
        mOkHttpClient = okHttpClientBuilder.build();
        retrofitBuilder.client(mOkHttpClient);
        mRetrofit = retrofitBuilder.build();
        mRxCache = rxCacheBuilder.build();
        mApiManager = mRetrofit.create(ApiService.class);
        return (R) this;
    }

    //===================????????????===============================//

    public <T> Observable<T> execute(Class<T> clazz) {
        return execute(new CallClazzProxy<ApiResult<T>, T>(clazz) {
        });
    }

    public <T> Observable<T> execute(Type type) {
        return execute(new CallClazzProxy<ApiResult<T>, T>(type) {
        });
    }

    public <T> Disposable execute(CallBack<T> callBack) {
        return execute(new CallBackProxy<ApiResult<T>, T>(callBack) {
        });
    }

    //==================================================//

    /**
     * ??????????????????????????????????????????Observable<CacheResult<T>>???
     *
     * @param observable
     * @param proxy
     * @param <T>
     * @return
     */
    protected <T> Observable<CacheResult<T>> toObservable(Observable observable, CallBackProxy<? extends ApiResult<T>, T> proxy) {
        return observable.map(new ApiResultFunc(proxy != null ? proxy.getType() : new TypeToken<ResponseBody>() {
        }.getType(), mKeepJson))
                .compose(new HttpResultTransformer())
                .compose(new HttpSchedulersTransformer(mIsSyncRequest, mIsOnMainThread))
                .compose(mRxCache.transformer(mCacheMode, proxy.getCallBack().getType()))
                .retryWhen(new RetryExceptionFunc(mRetryCount, mRetryDelay, mRetryIncreaseDelay));
    }

    /**
     * ??????????????????????????????????????????(CallBack??????)
     *
     * @param proxy
     * @param <T>
     * @return
     */
    public <T> Disposable execute(CallBackProxy<? extends ApiResult<T>, T> proxy) {
        Observable<CacheResult<T>> observable = build().toObservable(generateRequest(), proxy);
        if (CacheResult.class != proxy.getRawType()) {
            return observable.compose(new ObservableTransformer<CacheResult<T>, T>() {
                @Override
                public ObservableSource<T> apply(@NonNull Observable<CacheResult<T>> upstream) {
                    return upstream.map(new CacheResultFunc<T>());
                }
            }).subscribeWith(new CallBackSubscriber<T>(proxy.getCallBack()));
        } else {
            return observable.subscribeWith(new CallBackSubscriber<CacheResult<T>>(proxy.getCallBack()));
        }
    }

    /**
     * ??????????????????????????????????????????Observable<T>???
     *
     * @param proxy ?????????getType
     * @param <T>
     * @return
     */
    public <T> Observable<T> execute(CallClazzProxy<? extends ApiResult<T>, T> proxy) {
        return build().generateRequest()
                .map(new ApiResultFunc(proxy.getType(), mKeepJson))
                .compose(new HttpResultTransformer())
                .compose(new HttpSchedulersTransformer(mIsSyncRequest, mIsOnMainThread))
                .compose(mRxCache.transformer(mCacheMode, proxy.getCallType()))
                .retryWhen(new RetryExceptionFunc(mRetryCount, mRetryDelay, mRetryIncreaseDelay))
                .compose(new ObservableTransformer() {
                    @Override
                    public ObservableSource apply(@NonNull Observable upstream) {
                        return upstream.map(new CacheResultFunc<T>());
                    }
                });
    }

}

