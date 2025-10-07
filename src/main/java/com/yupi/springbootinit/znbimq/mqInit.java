package com.yupi.springbootinit.znbimq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;

import java.util.Scanner;

public class mqInit {

    private static final String TASK_QUEUE_NAME = "mq_consumer";
    private static final String EXCHANGE_NAME = "test_exchange";
    public static void main(String[] argv) throws Exception {


        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
       Connection connection = factory.newConnection();


       Channel channel = connection.createChannel();
        channel.exchangeDeclare(MqConstant.MQ_EXCHANGE, "direct");
       channel.queueDeclare(MqConstant.MQ_QUEUE, true, false, false, null);


        channel.queueBind(MqConstant.MQ_QUEUE, MqConstant.MQ_EXCHANGE, MqConstant.MQ_ROUTINGKEY);



    }
}
