package com.yupi.springbootinit.mq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.nio.charset.StandardCharsets;

public class Send {

    private final static String QUEUE_NAME = "hello";

    public static void main(String[] argv) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");

        //建立连接。创建通道
        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {

            //创建队列
            channel.queueDeclare(QUEUE_NAME, false, false, false, null);
            //消息
            String message = "cnmbd";
            channel.basicPublish("", QUEUE_NAME, null, message.getBytes(StandardCharsets.UTF_8));
            System.out.println(" [x] Sent '" + message + "'");
        }
    }
}