package com.yupi.springbootinit.mq;

import com.rabbitmq.client.*;

public class ReceiveLogsDirect {

  private static final String EXCHANGE_NAME = "direct_logs";

  public static void main(String[] argv) throws Exception {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost("localhost");
    Connection connection = factory.newConnection();
    Channel channel = connection.createChannel();

    channel.exchangeDeclare(EXCHANGE_NAME, "direct");
    channel.queueDeclare("lab", true, false, false, null);
    String queueName = "lab";
    channel.queueBind(queueName, EXCHANGE_NAME, "stains:gate");
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
      channel.queueBind(queueName2, EXCHANGE_NAME, "tiduisystem");
      System.out.println(" [sern] Waiting for messages. To exit press CTRL+C");

      DeliverCallback deliverCallback2 = (consumerTag, delivery) -> {
          String message = new String(delivery.getBody(), "UTF-8");
          System.out.println(" [sern] Received '" +
                  delivery.getEnvelope().getRoutingKey() + "':'" + message + "'");
      };
      channel.basicConsume(queueName2, true, deliverCallback2, consumerTag -> { });
  }
}