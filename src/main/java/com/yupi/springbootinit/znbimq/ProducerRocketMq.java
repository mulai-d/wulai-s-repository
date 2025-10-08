package com.yupi.springbootinit.znbimq;


import cn.hutool.json.JSONUtil;
import com.yupi.springbootinit.model.vo.MqMessageVO;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProducerRocketMq {


//    @Autowired
//    private RabbitTemplate rabbitTemplate;
//    public void sendMessage(MqMessageVO message) {
//
//        String jsonStr = JSONUtil.toJsonStr(message);
//        rabbitTemplate.convertAndSend(MqConstant.MQ_EXCHANGE, MqConstant.MQ_ROUTINGKEY ,jsonStr);
//    }


    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    public void sendMessage(String message) {
//        String jsonStr = JSONUtil.toJsonStr(message);
        rocketMQTemplate.convertAndSend("RocketMqTopic", message);

    }
}
