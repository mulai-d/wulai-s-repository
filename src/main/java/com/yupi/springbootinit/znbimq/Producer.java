package com.yupi.springbootinit.znbimq;

import cn.hutool.json.JSONUtil;
import com.yupi.springbootinit.model.entity.Chart;
import com.yupi.springbootinit.model.vo.MqMessageVO;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Producer {
    @Autowired
    private RabbitTemplate rabbitTemplate;
    public void sendMessage(MqMessageVO message) {

        String jsonStr = JSONUtil.toJsonStr(message);
        rabbitTemplate.convertAndSend(MqConstant.MQ_EXCHANGE, MqConstant.MQ_ROUTINGKEY ,jsonStr);
    }
}
