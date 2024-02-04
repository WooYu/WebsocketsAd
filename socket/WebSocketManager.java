package com.oohlink.player.sdk.socket;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.oohlink.player.sdk.socket.bean.WebSocketConfig;
import com.oohlink.player.sdk.socket.bean.WebSocketRespMsg;
import com.oohlink.player.sdk.socket.listener.MessageRetryCallback;
import com.oohlink.player.sdk.socket.listener.WsMsgCallback;
import com.oohlink.player.sdk.util.GsonUtils;
import com.oohlink.player.sdk.util.Logger;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * 通信过程约定
 * 1、WebSocket SDK与WebSocket Server之间采用Client/Server的TCP通信方式；
 * 2、客户端与服务端通信前需进行Token鉴权；
 * 3、连接由客户端主动发起，客户端和服务端都有权利断开连接，xx秒内未连接成功按连接超时处理，客户端将进入重连流程（SDK连接超时默认30s，可在初始化时，配置指定超时时间）；
 * 4、客户端需维护通信连接状态的有效性，在连接断开后，需自动进行重连尝试，直到连接恢复。
 * 5、所有通信消息除特别说明外都采用问答方式，有问必有答。数据发起方发送数据后，xx秒内未得到回复按通讯超时处理。（SDK通讯超时默认60s，可在初始化时，配置指定超时时间）；
 * 6、通讯超时后需重发数据（心跳等不影响业务的报文不需重发），默认重发次数为3，连续重发后仍然得不到回应，则认为存在异常，停止重发。
 * <p>
 * 心跳通信维护
 * 1、WebSocket SDK与WebSocket Server建立连接后，应及时发送心跳包；
 * 2、SDK应该每n秒发送一次心跳包 （定值设置，默认10s，可在初始化时，配置指定时间间隔），且SDK需统计心跳包应答情况；
 * 3、SDK 统计丢失m次心跳包后，应主动断开与服务端的通讯连接，进入重连流程 （定值设置，默认5次，可在初始化时，配置指定次数）。
 * <p>
 * WebSocket重连机制，几个要点：
 * 1、自动重连：在连接断开时，自动尝试重新连接。
 * 2、重试策略：实现一个合理的重试策略，例如指数退避（exponential backoff），这样可以在初次尝试失败后逐步增加重试间隔，避免过于频繁的重连尝试。
 * 3、最大尝试次数：设置最大重连次数，以防止无限重连。
 * 4、监听网络变化：在移动设备上，网络连接可能会频繁变化。监听网络状态变化，并在网络恢复时尝试重连。
 * 5、状态检查：定期检查WebSocket连接状态，并在检测到断开时尝试重连。
 * 6、错误处理：在重连逻辑中添加错误处理，确保在遇到无法恢复的错误时不会尝试重连。
 * 7、用户干预：提供一个机制让用户可以手动触发重连。
 * 8、通知：在重连时提供用户反馈，例如显示重连状态或者重连尝试的次数。
 * <p>
 * 消息的发送由以下几种情况  （是否需要保存、是否需要重试）
 * 1、心跳消息，10s一次 ，每次发送校验没有收到回复的心跳数量，不用保存和重发
 * 2、回执消息，如果发送失败，需要重试3次。发送超过重试次数需要丢弃
 * 3、上报消息，如果发送失败，需要重试3次。发送超过重试次数或者连接未建立，需要保存起来，下次连接正常时再次自动发送
 */
public class WebSocketManager {
    private static final String logTag = "tag_socket";

    private static WebSocketManager instance;
    private WebSocketConfig config;
    private WsMsgCallback wsMsgCallback;
    private OkHttpClient client;
    private WebSocket webSocket;
    private final Handler retryHandler;
    /**
     * 建立连接失败，重连次数
     */
    private int currentRetryCount = 0;
    /**
     * 手动关闭
     */
    private boolean isManualClose = false;

    /**
     * 长连接关闭code
     */
    /**
     * 客户端主动关闭
     */
    private final int CLOSE_CODE_CLIENT_ACTIVELY = 1000;
    /**
     * 由于心跳丢失过多，关闭连接
     */
    private final int CLOSE_CODE_HEARTBEAT_LOSS = 1001;

    /**
     * 是否已经建立连接
     */
    private ConnectionState connectState = ConnectionState.DISCONNECTED;

