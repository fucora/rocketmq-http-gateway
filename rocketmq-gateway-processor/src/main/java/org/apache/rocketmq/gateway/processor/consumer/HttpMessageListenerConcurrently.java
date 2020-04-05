package org.apache.rocketmq.gateway.processor.consumer;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.gateway.common.protocol.MessageConsumerRequest;
import org.apache.rocketmq.gateway.common.protocol.MessageConsumerResponse;
import org.apache.rocketmq.gateway.common.utils.URL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.*;

import static org.apache.rocketmq.gateway.common.protocol.MessageConsumerEntity.HEADER_KEY;
import static org.apache.rocketmq.gateway.common.protocol.MessageConsumerEntity.HEADER_TAG;
import static org.apache.rocketmq.gateway.common.protocol.MessageEntity.HEADER_REQ_ID;
import static org.apache.rocketmq.gateway.common.protocol.MessageEntity.HEADER_TOPIC;

final class HttpMessageListenerConcurrently extends HttpExecutor implements MessageListenerConcurrently {

    private static final int RESP_SUCCESS_200 = 200;

    private static final Logger logger = LoggerFactory.getLogger(HttpMessageListenerConcurrently.class);

    private final String topic;
    private final String app;
    private final String tag;
    private final String path;
    private final String callback;
    private final String gatewayAddress;

    HttpMessageListenerConcurrently(final String callback,
                                    final String topic,
                                    final String tag,
                                    final String app,
                                    final CloseableHttpClient httpClient,
                                    final String gatewayAddress) throws UnsupportedEncodingException {

        this.callback = callback;
        this.path = this.callback;
        this.topic = topic;
        this.tag = tag;
        this.app = app;
        this.httpClient = httpClient;
        this.gatewayAddress = gatewayAddress;
    }

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> messages, ConsumeConcurrentlyContext context) {
        MessageConsumerRequest requestLog = new MessageConsumerRequest();
        requestLog.setTopic(topic);
        requestLog.setTag(tag);
        requestLog.setApp(app);
        requestLog.setCallback(callback);
        requestLog.setGatewayAddress(gatewayAddress);

        MessageConsumerResponse responseLog = new MessageConsumerResponse();
        responseLog.setTopic(topic);
        responseLog.setTag(tag);
        responseLog.setApp(app);
        responseLog.setCallback(callback);
        responseLog.setGatewayAddress(gatewayAddress);

        RuntimeException ex = null;

        for (MessageExt message : messages) {
            requestLog.setTime(new Date());
            requestLog.setInternalReqId(UUID.randomUUID().toString());
            requestLog.setKey(message.getKeys());

            Map<String, String> headers = new HashMap<>();
            headers.put(HEADER_TAG, message.getTags());
            headers.put(HEADER_TOPIC, topic);
            headers.put(HEADER_REQ_ID, requestLog.getInternalReqId());
            headers.put(HEADER_KEY, message.getKeys());

            if (logger.isDebugEnabled()) {
                logger.info(String.format("%s%s", MessageConsumerRequest.LOG_PREFIX, requestLog.toString()));
            }

            try {
                String response = this.post(this.path, headers, message.getBody());
                if (StringUtils.isBlank(response)) {
                    responseLog.setContent("No response");
                    responseLog.setSuccess(false);

                    ex = new RuntimeException("No response");
                } else {
                    requestLog.setContent(response);
                    if (response.equals(RESP_SUCCESS_200)) {
                        responseLog.setSuccess(true);
                    } else {
                        ex = new RuntimeException("Consume message failed, non-success response." + response);
                    }
                }
            } catch (Exception e) {

                responseLog.setTime(new Date());
                responseLog.setInternalReqId(requestLog.getInternalReqId());
                responseLog.setContent(e.getMessage());
                responseLog.setSuccess(false);
                ex = new RuntimeException("Post message to app error ", e);
            }

            if (logger.isDebugEnabled()) {
                logger.info(String.format("%s%s", MessageConsumerResponse.LOG_PREFIX, responseLog.toString()));
            }

        }

        if (ex != null) {
            throw ex;
        }

        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

}


