package com.yupi.springbootinit;

import com.yupi.springbootinit.config.ThreadPoolConfig;
import com.yupi.springbootinit.config.WxOpenConfig;
import javax.annotation.Resource;

import com.yupi.springbootinit.manager.AiManager;
import com.yupi.springbootinit.manager.RedisLimiterManager;
import com.yupi.springbootinit.znbimq.Producer;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 主类测试
 *
 * @author <a href="https://github.com/liyupi">程序员鱼皮</a>
 * @from <a href="https://yupi.icu">编程导航知识星球</a>
 */
@SpringBootTest
class MainApplicationTests {

    @Resource
    private WxOpenConfig wxOpenConfig;

    @Test
    void contextLoads() {
        System.out.println(wxOpenConfig);
    }

    @Resource
    private AiManager aiManager;


    // import org.junit.jupiter.api.Test;
    @Test
    public void test() {
        String c = "分析需求：\n" +
                "分析网站用户的增长情况 \n" +
                "请使用 柱状图 \n" +
                "原始数据：\n" +
                "日期,用户数\n" +
                "1号,10\n" +
                " 2号,20\n" +
                " 3号,30";
        String s = aiManager.sendMsgToXingHuo(true, c);
        System.out.println("s = " + s);
    }

    @Autowired
    private RedisLimiterManager redisLimiterManager;

    @Test
    public void test1() throws InterruptedException {
        for (int i = 0; i < 2; i++) {
            redisLimiterManager.doLimiter("1");
            System.out.println("成功");
        }

        System.out.println("睡眠1s");
        Thread.sleep(1000);

        for (int i = 0; i < 2; i++) {
            redisLimiterManager.doLimiter("1");
            System.out.println("成功");
        }
    }


    @Autowired
    private Producer producer;
//
//    @Test
//    public void testRabbitTemplate() {
//        producer.sendMessage("test_exchange", "routing", "牧濑红莉栖");
//    }
}