    /**
     * 待接收心跳响应的集合
     */
    private final ArrayList<String> unResponseHeartBeats = new ArrayList<>();

    /**
     * 待接收业务消息回复的集合
     */
    private MessageProcessor<String, MessageRetryTask> businessMsgProcessor;

    /**
     * 长链接稳定性检查（独立于websockets自动重连机制外的）
     */
    private Disposable connectStabilityCheckDisposable;
    /**
     * 发起连接的
     */
    private Disposable connectDisposable;

    /**
     * 发送消息的
     */
    private Disposable sendMsgDisposable;

    /**
     * 断开websockets的
     */
    private Disposable disconnectDisposable;

    /**
     * 发送业务消息，增加背压控制
     */
    private PublishProcessor<MessageRetryTask> businessMsgSendProcessor;
    private Disposable businessMsgSendDisposable;


    private WebSocketManager() {
        this.retryHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized WebSocketManager getInstance() {
        if (instance == null) {
            instance = new WebSocketManager();
        }
        return instance;
    }

    /**
     * 设置配置并初始化客户端
     */
    public void setConfig(WebSocketConfig config) {
        Logger.d(logTag, "setConfig: " + config);
        this.config = config;
        initializeClient();
    }

    /**
     * 设置消息回调
     */
    public void setWsMsgCallback(WsMsgCallback wsMsgCallback) {
        this.wsMsgCallback = wsMsgCallback;
    }

    /**
     * 确保在不需要时释放资源
     */
    public void destroy() {
        Logger.d(logTag, "destroy()");
        closeConnection("Release resources");
        webSocket = null;
        client = null;

        if (null != wsMsgCallback) {
            wsMsgCallback.onDisconnected();
            wsMsgCallback = null;
        }
        connectState = ConnectionState.DISCONNECTED;
        resetStatistics();
        completePublishProcessor(businessMsgSendProcessor);
        stopConnectStabilityCheck();
    }

    /**
     * 获取是否已经建立连接
     */
    public boolean isConnected() {
        return null != webSocket && connectState == ConnectionState.CONNECTED;
    }

    public boolean isConnecting() {
        return null != webSocket && connectState == ConnectionState.CONNECTING;
    }

    /**
     * 更新token和URL，然后重新连接
     */
    public synchronized void updateTokenAndURL(String token, String url) {
        Logger.d(logTag, "updateTokenAndURL: token = " + token + ", url = " + url);
        if (config == null) {
            config = new WebSocketConfig();
        }
        config.setToken(token);
        config.setUrl(url);
        // 如果已经连接，则先关闭现有连接
        if (isConnected()) {
            closeConnection("updateTokenAndURL()");
        }
        // 重新连接
        connect();
    }

    /**
     * 手动触发连接（比如用户手动重试、网络重新连接）
     */
    public void manualReconnect() {
        Logger.e(logTag, "manualReconnect() isConnected = " + isConnected());
        resetStatistics();
        autoReconnect();
    }

    public synchronized void connect() {
        Logger.d(logTag, "connect()");
        if (connectState == ConnectionState.CONNECTING) {
            return;
        }
        connectState = ConnectionState.CONNECTING;

        if (null == config) {
            Logger.e(logTag, "connect() config is null");
            connectState = ConnectionState.DISCONNECTED;
            return;
        }
        if (null == client) {
            initializeClient();
        }

        disposableCancel(connectDisposable);
        connectDisposable = Observable
                .fromCallable(() -> {
                    Request request = new Request.Builder()
                            .url(config.getUrl())
                            .addHeader("Authorization", config.getToken())
                            .addHeader("Sec-WebSocket-Protocol", "apmp1.0")
                            .build();
                    webSocket = client.newWebSocket(request, webSocketListener);
                    return true;
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        result -> Logger.d(logTag, "connect() Initiating connection is normal."),
                        error -> {
                            error.printStackTrace();
                            Logger.e(logTag, "connect() error!");
                        }
                );
    }

    /**
     * 为业务消息添加重试机制
     */
    public void addRetryForBusinessMsg(String seqOfMsg, String businessMsg) {
        MessageRetryTask task = new MessageRetryTask(seqOfMsg, businessMsg, config.getMaxRetryCountOfResendMsg());
        task.setRetryResultCallback(new MessageRetryCallback() {
            @Override
            public void retryFailed(MessageRetryTask sendTask) {
                handleFailedBusinessMessage("retryFailed", sendTask.getSeq(), sendTask.getOriginMsg());
            }

            @Override
            public void resendMsg(MessageRetryTask sendTask) {
                Logger.e(logTag, "resendMsg() seq = " + sendTask.getSeq() + ", retriesLeft = " + sendTask.getRetriesLeft());
                if (null != businessMsgSendProcessor) {
                    businessMsgSendProcessor.onNext(sendTask);
                } else {
                    sendMsgOfBusiness(sendTask);
                }
            }
        });

        if (null != businessMsgSendProcessor) {
            businessMsgSendProcessor.onNext(task);
        } else {
            sendMsgOfBusiness(task);
        }
    }

    private void initializeClient() {
        client = new OkHttpClient.Builder()
                .readTimeout(config.getCommunicationTimeout(), TimeUnit.SECONDS)
                .writeTimeout(config.getCommunicationTimeout(), TimeUnit.SECONDS)
                .connectTimeout(config.getConnectTimeout(), TimeUnit.SECONDS)
                .build();
        businessMsgProcessor = new MessageProcessor<>(config.getMaxCapacityOfBusinessMsg());
        startConnectStabilityCheck();
    }

    private void onWebSocketClientMessage(String message) {
        if (TextUtils.isEmpty(message)) return;

        try {
            WebSocketRespMsg response = GsonUtils.fromJson(message, WebSocketRespMsg.class);
            if (null == response) {
                return;
            }
            if (TextUtils.equals(WsMsgManager.HeartBeatMessage, response.getCmd())) {
                unResponseHeartBeats.remove(response.getSeq());
                return;
            }
            Logger.d(logTag, "onWebSocketClientMessage() message = " + message);
            if (!TextUtils.equals(WsMsgManager.DnMessage, response.getCmd())) {
                return;
            }

            //如果是服务端的回执消息，那么根据消息seq匹配，从缓存队列移除对应的业务消息
            if (isReceiptMsg(response.getData())) {
                handleReceiptMsg(response);
                return;
            }

            //如果是服务端下发的消息，那么发送回执消息
            sendMsgOfReceipt(response.getSeq(), response.getTarget(), response.getSource());

            if (null != wsMsgCallback) {
                wsMsgCallback.onMessageReceived(response);
            }
        } catch (Throwable e) {
            e.printStackTrace();
            Logger.e(logTag, "onWebSocketClientMessage() abnormal . message = " + message);
            Logger.e(logTag, "onWebSocketClientMessage() e = " + e.getMessage());
        }
    }

    /***
     * 自动连接
     */
    private void autoReconnect() {
        if (isConnected()) {
            return;
        }
        connect();
    }

    private void closeConnection(String closeReason) {
        isManualClose = true;
        stopHeartbeat();

        closeWebSocket(CLOSE_CODE_CLIENT_ACTIVELY, closeReason);
    }

    private void closeWebSocket(int code, String reason) {
        Logger.e(logTag, "closeWebSocket() code = " + code + " , reason = " + reason);
        disconnectDisposable = Observable
                .fromCallable(() -> {
                    if (webSocket != null) {
                        return webSocket.close(code, reason);
                    } else {
                        return true;
                    }
                }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        result -> Logger.d(logTag, "closeWebSocket() result = " + result),
                        error -> {
                            error.printStackTrace();
                            Logger.e(logTag, "closeWebSocket() error!" + error.getMessage());
                        }
                );
    }

    /**
     * 重置统计变量
     */
    private void resetStatistics() {
        currentRetryCount = 0;
        unResponseHeartBeats.clear();
        if (null != retryHandler) {
            retryHandler.removeCallbacksAndMessages(null);
        }
        if (null != businessMsgProcessor) {
            Logger.e(logTag, "resetStatistics() The number of messages pending in the cache queue . size = " + businessMsgProcessor.getSize());
            businessMsgProcessor.clear();
        }
        disposableCancel(connectDisposable);
        disposableCancel(sendMsgDisposable);
        disposableCancel(businessMsgSendDisposable);
    }

    private void resetBusinessMsgSendProcessor() {
        completePublishProcessor(businessMsgSendProcessor);
        businessMsgSendProcessor = PublishProcessor.create();
        businessMsgSendDisposable = businessMsgSendProcessor
                .onBackpressureLatest()
                .observeOn(Schedulers.io())
                .subscribe(this::sendMsgOfBusiness, throwable -> {
                    throwable.printStackTrace();
                    Logger.e(logTag, "businessMsgSendProcessor abnormal! error = " + throwable.getMessage());
                }, () -> Logger.d(logTag, "businessMsgSendProcessor complete!"));
    }

    private void startHeartbeat() {
        sendMsgOfHeartbeat();
        removeRetryRunnable(heartbeatRunnable);
        retryHandler.postDelayed(heartbeatRunnable, config.getHeartbeatInterval() * 1000L);
    }

    private void stopHeartbeat() {
        if (null != retryHandler) {
            retryHandler.removeCallbacks(heartbeatRunnable);
        }
    }

    /**
     * 独立于websockets重连机制外的，重连检查
     */
    private void startConnectStabilityCheck() {
        stopConnectStabilityCheck();
        // 延迟启动，单位分钟
        int delayCheckTime = 30;
        connectStabilityCheckDisposable = Observable.timer(delayCheckTime, TimeUnit.MINUTES)
                .flatMap(ignored -> Observable.defer(() -> {
                    // 随机1~2倍延迟后再次检查
                    long delay = delayCheckTime + (long) (Math.random() * delayCheckTime);
                    return Observable.timer(delay, TimeUnit.MINUTES);
                }))
                .repeat() // 重复这个过程
                .subscribe(aLong -> onCheckConnectStability(), throwable -> {
                    Logger.e(logTag, "startConnectStabilityCheck() error!" + throwable.getMessage());
                    throwable.printStackTrace();
                }, () -> Logger.d(logTag, "startConnectStabilityCheck() complete!"));
    }

    private void stopConnectStabilityCheck() {
        disposableCancel(connectStabilityCheckDisposable);
    }

    private void onCheckConnectStability() {
        Logger.e(logTag, "onCheckConnectStability() isConnected = " + isConnected());
        if (!isConnected()) {
            manualReconnect();
        }
    }

    /**
     * 是否为回执消息
     */
    private boolean isReceiptMsg(Object data) {
        return null == data || "null".equals(data) || "".equals(data);
    }

    /**
     * 发送消息
     */
    private void sendWebSocketMessage(String message, boolean printLog) {
        sendMsgDisposable = Observable
                .fromCallable(() -> {
                    boolean sendResult = false;
                    if (null != webSocket && isConnected()) {
                        sendResult = webSocket.send(message);
                    }
                    if (printLog) {
                        Logger.e(logTag, "sendData() sendResult = " + sendResult + " , isConnected = " + isConnected() + " , message = " + message);
                    }
                    return sendResult;
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        result -> {
                        },
                        error -> {
                            error.printStackTrace();
                            Logger.e(logTag, "sendData() error!" + error.getMessage());
                        }
                );
    }

    /**
     * 发送回执消息
     */
    private void sendMsgOfReceipt(String seq, String source, String target) {
        sendWebSocketMessage(WsMsgManager.getInstance().generateUpCmdOfReceipt(seq, source, target), false);
    }

    /**
     * 发送心跳消息
     * 10s一次 ，每次发送校验没有收到回复的心跳数量，不用保存和重发
     */
    private void sendMsgOfHeartbeat() {
        String seqOfHeartBeat = WsMsgManager.getInstance().getSeq();
        sendWebSocketMessage(WsMsgManager.getInstance().generateHeartBeatCmd(seqOfHeartBeat), false);
        unResponseHeartBeats.add(seqOfHeartBeat);
    }

    /**
     * 发送业务消息
     */
    private void sendMsgOfBusiness(MessageRetryTask task) {
        if (null == task) {
            return;
        }
        if (!isConnected()) {
            handleFailedBusinessMessage("the connection has been disconnected", task.getSeq(), task.getOriginMsg());
            return;
        }

        sendWebSocketMessage(task.getOriginMsg(), true);
        if (null != retryHandler) {
            retryHandler.postDelayed(task, config.getCommunicationTimeout() * 1000L);
        }
        //添加到缓存队列（同一个元素多次添加，后面会添加不成功）
        businessMsgProcessor.addMessage(task.getSeq(), task);
    }

    /**
     * 处理失败的业务消息
     * 如果发送业务消息时未建立连接或者发送消息超过重试次数，那么消息发送失败
     */
    private void handleFailedBusinessMessage(String reason, String seq, String message) {
        Logger.e(logTag, "sendBusiness message sending failed! reason = " + reason + " . seq = " + seq + ", message = " + message);
        //从缓存队列移除
        businessMsgProcessor.removeMessage(seq);
        if (null != wsMsgCallback) {
            wsMsgCallback.onMessageSendFailed(seq, message);
        }
    }

    /**
     * 处理服务端的回执消息
     */
    private void handleReceiptMsg(WebSocketRespMsg response) {
        if (null != businessMsgProcessor) {
            String seqOfReceipt = response.getSeq();
            MessageRetryTask retryTask = businessMsgProcessor.getMessage(seqOfReceipt);
            if (null != retryTask) {
//                Logger.d(logTag, "handleReceiptMsg() seqOfReceipt = " + seqOfReceipt + " has been accepted successfully by the server.");
                retryTask.cancel();
                retryHandler.removeCallbacks(retryTask);

                if (null != wsMsgCallback) {
                    wsMsgCallback.onMessageSendSuccess(seqOfReceipt, retryTask.getOriginMsg());
                }
            }
            businessMsgProcessor.removeMessage(response.getSeq());
        }
    }

    private void retryConnection() {
        Logger.e(logTag, "retryConnection() isManualClose = " + isManualClose + " , currentRetryCount = " + currentRetryCount);
        if (isManualClose) {
            return;
        }

        stopHeartbeat();
        if (currentRetryCount < config.getMaxRetryCountOfReconnect()) {
            long retryInterval = (long) Math.pow(2, currentRetryCount) * 1000;
            Logger.e(logTag, "retryConnection() After a delay of " + retryInterval + " milliseconds, try connecting again!");
            retryHandler.postDelayed(this::autoReconnect, retryInterval);
            currentRetryCount++;
        } else {
            Logger.e(logTag, "Failed to connect after " + currentRetryCount + " retries!");
        }
    }

    private void removeRetryRunnable(Runnable runnable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (retryHandler.hasCallbacks(runnable)) {
                retryHandler.removeCallbacks(runnable);
            }
        }
    }

    private void disposableCancel(Disposable disposable) {
        if (null != disposable && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }

    private <T> void completePublishProcessor(PublishProcessor<T> publishProcessor) {
        if (null != publishProcessor && !publishProcessor.hasComplete()) {
            publishProcessor.onComplete();
        }
    }

    private final Runnable heartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isConnected() || null == webSocket) {
                Logger.e(logTag, "heartbeatRunnable() When sending a heartbeat packet, the long link has been disconnected!");
            }
            if (unResponseHeartBeats.size() >= config.getMaxHeartbeatLoss()) {
                closeWebSocket(CLOSE_CODE_HEARTBEAT_LOSS, "heartbeatRunnable() Too much heartbeat loss!");
                return;
            }
            sendMsgOfHeartbeat();
            retryHandler.postDelayed(this, config.getHeartbeatInterval() * 1000L);
        }
    };

    private final WebSocketListener webSocketListener = new WebSocketListener() {
        @Override
        public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
            Logger.d(logTag, "onOpen()");
            connectState = ConnectionState.CONNECTED;
            resetStatistics();
            resetBusinessMsgSendProcessor();
            isManualClose = false;
            startHeartbeat();

            if (null != wsMsgCallback) {
                wsMsgCallback.onConnected();
            }
        }

        @Override
        public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
            onWebSocketClientMessage(text);
        }

        @Override
        public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
            super.onClosing(webSocket, code, reason);
            connectState = ConnectionState.DISCONNECTED;
        }

        @Override
        public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
            Logger.e(logTag, "onClosed() code = " + code + ", reason = " + reason + " , isManualClose = " + isManualClose);
            connectState = ConnectionState.DISCONNECTED;
            if (null != wsMsgCallback) {
                wsMsgCallback.onDisconnected();
            }

            retryConnection();
        }

        @Override
        public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, Response response) {
            Logger.e(logTag, "onFailure() t = " + t.getMessage());
            connectState = ConnectionState.DISCONNECTED;
            if (null != wsMsgCallback) {
                wsMsgCallback.onDisconnected();
            }

            retryConnection();
        }
    };

}
