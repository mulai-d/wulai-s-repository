package com.yupi.springbootinit.znbimq;

import com.yupi.springbootinit.common.ErrorCode;
import com.yupi.springbootinit.exception.BusinessException;
import com.yupi.springbootinit.manager.AiManager;
import com.yupi.springbootinit.model.entity.Chart;
import com.yupi.springbootinit.service.ChartService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(consumerGroup = "MyConsumerGroup", topic = "RocketMqTopic")
public class ConsumerRocketMq implements RocketMQListener<String> {


    @Autowired
    private ChartService chartService;

    @Autowired
    private AiManager aiManager;

    @Override
    public void onMessage(String message) {
        log.info("ConsumerRocketMq onMessage: {}", message);
        System.out.println("接收到消息了!!!!");

        Chart taskChart = new Chart();
        Long id = Long.valueOf(message);
        taskChart.setId(id);
        taskChart.setStatus("running");
        boolean b = chartService.updateById(taskChart);
        if (!b){
            updateChartHandler(id, "修改图表状态为running失败");
            return;
        }


        //调用ai方法，并处理返回值
        String s = aiManager.sendMsgToXingHuo(true, getUserInput(id));
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
    }

    private String getUserInput(Long id){

        Chart chart = chartService.getById(id);
        String goal = chart.getGoal();
        String chartType = chart.getChartType();
        String csv = chart.getChartData();
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析目标：").append(goal).append(chartType).append("/n").append("数据：").append(csv);

        return userInput.toString();
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
