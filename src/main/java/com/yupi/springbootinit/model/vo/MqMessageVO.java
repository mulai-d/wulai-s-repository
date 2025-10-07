package com.yupi.springbootinit.model.vo;

import com.yupi.springbootinit.model.entity.Chart;
import lombok.Data;

@Data
public class MqMessageVO {
    private Chart chart;
    private String userInput;
}
