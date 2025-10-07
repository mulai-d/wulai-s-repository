package com.yupi.springbootinit.znbimq;

import cn.hutool.json.JSONUtil;
import com.rabbitmq.client.Channel;
import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.manager.AiManager;
import com.yupi.springbootinit.model.entity.Chart;
import com.yupi.springbootinit.model.vo.MqMessageVO;
import com.yupi.springbootinit.service.ChartService;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.A;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class Consumer {


    @Autowired
    private ChartService chartService;

    @Autowired
    private AiManager aiManager;

    //指定监听的队列，和确认机制
    @RabbitListener(queues = {MqConstant.MQ_QUEUE}, ackMode = "MANUAL")
    public void receive(String msg, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        MqMessageVO mqMessageVO = JSONUtil.toBean(msg, MqMessageVO.class);

        log.info("msg:{}", msg);
        Chart chart = mqMessageVO.getChart();
        String userInput = mqMessageVO.getUserInput();

        Chart taskChart = new Chart();
        Long id = chart.getId();
        taskChart.setId(id);
        taskChart.setStatus("running");
        boolean b = chartService.updateById(taskChart);//??????chart??
        if (!b){
            updateChartHandler(id, "修改图表状态为running失败");
            return;
        }

        log.info("正在调用ai方法");
        //调用ai方法，并处理返回值
        String s = aiManager.sendMsgToXingHuo(true, userInput);
        log.info("正在处理ai结果，并保存到数据库");
        String[] split = s.split("'【【【【'");
        if (split.length < 3) {
            updateChartHandler(id, "ai生成图表失败");
            return;
        }
        for (int i = 0; i < split.length; i++) {
            System.out.println("结果 "+i+" ："+split[i]);
        }
        //trim(); 移除字符串首尾的所有空白字符，包括空格、制表符（tab）、换行符等。
        String genChart = split[1].trim();
        String genResult = split[2].trim();
        Chart resultChart = new Chart();
        resultChart.setId(id);
        resultChart.setGenChart(genChart);
        resultChart.setGenResult(genResult);
        resultChart.setStatus("succeed");
        boolean b1 = chartService.updateById(resultChart);
        if (!b1){
            updateChartHandler(id, "修改chart状态为succeed和保存图表信息失败");
            return;
        }


        try {
            channel.basicAck(deliveryTag, false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }



    private void updateChartHandler(Long chartId, String message) {
        Chart chart = new Chart();
        chart.setId(chartId);
        chart.setStatus("failed");
        chart.setExecMessage(message);
        boolean b = chartService.updateById(chart);
        if (!b) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "修改"+chartId+"图表状态为failed，添加执行信息失败");
        }
    }
}
