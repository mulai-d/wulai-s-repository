package com.yupi.springbootinit.mq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

public class ReceiveLogsTopic {

  private static final String EXCHANGE_NAME = "topic_logs";
  String string="stain:gates.lab.1";

  public static void main(String[] argv) throws Exception {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");
    Connection connection = factory.newConnection();
    Channel channel = connection.createChannel();

    channel.exchangeDeclare(EXCHANGE_NAME, "topic");
      channel.queueDeclare("lab", true, false, false, null);
      String queueName = "lab";
      channel.queueBind(queueName, EXCHANGE_NAME, "*.lab.*");
      System.out.println(" [lab1] Waiting for messages. To exit press CTRL+C");

      DeliverCallback deliverCallback = (consumerTag, delivery) -> {
          String message = new String(delivery.getBody(), "UTF-8");
          System.out.println(" [lab] Received '" +
                  delivery.getEnvelope().getRoutingKey() + "':'" + message + "'");
      };


    channel.basicConsume(queueName, true, deliverCallback, consumerTag -> { });

      //创建队列2，及直接的监听机制
      channel.queueDeclare("sern", true, false, false, null);
      String queueName2 = "sern";
      channel.queueBind(queueName2, EXCHANGE_NAME, "*.tiduisystem.*");
      System.out.println(" [sern] Waiting for messages. To exit press CTRL+C");

      DeliverCallback deliverCallback2 = (consumerTag, delivery) -> {
          String message = new String(delivery.getBody(), "UTF-8");
          System.out.println(" [sern] Received '" +
                  delivery.getEnvelope().getRoutingKey() + "':'" + message + "'");
      };
      channel.basicConsume(queueName2, true, deliverCallback2, consumerTag -> { });


      //创建队列3
      channel.queueDeclare("lab2", true, false, false, null);
      String queueName3 = "lab2";
      channel.queueBind(queueName3, EXCHANGE_NAME, "*.lab.*");
      System.out.println(" [lab2] Waiting for messages. To exit press CTRL+C");

      DeliverCallback deliverCallback3 = (consumerTag, delivery) -> {
          String message = new String(delivery.getBody(), "UTF-8");
          System.out.println(" [lab2] Received '" +
                  delivery.getEnvelope().getRoutingKey() + "':'" + message + "'");
      };
      channel.basicConsume(queueName3, true, deliverCallback3, consumerTag -> { });
  }
}