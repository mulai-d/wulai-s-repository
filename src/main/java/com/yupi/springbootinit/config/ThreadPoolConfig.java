package com.yupi.springbootinit.config;


import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.*;

@Configuration
public class ThreadPoolConfig {

    @Bean
    public ThreadPoolExecutor threadPoolExecutor() {
        //线程池工厂可以给线程命名
        ThreadFactory threadFactory = new ThreadFactory() {
            private int count = 1;
            @Override
            public Thread newThread(@NotNull Runnable r) {
                Thread thread = new Thread(r);
                thread.setName("thread-" + count);
                count++;
                return thread;
            }
        };


        ThreadPoolExecutor poolExecutor = new ThreadPoolExecutor(
                2,
                4,
                100,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(4),
                threadFactory);

        return poolExecutor;
    }

}
